package org.apifocal.auth41.plugin.backchannel.mock;

import org.apifocal.auth41.ciba.spi.BackchannelProvider;
import org.apifocal.auth41.ciba.spi.BackchannelProviderFactory;
import org.apifocal.auth41.ciba.spi.CibaConstants;
import org.apifocal.auth41.spi.AbstractProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;

import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating mock backchannel providers.
 *
 * **WARNING: DO NOT USE IN PRODUCTION**
 *
 * This is a test-only provider that simulates authentication responses.
 */
public class MockBackchannelProviderFactory extends AbstractProviderFactory<BackchannelProvider>
        implements BackchannelProviderFactory {

    private static final String PROVIDER_ID = "mock-test-only";

    // Configuration keys
    private static final String CONFIG_DELAY = "delay";
    private static final String CONFIG_APPROVAL_RATE = "approvalRate";
    private static final String CONFIG_ERROR_RATE = "errorRate";
    private static final String CONFIG_AUTO_APPROVE = "autoApprove";

    // Default values
    private static final int DEFAULT_DELAY_MS = 3000;
    private static final int DEFAULT_APPROVAL_RATE = 100;
    private static final int DEFAULT_ERROR_RATE = 0;
    private static final boolean DEFAULT_AUTO_APPROVE = true;

    private long delayMillis;
    private int approvalRate;
    private int errorRate;
    private boolean autoApprove;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void doInit(Config.Scope config) {
        // Read configuration
        this.delayMillis = getConfigInt(config, CONFIG_DELAY, DEFAULT_DELAY_MS);
        this.approvalRate = getConfigInt(config, CONFIG_APPROVAL_RATE, DEFAULT_APPROVAL_RATE);
        this.errorRate = getConfigInt(config, CONFIG_ERROR_RATE, DEFAULT_ERROR_RATE);

        String autoApproveStr = getConfig(config, CONFIG_AUTO_APPROVE, String.valueOf(DEFAULT_AUTO_APPROVE));
        this.autoApprove = Boolean.parseBoolean(autoApproveStr);

        // Validate configuration
        if (approvalRate < 0 || approvalRate > 100) {
            logger.errorf("Invalid approvalRate: %d (must be 0-100), using default: %d",
                approvalRate, DEFAULT_APPROVAL_RATE);
            this.approvalRate = DEFAULT_APPROVAL_RATE;
        }

        if (errorRate < 0 || errorRate > 100) {
            logger.errorf("Invalid errorRate: %d (must be 0-100), using default: %d",
                errorRate, DEFAULT_ERROR_RATE);
            this.errorRate = DEFAULT_ERROR_RATE;
        }

        if (approvalRate + errorRate > 100) {
            logger.errorf("approvalRate (%d) + errorRate (%d) exceeds 100%%, reducing errorRate",
                approvalRate, errorRate);
            this.errorRate = Math.max(0, 100 - approvalRate);
        }

        if (delayMillis < 0) {
            logger.errorf("Invalid delay: %d (must be >= 0), using default: %d",
                delayMillis, DEFAULT_DELAY_MS);
            this.delayMillis = DEFAULT_DELAY_MS;
        }

        logger.warn("═══════════════════════════════════════════════════════════");
        logger.warn("  MOCK BACKCHANNEL PROVIDER FACTORY INITIALIZED");
        logger.warn("  Provider ID: " + PROVIDER_ID);
        logger.warn("  DO NOT USE THIS PROVIDER IN PRODUCTION");
        logger.warn("  Configuration:");
        logger.warn("    - Delay: " + delayMillis + "ms");
        logger.warn("    - Approval Rate: " + approvalRate + "%");
        logger.warn("    - Error Rate: " + errorRate + "%");
        logger.warn("    - Auto-Approve: " + autoApprove);
        logger.warn("═══════════════════════════════════════════════════════════");
    }

    @Override
    public BackchannelProvider create(KeycloakSession session) {
        return new MockBackchannelProvider(delayMillis, approvalRate, errorRate, autoApprove);
    }

    @Override
    public Set<String> getSupportedDeliveryModes() {
        Set<String> modes = new HashSet<>();
        modes.add(CibaConstants.MODE_POLL);
        return modes;
    }
}
