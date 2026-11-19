package com.sistema.pedidosCori;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.sistema.pedidosCori.service.NegocioActual;

public class NegocioPrincipalExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    public static final long PRINCIPAL = 1L;

    @Override
    public void beforeAll(ExtensionContext context) {
        NegocioActual.establecer(PRINCIPAL);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        NegocioActual.limpiar();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        NegocioActual.establecer(PRINCIPAL);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        NegocioActual.limpiar();
    }
}
