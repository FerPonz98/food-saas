package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.config.ResolutorNegocio;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.EjecutorNegocio;
import com.sistema.pedidosCori.service.NegocioActual;

@DataJpaTest
@Import({ ResolutorNegocio.class, EjecutorNegocio.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AislamientoHibernateTests {
    @Autowired IPlatoDao platos;
    @Autowired EjecutorNegocio ejecutor;

    private Plato plato(String nombre) {
        Plato p = new Plato();
        p.setNombre(nombre);
        p.setPrecio(10.0);
        p.setDisponible(true);
        return p;
    }

    @Test
    void cadaNegocioSoloVeYModificaSusDatos() {
        Long deA = ejecutor.en(9001L, () -> platos.save(plato("Plato A")).getId());
        Long deB = ejecutor.en(9002L, () -> platos.save(plato("Plato B")).getId());

        ejecutor.en(9001L, () -> {
            assertEquals(1, platos.findAll().stream().filter(p -> p.getNombre().startsWith("Plato ")).count());
            assertTrue(platos.findById(deA).isPresent());
            assertTrue(platos.findById(deB).isEmpty(), "findById no debe cruzar negocios");
            platos.marcarTodosComoNoDisponibles();
        });
        ejecutor.en(9002L, () -> assertTrue(platos.findById(deB).orElseThrow().getDisponible(),
            "las actualizaciones masivas no deben cruzar negocios"));
        ejecutor.en(NegocioActual.RAIZ, () -> {
            assertTrue(platos.findById(deA).isPresent());
            assertTrue(platos.findById(deB).isPresent());
        });
    }
}
