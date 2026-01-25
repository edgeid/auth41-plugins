package org.apifocal.auth41.plugin.trustnetwork;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating configuration-based TrustNetworkProvider instances.
 *
 * This factory:
 * - Loads trust network configuration during initialization
 * - Caches trust networks in memory
 * - Creates provider instances per session
 */
public class ConfigBasedTrustNetworkProviderFactory implements TrustNetworkProviderFactory {

    private static final Logger logger = Logger.getLogger(ConfigBasedTrustNetworkProviderFactory.class);
    private static final String PROVIDER_ID = "config-based";

    private final Map<String, TrustNetwork> networkCache = new ConcurrentHashMap<>();
    private TrustNetworkConfigLoader configLoader;

    @Override
    public TrustNetworkProvider create(KeycloakSession session) {
        return new ConfigBasedTrustNetworkProvider(session, networkCache, configLoader);
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing ConfigBasedTrustNetworkProviderFactory");

        // Initialize configuration loader
        this.configLoader = new TrustNetworkConfigLoader();

        // Load networks from configuration
        // For now, we'll load from a default network config if present
        String defaultNetworkId = config.get("default-network-id");
        if (defaultNetworkId != null && !defaultNetworkId.isEmpty()) {
            logger.infof("Loading default network: %s", defaultNetworkId);
            try {
                TrustNetwork network = configLoader.loadFromConfig(config, defaultNetworkId);
                if (network != null) {
                    networkCache.put(defaultNetworkId, network);
                    logger.infof("Loaded network %s with %d providers and %d trust relationships",
                        defaultNetworkId,
                        network.getProviders().size(),
                        network.getTrustRelationships().size());
                }
            } catch (Exception e) {
                logger.errorf(e, "Failed to load default network: %s", defaultNetworkId);
            }
        }

        logger.infof("ConfigBasedTrustNetworkProviderFactory initialized with %d networks", networkCache.size());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        logger.info("Closing ConfigBasedTrustNetworkProviderFactory");
        networkCache.clear();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Get cached network (for testing)
     */
    TrustNetwork getCachedNetwork(String networkId) {
        return networkCache.get(networkId);
    }

    /**
     * Add network to cache (for testing)
     */
    void putNetwork(String networkId, TrustNetwork network) {
        networkCache.put(networkId, network);
    }
}
