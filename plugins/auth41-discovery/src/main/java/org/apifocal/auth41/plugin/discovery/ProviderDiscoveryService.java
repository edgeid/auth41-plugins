package org.apifocal.auth41.plugin.discovery;

import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.keycloak.provider.Provider;

import java.time.Duration;
import java.util.Set;

/**
 * Service Provider Interface for discovering which provider(s) have a user's account.
 *
 * <p>This service queries the auth41-accounts storage to find user-to-provider associations
 * and provides caching for performance. It also supports CIBA-aware discovery to find
 * providers that support Client Initiated Backchannel Authentication.
 *
 * <p>Example usage:
 * <pre>{@code
 * ProviderDiscoveryService discovery = session.getProvider(ProviderDiscoveryService.class);
 * Set<String> providers = discovery.findProvidersByUser("user@example.com");
 * }</pre>
 */
public interface ProviderDiscoveryService extends Provider {

    /**
     * Find which provider(s) have this user's account.
     *
     * <p>This method first checks the cache, then queries the accounts storage if not cached.
     * Results are automatically cached with a default TTL.
     *
     * @param userIdentifier Email address or decentralized identifier (DID)
     * @return Set of provider IDs that have this user's account (empty if none found)
     */
    Set<String> findProvidersByUser(String userIdentifier);

    /**
     * Find which provider(s) have this user's account by email.
     *
     * <p>Similar to {@link #findProvidersByUser(String)} but specifically queries by email.
     * This is useful when you know the identifier is an email address.
     *
     * @param email User's email address
     * @return Set of provider IDs that have this user's account (empty if none found)
     */
    Set<String> findProvidersByEmail(String email);

    /**
     * Find user's home provider that supports CIBA (Client Initiated Backchannel Authentication).
     *
     * <p>This method:
     * <ol>
     *   <li>Finds all providers that have the user's account</li>
     *   <li>Checks which providers support CIBA in their metadata</li>
     *   <li>Validates there's a valid trust path from currentProvider to the CIBA provider</li>
     *   <li>Returns the first CIBA-capable provider found</li>
     * </ol>
     *
     * @param userIdentifier Email or DID
     * @param currentProvider Current provider making the request
     * @param network Trust network to validate paths
     * @return Provider ID that supports CIBA, or null if no CIBA-capable provider found
     */
    String findCibaHomeProvider(String userIdentifier, String currentProvider, TrustNetwork network);

    /**
     * Cache a user-to-provider association.
     *
     * <p>Stores the association in Keycloak's Infinispan cache with the specified TTL.
     * This improves performance by reducing database queries.
     *
     * @param userIdentifier Email or DID
     * @param providerId Provider ID
     * @param ttl Time-to-live for the cache entry
     */
    void cacheAssociation(String userIdentifier, String providerId, Duration ttl);

    /**
     * Clear cached association for a user.
     *
     * <p>This should be called when a user's home provider changes (e.g., after account migration).
     *
     * @param userIdentifier Email or DID
     */
    void clearCache(String userIdentifier);

    /**
     * Clear all cached associations.
     *
     * <p>This is useful for testing or when trust network configuration changes significantly.
     */
    void clearAllCache();
}
