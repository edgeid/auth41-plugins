package org.apifocal.auth41.plugin.registration.task;

import org.apifocal.auth41.plugin.registration.resource.RegistrationResourceProviderFactory;
import org.apifocal.auth41.plugin.registration.storage.RegistrationStorageProvider;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.timer.ScheduledTask;

import java.time.Instant;

/**
 * Scheduled task that cleans up expired invite tokens and registration requests.
 *
 * <p>This task runs periodically to delete expired records from the database,
 * preventing unbounded growth and maintaining performance.
 *
 * <p>The cleanup deletes:
 * <ul>
 *   <li>Invite tokens that have expired
 *   <li>Registration requests that have expired
 * </ul>
 *
 * <p>This task is scheduled by {@link RegistrationResourceProviderFactory}
 * and runs at the interval specified by the cleanup-task-interval-seconds configuration.
 */
public class RegistrationCleanupTask implements ScheduledTask {

    private static final Logger logger = Logger.getLogger(RegistrationCleanupTask.class);

    @Override
    public void run(KeycloakSession session) {
        try {
            logger.debug("Starting registration cleanup task");

            RegistrationStorageProvider storage = session.getProvider(RegistrationStorageProvider.class);
            if (storage == null) {
                logger.warn("RegistrationStorageProvider not available, skipping cleanup");
                return;
            }

            Instant now = Instant.now();

            // Delete expired invite tokens
            int deletedTokens = storage.deleteExpiredInviteTokens(now);
            if (deletedTokens > 0) {
                logger.infof("Deleted %d expired invite tokens", deletedTokens);
            }

            // Delete expired registration requests
            int deletedRequests = storage.deleteExpiredRegistrationRequests(now);
            if (deletedRequests > 0) {
                logger.infof("Deleted %d expired registration requests", deletedRequests);
            }

            if (deletedTokens == 0 && deletedRequests == 0) {
                logger.debug("No expired records to clean up");
            }

        } catch (Exception e) {
            logger.error("Error during registration cleanup task", e);
        }
    }
}
