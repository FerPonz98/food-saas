package com.sistema.pedidosCori.models.entity;

import org.hibernate.annotations.TenantId;
import com.fasterxml.jackson.annotation.JsonIgnore;

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
@Table(name = "llamado_mesa", indexes = @Index(name = "idx_llamado_mesa_pendiente", columnList = "atendido_en, numero_mesa"))
@Getter
@Setter
public class LlamadoMesa {
    public static final String MOTIVO_MESERO = "MESERO";
    public static final String MOTIVO_CUENTA = "CUENTA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @JsonIgnore
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "numero_mesa", nullable = false)
    private Integer numeroMesa;
    @Column(nullable = false, length = 20)
    private String motivo;
    @Column(length = 20)
    private String metodoPago;

    @Column(nullable = false)
    private LocalDateTime creadoEn;
    @Column(nullable = false)
    private LocalDateTime ultimoAvisoEn;

    @Column(name = "atendido_en")
    private LocalDateTime atendidoEn;

    @Column(length = 60)
    private String atendidoPor;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "confirmado_por", length = 60)
    private String confirmadoPor;
}
