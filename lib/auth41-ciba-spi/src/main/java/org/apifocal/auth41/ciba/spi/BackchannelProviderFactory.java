package org.apifocal.auth41.ciba.spi;

import org.keycloak.provider.ProviderFactory;

import java.util.Set;

/**
 * Factory for creating BackchannelProvider instances.
 *
 * Implementations should extend this interface and register via the
 * Java ServiceLoader mechanism.
 */
public interface BackchannelProviderFactory extends ProviderFactory<BackchannelProvider> {

    /**
     * Get the delivery modes supported by providers created by this factory.
     *
     * This allows the CIBA endpoint to advertise supported modes in the
     * OpenID Connect discovery document.
     *
     * @return set of supported delivery modes (e.g., "poll", "ping", "push")
     */
    Set<String> getSupportedDeliveryModes();
}
