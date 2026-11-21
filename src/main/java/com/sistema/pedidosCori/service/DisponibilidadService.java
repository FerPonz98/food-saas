package com.sistema.pedidosCori.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IMovimientoDisponibilidadDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.dto.StockUpdateDTO;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.MovimientoDisponibilidad;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;
import com.sistema.pedidosCori.models.entity.TipoDisponibilidad;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {
    public static final String PERMISO = "PERM_DISPONIBILIDAD";
    public static final int STOCK_MAXIMO = 9999;
    public static final int POCAS_UNIDADES = 3;
    static final int MOTIVO_MAXIMO = 200;

    private final IPlatoDao platoDao;
    private final IPresentacionPlatoDao presentacionDao;
    private final IMovimientoDisponibilidadDao movimientoDao;
    private final SimpMessagingTemplate mensajes;

    public record PresentacionPanel(Long id, String nombre, Integer stock, boolean disponible) {
    }

    public record ProductoPanel(Long id, String nombre, String categoria, String tipo, Integer stock, boolean pausado,
            String estado, boolean vendible, String imagenUrl, List<PresentacionPanel> presentaciones) {
    }

    public record MovimientoVista(Long id, Long platoId, String producto, String tipo, Integer cantidadAntes,
            Integer cantidadDespues, String motivo, String usuario, Long pedidoId, LocalDateTime fecha) {
    }

    public static boolean vendible(Plato p) {
        if (p == null || !Boolean.TRUE.equals(p.getDisponible()) || Boolean.TRUE.equals(p.getArchivado())) return false;
        if (Boolean.TRUE.equals(p.getPausado())) return false;
        return p.getStockDisponible() == null || p.getStockDisponible() > 0;
    }

    public static String estado(Plato p) {
        if (!Boolean.TRUE.equals(p.getDisponible()) || Boolean.TRUE.equals(p.getArchivado())) return "FUERA_DE_MENU";
        if (Boolean.TRUE.equals(p.getPausado())) return p.getTipoDisponibilidad() == TipoDisponibilidad.MANUAL ? "AGOTADO" : "PAUSADO";
        if (p.getStockDisponible() != null && p.getStockDisponible() <= 0) return "AGOTADO";
        if (p.getStockDisponible() != null && p.getStockDisponible() <= POCAS_UNIDADES) return "POCAS";
        return "DISPONIBLE";
    }

    private static String motivoNoVendible(Plato p) {
        if (!Boolean.TRUE.equals(p.getDisponible()) || Boolean.TRUE.equals(p.getArchivado()))
            return p.getNombre() + " no está a la venta hoy";
        if (Boolean.TRUE.equals(p.getPausado()) && p.getTipoDisponibilidad() != TipoDisponibilidad.MANUAL)
            return p.getNombre() + " está pausado por cocina";
        return p.getNombre() + " está agotado";
    }

    @Transactional
    public void descontar(List<DetallePedido> detalles) {
        Map<Long, Integer> porPlato = new LinkedHashMap<>();
        Map<Long, Integer> porPresentacion = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (DetallePedido d : detalles) {
            int q = d.getCantidad() == null ? 0 : d.getCantidad();
            Plato plato = d.getPlato();
            if (!vendible(plato)) throw new IllegalStateException(motivoNoVendible(plato));
            if (plato.getStockDisponible() != null) {
                porPlato.merge(plato.getId(), q, Integer::sum);
                nombres.put(plato.getId(), plato.getNombre());
                d.setStockDescontado(true);
            }
            PresentacionPlato pres = d.getPresentacion();
            if (pres != null) {
                if (!Boolean.TRUE.equals(pres.getDisponible()) || (pres.getStockDisponible() != null && pres.getStockDisponible() <= 0))
                    throw new IllegalStateException(plato.getNombre() + " – " + pres.getNombre() + " está agotado");
                if (pres.getStockDisponible() != null) {
                    porPresentacion.merge(pres.getId(), q, Integer::sum);
                    d.setPresentacionDescontada(true);
                }
            }
            Plato sopa = d.getAcompananteSopa();
            if (sopa != null) {
                if (!vendible(sopa)) throw new IllegalStateException(motivoNoVendible(sopa));
                if (sopa.getStockDisponible() != null) {
                    porPlato.merge(sopa.getId(), q, Integer::sum);
                    nombres.put(sopa.getId(), sopa.getNombre());
                    d.setSopaDescontada(true);
                }
            }
        }
        List<StockUpdateDTO> avisos = new ArrayList<>();
        porPlato.forEach((id, q) -> {
            if (platoDao.descontarStockPreciso(id, q) == 0) {
                Integer quedan = platoDao.stockActual(id);
                throw new IllegalStateException(sinStock(nombres.get(id), quedan, q));
            }
            avisos.add(StockUpdateDTO.builder().platoId(id).stockRestante(platoDao.stockActual(id)).nombrePlato(nombres.get(id)).build());
        });
        porPresentacion.forEach((id, q) -> {
            PresentacionPlato pres = presentacionDao.findById(id).orElseThrow();
            String nombre = pres.getPlato().getNombre() + " – " + pres.getNombre();
            if (presentacionDao.descontarStockPreciso(id, q) == 0)
                throw new IllegalStateException(sinStock(nombre, presentacionDao.stockActual(id), q));
            avisos.add(StockUpdateDTO.builder().platoId(pres.getPlato().getId()).presentacionId(id)
                .stockRestante(presentacionDao.stockActual(id)).nombrePlato(nombre).build());
        });
        avisos.forEach(this::avisarStock);
    }

    private static String sinStock(String nombre, Integer quedan, int pedidas) {
        if (quedan == null || quedan <= 0) return nombre + " se agotó. Quita ese producto del pedido.";
        return "Solo quedan " + quedan + " de " + nombre + " (pediste " + pedidas + ").";
    }

    @Transactional
    public void reponer(Pedido pedido, String usuario, String motivo) {
        Map<Long, Integer> porPlato = new LinkedHashMap<>();
        Map<Long, Integer> porPresentacion = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (DetallePedido d : pedido.getDetalles()) {
            int q = d.getCantidad() == null ? 0 : d.getCantidad();
            if (Boolean.TRUE.equals(d.getStockDescontado())) {
                porPlato.merge(d.getPlato().getId(), q, Integer::sum);
                nombres.put(d.getPlato().getId(), d.getPlato().getNombre());
                d.setStockDescontado(false);
            }
            if (Boolean.TRUE.equals(d.getPresentacionDescontada()) && d.getPresentacion() != null) {
                porPresentacion.merge(d.getPresentacion().getId(), q, Integer::sum);
                d.setPresentacionDescontada(false);
            }
            if (Boolean.TRUE.equals(d.getSopaDescontada()) && d.getAcompananteSopa() != null) {
                porPlato.merge(d.getAcompananteSopa().getId(), q, Integer::sum);
                nombres.put(d.getAcompananteSopa().getId(), d.getAcompananteSopa().getNombre());
                d.setSopaDescontada(false);
            }
        }
        String texto = "Pedido #" + pedido.getId() + " anulado" + (motivo == null ? "" : ": " + motivo);
        porPlato.forEach((id, q) -> {
            Integer antes = platoDao.stockActual(id);
            if (antes == null || platoDao.reponerStock(id, q) == 0) return;
            Integer despues = platoDao.stockActual(id);
            registrar(id, null, nombres.get(id), MovimientoDisponibilidad.Tipo.ANULACION, antes, despues, texto, usuario, pedido.getId());
            avisarStock(StockUpdateDTO.builder().platoId(id).stockRestante(despues).nombrePlato(nombres.get(id)).build());
        });
        porPresentacion.forEach((id, q) -> presentacionDao.findById(id).ifPresent(pres -> {
            Integer antes = presentacionDao.stockActual(id);
            if (antes == null || presentacionDao.reponerStock(id, q) == 0) return;
            Integer despues = presentacionDao.stockActual(id);
            String nombre = pres.getPlato().getNombre() + " – " + pres.getNombre();
            registrar(pres.getPlato().getId(), id, nombre, MovimientoDisponibilidad.Tipo.ANULACION, antes, despues, texto, usuario, pedido.getId());
            avisarStock(StockUpdateDTO.builder().platoId(pres.getPlato().getId()).presentacionId(id).stockRestante(despues).nombrePlato(nombre).build());
        }));
    }

    @Transactional(readOnly = true)
    public List<ProductoPanel> panel() {
        return platoDao.findByDisponibleTrueAndArchivadoFalse().stream()
            .sorted(Comparator.comparing((Plato p) -> Objects.toString(p.getCategoria(), ""))
                .thenComparing(p -> p.getNombre().toLowerCase(java.util.Locale.ROOT)))
            .map(DisponibilidadService::vista).toList();
    }

    static ProductoPanel vista(Plato p) {
        List<PresentacionPanel> pres = (p.getPresentaciones() == null ? List.<PresentacionPlato>of() : p.getPresentaciones()).stream()
            .sorted(Comparator.comparing(PresentacionPlato::getPrecio, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(pr -> new PresentacionPanel(pr.getId(), pr.getNombre(), pr.getStockDisponible(), Boolean.TRUE.equals(pr.getDisponible())))
            .toList();
        return new ProductoPanel(p.getId(), p.getNombre(), p.getCategoria(), p.getTipoDisponibilidad().name(), p.getStockDisponible(),
            Boolean.TRUE.equals(p.getPausado()), estado(p), vendible(p), p.getImagenUrl(), pres);
    }

    @Transactional(readOnly = true)
    public List<MovimientoVista> movimientosDeHoy() {
        return movimientoDao.findTop200ByFechaGreaterThanEqualOrderByFechaDesc(LocalDate.now().atStartOfDay()).stream()
            .map(m -> new MovimientoVista(m.getId(), m.getPlatoId(), m.getProducto(), m.getTipo().name(), m.getCantidadAntes(),
                m.getCantidadDespues(), m.getMotivo(), m.getUsuario(), m.getPedidoId(), m.getFecha()))
            .toList();
    }

    @Transactional
    public ProductoPanel ajustar(Long platoId, Integer delta, Integer cantidad, String motivo, String usuario) {
        Plato p = bloquear(platoId);
        if (p.getTipoDisponibilidad() != TipoDisponibilidad.LIMITADA || p.getStockDisponible() == null)
            throw new IllegalStateException(p.getNombre() + " no se controla por cantidad. Cambia su modalidad a «Cantidad limitada».");
        int antes = p.getStockDisponible();
        int despues = nuevaCantidad(antes, delta, cantidad);
        if (despues == antes) return vista(p);
        p.setStockDisponible(despues);
        platoDao.save(p);
        registrar(p.getId(), null, p.getNombre(), MovimientoDisponibilidad.Tipo.AJUSTE, antes, despues, motivoValido(motivo), usuario, null);
        avisarStock(StockUpdateDTO.builder().platoId(p.getId()).stockRestante(despues).nombrePlato(p.getNombre()).build());
        return vista(p);
    }

    @Transactional
    public ProductoPanel ajustarPresentacion(Long presentacionId, Integer delta, Integer cantidad, String motivo, String usuario) {
        PresentacionPlato pres = presentacionDao.findByIdForUpdate(presentacionId)
            .filter(pr -> !Boolean.TRUE.equals(pr.getPlato().getArchivado()))
            .orElseThrow(() -> new NoSuchElementException("Tamaño no encontrado"));
        if (pres.getStockDisponible() == null)
            throw new IllegalStateException("Este tamaño no tiene cantidad limitada");
        int antes = pres.getStockDisponible();
        int despues = nuevaCantidad(antes, delta, cantidad);
        String nombre = pres.getPlato().getNombre() + " – " + pres.getNombre();
        if (despues != antes) {
            pres.setStockDisponible(despues);
            presentacionDao.save(pres);
            registrar(pres.getPlato().getId(), pres.getId(), nombre, MovimientoDisponibilidad.Tipo.AJUSTE, antes, despues,
                motivoValido(motivo), usuario, null);
            avisarStock(StockUpdateDTO.builder().platoId(pres.getPlato().getId()).presentacionId(pres.getId())
                .stockRestante(despues).nombrePlato(nombre).build());
        }
        return vista(pres.getPlato());
    }

    @Transactional
    public ProductoPanel pausar(Long platoId, boolean pausado, String motivo, String usuario) {
        Plato p = bloquear(platoId);
        if (Boolean.TRUE.equals(p.getPausado()) == pausado) return vista(p);
        p.setPausado(pausado);
        platoDao.save(p);
        registrar(p.getId(), null, p.getNombre(), pausado ? MovimientoDisponibilidad.Tipo.PAUSA : MovimientoDisponibilidad.Tipo.REACTIVACION,
            p.getStockDisponible(), p.getStockDisponible(), motivoValido(motivo), usuario, null);
        avisarMenu();
        return vista(p);
    }

    @Transactional
    public ProductoPanel cambiarModalidad(Long platoId, String tipo, Integer cantidad, String motivo, String usuario) {
        Plato p = bloquear(platoId);
        TipoDisponibilidad nuevo;
        try {
            nuevo = TipoDisponibilidad.valueOf(tipo == null ? "" : tipo.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Modalidad no válida");
        }
        Integer antes = p.getStockDisponible();
        Integer despues;
        if (nuevo == TipoDisponibilidad.LIMITADA) {
            int base = cantidad != null ? cantidad : (antes != null ? antes : -1);
            if (base < 0) throw new IllegalArgumentException("Indica cuántas unidades hay");
            despues = nuevaCantidad(0, null, base);
        } else {
            despues = null;
        }
        if (nuevo == p.getTipoDisponibilidad() && Objects.equals(antes, despues)) return vista(p);
        TipoDisponibilidad anterior = p.getTipoDisponibilidad();
        p.setTipoDisponibilidad(nuevo);
        p.setStockDisponible(despues);
        if (nuevo != TipoDisponibilidad.MANUAL && anterior == TipoDisponibilidad.MANUAL) p.setPausado(false);
        platoDao.save(p);
        String detalle = anterior.getEtiqueta() + " → " + nuevo.getEtiqueta();
        String m = motivoValido(motivo);
        registrar(p.getId(), null, p.getNombre(), MovimientoDisponibilidad.Tipo.MODALIDAD, antes, despues,
            m == null ? detalle : detalle + " · " + m, usuario, null);
        avisarStock(StockUpdateDTO.builder().platoId(p.getId()).stockRestante(despues).nombrePlato(p.getNombre()).build());
        avisarMenu();
        return vista(p);
    }

    public static TipoDisponibilidad tipoParaCantidad(Plato p, Integer cantidad) {
        if (cantidad != null) return TipoDisponibilidad.LIMITADA;
        return p.getTipoDisponibilidad() == TipoDisponibilidad.MANUAL ? TipoDisponibilidad.MANUAL : TipoDisponibilidad.CONTINUA;
    }

    private Plato bloquear(Long platoId) {
        return platoDao.findByIdForUpdate(platoId)
            .filter(p -> !Boolean.TRUE.equals(p.getArchivado()))
            .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
    }

    private static int nuevaCantidad(int antes, Integer delta, Integer cantidad) {
        long resultado;
        if (cantidad != null) resultado = cantidad;
        else if (delta != null) resultado = (long) antes + delta;
        else throw new IllegalArgumentException("Indica la cantidad o cuánto sumar o restar");
        if (resultado < 0) throw new IllegalArgumentException("La cantidad no puede quedar en negativo");
        if (resultado > STOCK_MAXIMO) throw new IllegalArgumentException("La cantidad máxima es " + STOCK_MAXIMO);
        return (int) resultado;
    }

    private static String motivoValido(String motivo) {
        if (motivo == null || motivo.isBlank()) return null;
        String limpio = motivo.trim().replaceAll("\\s+", " ");
        if (limpio.length() > MOTIVO_MAXIMO) throw new IllegalArgumentException("El motivo admite hasta " + MOTIVO_MAXIMO + " caracteres");
        return limpio;
    }

    private void registrar(Long platoId, Long presentacionId, String producto, MovimientoDisponibilidad.Tipo tipo,
            Integer antes, Integer despues, String motivo, String usuario, Long pedidoId) {
        MovimientoDisponibilidad m = new MovimientoDisponibilidad();
        m.setPlatoId(platoId);
        m.setPresentacionId(presentacionId);
        m.setProducto(AuditoriaService.recortar(producto, 255));
        m.setTipo(tipo);
        m.setCantidadAntes(antes);
        m.setCantidadDespues(despues);
        m.setMotivo(motivo);
        m.setUsuario(AuditoriaService.recortar(usuario, 60));
        m.setPedidoId(pedidoId);
        m.setFecha(LocalDateTime.now());
        movimientoDao.save(m);
    }

    private void avisarStock(StockUpdateDTO aviso) {
        TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/stock", aviso));
    }

    private void avisarMenu() {
        NotificacionWsDTO aviso = NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje("Cocina actualizó la disponibilidad").build();
        TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/menu", aviso));
    }
}
