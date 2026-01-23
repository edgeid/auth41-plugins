package org.apifocal.auth41.plugin.registration.task;

import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationCleanupTaskTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RegistrationStorageProvider storage;

    private RegistrationCleanupTask task;

    @BeforeEach
    void setUp() {
        task = new RegistrationCleanupTask();
        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(storage);
    }

    @Test
    void shouldDeleteExpiredInviteTokens() {
        when(storage.deleteExpiredInviteTokens(any(Instant.class))).thenReturn(5);
        when(storage.deleteExpiredRegistrationRequests(any(Instant.class))).thenReturn(0);

        task.run(session);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(storage).deleteExpiredInviteTokens(captor.capture());

        // Verify that the instant is approximately now (within 1 second)
        Instant now = Instant.now();
        assertThat(captor.getValue()).isBetween(
                now.minusSeconds(1),
                now.plusSeconds(1)
        );
    }

    @Test
    void shouldDeleteExpiredRegistrationRequests() {
        when(storage.deleteExpiredInviteTokens(any(Instant.class))).thenReturn(0);
        when(storage.deleteExpiredRegistrationRequests(any(Instant.class))).thenReturn(3);

        task.run(session);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(storage).deleteExpiredRegistrationRequests(captor.capture());

        // Verify that the instant is approximately now (within 1 second)
        Instant now = Instant.now();
        assertThat(captor.getValue()).isBetween(
                now.minusSeconds(1),
                now.plusSeconds(1)
        );
    }

    @Test
    void shouldHandleNoExpiredRecords() {
        when(storage.deleteExpiredInviteTokens(any(Instant.class))).thenReturn(0);
        when(storage.deleteExpiredRegistrationRequests(any(Instant.class))).thenReturn(0);

        // Should not throw exception
        task.run(session);

        verify(storage).deleteExpiredInviteTokens(any(Instant.class));
        verify(storage).deleteExpiredRegistrationRequests(any(Instant.class));
    }

    @Test
    void shouldHandleStorageException() {
        when(storage.deleteExpiredInviteTokens(any(Instant.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Should not throw exception - errors are logged
        task.run(session);

        verify(storage).deleteExpiredInviteTokens(any(Instant.class));
        // Should not proceed to delete requests after exception
        verify(storage, never()).deleteExpiredRegistrationRequests(any(Instant.class));
    }

    @Test
    void shouldHandleNullStorageProvider() {
        when(session.getProvider(RegistrationStorageProvider.class)).thenReturn(null);

        // Should not throw exception
        task.run(session);

        // Verify no deletions were attempted
        verify(storage, never()).deleteExpiredInviteTokens(any(Instant.class));
        verify(storage, never()).deleteExpiredRegistrationRequests(any(Instant.class));
    }

    @Test
    void shouldDeleteBothTypesOfRecords() {
        when(storage.deleteExpiredInviteTokens(any(Instant.class))).thenReturn(10);
        when(storage.deleteExpiredRegistrationRequests(any(Instant.class))).thenReturn(7);

        task.run(session);

        verify(storage).deleteExpiredInviteTokens(any(Instant.class));
        verify(storage).deleteExpiredRegistrationRequests(any(Instant.class));
    }
}
