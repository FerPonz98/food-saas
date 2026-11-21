package com.sistema.pedidosCori.controller;

import com.sistema.pedidosCori.service.CobranzaService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.EstadoSuscripcion;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.ModuloService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice(assignableTypes = {PageController.class, AdminPageController.class, CuentaController.class,
    CartaPublicaController.class, SuperAdminController.class})
@RequiredArgsConstructor
public class NegocioAdvice {
    private final ConfiguracionNegocioService service;
    private final ModuloService modulos;
    private final CobranzaService cobranza;

    @ModelAttribute
    public void negocio(Model model) {
        ConfiguracionNegocioVista vista = service.vista();
        model.addAttribute("negocio", vista);
        model.addAttribute("modulos", new HashSet<>(vista.getModulos()));
        StringBuilder css = new StringBuilder(":root{");
        vista.getTema().forEach((clave, valor) -> css.append(clave).append(':').append(valor).append(';'));
        model.addAttribute("temaCss", css.append('}').toString());
        CobranzaService.AvisoBanner banner = esAdministrador() ? modulos.actual().map(i -> cobranza.banner(i.id())).orElse(null) : null;
        model.addAttribute("avisoSuscripcion", banner == null ? modulos.actual().map(NegocioAdvice::aviso).orElse(null) : banner.texto());
        model.addAttribute("avisoSuscripcionNivel", banner == null ? "warning" : banner.nivel());
    }

    private static boolean esAdministrador() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static String aviso(ModuloService.InfoNegocio info) {
        if (info.venceEn() == null) return info.estado() == EstadoSuscripcion.PRUEBA ? "Estás usando el periodo de prueba." : null;
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), info.venceEn());
        String prefijo = info.estado() == EstadoSuscripcion.PRUEBA ? "La prueba" : "La suscripción";
        if (dias < 0) return prefijo + " venció el " + info.venceEn() + ". Comunícate con soporte para renovarla.";
        if (dias <= 7) return prefijo + (dias == 0 ? " vence hoy." : " vence en " + dias + (dias == 1 ? " día." : " días."));
        return null;
    }
}
