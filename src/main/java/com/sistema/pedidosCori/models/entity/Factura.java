package com.sistema.pedidosCori.models.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "factura", indexes = @Index(name = "idx_factura_negocio_estado", columnList = "negocio_id, estado"))
@Getter
@Setter
public class Factura {
    public enum Estado { PENDIENTE, PAGADA, ANULADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private Long negocioId;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(name = "plan_codigo", nullable = false, length = 20)
    private String planCodigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CicloFacturacion ciclo;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(name = "emitida_en", nullable = false)
    private LocalDateTime emitidaEn;

    @Column(name = "vence_en", nullable = false)
    private LocalDate venceEn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "pagada_en")
    private LocalDateTime pagadaEn;

    @Column(name = "metodo_pago", length = 30)
    private String metodoPago;

    @Column(name = "referencia_pago", length = 120)
    private String referenciaPago;

    @Column(name = "registrada_por", length = 60)
    private String registradaPor;

    @Column(length = 300)
    private String nota;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<FacturaLinea> lineas = new ArrayList<>();
}
