package com.sistema.pedidosCori.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPagoParcialDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.ModoPago;
import com.sistema.pedidosCori.models.entity.PagoParcial;
import com.sistema.pedidosCori.models.entity.PagoParcialItem;
import com.sistema.pedidosCori.models.entity.Pedido;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DivisionCuentaService {
    public static final String METODO_DIVIDIDO = "DIVIDIDO";
    public static final int MAX_PARTES = 50;
    private static final double CENTAVO = 0.005;

    private final IPedidoDao pedidos;
    private final IPagoParcialDao pagos;
    private final AuditoriaService auditoria;
    private final ComandaService comandas;
    private final ConfiguracionNegocioService configuracion;
    private final SimpMessagingTemplate mensajes;

    public record ItemPago(Long detalleId, Integer cantidad) {
    }

    public record NuevoPago(String modo, String metodoPago, String persona, Integer partes, Double monto, List<ItemPago> items) {
    }

    public record Linea(Long detalleId, String nombre, Integer cantidad, Integer cantidadPagada, Double precioUnitario,
            Double subtotal) {
    }

    public record PagoVista(Long id, Double monto, String metodoPago, String persona, String modo, String detalle,
            String cobradoPor, LocalDateTime fecha) {
    }

    public record Cuenta(Long pedidoId, String etiqueta, Double total, Double abonado, Double saldo, Boolean pagado,
            List<Linea> lineas, List<PagoVista> pagos) {
    }

    @Transactional(readOnly = true)
    public Cuenta cuenta(Long pedidoId) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + pedidoId));
        return cuenta(p, pagos.findByPedidoIdOrderByFechaAscIdAsc(pedidoId));
    }

    @Transactional
    public Cuenta pagar(Long pedidoId, NuevoPago r) {
        if (r == null) throw new IllegalArgumentException("Indica cómo se paga esta parte");
        Pedido p = pedidos.findByIdForUpdate(pedidoId).orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + pedidoId));
        if (Boolean.TRUE.equals(p.getPagado())) throw new IllegalStateException("El pedido ya está pagado");
        List<PagoParcial> anteriores = new ArrayList<>(pagos.findByPedidoIdOrderByFechaAscIdAsc(pedidoId));
        double total = redondear(p.getTotal() == null ? 0 : p.getTotal());
        double saldo = redondear(total - abonado(anteriores));
        if (saldo <= CENTAVO) throw new IllegalStateException("Esta cuenta no tiene saldo pendiente");
        ModoPago modo = modo(r.modo());
        String metodo = metodo(r.metodoPago());
        String persona = r.persona() == null || r.persona().isBlank() ? null : r.persona().trim();
        if (persona != null && persona.length() > 60) throw new IllegalArgumentException("El nombre de la persona admite hasta 60 caracteres");

        PagoParcial pago = new PagoParcial();
        pago.setPedidoId(pedidoId);
        pago.setModo(modo);
        pago.setMetodoPago(metodo);
        pago.setPersona(persona);
        pago.setCobradoPor(UsuarioActual.nombre().orElse(null));
        pago.setFecha(LocalDateTime.now());
        double monto;
        String detalle;
        switch (modo) {
            case PRODUCTOS -> {
                Map<Long, Integer> pagadas = unidadesPagadas(anteriores);
                Map<Long, DetallePedido> lineas = new HashMap<>();
                p.getDetalles().forEach(d -> lineas.put(d.getId(), d));
                if (r.items() == null || r.items().isEmpty()) throw new IllegalArgumentException("Elige los productos que paga esta persona");
                double suma = 0;
                List<String> nombres = new ArrayList<>();
                Map<Long, Integer> elegidas = new HashMap<>();
                for (ItemPago i : r.items()) {
                    if (i == null || i.detalleId() == null || i.cantidad() == null || i.cantidad() < 1)
                        throw new IllegalArgumentException("Cantidad no válida");
                    elegidas.merge(i.detalleId(), i.cantidad(), Integer::sum);
                }
                for (Map.Entry<Long, Integer> e : elegidas.entrySet()) {
                    DetallePedido d = lineas.get(e.getKey());
                    if (d == null) throw new IllegalArgumentException("Un producto elegido no pertenece a este pedido");
                    int libres = d.getCantidad() - pagadas.getOrDefault(d.getId(), 0);
                    if (e.getValue() > libres)
                        throw new IllegalStateException("Solo quedan " + libres + " sin pagar de " + nombre(d));
                    PagoParcialItem item = new PagoParcialItem();
                    item.setPago(pago);
                    item.setDetallePedidoId(d.getId());
                    item.setCantidad(e.getValue());
                    pago.getItems().add(item);
                    suma += d.getPrecioUnitario() * e.getValue();
                    nombres.add(e.getValue() + " × " + nombre(d));
                }
                monto = Math.min(redondear(suma), saldo);
                detalle = String.join(", ", nombres);
            }
            case PARTES -> {
                if (r.partes() == null || r.partes() < 2 || r.partes() > MAX_PARTES)
                    throw new IllegalArgumentException("Divide la cuenta entre 2 y " + MAX_PARTES + " personas");
                double parte = redondear(total / r.partes());
                monto = saldo - parte < r.partes() * 0.01 ? saldo : parte;
                detalle = "1 de " + r.partes() + " partes iguales";
            }
            default -> {
                if (r.monto() == null || r.monto() <= 0) throw new IllegalArgumentException("Escribe un monto mayor que cero");
                monto = redondear(r.monto());
                if (monto - saldo > CENTAVO) throw new IllegalArgumentException("El monto supera el saldo pendiente");
                detalle = "Monto libre";
            }
        }
        if (monto <= 0) throw new IllegalArgumentException("El monto a cobrar debe ser mayor que cero");
        pago.setMonto(redondear(monto));
        pago.setDetalle(AuditoriaService.recortar(detalle, 500));
        pagos.save(pago);
        anteriores.add(pago);

        double nuevoAbonado = abonado(anteriores);
        p.setAbonado(nuevoAbonado);
        boolean completo = total - nuevoAbonado <= CENTAVO;
        if (completo) {
            Set<String> metodos = new LinkedHashSet<>();
            anteriores.forEach(x -> metodos.add(x.getMetodoPago()));
            p.setPagado(true);
            p.setMetodoPago(metodos.size() == 1 ? metodos.iterator().next() : METODO_DIVIDIDO);
            p.setCobradoPor(UsuarioActual.nombre().orElse(null));
            p.setFechaPago(LocalDateTime.now());
        }
        pedidos.save(p);
        String etiqueta = comandas.etiqueta(p);
        String simbolo = configuracion.vista().getSimboloMoneda();
        auditoria.registrar(AccionAuditoria.PAGO_PARCIAL_REGISTRADO, "Pedido", p.getId(),
            () -> etiqueta + " · " + (persona == null ? "" : persona + " · ") + metodo + " · " + simbolo + " "
                + String.format(Locale.ROOT, "%.2f", pago.getMonto()) + " · " + pago.getDetalle());
        if (completo)
            auditoria.registrar(AccionAuditoria.PEDIDO_COBRADO, "Pedido", p.getId(),
                () -> etiqueta + " · " + p.getMetodoPago() + " · " + simbolo + " " + String.format(Locale.ROOT, "%.2f", total)
                    + " · " + anteriores.size() + " pagos");
        NotificacionWsDTO aviso = NotificacionWsDTO.builder().pedidoId(p.getId()).estado(p.getEstado())
            .tipo("ACTUALIZACION_PAGO").mensaje((completo ? "Pago completo: " : "Pago parcial: ") + etiqueta).build();
        TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", aviso));
        return cuenta(p, anteriores);
    }

    private Cuenta cuenta(Pedido p, List<PagoParcial> lista) {
        Map<Long, Integer> pagadas = unidadesPagadas(lista);
        List<Linea> lineas = p.getDetalles().stream().map(d -> new Linea(d.getId(), nombre(d), d.getCantidad(),
            pagadas.getOrDefault(d.getId(), 0), d.getPrecioUnitario(), d.getSubtotal())).toList();
        double total = redondear(p.getTotal() == null ? 0 : p.getTotal());
        double abonado = Boolean.TRUE.equals(p.getPagado()) && lista.isEmpty() ? total : abonado(lista);
        List<PagoVista> vistas = lista.stream().map(x -> new PagoVista(x.getId(), x.getMonto(), x.getMetodoPago(),
            x.getPersona(), x.getModo().name(), x.getDetalle(), x.getCobradoPor(), x.getFecha())).toList();
        return new Cuenta(p.getId(), comandas.etiqueta(p), total, abonado, redondear(Math.max(0, total - abonado)),
            p.getPagado(), lineas, vistas);
    }

    private static Map<Long, Integer> unidadesPagadas(List<PagoParcial> lista) {
        Map<Long, Integer> pagadas = new HashMap<>();
        for (PagoParcial x : lista)
            for (PagoParcialItem i : x.getItems()) pagadas.merge(i.getDetallePedidoId(), i.getCantidad(), Integer::sum);
        return pagadas;
    }

    private static double abonado(List<PagoParcial> lista) {
        return redondear(lista.stream().mapToDouble(x -> x.getMonto() == null ? 0 : x.getMonto()).sum());
    }

    private static String nombre(DetallePedido d) {
        String base = d.getPlato() == null ? "Producto" : d.getPlato().getNombre();
        return d.getPresentacion() == null ? base : base + " – " + d.getPresentacion().getNombre();
    }

    private static ModoPago modo(String valor) {
        try {
            return ModoPago.valueOf(valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Elige si se divide por productos, por partes iguales o por monto");
        }
    }

    private static String metodo(String valor) {
        String m = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (m.isEmpty() || m.length() > 20) throw new IllegalArgumentException("Elige el método de pago");
        return m;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
