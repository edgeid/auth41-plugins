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
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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

    // Track initialization state - null means not yet attempted, true means success, false means failure
    private static volatile Boolean initializationSuccessful = null;
    private static volatile String initializationError = null;

    // Table names to check
    private static final String[] REQUIRED_TABLES = {
        "auth41_invite_tokens",
        "auth41_registration_requests"
    };

    @Override
    public RegistrationStorageProvider create(KeycloakSession session) {
        // Fail fast if initialization failed
        if (Boolean.FALSE.equals(initializationSuccessful)) {
            throw new IllegalStateException(
                "Auth41 Registration Storage Provider is not available due to initialization failure: " +
                initializationError
            );
        }
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
     * Check if initialization was successful.
     * Can be used for health checks or monitoring.
     *
     * @return true if initialized successfully, false if failed, null if not yet attempted
     */
    public static Boolean isInitializationSuccessful() {
        return initializationSuccessful;
    }

    /**
     * Get initialization error message if initialization failed.
     *
     * @return error message if initialization failed, null otherwise
     */
    public static String getInitializationError() {
        return initializationError;
    }

    /**
     * Ensure required database tables exist.
     * This is a fallback mechanism for when Keycloak's automatic Liquibase
     * execution doesn't work (e.g., in development mode).
     */
    private void ensureTablesExist(KeycloakSessionFactory factory) {
        try {
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
                                initializationSuccessful = true;
                                return;
                            }

                            logger.warn("Auth41 Registration tables not found - executing manual Liquibase update");
                            executeLiquibaseChangelog(connection);
                            logger.info("Auth41 Registration tables created successfully");
                            initializationSuccessful = true;

                        } catch (Exception e) {
                            initializationSuccessful = false;
                            initializationError = "Failed to create database tables: " + e.getMessage();
                            logger.error("CRITICAL: Failed to ensure Auth41 Registration tables exist. " +
                                       "The plugin will not function correctly until tables are created.", e);
                            throw new RuntimeException("Database initialization failed", e);
                        }
                    });

                } catch (Exception e) {
                    initializationSuccessful = false;
                    initializationError = "Failed to access database: " + e.getMessage();
                    logger.error("CRITICAL: Failed to access database for table initialization. " +
                               "The plugin will not function correctly.", e);
                    throw new RuntimeException("Database access failed", e);
                }
            });
        } catch (Exception e) {
            // Log the final error state
            logger.error("Auth41 Registration Storage Provider initialization FAILED. " +
                       "All operations will fail until this is resolved.", e);
            // Set failure state if not already set
            if (initializationSuccessful == null) {
                initializationSuccessful = false;
                initializationError = "Initialization transaction failed: " + e.getMessage();
            }
        }
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
     * Execute Liquibase changelog to create tables.
     * Uses Liquibase for database-agnostic schema management.
     */
    private void executeLiquibaseChangelog(Connection connection) throws Exception {
        // Wrap the connection in a Liquibase JDBC connection
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));

        // Create Liquibase instance with our changelog
        try (Liquibase liquibase = new Liquibase(
                "META-INF/registration-changelog.xml",
                new ClassLoaderResourceAccessor(),
                database)) {

            // Execute the changelog
            liquibase.update(new Contexts());

            logger.info("Auth41 Registration tables created successfully via Liquibase");
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
