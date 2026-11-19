package com.sistema.pedidosCori.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LimitadorPorIp {
    private final int maximo;
    private final Duration ventana;
    private final String mensaje;
    private final Map<String, Deque<LocalDateTime>> marcasPorIp = new ConcurrentHashMap<>();

    public LimitadorPorIp(int maximo, Duration ventana, String mensaje) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.mensaje = mensaje;
    }

    public void controlar(String ip) {
        if (ip == null) return;
        LocalDateTime ahora = LocalDateTime.now();
        Deque<LocalDateTime> marcas = marcasPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(ahora.minus(ventana))) marcas.pollFirst();
            if (marcas.size() >= maximo) throw new LlamadoService.LimiteExcedido(mensaje);
            marcas.addLast(ahora);
        }
        if (marcasPorIp.size() > 5000) marcasPorIp.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(ahora.minus(ventana));
            }
        });
    }
}
