package com.sistema.pedidosCori.service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dto.CategoriaVista;
import com.sistema.pedidosCori.models.entity.Modalidad;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class CartaPublicaService {
    private final IPlatoDao platoDao;
    private final CategoriaService categorias;
    private final ConfiguracionNegocioService configuracion;
    private final ModuloService modulos;

    public record CategoriaPublica(String codigo, String nombre, String icono, Integer orden, Boolean activa) {
    }

    public record PresentacionPublica(Long id, String nombre, Double precio, Boolean disponible,
            Integer stockDisponible, Modalidad modalidad) {
    }

    public record PlatoPublico(Long id, String nombre, String descripcion, Double precio, String categoria,
            Boolean disponible, Integer stockDisponible, String imagenUrl, List<PresentacionPublica> presentaciones) {
    }

    public record CartaPublica(List<CategoriaPublica> categorias, List<PlatoPublico> platos) {
    }

    @Transactional(readOnly = true)
    public CartaPublica carta() {
        String slug = modulos.actual().map(ModuloService.InfoNegocio::slug).orElse("");
        List<CategoriaPublica> cats = categorias.listar().stream()
            .filter(c -> !Boolean.FALSE.equals(c.activa()))
            .sorted(Comparator.comparing(CategoriaVista::orden, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(c -> new CategoriaPublica(c.codigo(), c.nombre(), c.icono(), c.orden(), true))
            .toList();
        List<PlatoPublico> platos = platoDao.findByDisponibleTrueAndArchivadoFalse().stream()
            .filter(CartaPublicaService::seVendeEnMesa)
            .map(p -> publico(p, slug))
            .toList();
        return new CartaPublica(cats, platos);
    }
    public void validarMesa(int numero) {
        Integer total = configuracion.vista().getCantidadPuntosAtencion();
        if (total == null || numero < 1 || numero > total) throw new NoSuchElementException("Mesa no encontrada");
    }
    @Transactional(readOnly = true)
    public void validarFotoPublica(Long platoId) {
        Plato p = platoDao.findById(platoId).orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
        if (!Boolean.TRUE.equals(p.getDisponible()) || Boolean.TRUE.equals(p.getArchivado()) || p.getImagenVersion() == null)
            throw new NoSuchElementException("Producto no encontrado");
    }
    private static boolean seVendeEnMesa(Plato p) {
        List<PresentacionPlato> pres = p.getPresentaciones();
        return pres == null || pres.isEmpty() || pres.stream().anyMatch(pr -> pr.getModalidad() != Modalidad.LLEVAR);
    }

    private static PlatoPublico publico(Plato p, String slug) {
        List<PresentacionPublica> pres = (p.getPresentaciones() == null ? List.<PresentacionPlato>of() : p.getPresentaciones())
            .stream()
            .filter(pr -> pr.getModalidad() != Modalidad.LLEVAR)
            .map(pr -> new PresentacionPublica(pr.getId(), pr.getNombre(), pr.getPrecio(), pr.getDisponible(),
                pr.getStockDisponible(), pr.getModalidad()))
            .toList();
        String imagen = p.getImagenVersion() == null ? null
            : "/api/publico/" + slug + "/platos/" + p.getId() + "/imagen?v=" + p.getImagenVersion();
        return new PlatoPublico(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getCategoria(),
            p.getDisponible(), p.getStockDisponible(), imagen, pres);
    }
}
