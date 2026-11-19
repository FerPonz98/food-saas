package com.sistema.pedidosCori.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.UsuarioSesion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class NegocioFiltro extends OncePerRequestFilter {
    public static final String ATRIBUTO_INFO = "negocio.info";
    private static final Pattern RUTA_PUBLICA = Pattern.compile("^/(?:n|api/publico)/([a-z0-9-]{2,40})(?:/.*)?$");
    private static final List<String> PERMITIDAS_SUSPENDIDO = List.of("/suspendido", "/logout", "/login", "/error",
        "/terminos", "/favicon.ico");
    private static final List<String> PREFIJOS_LIBRES = List.of("/css/", "/js/", "/img/");

    private final ModuloService modulos;
    private final IUsuarioDao usuarios;

    public NegocioFiltro(ModuloService modulos, IUsuarioDao usuarios) {
        this.modulos = modulos;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ruta = req.getRequestURI().substring(req.getContextPath().length());
        if (PREFIJOS_LIBRES.stream().anyMatch(ruta::startsWith)) {
            chain.doFilter(req, res);
            return;
        }
        long negocio = resolver(ruta);
        Long anterior = NegocioActual.crudo();
        try {
            NegocioActual.establecer(negocio);
            if (negocio > 0) {
                Optional<ModuloService.InfoNegocio> info = modulos.info(negocio);
                if (info.isEmpty() || !info.get().permiteAcceso()) {
                    if (!PERMITIDAS_SUSPENDIDO.contains(ruta)) {
                        rechazar(req, res, ruta);
                        return;
                    }
                } else {
                    req.setAttribute(ATRIBUTO_INFO, info.get());
                }
            }
            chain.doFilter(req, res);
        } finally {
            NegocioActual.establecer(anterior);
        }
    }

    private long resolver(String ruta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            if (auth.getPrincipal() instanceof UsuarioSesion sesion)
                return sesion.esSuperadmin() ? NegocioActual.RAIZ
                    : sesion.getNegocioId() == null ? NegocioActual.NINGUNO : sesion.getNegocioId();
            Optional<Usuario> usuario = usuarios.findByUsername(auth.getName());
            if (usuario.isEmpty()) return NegocioActual.valor();
            if (usuario.get().getRol() == Rol.SUPERADMIN) return NegocioActual.RAIZ;
            return usuario.get().getNegocioId() == null ? NegocioActual.NINGUNO : usuario.get().getNegocioId();
        }
        Matcher m = RUTA_PUBLICA.matcher(ruta);
        if (m.matches()) return modulos.porSlug(m.group(1)).map(ModuloService.InfoNegocio::id).orElse(NegocioActual.NINGUNO);
        return NegocioActual.NINGUNO;
    }

    private void rechazar(HttpServletRequest req, HttpServletResponse res, String ruta) throws IOException {
        boolean autenticado = SecurityContextHolder.getContext().getAuthentication() != null
            && !(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken);
        if (ruta.startsWith("/api/") || ruta.startsWith("/ws")) {
            SecurityConfig.apiError(res, autenticado ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND,
                autenticado ? "La suscripción de este negocio está suspendida" : "Esta carta no está disponible",
                autenticado ? "NEGOCIO_SUSPENDIDO" : "NO_ENCONTRADO");
            return;
        }
        if (autenticado) res.sendRedirect(req.getContextPath() + "/suspendido");
        else res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
