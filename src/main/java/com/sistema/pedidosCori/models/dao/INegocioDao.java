package com.sistema.pedidosCori.models.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.Negocio;

public interface INegocioDao extends JpaRepository<Negocio, Long> {
    Optional<Negocio> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Negocio> findAllByOrderByNombreAsc();

    List<Negocio> findTop2ByOrderByIdAsc();
}
