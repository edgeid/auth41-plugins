package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for registration request operations.
 *
 * <p>Endpoint: POST /realms/{realm}/registration/request
 *
 * <p>Handles registration request submission with invite token validation.
 */
public class RequestResource {

    private static final Logger logger = Logger.getLogger(RequestResource.class);

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public RequestResource(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Submit a registration request.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "invite_token": "uuid-string",
     *   "email": "user@example.com",
     *   "attributes": {
     *     "firstName": "John",
     *     "lastName": "Doe",
     *     "custom_field": "value"
     *   }
     * }
     * </pre>
     *
     * <p>Response:
     * <pre>
     * {
     *   "request_id": "uuid-string",
     *   "status": "pending",
     *   "polling_interval": 5
     * }
     * </pre>
     *
     * @param request Registration request data
     * @return Response with request ID or error
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitRegistrationRequest(Map<String, Object> request) {
        try {
            RealmModel realm = session.getContext().getRealm();
            if (realm == null) {
                logger.warn("No realm in context");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_realm"))
                        .build();
            }

            // Validate request body
            String inviteTokenValue = (String) request.get("invite_token");
            String email = (String) request.get("email");
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) request.get("attributes");

            if (inviteTokenValue == null || inviteTokenValue.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request", "error_description", "invite_token is required"))
                        .build();
            }

            if (email == null || email.trim().isEmpty() || !isValidEmail(email)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request", "error_description", "Valid email is required"))
                        .build();
            }

            if (attributes == null) {
                attributes = new HashMap<>();
            }

            // Validate invite token
            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);
            InviteToken inviteToken = storage.getInviteToken(inviteTokenValue);

            if (inviteToken == null) {
                logger.warnf("Invite token not found: %s", inviteTokenValue);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_token", "error_description", "Invalid invite token"))
                        .build();
            }

            if (inviteToken.isUsed()) {
                logger.warnf("Invite token already used: %s", inviteTokenValue);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_token", "error_description", "Invite token already used"))
                        .build();
            }

            if (inviteToken.isExpired()) {
                logger.warnf("Invite token expired: %s", inviteTokenValue);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "expired_token", "error_description", "Invite token has expired"))
                        .build();
            }

            // Check if email already registered
            if (session.users().getUserByEmail(realm, email) != null) {
                logger.warnf("Email already registered: %s", email);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "email_exists", "error_description", "Email already registered"))
                        .build();
            }

            // Create registration request
            String requestId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(config.getRequestTtlSeconds());

            RegistrationRequest registrationRequest = RegistrationRequest.builder()
                    .requestId(requestId)
                    .email(email)
                    .realmId(realm.getId())
                    .attributes(attributes)
                    .status(RegistrationRequest.Status.PENDING)
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .build();

            storage.createRegistrationRequest(registrationRequest);

            // Mark invite token as used
            storage.markInviteTokenUsed(inviteTokenValue);

            logger.infof("Created registration request %s for email %s in realm %s",
                    requestId, email, realm.getName());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "pending");
            response.put("polling_interval", config.getPollingIntervalSeconds());

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid registration request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid_request", "error_description", e.getMessage()))
                    .build();
        } catch (Exception e) {
            logger.error("Error processing registration request", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "server_error"))
                    .build();
        }
    }

    /**
     * Basic email validation.
     *
     * @param email Email address to validate
     * @return true if email format is valid
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
