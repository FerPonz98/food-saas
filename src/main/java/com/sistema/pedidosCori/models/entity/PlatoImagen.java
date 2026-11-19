package com.sistema.pedidosCori.models.entity;

import org.hibernate.annotations.TenantId;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plato_imagen")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatoImagen {
    @Id
    @Column(name = "plato_id")
    private Long platoId;

    @TenantId
    @JsonIgnore
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "datos", nullable = false, length = 1_048_576)
    private byte[] datos;

    @Column(name = "mime_type", nullable = false, length = 24)
    private String mimeType;
}
