package org.apifocal.auth41.plugin.registration.approval;

import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.resource.RegistrationResourceProviderFactory;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationApprovalTaskTest {

    private KeycloakSessionFactory sessionFactory;
    private KeycloakSession session;
    private RegistrationStorageProvider storage;
    private RealmProvider realmProvider;
    private UserProvider userProvider;
    private RealmModel realm;
    private KeycloakContext context;
    private RegistrationApprovalTask task;

    @BeforeEach
    void setUp() throws Exception {
        sessionFactory = mock(KeycloakSessionFactory.class);
        session = mock(KeycloakSession.class);
        storage = mock(RegistrationStorageProvider.class);
        realmProvider = mock(RealmProvider.class);
        userProvider = mock(UserProvider.class);
        realm = mock(RealmModel.class);
        context = mock(KeycloakContext.class);

        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
        when(session.realms()).thenReturn(realmProvider);
        when(session.users()).thenReturn(userProvider);
        when(session.getContext()).thenReturn(context);
        when(realmProvider.getRealm("test-realm")).thenReturn(realm);
        when(realm.getId()).thenReturn("test-realm");
        when(realm.getName()).thenReturn("Test Realm");

        // Set up static config via reflection
        RegistrationConfig config = RegistrationConfig.withDefaults();
        Field configField = RegistrationResourceProviderFactory.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, config);

        task = new RegistrationApprovalTask(sessionFactory);
    }

    @Test
    void shouldProcessPendingRequestsWhenTaskRuns() {
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
        task.run(session);

        // Then
        verify(storage).getPendingRequests(any(Instant.class));
        verify(userProvider).addUser(realm, "test@example.com");
        verify(storage).updateRegistrationRequest(argThat(req ->
                req.getStatus() == RegistrationRequest.Status.APPROVED
        ));
    }

    @Test
    void shouldHandleNoPendingRequests() {
        // Given
        when(storage.getPendingRequests(any(Instant.class))).thenReturn(List.of());

        // When
        task.run(session);

        // Then
        verify(storage).getPendingRequests(any(Instant.class));
        verify(userProvider, never()).addUser(any(), anyString());
    }

    @Test
    void shouldHandleExceptionGracefully() {
        // Given
        when(storage.getPendingRequests(any(Instant.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        task.run(session);

        // Then - Should not throw exception, just log it
        verify(storage).getPendingRequests(any(Instant.class));
    }

    @Test
    void shouldHandleNullConfigGracefully() throws Exception {
        // Given - Set config to null
        Field configField = RegistrationResourceProviderFactory.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, null);

        // When
        task.run(session);

        // Then - Should not process, just log warning
        verify(storage, never()).getPendingRequests(any());
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
        task.run(session);

        // Then
        verify(userProvider, times(2)).addUser(eq(realm), anyString());
        verify(storage, times(2)).updateRegistrationRequest(any());
    }
}
