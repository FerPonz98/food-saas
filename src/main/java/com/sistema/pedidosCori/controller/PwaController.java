package com.sistema.pedidosCori.controller;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.PwaIconService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PwaController {
    static final MediaType MANIFEST = MediaType.parseMediaType("application/manifest+json");

    private final ConfiguracionNegocioService configuracion;
    private final PwaIconService iconos;
    private final ModuloService modulos;

    @GetMapping({ "/manifest.webmanifest", "/n/{slug}/manifest.webmanifest" })
    public ResponseEntity<Map<String, Object>> manifest(@PathVariable(required = false) String slug) {
        Optional<ModuloService.InfoNegocio> info = negocio();
        if (info.isEmpty()) return ResponseEntity.notFound().build();
        String prefijo = "/n/" + info.get().slug();
        ConfiguracionNegocioVista n = configuracion.vista(info.get().id());
        long v = NegocioActual.ejecutarComo(info.get().id(), iconos::versionLogo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", prefijo + "/");
        m.put("name", n.getNombreNegocio());
        m.put("short_name", corto(n.getNombreNegocio()));
        if (n.getEslogan() != null && !n.getEslogan().isBlank()) m.put("description", n.getEslogan());
        m.put("lang", "es");
        m.put("start_url", prefijo + "/login");
        m.put("scope", "/");
        m.put("display", "standalone");
        m.put("background_color", "#FFFFFF");
        m.put("theme_color", n.getColorPrimario());
        m.put("icons", List.of(
            icono(prefijo, 192, false, v), icono(prefijo, 512, false, v),
            icono(prefijo, 192, true, v), icono(prefijo, 512, true, v)));
        return ResponseEntity.ok().contentType(MANIFEST).cacheControl(CacheControl.noCache()).body(m);
    }

    @GetMapping({ "/pwa/icon-{tamano:\\d+}.png", "/n/{slug}/pwa/icon-{tamano:\\d+}.png" })
    public ResponseEntity<byte[]> icono(@PathVariable int tamano, @RequestParam(required = false) Long v) {
        return png(tamano, false, v);
    }

    @GetMapping({ "/pwa/icon-maskable-{tamano:\\d+}.png", "/n/{slug}/pwa/icon-maskable-{tamano:\\d+}.png" })
    public ResponseEntity<byte[]> iconoMascara(@PathVariable int tamano, @RequestParam(required = false) Long v) {
        return png(tamano, true, v);
    }

    private Optional<ModuloService.InfoNegocio> negocio() {
        Optional<ModuloService.InfoNegocio> info = modulos.actual();
        if (info.isEmpty() && !NegocioActual.esRaiz()) info = modulos.unicoNegocio().flatMap(modulos::info);
        return info.filter(i -> i.tiene(Modulo.APP_INSTALABLE));
    }

    private ResponseEntity<byte[]> png(int tamano, boolean mascara, Long v) {
        if (!PwaIconService.TAMANOS.contains(tamano)) return ResponseEntity.notFound().build();
        Optional<ModuloService.InfoNegocio> info = modulos.actual();
        if (info.isEmpty() && !NegocioActual.esRaiz()) info = modulos.unicoNegocio().flatMap(modulos::info);
        long negocio = info.map(ModuloService.InfoNegocio::id).orElse(NegocioActual.NINGUNO);
        return NegocioActual.ejecutarComo(negocio, () -> {
            CacheControl cache = v != null && v == iconos.versionLogo()
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(cache)
                .body(iconos.icono(tamano, mascara));
        });
    }

    private static Map<String, String> icono(String prefijo, int tamano, boolean mascara, long v) {
        return Map.of(
            "src", prefijo + "/pwa/icon-" + (mascara ? "maskable-" : "") + tamano + ".png?v=" + v,
            "sizes", tamano + "x" + tamano,
            "type", "image/png",
            "purpose", mascara ? "maskable" : "any");
    }

    static String corto(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.length() <= 12) return limpio;
        String primera = limpio.split("\\s+")[0];
        return primera.length() <= 12 ? primera : limpio.substring(0, 12);
    }
}
