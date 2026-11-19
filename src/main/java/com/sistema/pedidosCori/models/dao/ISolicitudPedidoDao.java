package com.sistema.pedidosCori.models.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistema.pedidosCori.models.entity.EstadoSolicitud;
import com.sistema.pedidosCori.models.entity.SolicitudPedido;

import jakarta.persistence.LockModeType;

public interface ISolicitudPedidoDao extends JpaRepository<SolicitudPedido, Long> {
    List<SolicitudPedido> findByEstadoAndCreadoEnAfterOrderByCreadoEnAsc(EstadoSolicitud estado, LocalDateTime desde);

    Optional<SolicitudPedido> findByToken(String token);

    long countByNumeroMesaAndEstadoAndCreadoEnAfter(Integer numeroMesa, EstadoSolicitud estado, LocalDateTime desde);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudPedido s WHERE s.id = :id")
    Optional<SolicitudPedido> findByIdForUpdate(@Param("id") Long id);
}
