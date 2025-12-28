package org.apifocal.auth41.plugin.ciba;

import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
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
import java.util.HashMap;
import java.util.Map;

/**
 * CIBA token polling endpoint.
 *
 * Handles POST /ext/ciba/token requests for polling authentication status
 * and retrieving tokens when authentication is approved.
 *
 * NOTE: This is a simplified implementation that returns status information.
 * Full token generation integration with Keycloak's token endpoint will be
 * added in a future version.
 */
public class CibaTokenResource {

    private static final Logger logger = Logger.getLogger(CibaTokenResource.class);

    private final KeycloakSession session;
    private final RealmModel realm;

    public CibaTokenResource(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
    }

    /**
     * CIBA token polling endpoint.
     *
     * Clients poll this endpoint to check if the backchannel authentication
     * has been approved, denied, or is still pending.
     *
     * Per CIBA spec, this would normally be the standard token endpoint with
     * grant_type=urn:openid:params:grant-type:ciba. Although the OAuth2 Grant Type
     * SPI is available in the Keycloak 26.x baseline used by this codebase, we
     * continue to expose a custom endpoint for now to preserve existing behavior
     * and integrations until a full migration to the native SPI is completed.
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@Context HttpHeaders headers,
                         MultivaluedMap<String, String> formParams) {

        try {
            // Validate and extract parameters
            String authReqId = formParams.getFirst(CibaConstants.PARAM_AUTH_REQ_ID);
            String clientId = formParams.getFirst("client_id");

            if (ValidationUtils.isNullOrEmpty(authReqId)) {
                logger.warn("Missing auth_req_id in CIBA token request");
                return errorResponse("invalid_request", "Missing auth_req_id parameter", Response.Status.BAD_REQUEST);
            }

            if (ValidationUtils.isNullOrEmpty(clientId)) {
                logger.warn("Missing client_id in CIBA token request");
                return errorResponse("invalid_request", "Missing client_id parameter", Response.Status.BAD_REQUEST);
            }

            // Authenticate client (basic validation for now)
            ClientModel client = realm.getClientByClientId(clientId);
            if (client == null || !client.isEnabled()) {
                logger.warnf("Invalid or disabled client in CIBA token request: %s", clientId);
                return errorResponse("unauthorized_client", "Invalid client", Response.Status.UNAUTHORIZED);
            }

            // TODO: Implement proper client authentication (client_secret, JWT, mTLS)

            // Get backchannel provider
            BackchannelProvider backchannelProvider = session.getProvider(BackchannelProvider.class);
            if (backchannelProvider == null) {
                logger.error("No BackchannelProvider available for CIBA token request");
                return errorResponse("server_error", "CIBA not configured", Response.Status.INTERNAL_SERVER_ERROR);
            }

            // Check authentication status
            BackchannelAuthStatus status = backchannelProvider.getAuthenticationStatus(authReqId);

            // Verify status is not null
            if (status == null) {
                logger.errorf("BackchannelProvider returned null status for auth_req_id: %s", authReqId);
                return errorResponse("invalid_request", "Authentication request not found", Response.Status.BAD_REQUEST);
            }

            logger.debugf("CIBA token poll: auth_req_id=%s, status=%s, client=%s",
                authReqId, status.getStatus(), clientId);

            // Handle different statuses
            switch (status.getStatus()) {
                case PENDING:
                    // Authentication still pending - client should continue polling
                    logger.debugf("CIBA authentication pending: %s", authReqId);
                    return errorResponse(
                        CibaConstants.ERROR_AUTHORIZATION_PENDING,
                        "The authorization request is still pending",
                        Response.Status.BAD_REQUEST
                    );

                case DENIED:
                    // User denied the authentication
                    logger.warnf("CIBA authentication denied: %s", authReqId);
                    return errorResponse(
                        CibaConstants.ERROR_ACCESS_DENIED,
                        status.getErrorDescription() != null ? status.getErrorDescription() : "User denied the authentication request",
                        Response.Status.FORBIDDEN
                    );

                case ERROR:
                    // Error occurred during authentication
                    logger.errorf("CIBA authentication error: %s, error=%s", authReqId, status.getErrorCode());
                    return errorResponse(
                        status.getErrorCode() != null ? status.getErrorCode() : "server_error",
                        status.getErrorDescription() != null ? status.getErrorDescription() : "Authentication failed",
                        Response.Status.BAD_REQUEST
                    );

                case APPROVED:
                    // Authentication approved - return success response
                    logger.infof("CIBA authentication approved: %s, user=%s", authReqId, status.getUserId());

                    // Ensure userId is present before looking up the user
                    if (status.getUserId() == null) {
                        logger.errorf("Missing userId for approved CIBA request: authReqId=%s", authReqId);
                        return errorResponse(
                            "invalid_grant",
                            "User information is missing for the approved authentication request",
                            Response.Status.BAD_REQUEST
                        );
                    }

                    // Verify user exists
                    UserModel user = session.users().getUserById(realm, status.getUserId());
                    if (user == null) {
                        logger.errorf("User not found for approved CIBA request: userId=%s, authReqId=%s",
                            status.getUserId(), authReqId);
                        return errorResponse("invalid_grant", "User not found", Response.Status.BAD_REQUEST);
                    }

                    // TODO: Generate actual OAuth2 tokens using Keycloak's TokenManager
                    // For now, return a success response with user information
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "APPROVED");
                    response.put("auth_req_id", authReqId);
                    response.put("user_id", status.getUserId());
                    response.put("username", user.getUsername());
                    response.put("message", "Authentication approved. Token generation will be implemented in next version.");

                    // Note: In production, this should return:
                    // - access_token
                    // - token_type (Bearer)
                    // - expires_in
                    // - refresh_token (optional)
                    // - id_token (if openid scope)

                    return Response.ok(response).build();

                default:
                    logger.errorf("Unknown CIBA authentication status: %s", status.getStatus());
                    return errorResponse("server_error", "Unknown authentication status", Response.Status.INTERNAL_SERVER_ERROR);
            }

        } catch (BackchannelException e) {
            logger.errorf(e, "Backchannel error during CIBA token request");
            return errorResponse("server_error", "Failed to check authentication status", Response.Status.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.errorf(e, "Unexpected error during CIBA token request");
            return errorResponse("server_error", "Internal server error", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response errorResponse(String error, String errorDescription, Response.Status status) {
        OAuth2ErrorRepresentation errorRep = new OAuth2ErrorRepresentation();
        errorRep.setError(error);
        errorRep.setErrorDescription(errorDescription);

        return Response.status(status).entity(errorRep).build();
    }
}
