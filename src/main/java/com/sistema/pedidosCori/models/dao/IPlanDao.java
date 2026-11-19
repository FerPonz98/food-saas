package com.sistema.pedidosCori.models.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.Plan;

public interface IPlanDao extends JpaRepository<Plan, String> {
    List<Plan> findAllByOrderByOrdenAsc();
}
