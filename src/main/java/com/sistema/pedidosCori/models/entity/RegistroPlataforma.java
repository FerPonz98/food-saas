package com.sistema.pedidosCori.models.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "registro_plataforma", indexes = @Index(name = "idx_registro_plataforma_fecha", columnList = "fecha"))
@Getter
@Setter
public class RegistroPlataforma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 60)
    private String usuario;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(name = "negocio_id")
    private Long negocioId;

    @Column(length = 500)
    private String detalle;
}
