package org.apifocal.auth41.plugin.themes.provider;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ThemeSelectorProvider;
import org.keycloak.theme.ThemeSelectorProviderFactory;
import org.apifocal.auth41.plugin.themes.config.ThemeMappingConfig;

/**
 * Factory for creating Auth41ThemeSelectorProvider instances.
 * Loads configuration on initialization and provides it to created providers.
 */
public class Auth41ThemeSelectorProviderFactory implements ThemeSelectorProviderFactory {
    private static final Logger logger = Logger.getLogger(Auth41ThemeSelectorProviderFactory.class);
    private static final String PROVIDER_ID = "auth41-theme-selector";

    private ThemeMappingConfig config;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing Auth41 Theme Selector Provider Factory");

        // Load configuration from Keycloak config
        this.config = ThemeMappingConfig.loadFromConfig(config);

        logger.infof("Auth41 Theme Selector initialized with provider ID: %s", PROVIDER_ID);
        logger.infof("Realm mappings: %s", this.config.getRealmMappings());
        logger.infof("Client mappings: %s", this.config.getClientMappings());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        logger.debug("Post-initialization of Auth41 Theme Selector Provider Factory");
    }

    @Override
    public ThemeSelectorProvider create(KeycloakSession session) {
        return new Auth41ThemeSelectorProvider(session, config);
    }

    @Override
    public void close() {
        logger.info("Closing Auth41 Theme Selector Provider Factory");
    }
}
