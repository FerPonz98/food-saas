package com.sistema.pedidosCori.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistema.pedidosCori.service.MarcaPlataformaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class MarcaPlataformaController {
    private final MarcaPlataformaService marca;

    @GetMapping("/img/plataforma/logo")
    ResponseEntity<byte[]> logo(@RequestParam(required = false) Long v) throws IOException {
        MarcaPlataformaService.Logo logo = marca.logo();
        byte[] datos = logo.datos();
        String mime = logo.mimeType();
        if (datos == null) {
            datos = new ClassPathResource("static/img/logo-default.svg").getInputStream().readAllBytes();
            mime = "image/svg+xml";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition(ContentDisposition.inline().filename("plataforma").build());
        if ("image/svg+xml".equals(mime)) headers.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        headers.setCacheControl(v != null && v.equals(logo.version())
            ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable() : CacheControl.noCache());
        return ResponseEntity.ok().headers(headers).body(datos);
    }

    @GetMapping("/api/superadmin/marca")
    MarcaPlataformaService.Marca marca() { return marca.marca(); }

    @PutMapping("/api/superadmin/marca")
    MarcaPlataformaService.Marca actualizar(@RequestBody MarcaPlataformaService.DatosMarca datos) {
        return marca.actualizar(datos);
    }

    @PutMapping(value = "/api/superadmin/marca/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MarcaPlataformaService.Marca logo(@RequestParam("archivo") MultipartFile archivo) {
        return marca.actualizarLogo(archivo);
    }

    @DeleteMapping("/api/superadmin/marca/logo")
    MarcaPlataformaService.Marca quitarLogo() { return marca.quitarLogo(); }

    @GetMapping("/api/superadmin/apariencia/negocios")
    List<MarcaPlataformaService.AparienciaNegocio> negocios() { return marca.negocios(); }

    @PutMapping("/api/superadmin/negocios/{id}/colores")
    MarcaPlataformaService.AparienciaNegocio colores(@PathVariable Long id, @RequestBody MarcaPlataformaService.Colores colores) {
        return marca.coloresDe(id, colores);
    }
}
