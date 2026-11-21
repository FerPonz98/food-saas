package com.sistema.pedidosCori.config;

import com.sistema.pedidosCori.service.DisponibilidadService;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;
import com.sistema.pedidosCori.service.AuditoriaService;
import com.sistema.pedidosCori.service.CuentaService;
import com.sistema.pedidosCori.service.LoginAttemptService;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.UsuarioSesion;
import com.sistema.pedidosCori.models.entity.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Slf4j
@Configuration
@EnableScheduling
public class SecurityConfig {
    static final String POLITICA_CONTENIDO = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
        + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
        + "connect-src 'self' ws: wss:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService attempts,
            SessionRegistry sessionRegistry, AuditoriaService auditoria, IUsuarioDao usuarioDao,
            CuentaService cuentaService, ModuloService modulos) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/terminos", "/error", "/favicon.ico", "/css/**", "/js/**", "/img/**", "/branding/logo",
                    "/manifest.webmanifest", "/sw.js", "/pwa/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/n/*/login", "/n/*/logo", "/n/*/manifest.webmanifest", "/n/*/pwa/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/carta", "/mesa/*", "/n/*/carta", "/n/*/mesa/*",
                    "/api/publico/*/carta", "/api/publico/*/platos/*/imagen", "/api/publico/*/mesa/*/wifi",
                    "/api/publico/*/mesa/*/llamados", "/api/publico/*/mesa/*/pedidos", "/api/publico/*/mesa/*/pedidos/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/publico/*/mesa/*/llamados", "/api/publico/*/mesa/*/pedidos").permitAll()
                .requestMatchers("/superadmin", "/superadmin/**", "/api/superadmin/**").hasRole("SUPERADMIN")
                .requestMatchers("/api/llamados", "/api/llamados/**", "/api/solicitudes", "/api/solicitudes/**").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/", "/logout", "/cuenta", "/suspendido", "/ws", "/ws/**").authenticated()
                .requestMatchers("/mesero", "/catalogo").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/cocina").hasAnyRole("ADMIN", "COCINA")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/categorias", "/api/platos/*/imagen").hasAnyRole("ADMIN", "MESERO", "COCINA")
                .requestMatchers(HttpMethod.GET, "/api/platos/todos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/platos/**", "/api/menu-diario").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/platos/**", "/api/presentaciones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/platos/**", "/api/presentaciones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/estado").hasAnyRole("ADMIN", "COCINA", "MESERO")
                .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/*/detalles", "/api/pedidos/*/pagos").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/pagar", "/api/pedidos/*/anular").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/api/disponibilidad", "/api/disponibilidad/**").hasAuthority(DisponibilidadService.PERMISO)
                .requestMatchers("/admin/disponibilidad").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/*/cuenta").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/platos", "/api/platos/*/presentaciones").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/mesas/ocupadas").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/cocina").hasAnyRole("ADMIN", "COCINA")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/activos", "/api/pedidos/*").hasAnyRole("ADMIN", "MESERO", "COCINA")
                .anyRequest().denyAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .withObjectPostProcessor(redireccionLoginRelativa())
                .successHandler((req, res, authentication) -> {
                    attempts.reset(req.getParameter("username"), req.getRemoteAddr());
                    String username = authentication.getName();
                    try {
                        usuarioDao.registrarAcceso(username, LocalDateTime.now());
                    } catch (RuntimeException e) {
                        log.warn("No se pudo registrar el último acceso de {}: {}", username, e.getMessage());
                    }
                    long negocio = authentication.getPrincipal() instanceof UsuarioSesion sesion
                        ? (sesion.esSuperadmin() ? NegocioActual.RAIZ : sesion.getNegocioId() == null ? NegocioActual.NINGUNO : sesion.getNegocioId())
                        : NegocioActual.NINGUNO;
                    NegocioActual.ejecutarComo(negocio,
                        () -> auditoria.registrarComo(username, AccionAuditoria.LOGIN, "Usuario", null,
                            () -> "Acceso desde " + req.getRemoteAddr()));
                    boolean pendiente = cuentaService.pendientes(username).alguno();
                    if (pendiente) req.getSession().setAttribute(CuentaService.SESION_PENDIENTE, Boolean.TRUE);
                    res.sendRedirect(req.getContextPath() + (pendiente ? "/cuenta" : "/"));
                })
                .failureHandler((req, res, exception) -> {
                    attempts.failed(req.getParameter("username"), req.getRemoteAddr());
                    String intentado = req.getParameter("username");
                    String usuario = intentado == null || intentado.isBlank() ? "(vacío)"
                        : AuditoriaService.recortar(intentado.trim(), RegistroAuditoria.MAX_USUARIO);
                    Long negocio = intentado == null || intentado.isBlank() ? null
                        : usuarioDao.findByUsername(intentado.trim().toLowerCase(java.util.Locale.ROOT))
                            .map(Usuario::getNegocioId).orElse(null);
                    if (negocio != null)
                        NegocioActual.ejecutarComo(negocio, () -> auditoria.registrarComo(usuario, AccionAuditoria.LOGIN_FALLIDO,
                            "Usuario", null, () -> (exception instanceof DisabledException ? "Cuenta desactivada"
                                : exception instanceof LockedException ? "Suscripción suspendida" : "Credenciales incorrectas")
                                + " · Desde " + req.getRemoteAddr()));
                    if (exception instanceof LockedException) {
                        res.sendRedirect(req.getContextPath() + "/login?suspendido");
                        return;
                    }
                    redirectLoginError(req, res, attempts.remainingMinutes(req.getParameter("username"), req.getRemoteAddr()));
                })
                .permitAll()
            )
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID").permitAll())
            .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(event -> {
                    HttpServletRequest req = event.getRequest();
                    HttpServletResponse res = event.getResponse();
                    if (req.getRequestURI().startsWith(req.getContextPath() + "/api/"))
                        apiError(res, HttpStatus.UNAUTHORIZED, "Tu sesión fue cerrada por un administrador", "SESION_EXPIRADA");
                    else res.sendRedirect(req.getContextPath() + "/login?expirado");
                })))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**", "/api/publico/**"))
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_CONTENIDO))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(self), microphone=(), geolocation=(), payment=()")))
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    (req, res, e) -> apiError(res, HttpStatus.UNAUTHORIZED, "Debes iniciar sesión", "NO_AUTENTICADO"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .accessDeniedHandler((req, res, e) -> {
                    if (req.getRequestURI().startsWith(req.getContextPath() + "/api/"))
                        apiError(res, HttpStatus.FORBIDDEN, "No tienes permiso para esta operación", "PROHIBIDO");
                    else res.sendRedirect(req.getContextPath() + "/?denegado");
                })
            );
        http.addFilterBefore(new LoginThrottleFilter(attempts), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(new CuentaPendienteFilter(), AuthorizationFilter.class);
        http.addFilterAfter(new NegocioFiltro(modulos, usuarioDao), CuentaPendienteFilter.class);
        return http.build();
    }

    /**
     * Hace relativa la redirección a la pantalla de ingreso. Por defecto Spring Security la arma
     * absoluta con el esquema que ve el servidor, que detrás de un proxy TLS es {@code http://}:
     * el navegador la bloquearía (CSP form-action) o degradaría la conexión a HTTP. Se ajusta el
     * punto de entrada propio del formulario para conservar su negociación de contenido.
     */
    static ObjectPostProcessor<LoginUrlAuthenticationEntryPoint> redireccionLoginRelativa() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends LoginUrlAuthenticationEntryPoint> O postProcess(O entryPoint) {
                entryPoint.setFavorRelativeUris(true);
                return entryPoint;
            }
        };
    }

    static void redirectLoginError(HttpServletRequest req, HttpServletResponse res, long minutes) throws IOException {
        res.sendRedirect(req.getContextPath() + (minutes > 0 ? "/login?blocked=" + minutes : "/login?error"));
    }

    static void apiError(HttpServletResponse res, HttpStatus status, String mensaje, String codigo)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"codigo\":\"" + codigo + "\"}");
    }
}
