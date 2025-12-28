package org.apifocal.auth41.plugin.ciba;

import org.apifocal.auth41.spi.AbstractProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Factory for CIBA resource provider.
 *
 * Registers the CIBA endpoints at /realms/{realm}/ext/ciba
 */
public class CibaResourceProviderFactory extends AbstractProviderFactory<RealmResourceProvider>
        implements RealmResourceProviderFactory {

    private static final String PROVIDER_ID = "ciba";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void doInit(Config.Scope config) {
        logger.info("CIBA resource provider initialized");
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new CibaResourceProvider(session);
    }
}
