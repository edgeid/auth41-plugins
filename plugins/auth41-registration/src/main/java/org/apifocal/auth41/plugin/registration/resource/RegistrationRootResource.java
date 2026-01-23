package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.Path;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

/**
 * Root JAX-RS resource for registration endpoints.
 *
 * <p>Available at: /realms/{realm}/registration/
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /invite - Request invite token</li>
 *   <li>POST /request - Submit registration request with invite token</li>
 *   <li>GET /status/{requestId} - Poll registration status</li>
 * </ul>
 */
public class RegistrationRootResource {

    private static final Logger logger = Logger.getLogger(RegistrationRootResource.class);

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public RegistrationRootResource(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Invite token endpoint.
     *
     * @return InviteResource
     */
    @Path("invite")
    public InviteResource invite() {
        return new InviteResource(session, config);
    }

    /**
     * Registration request endpoint.
     *
     * @return RequestResource
     */
    @Path("request")
    public RequestResource request() {
        return new RequestResource(session, config);
    }

    /**
     * Status polling endpoint.
     *
     * @return StatusResource
     */
    @Path("status")
    public StatusResource status() {
        return new StatusResource(session, config);
    }
}
