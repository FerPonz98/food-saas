package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sistema.pedidosCori.config.ProxyEnvironmentPostProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ProxyEnvironmentPostProcessorTests {
    private final ProxyEnvironmentPostProcessor processor = new ProxyEnvironmentPostProcessor();

    private StandardEnvironment entorno(Map<String, Object> variables) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        processor.postProcessEnvironment(env, new SpringApplication());
        return env;
    }

    @Test
    void railwayEnablesProxyHeadersAndSecureCookie() {
        StandardEnvironment env = entorno(Map.of("RAILWAY_ENVIRONMENT_NAME", "production"));
        assertEquals("native", env.getProperty("FORWARD_HEADERS_STRATEGY"));
        assertEquals("true", env.getProperty("SESSION_COOKIE_SECURE"));
    }

    @Test
    void explicitVariablesAlwaysWin() {
        StandardEnvironment env = entorno(Map.of("RAILWAY_PROJECT_ID", "abc", "FORWARD_HEADERS_STRATEGY", "framework",
            "SESSION_COOKIE_SECURE", "false"));
        assertEquals("framework", env.getProperty("FORWARD_HEADERS_STRATEGY"));
        assertEquals("false", env.getProperty("SESSION_COOKIE_SECURE"));
    }

    @Test
    void localAndLanInstallationsKeepSafeDefaults() {
        StandardEnvironment env = entorno(Map.of());
        assertNull(env.getProperty("FORWARD_HEADERS_STRATEGY"));
        assertNull(env.getProperty("SESSION_COOKIE_SECURE"));
    }

    @Test
    void genericHttpsProxyCanBeDeclaredOrDisabled() {
        assertEquals("native", entorno(Map.of("APP_BEHIND_HTTPS_PROXY", "true")).getProperty("FORWARD_HEADERS_STRATEGY"));
        assertNull(entorno(Map.of("APP_BEHIND_HTTPS_PROXY", "false", "RAILWAY_ENVIRONMENT", "production"))
            .getProperty("FORWARD_HEADERS_STRATEGY"));
    }
}
