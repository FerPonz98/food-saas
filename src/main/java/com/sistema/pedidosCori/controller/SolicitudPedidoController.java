package com.sistema.pedidosCori.controller;

import java.util.List;
import java.util.Map;

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
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.service.SolicitudPedidoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
@RequiereModulo(Modulo.PEDIDOS_QR)
public class SolicitudPedidoController {
    private final SolicitudPedidoService solicitudes;

    public record Aprobacion(Boolean agregarAlAbierto) {
    }

    public record Rechazo(String motivo) {
    }

    @GetMapping
    public List<SolicitudPedidoService.SolicitudVista> pendientes() {
        return solicitudes.pendientes();
    }

    @PutMapping("/{id}/aprobar")
    public Pedido aprobar(@PathVariable Long id, @RequestBody(required = false) Aprobacion r, Authentication auth) {
        return solicitudes.aprobar(id, r == null || r.agregarAlAbierto() == null || r.agregarAlAbierto(), auth.getName());
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<Map<String, String>> rechazar(@PathVariable Long id, @RequestBody(required = false) Rechazo r,
            Authentication auth) {
        solicitudes.rechazar(id, r == null ? null : r.motivo(), auth.getName());
        return ResponseEntity.ok(Map.of("estado", "RECHAZADA"));
    }
}
