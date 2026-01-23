package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for invite token operations.
 *
 * <p>Endpoint: POST /realms/{realm}/registration/invite
 *
 * <p>Handles invite token generation with IP-based rate limiting.
 */
public class InviteResource {

    private static final Logger logger = Logger.getLogger(InviteResource.class);

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public InviteResource(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Request an invite token.
     *
     * <p>Request body: {} (empty JSON object)
     *
     * <p>Response:
     * <pre>
     * {
     *   "invite_token": "uuid-string",
     *   "expires_in": 300
     * }
     * </pre>
     *
     * <p>Rate limiting: Maximum of 3 invites per IP address per 5-minute window
     *
     * @return Response with invite token or error
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestInviteToken() {
        try {
            RealmModel realm = session.getContext().getRealm();
            if (realm == null) {
                logger.warn("No realm in context");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_realm"))
                        .build();
            }

            // Get client IP address
            String ipAddress = getClientIpAddress();
            if (ipAddress == null) {
                logger.warn("Could not determine client IP address");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request"))
                        .build();
            }

            // Check rate limiting
            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);
            Instant rateLimitWindow = Instant.now().minusSeconds(config.getRateLimitWindowSeconds());
            long recentInvites = storage.countRecentInvitesByIp(ipAddress, rateLimitWindow);

            if (recentInvites >= 3) {
                logger.warnf("Rate limit exceeded for IP %s: %d invites in window", ipAddress, recentInvites);
                return Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .entity(Map.of(
                                "error", "rate_limit_exceeded",
                                "error_description", "Too many invite requests. Please try again later."
                        ))
                        .build();
            }

            // Generate invite token
            String tokenValue = UUID.randomUUID().toString();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(config.getInviteTtlSeconds());

            InviteToken token = InviteToken.builder()
                    .inviteToken(tokenValue)
                    .ipAddress(ipAddress)
                    .realmId(realm.getId())
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .used(false)
                    .build();

            storage.createInviteToken(token);

            logger.debugf("Created invite token for IP %s in realm %s", ipAddress, realm.getName());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("invite_token", tokenValue);
            response.put("expires_in", config.getInviteTtlSeconds());

            return Response.ok(response).build();

        } catch (Exception e) {
            logger.error("Error creating invite token", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "server_error"))
                    .build();
        }
    }

    /**
     * Get client IP address from request context.
     *
     * @return IP address or null if not available
     */
    private String getClientIpAddress() {
        // Try to get IP from Keycloak session context
        if (session.getContext().getConnection() != null) {
            return session.getContext().getConnection().getRemoteAddr();
        }
        return null;
    }
}
