package com.sistema.pedidosCori.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Detecta plataformas que publican la aplicación detrás de un proxy inverso con HTTPS
 * (Railway) y, solo si el operador no las definió, aplica los valores seguros por defecto:
 * confiar en las cabeceras {@code X-Forwarded-*} del proxy interno y emitir la cookie de
 * sesión con {@code Secure}.
 *
 * <p>Sin esto, Tomcat cree que la petición llegó por {@code http://}: genera redirecciones
 * {@code http://} tras el POST de ingreso (que el navegador bloquea por la directiva CSP
 * {@code form-action 'self'} al ser un cambio de esquema), no envía HSTS y los QR apuntan a
 * {@code http://}. Las variables de entorno explícitas siempre tienen prioridad porque esta
 * fuente se agrega al final.
 */
public class ProxyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String FUENTE = "plataformaProxyHttps";
    static final String[] MARCAS_RAILWAY = {"RAILWAY_ENVIRONMENT", "RAILWAY_ENVIRONMENT_NAME", "RAILWAY_PROJECT_ID",
        "RAILWAY_SERVICE_ID", "RAILWAY_PUBLIC_DOMAIN"};

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!detrasDeProxyHttps(environment)) return;
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("FORWARD_HEADERS_STRATEGY", "native");
        valores.put("SESSION_COOKIE_SECURE", "true");
        environment.getPropertySources().addLast(new MapPropertySource(FUENTE, valores));
    }

    static boolean detrasDeProxyHttps(ConfigurableEnvironment environment) {
        if (environment.containsProperty("APP_BEHIND_HTTPS_PROXY"))
            return Boolean.parseBoolean(environment.getProperty("APP_BEHIND_HTTPS_PROXY"));
        for (String marca : MARCAS_RAILWAY) {
            String valor = environment.getProperty(marca);
            if (valor != null && !valor.isBlank()) return true;
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
