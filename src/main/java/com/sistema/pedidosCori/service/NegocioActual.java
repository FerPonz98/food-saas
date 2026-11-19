package com.sistema.pedidosCori.service;

import java.util.Optional;
import java.util.function.Supplier;

public final class NegocioActual {
    public static final long RAIZ = -1L;
    public static final long NINGUNO = 0L;

    private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

    private NegocioActual() {
    }

    public static long valor() {
        Long valor = ACTUAL.get();
        return valor == null ? NINGUNO : valor;
    }

    public static Long crudo() {
        return ACTUAL.get();
    }

    public static Optional<Long> id() {
        long valor = valor();
        return valor > 0 ? Optional.of(valor) : Optional.empty();
    }

    public static long requerido() {
        return id().orElseThrow(() -> new IllegalStateException("No hay un negocio seleccionado"));
    }

    public static boolean esRaiz() {
        return valor() == RAIZ;
    }

    public static void establecer(Long id) {
        if (id == null) ACTUAL.remove();
        else ACTUAL.set(id);
    }

    public static void limpiar() {
        ACTUAL.remove();
    }

    public static <T> T ejecutarComo(long id, Supplier<T> accion) {
        Long anterior = ACTUAL.get();
        ACTUAL.set(id);
        try {
            return accion.get();
        } finally {
            establecer(anterior);
        }
    }

    public static void ejecutarComo(long id, Runnable accion) {
        ejecutarComo(id, () -> {
            accion.run();
            return null;
        });
    }
}
