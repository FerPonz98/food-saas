package com.sistema.pedidosCori.models.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "negocio")
@Getter
@Setter
public class Negocio {
    public static final int MAX_SLUG = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = MAX_SLUG)
    private String slug;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_codigo", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado = EstadoSuscripcion.ACTIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CicloFacturacion ciclo = CicloFacturacion.MENSUAL;

    @Column(name = "inicio_suscripcion")
    private LocalDate inicioSuscripcion;

    @Column(name = "vence_en")
    private LocalDate venceEn;

    @Column(name = "contacto_nombre", length = 120)
    private String contactoNombre;

    @Column(name = "contacto_telefono", length = 40)
    private String contactoTelefono;

    @Column(name = "contacto_email", length = 120)
    private String contactoEmail;

    @Column(length = 1000)
    private String notas;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "negocio_modulo", joinColumns = @JoinColumn(name = "negocio_id"))
    @MapKeyColumn(name = "modulo", length = 40)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "habilitado", nullable = false)
    private Map<Modulo, Boolean> ajustesModulos = new EnumMap<>(Modulo.class);
}
