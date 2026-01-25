package org.apifocal.auth41.plugin.ciba;

import org.keycloak.models.KeycloakSession;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.ext.Provider;

/**
 * Root resource for CIBA endpoints.
 *
 * Provides sub-resources:
 * - /realms/{realm}/ext/ciba/auth - Backchannel authentication initiation
 * - /realms/{realm}/ext/ciba/token - Token polling endpoint
 *
 * Note: @Provider annotation is required for Keycloak 24+ (RESTEasy Reactive)
 */
@Provider
public class CibaRootResource {

    private final KeycloakSession session;

    public CibaRootResource(KeycloakSession session) {
        this.session = session;
    }

    /**
     * Backchannel authentication endpoint.
     *
     * POST /realms/{realm}/ext/ciba/auth
     */
    @Path("auth")
    public CibaAuthenticationResource auth() {
        return new CibaAuthenticationResource(session);
    }

    /**
     * Token polling endpoint for CIBA grant type.
     *
     * POST /realms/{realm}/ext/ciba/token
     */
    @Path("token")
    public CibaTokenResource token() {
        return new CibaTokenResource(session);
    }
}
