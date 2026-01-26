package org.apifocal.auth41.plugin.registration.storage;

import org.apifocal.auth41.plugin.registration.entity.InviteTokenEntity;
import org.apifocal.auth41.plugin.registration.entity.RegistrationRequestEntity;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.List;

/**
 * Factory for JpaRegistrationStorageProvider.
 *
 * <p>Registers JPA entities and provides Liquibase changelog location.
 * Keycloak's automatic Liquibase execution doesn't work reliably in development mode,
 * so this factory includes a fallback mechanism to create tables using direct DDL.
 *
 * <p>In production with Liquibase-based deployment, the changelog location is used.
 * In development mode, the fallback creates tables directly.
 */
public class JpaRegistrationStorageProviderFactory
        implements RegistrationStorageProviderFactory, JpaEntityProvider {

    private static final Logger logger = Logger.getLogger(JpaRegistrationStorageProviderFactory.class);
    public static final String PROVIDER_ID = "auth41-jpa-registration";

    @Override
    public RegistrationStorageProvider create(KeycloakSession session) {
        return new JpaRegistrationStorageProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing Auth41 Registration Storage Provider");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // NOTE: Keycloak's automatic Liquibase execution via JpaEntityProvider doesn't work
        // reliably in development mode. The Liquibase changelog at META-INF/registration-changelog.xml
        // is provided for production deployments. In development, tables will be created lazily
        // on first use (in the storage provider constructor).
        logger.info("Auth41 Registration Storage Provider initialized");
    }

    @Override
    public void close() {
        logger.info("Closing Auth41 Registration Storage Provider");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    // JpaEntityProvider implementation

    @Override
    public List<Class<?>> getEntities() {
        return List.of(InviteTokenEntity.class, RegistrationRequestEntity.class);
    }

    @Override
    public String getChangelogLocation() {
        // Provided for production Liquibase-based deployments
        return "META-INF/registration-changelog.xml";
    }

    @Override
    public String getFactoryId() {
        return PROVIDER_ID;
    }
}
