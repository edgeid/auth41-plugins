package org.apifocal.auth41.plugin.registration.storage;

import org.apifocal.auth41.plugin.registration.entity.InviteTokenEntity;
import org.apifocal.auth41.plugin.registration.entity.RegistrationRequestEntity;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.hibernate.Session;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for JpaRegistrationStorageProvider.
 *
 * <p>Registers JPA entities and creates provider instances.
 * <p>Includes fallback mechanism to ensure tables are created even if
 * Keycloak's automatic Liquibase execution doesn't work in dev mode.
 */
public class JpaRegistrationStorageProviderFactory
        implements RegistrationStorageProviderFactory, JpaEntityProvider {

    private static final Logger logger = Logger.getLogger(JpaRegistrationStorageProviderFactory.class);
    public static final String PROVIDER_ID = "auth41-jpa-registration";

    // Track initialization to prevent duplicate execution
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    // Table names to check
    private static final String[] REQUIRED_TABLES = {
        "auth41_invite_tokens",
        "auth41_registration_requests"
    };

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
        // postInit is called after all providers are initialized and database is ready
        if (initialized.compareAndSet(false, true)) {
            ensureTablesExist(factory);
        }
    }

    @Override
    public void close() {
        logger.info("Closing Auth41 Registration Storage Provider");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Ensure required database tables exist.
     * This is a fallback mechanism for when Keycloak's automatic Liquibase
     * execution doesn't work (e.g., in development mode).
     */
    private void ensureTablesExist(KeycloakSessionFactory factory) {
        KeycloakModelUtils.runJobInTransaction(factory, session -> {
            try {
                // Get EntityManager and access underlying JDBC connection via Hibernate
                JpaConnectionProvider jpaProvider = session.getProvider(JpaConnectionProvider.class);
                EntityManager em = jpaProvider.getEntityManager();
                Session hibernateSession = em.unwrap(Session.class);

                // Execute database operations with direct JDBC connection
                hibernateSession.doWork(connection -> {
                    try {
                        // Check if tables already exist
                        boolean allTablesExist = checkTablesExist(connection);

                        if (allTablesExist) {
                            logger.info("Auth41 Registration tables already exist - skipping initialization");
                            return;
                        }

                        logger.warn("Auth41 Registration tables not found - executing manual Liquibase update");
                        executeLiquibaseChangelog(connection);
                        logger.info("Auth41 Registration tables created successfully");

                    } catch (Exception e) {
                        logger.error("Failed to ensure Auth41 Registration tables exist", e);
                        // Don't rethrow - allow Keycloak to continue starting
                    }
                });

            } catch (Exception e) {
                logger.error("Failed to access database for table initialization", e);
            }
        });
    }

    /**
     * Check if all required tables exist in the database.
     */
    private boolean checkTablesExist(Connection connection) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();

        for (String tableName : REQUIRED_TABLES) {
            if (!tableExists(metaData, tableName)) {
                logger.debugf("Table %s does not exist", tableName);
                return false;
            }
        }

        logger.debugf("All required tables exist: %s", String.join(", ", REQUIRED_TABLES));
        return true;
    }

    /**
     * Check if a specific table exists.
     * Handles case-sensitivity across different databases.
     */
    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws Exception {
        // Try different case variations as databases handle case differently
        String[] variations = {
            tableName.toUpperCase(),
            tableName.toLowerCase(),
            tableName
        };

        for (String variation : variations) {
            try (ResultSet rs = metaData.getTables(null, null, variation, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Execute DDL to create tables.
     * Executes SQL directly within the existing transaction managed by Keycloak.
     */
    private void executeLiquibaseChangelog(Connection connection) throws Exception {
        // Execute DDL directly - we're already in a Keycloak-managed transaction
        try (java.sql.Statement stmt = connection.createStatement()) {
            // Create invite tokens table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS auth41_invite_tokens (" +
                "  invite_token VARCHAR(255) NOT NULL PRIMARY KEY, " +
                "  ip_address VARCHAR(45) NOT NULL, " +
                "  realm_id VARCHAR(255) NOT NULL, " +
                "  created_at TIMESTAMP NOT NULL, " +
                "  expires_at TIMESTAMP NOT NULL, " +
                "  used_at TIMESTAMP, " +
                "  used BOOLEAN NOT NULL" +
                ")"
            );

            // Create index for rate limiting (ignore if exists)
            try {
                stmt.execute(
                    "CREATE INDEX idx_ip_created " +
                    "ON auth41_invite_tokens (ip_address, created_at)"
                );
            } catch (Exception e) {
                // Ignore if index already exists
                logger.debugf("Index idx_ip_created may already exist: %s", e.getMessage());
            }

            // Create registration requests table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS auth41_registration_requests (" +
                "  request_id VARCHAR(255) NOT NULL PRIMARY KEY, " +
                "  email VARCHAR(255) NOT NULL, " +
                "  realm_id VARCHAR(255) NOT NULL, " +
                "  attributes TEXT, " +
                "  status VARCHAR(50) NOT NULL, " +
                "  created_at TIMESTAMP NOT NULL, " +
                "  approved_at TIMESTAMP, " +
                "  expires_at TIMESTAMP NOT NULL, " +
                "  user_id VARCHAR(255)" +
                ")"
            );

            // Create index for approval processor (ignore if exists)
            try {
                stmt.execute(
                    "CREATE INDEX idx_status_created " +
                    "ON auth41_registration_requests (status, created_at)"
                );
            } catch (Exception e) {
                // Ignore if index already exists
                logger.debugf("Index idx_status_created may already exist: %s", e.getMessage());
            }

            logger.info("Auth41 Registration tables created successfully via direct DDL");
        }
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
