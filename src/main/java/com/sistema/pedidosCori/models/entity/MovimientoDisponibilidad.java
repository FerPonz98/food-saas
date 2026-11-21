package com.sistema.pedidosCori.models.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
@Table(name = "movimiento_disponibilidad", indexes = @Index(name = "idx_movimiento_disponibilidad_negocio_fecha", columnList = "negocio_id, fecha"))
@Getter
@Setter
public class MovimientoDisponibilidad {
    public enum Tipo { AJUSTE, PAUSA, REACTIVACION, MODALIDAD, ANULACION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JsonIgnore
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "plato_id", nullable = false)
    private Long platoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    @Column(nullable = false)
    private String producto;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(name = "cantidad_antes")
    private Integer cantidadAntes;

    @Column(name = "cantidad_despues")
    private Integer cantidadDespues;

    @Column(length = 200)
    private String motivo;

    @Column(length = 60)
    private String usuario;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
