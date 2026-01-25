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

            // Handle attributes - could be Map or null
            Map<String, Object> attributes = null;
            Object attributesObj = request.get("attributes");
            if (attributesObj != null) {
                if (attributesObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attrsMap = (Map<String, Object>) attributesObj;
                    attributes = attrsMap;
                } else {
                    // If attributes is not a Map (e.g., String or other type), reject it
                    logger.warnf("Invalid attributes type: %s", attributesObj.getClass().getName());
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "invalid_request", "error_description", "attributes must be a JSON object"))
                            .build();
                }
            } else {
                attributes = new HashMap<>();
            }

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

            // Validate attributes size and content
            String attributeError = validateAttributes(attributes);
            if (attributeError != null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request", "error_description", attributeError))
                        .build();
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

            // Mark invite token as used BEFORE creating registration request
            // This prevents race condition where two concurrent requests could both pass
            // the isUsed() check and create duplicate registration requests.
            // If token is already used, this will throw IllegalStateException.
            try {
                storage.markInviteTokenUsed(inviteTokenValue);
            } catch (IllegalStateException e) {
                logger.warnf("Invite token already used (concurrent request): %s", inviteTokenValue);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_token", "error_description", "Invite token already used"))
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

            logger.infof("Created registration request %s for email %s in realm %s",
                    requestId, email, realm.getName());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "pending");
            response.put("expires_in", config.getRequestTtlSeconds());
            response.put("interval", config.getPollingIntervalSeconds());
            response.put("polling_interval", config.getPollingIntervalSeconds()); // Legacy field

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (IllegalArgumentException e) {
            // Log full exception details internally for debugging
            logger.error("Invalid registration request", e);

            // Return sanitized error message to client
            // Do not expose internal exception messages that could leak sensitive information
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "invalid_request",
                            "error_description", "The registration request is invalid. Please check your input."
                    ))
                    .build();
        } catch (Exception e) {
            // Log full exception details internally for debugging
            logger.error("Error processing registration request", e);

            // Return generic error message to client
            // Do not expose stack traces or internal error details
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "server_error",
                            "error_description", "An internal error occurred. Please try again later."
                    ))
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

    /**
     * Validate attributes map to prevent DoS attacks.
     *
     * <p>Limits:
     * <ul>
     *   <li>Maximum 50 attributes</li>
     *   <li>Maximum 1000 characters per attribute value</li>
     *   <li>Maximum 100 characters per attribute name</li>
     * </ul>
     *
     * @param attributes Attributes map to validate
     * @return Error message if invalid, null if valid
     */
    private String validateAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }

        // Limit number of attributes to prevent memory exhaustion
        if (attributes.size() > 50) {
            return "Too many attributes (maximum 50)";
        }

        // Validate each attribute
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Validate key length
            if (key == null || key.isEmpty()) {
                return "Attribute name cannot be empty";
            }
            if (key.length() > 100) {
                return "Attribute name too long (maximum 100 characters)";
            }

            // Validate value
            if (value != null) {
                String valueStr = String.valueOf(value);
                if (valueStr.length() > 1000) {
                    return "Attribute value too long (maximum 1000 characters)";
                }
            }
        }

        return null;
    }
}
