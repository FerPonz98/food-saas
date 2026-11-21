package com.sistema.pedidosCori.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IConfiguracionNegocioDao;
import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlanDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IRegistroPlataformaDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.CicloFacturacion;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;
import com.sistema.pedidosCori.models.entity.EstadoSuscripcion;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.models.entity.Negocio;
import com.sistema.pedidosCori.models.entity.Plan;
import com.sistema.pedidosCori.models.entity.RegistroPlataforma;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlataformaService {
    public static final int DIAS_PRUEBA = 14;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final String LETRAS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITOS = "23456789";

    private final INegocioDao negocios;
    private final IPlanDao planes;
    private final IUsuarioDao usuarios;
    private final IPlatoDao platos;
    private final IPedidoDao pedidos;
    private final IConfiguracionNegocioDao configuraciones;
    private final IRegistroPlataformaDao registros;
    private final ConfiguracionNegocioService configuracion;
    private final CategoriaService categorias;
    private final EjecutorNegocio ejecutor;
    private final PasswordEncoder passwordEncoder;
    private final ModuloService modulos;
    private final AuditoriaService auditoria;
    private final UsuarioService usuarioService;
    private final SecureRandom random = new SecureRandom();

    public record ModuloVista(String codigo, String etiqueta, String descripcion, String grupo) {
    }

    public record PlanVista(String codigo, String nombre, String descripcion, Integer orden, BigDecimal precioMensual,
            BigDecimal precioAnual, BigDecimal precioImplementacion, Integer mesasIncluidas, Integer productosIncluidos,
            Integer usuariosIncluidos, Boolean activo, List<String> modulos) {
    }

    public record Uso(int mesas, long productos, long usuarios, long pedidos30Dias, LocalDateTime ultimoAcceso) {
    }

    public record NegocioVista(Long id, String nombre, String slug, String planCodigo, String planNombre, String estado,
            String estadoEtiqueta, String ciclo, LocalDate inicioSuscripcion, LocalDate venceEn, Long diasParaVencer,
            String contactoNombre, String contactoTelefono, String contactoEmail, String notas, LocalDateTime creadoEn,
            Map<String, Boolean> ajustesModulos, List<String> modulos, Uso uso, List<String> avisos) {
    }

    public record NuevoNegocio(String nombre, String slug, String planCodigo, String estado, String ciclo,
            LocalDate venceEn, String adminUsername, String adminNombre, String contactoNombre, String contactoTelefono,
            String contactoEmail, String notas) {
    }

    public record DatosNegocio(String nombre, String slug, String contactoNombre, String contactoTelefono,
            String contactoEmail, String notas) {
    }

    public record Suscripcion(String planCodigo, String estado, String ciclo, LocalDate inicioSuscripcion, LocalDate venceEn) {
    }

    public record DatosPlan(String nombre, String descripcion, BigDecimal precioMensual, BigDecimal precioAnual,
            BigDecimal precioImplementacion, Integer mesasIncluidas, Integer productosIncluidos, Integer usuariosIncluidos,
            Boolean activo, List<String> modulos) {
    }

    public record AccesoTemporal(NegocioVista negocio, String username, String passwordTemporal) {
    }

    public record RegistroVista(Long id, LocalDateTime fecha, String usuario, String accion, Long negocioId, String detalle) {
    }

    public record MiPlan(String planCodigo, String planNombre, String estado, String estadoEtiqueta, String ciclo,
            LocalDate venceEn, Long diasParaVencer, Integer mesasIncluidas, Integer productosIncluidos,
            Integer usuariosIncluidos, Uso uso, List<String> avisos, List<Map<String, Object>> modulos) {
    }

    public List<ModuloVista> modulosDisponibles() {
        return Arrays.stream(Modulo.values())
            .map(m -> new ModuloVista(m.name(), m.getEtiqueta(), m.getDescripcion(), m.getGrupo())).toList();
    }

    @Transactional
    public void crearPlanesPredeterminados() {
        if (planes.count() > 0) return;
        Set<Modulo> pro = EnumSet.of(Modulo.APARIENCIA_AVANZADA, Modulo.APP_INSTALABLE, Modulo.CARTA_QR, Modulo.LLAMADOS,
            Modulo.PEDIDOS_QR, Modulo.DIVISION_CUENTA, Modulo.REPORTES_COMPLETOS, Modulo.DISPONIBILIDAD);
        Set<Modulo> premium = EnumSet.copyOf(pro);
        premium.addAll(EnumSet.of(Modulo.REPORTES_AVANZADOS, Modulo.AUDITORIA, Modulo.VARIOS_ADMINISTRADORES,
            Modulo.CARTA_SIN_MARCA, Modulo.FOTOS_ANIMADAS));
        planes.save(plan(Plan.NORMAL, "Normal", "Empieza a tomar pedidos digitales", 1, 650, 6500, 400, 10, 50, 3,
            EnumSet.noneOf(Modulo.class)));
        planes.save(plan(Plan.PRO, "Pro", "Tu restaurante digital completo", 2, 1200, 12000, 800, 30, 200, null, pro));
        planes.save(plan(Plan.PREMIUM, "Premium", "Control, seguridad y marca propia", 3, 2200, 22000, 1500,
            null, null, null, premium));
    }

    @Transactional(readOnly = true)
    public List<PlanVista> planes() {
        return planes.findAllByOrderByOrdenAsc().stream().map(PlataformaService::vista).toList();
    }

    @Transactional
    public PlanVista actualizarPlan(String codigo, DatosPlan d) {
        if (d == null) throw new IllegalArgumentException("Datos del plan requeridos");
        Plan p = planes.findById(codigo).orElseThrow(() -> new NoSuchElementException("Plan no encontrado"));
        p.setNombre(texto(d.nombre(), "nombre del plan", 60));
        p.setDescripcion(opcional(d.descripcion(), 240));
        p.setPrecioMensual(precio(d.precioMensual(), "mensual"));
        p.setPrecioAnual(precio(d.precioAnual(), "anual"));
        p.setPrecioImplementacion(precio(d.precioImplementacion(), "de implementación"));
        p.setMesasIncluidas(limite(d.mesasIncluidas(), "mesas"));
        p.setProductosIncluidos(limite(d.productosIncluidos(), "productos"));
        p.setUsuariosIncluidos(limite(d.usuariosIncluidos(), "usuarios"));
        if (d.activo() != null) p.setActivo(d.activo());
        p.setModulos(modulos(d.modulos()));
        planes.save(p);
        modulos.invalidarTodo();
        auditoria.registrarPlataforma("PLAN_EDITADO", null, p.getNombre() + " · Módulos: " + nombres(p.getModulos()));
        return vista(p);
    }

    @Transactional
    public Negocio crearNegocioInicial() {
        Negocio n = new Negocio();
        n.setNombre("Mi negocio");
        n.setSlug("principal");
        n.setPlan(planes.findById(Plan.PREMIUM).orElseThrow());
        n.setEstado(EstadoSuscripcion.ACTIVO);
        n.setCiclo(CicloFacturacion.MENSUAL);
        n.setInicioSuscripcion(LocalDate.now());
        n.setCreadoEn(LocalDateTime.now());
        Negocio guardado = negocios.save(n);
        modulos.invalidar(guardado.getId());
        return guardado;
    }

    public List<NegocioVista> negocios() {
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            Map<Long, Uso> uso = usoPorNegocio();
            return negocios.findAllByOrderByNombreAsc().stream()
                .map(n -> vista(n, uso.getOrDefault(n.getId(), new Uso(0, 0, 0, 0, null)))).toList();
        });
    }

    public NegocioVista negocio(Long id) {
        return ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = buscar(id);
            return vista(n, usoPorNegocio().getOrDefault(id, new Uso(0, 0, 0, 0, null)));
        });
    }

    public AccesoTemporal crearNegocio(NuevoNegocio r) {
        if (r == null) throw new IllegalArgumentException("Datos del negocio requeridos");
        String nombre = texto(r.nombre(), "nombre del negocio", 120);
        String slug = slug(r.slug() == null || r.slug().isBlank() ? sugerirSlug(nombre) : r.slug());
        Plan plan = planActivo(r.planCodigo());
        EstadoSuscripcion estado = estado(r.estado() == null || r.estado().isBlank() ? "PRUEBA" : r.estado());
        if (!estado.permiteAcceso()) throw new IllegalArgumentException("Un negocio nuevo debe empezar activo o en prueba");
        CicloFacturacion ciclo = ciclo(r.ciclo());
        String username = username(r.adminUsername());
        String adminNombre = r.adminNombre() == null || r.adminNombre().isBlank() ? "Administrador" : texto(r.adminNombre(), "nombre del administrador", 80);
        String email = email(r.contactoEmail());
        Long id = ejecutor.en(NegocioActual.RAIZ, () -> {
            if (negocios.existsBySlug(slug)) throw new IllegalStateException("Ya existe un negocio con la dirección «" + slug + "»");
            if (usuarios.existsByUsername(username)) throw new IllegalStateException("El usuario «" + username + "» ya existe");
            Negocio n = new Negocio();
            n.setNombre(nombre);
            n.setSlug(slug);
            n.setPlan(plan);
            n.setEstado(estado);
            n.setCiclo(ciclo);
            n.setInicioSuscripcion(LocalDate.now());
            n.setVenceEn(r.venceEn() != null ? r.venceEn() : estado == EstadoSuscripcion.PRUEBA ? LocalDate.now().plusDays(DIAS_PRUEBA) : null);
            n.setContactoNombre(opcional(r.contactoNombre(), 120));
            n.setContactoTelefono(opcional(r.contactoTelefono(), 40));
            n.setContactoEmail(email);
            n.setNotas(opcional(r.notas(), 1000));
            n.setCreadoEn(LocalDateTime.now());
            return negocios.save(n).getId();
        });
        modulos.invalidar(id);
        configuracion.crearPara(id, nombre);
        String temporal = generarTemporal();
        ejecutor.en(id, () -> {
            categorias.inicializar();
            usuarios.save(Usuario.builder().username(username).nombreCompleto(adminNombre).rol(Rol.ADMIN)
                .negocioId(id).password(passwordEncoder.encode(temporal)).activo(true)
                .debeCambiarPassword(true).passwordActualizadaEn(LocalDateTime.now()).build());
        });
        auditoria.registrarPlataforma("NEGOCIO_CREADO", id, nombre + " (" + slug + ") · Plan " + plan.getNombre()
            + " · " + estado.getEtiqueta() + " · Administrador " + username);
        return new AccesoTemporal(negocio(id), username, temporal);
    }

    public NegocioVista actualizarDatos(Long id, DatosNegocio d) {
        if (d == null) throw new IllegalArgumentException("Datos del negocio requeridos");
        String nombre = texto(d.nombre(), "nombre del negocio", 120);
        String slug = slug(d.slug());
        String email = email(d.contactoEmail());
        ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = buscar(id);
            if (!n.getSlug().equals(slug) && negocios.existsBySlug(slug))
                throw new IllegalStateException("Ya existe un negocio con la dirección «" + slug + "»");
            String anterior = n.getSlug();
            n.setNombre(nombre);
            n.setSlug(slug);
            n.setContactoNombre(opcional(d.contactoNombre(), 120));
            n.setContactoTelefono(opcional(d.contactoTelefono(), 40));
            n.setContactoEmail(email);
            n.setNotas(opcional(d.notas(), 1000));
            negocios.save(n);
            auditoria.registrarPlataforma("NEGOCIO_EDITADO", id, nombre + (anterior.equals(slug) ? "" : " · Dirección " + anterior + " → " + slug));
        });
        modulos.invalidar(id);
        return negocio(id);
    }

    public NegocioVista cambiarSuscripcion(Long id, Suscripcion s) {
        if (s == null) throw new IllegalArgumentException("Datos de la suscripción requeridos");
        Plan plan = planActivoOActual(s.planCodigo(), id);
        EstadoSuscripcion estado = estado(s.estado());
        CicloFacturacion ciclo = ciclo(s.ciclo());
        if (s.inicioSuscripcion() != null && s.venceEn() != null && s.venceEn().isBefore(s.inicioSuscripcion()))
            throw new IllegalArgumentException("El vencimiento no puede ser anterior al inicio");
        String[] resumen = new String[1];
        EstadoSuscripcion[] anterior = new EstadoSuscripcion[1];
        ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = buscar(id);
            anterior[0] = n.getEstado();
            List<String> cambios = new ArrayList<>();
            if (!n.getPlan().getCodigo().equals(plan.getCodigo()))
                cambios.add("Plan " + n.getPlan().getNombre() + " → " + plan.getNombre());
            if (n.getEstado() != estado) cambios.add("Estado " + n.getEstado().getEtiqueta() + " → " + estado.getEtiqueta());
            if (n.getCiclo() != ciclo) cambios.add("Ciclo " + n.getCiclo() + " → " + ciclo);
            if (!Objects.equals(n.getVenceEn(), s.venceEn())) cambios.add("Vence " + Objects.toString(s.venceEn(), "sin fecha"));
            n.setPlan(plan);
            if (estado != EstadoSuscripcion.SUSPENDIDO) n.setMotivoSuspension(null);
            else if (n.getEstado() != EstadoSuscripcion.SUSPENDIDO) n.setMotivoSuspension(CobranzaService.SUSPENSION_MANUAL);
            n.setEstado(estado);
            n.setCiclo(ciclo);
            if (s.inicioSuscripcion() != null) n.setInicioSuscripcion(s.inicioSuscripcion());
            n.setVenceEn(s.venceEn());
            negocios.save(n);
            resumen[0] = cambios.isEmpty() ? "Sin cambios" : String.join(" · ", cambios);
        });
        modulos.invalidar(id);
        auditoria.registrarPlataforma("SUSCRIPCION_ACTUALIZADA", id, resumen[0]);
        NegocioActual.ejecutarComo(id, () -> auditoria.registrarComo("soporte", AccionAuditoria.SUSCRIPCION_ACTUALIZADA,
            "Negocio", id, () -> resumen[0]));
        if (anterior[0].permiteAcceso() && !estado.permiteAcceso()) cerrarSesiones(id);
        return negocio(id);
    }

    public NegocioVista ajustarModulos(Long id, Map<String, String> ajustes) {
        if (ajustes == null) throw new IllegalArgumentException("Indica los módulos a ajustar");
        Map<Modulo, Boolean> nuevos = new EnumMap<>(Modulo.class);
        ajustes.forEach((codigo, modo) -> {
            Modulo m = modulo(codigo);
            switch (modo == null ? "" : modo.trim().toUpperCase(Locale.ROOT)) {
                case "ACTIVAR" -> nuevos.put(m, true);
                case "DESACTIVAR" -> nuevos.put(m, false);
                case "PLAN", "" -> { }
                default -> throw new IllegalArgumentException("Ajuste no válido para " + m.getEtiqueta());
            }
        });
        ejecutor.en(NegocioActual.RAIZ, () -> {
            Negocio n = buscar(id);
            n.getAjustesModulos().clear();
            n.getAjustesModulos().putAll(nuevos);
            negocios.save(n);
        });
        modulos.invalidar(id);
        List<String> texto = new ArrayList<>();
        nuevos.forEach((m, activo) -> texto.add((activo ? "+" : "−") + m.getEtiqueta()));
        auditoria.registrarPlataforma("MODULOS_AJUSTADOS", id, texto.isEmpty() ? "Módulos según el plan" : String.join(", ", texto));
        return negocio(id);
    }

    public AccesoTemporal restablecerAdministrador(Long id) {
        String temporal = generarTemporal();
        String username = ejecutor.en(NegocioActual.RAIZ, () -> {
            buscar(id);
            Usuario admin = usuarios.findFirstByNegocioIdAndRolOrderByIdAsc(id, Rol.ADMIN)
                .orElseThrow(() -> new NoSuchElementException("El negocio no tiene administradores"));
            admin.setPassword(passwordEncoder.encode(temporal));
            admin.setActivo(true);
            admin.setDebeCambiarPassword(true);
            admin.setPasswordActualizadaEn(LocalDateTime.now());
            usuarios.save(admin);
            return admin.getUsername();
        });
        usuarioService.expirarSesiones(username);
        auditoria.registrarPlataforma("ACCESO_RESTABLECIDO", id, "Contraseña temporal emitida para " + username);
        NegocioActual.ejecutarComo(id, () -> auditoria.registrarComo("soporte", AccionAuditoria.PASSWORD_RESTABLECIDA,
            "Usuario", null, () -> username + " · Contraseña temporal emitida por soporte"));
        return new AccesoTemporal(negocio(id), username, temporal);
    }

    public List<RegistroVista> registros(Long negocioId) {
        List<RegistroPlataforma> lista = ejecutor.en(NegocioActual.RAIZ, () -> negocioId == null
            ? registros.findTop100ByOrderByFechaDescIdDesc() : registros.findTop100ByNegocioIdOrderByFechaDescIdDesc(negocioId));
        return lista.stream().map(r -> new RegistroVista(r.getId(), r.getFecha(), r.getUsuario(), r.getAccion(),
            r.getNegocioId(), r.getDetalle())).toList();
    }

    public MiPlan miPlan() {
        long id = NegocioActual.requerido();
        NegocioVista n = negocio(id);
        Plan plan = ejecutor.en(NegocioActual.RAIZ, () -> buscar(id).getPlan());
        Set<Modulo> efectivos = modulos.info(id).map(ModuloService.InfoNegocio::modulos).orElse(Set.of());
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (Modulo m : Modulo.values()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("codigo", m.name());
            fila.put("etiqueta", m.getEtiqueta());
            fila.put("descripcion", m.getDescripcion());
            fila.put("grupo", m.getGrupo());
            fila.put("incluido", efectivos.contains(m));
            detalle.add(fila);
        }
        return new MiPlan(n.planCodigo(), n.planNombre(), n.estado(), n.estadoEtiqueta(), n.ciclo(), n.venceEn(),
            n.diasParaVencer(), plan.getMesasIncluidas(), plan.getProductosIncluidos(), plan.getUsuariosIncluidos(),
            n.uso(), n.avisos(), detalle);
    }

    private void cerrarSesiones(Long negocioId) {
        for (Usuario u : usuarios.findByNegocioId(negocioId)) usuarioService.expirarSesiones(u.getUsername());
    }

    private Map<Long, Uso> usoPorNegocio() {
        Map<Long, Integer> mesas = new HashMap<>();
        for (ConfiguracionNegocio c : configuraciones.findAll()) mesas.put(c.getId(), c.getCantidadPuntosAtencion());
        Map<Long, Long> productos = conteo(platos.contarActivosPorNegocio());
        Map<Long, Long> pedidos30 = conteo(pedidos.contarDesdePorNegocio(LocalDateTime.now().minusDays(30)));
        Map<Long, Long> activos = new HashMap<>();
        Map<Long, LocalDateTime> accesos = new HashMap<>();
        for (Object[] fila : usuarios.resumenPorNegocio()) {
            activos.put((Long) fila[0], ((Number) fila[1]).longValue());
            if (fila[2] != null) accesos.put((Long) fila[0], (LocalDateTime) fila[2]);
        }
        Map<Long, Uso> uso = new HashMap<>();
        for (Negocio n : negocios.findAll()) {
            Long id = n.getId();
            uso.put(id, new Uso(mesas.getOrDefault(id, 0), productos.getOrDefault(id, 0L), activos.getOrDefault(id, 0L),
                pedidos30.getOrDefault(id, 0L), accesos.get(id)));
        }
        return uso;
    }

    private static Map<Long, Long> conteo(List<Object[]> filas) {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : filas) mapa.put((Long) fila[0], ((Number) fila[1]).longValue());
        return mapa;
    }

    private NegocioVista vista(Negocio n, Uso uso) {
        Plan p = n.getPlan();
        List<String> avisos = new ArrayList<>();
        if (p.getMesasIncluidas() != null && uso.mesas() > p.getMesasIncluidas())
            avisos.add("Usa " + uso.mesas() + " mesas; el plan " + p.getNombre() + " incluye " + p.getMesasIncluidas());
        if (p.getProductosIncluidos() != null && uso.productos() > p.getProductosIncluidos())
            avisos.add("Tiene " + uso.productos() + " productos; el plan " + p.getNombre() + " incluye " + p.getProductosIncluidos());
        if (p.getUsuariosIncluidos() != null && uso.usuarios() > p.getUsuariosIncluidos())
            avisos.add("Tiene " + uso.usuarios() + " usuarios activos; el plan " + p.getNombre() + " incluye " + p.getUsuariosIncluidos());
        Long dias = n.getVenceEn() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), n.getVenceEn());
        if (dias != null && dias < 0) avisos.add("La suscripción venció hace " + (-dias) + (dias == -1 ? " día" : " días"));
        else if (dias != null && dias <= 7) avisos.add("La suscripción vence en " + dias + (dias == 1 ? " día" : " días"));
        Map<String, Boolean> ajustes = new LinkedHashMap<>();
        n.getAjustesModulos().forEach((m, v) -> ajustes.put(m.name(), v));
        return new NegocioVista(n.getId(), n.getNombre(), n.getSlug(), p.getCodigo(), p.getNombre(), n.getEstado().name(),
            n.getEstado().getEtiqueta(), n.getCiclo().name(), n.getInicioSuscripcion(), n.getVenceEn(), dias,
            n.getContactoNombre(), n.getContactoTelefono(), n.getContactoEmail(), n.getNotas(), n.getCreadoEn(), ajustes,
            ModuloService.efectivos(n).stream().map(Enum::name).sorted().toList(), uso, avisos);
    }

    private static PlanVista vista(Plan p) {
        return new PlanVista(p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getOrden(), p.getPrecioMensual(),
            p.getPrecioAnual(), p.getPrecioImplementacion(), p.getMesasIncluidas(), p.getProductosIncluidos(),
            p.getUsuariosIncluidos(), p.getActivo(), p.getModulos().stream().map(Enum::name).sorted().toList());
    }

    private static Plan plan(String codigo, String nombre, String descripcion, int orden, int mensual, int anual,
            int implementacion, Integer mesas, Integer productos, Integer usuarios, Set<Modulo> modulos) {
        Plan p = new Plan();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setOrden(orden);
        p.setPrecioMensual(BigDecimal.valueOf(mensual));
        p.setPrecioAnual(BigDecimal.valueOf(anual));
        p.setPrecioImplementacion(BigDecimal.valueOf(implementacion));
        p.setMesasIncluidas(mesas);
        p.setProductosIncluidos(productos);
        p.setUsuariosIncluidos(usuarios);
        p.setActivo(true);
        p.setModulos(EnumSet.copyOf(modulos.isEmpty() ? EnumSet.noneOf(Modulo.class) : modulos));
        return p;
    }

    private Negocio buscar(Long id) {
        if (id == null) throw new NoSuchElementException("Negocio no encontrado");
        return negocios.findById(id).orElseThrow(() -> new NoSuchElementException("Negocio no encontrado"));
    }

    private Plan planActivo(String codigo) {
        Plan p = planes.findById(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Elige un plan válido"));
        if (!Boolean.TRUE.equals(p.getActivo())) throw new IllegalArgumentException("El plan " + p.getNombre() + " no está disponible");
        return p;
    }

    private Plan planActivoOActual(String codigo, Long negocioId) {
        Plan p = planes.findById(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Elige un plan válido"));
        if (Boolean.TRUE.equals(p.getActivo())) return p;
        String actual = ejecutor.en(NegocioActual.RAIZ, () -> buscar(negocioId).getPlan().getCodigo());
        if (actual.equals(p.getCodigo())) return p;
        throw new IllegalArgumentException("El plan " + p.getNombre() + " no está disponible");
    }

    private static Set<Modulo> modulos(List<String> codigos) {
        Set<Modulo> resultado = EnumSet.noneOf(Modulo.class);
        if (codigos != null) for (String c : codigos) resultado.add(modulo(c));
        return resultado;
    }

    private static Modulo modulo(String codigo) {
        try {
            return Modulo.valueOf(codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Módulo desconocido: " + codigo);
        }
    }

    private static String nombres(Set<Modulo> modulos) {
        return modulos.isEmpty() ? "ninguno" : String.join(", ", modulos.stream().map(Modulo::getEtiqueta).toList());
    }

    private static EstadoSuscripcion estado(String valor) {
        try {
            return EstadoSuscripcion.valueOf(valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de suscripción no válido");
        }
    }

    private static CicloFacturacion ciclo(String valor) {
        if (valor == null || valor.isBlank()) return CicloFacturacion.MENSUAL;
        try {
            return CicloFacturacion.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ciclo de facturación no válido");
        }
    }

    static String sugerirSlug(String nombre) {
        String base = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > 40) base = base.substring(0, 40).replaceAll("-+$", "");
        return base.length() < 2 ? "negocio" : base;
    }

    private static String slug(String valor) {
        String s = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (s.length() < 2 || s.length() > 40 || !SLUG.matcher(s).matches())
            throw new IllegalArgumentException("La dirección del negocio debe tener de 2 a 40 letras minúsculas, números o guiones");
        return s;
    }

    private static String username(String valor) {
        String u = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(u).matches())
            throw new IllegalArgumentException("El usuario administrador debe tener de 3 a 40 letras minúsculas, números, punto, guion o guion bajo");
        return u;
    }

    private static String email(String valor) {
        String e = opcional(valor, 120);
        if (e != null && !e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("El correo de contacto no es válido");
        return e;
    }

    private static String texto(String valor, String campo, int max) {
        if (valor == null || valor.isBlank() || valor.trim().length() > max)
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio y admite hasta " + max + " caracteres");
        return valor.trim();
    }

    private static String opcional(String valor, int max) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (limpio.length() > max) throw new IllegalArgumentException("Un campo supera los " + max + " caracteres permitidos");
        return limpio;
    }

    private static BigDecimal precio(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException("El precio " + campo + " debe ser cero o mayor");
        return valor;
    }

    private static Integer limite(Integer valor, String campo) {
        if (valor != null && valor < 0) throw new IllegalArgumentException("El límite sugerido de " + campo + " no puede ser negativo");
        return valor;
    }

    private String generarTemporal() {
        StringBuilder clave = new StringBuilder();
        for (int bloque = 0; bloque < 3; bloque++) {
            if (bloque > 0) clave.append('-');
            for (int i = 0; i < 3; i++) clave.append(LETRAS.charAt(random.nextInt(LETRAS.length())));
            clave.append(DIGITOS.charAt(random.nextInt(DIGITOS.length())));
        }
        return clave.toString();
    }
}
