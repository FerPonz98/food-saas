package com.sistema.pedidosCori.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IConfiguracionNegocioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioRequest;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Modulo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracionNegocioService {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private final IConfiguracionNegocioDao dao;
    private final LogoValidator logoValidator;
    private final AuditoriaService auditoria;

    public static final int MAXIMO_PUNTOS_ATENCION = 500;
    private final ModuloService modulos;

    @Transactional
    public void crearPara(long negocioId, String nombre) {
        if (dao.existsById(negocioId)) return;
        ConfiguracionNegocio c = nuevaPorDefecto();
        c.setId(negocioId);
        if (nombre != null && !nombre.isBlank()) c.setNombreNegocio(nombre.trim());
        dao.save(c);
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocioVista vista() {
        Optional<Long> negocio = NegocioActual.id();
        if (negocio.isPresent()) return vista(negocio.get());
        if (NegocioActual.esRaiz()) return vistaPlataforma();
        return vistaPredeterminada();
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocioVista vista(long negocioId) {
        ConfiguracionNegocio c = dao.findById(negocioId).orElseGet(this::nuevaPorDefecto);
        return vista(c, modulos.info(negocioId).orElse(null));
    }

    public ConfiguracionNegocioVista vistaPlataforma() {
        ConfiguracionNegocio c = nuevaPorDefecto();
        c.setNombreNegocio("Plataforma");
        c.setEslogan("Administración de negocios y planes");
        return vista(c, null);
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocioVista vistaPredeterminada() {
        return modulos.unicoNegocio().map(this::vista).orElseGet(() -> vista(nuevaPorDefecto(), null));
    }

    private ConfiguracionNegocioVista vista(ConfiguracionNegocio c, ModuloService.InfoNegocio info) {
        Map<String, String> tema = TemaNegocio.variables(c.getColorPrimario(), c.getColorSecundario(), c.getColorAcento());
        String logo = info == null ? "/branding/logo?v=" + c.getLogoVersion() : "/n/" + info.slug() + "/logo?v=" + c.getLogoVersion();
        return new ConfiguracionNegocioVista(c.getNombreNegocio(), c.getEslogan(), c.getColorPrimario(),
            c.getColorSecundario(), c.getColorAcento(), c.getPuntoAtencionSingular(), c.getPuntoAtencionPlural(),
            c.getEtiquetaParaLlevar(), c.getEtiquetaRolMesero(), c.getEtiquetaRolCocina(), c.getSimboloMoneda(),
            c.getCantidadPuntosAtencion(), logo, c.getLogoMimeType(), tema,
            info == null ? null : info.slug(), info == null ? null : info.planCodigo(), info == null ? null : info.planNombre(),
            info == null ? java.util.List.of() : info.modulos().stream().map(Enum::name).sorted().toList());
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocio logoDe(long negocioId) {
        return dao.findById(negocioId).orElseGet(this::nuevaPorDefecto);
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocio logoActual() {
        Optional<Long> negocio = NegocioActual.id().or(modulos::unicoNegocio);
        return negocio.map(this::logoDe).orElseGet(this::nuevaPorDefecto);
    }

    @Transactional
    public ConfiguracionNegocioVista actualizar(ConfiguracionNegocioRequest r) {
        if (r == null) throw new IllegalArgumentException("Configuración requerida");
        String nombre = texto(r.nombreNegocio(), "nombre del negocio", 120);
        String eslogan = r.eslogan() == null ? "" : r.eslogan().trim();
        if (eslogan.length() > 240) throw new IllegalArgumentException("El eslogan no puede superar 240 caracteres");
        String primario = color(r.colorPrimario(), "primario");
        String secundario = color(r.colorSecundario(), "secundario");
        String acento = color(r.colorAcento(), "acento");
        String singular = texto(r.puntoAtencionSingular(), "punto de atención singular", 40);
        String plural = texto(r.puntoAtencionPlural(), "punto de atención plural", 40);
        String llevar = texto(r.etiquetaParaLlevar(), "etiqueta para llevar", 40);
        String mesero = texto(r.etiquetaRolMesero(), "etiqueta del rol mesero", 40);
        String cocina = texto(r.etiquetaRolCocina(), "etiqueta del rol cocina", 40);
        String moneda = texto(r.simboloMoneda(), "símbolo de moneda", 12);
        if (r.cantidadPuntosAtencion() == null || r.cantidadPuntosAtencion() < 0 || r.cantidadPuntosAtencion() > MAXIMO_PUNTOS_ATENCION)
            throw new IllegalArgumentException("La cantidad de puntos de atención debe estar entre 0 y " + MAXIMO_PUNTOS_ATENCION);
        ConfiguracionNegocio c = actual();
        if (!modulos.tiene(Modulo.APARIENCIA_AVANZADA)) {
            boolean cambiaAvanzado = !Objects.equals(Objects.toString(c.getEslogan(), ""), eslogan)
                || !c.getColorPrimario().equalsIgnoreCase(primario) || !c.getColorSecundario().equalsIgnoreCase(secundario)
                || !c.getColorAcento().equalsIgnoreCase(acento)
                || !c.getPuntoAtencionSingular().equals(singular) || !c.getPuntoAtencionPlural().equals(plural)
                || !c.getEtiquetaParaLlevar().equals(llevar) || !c.getEtiquetaRolMesero().equals(mesero)
                || !c.getEtiquetaRolCocina().equals(cocina);
            if (cambiaAvanzado) throw new ModuloService.ModuloNoIncluido(Modulo.APARIENCIA_AVANZADA);
        }
        c.setNombreNegocio(nombre); c.setEslogan(eslogan);
        c.setColorPrimario(primario); c.setColorSecundario(secundario); c.setColorAcento(acento);
        c.setPuntoAtencionSingular(singular); c.setPuntoAtencionPlural(plural);
        c.setEtiquetaParaLlevar(llevar); c.setEtiquetaRolMesero(mesero);
        c.setEtiquetaRolCocina(cocina); c.setSimboloMoneda(moneda);
        c.setCantidadPuntosAtencion(r.cantidadPuntosAtencion());
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.CONFIGURACION_ACTUALIZADA, "ConfiguracionNegocio", c.getId(),
            "Nombre: " + nombre + " · " + r.cantidadPuntosAtencion() + " " + plural.toLowerCase(java.util.Locale.ROOT)
                + " · Colores " + primario + ", " + secundario + ", " + acento);
        return vista();
    }

    @Transactional
    public ConfiguracionNegocioVista actualizarLogo(org.springframework.web.multipart.MultipartFile archivo) {
        LogoValidator.LogoValidado logo = logoValidator.validar(archivo);
        ConfiguracionNegocio c = actual();
        c.setLogoDatos(logo.datos());
        c.setLogoMimeType(logo.mimeType());
        c.setLogoVersion(c.getLogoVersion() + 1);
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.LOGO_ACTUALIZADO, "ConfiguracionNegocio", c.getId(),
            "Formato " + logo.mimeType() + " · " + (logo.datos() == null ? 0 : (logo.datos().length + 1023) / 1024) + " KB");
        return vista();
    }

    @Transactional
    public ConfiguracionNegocioVista restablecer() {
        ConfiguracionNegocio c = actual();
        ConfiguracionNegocio defaults = nuevaPorDefecto();
        c.setNombreNegocio(defaults.getNombreNegocio()); c.setEslogan(defaults.getEslogan());
        c.setColorPrimario(defaults.getColorPrimario()); c.setColorSecundario(defaults.getColorSecundario());
        c.setColorAcento(defaults.getColorAcento());
        c.setPuntoAtencionSingular(defaults.getPuntoAtencionSingular());
        c.setPuntoAtencionPlural(defaults.getPuntoAtencionPlural());
        c.setEtiquetaParaLlevar(defaults.getEtiquetaParaLlevar());
        c.setEtiquetaRolMesero(defaults.getEtiquetaRolMesero());
        c.setEtiquetaRolCocina(defaults.getEtiquetaRolCocina());
        c.setSimboloMoneda(defaults.getSimboloMoneda());
        c.setCantidadPuntosAtencion(defaults.getCantidadPuntosAtencion());
        c.setLogoDatos(null); c.setLogoMimeType(null);
        c.setLogoVersion(c.getLogoVersion() + 1);
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.CONFIGURACION_RESTABLECIDA, "ConfiguracionNegocio", c.getId(),
            "Se restauraron los valores predeterminados y el logo");
        return vista();
    }
    public record Wifi(String nombre, String clave) {
        public boolean configurado() { return nombre != null && !nombre.isBlank(); }
    }

    @Transactional(readOnly = true)
    public Wifi wifi() {
        ConfiguracionNegocio c = actual();
        return new Wifi(c.getWifiNombre() == null ? "" : c.getWifiNombre(), c.getWifiClave() == null ? "" : c.getWifiClave());
    }

    @Transactional
    public Wifi actualizarWifi(Wifi w) {
        String nombre = w == null || w.nombre() == null ? "" : w.nombre().trim();
        String clave = w == null || w.clave() == null ? "" : w.clave();
        if (nombre.length() > 32) throw new IllegalArgumentException("El nombre del Wi-Fi admite hasta 32 caracteres");
        if (clave.length() > 63) throw new IllegalArgumentException("La clave del Wi-Fi admite hasta 63 caracteres");
        if (nombre.isEmpty() && !clave.isEmpty()) throw new IllegalArgumentException("Escribe el nombre del Wi-Fi");
        if (!clave.isEmpty() && clave.length() < 8)
            throw new IllegalArgumentException("Las claves de Wi-Fi tienen al menos 8 caracteres");
        ConfiguracionNegocio c = actual();
        c.setWifiNombre(nombre.isEmpty() ? null : nombre);
        c.setWifiClave(clave.isEmpty() ? null : clave);
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.WIFI_ACTUALIZADO, "ConfiguracionNegocio", c.getId(),
            nombre.isEmpty() ? "Wi-Fi oculto en la carta" : "Red «" + nombre + "»" + (clave.isEmpty() ? " sin clave" : ""));
        return wifi();
    }

    private ConfiguracionNegocio actual() {
        long negocio = NegocioActual.requerido();
        return dao.findById(negocio).orElseThrow(() -> new IllegalStateException("Falta la configuración del negocio"));
    }

    private String texto(String valor, String campo, int max) {
        if (valor == null || valor.isBlank() || valor.trim().length() > max)
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio y admite hasta " + max + " caracteres");
        return valor.trim();
    }

    private String color(String valor, String campo) {
        if (valor == null || !HEX.matcher(valor).matches())
            throw new IllegalArgumentException("El color " + campo + " debe tener formato #RRGGBB");
        return valor.toUpperCase(java.util.Locale.ROOT);
    }

    private ConfiguracionNegocio nuevaPorDefecto() {
        ConfiguracionNegocio c = new ConfiguracionNegocio();
        c.setNombreNegocio("Gestión Comercial"); c.setEslogan("");
        c.setColorPrimario("#24324A"); c.setColorSecundario("#64748B"); c.setColorAcento("#C56A3D");
        c.setPuntoAtencionSingular("Mesa"); c.setPuntoAtencionPlural("Mesas");
        c.setEtiquetaParaLlevar("Para llevar"); c.setEtiquetaRolMesero("Mesero");
        c.setEtiquetaRolCocina("Cocina"); c.setSimboloMoneda("Bs.");
        c.setCantidadPuntosAtencion(20);
        return c;
    }
}
