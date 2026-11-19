package com.sistema.pedidosCori.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;

@Component
public class EjecutorNegocio {
    private final EntityManagerFactory emf;
    private final TransactionTemplate transaccionNueva;

    public EjecutorNegocio(EntityManagerFactory emf, PlatformTransactionManager transacciones) {
        this.emf = emf;
        this.transaccionNueva = new TransactionTemplate(transacciones);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T en(long negocioId, Supplier<T> accion) {
        Object sesionDeVista = !TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(emf)
            ? TransactionSynchronizationManager.unbindResource(emf) : null;
        try {
            return NegocioActual.ejecutarComo(negocioId, () -> transaccionNueva.execute(estado -> accion.get()));
        } finally {
            if (sesionDeVista != null) TransactionSynchronizationManager.bindResource(emf, sesionDeVista);
        }
    }

    public void en(long negocioId, Runnable accion) {
        en(negocioId, () -> {
            accion.run();
            return null;
        });
    }
}
