package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.util.Map;

/**
 * Root JAX-RS resource for registration endpoints.
 *
 * <p>Available at: /realms/{realm}/registration/
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /invite - Request invite token</li>
 *   <li>POST /request - Submit registration request with invite token</li>
 *   <li>POST /status - Poll registration status</li>
 *   <li>DELETE /test/clear - Clear all tokens and requests (test-only)</li>
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

    /**
     * Test-only endpoint to clear all invite tokens and registration requests.
     * <p>WARNING: This is for testing purposes only and should not be exposed in production.
     *
     * @return Response confirming deletion
     */
    @DELETE
    @Path("test/clear")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearTestData() {
        try {
            logger.warn("TEST ENDPOINT: Clearing all invite tokens and registration requests");

            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);

            // Delete all invite tokens
            int invitesDeleted = storage.deleteAllInviteTokens();

            // Delete all registration requests
            int requestsDeleted = storage.deleteAllRegistrationRequests();

            logger.infof("TEST ENDPOINT: Deleted %d invite tokens and %d registration requests",
                    invitesDeleted, requestsDeleted);

            return Response.ok(Map.of(
                    "message", "Test data cleared",
                    "invites_deleted", invitesDeleted,
                    "requests_deleted", requestsDeleted
            )).build();

        } catch (Exception e) {
            logger.error("Error clearing test data", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "server_error"))
                    .build();
        }
    }
}
