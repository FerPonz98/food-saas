package com.sistema.pedidosCori.models.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "modulo_precio")
@Getter
@Setter
public class ModuloPrecio {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Modulo modulo;

    @Column(name = "precio_mensual", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMensual = BigDecimal.ZERO;

    @Column(name = "precio_anual", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnual = BigDecimal.ZERO;

    @Column(name = "se_vende_aparte", nullable = false)
    private Boolean seVendeAparte = true;
}
