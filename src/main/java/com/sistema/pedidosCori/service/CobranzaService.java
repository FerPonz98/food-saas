package com.sistema.pedidosCori.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sistema.pedidosCori.models.dao.IAvisoCobroDao;
import com.sistema.pedidosCori.models.dao.IFacturaDao;
import com.sistema.pedidosCori.models.dao.IModuloPrecioDao;
import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.dao.IParametrosCobroDao;
import com.sistema.pedidosCori.models.entity.AvisoCobro;
import com.sistema.pedidosCori.models.entity.CicloFacturacion;
import com.sistema.pedidosCori.models.entity.EstadoSuscripcion;
import com.sistema.pedidosCori.models.entity.Factura;
import com.sistema.pedidosCori.models.entity.FacturaLinea;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.models.entity.ModuloPrecio;
import com.sistema.pedidosCori.models.entity.Negocio;
import com.sistema.pedidosCori.models.entity.ParametrosCobro;
import com.sistema.pedidosCori.models.entity.Plan;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CobranzaService {
    public static final String SUSPENSION_POR_PAGO = "FALTA_DE_PAGO";
    public static final String SUSPENSION_MANUAL = "MANUAL";
    private static final Logger log = LoggerFactory.getLogger(CobranzaService.class);
    private static final Map<Modulo, Integer> PRECIOS_INICIALES = precios();

    private final IFacturaDao facturas;
    private final IAvisoCobroDao avisos;
    private final IModuloPrecioDao precios;
    private final IParametrosCobroDao parametros;
    private final INegocioDao negocios;
    private final EjecutorNegocio ejecutor;
    private final ModuloService modulos;
    private final AuditoriaService auditoria;

    public record Parametros(Integer diasGeneracion, Integer diasRecordatorio, Integer diasGracia,
            Boolean suspensionAutomatica, String instruccionesPago) {
    }

    public record PrecioModulo(String modulo, String etiqueta, String grupo, BigDecimal precioMensual, BigDecimal precioAnual,
            boolean seVendeAparte) {
    }

    public record DatosPrecio(BigDecimal precioMensual, BigDecimal precioAnual, Boolean seVendeAparte) {
    }

    public record LineaVista(String concepto, String modulo, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public record FacturaVista(Long id, Long negocioId, String negocioNombre, String numero, String planCodigo, String ciclo,
            LocalDate periodoDesde, LocalDate periodoHasta, LocalDateTime emitidaEn, LocalDate venceEn, BigDecimal total,
            String estado, String situacion, Long diasParaVencer, LocalDate bloqueoEl, LocalDateTime pagadaEn, String metodoPago,
            String referenciaPago, String registradaPor, String nota, List<LineaVista> lineas) {
    }

    public record AvisoVista(String tipo, String mensaje, LocalDateTime fecha, Long facturaId) {
    }

    public record EstadoCobro(String estadoSuscripcion, LocalDate pagadoHasta, String ciclo, FacturaVista pendiente,
            List<LineaVista> proximoCargo, BigDecimal proximoTotal, List<FacturaVista> facturas, List<AvisoVista> avisos,
            String instruccionesPago, String aviso, String nivelAviso) {
    }

    public record Pago(String metodo, String referencia, String nota) {
    }

    public record ResultadoCobranza(int facturasEmitidas, int avisosEnviados, int negociosSuspendidos) {
    }

    public record AvisoBanner(String texto, String nivel) {
    }

    private static Map<Modulo, Integer> precios() {
        Map<Modulo, Integer> m = new EnumMap<>(Modulo.class);
        m.put(Modulo.APARIENCIA_AVANZADA, 100);
        m.put(Modulo.APP_INSTALABLE, 100);
        m.put(Modulo.CARTA_QR, 200);
        m.put(Modulo.LLAMADOS, 100);
        m.put(Modulo.PEDIDOS_QR, 150);
        m.put(Modulo.DISPONIBILIDAD, 150);
        m.put(Modulo.DIVISION_CUENTA, 100);
        m.put(Modulo.FOTOS_ANIMADAS, 150);
        m.put(Modulo.CARTA_SIN_MARCA, 150);
        m.put(Modulo.REPORTES_COMPLETOS, 150);
        m.put(Modulo.REPORTES_AVANZADOS, 200);
        m.put(Modulo.AUDITORIA, 150);
        m.put(Modulo.VARIOS_ADMINISTRADORES, 100);
        return m;
    }

    public void inicializar() {
        ejecutor.en(NegocioActual.RAIZ, () -> {
            if (!parametros.existsById(ParametrosCobro.ID)) {
                ParametrosCobro p = new ParametrosCobro();
                p.setInstruccionesPago("Paga por transferencia o QR y envía el comprobante a soporte indicando el número de factura.");
                parametros.save(p);
            }
            for (Modulo m : Modulo.values()) {
                if (precios.existsById(m)) continue;
                ModuloPrecio p = new ModuloPrecio();
                p.setModulo(m);
                BigDecimal mensual = BigDecimal.valueOf(PRECIOS_INICIALES.getOrDefault(m, 100)).setScale(2);
                p.setPrecioMensual(mensual);
                p.setPrecioAnual(mensual.multiply(BigDecimal.TEN));
                precios.save(p);
            }
        });
    }

    public Parametros parametros() {
        return ejecutor.en(NegocioActual.RAIZ, () -> vista(parametrosActuales()));
    }

    public Parametros actualizarParametros(Parametros r) {
        if (r == null) throw new IllegalArgumentException("Datos requeridos");
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            ParametrosCobro p = parametrosActuales();
            p.setDiasGeneracion(dias(r.diasGeneracion(), "Los días de emisión"));
            p.setDiasRecordatorio(dias(r.diasRecordatorio(), "Los días de recordatorio"));
            p.setDiasGracia(dias(r.diasGracia(), "Los días de gracia"));
            p.setSuspensionAutomatica(!Boolean.FALSE.equals(r.suspensionAutomatica()));
            p.setInstruccionesPago(texto(r.instruccionesPago(), 1000, "Las instrucciones de pago"));
            parametros.save(p);
            auditoria.registrarPlataforma("COBRO_PARAMETROS", null, "Emisión " + p.getDiasGeneracion() + " días antes · Gracia "
                + p.getDiasGracia() + " días · Suspensión automática " + (p.getSuspensionAutomatica() ? "sí" : "no"));
            return vista(p);
        });
    }

    public List<PrecioModulo> preciosModulos() {
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            Map<Modulo, ModuloPrecio> mapa = mapaPrecios();
            List<PrecioModulo> lista = new ArrayList<>();
            for (Modulo m : Modulo.values()) {
                ModuloPrecio p = mapa.get(m);
                lista.add(new PrecioModulo(m.name(), m.getEtiqueta(), m.getGrupo(),
                    p == null ? BigDecimal.ZERO : p.getPrecioMensual(), p == null ? BigDecimal.ZERO : p.getPrecioAnual(),
                    p == null || Boolean.TRUE.equals(p.getSeVendeAparte())));
            }
            return lista;
        });
    }

    public PrecioModulo actualizarPrecio(String codigo, DatosPrecio d) {
        Modulo modulo;
        try {
            modulo = Modulo.valueOf(codigo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NoSuchElementException("Módulo no encontrado");
        }
        if (d == null) throw new IllegalArgumentException("Datos requeridos");
        BigDecimal mensual = dinero(d.precioMensual(), "El precio mensual");
        BigDecimal anual = dinero(d.precioAnual(), "El precio anual");
        ejecutor.en(NegocioActual.RAIZ, () -> {
            ModuloPrecio p = precios.findById(modulo).orElseGet(() -> {
                ModuloPrecio nuevo = new ModuloPrecio();
                nuevo.setModulo(modulo);
                return nuevo;
            });
            p.setPrecioMensual(mensual);
            p.setPrecioAnual(anual);
            p.setSeVendeAparte(!Boolean.FALSE.equals(d.seVendeAparte()));
            precios.save(p);
        });
        auditoria.registrarPlataforma("PRECIO_MODULO", null, modulo.getEtiqueta() + " · Mensual " + mensual + " · Anual " + anual);
        return preciosModulos().stream().filter(x -> x.modulo().equals(modulo.name())).findFirst().orElseThrow();
    }

    public List<FacturaVista> facturas(String estado) {
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            Map<Long, String> nombres = new java.util.HashMap<>();
            negocios.findAll().forEach(n -> nombres.put(n.getId(), n.getNombre()));
            ParametrosCobro p = parametrosActuales();
            return facturas.findTop300ByOrderByEmitidaEnDesc().stream()
                .filter(f -> estado == null || estado.isBlank() || f.getEstado().name().equalsIgnoreCase(estado)
                    || ("VENCIDA".equalsIgnoreCase(estado) && "VENCIDA".equals(situacion(f, p))))
                .map(f -> vista(f, nombres.get(f.getNegocioId()), p)).toList();
        });
    }

    public EstadoCobro estado(long negocioId) {
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = negocios.findById(negocioId).orElseThrow(() -> new NoSuchElementException("Negocio no encontrado"));
            ParametrosCobro p = parametrosActuales();
            List<Factura> pendientes = facturas.findByNegocioIdAndEstadoOrderByVenceEnAsc(negocioId, Factura.Estado.PENDIENTE);
            FacturaVista pendiente = pendientes.isEmpty() ? null : vista(pendientes.get(0), n.getNombre(), p);
            List<LineaVista> proximo = lineas(n, mapaPrecios()).stream().map(CobranzaService::vista).toList();
            BigDecimal total = proximo.stream().map(LineaVista::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<FacturaVista> historial = facturas.findTop24ByNegocioIdOrderByPeriodoDesdeDesc(negocioId).stream()
                .map(f -> vista(f, n.getNombre(), p)).toList();
            List<AvisoVista> recientes = avisos.findTop20ByNegocioIdOrderByFechaDescIdDesc(negocioId).stream()
                .map(a -> new AvisoVista(a.getTipo().name(), a.getMensaje(), a.getFecha(), a.getFacturaId())).toList();
            AvisoBanner banner = banner(n, pendientes.isEmpty() ? null : pendientes.get(0), p, LocalDate.now());
            return new EstadoCobro(n.getEstado().name(), n.getVenceEn(), n.getCiclo().name(), pendiente, proximo, total,
                historial, recientes, p.getInstruccionesPago(), banner == null ? null : banner.texto(), banner == null ? null : banner.nivel());
        });
    }

    public AvisoBanner banner(long negocioId) {
        return ejecutor.en(NegocioActual.RAIZ, () -> negocios.findById(negocioId).map(n -> {
            List<Factura> pendientes = facturas.findByNegocioIdAndEstadoOrderByVenceEnAsc(negocioId, Factura.Estado.PENDIENTE);
            return banner(n, pendientes.isEmpty() ? null : pendientes.get(0), parametrosActuales(), LocalDate.now());
        }).orElse(null));
    }

    private AvisoBanner banner(Negocio n, Factura pendiente, ParametrosCobro p, LocalDate hoy) {
        if (pendiente != null) {
            long dias = ChronoUnit.DAYS.between(hoy, pendiente.getVenceEn());
            String monto = pendiente.getTotal().setScale(2, RoundingMode.HALF_UP) + " (factura " + pendiente.getNumero() + ")";
            if (dias > 0) return new AvisoBanner("Tienes un pago de Bs " + monto + " que vence en " + dias + (dias == 1 ? " día." : " días."), "info");
            if (dias == 0) return new AvisoBanner("Hoy vence tu pago de Bs " + monto + ".", "warning");
            LocalDate bloqueo = pendiente.getVenceEn().plusDays(p.getDiasGracia() + 1L);
            if (Boolean.TRUE.equals(p.getSuspensionAutomatica()))
                return new AvisoBanner("Tu pago de Bs " + monto + " está vencido. El sistema se bloqueará el " + fecha(bloqueo)
                    + " si no se registra el pago.", "error");
            return new AvisoBanner("Tu pago de Bs " + monto + " está vencido. Regulariza para evitar la suspensión.", "error");
        }
        if (n.getVenceEn() == null) return n.getEstado() == EstadoSuscripcion.PRUEBA ? new AvisoBanner("Estás usando el periodo de prueba.", "info") : null;
        long dias = ChronoUnit.DAYS.between(hoy, n.getVenceEn());
        String prefijo = n.getEstado() == EstadoSuscripcion.PRUEBA ? "La prueba" : "La suscripción";
        if (dias < 0) return new AvisoBanner(prefijo + " venció el " + fecha(n.getVenceEn()) + ". Comunícate con soporte para renovarla.", "error");
        if (dias <= 7) return new AvisoBanner(prefijo + (dias == 0 ? " vence hoy." : " vence en " + dias + (dias == 1 ? " día." : " días.")), "warning");
        return null;
    }

    public FacturaVista emitir(long negocioId) {
        FacturaVista f = ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = negocios.findById(negocioId).orElseThrow(() -> new NoSuchElementException("Negocio no encontrado"));
            if (n.getVenceEn() == null)
                throw new IllegalStateException("Define primero hasta qué fecha está pagado el negocio (vencimiento).");
            if (existeFactura(negocioId, n.getVenceEn()))
                throw new IllegalStateException("Ya existe una factura para el periodo que empieza el " + fecha(n.getVenceEn()));
            return vista(crearFactura(n, LocalDate.now()), n.getNombre(), parametrosActuales());
        });
        auditoria.registrarPlataforma("FACTURA_EMITIDA", negocioId, f.numero() + " · Bs " + f.total());
        return f;
    }

    private Factura crearFactura(Negocio n, LocalDate hoy) {
        Factura f = new Factura();
        f.setNegocioId(n.getId());
        f.setNumero("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        f.setPlanCodigo(n.getPlan().getCodigo());
        f.setCiclo(n.getCiclo());
        f.setPeriodoDesde(n.getVenceEn());
        f.setPeriodoHasta(n.getCiclo() == CicloFacturacion.ANUAL ? n.getVenceEn().plusYears(1) : n.getVenceEn().plusMonths(1));
        f.setEmitidaEn(LocalDateTime.now());
        f.setVenceEn(n.getVenceEn().isBefore(hoy) ? hoy : n.getVenceEn());
        List<FacturaLinea> lineas = lineas(n, mapaPrecios());
        lineas.forEach(l -> l.setFactura(f));
        f.setLineas(lineas);
        f.setTotal(lineas.stream().map(FacturaLinea::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        facturas.saveAndFlush(f);
        f.setNumero(String.format(Locale.ROOT, "F-%d-%06d", f.getEmitidaEn().getYear(), f.getId()));
        facturas.save(f);
        avisar(n.getId(), f, AvisoCobro.Tipo.FACTURA_EMITIDA, hoy, "Se emitió la factura " + f.getNumero() + " por Bs "
            + f.getTotal() + " (" + fecha(f.getPeriodoDesde()) + " al " + fecha(f.getPeriodoHasta()) + "). Vence el " + fecha(f.getVenceEn()) + ".");
        return f;
    }

    private List<FacturaLinea> lineas(Negocio n, Map<Modulo, ModuloPrecio> tabla) {
        boolean anual = n.getCiclo() == CicloFacturacion.ANUAL;
        List<FacturaLinea> lineas = new ArrayList<>();
        Plan plan = n.getPlan();
        lineas.add(linea("Plan " + plan.getNombre() + (anual ? " · anual" : " · mensual"), null,
            anual ? plan.getPrecioAnual() : plan.getPrecioMensual()));
        if (n.getAjustesModulos() != null) {
            for (Modulo m : Modulo.values()) {
                if (!Boolean.TRUE.equals(n.getAjustesModulos().get(m))) continue;
                if (plan.getModulos() != null && plan.getModulos().contains(m)) continue;
                ModuloPrecio p = tabla.get(m);
                BigDecimal precio = p == null ? BigDecimal.ZERO : anual ? p.getPrecioAnual() : p.getPrecioMensual();
                if (precio.signum() > 0) lineas.add(linea("Módulo extra: " + m.getEtiqueta(), m, precio));
            }
        }
        return lineas;
    }

    private static FacturaLinea linea(String concepto, Modulo modulo, BigDecimal precio) {
        FacturaLinea l = new FacturaLinea();
        l.setConcepto(concepto);
        l.setModulo(modulo);
        l.setCantidad(1);
        BigDecimal p = (precio == null ? BigDecimal.ZERO : precio).setScale(2, RoundingMode.HALF_UP);
        l.setPrecioUnitario(p);
        l.setSubtotal(p);
        return l;
    }

    public FacturaVista registrarPago(long facturaId, Pago pago) {
        String metodo = pago == null ? null : texto(pago.metodo(), 30, "El método de pago");
        if (metodo == null) throw new IllegalArgumentException("Indica cómo se pagó");
        String referencia = texto(pago.referencia(), 120, "La referencia");
        String nota = texto(pago.nota(), 300, "La nota");
        String usuario = AuditoriaService.recortar(UsuarioActual.nombre().orElse("soporte"), 60);
        Object[] resultado = ejecutor.en(NegocioActual.RAIZ, () -> {
            Factura f = facturas.findByIdForUpdate(facturaId).orElseThrow(() -> new NoSuchElementException("Factura no encontrada"));
            if (f.getEstado() != Factura.Estado.PENDIENTE)
                throw new IllegalStateException(f.getEstado() == Factura.Estado.PAGADA ? "Esta factura ya está pagada" : "Esta factura está anulada");
            Negocio n = negocios.findById(f.getNegocioId()).orElseThrow();
            f.setEstado(Factura.Estado.PAGADA);
            f.setPagadaEn(LocalDateTime.now());
            f.setMetodoPago(metodo);
            f.setReferenciaPago(referencia);
            f.setRegistradaPor(usuario);
            if (nota != null) f.setNota(nota);
            facturas.save(f);
            if (n.getVenceEn() == null || n.getVenceEn().isBefore(f.getPeriodoHasta())) n.setVenceEn(f.getPeriodoHasta());
            boolean reactivado = false;
            if (n.getEstado() == EstadoSuscripcion.SUSPENDIDO && SUSPENSION_POR_PAGO.equals(n.getMotivoSuspension())
                    && facturas.findByNegocioIdAndEstadoOrderByVenceEnAsc(n.getId(), Factura.Estado.PENDIENTE).stream()
                        .noneMatch(x -> estaFueraDePlazo(x, parametrosActuales(), LocalDate.now()))) {
                n.setEstado(EstadoSuscripcion.ACTIVO);
                n.setMotivoSuspension(null);
                reactivado = true;
            } else if (n.getEstado() == EstadoSuscripcion.PRUEBA) {
                n.setEstado(EstadoSuscripcion.ACTIVO);
            }
            negocios.save(n);
            avisar(n.getId(), f, AvisoCobro.Tipo.PAGO_RECIBIDO, LocalDate.now(), "Recibimos tu pago de la factura " + f.getNumero()
                + ". Tu servicio está pagado hasta el " + fecha(n.getVenceEn()) + "." + (reactivado ? " Tu acceso fue reactivado." : ""));
            return new Object[]{vista(f, n.getNombre(), parametrosActuales()), reactivado};
        });
        FacturaVista f = (FacturaVista) resultado[0];
        modulos.invalidar(f.negocioId());
        auditoria.registrarPlataforma("PAGO_REGISTRADO", f.negocioId(), f.numero() + " · Bs " + f.total() + " · " + metodo
            + (Boolean.TRUE.equals(resultado[1]) ? " · Acceso reactivado" : ""));
        return f;
    }

    public FacturaVista anular(long facturaId, String motivo) {
        String limpio = texto(motivo, 300, "El motivo");
        if (limpio == null) throw new IllegalArgumentException("Indica por qué se anula la factura");
        FacturaVista f = ejecutor.en(NegocioActual.RAIZ, () -> {
            Factura factura = facturas.findByIdForUpdate(facturaId).orElseThrow(() -> new NoSuchElementException("Factura no encontrada"));
            if (factura.getEstado() != Factura.Estado.PENDIENTE) throw new IllegalStateException("Solo se anulan facturas pendientes");
            factura.setEstado(Factura.Estado.ANULADA);
            factura.setNota(limpio);
            facturas.save(factura);
            return vista(factura, negocios.findById(factura.getNegocioId()).map(Negocio::getNombre).orElse(null), parametrosActuales());
        });
        auditoria.registrarPlataforma("FACTURA_ANULADA", f.negocioId(), f.numero() + " · " + limpio);
        return f;
    }

    @Scheduled(cron = "${cobranza.cron:0 0 7 * * *}")
    public void ejecutarProgramado() {
        try {
            ResultadoCobranza r = ejecutar(LocalDate.now());
            log.info("Cobranza diaria: {} facturas emitidas, {} avisos, {} negocios suspendidos",
                r.facturasEmitidas(), r.avisosEnviados(), r.negociosSuspendidos());
        } catch (RuntimeException e) {
            log.error("Falló la cobranza diaria: {}", e.getMessage(), e);
        }
    }

    public ResultadoCobranza ejecutar(LocalDate hoy) {
        List<Long> ids = ejecutor.en(NegocioActual.RAIZ, () -> negocios.findAll().stream().map(Negocio::getId).toList());
        int[] totales = new int[3];
        List<Long> suspendidos = new ArrayList<>();
        for (Long id : ids) {
            try {
                int[] r = ejecutor.en(NegocioActual.RAIZ, () -> procesar(id, hoy));
                totales[0] += r[0];
                totales[1] += r[1];
                if (r[2] > 0) {
                    totales[2]++;
                    suspendidos.add(id);
                }
            } catch (RuntimeException e) {
                log.warn("Cobranza del negocio {} falló: {}", id, e.getMessage());
            }
        }
        suspendidos.forEach(id -> {
            modulos.invalidar(id);
            auditoria.registrarPlataforma("SUSPENSION_FALTA_DE_PAGO", id, "Suspendido automáticamente por falta de pago");
        });
        return new ResultadoCobranza(totales[0], totales[1], totales[2]);
    }

    public ResultadoCobranza ejecutarPara(long negocioId, LocalDate hoy) {
        int[] r = ejecutor.en(NegocioActual.RAIZ, () -> procesar(negocioId, hoy));
        if (r[2] > 0) {
            modulos.invalidar(negocioId);
            auditoria.registrarPlataforma("SUSPENSION_FALTA_DE_PAGO", negocioId, "Suspendido automáticamente por falta de pago");
        }
        return new ResultadoCobranza(r[0], r[1], r[2]);
    }

    private int[] procesar(Long id, LocalDate hoy) {
        Negocio n = negocios.findById(id).orElse(null);
        int emitidas = 0;
        int enviados = 0;
        int suspendido = 0;
        if (n == null || n.getVenceEn() == null || n.getEstado() == EstadoSuscripcion.CANCELADO) return new int[]{0, 0, 0};
        if (n.getEstado() == EstadoSuscripcion.SUSPENDIDO && !SUSPENSION_POR_PAGO.equals(n.getMotivoSuspension())) return new int[]{0, 0, 0};
        ParametrosCobro p = parametrosActuales();
        if (n.getEstado() != EstadoSuscripcion.SUSPENDIDO
                && ChronoUnit.DAYS.between(hoy, n.getVenceEn()) <= p.getDiasGeneracion()
                && !existeFactura(id, n.getVenceEn())) {
            crearFactura(n, hoy);
            emitidas++;
            enviados++;
        }
        List<Factura> pendientes = facturas.findByNegocioIdAndEstadoOrderByVenceEnAsc(id, Factura.Estado.PENDIENTE);
        if (pendientes.isEmpty()) return new int[]{emitidas, enviados, 0};
        Factura f = pendientes.get(0);
        long dias = ChronoUnit.DAYS.between(hoy, f.getVenceEn());
        String monto = "Bs " + f.getTotal() + " (factura " + f.getNumero() + ")";
        if (dias >= 0 && dias <= p.getDiasRecordatorio()) {
            if (avisar(id, f, AvisoCobro.Tipo.RECORDATORIO, hoy, dias == 0 ? "Hoy vence tu pago de " + monto + "."
                    : "Tu pago de " + monto + " vence en " + dias + (dias == 1 ? " día" : " días") + ", el " + fecha(f.getVenceEn()) + "."))
                enviados++;
        } else if (dias < 0) {
            long atraso = -dias;
            LocalDate bloqueo = f.getVenceEn().plusDays(p.getDiasGracia() + 1L);
            boolean automatica = Boolean.TRUE.equals(p.getSuspensionAutomatica());
            if (atraso <= p.getDiasGracia()) {
                boolean ultimo = atraso == p.getDiasGracia();
                String texto = ultimo
                    ? "Último aviso: tu pago de " + monto + " sigue pendiente." + (automatica ? " Mañana (" + fecha(bloqueo) + ") se bloqueará el acceso al sistema." : "")
                    : "Tu pago de " + monto + " está vencido hace " + atraso + (atraso == 1 ? " día." : " días.")
                        + (automatica ? " El acceso se bloqueará el " + fecha(bloqueo) + "." : "");
                if (avisar(id, f, ultimo ? AvisoCobro.Tipo.ULTIMO_AVISO : AvisoCobro.Tipo.VENCIDA, hoy, texto)) enviados++;
            } else if (automatica && n.getEstado() != EstadoSuscripcion.SUSPENDIDO) {
                n.setEstado(EstadoSuscripcion.SUSPENDIDO);
                n.setMotivoSuspension(SUSPENSION_POR_PAGO);
                negocios.save(n);
                avisar(id, f, AvisoCobro.Tipo.SUSPENDIDO, hoy, "El acceso se suspendió por falta de pago de " + monto
                    + ". Se reactiva automáticamente al registrar el pago.");
                enviados++;
                suspendido = 1;
            }
        }
        return new int[]{emitidas, enviados, suspendido};
    }

    private boolean existeFactura(Long negocioId, LocalDate desde) {
        return facturas.existsByNegocioIdAndPeriodoDesdeAndEstadoNot(negocioId, desde, Factura.Estado.ANULADA);
    }

    private boolean estaFueraDePlazo(Factura f, ParametrosCobro p, LocalDate hoy) {
        return f.getVenceEn().plusDays(p.getDiasGracia()).isBefore(hoy);
    }

    private boolean avisar(Long negocioId, Factura f, AvisoCobro.Tipo tipo, LocalDate dia, String mensaje) {
        Long facturaId = f == null ? null : f.getId();
        if (avisos.existsByNegocioIdAndFacturaIdAndTipoAndDia(negocioId, facturaId, tipo, dia)) return false;
        AvisoCobro a = new AvisoCobro();
        a.setNegocioId(negocioId);
        a.setFacturaId(facturaId);
        a.setTipo(tipo);
        a.setMensaje(AuditoriaService.recortar(mensaje, 300));
        a.setFecha(LocalDateTime.now());
        a.setDia(dia);
        avisos.save(a);
        return true;
    }

    private ParametrosCobro parametrosActuales() {
        return parametros.findById(ParametrosCobro.ID).orElseGet(() -> {
            ParametrosCobro p = new ParametrosCobro();
            return parametros.save(p);
        });
    }

    private Map<Modulo, ModuloPrecio> mapaPrecios() {
        Map<Modulo, ModuloPrecio> mapa = new EnumMap<>(Modulo.class);
        precios.findAll().forEach(p -> mapa.put(p.getModulo(), p));
        return mapa;
    }

    private String situacion(Factura f, ParametrosCobro p) {
        if (f.getEstado() != Factura.Estado.PENDIENTE) return f.getEstado().name();
        LocalDate hoy = LocalDate.now();
        if (f.getVenceEn().isBefore(hoy)) return estaFueraDePlazo(f, p, hoy) ? "BLOQUEO" : "VENCIDA";
        return "AL_DIA";
    }

    private FacturaVista vista(Factura f, String negocio, ParametrosCobro p) {
        Long dias = f.getEstado() == Factura.Estado.PENDIENTE ? ChronoUnit.DAYS.between(LocalDate.now(), f.getVenceEn()) : null;
        LocalDate bloqueo = f.getEstado() == Factura.Estado.PENDIENTE && Boolean.TRUE.equals(p.getSuspensionAutomatica())
            ? f.getVenceEn().plusDays(p.getDiasGracia() + 1L) : null;
        return new FacturaVista(f.getId(), f.getNegocioId(), negocio, f.getNumero(), f.getPlanCodigo(), f.getCiclo().name(),
            f.getPeriodoDesde(), f.getPeriodoHasta(), f.getEmitidaEn(), f.getVenceEn(), f.getTotal(), f.getEstado().name(),
            situacion(f, p), dias, bloqueo, f.getPagadaEn(), f.getMetodoPago(), f.getReferenciaPago(), f.getRegistradaPor(), f.getNota(),
            f.getLineas().stream().map(CobranzaService::vista).toList());
    }

    private static LineaVista vista(FacturaLinea l) {
        return new LineaVista(l.getConcepto(), l.getModulo() == null ? null : l.getModulo().name(), l.getCantidad(),
            l.getPrecioUnitario(), l.getSubtotal());
    }

    private static Parametros vista(ParametrosCobro p) {
        return new Parametros(p.getDiasGeneracion(), p.getDiasRecordatorio(), p.getDiasGracia(),
            p.getSuspensionAutomatica(), p.getInstruccionesPago());
    }

    private static int dias(Integer valor, String campo) {
        if (valor == null || valor < 0 || valor > 60) throw new IllegalArgumentException(campo + " deben estar entre 0 y 60");
        return valor;
    }

    private static BigDecimal dinero(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException(campo + " no puede ser negativo");
        if (valor.compareTo(BigDecimal.valueOf(1_000_000)) > 0) throw new IllegalArgumentException(campo + " es demasiado alto");
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String texto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (limpio.length() > max) throw new IllegalArgumentException(campo + " admite hasta " + max + " caracteres");
        return limpio;
    }

    private static String fecha(LocalDate f) {
        return f == null ? "—" : String.format(Locale.ROOT, "%02d/%02d/%d", f.getDayOfMonth(), f.getMonthValue(), f.getYear());
    }
}
