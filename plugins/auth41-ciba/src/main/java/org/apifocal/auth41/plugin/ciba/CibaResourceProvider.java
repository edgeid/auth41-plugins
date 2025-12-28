package org.apifocal.auth41.plugin.ciba;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

/**
 * Resource provider that exposes CIBA endpoints under /realms/{realm}/ext/ciba
 */
public class CibaResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public CibaResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new CibaAuthenticationResource(session);
    }

    @Override
    public void close() {
        // No resources to close
    }
}
