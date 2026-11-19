package com.sistema.pedidosCori.config;

import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Hace que todas las redirecciones de Tomcat ({@code sendRedirect}) usen una URL relativa en la
 * cabecera {@code Location}. El navegador la resuelve contra el origen con el que entró
 * (https://, dominio de Railway o dominio propio), de modo que la redirección posterior al POST
 * de ingreso nunca viola la CSP {@code form-action 'self'} aunque el proxy no informe el esquema.
 * Se aplica en código, al final de la cadena de personalizaciones, para que ninguna propiedad
 * la revierta.
 */
@Configuration(proxyBeanMethods = false)
public class ProxyConfig implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, Ordered {
    @Override
    public void customize(ConfigurableTomcatWebServerFactory factory) {
        factory.addContextCustomizers(context -> context.setUseRelativeRedirects(true));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
