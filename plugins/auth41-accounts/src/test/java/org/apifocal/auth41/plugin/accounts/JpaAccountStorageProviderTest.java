package org.apifocal.auth41.plugin.accounts;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaAccountStorageProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private JpaConnectionProvider jpaConnectionProvider;

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<UserAccountEntity> entityQuery;

    @Mock
    private TypedQuery<Long> longQuery;

    private JpaAccountStorageProvider provider;

    @BeforeEach
    void setUp() {
        when(session.getProvider(JpaConnectionProvider.class)).thenReturn(jpaConnectionProvider);
        when(jpaConnectionProvider.getEntityManager()).thenReturn(em);
        provider = new JpaAccountStorageProvider(session);
    }

    @Test
    void testCreateAccount() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .name("John Doe")
            .homeProviderId("provider-a")
            .build();

        // Simulate account doesn't exist
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(null);

        provider.createAccount(account);

        verify(em).persist(any(UserAccountEntity.class));
        verify(em).flush();
    }

    @Test
    void testCreateAccountThrowsWhenExists() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .build();

        // Simulate account already exists
        UserAccountEntity existingEntity = new UserAccountEntity("user@example.com", "provider-a");
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(existingEntity);

        assertThatThrownBy(() -> provider.createAccount(account))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void testCreateAccountThrowsWhenNull() {
        assertThatThrownBy(() -> provider.createAccount(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void testGetAccount() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        entity.setEmail("user@example.com");
        entity.setName("John Doe");

        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(entity);

        UserAccount account = provider.getAccount("user@example.com");

        assertThat(account).isNotNull();
        assertThat(account.getUserIdentifier()).isEqualTo("user@example.com");
        assertThat(account.getEmail()).isEqualTo("user@example.com");
        assertThat(account.getName()).isEqualTo("John Doe");
        assertThat(account.getHomeProviderId()).isEqualTo("provider-a");
    }

    @Test
    void testGetAccountReturnsNullWhenNotFound() {
        when(em.find(UserAccountEntity.class, "unknown@example.com")).thenReturn(null);

        UserAccount account = provider.getAccount("unknown@example.com");

        assertThat(account).isNull();
    }

    @Test
    void testGetAccountReturnsNullWhenIdentifierNull() {
        UserAccount account = provider.getAccount(null);
        assertThat(account).isNull();
    }

    @Test
    void testGetAccountByEmail() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        entity.setEmail("user@example.com");

        when(em.createQuery(anyString(), eq(UserAccountEntity.class))).thenReturn(entityQuery);
        when(entityQuery.setParameter(anyString(), any())).thenReturn(entityQuery);
        when(entityQuery.getSingleResult()).thenReturn(entity);

        UserAccount account = provider.getAccountByEmail("user@example.com");

        assertThat(account).isNotNull();
        assertThat(account.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void testGetAccountByEmailReturnsNullWhenNotFound() {
        when(em.createQuery(anyString(), eq(UserAccountEntity.class))).thenReturn(entityQuery);
        when(entityQuery.setParameter(anyString(), any())).thenReturn(entityQuery);
        when(entityQuery.getSingleResult()).thenThrow(new NoResultException());

        UserAccount account = provider.getAccountByEmail("unknown@example.com");

        assertThat(account).isNull();
    }

    @Test
    void testUpdateAccount() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(entity);

        UserAccount updatedAccount = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("newemail@example.com")
            .name("Jane Doe")
            .homeProviderId("provider-b")
            .build();

        provider.updateAccount(updatedAccount);

        verify(em).merge(entity);
        verify(em).flush();
        assertThat(entity.getEmail()).isEqualTo("newemail@example.com");
        assertThat(entity.getName()).isEqualTo("Jane Doe");
        assertThat(entity.getHomeProviderId()).isEqualTo("provider-b");
    }

    @Test
    void testUpdateAccountThrowsWhenNotFound() {
        when(em.find(UserAccountEntity.class, "unknown@example.com")).thenReturn(null);

        UserAccount account = UserAccount.builder()
            .userIdentifier("unknown@example.com")
            .homeProviderId("provider-a")
            .build();

        assertThatThrownBy(() -> provider.updateAccount(account))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testDeleteAccount() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(entity);

        boolean deleted = provider.deleteAccount("user@example.com");

        assertThat(deleted).isTrue();
        verify(em).remove(entity);
        verify(em).flush();
    }

    @Test
    void testDeleteAccountReturnsFalseWhenNotFound() {
        when(em.find(UserAccountEntity.class, "unknown@example.com")).thenReturn(null);

        boolean deleted = provider.deleteAccount("unknown@example.com");

        assertThat(deleted).isFalse();
        verify(em, never()).remove(any());
    }

    @Test
    void testFindAccountsByProvider() {
        UserAccountEntity entity1 = new UserAccountEntity("user1@example.com", "provider-a");
        UserAccountEntity entity2 = new UserAccountEntity("user2@example.com", "provider-a");

        when(em.createQuery(anyString(), eq(UserAccountEntity.class))).thenReturn(entityQuery);
        when(entityQuery.setParameter(anyString(), any())).thenReturn(entityQuery);
        when(entityQuery.getResultList()).thenReturn(List.of(entity1, entity2));

        List<UserAccount> accounts = provider.findAccountsByProvider("provider-a");

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(UserAccount::getUserIdentifier)
            .containsExactly("user1@example.com", "user2@example.com");
    }

    @Test
    void testFindProvidersByUser() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(entity);

        Set<String> providers = provider.findProvidersByUser("user@example.com");

        assertThat(providers).containsExactly("provider-a");
    }

    @Test
    void testFindProvidersByUserReturnsEmptyWhenNotFound() {
        when(em.find(UserAccountEntity.class, "unknown@example.com")).thenReturn(null);

        Set<String> providers = provider.findProvidersByUser("unknown@example.com");

        assertThat(providers).isEmpty();
    }

    @Test
    void testAccountExists() {
        UserAccountEntity entity = new UserAccountEntity("user@example.com", "provider-a");
        when(em.find(UserAccountEntity.class, "user@example.com")).thenReturn(entity);

        boolean exists = provider.accountExists("user@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void testAccountNotExists() {
        when(em.find(UserAccountEntity.class, "unknown@example.com")).thenReturn(null);

        boolean exists = provider.accountExists("unknown@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    void testGetAccountCount() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(42L);

        long count = provider.getAccountCount();

        assertThat(count).isEqualTo(42);
    }

    @Test
    void testGetAccountCountByProvider() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(10L);

        long count = provider.getAccountCountByProvider("provider-a");

        assertThat(count).isEqualTo(10);
    }
}
