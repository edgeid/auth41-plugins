package org.apifocal.auth41.plugin.registration.resource;

import org.apifocal.auth41.plugin.registration.approval.RegistrationApprovalTask;
import org.apifocal.auth41.plugin.registration.config.RegistrationConfig;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;
import org.keycloak.timer.TimerProvider;

/**
 * Factory for creating RegistrationResourceProvider instances.
 *
 * <p>This factory creates REST resource providers that expose registration
 * endpoints at /realms/{realm}/registration/...
 *
 * <p>Also schedules the approval processor task to run periodically.
 */
public class RegistrationResourceProviderFactory implements RealmResourceProviderFactory {

    private static final Logger logger = Logger.getLogger(RegistrationResourceProviderFactory.class);

    public static final String PROVIDER_ID = "registration";
    private static final long APPROVAL_TASK_INTERVAL_MS = 10000; // 10 seconds

    private static RegistrationConfig config;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void init(Config.Scope configScope) {
        config = RegistrationConfig.fromConfig(configScope);
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new RegistrationResourceProvider(session, config);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Schedule approval processor task
        scheduleApprovalTask(factory);
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    /**
     * Get the configuration (used by scheduled task).
     *
     * @return Registration configuration
     */
    public static RegistrationConfig getConfig() {
        return config;
    }

    /**
     * Schedule the approval processor task to run periodically.
     *
     * @param factory Keycloak session factory
     */
    private void scheduleApprovalTask(KeycloakSessionFactory factory) {
        try {
            // Create a session to access TimerProvider
            KeycloakSession session = factory.create();
            try {
                TimerProvider timer = session.getProvider(TimerProvider.class);
                if (timer != null) {
                    RegistrationApprovalTask task = new RegistrationApprovalTask(factory);
                    timer.scheduleTask(task, APPROVAL_TASK_INTERVAL_MS, "RegistrationApprovalTask");
                    logger.infof("Scheduled registration approval task to run every %d seconds",
                            APPROVAL_TASK_INTERVAL_MS / 1000);
                } else {
                    logger.warn("TimerProvider not available, approval task not scheduled");
                }
            } finally {
                session.close();
            }
        } catch (Exception e) {
            logger.error("Failed to schedule registration approval task", e);
        }
    }
}
