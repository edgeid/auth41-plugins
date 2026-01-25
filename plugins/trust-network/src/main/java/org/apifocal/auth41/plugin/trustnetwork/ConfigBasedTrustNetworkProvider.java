package org.apifocal.auth41.plugin.trustnetwork;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.util.Map;
import java.util.Set;

/**
 * Configuration-based implementation of TrustNetworkProvider.
 *
 * This provider:
 * - Loads networks from cached configuration
 * - Provides fast in-memory lookups
 * - Thread-safe operations
 */
public class ConfigBasedTrustNetworkProvider implements TrustNetworkProvider {

    private static final Logger logger = Logger.getLogger(ConfigBasedTrustNetworkProvider.class);

    private final KeycloakSession session;
    private final Map<String, TrustNetwork> networkCache;
    private final TrustNetworkConfigLoader configLoader;

    public ConfigBasedTrustNetworkProvider(
        KeycloakSession session,
        Map<String, TrustNetwork> networkCache,
        TrustNetworkConfigLoader configLoader) {
        this.session = session;
        this.networkCache = networkCache;
        this.configLoader = configLoader;
    }

    @Override
    public TrustNetwork loadNetwork(String networkId) {
        if (networkId == null || networkId.isEmpty()) {
            logger.warn("Attempted to load network with null or empty networkId");
            return null;
        }

        TrustNetwork network = networkCache.get(networkId);
        if (network == null) {
            logger.debugf("Network %s not found in cache", networkId);
        }
        return network;
    }

    @Override
    public boolean isMember(String providerId, String networkId) {
        if (providerId == null || networkId == null) {
            return false;
        }

        TrustNetwork network = loadNetwork(networkId);
        if (network == null) {
            return false;
        }

        return network.isMember(providerId);
    }

    @Override
    public ProviderNode getProviderMetadata(String providerId, String networkId) {
        if (providerId == null || networkId == null) {
            return null;
        }

        TrustNetwork network = loadNetwork(networkId);
        if (network == null) {
            return null;
        }

        return network.getProvider(providerId);
    }

    @Override
    public Set<TrustEdge> getTrustRelationships(String networkId) {
        if (networkId == null) {
            return Set.of();
        }

        TrustNetwork network = loadNetwork(networkId);
        if (network == null) {
            return Set.of();
        }

        return network.getTrustRelationships();
    }

    @Override
    public boolean hasTrustRelationship(String fromProvider, String toProvider, String networkId) {
        if (fromProvider == null || toProvider == null || networkId == null) {
            return false;
        }

        TrustNetwork network = loadNetwork(networkId);
        if (network == null) {
            return false;
        }

        return network.hasTrustRelationship(fromProvider, toProvider);
    }

    @Override
    public void refreshNetwork(String networkId) {
        if (networkId == null || networkId.isEmpty()) {
            logger.warn("Attempted to refresh network with null or empty networkId");
            return;
        }

        logger.infof("Refreshing network: %s", networkId);
        // For now, this is a no-op since we load from static config
        // In future, this would fetch from governance registry
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
