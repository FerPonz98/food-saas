package com.sistema.pedidosCori.service;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sistema.pedidosCori.models.dao.IConfiguracionNegocioDao;
import com.sistema.pedidosCori.models.dao.IMarcaPlataformaDao;
import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;
import com.sistema.pedidosCori.models.entity.MarcaPlataforma;
import com.sistema.pedidosCori.models.entity.Negocio;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarcaPlataformaService {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private final IMarcaPlataformaDao marcas;
    private final IConfiguracionNegocioDao configuraciones;
    private final INegocioDao negocios;
    private final EjecutorNegocio ejecutor;
    private final LogoValidator logoValidator;
    private final AuditoriaService auditoria;

    public record Marca(String nombre, String eslogan, String colorPrimario, String colorSecundario, String colorAcento,
            String logoUrl, boolean logoPropio) {
    }

    public record DatosMarca(String nombre, String eslogan, String colorPrimario, String colorSecundario, String colorAcento) {
    }

    public record Colores(String colorPrimario, String colorSecundario, String colorAcento) {
    }

    public record AparienciaNegocio(Long id, String nombre, String slug, String nombreVisible, String colorPrimario,
            String colorSecundario, String colorAcento, String logoUrl) {
    }

    public record Logo(byte[] datos, String mimeType, Long version) {
    }

    public MarcaPlataforma actual() {
        return ejecutor.en(NegocioActual.RAIZ, this::leer);
    }

    public Marca marca() {
        return vista(actual());
    }

    public Marca actualizar(DatosMarca d) {
        if (d == null) throw new IllegalArgumentException("Datos requeridos");
        String nombre = texto(d.nombre(), 120, "El nombre");
        if (nombre == null) throw new IllegalArgumentException("Escribe el nombre de la plataforma");
        String eslogan = texto(d.eslogan(), 240, "El eslogan");
        String p = color(d.colorPrimario(), "primario");
        String s = color(d.colorSecundario(), "secundario");
        String a = color(d.colorAcento(), "de acento");
        MarcaPlataforma m = ejecutor.en(NegocioActual.RAIZ, () -> {
            MarcaPlataforma x = leer();
            x.setNombre(nombre);
            x.setEslogan(eslogan);
            x.setColorPrimario(p);
            x.setColorSecundario(s);
            x.setColorAcento(a);
            return marcas.save(x);
        });
        auditoria.registrarPlataforma("MARCA_PLATAFORMA", null, nombre + " · Colores " + p + ", " + s + ", " + a);
        return vista(m);
    }

    public Marca actualizarLogo(MultipartFile archivo) {
        LogoValidator.LogoValidado logo = logoValidator.validar(archivo);
        MarcaPlataforma m = ejecutor.en(NegocioActual.RAIZ, () -> {
            MarcaPlataforma x = leer();
            x.setLogoDatos(logo.datos());
            x.setLogoMimeType(logo.mimeType());
            x.setLogoVersion(x.getLogoVersion() + 1);
            return marcas.save(x);
        });
        auditoria.registrarPlataforma("LOGO_PLATAFORMA", null, "Formato " + logo.mimeType());
        return vista(m);
    }

    public Marca quitarLogo() {
        MarcaPlataforma m = ejecutor.en(NegocioActual.RAIZ, () -> {
            MarcaPlataforma x = leer();
            x.setLogoDatos(null);
            x.setLogoMimeType(null);
            x.setLogoVersion(x.getLogoVersion() + 1);
            return marcas.save(x);
        });
        auditoria.registrarPlataforma("LOGO_PLATAFORMA", null, "Se quitó el logo de la plataforma");
        return vista(m);
    }

    public Logo logo() {
        MarcaPlataforma m = actual();
        return new Logo(m.getLogoDatos(), m.getLogoMimeType(), m.getLogoVersion());
    }

    public List<AparienciaNegocio> negocios() {
        List<Negocio> lista = ejecutor.en(NegocioActual.RAIZ, () -> negocios.findAll());
        return lista.stream().map(n -> ejecutor.en(n.getId(), () -> {
            ConfiguracionNegocio c = configuraciones.findById(n.getId()).orElse(null);
            return c == null
                ? new AparienciaNegocio(n.getId(), n.getNombre(), n.getSlug(), n.getNombre(), null, null, null, null)
                : new AparienciaNegocio(n.getId(), n.getNombre(), n.getSlug(), c.getNombreNegocio(), c.getColorPrimario(),
                    c.getColorSecundario(), c.getColorAcento(), "/n/" + n.getSlug() + "/logo?v=" + c.getLogoVersion());
        })).toList();
    }

    public AparienciaNegocio coloresDe(long negocioId, Colores colores) {
        if (colores == null) throw new IllegalArgumentException("Datos requeridos");
        String p = color(colores.colorPrimario(), "primario");
        String s = color(colores.colorSecundario(), "secundario");
        String a = color(colores.colorAcento(), "de acento");
        Negocio n = ejecutor.en(NegocioActual.RAIZ, () -> negocios.findById(negocioId))
            .orElseThrow(() -> new NoSuchElementException("Negocio no encontrado"));
        AparienciaNegocio r = ejecutor.en(negocioId, () -> {
            ConfiguracionNegocio c = configuraciones.findById(negocioId)
                .orElseThrow(() -> new NoSuchElementException("El negocio aún no tiene configuración"));
            c.setColorPrimario(p);
            c.setColorSecundario(s);
            c.setColorAcento(a);
            configuraciones.save(c);
            auditoria.registrarComo("soporte", AccionAuditoria.CONFIGURACION_ACTUALIZADA, "ConfiguracionNegocio", c.getId(),
                () -> "Colores cambiados por soporte: " + p + ", " + s + ", " + a);
            return new AparienciaNegocio(n.getId(), n.getNombre(), n.getSlug(), c.getNombreNegocio(), p, s, a,
                "/n/" + n.getSlug() + "/logo?v=" + c.getLogoVersion());
        });
        auditoria.registrarPlataforma("COLORES_NEGOCIO", negocioId, n.getNombre() + " · " + p + ", " + s + ", " + a);
        return r;
    }

    private MarcaPlataforma leer() {
        return marcas.findById(MarcaPlataforma.ID).orElseGet(() -> marcas.save(new MarcaPlataforma()));
    }

    private static Marca vista(MarcaPlataforma m) {
        return new Marca(m.getNombre(), m.getEslogan(), m.getColorPrimario(), m.getColorSecundario(), m.getColorAcento(),
            "/img/plataforma/logo?v=" + m.getLogoVersion(), m.getLogoDatos() != null);
    }

    private static String color(String valor, String campo) {
        if (valor == null || !HEX.matcher(valor).matches())
            throw new IllegalArgumentException("El color " + campo + " debe tener formato #RRGGBB");
        return valor.toUpperCase(Locale.ROOT);
    }

    private static String texto(String valor, int max, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (limpio.length() > max) throw new IllegalArgumentException(campo + " admite hasta " + max + " caracteres");
        return limpio;
    }
}
