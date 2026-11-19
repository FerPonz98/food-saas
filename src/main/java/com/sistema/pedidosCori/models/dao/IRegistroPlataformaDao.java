package com.sistema.pedidosCori.models.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.RegistroPlataforma;

public interface IRegistroPlataformaDao extends JpaRepository<RegistroPlataforma, Long> {
    List<RegistroPlataforma> findTop100ByNegocioIdOrderByFechaDescIdDesc(Long negocioId);

    List<RegistroPlataforma> findTop100ByOrderByFechaDescIdDesc();
}
