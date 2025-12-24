package org.apifocal.auth41.plugin.trustnetwork;

import org.keycloak.provider.Provider;

/**
 * SPI for managing trust networks in OIDC federation.
 *
 * This provider:
 * - Loads trust network configuration
 * - Queries trust relationships
 * - Validates provider membership
 * - Caches trust network data
 */
public interface TrustNetworkProvider extends Provider {

    /**
     * Load trust network by ID
     *
     * @param networkId Unique network identifier
     * @return Trust network, or null if not found
     */
    TrustNetwork loadNetwork(String networkId);

    /**
     * Check if a provider is a member of the network
     *
     * @param providerId Provider identifier
     * @param networkId Network identifier
     * @return true if provider is in network
     */
    boolean isMember(String providerId, String networkId);

    /**
     * Get provider metadata
     *
     * @param providerId Provider identifier
     * @param networkId Network identifier
     * @return Provider node, or null if not found
     */
    ProviderNode getProviderMetadata(String providerId, String networkId);

    /**
     * Get all trust relationships in the network
     *
     * @param networkId Network identifier
     * @return Set of trust edges
     */
    java.util.Set<TrustEdge> getTrustRelationships(String networkId);

    /**
     * Check if there is a direct trust relationship between two providers
     *
     * @param fromProvider Source provider
     * @param toProvider Target provider
     * @param networkId Network identifier
     * @return true if direct trust exists
     */
    boolean hasTrustRelationship(String fromProvider, String toProvider, String networkId);

    /**
     * Refresh cached trust network from source
     *
     * @param networkId Network identifier
     */
    void refreshNetwork(String networkId);
}
