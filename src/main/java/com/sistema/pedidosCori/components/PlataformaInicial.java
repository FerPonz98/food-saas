package com.sistema.pedidosCori.components;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Negocio;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.EjecutorNegocio;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.PlataformaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class PlataformaInicial implements CommandLineRunner {
    private final PlataformaService plataforma;
    private final INegocioDao negocios;
    private final IUsuarioDao usuarios;
    private final ConfiguracionNegocioService configuracion;
    private final com.sistema.pedidosCori.service.CobranzaService cobranza;
    private final EjecutorNegocio ejecutor;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.seed.superadmin-username:superadmin}")
    private String superadminUsername;
    @Value("${app.seed.superadmin-password:}")
    private String superadminPassword;

    @Override
    public void run(String... args) {
        ejecutor.en(NegocioActual.RAIZ, () -> {
            plataforma.crearPlanesPredeterminados();
            if (negocios.count() == 0) {
                Negocio principal = plataforma.crearNegocioInicial();
                log.info("Negocio inicial creado: {} ({})", principal.getNombre(), principal.getSlug());
            }
            asegurarSuperadmin();
        });
        for (Negocio n : ejecutor.en(NegocioActual.RAIZ, () -> negocios.findAll()))
            configuracion.crearPara(n.getId(), n.getNombre());
        cobranza.inicializar();
        cobranza.ejecutarProgramado();
    }

    public Long negocioPrincipal() {
        return ejecutor.en(NegocioActual.RAIZ, () -> negocios.findTop2ByOrderByIdAsc().stream().findFirst()
            .map(Negocio::getId).orElseThrow(() -> new IllegalStateException("No hay negocios registrados")));
    }

    private void asegurarSuperadmin() {
        if (usuarios.countByRolAndActivoTrue(Rol.SUPERADMIN) > 0) return;
        String username = superadminUsername == null || superadminUsername.isBlank() ? "superadmin"
            : superadminUsername.trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByUsername(username)) {
            log.warn("No hay superadministradores activos y el usuario {} ya existe con otro rol; define SEED_SUPERADMIN_USERNAME", username);
            return;
        }
        boolean produccion = environment.acceptsProfiles(Profiles.of("prod"));
        String password = superadminPassword;
        if (password == null || password.isBlank() || password.startsWith("CAMBIAR_")) {
            if (produccion) {
                log.warn("Perfil prod sin SEED_SUPERADMIN_PASSWORD: no se creó el superadministrador. "
                    + "Usa RECUPERAR_ACCESO=<usuario> para emitirle una contraseña temporal.");
                return;
            }
            byte[] bytes = new byte[18];
            random.nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "7a";
            log.warn("Contraseña inicial generada para el superadministrador {}: {} (solo se muestra ahora)", username, password);
        } else if (produccion) {
            DataInitializer.exigirPassword("SEED_SUPERADMIN_PASSWORD", password);
        }
        usuarios.save(Usuario.builder().username(username).nombreCompleto("Superadministrador").rol(Rol.SUPERADMIN)
            .negocioId(null).password(passwordEncoder.encode(password)).activo(true).debeCambiarPassword(true)
            .passwordActualizadaEn(LocalDateTime.now()).build());
        log.info("Superadministrador inicial creado: {}", username);
    }
}
