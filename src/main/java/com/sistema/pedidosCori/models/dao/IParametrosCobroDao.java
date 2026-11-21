package com.sistema.pedidosCori.models.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.ParametrosCobro;

public interface IParametrosCobroDao extends JpaRepository<ParametrosCobro, Integer> {
}
