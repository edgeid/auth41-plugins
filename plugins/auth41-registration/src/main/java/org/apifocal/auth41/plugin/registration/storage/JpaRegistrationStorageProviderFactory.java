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
 * <p>Registers JPA entities and creates provider instances.
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
        // No post-init needed
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
        return "META-INF/registration-changelog.xml";
    }

    @Override
    public String getFactoryId() {
        return PROVIDER_ID;
    }
}
