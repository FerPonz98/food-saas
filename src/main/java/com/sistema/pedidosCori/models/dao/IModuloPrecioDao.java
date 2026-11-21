package com.sistema.pedidosCori.models.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.models.entity.ModuloPrecio;

public interface IModuloPrecioDao extends JpaRepository<ModuloPrecio, Modulo> {
}
