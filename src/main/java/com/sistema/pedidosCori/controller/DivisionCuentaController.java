package com.sistema.pedidosCori.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.config.RequiereModulo;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.service.DivisionCuentaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos/{id}")
@RequiredArgsConstructor
@RequiereModulo(Modulo.DIVISION_CUENTA)
public class DivisionCuentaController {
    private final DivisionCuentaService division;

    @GetMapping("/cuenta")
    public DivisionCuentaService.Cuenta cuenta(@PathVariable Long id) {
        return division.cuenta(id);
    }

    @PostMapping("/pagos")
    public DivisionCuentaService.Cuenta pagar(@PathVariable Long id, @RequestBody DivisionCuentaService.NuevoPago pago) {
        return division.pagar(id, pago);
    }
}
