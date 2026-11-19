package com.sistema.pedidosCori.models.entity;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "plan")
@Getter
@Setter
public class Plan {
    public static final String NORMAL = "NORMAL";
    public static final String PRO = "PRO";
    public static final String PREMIUM = "PREMIUM";

    @Id
    @Column(length = 20)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(length = 240)
    private String descripcion;

    @Column(nullable = false)
    private Integer orden;

    @Column(name = "precio_mensual", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "precio_anual", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnual;

    @Column(name = "precio_implementacion", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioImplementacion;

    @Column(name = "mesas_incluidas")
    private Integer mesasIncluidas;

    @Column(name = "productos_incluidos")
    private Integer productosIncluidos;

    @Column(name = "usuarios_incluidos")
    private Integer usuariosIncluidos;

    @Column(nullable = false)
    private Boolean activo = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_modulo", joinColumns = @JoinColumn(name = "plan_codigo"))
    @Enumerated(EnumType.STRING)
    @Column(name = "modulo", length = 40, nullable = false)
    private Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
}
