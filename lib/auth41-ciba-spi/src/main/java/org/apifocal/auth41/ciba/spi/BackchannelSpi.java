package org.apifocal.auth41.ciba.spi;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * SPI registration for BackchannelProvider.
 *
 * This class registers the BackchannelProvider SPI with Keycloak's provider framework,
 * allowing implementations to be discovered via the Java ServiceLoader mechanism.
 */
public class BackchannelSpi implements Spi {

    public static final String SPI_NAME = "backchannel";

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return SPI_NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return BackchannelProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return BackchannelProviderFactory.class;
    }
}
