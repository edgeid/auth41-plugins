package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestResourceTest {

    private KeycloakSession session;
    private RegistrationConfig config;
    private RegistrationStorageProvider storage;
    private RealmModel realm;
    private KeycloakContext context;
    private UserProvider userProvider;
    private RequestResource resource;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        config = RegistrationConfig.withDefaults();
        storage = mock(RegistrationStorageProvider.class);
        realm = mock(RealmModel.class);
        context = mock(KeycloakContext.class);
        userProvider = mock(UserProvider.class);

        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
        when(session.getContext()).thenReturn(context);
        when(session.users()).thenReturn(userProvider);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getId()).thenReturn("test-realm");
        when(realm.getName()).thenReturn("Test Realm");

        resource = new RequestResource(session, config);
    }

    @Test
    void shouldCreateRegistrationRequest() {
        // Given
        String inviteTokenValue = "valid-token";
        InviteToken inviteToken = InviteToken.builder()
                .inviteToken(inviteTokenValue)
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken(inviteTokenValue)).thenReturn(inviteToken);
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", inviteTokenValue);
        request.put("email", "test@example.com");
        request.put("attributes", Map.of("firstName", "John", "lastName", "Doe"));

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(201); // Created
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsKeys("request_id", "status", "polling_interval");
        assertThat(body.get("status")).isEqualTo("pending");
        assertThat(body.get("polling_interval")).isEqualTo(5);

        verify(storage).createRegistrationRequest(argThat(req ->
                req.getEmail().equals("test@example.com") &&
                req.getRealmId().equals("test-realm") &&
                req.getStatus() == RegistrationRequest.Status.PENDING &&
                req.getAttributes().containsKey("firstName")
        ));
        verify(storage).markInviteTokenUsed(inviteTokenValue);
    }

    @Test
    void shouldReturnBadRequestWhenInviteTokenMissing() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        assertThat(body.get("error_description")).asString().contains("invite_token");
    }

    @Test
    void shouldReturnBadRequestWhenEmailMissing() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
        assertThat(body.get("error_description")).asString().contains("email");
    }

    @Test
    void shouldReturnBadRequestWhenEmailInvalid() {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "not-an-email");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
    }

    @Test
    void shouldReturnBadRequestWhenInviteTokenNotFound() {
        // Given
        when(storage.getInviteToken("invalid-token")).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "invalid-token");
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_token");
    }

    @Test
    void shouldReturnBadRequestWhenInviteTokenAlreadyUsed() {
        // Given
        InviteToken usedToken = InviteToken.builder()
                .inviteToken("used-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(true)
                .usedAt(Instant.now())
                .build();

        when(storage.getInviteToken("used-token")).thenReturn(usedToken);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "used-token");
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_token");
        assertThat(body.get("error_description")).asString().contains("already used");
    }

    @Test
    void shouldReturnBadRequestWhenInviteTokenExpired() {
        // Given
        InviteToken expiredToken = InviteToken.builder()
                .inviteToken("expired-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(1))
                .used(false)
                .build();

        when(storage.getInviteToken("expired-token")).thenReturn(expiredToken);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "expired-token");
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("expired_token");
    }

    @Test
    void shouldReturnBadRequestWhenEmailAlreadyExists() {
        // Given
        InviteToken validToken = InviteToken.builder()
                .inviteToken("valid-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken("valid-token")).thenReturn(validToken);
        when(userProvider.getUserByEmail(realm, "existing@example.com")).thenReturn(mock(UserModel.class));

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "existing@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("email_exists");
    }

    @Test
    void shouldHandleEmptyAttributes() {
        // Given
        InviteToken validToken = InviteToken.builder()
                .inviteToken("valid-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken("valid-token")).thenReturn(validToken);
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "test@example.com");
        // No attributes provided

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(201);
        verify(storage).createRegistrationRequest(argThat(req ->
                req.getAttributes() != null && req.getAttributes().isEmpty()
        ));
    }

    @Test
    void shouldUseConfiguredTtl() {
        // Given
        InviteToken validToken = InviteToken.builder()
                .inviteToken("valid-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken("valid-token")).thenReturn(validToken);
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "test@example.com");

        // When
        resource.submitRegistrationRequest(request);

        // Then
        verify(storage).createRegistrationRequest(argThat(req -> {
            long ttl = req.getExpiresAt().getEpochSecond() - req.getCreatedAt().getEpochSecond();
            return Math.abs(ttl - config.getRequestTtlSeconds()) < 2;
        }));
    }

    @Test
    void shouldHandleStorageException() {
        // Given
        InviteToken validToken = InviteToken.builder()
                .inviteToken("valid-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken("valid-token")).thenReturn(validToken);
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);
        doThrow(new RuntimeException("Database error")).when(storage).createRegistrationRequest(any());

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("server_error");
    }

    @Test
    void shouldValidateEmailFormat() {
        // Test various invalid email formats
        String[] invalidEmails = {
                "not-an-email",
                "@example.com",
                "test@",
                "test@@example.com",
                "test example@test.com"
        };

        for (String invalidEmail : invalidEmails) {
            Map<String, Object> request = new HashMap<>();
            request.put("invite_token", "valid-token");
            request.put("email", invalidEmail);

            Response response = resource.submitRegistrationRequest(request);

            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void shouldAcceptValidEmailFormats() {
        // Given
        InviteToken validToken = InviteToken.builder()
                .inviteToken("valid-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();

        when(storage.getInviteToken("valid-token")).thenReturn(validToken);
        when(userProvider.getUserByEmail(eq(realm), anyString())).thenReturn(null);

        String[] validEmails = {
                "test@example.com",
                "user.name@example.com",
                "user+tag@example.co.uk",
                "test_123@test-domain.com"
        };

        for (String validEmail : validEmails) {
            Map<String, Object> request = new HashMap<>();
            request.put("invite_token", "valid-token");
            request.put("email", validEmail);
            request.put("attributes", new HashMap<>());

            Response response = resource.submitRegistrationRequest(request);

            assertThat(response.getStatus()).isEqualTo(201);
        }
    }

    @Test
    void shouldReturnBadRequestWhenRealmIsMissing() {
        // Given
        when(context.getRealm()).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("invite_token", "valid-token");
        request.put("email", "test@example.com");

        // When
        Response response = resource.submitRegistrationRequest(request);

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_realm");
    }
}
