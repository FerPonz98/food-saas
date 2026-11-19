package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Prueba el ingreso contra un Tomcat real, como lo ve el navegador detrás del proxy TLS de
 * Railway (o de un dominio propio) y en localhost. La CSP {@code form-action 'self'} hace que
 * el navegador bloquee cualquier redirección del POST de ingreso hacia otro origen, incluido
 * {@code http://} cuando la página se abrió por {@code https://}; por eso toda {@code Location}
 * debe ser relativa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:proxyHttps;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "server.forward-headers-strategy=native",
    "app.security.login.max-attempts=50"
})
class ProxyHttpsLoginTests {
    private static final String SUPERADMIN = "superadmin";
    private static final String CLAVE = "TestSuperadmin-2026";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Cabeceras que añade el proxy de Railway: TLS terminado en el borde y dominio público. */
    private static final String[] PROXY_HTTPS = {
        "X-Forwarded-Proto", "https", "X-Forwarded-Host", "demo.up.railway.app", "X-Forwarded-Port", "443",
        "X-Forwarded-For", "203.0.113.7"};
    private static final String[] PROXY_DOMINIO_PROPIO = {
        "X-Forwarded-Proto", "https", "X-Forwarded-Host", "pedidos.minegocio.com", "X-Forwarded-Port", "443"};
    private static final String[] DIRECTO = {};

    @Test
    void loginBehindRailwayHttpsProxyRedirectsWithinSameOrigin() throws Exception {
        HttpResponse<String> respuesta = login(PROXY_HTTPS, SUPERADMIN, CLAVE);
        assertEquals(302, respuesta.statusCode());
        String destino = location(respuesta);
        assertSameOriginRelative(destino);
        assertTrue(destino.equals("/") || destino.equals("/cuenta"), "Ingreso correcto: " + destino);
        assertTrue(respuesta.headers().firstValue("Strict-Transport-Security").isPresent(),
            "El servidor debe reconocer la petición como HTTPS gracias a X-Forwarded-Proto");
        assertCspIntacta(respuesta);
        assertNotNull(cookieSesion(respuesta), "El ingreso renueva la sesión");
    }

    @Test
    void loginOnCustomDomainRedirectsWithinSameOrigin() throws Exception {
        HttpResponse<String> respuesta = login(PROXY_DOMINIO_PROPIO, SUPERADMIN, CLAVE);
        assertEquals(302, respuesta.statusCode());
        assertSameOriginRelative(location(respuesta));
        assertFalse(location(respuesta).contains("railway"), "No depende de ningún dominio fijo");
    }

    @Test
    void loginOnLocalhostOverHttpStillWorks() throws Exception {
        HttpResponse<String> respuesta = login(DIRECTO, SUPERADMIN, CLAVE);
        assertEquals(302, respuesta.statusCode());
        String destino = location(respuesta);
        assertSameOriginRelative(destino);
        assertTrue(destino.equals("/") || destino.equals("/cuenta"), "Ingreso correcto: " + destino);
        assertTrue(respuesta.headers().firstValue("Strict-Transport-Security").isEmpty(),
            "Sin HTTPS no se envía HSTS (rompería localhost)");
    }

    @Test
    void wrongPasswordRedirectsRelativeToLoginError() throws Exception {
        HttpResponse<String> respuesta = login(PROXY_HTTPS, SUPERADMIN, "ClaveIncorrecta-1");
        assertEquals(302, respuesta.statusCode());
        assertEquals("/login?error", location(respuesta));
    }

    @Test
    void csrfIsStillEnforcedOnLogin() throws Exception {
        HttpResponse<String> sinToken = send(post("/login", "username=" + SUPERADMIN + "&password=" + CLAVE, null, PROXY_HTTPS));
        assertFalse(List.of("/", "/cuenta").contains(sinToken.headers().firstValue("Location").orElse("")),
            "Sin token CSRF no debe autenticar");
        assertTrue(sinToken.statusCode() == 403 || location(sinToken).equals("/?denegado"),
            "Rechazo CSRF: " + sinToken.statusCode());

        HttpResponse<String> pagina = send(get("/login", null, PROXY_HTTPS));
        String sesion = cookieSesion(pagina);
        HttpResponse<String> tokenFalso = send(post("/login", form(SUPERADMIN, CLAVE, "token-falso"), sesion, PROXY_HTTPS));
        assertFalse(List.of("/", "/cuenta").contains(tokenFalso.headers().firstValue("Location").orElse("")),
            "Con token CSRF falso no debe autenticar");
    }

    @Test
    void protectedPagesRedirectToLoginWithRelativeUrl() throws Exception {
        for (String[] origen : List.of(PROXY_HTTPS, PROXY_DOMINIO_PROPIO, DIRECTO)) {
            HttpResponse<String> respuesta = send(get("/admin", null, origen));
            assertEquals(302, respuesta.statusCode());
            assertEquals("/login", location(respuesta));
        }
        HttpResponse<String> api = send(get("/api/admin/usuarios", null, PROXY_HTTPS));
        assertEquals(401, api.statusCode(), "Las API siguen respondiendo 401 JSON, sin redirección");
    }

    @Test
    void logoutRedirectsRelative() throws Exception {
        HttpResponse<String> ingreso = login(PROXY_HTTPS, SUPERADMIN, CLAVE);
        String sesion = cookieSesion(ingreso);
        HttpResponse<String> pagina = send(get("/cuenta", sesion, PROXY_HTTPS));
        String token = csrf(pagina.body());
        HttpResponse<String> salida = send(post("/logout", "_csrf=" + enc(token), sesion, PROXY_HTTPS));
        assertEquals(302, salida.statusCode());
        assertEquals("/login?logout", location(salida));
    }

    @Test
    void loginPageSendsFullCspIncludingFormAction() throws Exception {
        for (String[] origen : List.of(PROXY_HTTPS, DIRECTO)) {
            HttpResponse<String> pagina = send(get("/login", null, origen));
            assertEquals(200, pagina.statusCode());
            assertCspIntacta(pagina);
            assertTrue(pagina.body().contains("action=\"/login\""), "El formulario envía a una ruta relativa");
        }
    }

    private HttpResponse<String> login(String[] origen, String usuario, String clave) throws Exception {
        HttpResponse<String> pagina = send(get("/login", null, origen));
        assertEquals(200, pagina.statusCode());
        String sesion = cookieSesion(pagina);
        assertNotNull(sesion, "La pantalla de ingreso crea la sesión que guarda el token CSRF");
        return send(post("/login", form(usuario, clave, csrf(pagina.body())), sesion, origen));
    }

    private static void assertSameOriginRelative(String destino) {
        assertTrue(destino.startsWith("/") && !destino.startsWith("//"),
            "La redirección debe ser relativa al origen para cumplir form-action 'self': " + destino);
    }

    private static void assertCspIntacta(HttpResponse<String> respuesta) {
        String csp = respuesta.headers().firstValue("Content-Security-Policy").orElse("");
        assertTrue(csp.contains("form-action 'self'"), "CSP form-action vigente: " + csp);
        assertTrue(csp.contains("default-src 'self'"), csp);
        assertTrue(csp.contains("frame-ancestors 'self'"), csp);
    }

    private HttpRequest get(String ruta, String sesion, String[] origen) {
        return base(ruta, sesion, origen).GET().build();
    }

    private HttpRequest post(String ruta, String cuerpo, String sesion, String[] origen) {
        return base(ruta, sesion, origen).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
    }

    private HttpRequest.Builder base(String ruta, String sesion, String[] origen) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
            .header("Accept", "text/html");
        for (int i = 0; i < origen.length; i += 2) b.header(origen[i], origen[i + 1]);
        if (sesion != null) b.header("Cookie", "JSESSIONID=" + sesion);
        return b;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String form(String usuario, String clave, String token) {
        return "username=" + enc(usuario) + "&password=" + enc(clave) + "&_csrf=" + enc(token);
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String location(HttpResponse<String> respuesta) {
        return respuesta.headers().firstValue("Location").orElse("");
    }

    private static String csrf(String html) {
        Matcher m = CSRF.matcher(html);
        assertTrue(m.find(), "La página incluye el token CSRF");
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static String cookieSesion(HttpResponse<String> respuesta) {
        Optional<String> cookie = respuesta.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID=")).findFirst();
        return cookie.map(c -> c.substring("JSESSIONID=".length(), c.indexOf(';') > 0 ? c.indexOf(';') : c.length()))
            .orElse(null);
    }
}
