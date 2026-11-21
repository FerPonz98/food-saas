package com.sistema.pedidosCori.models.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.AvisoCobro;

public interface IAvisoCobroDao extends JpaRepository<AvisoCobro, Long> {
    boolean existsByNegocioIdAndFacturaIdAndTipoAndDia(Long negocioId, Long facturaId, AvisoCobro.Tipo tipo, LocalDate dia);

    List<AvisoCobro> findTop20ByNegocioIdOrderByFechaDescIdDesc(Long negocioId);
}
