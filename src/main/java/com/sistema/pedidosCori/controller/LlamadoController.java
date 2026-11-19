package com.sistema.pedidosCori.controller;

import com.sistema.pedidosCori.models.entity.Modulo;

import com.sistema.pedidosCori.config.RequiereModulo;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.service.LlamadoService;

import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping("/api/llamados")
@RequiereModulo(Modulo.LLAMADOS)
@RequiredArgsConstructor
public class LlamadoController {
    private final LlamadoService llamados;

    @GetMapping
    public List<LlamadoService.LlamadoVista> pendientes() {
        return llamados.pendientes();
    }

    @PutMapping("/{id}/confirmar")
    public LlamadoService.LlamadoVista confirmar(@PathVariable Long id, Authentication auth) {
        return llamados.confirmar(id, auth.getName());
    }

    @PutMapping("/{id}/atender")
    public LlamadoService.LlamadoVista atender(@PathVariable Long id, Authentication auth) {
        return llamados.atender(id, auth.getName());
    }
}
