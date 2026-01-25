package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST resource for registration status polling.
 *
 * <p>Endpoint: POST /realms/{realm}/registration/status
 * <p>Request body: {"request_id": "..."}
 *
 * <p>Handles CIBA-style polling of registration request status.
 */
public class StatusResource {

    private static final Logger logger = Logger.getLogger(StatusResource.class);

    private final KeycloakSession session;
    private final RegistrationConfig config;

    public StatusResource(KeycloakSession session, RegistrationConfig config) {
        this.session = session;
        this.config = config;
    }

    /**
     * Poll registration request status.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "request_id": "uuid-string"
     * }
     * </pre>
     *
     * <p>Response for pending request:
     * <pre>
     * {
     *   "status": "pending",
     *   "polling_interval": 5
     * }
     * </pre>
     *
     * <p>Response for approved request:
     * <pre>
     * {
     *   "status": "approved",
     *   "user_id": "uuid-string"
     * }
     * </pre>
     *
     * <p>Response for denied request:
     * <pre>
     * {
     *   "status": "denied",
     *   "error": "request_denied"
     * }
     * </pre>
     *
     * @param requestBody Request body containing request_id
     * @return Response with status or error
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus(Map<String, Object> requestBody) {
        try {
            RealmModel realm = session.getContext().getRealm();
            if (realm == null) {
                logger.warn("No realm in context");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_realm"))
                        .build();
            }

            // Extract request_id from request body
            if (requestBody == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request", "error_description", "Request body is required"))
                        .build();
            }

            String requestId = (String) requestBody.get("request_id");
            if (requestId == null || requestId.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid_request", "error_description", "request_id is required"))
                        .build();
            }

            // Get registration request
            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);
            RegistrationRequest request = storage.getRegistrationRequest(requestId);

            if (request == null) {
                logger.warnf("Registration request not found: %s", requestId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "not_found", "error_description", "Registration request not found"))
                        .build();
            }

            // Build response based on status
            Map<String, Object> response = new HashMap<>();

            switch (request.getStatus()) {
                case PENDING:
                    // Check if request expired (only for PENDING status)
                    if (Instant.now().isAfter(request.getExpiresAt())) {
                        // Update status to expired
                        RegistrationRequest expired = RegistrationRequest.builder()
                                .requestId(request.getRequestId())
                                .email(request.getEmail())
                                .realmId(request.getRealmId())
                                .attributes(request.getAttributes())
                                .status(RegistrationRequest.Status.EXPIRED)
                                .createdAt(request.getCreatedAt())
                                .approvedAt(request.getApprovedAt())
                                .expiresAt(request.getExpiresAt())
                                .userId(request.getUserId())
                                .build();
                        storage.updateRegistrationRequest(expired);

                        logger.infof("Registration request expired: %s", requestId);

                        response.put("status", "expired");
                        response.put("error", "expired_request");
                        response.put("error_description", "Registration request has expired");
                        return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
                    }

                    response.put("status", "pending");
                    response.put("polling_interval", config.getPollingIntervalSeconds());
                    return Response.ok(response).build();

                case APPROVED:
                    response.put("status", "approved");
                    if (request.getUserId() != null) {
                        response.put("user_id", request.getUserId());
                    }
                    if (request.getEmail() != null) {
                        response.put("email", request.getEmail());
                    }
                    return Response.ok(response).build();

                case DENIED:
                    response.put("status", "denied");
                    response.put("error", "request_denied");
                    response.put("error_description", "Registration request was denied");
                    return Response.status(Response.Status.FORBIDDEN).entity(response).build();

                case EXPIRED:
                    response.put("status", "expired");
                    response.put("error", "expired_request");
                    response.put("error_description", "Registration request has expired");
                    return Response.status(Response.Status.BAD_REQUEST).entity(response).build();

                case ERROR:
                    response.put("status", "error");
                    response.put("error", "processing_error");
                    response.put("error_description", "An error occurred processing your registration");
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();

                default:
                    logger.warnf("Unknown status for request %s: %s", requestId, request.getStatus());
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", "server_error"))
                            .build();
            }

        } catch (Exception e) {
            logger.error("Error checking registration status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "server_error"))
                    .build();
        }
    }
}
