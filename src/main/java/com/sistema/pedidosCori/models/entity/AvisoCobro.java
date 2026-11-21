package com.sistema.pedidosCori.models.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "aviso_cobro",
    uniqueConstraints = @UniqueConstraint(name = "uk_aviso_cobro_dia", columnNames = {"negocio_id", "factura_id", "tipo", "dia"}),
    indexes = @Index(name = "idx_aviso_cobro_negocio_fecha", columnList = "negocio_id, fecha"))
@Getter
@Setter
public class AvisoCobro {
    public enum Tipo { FACTURA_EMITIDA, RECORDATORIO, VENCIDA, ULTIMO_AVISO, SUSPENDIDO, PAGO_RECIBIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @Column(name = "factura_id")
    private Long facturaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 300)
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private LocalDate dia;
}
