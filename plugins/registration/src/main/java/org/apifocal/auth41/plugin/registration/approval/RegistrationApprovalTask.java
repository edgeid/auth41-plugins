package org.apifocal.auth41.plugin.registration.approval;

import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.apifocal.auth41.plugin.registration.resource.RegistrationResourceProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.timer.ScheduledTask;

/**
 * Scheduled task that periodically processes pending registration requests.
 *
 * <p>This task runs every 10 seconds and processes all pending registration
 * requests that are older than the configured approval delay.
 *
 * <p>Registered in RegistrationResourceProviderFactory using Keycloak's
 * TimerProvider during postInit().
 */
public class RegistrationApprovalTask implements ScheduledTask {

    private static final Logger logger = Logger.getLogger(RegistrationApprovalTask.class);

    private final KeycloakSessionFactory sessionFactory;

    public RegistrationApprovalTask(KeycloakSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void run(KeycloakSession session) {
        try {
            // Get configuration
            RegistrationConfig config = RegistrationResourceProviderFactory.getConfig();
            if (config == null) {
                logger.warn("RegistrationConfig not available, skipping approval processing");
                return;
            }

            // Process pending requests
            RegistrationApprovalProcessor processor = new RegistrationApprovalProcessor(session, config);
            int processed = processor.processPendingRequests();

            if (processed > 0) {
                logger.debugf("Approval task processed %d registration requests", processed);
            }

        } catch (Exception e) {
            logger.error("Error in registration approval task", e);
        }
    }
}
