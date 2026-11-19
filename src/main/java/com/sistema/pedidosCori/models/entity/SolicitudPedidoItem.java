package com.sistema.pedidosCori.models.entity;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "solicitud_pedido_item")
@Getter
@Setter
public class SolicitudPedidoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudPedido solicitud;

    @Column(name = "plato_id", nullable = false)
    private Long platoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    @Column(name = "acompanante_id")
    private Long acompananteId;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(length = 120)
    private String notas;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "precio_unitario", nullable = false)
    private Double precioUnitario;

    @Column(nullable = false)
    private Double subtotal;
}
