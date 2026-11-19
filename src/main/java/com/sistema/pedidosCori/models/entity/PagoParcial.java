package com.sistema.pedidosCori.models.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pago_parcial")
@Getter
@Setter
public class PagoParcial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(nullable = false)
    private Double monto;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago;

    @Column(length = 60)
    private String persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoPago modo;

    @Column(length = 500)
    private String detalle;

    @Column(name = "cobrado_por", length = 60)
    private String cobradoPor;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PagoParcialItem> items = new ArrayList<>();
}
