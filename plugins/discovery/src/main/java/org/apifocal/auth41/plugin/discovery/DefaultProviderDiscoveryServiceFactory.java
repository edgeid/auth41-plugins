package org.apifocal.auth41.plugin.discovery;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for creating DefaultProviderDiscoveryService instances.
 */
public class DefaultProviderDiscoveryServiceFactory implements ProviderDiscoveryServiceFactory {

    private static final String PROVIDER_ID = "default";

    @Override
    public ProviderDiscoveryService create(KeycloakSession session) {
        return new DefaultProviderDiscoveryService(session);
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // No resources to close
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
