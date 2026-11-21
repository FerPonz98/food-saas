package com.sistema.pedidosCori.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "parametros_cobro")
@Getter
@Setter
public class ParametrosCobro {
    public static final int ID = 1;

    @Id
    private Integer id = ID;

    @Column(name = "dias_generacion", nullable = false)
    private Integer diasGeneracion = 7;

    @Column(name = "dias_recordatorio", nullable = false)
    private Integer diasRecordatorio = 7;

    @Column(name = "dias_gracia", nullable = false)
    private Integer diasGracia = 5;

    @Column(name = "cobranza_activa", nullable = false)
    private Boolean cobranzaActiva = true;

    @Column(name = "suspension_automatica", nullable = false)
    private Boolean suspensionAutomatica = true;

    @Column(name = "instrucciones_pago", length = 1000)
    private String instruccionesPago;
}
