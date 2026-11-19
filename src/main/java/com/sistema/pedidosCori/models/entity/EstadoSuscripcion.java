package com.sistema.pedidosCori.models.entity;

public enum EstadoSuscripcion {
    PRUEBA("En prueba", true),
    ACTIVO("Activo", true),
    SUSPENDIDO("Suspendido", false),
    CANCELADO("Cancelado", false);

    private final String etiqueta;
    private final boolean permiteAcceso;

    EstadoSuscripcion(String etiqueta, boolean permiteAcceso) {
        this.etiqueta = etiqueta;
        this.permiteAcceso = permiteAcceso;
    }

    public String getEtiqueta() { return etiqueta; }

    public boolean permiteAcceso() { return permiteAcceso; }
}
