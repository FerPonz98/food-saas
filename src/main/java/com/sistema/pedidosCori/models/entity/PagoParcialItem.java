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
@Table(name = "pago_parcial_item")
@Getter
@Setter
public class PagoParcialItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private PagoParcial pago;

    @Column(name = "detalle_pedido_id", nullable = false)
    private Long detallePedidoId;

    @Column(nullable = false)
    private Integer cantidad;
}
