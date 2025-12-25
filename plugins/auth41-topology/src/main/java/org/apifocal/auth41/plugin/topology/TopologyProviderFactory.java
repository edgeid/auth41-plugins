package org.apifocal.auth41.plugin.topology;

import org.keycloak.provider.ProviderFactory;

/**
 * Factory for creating TopologyProvider instances.
 */
public interface TopologyProviderFactory extends ProviderFactory<TopologyProvider> {

    /**
     * Get the topology type this factory creates providers for.
     *
     * @return Topology type identifier (e.g., "hub-and-spoke")
     */
    String getTopologyType();
}
