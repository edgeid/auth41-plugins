package org.apifocal.auth41.plugin.registration.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.apifocal.auth41.plugin.registration.entity.InviteTokenEntity;
import org.apifocal.auth41.plugin.registration.entity.RegistrationRequestEntity;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaRegistrationStorageProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private JpaConnectionProvider jpaConnectionProvider;

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Long> countQuery;

    @Mock
    private TypedQuery<RegistrationRequestEntity> requestQuery;

    private JpaRegistrationStorageProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(session.getProvider(JpaConnectionProvider.class)).thenReturn(jpaConnectionProvider);
        lenient().when(jpaConnectionProvider.getEntityManager()).thenReturn(em);
        provider = new JpaRegistrationStorageProvider(session);
    }

    // Invite Token Tests

    @Test
    void shouldCreateInviteToken() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        provider.createInviteToken(token);

        ArgumentCaptor<InviteTokenEntity> captor = ArgumentCaptor.forClass(InviteTokenEntity.class);
        verify(em).persist(captor.capture());
        verify(em).flush();

        InviteTokenEntity persistedEntity = captor.getValue();
        assertThat(persistedEntity.getInviteToken()).isEqualTo("test-token");
        assertThat(persistedEntity.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(persistedEntity.getRealmId()).isEqualTo("test-realm");
    }

    @Test
    void shouldThrowExceptionWhenCreateInviteTokenWithNull() {
        assertThatThrownBy(() -> provider.createInviteToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null");

        verify(em, never()).persist(any());
    }

    @Test
    void shouldThrowExceptionWhenInviteTokenAlreadyExists() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        // Simulate constraint violation
        PersistenceException constraintException = new PersistenceException("duplicate key");
        doThrow(constraintException).when(em).persist(any(InviteTokenEntity.class));

        assertThatThrownBy(() -> provider.createInviteToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldGetInviteToken() {
        InviteTokenEntity entity = new InviteTokenEntity(
                "test-token",
                "192.168.1.1",
                "test-realm",
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );

        when(em.find(InviteTokenEntity.class, "test-token")).thenReturn(entity);

        InviteToken token = provider.getInviteToken("test-token");

        assertThat(token).isNotNull();
        assertThat(token.getInviteToken()).isEqualTo("test-token");
        assertThat(token.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(token.getRealmId()).isEqualTo("test-realm");
    }

    @Test
    void shouldReturnNullWhenInviteTokenNotFound() {
        when(em.find(InviteTokenEntity.class, "nonexistent")).thenReturn(null);

        InviteToken token = provider.getInviteToken("nonexistent");

        assertThat(token).isNull();
    }

    @Test
    void shouldReturnNullWhenGetInviteTokenWithNull() {
        InviteToken token = provider.getInviteToken(null);

        assertThat(token).isNull();
        verify(em, never()).find(any(), any());
    }

    @Test
    void shouldCountRecentInvitesByIp() {
        Instant since = Instant.now().minus(5, ChronoUnit.MINUTES);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(eq("ipAddress"), anyString())).thenReturn(countQuery);
        when(countQuery.setParameter(eq("since"), any(Instant.class))).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(3L);

        long count = provider.countRecentInvitesByIp("192.168.1.1", since);

        assertThat(count).isEqualTo(3L);
        verify(countQuery).setParameter("ipAddress", "192.168.1.1");
        verify(countQuery).setParameter("since", since);
    }

    @Test
    void shouldReturnZeroWhenCountRecentInvitesByIpWithNull() {
        long count1 = provider.countRecentInvitesByIp(null, Instant.now());
        long count2 = provider.countRecentInvitesByIp("192.168.1.1", null);

        assertThat(count1).isZero();
        assertThat(count2).isZero();
        verify(em, never()).createQuery(anyString(), any());
    }

    @Test
    void shouldMarkInviteTokenUsed() {
        Instant beforeMark = Instant.now();

        InviteTokenEntity entity = new InviteTokenEntity(
                "test-token",
                "192.168.1.1",
                "test-realm",
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );

        when(em.find(InviteTokenEntity.class, "test-token")).thenReturn(entity);

        provider.markInviteTokenUsed("test-token");

        Instant afterMark = Instant.now();

        assertThat(entity.isUsed()).isTrue();
        assertThat(entity.getUsedAt()).isBetween(beforeMark, afterMark);
        verify(em).merge(entity);
        verify(em).flush();
    }

    @Test
    void shouldThrowExceptionWhenMarkInviteTokenUsedWithNull() {
        assertThatThrownBy(() -> provider.markInviteTokenUsed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");

        verify(em, never()).merge(any());
    }

    @Test
    void shouldThrowExceptionWhenMarkInviteTokenUsedNotFound() {
        when(em.find(InviteTokenEntity.class, "nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> provider.markInviteTokenUsed("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(em, never()).merge(any());
    }

    @Test
    void shouldThrowExceptionWhenMarkInviteTokenAlreadyUsed() {
        InviteTokenEntity entity = new InviteTokenEntity(
                "test-token",
                "192.168.1.1",
                "test-realm",
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );
        entity.setUsed(true);
        entity.setUsedAt(Instant.now());

        when(em.find(InviteTokenEntity.class, "test-token")).thenReturn(entity);

        assertThatThrownBy(() -> provider.markInviteTokenUsed("test-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already used");

        verify(em, never()).merge(any());
    }

    // Registration Request Tests

    @Test
    void shouldCreateRegistrationRequest() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("firstName", "John");

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .attributes(attrs)
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        provider.createRegistrationRequest(request);

        ArgumentCaptor<RegistrationRequestEntity> captor = ArgumentCaptor.forClass(RegistrationRequestEntity.class);
        verify(em).persist(captor.capture());
        verify(em).flush();

        RegistrationRequestEntity persistedEntity = captor.getValue();
        assertThat(persistedEntity.getRequestId()).isEqualTo("req-123");
        assertThat(persistedEntity.getEmail()).isEqualTo("john@example.com");
        assertThat(persistedEntity.getRealmId()).isEqualTo("test-realm");
        assertThat(persistedEntity.getStatus()).isEqualTo(RegistrationRequest.Status.PENDING);
    }

    @Test
    void shouldThrowExceptionWhenCreateRegistrationRequestWithNull() {
        assertThatThrownBy(() -> provider.createRegistrationRequest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request cannot be null");

        verify(em, never()).persist(any());
    }

    @Test
    void shouldThrowExceptionWhenRegistrationRequestAlreadyExists() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        PersistenceException constraintException = new PersistenceException("unique constraint");
        doThrow(constraintException).when(em).persist(any(RegistrationRequestEntity.class));

        assertThatThrownBy(() -> provider.createRegistrationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldGetRegistrationRequest() {
        RegistrationRequestEntity entity = new RegistrationRequestEntity(
                "req-123",
                "john@example.com",
                "test-realm",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(em.find(RegistrationRequestEntity.class, "req-123")).thenReturn(entity);

        RegistrationRequest request = provider.getRegistrationRequest("req-123");

        assertThat(request).isNotNull();
        assertThat(request.getRequestId()).isEqualTo("req-123");
        assertThat(request.getEmail()).isEqualTo("john@example.com");
        assertThat(request.getStatus()).isEqualTo(RegistrationRequest.Status.PENDING);
    }

    @Test
    void shouldReturnNullWhenRegistrationRequestNotFound() {
        when(em.find(RegistrationRequestEntity.class, "nonexistent")).thenReturn(null);

        RegistrationRequest request = provider.getRegistrationRequest("nonexistent");

        assertThat(request).isNull();
    }

    @Test
    void shouldReturnNullWhenGetRegistrationRequestWithNull() {
        RegistrationRequest request = provider.getRegistrationRequest(null);

        assertThat(request).isNull();
        verify(em, never()).find(any(), any());
    }

    @Test
    void shouldUpdateRegistrationRequest() {
        RegistrationRequestEntity entity = new RegistrationRequestEntity(
                "req-123",
                "john@example.com",
                "test-realm",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(em.find(RegistrationRequestEntity.class, "req-123")).thenReturn(entity);

        RegistrationRequest updated = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.APPROVED)
                .approvedAt(Instant.now())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .userId("user-456")
                .build();

        provider.updateRegistrationRequest(updated);

        assertThat(entity.getStatus()).isEqualTo(RegistrationRequest.Status.APPROVED);
        assertThat(entity.getUserId()).isEqualTo("user-456");
        assertThat(entity.getApprovedAt()).isNotNull();
        verify(em).merge(entity);
        verify(em).flush();
    }

    @Test
    void shouldThrowExceptionWhenUpdateRegistrationRequestWithNull() {
        assertThatThrownBy(() -> provider.updateRegistrationRequest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request cannot be null");

        verify(em, never()).merge(any());
    }

    @Test
    void shouldThrowExceptionWhenUpdateRegistrationRequestNotFound() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("nonexistent")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.APPROVED)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        when(em.find(RegistrationRequestEntity.class, "nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> provider.updateRegistrationRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(em, never()).merge(any());
    }

    @Test
    void shouldGetPendingRequests() {
        Instant olderThan = Instant.now().minus(30, ChronoUnit.SECONDS);

        RegistrationRequestEntity entity1 = new RegistrationRequestEntity(
                "req-1",
                "user1@example.com",
                "test-realm",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        RegistrationRequestEntity entity2 = new RegistrationRequestEntity(
                "req-2",
                "user2@example.com",
                "test-realm",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(em.createQuery(anyString(), eq(RegistrationRequestEntity.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("status"), any(RegistrationRequest.Status.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("olderThan"), any(Instant.class))).thenReturn(requestQuery);
        when(requestQuery.getResultList()).thenReturn(Arrays.asList(entity1, entity2));

        List<RegistrationRequest> requests = provider.getPendingRequests(olderThan);

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getRequestId()).isEqualTo("req-1");
        assertThat(requests.get(1).getRequestId()).isEqualTo("req-2");

        verify(requestQuery).setParameter("status", RegistrationRequest.Status.PENDING);
        verify(requestQuery).setParameter("olderThan", olderThan);
    }

    @Test
    void shouldUseCurrentTimeWhenGetPendingRequestsWithNull() {
        Instant beforeCall = Instant.now();

        when(em.createQuery(anyString(), eq(RegistrationRequestEntity.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("status"), any(RegistrationRequest.Status.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("olderThan"), any(Instant.class))).thenReturn(requestQuery);
        when(requestQuery.getResultList()).thenReturn(Arrays.asList());

        provider.getPendingRequests(null);

        Instant afterCall = Instant.now();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(requestQuery).setParameter(eq("olderThan"), instantCaptor.capture());

        Instant capturedInstant = instantCaptor.getValue();
        assertThat(capturedInstant).isBetween(beforeCall, afterCall);
    }

    @Test
    void shouldReturnEmptyListWhenNoPendingRequests() {
        when(em.createQuery(anyString(), eq(RegistrationRequestEntity.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("status"), any(RegistrationRequest.Status.class))).thenReturn(requestQuery);
        when(requestQuery.setParameter(eq("olderThan"), any(Instant.class))).thenReturn(requestQuery);
        when(requestQuery.getResultList()).thenReturn(Arrays.asList());

        List<RegistrationRequest> requests = provider.getPendingRequests(Instant.now());

        assertThat(requests).isEmpty();
    }

    @Test
    void shouldDeleteExpiredInviteTokens() {
        Instant expiredBefore = Instant.now();
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM InviteTokenEntity i WHERE i.expiresAt < :expiredBefore"))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(5);

        int deleted = provider.deleteExpiredInviteTokens(expiredBefore);

        assertThat(deleted).isEqualTo(5);
        verify(query).setParameter("expiredBefore", expiredBefore);
        verify(query).executeUpdate();
    }

    @Test
    void shouldDeleteExpiredRegistrationRequests() {
        Instant expiredBefore = Instant.now();
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM RegistrationRequestEntity r WHERE r.expiresAt < :expiredBefore"))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(3);

        int deleted = provider.deleteExpiredRegistrationRequests(expiredBefore);

        assertThat(deleted).isEqualTo(3);
        verify(query).setParameter("expiredBefore", expiredBefore);
        verify(query).executeUpdate();
    }

    @Test
    void shouldReturnZeroWhenNoExpiredTokensToDelete() {
        Instant expiredBefore = Instant.now();
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM InviteTokenEntity i WHERE i.expiresAt < :expiredBefore"))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        int deleted = provider.deleteExpiredInviteTokens(expiredBefore);

        assertThat(deleted).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenNoExpiredRequestsToDelete() {
        Instant expiredBefore = Instant.now();
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM RegistrationRequestEntity r WHERE r.expiresAt < :expiredBefore"))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        int deleted = provider.deleteExpiredRegistrationRequests(expiredBefore);

        assertThat(deleted).isEqualTo(0);
    }

    // Test-only methods for clearing data

    @Test
    void deleteAllInviteTokens_shouldDeleteAllTokens() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM InviteTokenEntity")).thenReturn(query);
        when(query.executeUpdate()).thenReturn(10);

        // When
        int deleted = provider.deleteAllInviteTokens();

        // Then
        assertThat(deleted).isEqualTo(10);
        verify(em).createQuery("DELETE FROM InviteTokenEntity");
        verify(query).executeUpdate();
    }

    @Test
    void deleteAllInviteTokens_shouldReturnZeroWhenNoTokensExist() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM InviteTokenEntity")).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        // When
        int deleted = provider.deleteAllInviteTokens();

        // Then
        assertThat(deleted).isEqualTo(0);
    }

    @Test
    void deleteAllInviteTokens_shouldPropagateExceptionOnFailure() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM InviteTokenEntity")).thenReturn(query);
        when(query.executeUpdate()).thenThrow(new PersistenceException("Database error"));

        // When/Then
        assertThatThrownBy(() -> provider.deleteAllInviteTokens())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("Database error");
    }

    @Test
    void deleteAllRegistrationRequests_shouldDeleteAllRequests() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM RegistrationRequestEntity")).thenReturn(query);
        when(query.executeUpdate()).thenReturn(25);

        // When
        int deleted = provider.deleteAllRegistrationRequests();

        // Then
        assertThat(deleted).isEqualTo(25);
        verify(em).createQuery("DELETE FROM RegistrationRequestEntity");
        verify(query).executeUpdate();
    }

    @Test
    void deleteAllRegistrationRequests_shouldReturnZeroWhenNoRequestsExist() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM RegistrationRequestEntity")).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        // When
        int deleted = provider.deleteAllRegistrationRequests();

        // Then
        assertThat(deleted).isEqualTo(0);
    }

    @Test
    void deleteAllRegistrationRequests_shouldPropagateExceptionOnFailure() {
        // Given
        Query query = mock(Query.class);
        when(em.createQuery("DELETE FROM RegistrationRequestEntity")).thenReturn(query);
        when(query.executeUpdate()).thenThrow(new PersistenceException("Connection timeout"));

        // When/Then
        assertThatThrownBy(() -> provider.deleteAllRegistrationRequests())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("Connection timeout");
    }
}
