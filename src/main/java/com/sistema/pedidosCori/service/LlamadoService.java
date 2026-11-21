package com.sistema.pedidosCori.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.ILlamadoMesaDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.LlamadoMesa;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class LlamadoService {
    public static final Set<String> MOTIVOS = Set.of(LlamadoMesa.MOTIVO_MESERO, LlamadoMesa.MOTIVO_CUENTA);
    public static final Set<String> METODOS_PAGO = Set.of("EFECTIVO", "TARJETA", "QR");
    static final Duration VIGENCIA = Duration.ofHours(3);
    static final Duration ESPERA_RECORDATORIO = Duration.ofSeconds(45);
    static final int MAX_POR_IP = 8;
    static final Duration VENTANA_IP = Duration.ofMinutes(5);

    private final ILlamadoMesaDao dao;
    private final SimpMessagingTemplate mensajes;
    private final CartaPublicaService carta;
    private final ConfiguracionNegocioService configuracion;
    private final AuditoriaService auditoria;
    private final Map<String, Deque<LocalDateTime>> porIp = new ConcurrentHashMap<>();

    public record LlamadoVista(Long id, Integer numeroMesa, String motivo, String metodoPago,
            LocalDateTime creadoEn, LocalDateTime ultimoAvisoEn, LocalDateTime confirmadoEn, String confirmadoPor) {
        static LlamadoVista de(LlamadoMesa l) {
            return new LlamadoVista(l.getId(), l.getNumeroMesa(), l.getMotivo(), l.getMetodoPago(),
                l.getCreadoEn(), l.getUltimoAvisoEn(), l.getConfirmadoEn(), l.getConfirmadoPor());
        }
    }
    public record EstadoCliente(String motivo, String metodoPago, LocalDateTime desde, boolean confirmado, long esperaSegundos) {
        static EstadoCliente de(LlamadoMesa l) {
            long espera = Math.max(0, Duration.between(l.getCreadoEn(), LocalDateTime.now()).getSeconds());
            return new EstadoCliente(l.getMotivo(), l.getMetodoPago(), l.getCreadoEn(), l.getConfirmadoEn() != null, espera);
        }
    }

    @Transactional
    public EstadoCliente llamar(int mesa, String motivo, String metodoPago, String ip) {
        carta.validarMesa(mesa);
        String m = motivo == null ? "" : motivo.trim().toUpperCase(java.util.Locale.ROOT);
        if (!MOTIVOS.contains(m)) throw new IllegalArgumentException("Motivo no válido");
        String metodo = null;
        if (LlamadoMesa.MOTIVO_CUENTA.equals(m) && metodoPago != null && !metodoPago.isBlank()) {
            metodo = metodoPago.trim().toUpperCase(java.util.Locale.ROOT);
            if (!METODOS_PAGO.contains(metodo)) throw new IllegalArgumentException("Forma de pago no válida");
        }
        LocalDateTime ahora = LocalDateTime.now();
        LlamadoMesa l = dao.findFirstByNumeroMesaAndMotivoAndAtendidoEnIsNullAndCreadoEnAfter(mesa, m, ahora.minus(VIGENCIA))
            .orElse(null);
        boolean avisar;
        boolean recordatorio = false;
        if (l == null) {
            controlarIp(ip, ahora);
            l = new LlamadoMesa();
            l.setNumeroMesa(mesa);
            l.setMotivo(m);
            l.setMetodoPago(metodo);
            l.setCreadoEn(ahora);
            l.setUltimoAvisoEn(ahora);
            avisar = true;
        } else {
            boolean cambioPago = metodo != null && !metodo.equals(l.getMetodoPago());
            if (cambioPago) l.setMetodoPago(metodo);
            avisar = cambioPago || l.getUltimoAvisoEn().plus(ESPERA_RECORDATORIO).isBefore(ahora);
            if (avisar) {
                controlarIp(ip, ahora);
                l.setUltimoAvisoEn(ahora);
                recordatorio = !cambioPago;
            }
        }
        dao.save(l);
        if (avisar) {
            Map<String, Object> aviso = aviso("LLAMADO_MESA", l);
            aviso.put("recordatorio", recordatorio);
            aviso.put("mensaje", mensaje(l, recordatorio));
            TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", (Object) aviso));
        }
        return EstadoCliente.de(l);
    }

    @Transactional(readOnly = true)
    public List<EstadoCliente> estadoMesa(int mesa) {
        carta.validarMesa(mesa);
        return dao.findByNumeroMesaAndAtendidoEnIsNullAndCreadoEnAfterOrderByCreadoEnAsc(mesa, LocalDateTime.now().minus(VIGENCIA))
            .stream().map(EstadoCliente::de).toList();
    }

    @Transactional(readOnly = true)
    public List<LlamadoVista> pendientes() {
        return dao.findByAtendidoEnIsNullAndCreadoEnAfterOrderByCreadoEnAsc(LocalDateTime.now().minus(VIGENCIA))
            .stream().map(LlamadoVista::de).toList();
    }

    @Transactional
    public LlamadoVista confirmar(Long id, String usuario) {
        LlamadoMesa l = dao.findById(id).orElseThrow(() -> new NoSuchElementException("Aviso no encontrado"));
        if (l.getAtendidoEn() != null) throw new IllegalStateException("Este aviso ya fue atendido");
        if (l.getConfirmadoEn() == null) {
            l.setConfirmadoEn(LocalDateTime.now());
            l.setConfirmadoPor(AuditoriaService.recortar(usuario, 60));
            dao.save(l);
            auditoria.registrar(AccionAuditoria.LLAMADO_CONFIRMADO, "LlamadoMesa", l.getId(),
                () -> configuracion.vista().getPuntoAtencionSingular() + " " + l.getNumeroMesa() + " · "
                    + (LlamadoMesa.MOTIVO_CUENTA.equals(l.getMotivo()) ? "Cuenta" : "Llamado"));
            Map<String, Object> aviso = aviso("LLAMADO_CONFIRMADO", l);
            aviso.put("confirmadoPor", l.getConfirmadoPor());
            TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", (Object) aviso));
        }
        return LlamadoVista.de(l);
    }

    @Transactional
    public LlamadoVista atender(Long id, String usuario) {
        LlamadoMesa l = dao.findById(id).orElseThrow(() -> new NoSuchElementException("Aviso no encontrado"));
        if (l.getAtendidoEn() == null) {
            l.setAtendidoEn(LocalDateTime.now());
            l.setAtendidoPor(AuditoriaService.recortar(usuario, 60));
            dao.save(l);
            Map<String, Object> aviso = aviso("LLAMADO_ATENDIDO", l);
            aviso.put("atendidoPor", l.getAtendidoPor());
            TrasConfirmar.ejecutar(() -> mensajes.convertAndSend("/topic/mesero", (Object) aviso));
        }
        return LlamadoVista.de(l);
    }

    private void controlarIp(String ip, LocalDateTime ahora) {
        if (ip == null) return;
        Deque<LocalDateTime> marcas = porIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(ahora.minus(VENTANA_IP))) marcas.pollFirst();
            if (marcas.size() >= MAX_POR_IP)
                throw new LimiteExcedido("Ya enviamos varios avisos. Espera un momento, el personal ya fue notificado.");
            marcas.addLast(ahora);
        }
        if (porIp.size() > 5000) porIp.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(ahora.minus(VENTANA_IP));
            }
        });
    }

    private Map<String, Object> aviso(String tipo, LlamadoMesa l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("llamadoId", l.getId());
        m.put("numeroMesa", l.getNumeroMesa());
        m.put("motivo", l.getMotivo());
        m.put("metodoPago", l.getMetodoPago());
        m.put("creadoEn", l.getCreadoEn().toString());
        m.put("confirmadoEn", l.getConfirmadoEn() == null ? null : l.getConfirmadoEn().toString());
        m.put("confirmadoPor", l.getConfirmadoPor());
        return m;
    }

    private String mensaje(LlamadoMesa l, boolean recordatorio) {
        String mesa = configuracion.vista().getPuntoAtencionSingular() + " " + l.getNumeroMesa();
        String texto = LlamadoMesa.MOTIVO_CUENTA.equals(l.getMotivo())
            ? mesa + " pide la cuenta" + (l.getMetodoPago() == null ? "" : " (" + nombrePago(l.getMetodoPago()) + ")")
            : mesa + " llama al " + configuracion.vista().getEtiquetaRolMesero().toLowerCase(java.util.Locale.ROOT);
        return recordatorio ? texto + " — otra vez" : texto;
    }

    private static String nombrePago(String metodo) {
        return switch (metodo) {
            case "EFECTIVO" -> "efectivo";
            case "TARJETA" -> "tarjeta";
            case "QR" -> "pago QR";
            default -> metodo;
        };
    }

    public static class LimiteExcedido extends RuntimeException {
        public LimiteExcedido(String mensaje) { super(mensaje); }
    }
}
