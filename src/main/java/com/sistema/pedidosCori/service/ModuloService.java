package com.sistema.pedidosCori.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.entity.EstadoSuscripcion;
import com.sistema.pedidosCori.models.entity.Modulo;
import com.sistema.pedidosCori.models.entity.Negocio;

@Service
public class ModuloService {
    private static final long VIGENCIA_CACHE_MS = 15_000;

    private final INegocioDao negocios;
    private final Map<Long, Entrada> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> porSlug = new ConcurrentHashMap<>();

    public ModuloService(INegocioDao negocios) {
        this.negocios = negocios;
    }

    public record InfoNegocio(long id, String slug, String nombre, EstadoSuscripcion estado, String planCodigo,
            String planNombre, Set<Modulo> modulos, LocalDate venceEn) {
        public boolean permiteAcceso() { return estado.permiteAcceso(); }

        public boolean tiene(Modulo modulo) { return modulos.contains(modulo); }
    }

    private record Entrada(InfoNegocio info, long creada) {
    }

    private record Unico(Optional<Long> id, long creado) {
    }

    private volatile Unico unico;

    public static class ModuloNoIncluido extends RuntimeException {
        private final Modulo modulo;

        public ModuloNoIncluido(Modulo modulo) {
            super("«" + modulo.getEtiqueta() + "» no está incluido en el plan de este negocio");
            this.modulo = modulo;
        }

        public Modulo getModulo() { return modulo; }
    }

    public static Set<Modulo> efectivos(Negocio n) {
        Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
        if (n.getPlan() != null && n.getPlan().getModulos() != null) modulos.addAll(n.getPlan().getModulos());
        if (n.getAjustesModulos() != null) n.getAjustesModulos().forEach((modulo, habilitado) -> {
            if (Boolean.TRUE.equals(habilitado)) modulos.add(modulo);
            else modulos.remove(modulo);
        });
        return modulos;
    }

    public Optional<InfoNegocio> info(long negocioId) {
        if (negocioId <= 0) return Optional.empty();
        Entrada entrada = cache.get(negocioId);
        if (entrada != null && System.currentTimeMillis() - entrada.creada() < VIGENCIA_CACHE_MS)
            return Optional.of(entrada.info());
        Optional<InfoNegocio> info = negocios.findById(negocioId).map(ModuloService::aInfo);
        info.ifPresentOrElse(i -> {
            cache.put(negocioId, new Entrada(i, System.currentTimeMillis()));
            porSlug.put(i.slug(), negocioId);
        }, () -> cache.remove(negocioId));
        return info;
    }

    public Optional<InfoNegocio> porSlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        Long id = porSlug.get(slug);
        if (id != null) {
            Optional<InfoNegocio> info = info(id);
            if (info.isPresent() && info.get().slug().equals(slug)) return info;
            porSlug.remove(slug);
        }
        return negocios.findBySlug(slug).map(n -> {
            InfoNegocio i = aInfo(n);
            cache.put(i.id(), new Entrada(i, System.currentTimeMillis()));
            porSlug.put(i.slug(), i.id());
            return i;
        });
    }

    public Optional<InfoNegocio> actual() {
        return NegocioActual.id().flatMap(this::info);
    }

    public Set<Modulo> modulosActuales() {
        return actual().map(InfoNegocio::modulos).orElse(Collections.emptySet());
    }

    public boolean tiene(Modulo modulo) {
        return actual().map(i -> i.tiene(modulo)).orElse(false);
    }

    public void exigir(Modulo modulo) {
        if (!tiene(modulo)) throw new ModuloNoIncluido(modulo);
    }

    public Optional<Long> principal() {
        return negocios.findTop2ByOrderByIdAsc().stream().findFirst().map(Negocio::getId);
    }

    public Optional<Long> unicoNegocio() {
        Unico guardado = unico;
        if (guardado != null && System.currentTimeMillis() - guardado.creado() < VIGENCIA_CACHE_MS) return guardado.id();
        List<Long> ids = negocios.findTop2ByOrderByIdAsc().stream().map(Negocio::getId).toList();
        Optional<Long> resultado = ids.size() == 1 ? Optional.of(ids.get(0)) : Optional.empty();
        unico = new Unico(resultado, System.currentTimeMillis());
        return resultado;
    }

    public void invalidar(Long negocioId) {
        unico = null;
        if (negocioId == null) return;
        Entrada anterior = cache.remove(negocioId);
        if (anterior != null) porSlug.remove(anterior.info().slug());
    }

    public void invalidarTodo() {
        unico = null;
        cache.clear();
        porSlug.clear();
    }

    private static InfoNegocio aInfo(Negocio n) {
        return new InfoNegocio(n.getId(), n.getSlug(), n.getNombre(), n.getEstado(),
            n.getPlan().getCodigo(), n.getPlan().getNombre(), Collections.unmodifiableSet(efectivos(n)), n.getVenceEn());
    }
}
