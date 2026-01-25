package org.apifocal.auth41.plugin.registration.config;

import org.jboss.logging.Logger;
import org.keycloak.Config;

/**
 * Configuration for registration plugin.
 *
 * <p>Loads configuration from Keycloak's Config.Scope (system properties).
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>invite-ttl-seconds - Invite token time-to-live (default: 300 = 5 minutes)</li>
 *   <li>request-ttl-seconds - Registration request time-to-live (default: 600 = 10 minutes)</li>
 *   <li>approval-delay-seconds - Auto-approval delay (default: 30 seconds)</li>
 *   <li>rate-limit-window-seconds - Rate limiting window (default: 300 = 5 minutes)</li>
 *   <li>rate-limit-max-invites - Maximum invites per IP in rate limit window (default: 3)</li>
 *   <li>polling-interval-seconds - Recommended polling interval (default: 5 seconds)</li>
 *   <li>approval-task-interval-seconds - Approval processor task interval (default: 10 seconds)</li>
 *   <li>cleanup-task-interval-seconds - Cleanup task interval for expired records (default: 3600 = 1 hour)</li>
 * </ul>
 *
 * <p>Example system properties:
 * <pre>
 * -Dspi-realm-resource-registration-invite-ttl-seconds=600
 * -Dspi-realm-resource-registration-approval-delay-seconds=60
 * -Dspi-realm-resource-registration-rate-limit-max-invites=5
 * </pre>
 */
public class RegistrationConfig {

    private static final Logger logger = Logger.getLogger(RegistrationConfig.class);

    // Default values
    private static final int DEFAULT_INVITE_TTL_SECONDS = 300;             // 5 minutes
    private static final int DEFAULT_REQUEST_TTL_SECONDS = 600;            // 10 minutes
    private static final int DEFAULT_APPROVAL_DELAY_SECONDS = 30;          // 30 seconds
    private static final int DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 300;      // 5 minutes
    private static final int DEFAULT_RATE_LIMIT_MAX_INVITES = 3;           // 3 invites per window
    private static final int DEFAULT_POLLING_INTERVAL_SECONDS = 5;         // 5 seconds
    private static final int DEFAULT_APPROVAL_TASK_INTERVAL_SECONDS = 10;  // 10 seconds
    private static final int DEFAULT_CLEANUP_TASK_INTERVAL_SECONDS = 3600; // 1 hour

    private final int inviteTtlSeconds;
    private final int requestTtlSeconds;
    private final int approvalDelaySeconds;
    private final int rateLimitWindowSeconds;
    private final int rateLimitMaxInvites;
    private final int pollingIntervalSeconds;
    private final int approvalTaskIntervalSeconds;
    private final int cleanupTaskIntervalSeconds;

    /**
     * Create configuration from Keycloak Config.Scope.
     *
     * @param config Keycloak configuration scope
     * @return Configuration instance
     */
    public static RegistrationConfig fromConfig(Config.Scope config) {
        return new RegistrationConfig(config);
    }

    /**
     * Create configuration with default values (for testing).
     *
     * @return Configuration with defaults
     */
    public static RegistrationConfig withDefaults() {
        return new RegistrationConfig(null);
    }

    private RegistrationConfig(Config.Scope config) {
        if (config != null) {
            this.inviteTtlSeconds = config.getInt("invite-ttl-seconds", DEFAULT_INVITE_TTL_SECONDS);
            this.requestTtlSeconds = config.getInt("request-ttl-seconds", DEFAULT_REQUEST_TTL_SECONDS);
            this.approvalDelaySeconds = config.getInt("approval-delay-seconds", DEFAULT_APPROVAL_DELAY_SECONDS);
            this.rateLimitWindowSeconds = config.getInt("rate-limit-window-seconds", DEFAULT_RATE_LIMIT_WINDOW_SECONDS);
            this.rateLimitMaxInvites = config.getInt("rate-limit-max-invites", DEFAULT_RATE_LIMIT_MAX_INVITES);
            this.pollingIntervalSeconds = config.getInt("polling-interval-seconds", DEFAULT_POLLING_INTERVAL_SECONDS);
            this.approvalTaskIntervalSeconds = config.getInt("approval-task-interval-seconds", DEFAULT_APPROVAL_TASK_INTERVAL_SECONDS);
            this.cleanupTaskIntervalSeconds = config.getInt("cleanup-task-interval-seconds", DEFAULT_CLEANUP_TASK_INTERVAL_SECONDS);
        } else {
            // Use defaults (for testing or when config is not available)
            this.inviteTtlSeconds = DEFAULT_INVITE_TTL_SECONDS;
            this.requestTtlSeconds = DEFAULT_REQUEST_TTL_SECONDS;
            this.approvalDelaySeconds = DEFAULT_APPROVAL_DELAY_SECONDS;
            this.rateLimitWindowSeconds = DEFAULT_RATE_LIMIT_WINDOW_SECONDS;
            this.rateLimitMaxInvites = DEFAULT_RATE_LIMIT_MAX_INVITES;
            this.pollingIntervalSeconds = DEFAULT_POLLING_INTERVAL_SECONDS;
            this.approvalTaskIntervalSeconds = DEFAULT_APPROVAL_TASK_INTERVAL_SECONDS;
            this.cleanupTaskIntervalSeconds = DEFAULT_CLEANUP_TASK_INTERVAL_SECONDS;
        }

        logConfiguration();
    }

    private void logConfiguration() {
        logger.infof("Registration configuration loaded:");
        logger.infof("  Invite TTL: %d seconds", inviteTtlSeconds);
        logger.infof("  Request TTL: %d seconds", requestTtlSeconds);
        logger.infof("  Approval delay: %d seconds", approvalDelaySeconds);
        logger.infof("  Rate limit window: %d seconds", rateLimitWindowSeconds);
        logger.infof("  Rate limit max invites: %d", rateLimitMaxInvites);
        logger.infof("  Polling interval: %d seconds", pollingIntervalSeconds);
        logger.infof("  Approval task interval: %d seconds", approvalTaskIntervalSeconds);
        logger.infof("  Cleanup task interval: %d seconds", cleanupTaskIntervalSeconds);
    }

    /**
     * @return Invite token time-to-live in seconds
     */
    public int getInviteTtlSeconds() {
        return inviteTtlSeconds;
    }

    /**
     * @return Registration request time-to-live in seconds
     */
    public int getRequestTtlSeconds() {
        return requestTtlSeconds;
    }

    /**
     * @return Auto-approval delay in seconds
     */
    public int getApprovalDelaySeconds() {
        return approvalDelaySeconds;
    }

    /**
     * @return Rate limiting window in seconds
     */
    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    /**
     * @return Maximum number of invites allowed per IP in rate limit window
     */
    public int getRateLimitMaxInvites() {
        return rateLimitMaxInvites;
    }

    /**
     * @return Recommended polling interval in seconds
     */
    public int getPollingIntervalSeconds() {
        return pollingIntervalSeconds;
    }

    /**
     * @return Approval task interval in seconds
     */
    public int getApprovalTaskIntervalSeconds() {
        return approvalTaskIntervalSeconds;
    }

    /**
     * @return Cleanup task interval in seconds
     */
    public int getCleanupTaskIntervalSeconds() {
        return cleanupTaskIntervalSeconds;
    }

    @Override
    public String toString() {
        return "RegistrationConfig{" +
                "inviteTtl=" + inviteTtlSeconds + "s" +
                ", requestTtl=" + requestTtlSeconds + "s" +
                ", approvalDelay=" + approvalDelaySeconds + "s" +
                ", rateLimitWindow=" + rateLimitWindowSeconds + "s" +
                ", rateLimitMaxInvites=" + rateLimitMaxInvites +
                ", pollingInterval=" + pollingIntervalSeconds + "s" +
                ", approvalTaskInterval=" + approvalTaskIntervalSeconds + "s" +
                ", cleanupTaskInterval=" + cleanupTaskIntervalSeconds + "s" +
                '}';
    }
}
