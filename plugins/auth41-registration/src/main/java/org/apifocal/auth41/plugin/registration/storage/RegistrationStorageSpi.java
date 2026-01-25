package org.apifocal.auth41.plugin.registration.storage;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * SPI for registration workflow storage.
 *
 * <p>This SPI provides storage for invite tokens and registration requests
 * used in the registration workflow.
 */
public class RegistrationStorageSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "registration-storage";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return RegistrationStorageProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return RegistrationStorageProviderFactory.class;
    }
}
