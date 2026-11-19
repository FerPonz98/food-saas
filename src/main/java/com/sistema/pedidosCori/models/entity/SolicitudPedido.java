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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "solicitud_pedido")
@Getter
@Setter
public class SolicitudPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "numero_mesa", nullable = false)
    private Integer numeroMesa;

    @Column(nullable = false, length = 40)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "nombre_cliente", length = 60)
    private String nombreCliente;

    @Column(length = 200)
    private String nota;

    @Column(nullable = false)
    private Double total = 0.0;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    @Column(name = "revisado_por", length = 60)
    private String revisadoPor;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<SolicitudPedidoItem> items = new ArrayList<>();
}
