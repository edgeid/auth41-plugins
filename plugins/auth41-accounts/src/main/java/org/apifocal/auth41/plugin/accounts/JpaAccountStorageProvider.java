package org.apifocal.auth41.plugin.accounts;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA-based implementation of AccountStorageProvider.
 *
 * <p>Uses Keycloak's existing JPA connection to store user account records
 * in the same database as Keycloak.
 */
public class JpaAccountStorageProvider implements AccountStorageProvider {

    private static final Logger logger = Logger.getLogger(JpaAccountStorageProvider.class);

    private final KeycloakSession session;
    private final EntityManager em;

    public JpaAccountStorageProvider(KeycloakSession session) {
        this.session = session;
        this.em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    @Override
    public void createAccount(UserAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        try {
            UserAccountEntity entity = UserAccountEntity.fromUserAccount(account);
            em.persist(entity);
            em.flush();

            logger.debugf("Created account: %s at provider %s",
                account.getUserIdentifier(), account.getHomeProviderId());
        } catch (jakarta.persistence.PersistenceException e) {
            // Check if this is a constraint violation (duplicate key)
            Throwable current = e;
            while (current != null) {
                String message = current.getMessage();
                String className = current.getClass().getName();

                // Check exception class name
                if (className.contains("ConstraintViolationException")) {
                    throw new IllegalArgumentException(
                        "Account already exists: " + account.getUserIdentifier(), e
                    );
                }

                // Check message content
                if (message != null) {
                    String lowerMessage = message.toLowerCase();
                    if (lowerMessage.contains("constraint") ||
                        lowerMessage.contains("unique") ||
                        lowerMessage.contains("duplicate") ||
                        lowerMessage.contains("primary key")) {
                        throw new IllegalArgumentException(
                            "Account already exists: " + account.getUserIdentifier(), e
                        );
                    }
                }

                current = current.getCause();
            }
            // Re-throw if not a constraint violation
            throw e;
        }
    }

    @Override
    public UserAccount getAccount(String userIdentifier) {
        if (userIdentifier == null) {
            return null;
        }

        UserAccountEntity entity = em.find(UserAccountEntity.class, userIdentifier);
        return entity != null ? entity.toUserAccount() : null;
    }

    @Override
    public UserAccount getAccountByEmail(String email) {
        if (email == null) {
            return null;
        }

        try {
            TypedQuery<UserAccountEntity> query = em.createQuery(
                "SELECT u FROM UserAccountEntity u WHERE u.email = :email",
                UserAccountEntity.class
            );
            query.setParameter("email", email);
            UserAccountEntity entity = query.getSingleResult();
            return entity.toUserAccount();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void updateAccount(UserAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }

        UserAccountEntity entity = em.find(UserAccountEntity.class, account.getUserIdentifier());
        if (entity == null) {
            throw new IllegalArgumentException(
                "Account not found: " + account.getUserIdentifier()
            );
        }

        // Update fields
        entity.setEmail(account.getEmail());
        entity.setName(account.getName());
        entity.setHomeProviderId(account.getHomeProviderId());
        // Create mutable copy of attributes for JPA change tracking
        entity.setAttributes(new java.util.HashMap<>(account.getAttributes()));
        // updatedAt is set automatically via @PreUpdate

        em.merge(entity);
        em.flush();

        logger.debugf("Updated account: %s", account.getUserIdentifier());
    }

    @Override
    public boolean deleteAccount(String userIdentifier) {
        if (userIdentifier == null) {
            return false;
        }

        UserAccountEntity entity = em.find(UserAccountEntity.class, userIdentifier);
        if (entity == null) {
            return false;
        }

        em.remove(entity);
        em.flush();

        logger.debugf("Deleted account: %s", userIdentifier);
        return true;
    }

    @Override
    public List<UserAccount> findAccountsByProvider(String providerId) {
        if (providerId == null) {
            return List.of();
        }

        TypedQuery<UserAccountEntity> query = em.createQuery(
            "SELECT u FROM UserAccountEntity u WHERE u.homeProviderId = :providerId",
            UserAccountEntity.class
        );
        query.setParameter("providerId", providerId);

        return query.getResultList().stream()
            .map(UserAccountEntity::toUserAccount)
            .collect(Collectors.toList());
    }

    @Override
    public Set<String> findProvidersByEmail(String userEmail) {
        if (userEmail == null) {
            return Set.of();
        }

        UserAccount account = getAccountByEmail(userEmail);
        if (account == null) {
            return Set.of();
        }

        return Set.of(account.getHomeProviderId());
    }

    @Override
    public Set<String> findProvidersByUser(String userIdentifier) {
        if (userIdentifier == null) {
            return Set.of();
        }

        UserAccount account = getAccount(userIdentifier);
        if (account == null) {
            return Set.of();
        }

        return Set.of(account.getHomeProviderId());
    }

    @Override
    public boolean accountExists(String userIdentifier) {
        if (userIdentifier == null) {
            return false;
        }

        return em.find(UserAccountEntity.class, userIdentifier) != null;
    }

    @Override
    public long getAccountCount() {
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(u) FROM UserAccountEntity u",
            Long.class
        );
        return query.getSingleResult();
    }

    @Override
    public long getAccountCountByProvider(String providerId) {
        if (providerId == null) {
            return 0;
        }

        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(u) FROM UserAccountEntity u WHERE u.homeProviderId = :providerId",
            Long.class
        );
        query.setParameter("providerId", providerId);
        return query.getSingleResult();
    }

    @Override
    public void close() {
        // EntityManager is managed by Keycloak, don't close it
    }
}
