package com.sistema.pedidosCori.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.config.RequiereModulo;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.service.CartaPublicaService;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.LlamadoService;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.PlatoImagenService;
import com.sistema.pedidosCori.service.SolicitudPedidoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CartaPublicaController {
    private final CartaPublicaService carta;
    private final ConfiguracionNegocioService configuracion;
    private final ModuloService modulos;

    @GetMapping({ "/carta", "/mesa/{numero}" })
    public String anterior(@PathVariable(required = false) String numero) {
        String slug = modulos.principal().flatMap(modulos::info).map(ModuloService.InfoNegocio::slug)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        return "redirect:/n/" + slug + (numero == null ? "/carta" : "/mesa/" + numero);
    }

    @GetMapping("/n/{slug}/carta")
    @RequiereModulo(Modulo.CARTA_QR)
    public String general(@PathVariable String slug, Model model) {
        model.addAttribute("mesa", null);
        model.addAttribute("wifiDisponible", false);
        return "carta";
    }

    @GetMapping("/n/{slug}/mesa/{numero}")
    @RequiereModulo(Modulo.CARTA_QR)
    public String mesa(@PathVariable String slug, @PathVariable String numero, Model model) {
        int n;
        try {
            n = Integer.parseInt(numero);
            carta.validarMesa(n);
        } catch (NumberFormatException | NoSuchElementException e) {
            return "redirect:/n/" + slug + "/carta";
        }
        model.addAttribute("mesa", n);
        model.addAttribute("wifiDisponible", configuracion.wifi().configurado());
        return "carta";
    }
}

@RestController
@RequestMapping("/api/publico/{slug}")
@RequiredArgsConstructor
@RequiereModulo(Modulo.CARTA_QR)
class CartaPublicaApiController {
    private final CartaPublicaService carta;
    private final ConfiguracionNegocioService configuracion;
    private final LlamadoService llamados;
    private final SolicitudPedidoService solicitudes;
    private final PlatoImagenService imagenes;

    public record LlamadoRequest(String motivo, String metodoPago) {
    }

    @GetMapping("/carta")
    ResponseEntity<CartaPublicaService.CartaPublica> verCarta() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(carta.carta());
    }

    @GetMapping("/mesa/{numero}/wifi")
    ResponseEntity<Map<String, String>> wifi(@PathVariable int numero) {
        carta.validarMesa(numero);
        ConfiguracionNegocioService.Wifi w = configuracion.wifi();
        if (!w.configurado()) throw new NoSuchElementException("El local no tiene Wi-Fi para clientes");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("nombre", w.nombre(), "clave", w.clave()));
    }

    @GetMapping("/mesa/{numero}/llamados")
    @RequiereModulo(Modulo.LLAMADOS)
    ResponseEntity<List<LlamadoService.EstadoCliente>> estado(@PathVariable int numero) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(llamados.estadoMesa(numero));
    }

    @PostMapping("/mesa/{numero}/llamados")
    @RequiereModulo(Modulo.LLAMADOS)
    LlamadoService.EstadoCliente llamar(@PathVariable int numero, @RequestBody LlamadoRequest r, HttpServletRequest req) {
        return llamados.llamar(numero, r == null ? null : r.motivo(), r == null ? null : r.metodoPago(), req.getRemoteAddr());
    }

    @PostMapping("/mesa/{numero}/pedidos")
    @RequiereModulo(Modulo.PEDIDOS_QR)
    ResponseEntity<SolicitudPedidoService.EstadoCliente> pedir(@PathVariable int numero,
            @RequestBody SolicitudPedidoService.SolicitudNueva r, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(solicitudes.crear(numero, r, req.getRemoteAddr()));
    }

    @GetMapping("/mesa/{numero}/pedidos/{token}")
    @RequiereModulo(Modulo.PEDIDOS_QR)
    ResponseEntity<SolicitudPedidoService.EstadoCliente> estadoPedido(@PathVariable int numero, @PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(solicitudes.estadoCliente(numero, token));
    }

    @GetMapping("/platos/{id}/imagen")
    ResponseEntity<byte[]> foto(@PathVariable Long id, @RequestParam(required = false) Long v) {
        carta.validarFotoPublica(id);
        PlatoImagenService.ImagenPlato imagen = imagenes.obtener(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(imagen.mimeType()));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition(ContentDisposition.inline().filename("producto-" + id).build());
        headers.setCacheControl(v != null && v.equals(imagen.version())
            ? CacheControl.maxAge(Duration.ofDays(30)).cachePublic()
            : CacheControl.noCache());
        return ResponseEntity.ok().headers(headers).body(imagen.datos());
    }
}
