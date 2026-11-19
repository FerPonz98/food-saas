package com.sistema.pedidosCori.service;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {
    private final IUsuarioDao usuarioDao;
    private final ModuloService modulos;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarioDao.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        boolean bloqueado = u.getNegocioId() != null
            && modulos.info(u.getNegocioId()).map(i -> !i.permiteAcceso()).orElse(true);
        return new UsuarioSesion(u.getUsername(), u.getPassword(), Boolean.TRUE.equals(u.getActivo()), bloqueado,
            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name())), u.getNegocioId());
    }
}
