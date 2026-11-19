package com.sistema.pedidosCori.models.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.PagoParcial;

public interface IPagoParcialDao extends JpaRepository<PagoParcial, Long> {
    List<PagoParcial> findByPedidoIdOrderByFechaAscIdAsc(Long pedidoId);

    List<PagoParcial> findByPedidoIdIn(Collection<Long> pedidoIds);
}
