package org.apifocal.auth41.plugin.ciba;

import org.apifocal.auth41.ciba.spi.BackchannelAuthStatus;
import org.apifocal.auth41.ciba.spi.BackchannelException;
import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.representations.idm.OAuth2ErrorRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for CibaTokenResource.
 *
 * Tests cover:
 * - Request parameter validation
 * - Client authentication and validation
 * - All backchannel authentication statuses (PENDING, DENIED, ERROR, APPROVED)
 * - User lookup for approved requests
 * - Error handling (BackchannelException, generic exceptions)
 * - Missing BackchannelProvider scenarios
 */
@ExtendWith(MockitoExtension.class)
class CibaTokenResourceTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private ClientModel client;

    @Mock
    private UserModel user;

    @Mock
    private UserProvider userProvider;

    @Mock
    private BackchannelProvider backchannelProvider;

    @Mock
    private HttpHeaders headers;

    private CibaTokenResource tokenResource;
    private MultivaluedMap<String, String> formParams;

    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_AUTH_REQ_ID = "auth-req-123";
    private static final String TEST_USER_ID = "user-456";
    private static final String TEST_USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        // Setup session context
        lenient().when(session.getContext()).thenReturn(context);
        lenient().when(context.getRealm()).thenReturn(realm);
        lenient().when(session.users()).thenReturn(userProvider);

        // Setup client
        lenient().when(realm.getClientByClientId(TEST_CLIENT_ID)).thenReturn(client);
        lenient().when(client.isEnabled()).thenReturn(true);

        // Setup backchannel provider
        lenient().when(session.getProvider(BackchannelProvider.class)).thenReturn(backchannelProvider);

        // Setup user
        lenient().when(user.getId()).thenReturn(TEST_USER_ID);
        lenient().when(user.getUsername()).thenReturn(TEST_USERNAME);
        lenient().when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(user);

        // Initialize form params
        formParams = new MultivaluedHashMap<>();
        formParams.putSingle(CibaConstants.PARAM_AUTH_REQ_ID, TEST_AUTH_REQ_ID);
        formParams.putSingle("client_id", TEST_CLIENT_ID);

        // Create resource
        tokenResource = new CibaTokenResource(session);
    }

    // ==================== Parameter Validation Tests ====================

    @Test
    void shouldRejectRequestWithMissingAuthReqId() throws BackchannelException {
        // Given
        formParams.remove(CibaConstants.PARAM_AUTH_REQ_ID);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing auth_req_id parameter");
    }

    @Test
    void shouldRejectRequestWithNullAuthReqId() throws BackchannelException {
        // Given
        formParams.putSingle(CibaConstants.PARAM_AUTH_REQ_ID, null);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing auth_req_id parameter");
    }

    @Test
    void shouldRejectRequestWithEmptyAuthReqId() throws BackchannelException {
        // Given
        formParams.putSingle(CibaConstants.PARAM_AUTH_REQ_ID, "");

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing auth_req_id parameter");
    }

    @Test
    void shouldRejectRequestWithMissingClientId() throws BackchannelException {
        // Given
        formParams.remove("client_id");

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing client_id parameter");
    }

    @Test
    void shouldRejectRequestWithNullClientId() throws BackchannelException {
        // Given
        formParams.putSingle("client_id", null);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing client_id parameter");
    }

    @Test
    void shouldRejectRequestWithEmptyClientId() throws BackchannelException {
        // Given
        formParams.putSingle("client_id", "");

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing client_id parameter");
    }

    // ==================== Client Validation Tests ====================

    @Test
    void shouldRejectRequestWithInvalidClient() throws BackchannelException {
        // Given
        when(realm.getClientByClientId(TEST_CLIENT_ID)).thenReturn(null);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("unauthorized_client");
        assertThat(error.getErrorDescription()).isEqualTo("Invalid client");
    }

    @Test
    void shouldRejectRequestWithDisabledClient() throws BackchannelException {
        // Given
        when(client.isEnabled()).thenReturn(false);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("unauthorized_client");
        assertThat(error.getErrorDescription()).isEqualTo("Invalid client");
    }

    // ==================== BackchannelProvider Tests ====================

    @Test
    void shouldRejectRequestWhenBackchannelProviderNotAvailable() {
        // Given
        when(session.getProvider(BackchannelProvider.class)).thenReturn(null);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).isEqualTo("CIBA not configured");
    }

    // ==================== Authentication Status Tests ====================

    @Test
    void shouldReturnAuthorizationPendingWhenStatusIsPending() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.PENDING)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo(CibaConstants.ERROR_AUTHORIZATION_PENDING);
        assertThat(error.getErrorDescription()).isEqualTo("The authorization request is still pending");
    }

    @Test
    void shouldReturnAccessDeniedWhenStatusIsDenied() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.DENIED)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo(CibaConstants.ERROR_ACCESS_DENIED);
        assertThat(error.getErrorDescription()).isEqualTo("User denied the authentication request");
    }

    @Test
    void shouldReturnAccessDeniedWithCustomMessageWhenStatusIsDenied() throws BackchannelException {
        // Given
        String customMessage = "User explicitly denied authentication";
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.DENIED)
            .errorDescription(customMessage)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo(CibaConstants.ERROR_ACCESS_DENIED);
        assertThat(error.getErrorDescription()).isEqualTo(customMessage);
    }

    @Test
    void shouldReturnErrorWhenStatusIsError() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.ERROR)
            .errorCode("timeout")
            .errorDescription("Authentication timed out")
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("timeout");
        assertThat(error.getErrorDescription()).isEqualTo("Authentication timed out");
    }

    @Test
    void shouldReturnGenericErrorWhenStatusIsErrorWithoutDetails() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.ERROR)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).isEqualTo("Authentication failed");
    }

    @Test
    void shouldReturnSuccessResponseWhenStatusIsApproved() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.APPROVED)
            .userId(TEST_USER_ID)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody).containsEntry("status", "APPROVED");
        assertThat(responseBody).containsEntry("auth_req_id", TEST_AUTH_REQ_ID);
        assertThat(responseBody).containsEntry("user_id", TEST_USER_ID);
        assertThat(responseBody).containsEntry("username", TEST_USERNAME);
        assertThat(responseBody).containsKey("message");
    }

    @Test
    void shouldReturnErrorWhenUserNotFoundForApprovedRequest() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.APPROVED)
            .userId(TEST_USER_ID)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);
        when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(null);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_grant");
        assertThat(error.getErrorDescription()).isEqualTo("User not found");
    }

    // ==================== Exception Handling Tests ====================

    @Test
    void shouldHandleBackchannelException() throws BackchannelException {
        // Given
        when(backchannelProvider.getAuthenticationStatus(anyString()))
            .thenThrow(new BackchannelException("Backchannel service unavailable"));

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).isEqualTo("Failed to check authentication status");
    }

    @Test
    void shouldHandleGenericException() throws BackchannelException {
        // Given
        when(backchannelProvider.getAuthenticationStatus(anyString()))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).isEqualTo("Internal server error");
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleWhitespaceOnlyAuthReqId() throws BackchannelException {
        // Given
        formParams.putSingle(CibaConstants.PARAM_AUTH_REQ_ID, "   ");

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing auth_req_id parameter");
    }

    @Test
    void shouldHandleWhitespaceOnlyClientId() throws BackchannelException {
        // Given
        formParams.putSingle("client_id", "   ");

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).isEqualTo("Missing client_id parameter");
    }

    @Test
    void shouldAcceptValidTokenPollRequest() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.PENDING)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo(CibaConstants.ERROR_AUTHORIZATION_PENDING);
    }

    @Test
    void shouldHandleApprovedStatusWithCompleteUserInformation() throws BackchannelException {
        // Given
        BackchannelAuthStatus status = BackchannelAuthStatus.builder()
            .authReqId(TEST_AUTH_REQ_ID)
            .status(BackchannelAuthStatus.Status.APPROVED)
            .userId(TEST_USER_ID)
            .build();
        when(backchannelProvider.getAuthenticationStatus(TEST_AUTH_REQ_ID)).thenReturn(status);

        // When
        Response response = tokenResource.token(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody).containsEntry("status", "APPROVED");
        assertThat(responseBody).containsEntry("username", TEST_USERNAME);
        assertThat(responseBody).containsEntry("user_id", TEST_USER_ID);
        assertThat(responseBody).containsEntry("auth_req_id", TEST_AUTH_REQ_ID);
    }
}
