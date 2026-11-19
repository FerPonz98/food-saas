package com.sistema.pedidosCori.config;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import com.sistema.pedidosCori.service.NegocioActual;

@Component
public class ResolutorNegocio implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return NegocioActual.valor();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(Long id) {
        return id != null && id == NegocioActual.RAIZ;
    }

    @Override
    public void customize(Map<String, Object> propiedades) {
        propiedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
