package org.apifocal.auth41.plugin.accounts;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.List;

/**
 * Factory for JpaAccountStorageProvider.
 *
 * <p>Registers JPA entity and creates provider instances.
 */
public class JpaAccountStorageProviderFactory
    implements AccountStorageProviderFactory, JpaEntityProvider {

    private static final Logger logger = Logger.getLogger(JpaAccountStorageProviderFactory.class);
    public static final String PROVIDER_ID = "auth41-jpa-accounts";

    @Override
    public AccountStorageProvider create(KeycloakSession session) {
        return new JpaAccountStorageProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing Auth41 Accounts Storage Provider");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-init needed
    }

    @Override
    public void close() {
        logger.info("Closing Auth41 Accounts Storage Provider");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    // JpaEntityProvider implementation

    @Override
    public List<Class<?>> getEntities() {
        return List.of(UserAccountEntity.class);
    }

    @Override
    public String getChangelogLocation() {
        // We'll use Liquibase changelog for schema management
        return "META-INF/auth41-accounts-changelog.xml";
    }

    @Override
    public String getFactoryId() {
        return PROVIDER_ID;
    }
}
