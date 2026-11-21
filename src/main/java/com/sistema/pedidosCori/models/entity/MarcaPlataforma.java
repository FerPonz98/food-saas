package com.sistema.pedidosCori.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "plataforma_marca")
@Getter
@Setter
public class MarcaPlataforma {
    public static final int ID = 1;

    @Id
    private Integer id = ID;

    @Column(nullable = false, length = 120)
    private String nombre = "Plataforma";

    @Column(length = 240)
    private String eslogan = "Administración de negocios y planes";

    @Column(name = "color_primario", nullable = false, length = 7)
    private String colorPrimario = "#24324A";

    @Column(name = "color_secundario", nullable = false, length = 7)
    private String colorSecundario = "#64748B";

    @Column(name = "color_acento", nullable = false, length = 7)
    private String colorAcento = "#C56A3D";

    @Column(name = "logo_datos", length = 1_048_576)
    private byte[] logoDatos;

    @Column(name = "logo_mime_type", length = 40)
    private String logoMimeType;

    @Column(name = "logo_version", nullable = false)
    private Long logoVersion = 1L;
}
