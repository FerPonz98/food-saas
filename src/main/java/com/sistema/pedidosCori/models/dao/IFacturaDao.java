package com.sistema.pedidosCori.models.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistema.pedidosCori.models.entity.Factura;

import jakarta.persistence.LockModeType;

public interface IFacturaDao extends JpaRepository<Factura, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Factura f WHERE f.id = :id")
    Optional<Factura> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNegocioIdAndPeriodoDesdeAndEstadoNot(Long negocioId, LocalDate periodoDesde, Factura.Estado estado);

    List<Factura> findByNegocioIdAndEstadoOrderByVenceEnAsc(Long negocioId, Factura.Estado estado);

    List<Factura> findTop24ByNegocioIdOrderByPeriodoDesdeDesc(Long negocioId);

    List<Factura> findTop300ByOrderByEmitidaEnDesc();
}
