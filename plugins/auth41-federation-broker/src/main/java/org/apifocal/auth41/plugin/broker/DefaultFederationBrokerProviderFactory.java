package org.apifocal.auth41.plugin.broker;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for creating DefaultFederationBrokerProvider instances.
 */
public class DefaultFederationBrokerProviderFactory implements FederationBrokerProviderFactory {

    private static final String PROVIDER_ID = "default";

    @Override
    public FederationBrokerProvider create(KeycloakSession session) {
        return new DefaultFederationBrokerProvider(session);
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
        // No resources to clean up
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
