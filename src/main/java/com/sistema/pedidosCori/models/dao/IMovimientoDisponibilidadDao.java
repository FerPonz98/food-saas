package com.sistema.pedidosCori.models.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.MovimientoDisponibilidad;

public interface IMovimientoDisponibilidadDao extends JpaRepository<MovimientoDisponibilidad, Long> {
    List<MovimientoDisponibilidad> findTop200ByFechaGreaterThanEqualOrderByFechaDesc(LocalDateTime desde);
}
