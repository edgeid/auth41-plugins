package org.apifocal.auth41.plugin.registration.resource;

import jakarta.ws.rs.core.Response;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InviteResourceTest {

    private KeycloakSession session;
    private RegistrationConfig config;
    private RegistrationStorageProvider storage;
    private RealmModel realm;
    private KeycloakContext context;
    private ClientConnection connection;
    private InviteResource resource;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        config = RegistrationConfig.withDefaults();
        storage = mock(RegistrationStorageProvider.class);
        realm = mock(RealmModel.class);
        context = mock(KeycloakContext.class);
        connection = mock(ClientConnection.class);

        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(context.getConnection()).thenReturn(connection);
        when(realm.getId()).thenReturn("test-realm");
        when(realm.getName()).thenReturn("Test Realm");
        when(connection.getRemoteAddr()).thenReturn("192.168.1.1");

        resource = new InviteResource(session, config);
    }

    @Test
    void shouldCreateInviteToken() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(0L);

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsKeys("invite_token", "expires_in");
        assertThat(body.get("invite_token")).isNotNull();
        assertThat(body.get("expires_in")).isEqualTo(300);

        verify(storage).createInviteToken(argThat(token ->
                token.getIpAddress().equals("192.168.1.1") &&
                token.getRealmId().equals("test-realm") &&
                !token.isUsed()
        ));
    }

    @Test
    void shouldReturnBadRequestWhenRealmIsMissing() {
        // Given
        when(context.getRealm()).thenReturn(null);

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_realm");
    }

    @Test
    void shouldReturnBadRequestWhenIpAddressIsMissing() {
        // Given
        when(context.getConnection()).thenReturn(null);

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("invalid_request");
    }

    @Test
    void shouldEnforceRateLimit() {
        // Given - 3 recent invites from this IP
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(3L);

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(429); // Too Many Requests
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("rate_limit_exceeded");
        assertThat(body.get("error_description")).asString().contains("Too many invite requests");

        verify(storage, never()).createInviteToken(any());
    }

    @Test
    void shouldAllowRequestWhenUnderRateLimit() {
        // Given - 2 recent invites (under limit of 3)
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(2L);

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        verify(storage).createInviteToken(any());
    }

    @Test
    void shouldHandleStorageException() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        Response response = resource.requestInviteToken();

        // Then
        assertThat(response.getStatus()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("server_error");
    }

    @Test
    void shouldUseConfiguredTtl() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(0L);

        // When
        Response response = resource.requestInviteToken();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body.get("expires_in")).isEqualTo(config.getInviteTtlSeconds());
    }

    @Test
    void shouldQueryRateLimitWithCorrectTimeWindow() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(0L);

        // When
        resource.requestInviteToken();

        // Then
        verify(storage).countRecentInvitesByIp(
                eq("192.168.1.1"),
                argThat(instant -> {
                    // Should be approximately config.getRateLimitWindowSeconds() ago
                    long secondsAgo = Instant.now().getEpochSecond() - instant.getEpochSecond();
                    return Math.abs(secondsAgo - config.getRateLimitWindowSeconds()) < 2;
                })
        );
    }

    @Test
    void shouldCreateTokenWithCorrectExpiry() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(0L);

        // When
        resource.requestInviteToken();

        // Then
        verify(storage).createInviteToken(argThat(token -> {
            long ttl = token.getExpiresAt().getEpochSecond() - token.getCreatedAt().getEpochSecond();
            return Math.abs(ttl - config.getInviteTtlSeconds()) < 2;
        }));
    }

    @Test
    void shouldGenerateUniqueTokenValues() {
        // Given
        when(storage.countRecentInvitesByIp(anyString(), any(Instant.class))).thenReturn(0L);

        // When
        Response response1 = resource.requestInviteToken();
        Response response2 = resource.requestInviteToken();

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> body1 = (Map<String, Object>) response1.getEntity();
        @SuppressWarnings("unchecked")
        Map<String, Object> body2 = (Map<String, Object>) response2.getEntity();

        assertThat(body1.get("invite_token")).isNotEqualTo(body2.get("invite_token"));
    }
}
