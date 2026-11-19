package com.sistema.pedidosCori.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ModuloInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final ModuloService modulos;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        String ruta = req.getRequestURI().substring(req.getContextPath().length());
        if ((ruta.startsWith("/api/publico/") || ruta.startsWith("/n/")) && NegocioActual.id().isEmpty()) {
            if (ruta.startsWith("/api/")) SecurityConfig.apiError(res, HttpStatus.NOT_FOUND, "Esta carta no está disponible", "NO_ENCONTRADO");
            else res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }
        Set<Modulo> requeridos = EnumSet.noneOf(Modulo.class);
        RequiereModulo enClase = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), RequiereModulo.class);
        RequiereModulo enMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), RequiereModulo.class);
        if (enClase != null) requeridos.addAll(Arrays.asList(enClase.value()));
        if (enMetodo != null) requeridos.addAll(Arrays.asList(enMetodo.value()));
        for (Modulo modulo : requeridos) {
            if (modulos.tiene(modulo)) continue;
            if (ruta.startsWith("/api/publico/")) {
                SecurityConfig.apiError(res, HttpStatus.NOT_FOUND, "Esta función no está disponible", "NO_ENCONTRADO");
            } else if (ruta.startsWith("/api/")) {
                ModuloService.ModuloNoIncluido error = new ModuloService.ModuloNoIncluido(modulo);
                res.setStatus(HttpStatus.FORBIDDEN.value());
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"mensaje\":\"" + error.getMessage().replace("\"", "'")
                    + "\",\"codigo\":\"MODULO_NO_INCLUIDO\",\"modulo\":\"" + modulo.name() + "\"}");
            } else if (ruta.startsWith("/admin")) {
                res.sendRedirect(req.getContextPath() + "/admin/plan?modulo=" + modulo.name());
            } else {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return false;
        }
        return true;
    }
}
