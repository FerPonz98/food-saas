package com.sistema.pedidosCori.models.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.LlamadoMesa;

public interface ILlamadoMesaDao extends JpaRepository<LlamadoMesa, Long> {
    List<LlamadoMesa> findByAtendidoEnIsNullAndCreadoEnAfterOrderByCreadoEnAsc(LocalDateTime desde);

    List<LlamadoMesa> findByNumeroMesaAndAtendidoEnIsNullAndCreadoEnAfterOrderByCreadoEnAsc(Integer numeroMesa, LocalDateTime desde);

    Optional<LlamadoMesa> findFirstByNumeroMesaAndMotivoAndAtendidoEnIsNullAndCreadoEnAfter(Integer numeroMesa, String motivo, LocalDateTime desde);
}
