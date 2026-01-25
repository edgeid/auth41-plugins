package org.apifocal.auth41.plugin.registration.resource;

import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

/**
 * Provider that creates the root resource for registration endpoints.
 *
 * <p>This provider is instantiated per request and creates the JAX-RS
 * root resource that handles all registration-related endpoints.
 */
public class RegistrationResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public RegistrationResourceProvider(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public Object getResource() {
        return new RegistrationRootResource(session, config);
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
