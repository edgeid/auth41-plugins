package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatusResourceTest {

    private KeycloakSession session;
    private RegistrationConfig config;
    private RegistrationStorageProvider storage;
    private RealmModel realm;
    private KeycloakContext context;
    private StatusResource resource;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        config = RegistrationConfig.withDefaults();
        storage = mock(RegistrationStorageProvider.class);
        realm = mock(RealmModel.class);
        context = mock(KeycloakContext.class);

        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getId()).thenReturn("test-realm");
        when(realm.getName()).thenReturn("Test Realm");

        resource = new StatusResource(session, config);
    }

    @Test
    void shouldReturnPendingStatus() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("pending");
        assertThat(body.get("polling_interval")).isEqualTo(5);
    }

    @Test
    void shouldReturnApprovedStatus() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.APPROVED)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .approvedAt(Instant.now())
                .userId("user-123")
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("approved");
        assertThat(body.get("user_id")).isEqualTo("user-123");
    }

    @Test
    void shouldReturnDeniedStatus() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.DENIED)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(403); // Forbidden
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("denied");
        assertThat(body.get("error")).isEqualTo("request_denied");
    }

    @Test
    void shouldReturnExpiredStatus() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.EXPIRED)
                .createdAt(Instant.now().minusSeconds(700))
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("expired");
        assertThat(body.get("error")).isEqualTo("expired_request");
    }

    @Test
    void shouldReturnErrorStatus() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.ERROR)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("error")).isEqualTo("processing_error");
    }

    @Test
    void shouldReturnNotFoundWhenRequestDoesNotExist() {
        // Given
        when(storage.getRegistrationRequest("nonexistent")).thenReturn(null);

        // When
        Response response = resource.getStatus(Map.of("request_id", "nonexistent"));

        // Then
        assertThat(response.getStatus()).isEqualTo(404);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("not_found");
    }

    @Test
    void shouldReturnBadRequestWhenRequestIdEmpty() {
        // When
        Response response = resource.getStatus(Map.of("request_id", ""));

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
    }

    @Test
    void shouldReturnBadRequestWhenRequestIdNull() {
        // When
        Response response = resource.getStatus(null);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
    }

    @Test
    void shouldUpdateStatusToExpiredWhenPendingAndExpired() {
        // Given - Pending request that has expired
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(700))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        // Verify that status was updated to EXPIRED
        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getRequestId().equals("test-request") &&
                req.getStatus() == RegistrationRequest.Status.EXPIRED
        ));
    }

    @Test
    void shouldNotUpdateStatusWhenAlreadyExpired() {
        // Given - Already expired request
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.EXPIRED)
                .createdAt(Instant.now().minusSeconds(700))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(400);

        // Verify that status was NOT updated (already expired)
        verify(storage, never()).updateRegistrationRequest(any());
    }

    @Test
    void shouldNotCheckExpiryForApprovedRequests() {
        // Given - Approved request that's technically past expiry
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.APPROVED)
                .createdAt(Instant.now().minusSeconds(700))
                .expiresAt(Instant.now().minusSeconds(1))
                .approvedAt(Instant.now().minusSeconds(100))
                .userId("user-123")
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then - Should still return approved status despite being past expiry
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("approved");
    }

    @Test
    void shouldHandleStorageException() {
        // Given
        when(storage.getRegistrationRequest("test-request"))
                .thenThrow(new RuntimeException("Database error"));

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("server_error");
    }

    @Test
    void shouldReturnBadRequestWhenRealmIsMissing() {
        // Given
        when(context.getRealm()).thenReturn(null);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_realm");
    }

    @Test
    void shouldIncludePollingIntervalInPendingResponse() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("polling_interval")).isEqualTo(config.getPollingIntervalSeconds());
    }

    @Test
    void shouldHandleApprovedRequestWithoutUserId() {
        // Given - Approved but userId is null (edge case)
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.APPROVED)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .approvedAt(Instant.now())
                .userId(null)
                .build();

        when(storage.getRegistrationRequest("test-request")).thenReturn(request);

        // When
        Response response = resource.getStatus(Map.of("request_id", "test-request"));

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("status")).isEqualTo("approved");
        assertThat(body).doesNotContainKey("user_id");
    }
}
