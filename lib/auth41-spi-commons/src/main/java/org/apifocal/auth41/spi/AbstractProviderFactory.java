package org.apifocal.auth41.spi;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;

/**
 * Abstract base class for Auth41 provider factories.
 *
 * <p>Provides common lifecycle management with structured logging and
 * template methods for initialization and cleanup.
 *
 * <p>Subclasses should override {@link #doInit(Config.Scope)},
 * {@link #doPostInit(KeycloakSessionFactory)}, and {@link #doClose()}
 * to provide custom initialization and cleanup logic.
 *
 * @param <T> the provider type
 */
public abstract class AbstractProviderFactory<T extends Provider> implements ProviderFactory<T> {

    protected final Logger logger;

    protected AbstractProviderFactory() {
        this.logger = Logger.getLogger(getClass());
    }

    @Override
    public void init(Config.Scope config) {
        logger.infof("Initializing %s (provider ID: %s)", getClass().getSimpleName(), getId());
        try {
            doInit(config);
            logger.infof("%s initialized successfully", getId());
        } catch (Exception e) {
            logger.errorf(e, "Failed to initialize %s", getId());
            throw new RuntimeException("Initialization failed for " + getId(), e);
        }
    }

    /**
     * Perform provider-specific initialization.
     *
     * <p>Override this method to load configuration, initialize caches, etc.
     *
     * @param config the configuration scope
     */
    protected abstract void doInit(Config.Scope config);

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        logger.debugf("Post-initialization of %s", getClass().getSimpleName());
        try {
            doPostInit(factory);
        } catch (Exception e) {
            logger.errorf(e, "Post-initialization failed for %s", getId());
            throw new RuntimeException("Post-initialization failed for " + getId(), e);
        }
    }

    /**
     * Perform post-initialization tasks.
     *
     * <p>Override this method if you need access to the session factory
     * after all providers are initialized.
     *
     * <p>Default implementation does nothing.
     *
     * @param factory the Keycloak session factory
     */
    protected void doPostInit(KeycloakSessionFactory factory) {
        // Default: no-op
    }

    @Override
    public void close() {
        logger.infof("Closing %s", getClass().getSimpleName());
        try {
            doClose();
        } catch (Exception e) {
            logger.errorf(e, "Error during close of %s", getId());
        }
    }

    /**
     * Perform cleanup tasks.
     *
     * <p>Override this method to release resources, clear caches, etc.
     *
     * <p>Default implementation does nothing.
     */
    protected void doClose() {
        // Default: no-op
    }

    /**
     * Get the configuration value as string with default.
     *
     * @param config the configuration scope
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the configuration value or default
     */
    protected String getConfig(Config.Scope config, String key, String defaultValue) {
        return config.get(key, defaultValue);
    }

    /**
     * Get the configuration value as integer with default.
     *
     * @param config the configuration scope
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the configuration value or default
     */
    protected int getConfigInt(Config.Scope config, String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warnf("Invalid integer value for config key '%s': %s, using default: %d",
                key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get the configuration value as boolean with default.
     *
     * @param config the configuration scope
     * @param key the configuration key
     * @param defaultValue the default value if key not found
     * @return the configuration value or default
     */
    protected boolean getConfigBoolean(Config.Scope config, String key, boolean defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
