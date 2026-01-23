package org.apifocal.auth41.plugin.registration.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.apifocal.auth41.plugin.registration.entity.InviteTokenEntity;
import org.apifocal.auth41.plugin.registration.entity.RegistrationRequestEntity;
import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.jboss.logging.Logger;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA-based implementation of RegistrationStorageProvider.
 *
 * <p>Uses Keycloak's existing JPA connection to store registration data
 * in the same database as Keycloak.
 */
public class JpaRegistrationStorageProvider implements RegistrationStorageProvider {

    private static final Logger logger = Logger.getLogger(JpaRegistrationStorageProvider.class);

    private final KeycloakSession session;
    private final EntityManager em;

    public JpaRegistrationStorageProvider(KeycloakSession session) {
        this.session = session;
        this.em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    // Invite token operations

    @Override
    public void createInviteToken(InviteToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        try {
            InviteTokenEntity entity = InviteTokenEntity.fromInviteToken(token);
            em.persist(entity);
            em.flush();

            logger.debugf("Created invite token for IP %s in realm %s",
                    token.getIpAddress(), token.getRealmId());
        } catch (jakarta.persistence.PersistenceException e) {
            // Check if this is a constraint violation (duplicate key)
            if (isConstraintViolation(e)) {
                throw new IllegalArgumentException(
                        "Invite token already exists: " + token.getInviteToken(), e
                );
            }
            throw e;
        }
    }

    @Override
    public InviteToken getInviteToken(String inviteToken) {
        if (inviteToken == null) {
            return null;
        }

        InviteTokenEntity entity = em.find(InviteTokenEntity.class, inviteToken);
        return entity != null ? entity.toInviteToken() : null;
    }

    @Override
    public long countRecentInvitesByIp(String ipAddress, Instant since) {
        if (ipAddress == null || since == null) {
            return 0;
        }

        TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(t) FROM InviteTokenEntity t " +
                        "WHERE t.ipAddress = :ipAddress AND t.createdAt > :since",
                Long.class
        );
        query.setParameter("ipAddress", ipAddress);
        query.setParameter("since", since);
        return query.getSingleResult();
    }

    @Override
    public void markInviteTokenUsed(String inviteToken) {
        if (inviteToken == null) {
            throw new IllegalArgumentException("Invite token cannot be null");
        }

        InviteTokenEntity entity = em.find(InviteTokenEntity.class, inviteToken);
        if (entity == null) {
            throw new IllegalArgumentException("Invite token not found: " + inviteToken);
        }

        // Check if already used to prevent race condition
        if (entity.isUsed()) {
            throw new IllegalStateException("Invite token already used: " + inviteToken);
        }

        entity.setUsed(true);
        entity.setUsedAt(Instant.now());
        em.merge(entity);
        em.flush();

        logger.debugf("Marked invite token as used: %s", inviteToken);
    }

    // Registration request operations

    @Override
    public void createRegistrationRequest(RegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        try {
            RegistrationRequestEntity entity = RegistrationRequestEntity.fromRegistrationRequest(request);
            em.persist(entity);
            em.flush();

            logger.debugf("Created registration request for %s in realm %s",
                    request.getEmail(), request.getRealmId());
        } catch (jakarta.persistence.PersistenceException e) {
            if (isConstraintViolation(e)) {
                throw new IllegalArgumentException(
                        "Registration request already exists: " + request.getRequestId(), e
                );
            }
            throw e;
        }
    }

    @Override
    public RegistrationRequest getRegistrationRequest(String requestId) {
        if (requestId == null) {
            return null;
        }

        RegistrationRequestEntity entity = em.find(RegistrationRequestEntity.class, requestId);
        return entity != null ? entity.toRegistrationRequest() : null;
    }

    @Override
    public void updateRegistrationRequest(RegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        RegistrationRequestEntity entity = em.find(RegistrationRequestEntity.class, request.getRequestId());
        if (entity == null) {
            throw new IllegalArgumentException(
                    "Registration request not found: " + request.getRequestId()
            );
        }

        // Update fields
        entity.setEmail(request.getEmail());
        entity.setAttributes(new HashMap<>(request.getAttributes()));
        entity.setStatus(request.getStatus());
        entity.setApprovedAt(request.getApprovedAt());
        entity.setUserId(request.getUserId());

        em.merge(entity);
        em.flush();

        logger.debugf("Updated registration request %s: status=%s",
                request.getRequestId(), request.getStatus());
    }

    @Override
    public List<RegistrationRequest> getPendingRequests(Instant olderThan) {
        if (olderThan == null) {
            olderThan = Instant.now();
        }

        TypedQuery<RegistrationRequestEntity> query = em.createQuery(
                "SELECT r FROM RegistrationRequestEntity r " +
                        "WHERE r.status = :status AND r.createdAt < :olderThan " +
                        "ORDER BY r.createdAt ASC",
                RegistrationRequestEntity.class
        );
        query.setParameter("status", RegistrationRequest.Status.PENDING);
        query.setParameter("olderThan", olderThan);

        return query.getResultList().stream()
                .map(RegistrationRequestEntity::toRegistrationRequest)
                .collect(Collectors.toList());
    }

    @Override
    public int deleteExpiredInviteTokens(Instant expiredBefore) {
        // Delete tokens that have expired before the given instant
        Query query = em.createQuery(
                "DELETE FROM InviteTokenEntity i WHERE i.expiresAt < :expiredBefore"
        );
        query.setParameter("expiredBefore", expiredBefore);
        return query.executeUpdate();
    }

    @Override
    public int deleteExpiredRegistrationRequests(Instant expiredBefore) {
        // Delete requests that have expired before the given instant
        Query query = em.createQuery(
                "DELETE FROM RegistrationRequestEntity r WHERE r.expiresAt < :expiredBefore"
        );
        query.setParameter("expiredBefore", expiredBefore);
        return query.executeUpdate();
    }

    @Override
    public void close() {
        // EntityManager is managed by Keycloak
    }

    /**
     * Check if exception is a constraint violation (duplicate key).
     */
    private boolean isConstraintViolation(jakarta.persistence.PersistenceException e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            String className = current.getClass().getName();

            // Check exception class name
            if (className.contains("ConstraintViolationException")) {
                return true;
            }

            // Check message content
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("constraint") ||
                        lowerMessage.contains("unique") ||
                        lowerMessage.contains("duplicate") ||
                        lowerMessage.contains("primary key")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }
}
