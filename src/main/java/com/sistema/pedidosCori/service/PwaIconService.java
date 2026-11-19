package com.sistema.pedidosCori.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class PwaIconService {
    public static final Set<Integer> TAMANOS = Set.of(180, 192, 512);
    private static final double ESCALA_NORMAL = 0.84;
    private static final double ESCALA_MASCARA = 0.62;

    private final ConfiguracionNegocioService configuracion;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public long versionLogo() {
        return configuracion.logoActual().getLogoVersion();
    }

    public byte[] icono(int tamano, boolean mascara) {
        if (!TAMANOS.contains(tamano)) throw new IllegalArgumentException("Tamaño de ícono no soportado");
        ConfiguracionNegocio c = configuracion.logoActual();
        String negocio = String.valueOf(c.getId());
        String version = negocio + ":" + c.getLogoVersion() + ":";
        String clave = version + tamano + ":" + mascara;
        return cache.computeIfAbsent(clave, k -> {
            cache.keySet().removeIf(otra -> otra.startsWith(negocio + ":") && !otra.startsWith(version));
            return dibujar(logo(c), tamano, mascara);
        });
    }

    private BufferedImage logo(ConfiguracionNegocio c) {
        byte[] datos = c.getLogoDatos();
        if (datos != null) {
            try {
                BufferedImage imagen = "image/svg+xml".equals(c.getLogoMimeType())
                    ? svg(new ByteArrayInputStream(datos))
                    : ImageIO.read(new ByteArrayInputStream(datos));
                if (imagen != null) return imagen;
            } catch (Exception | LinkageError e) {
                log.warn("No se pudo convertir el logo del negocio en ícono, se usa el predeterminado: {}", e.toString());
            }
        }
        try (InputStream in = new ClassPathResource("static/img/logo-default.svg").getInputStream()) {
            return svg(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    private static BufferedImage svg(InputStream in) {
        SVGDocument doc = new SVGLoader().load(in, null, LoaderContext.createDefault());
        if (doc == null) return null;
        FloatSize tam = doc.size();
        double base = Math.max(tam.width, tam.height);
        if (base <= 0) return null;
        double factor = 512 / base;
        int ancho = Math.max(1, (int) Math.round(tam.width * factor));
        int alto = Math.max(1, (int) Math.round(tam.height * factor));
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            calidad(g);
            doc.render(null, g, new ViewBox(0, 0, ancho, alto));
        } finally {
            g.dispose();
        }
        return img;
    }

    private static byte[] dibujar(BufferedImage logo, int tamano, boolean mascara) {
        BufferedImage lienzo = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            calidad(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tamano, tamano);
            double espacio = tamano * (mascara ? ESCALA_MASCARA : ESCALA_NORMAL);
            double factor = espacio / Math.max(logo.getWidth(), logo.getHeight());
            int ancho = (int) Math.round(logo.getWidth() * factor);
            int alto = (int) Math.round(logo.getHeight() * factor);
            g.drawImage(logo, (tamano - ancho) / 2, (tamano - alto) / 2, ancho, alto, null);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void calidad(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
}
