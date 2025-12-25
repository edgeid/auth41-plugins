# Auth41 Discovery Plugin

Provider discovery service for Auth41 federation - discovers which provider has a user's account and supports CIBA-aware discovery.

## Overview

The auth41-discovery plugin implements a Service Provider Interface (SPI) for discovering which identity provider(s) have a user's account in a federated authentication environment. It queries the auth41-accounts storage and provides caching for performance optimization.

### Key Features

- **User-to-Provider Discovery**: Find which provider(s) have a user's account by identifier or email
- **CIBA Support**: Special discovery logic for Client Initiated Backchannel Authentication
- **In-Memory Caching**: Reduces database queries with configurable TTL caching
- **Trust Path Validation**: Validates trust relationships between providers
- **Multiple Lookup Methods**: Supports lookup by user identifier or email address

## Architecture

```
┌─────────────────────────────────────────┐
│  ProviderDiscoveryService SPI           │
├─────────────────────────────────────────┤
│  + findProvidersByUser()                │
│  + findProvidersByEmail()               │
│  + findCibaHomeProvider()               │
│  + cacheAssociation()                   │
│  + clearCache()                         │
└─────────────────────────────────────────┘
                    ↑
                    │ implements
                    │
┌─────────────────────────────────────────┐
│  DefaultProviderDiscoveryService        │
├─────────────────────────────────────────┤
│  - In-memory cache (ConcurrentHashMap)  │
│  - Queries AccountStorageProvider       │
│  - Validates CIBA support               │
│  - Checks trust paths                   │
└─────────────────────────────────────────┘
                    │
                    │ depends on
                    ↓
┌──────────────────────┐  ┌──────────────────────┐
│ AccountStorageProvider│  │ TopologyProvider    │
└──────────────────────┘  └──────────────────────┘
```

## API Reference

### ProviderDiscoveryService

Main SPI interface for provider discovery operations.

#### Methods

**findProvidersByUser(String userIdentifier)**
- Finds which provider(s) have this user's account
- Checks cache first, then queries storage
- Automatically caches results with default 1-hour TTL
- Returns empty set if user not found

**findProvidersByEmail(String email)**
- Similar to `findProvidersByUser` but specifically for email lookups
- Useful when you know the identifier is an email address

**findCibaHomeProvider(String userIdentifier, String currentProvider, TrustNetwork network)**
- Finds user's home provider that supports CIBA
- Validates CIBA support in provider attributes
- Checks trust path from current provider to home provider
- Requires path with ≤2 hops for CIBA compatibility
- Returns null if no CIBA-capable provider found

**cacheAssociation(String userIdentifier, String providerId, Duration ttl)**
- Manually cache a user-to-provider association
- Useful for pre-warming cache or custom caching strategies

**clearCache(String userIdentifier)**
- Clear cached association for a specific user
- Call after user migration or provider changes

**clearAllCache()**
- Clear all cached associations
- Useful for testing or configuration changes

## Usage Examples

### Basic Provider Discovery

```java
// Get the discovery service
ProviderDiscoveryService discovery = session.getProvider(ProviderDiscoveryService.class);

// Find which provider has this user
Set<String> providers = discovery.findProvidersByUser("alice@example.com");
if (!providers.isEmpty()) {
    String homeProvider = providers.iterator().next();
    System.out.println("User's home provider: " + homeProvider);
}
```

### Email-Based Discovery

```java
// Look up by email specifically
Set<String> providers = discovery.findProvidersByEmail("bob@example.com");
```

### CIBA-Aware Discovery

```java
// Find CIBA-capable home provider
TrustNetwork network = session.getProvider(TrustNetworkProvider.class).getTrustNetwork();
String cibaProvider = discovery.findCibaHomeProvider(
    "carol@example.com",
    "current-provider-id",
    network
);

if (cibaProvider != null) {
    // Initiate CIBA flow to home provider
    initiateCibaAuthentication(cibaProvider);
} else {
    // Fall back to standard federation flow
    redirectToHomeProvider();
}
```

### Manual Cache Management

```java
// Pre-warm cache for frequently accessed users
discovery.cacheAssociation("vip@example.com", "provider-a", Duration.ofHours(24));

// Clear cache after user migration
discovery.clearCache("migrated@example.com");

// Clear all cache after trust network update
discovery.clearAllCache();
```

## CIBA Support

### How CIBA Discovery Works

1. **Find User's Providers**: Query accounts storage for user's home provider
2. **Check CIBA Support**: Look for `ciba_supported=true` in provider attributes
3. **Validate Trust Path**: Compute trust path from current provider to home provider
4. **Check Hop Count**: Ensure path has ≤2 hops (direct or single intermediary)
5. **Return Provider**: First provider matching all criteria, or null

### Configuring CIBA Support

Providers indicate CIBA support via attributes in the trust network:

```java
ProviderNode provider = ProviderNode.builder()
    .providerId("provider-a")
    .issuer("https://provider-a.example.com")
    .attribute("ciba_supported", "true")  // Enable CIBA
    .build();
```

Accepted values for `ciba_supported`:
- `"true"`, `"TRUE"`, `"yes"`, `"YES"`, `"1"` → CIBA enabled
- Any other value → CIBA disabled

## Caching Strategy

### Cache Implementation

- **Storage**: In-memory `ConcurrentHashMap` (thread-safe)
- **Default TTL**: 1 hour
- **Eviction**: Time-based (checked on access)
- **Future**: Will use Keycloak's Infinispan cache in production

### Cache Benefits

- **Reduced Database Load**: Avoids repeated queries for same user
- **Improved Performance**: Sub-millisecond cache lookups
- **Scalability**: Handles high query volumes

### Cache Invalidation

Cache should be cleared when:
- User migrates to a different provider
- Provider is removed from trust network
- Trust relationships change
- Testing/debugging discovery logic

## Dependencies

### Runtime Dependencies
- `auth41-accounts` - User account storage
- `auth41-trust-network` - Trust network data model
- `auth41-topology` - Trust path computation
- Keycloak Server SPI

### Build Dependencies
- Java 17+
- Maven 3.8+

## Building

```bash
# Build just this plugin
mvn clean install -pl plugins/auth41-discovery

# Build with all dependencies
mvn clean install
```

## Testing

```bash
# Run all tests
mvn test -pl plugins/auth41-discovery

# Run specific test
mvn test -pl plugins/auth41-discovery -Dtest=DefaultProviderDiscoveryServiceTest
```

### Test Coverage

- 23 unit tests covering all discovery scenarios
- Cache behavior (hits, misses, eviction)
- CIBA discovery logic
- Null safety and edge cases
- Provider not found scenarios
- Trust path validation

## Deployment

1. Build the JAR: `mvn clean install`
2. Copy to Keycloak: `cp target/auth41-discovery-*.jar $KEYCLOAK_HOME/providers/`
3. Rebuild Keycloak: `$KEYCLOAK_HOME/bin/kc.sh build`
4. Restart Keycloak

## Integration Points

### Used By

- `auth41-federation-broker` - Uses discovery to route authentication requests
- `auth41-handoff` - Finds target provider for user migration
- CIBA flows - Discovers CIBA-capable home providers

### Uses

- `auth41-accounts` - Queries user account storage
- `auth41-trust-network` - Gets provider metadata and attributes
- `auth41-topology` - Validates trust paths

## Performance Considerations

### Cache Hit Ratio

- Expected cache hit ratio: >90% for typical workloads
- Cache warm-up time: Immediate (on first access per user)
- Memory footprint: ~1KB per cached association

### Query Performance

- Cache lookup: <1ms
- Storage query (cache miss): 5-20ms (depends on database)
- CIBA discovery with path validation: 10-50ms

### Scalability

- Cache is local to each Keycloak instance
- No cross-node cache synchronization (stateless)
- Horizontal scaling: Linear (independent caches per node)

## Troubleshooting

### User Not Found

**Symptom**: `findProvidersByUser()` returns empty set

**Causes**:
- User account not in auth41-accounts storage
- Incorrect user identifier (case-sensitive)
- Account storage provider not configured

**Solution**: Verify user exists in accounts storage

### CIBA Discovery Returns Null

**Symptom**: `findCibaHomeProvider()` returns null

**Causes**:
- Provider doesn't have `ciba_supported=true` attribute
- No valid trust path from current to home provider
- Trust path has >2 hops
- Topology provider not configured

**Solution**: Check provider attributes and trust network configuration

### Cache Not Working

**Symptom**: Seeing database queries on every request

**Check**:
1. Verify cache entries with logging
2. Check cache TTL hasn't expired
3. Ensure same identifier is being used
4. Verify cache isn't being cleared between requests

## Future Enhancements

- [ ] Migrate to Keycloak Infinispan for distributed caching
- [ ] Add metrics for cache hit/miss ratios
- [ ] Support for multi-provider users (primary/secondary)
- [ ] Configurable CIBA hop limit
- [ ] Provider preference ranking for CIBA selection
- [ ] Discovery result pagination for large result sets

## License

Copyright © 2025 APIFocal
