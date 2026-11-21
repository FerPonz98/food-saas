package com.sistema.pedidosCori.models.entity;

public enum TipoDisponibilidad {
    LIMITADA("Cantidad limitada"),
    MANUAL("Disponibilidad manual"),
    CONTINUA("Disponibilidad continua");

    private final String etiqueta;

    TipoDisponibilidad(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
