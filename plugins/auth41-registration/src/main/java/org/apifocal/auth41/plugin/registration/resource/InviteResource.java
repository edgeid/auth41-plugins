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

            if (recentInvites >= config.getRateLimitMaxInvites()) {
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
     * <p>This method retrieves the client IP address using Keycloak's ClientConnection,
     * which automatically handles X-Forwarded-For headers when Keycloak is properly
     * configured for reverse proxy environments.
     *
     * <p><strong>Important for Production Deployment:</strong>
     * <ul>
     *   <li>Keycloak must be started with proxy mode: {@code --proxy edge} or {@code --proxy reencrypt}</li>
     *   <li>This enables Keycloak to trust X-Forwarded-For, X-Forwarded-Proto, and X-Forwarded-Host headers</li>
     *   <li>Without proper proxy configuration, all requests will appear to come from the proxy's IP</li>
     *   <li>This breaks rate limiting as all clients will be treated as a single IP address</li>
     * </ul>
     *
     * <p>Example Keycloak startup:
     * <pre>
     * bin/kc.sh start --proxy edge --hostname=auth.example.com
     * </pre>
     *
     * <p>For more details, see:
     * <a href="https://www.keycloak.org/server/reverseproxy">Keycloak Reverse Proxy Documentation</a>
     *
     * @return Client IP address, or null if not available
     */
    private String getClientIpAddress() {
        // Keycloak's ClientConnection.getRemoteAddr() handles X-Forwarded-For automatically
        // when proxy mode is configured. The order of precedence is:
        // 1. X-Forwarded-For header (first non-private IP if proxy mode enabled)
        // 2. Direct connection remote address
        if (session.getContext().getConnection() != null) {
            String remoteAddr = session.getContext().getConnection().getRemoteAddr();
            if (remoteAddr != null && !remoteAddr.isEmpty()) {
                return remoteAddr;
            }
        }

        // This should rarely happen - log warning for debugging
        logger.warn("Could not determine client IP address from connection context");
        return null;
    }
}
