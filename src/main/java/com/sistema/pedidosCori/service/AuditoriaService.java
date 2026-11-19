package com.sistema.pedidosCori.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IRegistroAuditoriaDao;
import com.sistema.pedidosCori.models.dao.IRegistroPlataformaDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroPlataforma;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditoriaService {
    public static final int TAMANO_MAXIMO = 100;

    private final IRegistroAuditoriaDao dao;
    private final IRegistroPlataformaDao plataforma;
    private final EjecutorNegocio ejecutor;

    public AuditoriaService(IRegistroAuditoriaDao dao, IRegistroPlataformaDao plataforma, EjecutorNegocio ejecutor) {
        this.dao = dao;
        this.plataforma = plataforma;
        this.ejecutor = ejecutor;
    }

    public void registrarPlataforma(String accion, Long negocioId, String detalle) {
        RegistroPlataforma r = new RegistroPlataforma();
        r.setFecha(LocalDateTime.now());
        r.setUsuario(recortar(UsuarioActual.nombre().orElse("servidor"), RegistroAuditoria.MAX_USUARIO));
        r.setAccion(recortar(accion, RegistroAuditoria.MAX_ACCION));
        r.setNegocioId(negocioId);
        r.setDetalle(recortar(detalle, RegistroAuditoria.MAX_DETALLE));
        TrasConfirmar.ejecutar(() -> {
            try {
                ejecutor.en(NegocioActual.RAIZ, () -> plataforma.save(r));
            } catch (RuntimeException e) {
                log.warn("No se pudo registrar la acción de plataforma {}: {}", accion, e.getMessage());
            }
        });
    }

    public void registrar(AccionAuditoria accion, String entidad, Object entidadId, String detalle) {
        registrarComo(null, accion, entidad, entidadId, () -> detalle);
    }

    public void registrar(AccionAuditoria accion, String entidad, Object entidadId, Supplier<String> detalle) {
        registrarComo(null, accion, entidad, entidadId, detalle);
    }

    public void registrarComo(String usuario, AccionAuditoria accion, String entidad, Object entidadId,
            Supplier<String> detalle) {
        RegistroAuditoria registro;
        try {
            String autor = usuario != null ? usuario : UsuarioActual.nombre().orElse(null);
            registro = RegistroAuditoria.builder()
                .fecha(LocalDateTime.now())
                .usuario(recortar(autor, RegistroAuditoria.MAX_USUARIO))
                .accion(recortar(accion.name(), RegistroAuditoria.MAX_ACCION))
                .entidad(recortar(entidad, RegistroAuditoria.MAX_ENTIDAD))
                .entidadId(entidadId == null ? null : recortar(String.valueOf(entidadId), RegistroAuditoria.MAX_ENTIDAD_ID))
                .detalle(recortar(detalle == null ? null : detalle.get(), RegistroAuditoria.MAX_DETALLE))
                .build();
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar la auditoría {}: {}", accion, e.getMessage());
            return;
        }
        long negocio = NegocioActual.valor();
        if (negocio == NegocioActual.RAIZ) {
            registrarPlataforma(registro.getAccion(), null,
                (registro.getUsuario() == null ? "" : registro.getUsuario() + " · ") + Objects.toString(registro.getDetalle(), ""));
            return;
        }
        if (negocio <= 0) {
            log.info("Auditoría {} sin negocio asociado: {}", registro.getAccion(), registro.getDetalle());
            return;
        }
        TrasConfirmar.ejecutar(() -> persistir(negocio, registro));
    }

    private void persistir(long negocio, RegistroAuditoria registro) {
        try {
            ejecutor.en(negocio, () -> dao.save(registro));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría {}: {}", registro.getAccion(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> buscar(String usuario, String accion, LocalDate desde, LocalDate hasta,
            int pagina, int tamano) {
        if (pagina < 0) throw new IllegalArgumentException("La página no puede ser negativa");
        if (tamano < 1) throw new IllegalArgumentException("El tamaño de página debe ser mayor que cero");
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
        String accionNormalizada = vacioANulo(accion);
        if (accionNormalizada != null && Arrays.stream(AccionAuditoria.values()).noneMatch(a -> a.name().equals(accionNormalizada)))
            throw new IllegalArgumentException("Acción de auditoría desconocida");
        return dao.buscar(vacioANulo(usuario), accionNormalizada,
            desde == null ? null : desde.atStartOfDay(),
            hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
            PageRequest.of(pagina, Math.min(tamano, TAMANO_MAXIMO), Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public List<String> usuarios() { return dao.usuariosDistintos(); }

    public List<Map<String, String>> acciones() {
        return Arrays.stream(AccionAuditoria.values())
            .map(a -> Map.of("codigo", a.name(), "etiqueta", a.getEtiqueta())).toList();
    }

    public static String recortar(String valor, int max) {
        if (valor == null) return null;
        if (valor.length() <= max) return valor;
        int fin = max - 1;
        if (Character.isHighSurrogate(valor.charAt(fin - 1))) fin--;
        return valor.substring(0, fin) + "…";
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
