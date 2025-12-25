package org.apifocal.auth41.plugin.accounts;

import org.keycloak.provider.Provider;

import java.util.List;
import java.util.Set;

/**
 * SPI for user account storage and provider association tracking.
 *
 * <p>This provider stores user account records and tracks which provider
 * has custody of each user's account. Used by the discovery service to
 * route authentication requests to the correct home provider.
 *
 * <p>Implementations should:
 * <ul>
 *   <li>Provide CRUD operations for user accounts
 *   <li>Track user-to-provider associations
 *   <li>Support lookups by email and user identifier
 *   <li>Handle concurrent access safely
 * </ul>
 */
public interface AccountStorageProvider extends Provider {

    /**
     * Create a new user account record.
     *
     * @param account User account to create
     * @throws IllegalArgumentException if account with same identifier already exists
     */
    void createAccount(UserAccount account);

    /**
     * Get user account by identifier.
     *
     * @param userIdentifier Unique user identifier (email or DID)
     * @return User account or null if not found
     */
    UserAccount getAccount(String userIdentifier);

    /**
     * Get user account by email address.
     *
     * @param email User's email address
     * @return User account or null if not found
     */
    UserAccount getAccountByEmail(String email);

    /**
     * Update existing user account.
     *
     * @param account Updated account information
     * @throws IllegalArgumentException if account doesn't exist
     */
    void updateAccount(UserAccount account);

    /**
     * Delete user account.
     *
     * @param userIdentifier User identifier
     * @return true if deleted, false if not found
     */
    boolean deleteAccount(String userIdentifier);

    /**
     * Find all accounts associated with a provider.
     *
     * @param providerId Provider ID
     * @return List of user accounts at this provider
     */
    List<UserAccount> findAccountsByProvider(String providerId);

    /**
     * Find providers that have an account for this user (by email).
     *
     * <p>Used for user discovery - which provider(s) have this user's account?
     *
     * @param userEmail User's email address
     * @return Set of provider IDs that have this user
     */
    Set<String> findProvidersByEmail(String userEmail);

    /**
     * Find providers that have an account for this user (by identifier).
     *
     * @param userIdentifier User identifier (email or DID)
     * @return Set of provider IDs that have this user
     */
    Set<String> findProvidersByUser(String userIdentifier);

    /**
     * Check if a user account exists.
     *
     * @param userIdentifier User identifier
     * @return true if account exists
     */
    boolean accountExists(String userIdentifier);

    /**
     * Get total count of user accounts.
     *
     * @return Total number of accounts
     */
    long getAccountCount();

    /**
     * Get count of accounts for a specific provider.
     *
     * @param providerId Provider ID
     * @return Number of accounts at this provider
     */
    long getAccountCountByProvider(String providerId);
}
