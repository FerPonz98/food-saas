package com.sistema.pedidosCori.models.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.MarcaPlataforma;

public interface IMarcaPlataformaDao extends JpaRepository<MarcaPlataforma, Integer> {
}
