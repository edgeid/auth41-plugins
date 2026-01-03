package org.apifocal.auth41.plugin.ciba;

import org.apifocal.auth41.ciba.spi.BackchannelAuthRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CibaAuthenticationResourceTest {

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

    private CibaAuthenticationResource resource;

    @BeforeEach
    void setUp() {
        lenient().when(session.getContext()).thenReturn(context);
        lenient().when(context.getRealm()).thenReturn(realm);
        lenient().when(session.users()).thenReturn(userProvider);

        resource = new CibaAuthenticationResource(session);
    }

    @Test
    void shouldSuccessfullyInitiateAuthentication() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("scope", "openid profile");
        formParams.putSingle("binding_message", "Login to MyApp");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(user.getUsername()).thenReturn("testuser");
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody).containsKey(CibaConstants.PARAM_AUTH_REQ_ID);
        assertThat(responseBody).containsKey(CibaConstants.PARAM_EXPIRES_IN);
        assertThat(responseBody).containsKey(CibaConstants.PARAM_INTERVAL);

        String authReqId = (String) responseBody.get(CibaConstants.PARAM_AUTH_REQ_ID);
        assertThat(authReqId).startsWith("urn:uuid:");

        // Verify backchannel provider was called
        ArgumentCaptor<BackchannelAuthRequest> requestCaptor = ArgumentCaptor.forClass(BackchannelAuthRequest.class);
        verify(backchannelProvider).initiateAuthentication(requestCaptor.capture());

        BackchannelAuthRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getAuthReqId()).isEqualTo(authReqId);
        assertThat(capturedRequest.getClientId()).isEqualTo("test-client");
        assertThat(capturedRequest.getLoginHint()).isEqualTo("user@example.com");
        assertThat(capturedRequest.getScope()).isEqualTo("openid profile");
        assertThat(capturedRequest.getBindingMessage()).isEqualTo("Login to MyApp");
    }

    @Test
    void shouldRejectRequestWithMissingLoginHint() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        // Missing login_hint

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("login_hint");

        verify(backchannelProvider, never()).initiateAuthentication(any());
    }

    @Test
    void shouldRejectRequestWithEmptyLoginHint() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "   "); // Empty/whitespace

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("login_hint");
    }

    @Test
    void shouldRejectRequestWithMissingClientId() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("login_hint", "user@example.com");
        // Missing client_id

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("client_id");
    }

    @Test
    void shouldRejectRequestWithInvalidClient() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "invalid-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("invalid-client")).thenReturn(null);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("unauthorized_client");
        assertThat(error.getErrorDescription()).contains("Invalid client");
    }

    @Test
    void shouldRejectRequestWithDisabledClient() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "disabled-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("disabled-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(false);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("unauthorized_client");
        assertThat(error.getErrorDescription()).contains("Invalid client");
    }

    @Test
    void shouldRejectRequestWhenUserNotFound() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "nonexistent@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(userProvider.getUserByUsername(realm, "nonexistent@example.com")).thenReturn(null);
        when(userProvider.getUserByEmail(realm, "nonexistent@example.com")).thenReturn(null);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("Unknown user");
    }

    @Test
    void shouldResolveUserByUsername() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "testuser"); // Username, not email

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByUsername(realm, "testuser")).thenReturn(user);
        when(user.getUsername()).thenReturn("testuser");
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(userProvider).getUserByUsername(realm, "testuser");
    }

    @Test
    void shouldResolveUserByEmail() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByUsername(realm, "user@example.com")).thenReturn(null);
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(user.getUsername()).thenReturn("testuser");
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(userProvider).getUserByEmail(realm, "user@example.com");
    }

    @Test
    void shouldHandleRequestedExpiry() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("requested_expiry", "600");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody.get(CibaConstants.PARAM_EXPIRES_IN)).isEqualTo(600);

        ArgumentCaptor<BackchannelAuthRequest> requestCaptor = ArgumentCaptor.forClass(BackchannelAuthRequest.class);
        verify(backchannelProvider).initiateAuthentication(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestedExpiry()).isEqualTo(600);
    }

    @Test
    void shouldUseDefaultExpiryWhenNotSpecified() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody.get(CibaConstants.PARAM_EXPIRES_IN)).isEqualTo(CibaConstants.DEFAULT_EXPIRES_IN);
    }

    @Test
    void shouldRejectInvalidRequestedExpiry() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("requested_expiry", "invalid");

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("requested_expiry");
    }

    @Test
    void shouldRejectNegativeRequestedExpiry() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("requested_expiry", "-100");

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("must be positive");
    }

    @Test
    void shouldRejectZeroRequestedExpiry() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("requested_expiry", "0");

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("must be positive");
    }

    @Test
    void shouldHandleUserCodeParameter() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("user_code", "123456");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<BackchannelAuthRequest> requestCaptor = ArgumentCaptor.forClass(BackchannelAuthRequest.class);
        verify(backchannelProvider).initiateAuthentication(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getUserCode()).isEqualTo("123456");
    }

    @Test
    void shouldHandleBindingMessageParameter() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("binding_message", "Approve payment of $100");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<BackchannelAuthRequest> requestCaptor = ArgumentCaptor.forClass(BackchannelAuthRequest.class);
        verify(backchannelProvider).initiateAuthentication(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBindingMessage()).isEqualTo("Approve payment of $100");
    }

    @Test
    void shouldAcceptBindingMessageAtMaxLength() throws BackchannelException {
        // Given - Create a binding message exactly at max length (256 chars)
        String maxLengthMessage = "A".repeat(CibaConstants.MAX_BINDING_MESSAGE_LENGTH);
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("binding_message", maxLengthMessage);

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<BackchannelAuthRequest> requestCaptor = ArgumentCaptor.forClass(BackchannelAuthRequest.class);
        verify(backchannelProvider).initiateAuthentication(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBindingMessage()).isEqualTo(maxLengthMessage);
        assertThat(requestCaptor.getValue().getBindingMessage()).hasSize(CibaConstants.MAX_BINDING_MESSAGE_LENGTH);
    }

    @Test
    void shouldRejectBindingMessageExceedingMaxLength() {
        // Given - Create a binding message exceeding max length (257 chars)
        String tooLongMessage = "A".repeat(CibaConstants.MAX_BINDING_MESSAGE_LENGTH + 1);
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("binding_message", tooLongMessage);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("binding_message");
        assertThat(error.getErrorDescription()).contains("exceeds maximum length");
        assertThat(error.getErrorDescription()).contains("256");
    }

    @Test
    void shouldRejectVeryLongBindingMessage() {
        // Given - Create an extremely long binding message (1000 chars)
        String veryLongMessage = "This is a very long binding message. ".repeat(30);
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");
        formParams.putSingle("binding_message", veryLongMessage);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("invalid_request");
        assertThat(error.getErrorDescription()).contains("binding_message");
        assertThat(error.getErrorDescription()).contains("exceeds maximum length");
    }

    @Test
    void shouldReturnServerErrorWhenBackchannelProviderNotAvailable() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(null);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(500);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).contains("CIBA not configured");
    }

    @Test
    void shouldHandleBackchannelProviderException() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        doThrow(new BackchannelException("Network error"))
            .when(backchannelProvider).initiateAuthentication(any());

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(500);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).contains("Backchannel authentication failed");
    }

    @Test
    void shouldHandleUnexpectedException() {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenThrow(new RuntimeException("Database error"));

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(500);

        OAuth2ErrorRepresentation error = (OAuth2ErrorRepresentation) response.getEntity();
        assertThat(error.getError()).isEqualTo("server_error");
        assertThat(error.getErrorDescription()).contains("Internal server error");
    }

    @Test
    void shouldIncludePollIntervalInResponse() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response = resource.authenticate(headers, formParams);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getEntity();
        assertThat(responseBody.get(CibaConstants.PARAM_INTERVAL))
            .isEqualTo(CibaConstants.DEFAULT_POLL_INTERVAL);
    }

    @Test
    void shouldGenerateUniqueAuthReqIds() throws BackchannelException {
        // Given
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("client_id", "test-client");
        formParams.putSingle("login_hint", "user@example.com");

        when(realm.getClientByClientId("test-client")).thenReturn(client);
        when(client.isEnabled()).thenReturn(true);
        when(client.getClientId()).thenReturn("test-client");
        when(userProvider.getUserByEmail(realm, "user@example.com")).thenReturn(user);
        when(session.getProvider(eq(BackchannelProvider.class), any(String.class))).thenReturn(backchannelProvider);

        // When
        Response response1 = resource.authenticate(headers, formParams);
        Response response2 = resource.authenticate(headers, formParams);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> body1 = (Map<String, Object>) response1.getEntity();
        @SuppressWarnings("unchecked")
        Map<String, Object> body2 = (Map<String, Object>) response2.getEntity();

        String authReqId1 = (String) body1.get(CibaConstants.PARAM_AUTH_REQ_ID);
        String authReqId2 = (String) body2.get(CibaConstants.PARAM_AUTH_REQ_ID);

        assertThat(authReqId1).isNotEqualTo(authReqId2);
        assertThat(authReqId1).startsWith("urn:uuid:");
        assertThat(authReqId2).startsWith("urn:uuid:");
    }
}
