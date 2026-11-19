package com.sistema.pedidosCori.models.entity;

public enum Modulo {
    APARIENCIA_AVANZADA("Apariencia propia", "Colores, eslogan y nombres a medida («Mesa» o «Box», «Mesero» o «Garzón»).", "Marca y apariencia"),
    APP_INSTALABLE("App instalable", "Instalar el sistema como app en celulares y tablets del personal.", "Marca y apariencia"),
    CARTA_QR("Carta digital con QR", "Carta en el celular del cliente, QR único por mesa y Wi-Fi visible.", "Experiencia del cliente"),
    LLAMADOS("Llamar al mesero", "El cliente llama al mesero o pide la cuenta desde la mesa; el mesero confirma que va.", "Experiencia del cliente"),
    PEDIDOS_QR("Pedidos desde la carta", "El cliente arma su pedido desde la carta QR y el mesero lo aprueba antes de enviarlo a cocina.", "Experiencia del cliente"),
    DIVISION_CUENTA("División de cuenta", "Cobrar una misma cuenta por persona o por productos, con distintos métodos de pago.", "Operación diaria"),
    CARTA_SIN_MARCA("Carta sin la marca del sistema", "La carta QR muestra solo la marca del negocio.", "Experiencia del cliente"),
    REPORTES_COMPLETOS("Reportes completos", "Reportes por día, semana y mes, productos más vendidos y ventas por mesero.", "Control y reportes"),
    REPORTES_AVANZADOS("Reportes avanzados", "Horas pico, comparación entre periodos y exportación a Excel.", "Control y reportes"),
    AUDITORIA("Registro de actividad", "Quién creó, editó o cobró cada pedido y cuándo.", "Control y reportes"),
    VARIOS_ADMINISTRADORES("Varios administradores", "Más de un usuario administrador activo.", "Control y reportes");

    private final String etiqueta;
    private final String descripcion;
    private final String grupo;

    Modulo(String etiqueta, String descripcion, String grupo) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.grupo = grupo;
    }

    public String getEtiqueta() { return etiqueta; }

    public String getDescripcion() { return descripcion; }

    public String getGrupo() { return grupo; }
}
