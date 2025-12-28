package org.apifocal.auth41.plugin.ciba;

import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
import org.apifocal.auth41.ciba.spi.BackchannelException;
import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.apifocal.auth41.common.validation.ValidationUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for CIBA backchannel authentication endpoint.
 *
 * Handles POST /ext/ciba/auth requests
 */
public class CibaAuthenticationResource {

    private static final Logger logger = Logger.getLogger(CibaAuthenticationResource.class);

    private final KeycloakSession session;
    private final RealmModel realm;

    public CibaAuthenticationResource(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
    }

    /**
     * CIBA backchannel authentication endpoint.
     *
     * Spec: https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(@Context HttpHeaders headers,
                                  MultivaluedMap<String, String> formParams) {

        try {
            // 1. Extract and validate parameters
            CibaAuthRequest request = validateRequest(formParams);

            // 2. Authenticate client
            ClientModel client = authenticateClient(request);

            // 3. Resolve user from login hint
            UserModel user = resolveUser(request.getLoginHint());
            if (user == null) {
                logger.warnf("User not found for login_hint: %s", request.getLoginHint());
                return errorResponse("invalid_request", "Unknown user", Response.Status.BAD_REQUEST);
            }

            // 4. Generate auth_req_id
            String authReqId = generateAuthReqId();

            // 5. Get backchannel provider
            BackchannelProvider backchannelProvider = session.getProvider(BackchannelProvider.class);
            if (backchannelProvider == null) {
                logger.error("No BackchannelProvider available");
                return errorResponse("server_error", "CIBA not configured", Response.Status.INTERNAL_SERVER_ERROR);
            }

            // 6. Create backchannel auth request
            BackchannelAuthRequest backchannelRequest = BackchannelAuthRequest.builder()
                .authReqId(authReqId)
                .clientId(client.getClientId())
                .scope(request.getScope())
                .loginHint(request.getLoginHint())
                .bindingMessage(request.getBindingMessage())
                .userCode(request.getUserCode())
                .requestedExpiry(request.getRequestedExpiry())
                .createdAt(Instant.now())
                .build();

            // 7. Initiate backchannel authentication
            backchannelProvider.initiateAuthentication(backchannelRequest);

            logger.infof("CIBA authentication initiated: auth_req_id=%s, client=%s, user=%s",
                authReqId, client.getClientId(), user.getUsername());

            // 8. Build response
            Map<String, Object> response = new HashMap<>();
            response.put(CibaConstants.PARAM_AUTH_REQ_ID, authReqId);
            response.put(CibaConstants.PARAM_EXPIRES_IN,
                request.getRequestedExpiry() != null ? request.getRequestedExpiry() : CibaConstants.DEFAULT_EXPIRES_IN);
            response.put(CibaConstants.PARAM_INTERVAL, CibaConstants.DEFAULT_POLL_INTERVAL);

            return Response.ok(response).build();

        } catch (ValidationException e) {
            logger.warnf("CIBA validation error: %s", e.getMessage());
            return errorResponse(e.getErrorCode(), e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (BackchannelException e) {
            logger.errorf(e, "CIBA backchannel error: %s", e.getMessage());
            return errorResponse("server_error", "Backchannel authentication failed", Response.Status.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.errorf(e, "Unexpected CIBA error");
            return errorResponse("server_error", "Internal server error", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private CibaAuthRequest validateRequest(MultivaluedMap<String, String> formParams) throws ValidationException {
        String scope = formParams.getFirst("scope");
        String loginHint = formParams.getFirst(CibaConstants.PARAM_LOGIN_HINT);
        String bindingMessage = formParams.getFirst(CibaConstants.PARAM_BINDING_MESSAGE);
        String userCode = formParams.getFirst(CibaConstants.PARAM_USER_CODE);
        String requestedExpiryStr = formParams.getFirst(CibaConstants.PARAM_REQUESTED_EXPIRY);
        String clientId = formParams.getFirst("client_id");

        // Validate required parameters
        if (ValidationUtils.isNullOrEmpty(loginHint)) {
            throw new ValidationException("invalid_request", "Missing login_hint parameter");
        }

        if (ValidationUtils.isNullOrEmpty(clientId)) {
            throw new ValidationException("invalid_request", "Missing client_id parameter");
        }

        // Validate optional binding_message length
        if (bindingMessage != null && bindingMessage.length() > CibaConstants.MAX_BINDING_MESSAGE_LENGTH) {
            throw new ValidationException("invalid_request",
                String.format("binding_message exceeds maximum length of %d characters",
                    CibaConstants.MAX_BINDING_MESSAGE_LENGTH));
        }

        // Parse optional requested_expiry
        Integer requestedExpiry = null;
        if (requestedExpiryStr != null) {
            try {
                requestedExpiry = Integer.parseInt(requestedExpiryStr);
                if (requestedExpiry <= 0) {
                    throw new ValidationException("invalid_request", "requested_expiry must be positive");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("invalid_request", "Invalid requested_expiry");
            }
        }

        return new CibaAuthRequest(clientId, scope, loginHint, bindingMessage, userCode, requestedExpiry);
    }

    private ClientModel authenticateClient(CibaAuthRequest request) throws ValidationException {
        ClientModel client = realm.getClientByClientId(request.getClientId());
        if (client == null || !client.isEnabled()) {
            throw new ValidationException("unauthorized_client", "Invalid client");
        }

        // TODO: Implement proper client authentication (client_secret, JWT, etc.)
        // For now, we just validate the client exists and is enabled

        return client;
    }

    private UserModel resolveUser(String loginHint) {
        // Try to find user by username
        UserModel user = session.users().getUserByUsername(realm, loginHint);
        if (user != null) {
            return user;
        }

        // Try to find user by email
        if (ValidationUtils.isValidEmail(loginHint)) {
            user = session.users().getUserByEmail(realm, loginHint);
        }

        return user;
    }

    private String generateAuthReqId() {
        return "urn:uuid:" + UUID.randomUUID().toString();
    }

    private Response errorResponse(String error, String errorDescription, Response.Status status) {
        OAuth2ErrorRepresentation errorRep = new OAuth2ErrorRepresentation();
        errorRep.setError(error);
        errorRep.setErrorDescription(errorDescription);

        return Response.status(status).entity(errorRep).build();
    }

    /**
     * Internal request representation
     */
    private static class CibaAuthRequest {
        private final String clientId;
        private final String scope;
        private final String loginHint;
        private final String bindingMessage;
        private final String userCode;
        private final Integer requestedExpiry;

        public CibaAuthRequest(String clientId, String scope, String loginHint,
                               String bindingMessage, String userCode, Integer requestedExpiry) {
            this.clientId = clientId;
            this.scope = scope;
            this.loginHint = loginHint;
            this.bindingMessage = bindingMessage;
            this.userCode = userCode;
            this.requestedExpiry = requestedExpiry;
        }

        public String getClientId() { return clientId; }
        public String getScope() { return scope; }
        public String getLoginHint() { return loginHint; }
        public String getBindingMessage() { return bindingMessage; }
        public String getUserCode() { return userCode; }
        public Integer getRequestedExpiry() { return requestedExpiry; }
    }

    /**
     * Validation exception with OAuth2 error code
     */
    private static class ValidationException extends Exception {
        private final String errorCode;

        public ValidationException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
