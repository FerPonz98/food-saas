package com.sistema.pedidosCori.controller;

import com.sistema.pedidosCori.models.entity.Modulo;

import com.sistema.pedidosCori.config.RequiereModulo;

import java.util.NoSuchElementException;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.sistema.pedidosCori.service.CartaPublicaService;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.QrService;

import lombok.RequiredArgsConstructor;
@RestController
@RequiredArgsConstructor
@RequiereModulo(Modulo.CARTA_QR)
public class CartaQrController {
    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");

    private final ConfiguracionNegocioService configuracion;
    private final CartaPublicaService carta;
    private final QrService qr;
    private final ModuloService modulos;

    @GetMapping("/api/admin/configuracion/wifi")
    public ConfiguracionNegocioService.Wifi wifi() {
        return configuracion.wifi();
    }

    @PutMapping("/api/admin/configuracion/wifi")
    public ConfiguracionNegocioService.Wifi actualizarWifi(@RequestBody ConfiguracionNegocioService.Wifi wifi) {
        return configuracion.actualizarWifi(wifi);
    }

    @GetMapping("/admin/qr/mesa/{numero}.svg")
    public ResponseEntity<String> qrMesa(@PathVariable int numero) {
        carta.validarMesa(numero);
        return svg(qr.svg(base() + "/n/" + slug() + "/mesa/" + numero));
    }

    @GetMapping("/admin/qr/carta.svg")
    public ResponseEntity<String> qrCarta() {
        return svg(qr.svg(base() + "/n/" + slug() + "/carta"));
    }

    @GetMapping("/admin/qr/wifi.svg")
    public ResponseEntity<String> qrWifi() {
        ConfiguracionNegocioService.Wifi w = configuracion.wifi();
        if (!w.configurado()) throw new NoSuchElementException("Primero configura el Wi-Fi");
        return svg(qr.svg(QrService.contenidoWifi(w.nombre(), w.clave())));
    }
    private String slug() {
        return modulos.actual().map(ModuloService.InfoNegocio::slug)
            .orElseThrow(() -> new NoSuchElementException("Negocio no encontrado"));
    }

    static String base() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static ResponseEntity<String> svg(String contenido) {
        return ResponseEntity.ok().contentType(SVG).cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff").body(contenido);
    }
}
