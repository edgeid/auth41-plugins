package org.apifocal.auth41.plugin.topology;

import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.keycloak.provider.Provider;

/**
 * SPI for computing trust paths based on network topology.
 *
 * Different topology implementations (hub-and-spoke, peer-to-peer, etc.)
 * compute trust paths differently based on the network structure.
 */
public interface TopologyProvider extends Provider {

    /**
     * Compute the trust path from source provider to target provider.
     *
     * @param network Trust network containing providers and relationships
     * @param sourceProvider Source provider ID
     * @param targetProvider Target provider ID
     * @return TrustPath with path information, or unreachable path if no route exists
     */
    TrustPath computeTrustPath(TrustNetwork network, String sourceProvider, String targetProvider);

    /**
     * Validate that the network structure conforms to this topology.
     *
     * @param network Trust network to validate
     * @return true if the network is valid for this topology, false otherwise
     */
    boolean validateTopology(TrustNetwork network);

    /**
     * Get the topology type identifier (e.g., "hub-and-spoke", "peer-to-peer")
     *
     * @return Topology type identifier
     */
    String getTopologyType();
}
