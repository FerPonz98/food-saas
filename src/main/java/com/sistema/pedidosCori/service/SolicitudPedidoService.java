package com.sistema.pedidosCori.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dao.ISolicitudPedidoDao;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.EstadoSolicitud;
import com.sistema.pedidosCori.models.entity.Modalidad;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;
import com.sistema.pedidosCori.models.entity.SolicitudPedido;
import com.sistema.pedidosCori.models.entity.SolicitudPedidoItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolicitudPedidoService {
    public static final int MAX_ITEMS = 30;
    public static final int MAX_PENDIENTES_POR_MESA = 5;
    static final Duration VIGENCIA = Duration.ofHours(3);

    private final ISolicitudPedidoDao dao;
    private final IPlatoDao platos;
    private final IPresentacionPlatoDao presentaciones;
    private final IPedidoDao pedidos;
    private final ComandaService comandas;
    private final CartaPublicaService carta;
    private final ConfiguracionNegocioService configuracion;
    private final AuditoriaService auditoria;
    private final SimpMessagingTemplate mensajes;
    private final SecureRandom random = new SecureRandom();
    private final LimitadorPorIp limitador = new LimitadorPorIp(6, Duration.ofMinutes(10),
        "Ya enviaste varios pedidos. Espera a que el personal los revise.");

    public record ItemNuevo(Long platoId, Long presentacionId, Integer cantidad, String notas) {
    }

    public record SolicitudNueva(String nombreCliente, String nota, List<ItemNuevo> items) {
    }

    public record ItemVista(String nombre, Integer cantidad, Double precioUnitario, Double subtotal, String notas) {
    }

    public record EstadoCliente(String token, String estado, Double total, List<ItemVista> items,
            LocalDateTime creadoEn, String motivoRechazo) {
    }

    public record SolicitudVista(Long id, Integer numeroMesa, String nombreCliente, String nota, Double total,
            LocalDateTime creadoEn, List<ItemVista> items, Long pedidoAbiertoId) {
    }

    @Transactional
    public EstadoCliente crear(int mesa, SolicitudNueva r, String ip) {
        carta.validarMesa(mesa);
        if (r == null || r.items() == null || r.items().isEmpty()) throw new IllegalArgumentException("Agrega al menos un producto");
        if (r.items().size() > MAX_ITEMS) throw new IllegalArgumentException("El pedido admite hasta " + MAX_ITEMS + " productos");
        String nombre = texto(r.nombreCliente(), 60, "El nombre");
        String nota = texto(r.nota(), 200, "La nota");
        LocalDateTime ahora = LocalDateTime.now();
        if (dao.countByNumeroMesaAndEstadoAndCreadoEnAfter(mesa, EstadoSolicitud.PENDIENTE, ahora.minus(VIGENCIA)) >= MAX_PENDIENTES_POR_MESA)
            throw new LlamadoService.LimiteExcedido("Esta mesa ya tiene varios pedidos esperando confirmación.");
        SolicitudPedido s = new SolicitudPedido();
        s.setNumeroMesa(mesa);
        s.setToken(token());
        s.setEstado(EstadoSolicitud.PENDIENTE);
        s.setNombreCliente(nombre);
        s.setNota(nota);
        s.setCreadoEn(ahora);
        double total = 0;
        for (ItemNuevo i : r.items()) {
            SolicitudPedidoItem item = item(i);
            item.setSolicitud(s);
            s.getItems().add(item);
            total += item.getSubtotal();
        }
        s.setTotal(redondear(total));
        limitador.controlar(ip);
        SolicitudPedido guardada = dao.save(s);
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("tipo", "PEDIDO_QR");
        aviso.put("solicitudId", guardada.getId());
        aviso.put("numeroMesa", mesa);
        aviso.put("total", guardada.getTotal());
        aviso.put("mensaje", configuracion.vista().getPuntoAtencionSingular() + " " + mesa + " envió un pedido desde la carta");
        TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", (Object) aviso));
        return estado(guardada);
    }

    @Transactional(readOnly = true)
    public EstadoCliente estadoCliente(int mesa, String token) {
        carta.validarMesa(mesa);
        SolicitudPedido s = dao.findByToken(token == null ? "" : token)
            .filter(x -> x.getNumeroMesa() == mesa)
            .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado"));
        return estado(s);
    }

    @Transactional(readOnly = true)
    public List<SolicitudVista> pendientes() {
        List<EstadoPedido> abiertos = List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);
        return dao.findByEstadoAndCreadoEnAfterOrderByCreadoEnAsc(EstadoSolicitud.PENDIENTE, LocalDateTime.now().minus(VIGENCIA))
            .stream().map(s -> new SolicitudVista(s.getId(), s.getNumeroMesa(), s.getNombreCliente(), s.getNota(), s.getTotal(),
                s.getCreadoEn(), items(s), pedidoAbierto(s.getNumeroMesa(), abiertos))).toList();
    }

    @Transactional
    public Pedido aprobar(Long id, boolean agregarAlAbierto, String usuario) {
        SolicitudPedido s = pendiente(id);
        List<PedidoRequestDTO.DetalleRequestDTO> detalles = new ArrayList<>();
        for (SolicitudPedidoItem i : s.getItems()) {
            PedidoRequestDTO.DetalleRequestDTO d = new PedidoRequestDTO.DetalleRequestDTO();
            d.setPlatoId(i.getPlatoId());
            d.setPresentacionId(i.getPresentacionId());
            d.setCantidad(i.getCantidad());
            d.setNotas(i.getNotas());
            detalles.add(d);
        }
        Long abierto = agregarAlAbierto ? pedidoAbierto(s.getNumeroMesa(),
            List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO)) : null;
        Pedido pedido;
        if (abierto != null) {
            pedido = comandas.agregar(abierto, detalles);
        } else {
            PedidoRequestDTO dto = new PedidoRequestDTO();
            dto.setTipo("MESA");
            dto.setNumeroMesa(s.getNumeroMesa());
            dto.setNombreCliente(s.getNombreCliente());
            dto.setObservaciones(s.getNota());
            dto.setDetalles(detalles);
            pedido = comandas.crear(dto);
        }
        s.setEstado(EstadoSolicitud.APROBADA);
        s.setPedidoId(pedido.getId());
        s.setRevisadoEn(LocalDateTime.now());
        s.setRevisadoPor(AuditoriaService.recortar(usuario, 60));
        dao.save(s);
        auditoria.registrar(AccionAuditoria.PEDIDO_QR_APROBADO, "SolicitudPedido", s.getId(),
            () -> configuracion.vista().getPuntoAtencionSingular() + " " + s.getNumeroMesa() + " · Pedido #" + pedido.getId()
                + (abierto != null ? " (agregado al pedido abierto)" : ""));
        avisarRevision(s);
        return pedido;
    }

    @Transactional
    public void rechazar(Long id, String motivo, String usuario) {
        SolicitudPedido s = pendiente(id);
        String limpio = texto(motivo, 200, "El motivo");
        s.setEstado(EstadoSolicitud.RECHAZADA);
        s.setMotivoRechazo(limpio);
        s.setRevisadoEn(LocalDateTime.now());
        s.setRevisadoPor(AuditoriaService.recortar(usuario, 60));
        dao.save(s);
        auditoria.registrar(AccionAuditoria.PEDIDO_QR_RECHAZADO, "SolicitudPedido", s.getId(),
            () -> configuracion.vista().getPuntoAtencionSingular() + " " + s.getNumeroMesa()
                + (limpio == null ? "" : " · " + limpio));
        avisarRevision(s);
    }

    private SolicitudPedido pendiente(Long id) {
        SolicitudPedido s = dao.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Pedido de la carta no encontrado"));
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) throw new IllegalStateException("Este pedido ya fue revisado");
        return s;
    }

    private Long pedidoAbierto(Integer mesa, List<EstadoPedido> estados) {
        List<Pedido> abiertos = pedidos.findByNumeroMesaAndPagadoFalseAndEstadoInOrderByFechaCreacionAsc(mesa, estados);
        return abiertos.isEmpty() ? null : abiertos.get(abiertos.size() - 1).getId();
    }

    private void avisarRevision(SolicitudPedido s) {
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("tipo", "PEDIDO_QR_REVISADO");
        aviso.put("solicitudId", s.getId());
        aviso.put("estado", s.getEstado().name());
        TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", (Object) aviso));
    }

    private SolicitudPedidoItem item(ItemNuevo i) {
        if (i == null || i.platoId() == null) throw new IllegalArgumentException("Cada producto debe indicar qué plato es");
        if (i.cantidad() == null || i.cantidad() < 1 || i.cantidad() > PedidoServiceImpl.CANTIDAD_MAXIMA)
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + PedidoServiceImpl.CANTIDAD_MAXIMA);
        Plato plato = platos.findById(i.platoId())
            .filter(p -> Boolean.TRUE.equals(p.getDisponible()) && !Boolean.TRUE.equals(p.getArchivado()))
            .orElseThrow(() -> new IllegalArgumentException("Un producto ya no está disponible. Actualiza la carta."));
        if (!DisponibilidadService.vendible(plato))
            throw new IllegalArgumentException(plato.getNombre() + " está agotado. Quítalo de tu pedido.");
        PresentacionPlato pres = null;
        if (i.presentacionId() != null) {
            pres = presentaciones.findById(i.presentacionId())
                .filter(pr -> pr.getPlato().getId().equals(plato.getId()) && Boolean.TRUE.equals(pr.getDisponible())
                    && (pr.getStockDisponible() == null || pr.getStockDisponible() > 0)
                    && pr.getModalidad() != Modalidad.LLEVAR)
                .orElseThrow(() -> new IllegalArgumentException("El tamaño elegido de " + plato.getNombre() + " no está disponible"));
        } else if (plato.getPresentaciones() != null && plato.getPresentaciones().stream()
                .anyMatch(pr -> pr.getModalidad() != Modalidad.LLEVAR)) {
            throw new IllegalArgumentException("Elige un tamaño para " + plato.getNombre());
        }
        String notas = texto(i.notas(), PedidoServiceImpl.NOTA_MAXIMA, "La nota");
        double precio = pres != null ? pres.getPrecio() : plato.getPrecio();
        SolicitudPedidoItem item = new SolicitudPedidoItem();
        item.setPlatoId(plato.getId());
        item.setPresentacionId(pres == null ? null : pres.getId());
        item.setCantidad(i.cantidad());
        item.setNotas(notas);
        item.setNombre(pres == null ? plato.getNombre() : plato.getNombre() + " – " + pres.getNombre());
        item.setPrecioUnitario(precio);
        item.setSubtotal(redondear(precio * i.cantidad()));
        return item;
    }

    private EstadoCliente estado(SolicitudPedido s) {
        return new EstadoCliente(s.getToken(), s.getEstado().name(), s.getTotal(), items(s), s.getCreadoEn(), s.getMotivoRechazo());
    }

    private static List<ItemVista> items(SolicitudPedido s) {
        return s.getItems().stream().map(i -> new ItemVista(i.getNombre(), i.getCantidad(), i.getPrecioUnitario(),
            i.getSubtotal(), i.getNotas())).toList();
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String texto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.length() > max) throw new IllegalArgumentException(campo + " admite hasta " + max + " caracteres");
        return limpio;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
