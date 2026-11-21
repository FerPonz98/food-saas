package com.sistema.pedidosCori.controller;

import com.sistema.pedidosCori.models.entity.Modulo;

import com.sistema.pedidosCori.config.RequiereModulo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String inicio() { return "admin/inicio"; }

    @GetMapping("/admin/menu-hoy")
    public String menuHoy() { return "admin/menu-hoy"; }

    @GetMapping("/admin/productos")
    public String productos() { return "admin/productos"; }

    @GetMapping("/admin/categorias")
    public String categorias() { return "admin/categorias"; }

    @GetMapping("/admin/local")
    public String local() { return "admin/local"; }

    @GetMapping("/admin/usuarios")
    public String usuarios() { return "admin/usuarios"; }

    @GetMapping("/admin/reportes")
    public String reportes() { return "admin/reportes"; }

    @GetMapping("/admin/plan")
    public String plan() { return "admin/plan"; }

    @GetMapping("/admin/actividad")
    @RequiereModulo(Modulo.AUDITORIA)
    public String actividad() { return "admin/actividad"; }

    @GetMapping("/admin/carta-qr")
    @RequiereModulo(Modulo.CARTA_QR)
    public String cartaQr() { return "admin/carta-qr"; }

    @GetMapping("/admin/disponibilidad")
    @RequiereModulo(Modulo.DISPONIBILIDAD)
    public String disponibilidad() { return "admin/disponibilidad"; }

    @GetMapping("/admin/apariencia")
    public String apariencia() { return "admin/apariencia"; }
}
