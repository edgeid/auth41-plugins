package org.apifocal.auth41.plugin.registration.approval;

import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationApprovalProcessorTest {

    private KeycloakSession session;
    private RegistrationConfig config;
    private RegistrationStorageProvider storage;
    private RealmProvider realmProvider;
    private UserProvider userProvider;
    private RealmModel realm;
    private RegistrationApprovalProcessor processor;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        config = RegistrationConfig.withDefaults();
        storage = mock(RegistrationStorageProvider.class);
        realmProvider = mock(RealmProvider.class);
        userProvider = mock(UserProvider.class);
        realm = mock(RealmModel.class);

        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
        when(session.realms()).thenReturn(realmProvider);
        when(session.users()).thenReturn(userProvider);
        when(realmProvider.getRealm("test-realm")).thenReturn(realm);
        when(realm.getId()).thenReturn("test-realm");
        when(realm.getName()).thenReturn("Test Realm");

        processor = new RegistrationApprovalProcessor(session, config);
    }

    @Test
    void shouldProcessPendingRequest() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(Map.of("firstName", "John", "lastName", "Doe"))
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("test@example.com");
        when(userProvider.addUser(realm, "test@example.com")).thenReturn(user);

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(1);

        verify(userProvider).addUser(realm, "test@example.com");
        verify(user).setEmail("test@example.com");
        verify(user).setEmailVerified(false);
        verify(user).setEnabled(true);
        verify(user).setFirstName("John");
        verify(user).setLastName("Doe");

        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getRequestId().equals("test-request") &&
                req.getStatus() == RegistrationRequest.Status.APPROVED &&
                req.getUserId().equals("user-123") &&
                req.getApprovedAt() != null
        ));
    }

    @Test
    void shouldReturnZeroWhenNoPendingRequests() {
        // Given
        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of());

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(0);
        verify(userProvider, never()).addUser(any(), anyString());
    }

    @Test
    void shouldProcessMultipleRequests() {
        // Given
        RegistrationRequest request1 = RegistrationRequest.builder()
                .requestId("request-1")
                .email("user1@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        RegistrationRequest request2 = RegistrationRequest.builder()
                .requestId("request-2")
                .email("user2@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request1, request2));
        when(userProvider.getUserByEmail(eq(realm), anyString())).thenReturn(null);

        UserModel user1 = mock(UserModel.class);
        when(user1.getId()).thenReturn("user-1");
        when(user1.getUsername()).thenReturn("user1@example.com");
        when(userProvider.addUser(realm, "user1@example.com")).thenReturn(user1);

        UserModel user2 = mock(UserModel.class);
        when(user2.getId()).thenReturn("user-2");
        when(user2.getUsername()).thenReturn("user2@example.com");
        when(userProvider.addUser(realm, "user2@example.com")).thenReturn(user2);

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(2);
        verify(userProvider, times(2)).addUser(eq(realm), anyString());
        verify(storage, times(2)).updateRegistrationRequest(any());
    }

    @Test
    void shouldSetCustomAttributes() {
        // Given
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("firstName", "Jane");
        attributes.put("lastName", "Smith");
        attributes.put("phoneNumber", "+1234567890");
        attributes.put("company", "Acme Corp");

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(attributes)
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("test@example.com");
        when(userProvider.addUser(realm, "test@example.com")).thenReturn(user);

        // When
        processor.processPendingRequests();

        // Then
        verify(user).setFirstName("Jane");
        verify(user).setLastName("Smith");
        verify(user).setSingleAttribute("phoneNumber", "+1234567890");
        verify(user).setSingleAttribute("company", "Acme Corp");
    }

    @Test
    void shouldMarkAsErrorWhenUserAlreadyExists() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("existing@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "existing@example.com")).thenReturn(mock(UserModel.class));

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(1);
        verify(userProvider, never()).addUser(any(), anyString());
        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getRequestId().equals("test-request") &&
                req.getStatus() == RegistrationRequest.Status.ERROR
        ));
    }

    @Test
    void shouldMarkAsErrorWhenRealmNotFound() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("nonexistent-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(realmProvider.getRealm("nonexistent-realm")).thenReturn(null);

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(1);
        verify(userProvider, never()).addUser(any(), anyString());
        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getRequestId().equals("test-request") &&
                req.getStatus() == RegistrationRequest.Status.ERROR
        ));
    }

    @Test
    void shouldContinueProcessingAfterError() {
        // Given
        RegistrationRequest failingRequest = RegistrationRequest.builder()
                .requestId("failing-request")
                .email("fail@example.com")
                .realmId("nonexistent-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        RegistrationRequest successRequest = RegistrationRequest.builder()
                .requestId("success-request")
                .email("success@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(failingRequest, successRequest));
        when(realmProvider.getRealm("nonexistent-realm")).thenReturn(null);
        when(userProvider.getUserByEmail(realm, "success@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("success@example.com");
        when(userProvider.addUser(realm, "success@example.com")).thenReturn(user);

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(2);
        verify(storage, times(2)).updateRegistrationRequest(any());
    }

    @Test
    void shouldQueryWithCorrectThreshold() {
        // Given
        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of());

        // When
        processor.processPendingRequests();

        // Then
        verify(storage).getPendingRequests(argThat(instant -> {
            // Should be approximately config.getApprovalDelaySeconds() ago
            long secondsAgo = Instant.now().getEpochSecond() - instant.getEpochSecond();
            return Math.abs(secondsAgo - config.getApprovalDelaySeconds()) < 2;
        }));
    }

    @Test
    void shouldReturnZeroWhenStorageProviderNotAvailable() {
        // Given
        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(null);

        // When
        int processed = processor.processPendingRequests();

        // Then
        assertThat(processed).isEqualTo(0);
    }

    @Test
    void shouldHandleNullAttributeValues() {
        // Given
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("firstName", "John");
        attributes.put("customField", null);

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(attributes)
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("test@example.com");
        when(userProvider.addUser(realm, "test@example.com")).thenReturn(user);

        // When
        processor.processPendingRequests();

        // Then
        verify(user).setFirstName("John");
        verify(user, never()).setSingleAttribute(eq("customField"), any());
    }

    @Test
    void shouldHandleEmptyAttributes() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("test@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "test@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("test@example.com");
        when(userProvider.addUser(realm, "test@example.com")).thenReturn(user);

        // When
        processor.processPendingRequests();

        // Then
        verify(user).setEmail("test@example.com");
        verify(user).setEmailVerified(false);
        verify(user).setEnabled(true);
        verify(user, never()).setFirstName(any());
        verify(user, never()).setLastName(any());
        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getStatus() == RegistrationRequest.Status.APPROVED
        ));
    }

    @Test
    void shouldUseEmailAsUsername() {
        // Given
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("test-request")
                .email("testuser@example.com")
                .realmId("test-realm")
                .attributes(new HashMap<>())
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of(request));
        when(userProvider.getUserByEmail(realm, "testuser@example.com")).thenReturn(null);

        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("testuser@example.com");
        when(userProvider.addUser(realm, "testuser@example.com")).thenReturn(user);

        // When
        processor.processPendingRequests();

        // Then
        verify(userProvider).addUser(realm, "testuser@example.com");
        verify(user).setEmail("testuser@example.com");
    }
}
