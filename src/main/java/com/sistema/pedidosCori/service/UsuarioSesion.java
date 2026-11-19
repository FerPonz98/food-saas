package com.sistema.pedidosCori.service;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class UsuarioSesion extends User {
    private final Long negocioId;

    public UsuarioSesion(String username, String password, boolean activo, boolean bloqueado,
            Collection<? extends GrantedAuthority> permisos, Long negocioId) {
        super(username, password, activo, true, true, !bloqueado, permisos);
        this.negocioId = negocioId;
    }

    public Long getNegocioId() { return negocioId; }

    public boolean esSuperadmin() {
        return getAuthorities().stream().anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    @Override
    public boolean equals(Object otro) { return super.equals(otro); }

    @Override
    public int hashCode() { return super.hashCode(); }
}
