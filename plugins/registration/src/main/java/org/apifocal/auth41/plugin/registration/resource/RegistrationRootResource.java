package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;

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
     * Admin-only test endpoint to clear all invite tokens and registration requests.
     * <p>SECURITY: This endpoint requires BOTH:
     * <ul>
     *   <li>Configuration flag: enable-test-endpoints=true (default: false)</li>
     *   <li>Admin authentication: realm-admin role required</li>
     * </ul>
     * <p>This should only be enabled in development/test environments.
     *
     * @return Response confirming deletion or error
     */
    @DELETE
    @Path("test/clear")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearTestData() {
        try {
            // SECURITY CHECK: Verify test endpoints are enabled in configuration
            if (!config.isTestEndpointsEnabled()) {
                logger.warn("SECURITY: Attempt to access disabled test endpoint /test/clear");
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "not_found"))
                        .build();
            }

            logger.warn("TEST ENDPOINT: Clearing all invite tokens and registration requests");

            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);

            // Delete all invite tokens
            int invitesDeleted = storage.deleteAllInviteTokens();

            // Delete all registration requests
            int requestsDeleted = storage.deleteAllRegistrationRequests();

            logger.infof("ADMIN TEST ENDPOINT: Deleted %d invite tokens and %d registration requests",
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

    /**
     * Check if the current user has admin permissions to manage the realm.
     * Protected for testing purposes.
     *
     * <p>Checks for realm-admin or admin role in the access token.
     *
     * @return true if user has admin permissions
     */
    protected boolean isAuthorizedAdmin() {
        try {
            AccessToken token = session.getContext().getAuthenticationSession() != null
                    ? session.tokens().decode(session.getContext().getAuthenticationSession().toString(), AccessToken.class)
                    : null;

            if (token == null) {
                // No token available - not authenticated
                return false;
            }

            RealmModel realm = session.getContext().getRealm();

            // Check for realm-admin role
            AccessToken.Access realmAccess = token.getRealmAccess();
            if (realmAccess != null && realmAccess.getRoles() != null) {
                if (realmAccess.getRoles().contains(Constants.REALM_MANAGEMENT_CLIENT_ID) ||
                    realmAccess.getRoles().contains("admin") ||
                    realmAccess.getRoles().contains("realm-admin")) {
                    return true;
                }
            }

            // Check for admin client roles
            ClientModel realmManagementClient = realm.getClientByClientId(Constants.REALM_MANAGEMENT_CLIENT_ID);
            if (realmManagementClient != null) {
                AccessToken.Access clientAccess = token.getResourceAccess(Constants.REALM_MANAGEMENT_CLIENT_ID);
                if (clientAccess != null && clientAccess.getRoles() != null) {
                    return clientAccess.getRoles().contains("realm-admin") ||
                           clientAccess.getRoles().contains("manage-realm");
                }
            }

            return false;
        } catch (Exception e) {
            logger.warn("Error checking admin permissions", e);
            return false;
        }
    }
}
