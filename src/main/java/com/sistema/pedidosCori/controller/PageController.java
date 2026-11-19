package com.sistema.pedidosCori.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final ModuloService modulos;

    @GetMapping("/")
    public String home(Authentication auth) {
        if (tieneRol(auth, "ROLE_SUPERADMIN")) return "redirect:/superadmin";
        if (tieneRol(auth, "ROLE_ADMIN"))  return "redirect:/admin";
        if (tieneRol(auth, "ROLE_COCINA")) return "redirect:/cocina";
        return "redirect:/mesero";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return "redirect:/";
        return "login";
    }

    @GetMapping("/n/{slug}/login")
    public String loginNegocio(@PathVariable String slug, Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return "redirect:/";
        return "login";
    }

    @GetMapping("/suspendido")
    public String suspendido(Model model) {
        model.addAttribute("negocioSuspendido", NegocioActual.id().flatMap(modulos::info).orElse(null));
        return "suspendido";
    }

    @GetMapping("/mesero")
    public String mesero() { return "mesero"; }

    @GetMapping("/catalogo")
    public String catalogo() { return "catalogo"; }

    @GetMapping("/cocina")
    public String cocina() { return "cocina"; }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }
}
