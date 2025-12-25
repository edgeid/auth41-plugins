package org.apifocal.auth41.plugin.broker;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for creating DefaultFederationBrokerProvider instances.
 *
 * <p>Configuration:
 * <pre>
 * --spi-federation-broker-default-broker-client-id=custom-broker-id
 * </pre>
 */
public class DefaultFederationBrokerProviderFactory implements FederationBrokerProviderFactory {

    private static final String PROVIDER_ID = "default";
    private static final String CONFIG_BROKER_CLIENT_ID = "broker-client-id";

    private String brokerClientId;

    @Override
    public FederationBrokerProvider create(KeycloakSession session) {
        return new DefaultFederationBrokerProvider(session, brokerClientId);
    }

    @Override
    public void init(Config.Scope config) {
        this.brokerClientId = config.get(CONFIG_BROKER_CLIENT_ID);
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
