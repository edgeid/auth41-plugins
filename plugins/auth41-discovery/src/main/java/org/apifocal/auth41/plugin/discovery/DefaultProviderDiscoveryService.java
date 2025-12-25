package org.apifocal.auth41.plugin.discovery;

import org.apifocal.auth41.plugin.accounts.AccountStorageProvider;
import org.apifocal.auth41.plugin.accounts.UserAccount;
import org.apifocal.auth41.plugin.topology.TopologyProvider;
import org.apifocal.auth41.plugin.topology.TrustPath;
import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of ProviderDiscoveryService.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Queries auth41-accounts storage for user-to-provider associations</li>
 *   <li>Caches results in-memory (will use Keycloak Infinispan in production)</li>
 *   <li>Validates CIBA support via provider metadata</li>
 *   <li>Uses topology provider to validate trust paths</li>
 * </ul>
 */
public class DefaultProviderDiscoveryService implements ProviderDiscoveryService {

    private static final Logger logger = Logger.getLogger(DefaultProviderDiscoveryService.class);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
    private static final String CIBA_METADATA_KEY = "ciba_supported";

    private final KeycloakSession session;
    // Simple in-memory cache - in production this would use Keycloak's Infinispan
    private final Map<String, CachedAssociation> cache = new ConcurrentHashMap<>();

    public DefaultProviderDiscoveryService(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Set<String> findProvidersByUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.trim().isEmpty()) {
            return Collections.emptySet();
        }

        // Check cache first
        CachedAssociation cached = cache.get(userIdentifier);
        if (cached != null && !cached.isExpired()) {
            logger.debugf("Cache hit for user: %s -> %s", userIdentifier, cached.getProviderId());
            return Set.of(cached.getProviderId());
        }

        // Query accounts storage
        AccountStorageProvider accountStorage = session.getProvider(AccountStorageProvider.class);
        if (accountStorage == null) {
            logger.warn("AccountStorageProvider not available");
            return Collections.emptySet();
        }

        UserAccount account = accountStorage.getAccount(userIdentifier);
        if (account == null) {
            logger.debugf("No account found for user: %s", userIdentifier);
            return Collections.emptySet();
        }

        String providerId = account.getHomeProviderId();
        logger.debugf("Found provider for user %s: %s", userIdentifier, providerId);

        // Cache the result
        cacheAssociation(userIdentifier, providerId, DEFAULT_CACHE_TTL);

        return Set.of(providerId);
    }

    @Override
    public Set<String> findProvidersByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Collections.emptySet();
        }

        // Check cache first
        CachedAssociation cached = cache.get(email);
        if (cached != null && !cached.isExpired()) {
            logger.debugf("Cache hit for email: %s -> %s", email, cached.getProviderId());
            return Set.of(cached.getProviderId());
        }

        // Query accounts storage by email
        AccountStorageProvider accountStorage = session.getProvider(AccountStorageProvider.class);
        if (accountStorage == null) {
            logger.warn("AccountStorageProvider not available");
            return Collections.emptySet();
        }

        UserAccount account = accountStorage.getAccountByEmail(email);
        if (account == null) {
            logger.debugf("No account found for email: %s", email);
            return Collections.emptySet();
        }

        String providerId = account.getHomeProviderId();
        logger.debugf("Found provider for email %s: %s", email, providerId);

        // Cache the result
        cacheAssociation(email, providerId, DEFAULT_CACHE_TTL);

        return Set.of(providerId);
    }

    @Override
    public String findCibaHomeProvider(String userIdentifier, String currentProvider, TrustNetwork network) {
        if (userIdentifier == null || currentProvider == null || network == null) {
            return null;
        }

        // Find all providers for this user
        Set<String> providers = findProvidersByUser(userIdentifier);
        if (providers.isEmpty()) {
            logger.debugf("No providers found for user: %s", userIdentifier);
            return null;
        }

        // Get topology provider
        TopologyProvider topology = session.getProvider(TopologyProvider.class);
        if (topology == null) {
            logger.warn("TopologyProvider not available");
            return null;
        }

        // Find first provider that supports CIBA and has valid path
        for (String providerId : providers) {
            ProviderNode provider = network.getProviders().get(providerId);
            if (provider == null) {
                logger.debugf("Provider %s not found in trust network", providerId);
                continue;
            }

            // Check if provider supports CIBA
            if (!isCibaSupported(provider)) {
                logger.debugf("Provider %s does not support CIBA", providerId);
                continue;
            }

            // Validate trust path from current provider to home provider
            TrustPath path = topology.computeTrustPath(network, currentProvider, providerId);
            if (!path.isReachable()) {
                logger.debugf("No valid trust path from %s to %s", currentProvider, providerId);
                continue;
            }

            // For CIBA, limit to direct connections (1 hop) or single intermediary (2 hops)
            // 1 hop = source → target (direct)
            // 2 hops = source → intermediary → target
            if (path.getHopCount() > 2) {
                logger.debugf("Trust path from %s to %s has too many hops for CIBA (%d), max allowed is 2",
                    currentProvider, providerId, path.getHopCount());
                continue;
            }

            logger.debugf("Found CIBA home provider for user %s: %s", userIdentifier, providerId);
            return providerId;
        }

        logger.debugf("No CIBA-capable provider found for user: %s", userIdentifier);
        return null;
    }

    @Override
    public void cacheAssociation(String userIdentifier, String providerId, Duration ttl) {
        if (userIdentifier == null || providerId == null || ttl == null) {
            return;
        }

        long expirationTime = System.currentTimeMillis() + ttl.toMillis();
        cache.put(userIdentifier, new CachedAssociation(providerId, expirationTime));
        logger.debugf("Cached association: %s -> %s (TTL: %s)", userIdentifier, providerId, ttl);
    }

    @Override
    public void clearCache(String userIdentifier) {
        if (userIdentifier == null) {
            return;
        }

        cache.remove(userIdentifier);
        logger.debugf("Cleared cache for user: %s", userIdentifier);
    }

    @Override
    public void clearAllCache() {
        int size = cache.size();
        cache.clear();
        logger.debugf("Cleared all cached associations (%d entries)", size);
    }

    @Override
    public void close() {
        // No resources to close
    }

    /**
     * Check if a provider supports CIBA based on its attributes.
     */
    private boolean isCibaSupported(ProviderNode provider) {
        Map<String, String> attributes = provider.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return false;
        }

        String cibaSupport = attributes.get(CIBA_METADATA_KEY);
        if (cibaSupport == null) {
            return false;
        }

        // Check for truthy string values
        String value = cibaSupport.toLowerCase();
        return "true".equals(value) || "yes".equals(value) || "1".equals(value);
    }

    /**
     * Simple cache entry for user-to-provider associations.
     */
    private static class CachedAssociation {
        private final String providerId;
        private final long expirationTime;

        CachedAssociation(String providerId, long expirationTime) {
            this.providerId = providerId;
            this.expirationTime = expirationTime;
        }

        String getProviderId() {
            return providerId;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}
