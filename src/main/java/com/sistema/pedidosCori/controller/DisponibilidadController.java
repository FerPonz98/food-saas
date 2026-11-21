package com.sistema.pedidosCori.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.config.RequiereModulo;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.service.DisponibilidadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/disponibilidad")
@RequiereModulo(Modulo.DISPONIBILIDAD)
@RequiredArgsConstructor
public class DisponibilidadController {
    private final DisponibilidadService disponibilidad;

    public record AjusteRequest(Integer delta, Integer cantidad, String motivo) {
    }

    public record PausaRequest(Boolean pausado, String motivo) {
    }

    public record ModalidadRequest(String tipo, Integer cantidad, String motivo) {
    }

    @GetMapping
    public ResponseEntity<List<DisponibilidadService.ProductoPanel>> panel() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(disponibilidad.panel());
    }

    @GetMapping("/movimientos")
    public ResponseEntity<List<DisponibilidadService.MovimientoVista>> movimientos() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(disponibilidad.movimientosDeHoy());
    }

    @PutMapping("/platos/{id}/cantidad")
    public DisponibilidadService.ProductoPanel ajustar(@PathVariable Long id, @RequestBody AjusteRequest r, Authentication auth) {
        if (r == null) throw new IllegalArgumentException("Indica la cantidad");
        return disponibilidad.ajustar(id, r.delta(), r.cantidad(), r.motivo(), auth.getName());
    }

    @PutMapping("/presentaciones/{id}/cantidad")
    public DisponibilidadService.ProductoPanel ajustarPresentacion(@PathVariable Long id, @RequestBody AjusteRequest r, Authentication auth) {
        if (r == null) throw new IllegalArgumentException("Indica la cantidad");
        return disponibilidad.ajustarPresentacion(id, r.delta(), r.cantidad(), r.motivo(), auth.getName());
    }

    @PutMapping("/platos/{id}/pausa")
    public DisponibilidadService.ProductoPanel pausar(@PathVariable Long id, @RequestBody PausaRequest r, Authentication auth) {
        if (r == null || r.pausado() == null) throw new IllegalArgumentException("Indica si el producto se pausa o se reactiva");
        return disponibilidad.pausar(id, r.pausado(), r.motivo(), auth.getName());
    }

    @PutMapping("/platos/{id}/modalidad")
    public DisponibilidadService.ProductoPanel modalidad(@PathVariable Long id, @RequestBody ModalidadRequest r, Authentication auth) {
        if (r == null) throw new IllegalArgumentException("Indica la modalidad");
        return disponibilidad.cambiarModalidad(id, r.tipo(), r.cantidad(), r.motivo(), auth.getName());
    }
}
