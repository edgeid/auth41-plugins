# Discovery Plugin

The Discovery plugin locates a user's home identity provider based on their identifier (email or username).

## Overview

**Plugin Name**: `auth41-discovery`
**SPI**: Custom `DiscoveryProvider` and `DiscoveryProviderFactory`
**Purpose**: Discover which provider holds a user's account

The Discovery plugin answers the question: "Where should this user authenticate?" It uses multiple discovery methods to find the user's home identity provider.

## Key Concepts

### Home Provider

The identity provider where a user has their primary account and authenticates.

Example:
- User: `alice@university-a.edu`
- Home Provider: University A's Keycloak instance
- Service Provider: University Consortium Hub

### Discovery Methods

Auth41 supports multiple discovery methods, tried in priority order:

1. **User Attribute** - Check existing user's `home_provider` attribute
2. **Email Domain Mapping** - Match email domain to provider
3. **WebFinger** - Query WebFinger endpoint
4. **Fallback Provider** - Use configured default provider

## Discovery Methods

### 1. User Attribute Discovery

For existing users with linked accounts, check user attributes:

```java
UserModel user = session.users().getUserByEmail(realm, "alice@university-a.edu");
if (user != null) {
    String homeProvider = user.getFirstAttribute("home_provider");
    if (homeProvider != null) {
        return getProviderInfo(homeProvider);
    }
}
```

**Advantages**:
- Fastest method (database lookup)
- Works for users with existing shadow accounts
- Explicit user-to-provider binding

**Limitations**:
- Only works for existing users
- Requires previous authentication or manual setup

### 2. Email Domain Mapping

Match email domain to provider configuration:

```json
{
  "provider_id": "university-a",
  "issuer": "https://sso.university-a.edu/realms/students",
  "discovery": {
    "email_domains": ["university-a.edu", "univ-a.edu", "alumni.univ-a.edu"]
  }
}
```

```java
String email = "alice@university-a.edu";
String domain = extractDomain(email);  // "university-a.edu"

for (ProviderInfo provider : trustNetwork.getAllProviders()) {
    List<String> domains = provider.getDiscovery().getEmailDomains();
    if (domains.contains(domain)) {
        return provider;
    }
}
```

**Advantages**:
- Works for first-time users
- No external dependencies
- Fast (in-memory lookup)
- Handles multiple domains per provider

**Limitations**:
- Requires pre-configuration of domains
- Doesn't handle dynamic provider changes

### 3. WebFinger Discovery

Query WebFinger endpoint at user's domain:

**Request**:
```http
GET /.well-known/webfinger?resource=acct:alice@university-a.edu
Host: university-a.edu
```

**Response**:
```json
{
  "subject": "acct:alice@university-a.edu",
  "links": [
    {
      "rel": "http://openid.net/specs/connect/1.0/issuer",
      "href": "https://sso.university-a.edu/realms/students"
    }
  ]
}
```

**Discovery Logic**:
```java
String email = "alice@university-a.edu";
String domain = extractDomain(email);

WebFingerResponse response = queryWebFinger(domain, email);
String issuer = response.getIssuer();

// Find provider with matching issuer
for (ProviderInfo provider : trustNetwork.getAllProviders()) {
    if (provider.getIssuer().equals(issuer)) {
        return provider;
    }
}
```

**Advantages**:
- Standard protocol (RFC 7033)
- Dynamic discovery (no pre-configuration)
- Works with any compliant provider

**Limitations**:
- Requires HTTP request (latency)
- External dependency (DNS, network)
- Requires WebFinger server at domain

### 4. Fallback Provider

Use configured default provider when other methods fail:

```bash
-Dauth41.discovery.fallback-provider=default-hub
```

**Use Cases**:
- Unknown email domains
- WebFinger unavailable
- First-time users without domain configuration

## API Reference

### DiscoveryProvider SPI

```java
public interface DiscoveryProvider extends Provider {
    /**
     * Discover home provider for user identifier
     *
     * @param userIdentifier email or username
     * @return provider info, or null if not found
     */
    ProviderInfo discoverProvider(String userIdentifier);

    /**
     * Discover provider with hint
     *
     * @param userIdentifier email or username
     * @param hint login_hint from OIDC request
     * @return provider info, or null if not found
     */
    ProviderInfo discoverProvider(String userIdentifier, String hint);

    /**
     * Check if provider can be discovered for identifier
     *
     * @param userIdentifier email or username
     * @return true if discoverable
     */
    boolean canDiscover(String userIdentifier);
}
```

### Usage Example

```java
public class FederationBroker implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String username = context.getUser().getUsername();

        // Get discovery provider
        DiscoveryProvider discovery =
            context.getSession().getProvider(DiscoveryProvider.class);

        // Discover home provider
        ProviderInfo homeProvider = discovery.discoverProvider(username);

        if (homeProvider == null) {
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        // Redirect to home provider for authentication
        redirectToProvider(context, homeProvider);
    }
}
```

## Configuration

### Email Domain Configuration

Add domains to provider configuration in trust network:

```json
{
  "providers": {
    "university-a": {
      "provider_id": "university-a",
      "issuer": "https://sso.university-a.edu/realms/students",
      "discovery": {
        "email_domains": [
          "university-a.edu",
          "univ-a.edu",
          "alumni.university-a.edu"
        ],
        "webfinger_enabled": false
      }
    }
  }
}
```

### WebFinger Configuration

Enable WebFinger per provider:

```json
{
  "discovery": {
    "webfinger_enabled": true,
    "webfinger_timeout": 5000
  }
}
```

### System Properties

```bash
# Enable WebFinger globally
-Dauth41.discovery.webfinger.enabled=true

# WebFinger timeout (milliseconds)
-Dauth41.discovery.webfinger.timeout=5000

# Fallback provider when discovery fails
-Dauth41.discovery.fallback-provider=default-hub

# Cache TTL (seconds)
-Dauth41.discovery.cache.ttl=300

# Cache max size
-Dauth41.discovery.cache.max-size=1000
```

## Caching

Discovery results are cached to improve performance:

### Cache Key

```java
String cacheKey = userIdentifier.toLowerCase();  // email or username
```

### Cache Entry

```java
public class DiscoveryCacheEntry {
    private final ProviderInfo provider;
    private final Instant timestamp;
    private final DiscoveryMethod method;

    public boolean isExpired(Duration ttl) {
        return Instant.now().isAfter(timestamp.plus(ttl));
    }
}
```

### Cache Operations

```java
// Check cache
DiscoveryCacheEntry cached = cache.get(userIdentifier);
if (cached != null && !cached.isExpired(cacheTtl)) {
    return cached.getProvider();
}

// Perform discovery
ProviderInfo provider = performDiscovery(userIdentifier);

// Cache result
cache.put(userIdentifier, new DiscoveryCacheEntry(
    provider,
    Instant.now(),
    DiscoveryMethod.EMAIL_DOMAIN
));
```

### Cache Invalidation

- TTL-based expiration (default: 5 minutes)
- Manual invalidation when trust network changes
- LRU eviction when cache full

## Login Hints

OIDC `login_hint` parameter can provide discovery hints:

### Email Hint

```
https://hub.example.com/auth?login_hint=alice@university-a.edu
```

Extract email and use for discovery:

```java
String loginHint = context.getAuthenticationSession().getClientNote("login_hint");
if (loginHint != null && isEmail(loginHint)) {
    return discoverByEmail(loginHint);
}
```

### Provider Hint

```
https://hub.example.com/auth?login_hint=provider:university-a
```

Extract provider ID directly:

```java
if (loginHint.startsWith("provider:")) {
    String providerId = loginHint.substring("provider:".length());
    return trustNetwork.getProvider(providerId);
}
```

### Issuer Hint

```
https://hub.example.com/auth?login_hint=issuer:https://sso.university-a.edu/realms/students
```

Find provider by issuer:

```java
if (loginHint.startsWith("issuer:")) {
    String issuer = loginHint.substring("issuer:".length());
    for (ProviderInfo provider : trustNetwork.getAllProviders()) {
        if (provider.getIssuer().equals(issuer)) {
            return provider;
        }
    }
}
```

## Data Model

### ProviderInfo

Provider information returned by discovery:

```java
public class ProviderInfo {
    private final String providerId;
    private final String issuer;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String jwksUri;
    private final DiscoveryConfig discovery;

    // Getters...
}
```

### DiscoveryConfig

Discovery configuration for a provider:

```java
public class DiscoveryConfig {
    private final List<String> emailDomains;
    private final boolean webfingerEnabled;
    private final int webfingerTimeout;

    // Getters...
}
```

### DiscoveryMethod Enum

```java
public enum DiscoveryMethod {
    USER_ATTRIBUTE,    // Existing user attribute
    EMAIL_DOMAIN,      // Email domain mapping
    WEBFINGER,         // WebFinger query
    LOGIN_HINT,        // From OIDC login_hint
    FALLBACK           // Configured fallback
}
```

## Performance

### Discovery Performance

| Method | Typical Latency | Cache Hit |
|--------|----------------|-----------|
| User Attribute | < 10ms | 95% |
| Email Domain | < 5ms | 90% |
| WebFinger | 100-500ms | 80% |
| Login Hint | < 5ms | N/A |

### Optimization Strategies

1. **Aggressive Caching**: Cache for 5+ minutes
2. **Parallel Discovery**: Try multiple methods concurrently (future)
3. **Prefetching**: Warm cache for known domains
4. **WebFinger Fallback**: Only if email domain fails

## Security Considerations

### Email Validation

Validate email format before discovery:

```java
if (!isValidEmail(userIdentifier)) {
    throw new DiscoveryException("Invalid email format");
}
```

### Domain Validation

Only allow known domains:

```java
String domain = extractDomain(email);
if (!isAllowedDomain(domain)) {
    logger.warn("Discovery attempted for unknown domain: {}", domain);
    return null;
}
```

### WebFinger Security

- Use HTTPS only (enforce TLS)
- Validate WebFinger response format
- Timeout protection (default: 5 seconds)
- Rate limiting to prevent abuse

### Cache Poisoning Prevention

- Validate provider exists in trust network
- Don't cache failed discoveries
- Clear cache on trust network update

## Troubleshooting

### Discovery Fails for Valid Email

**Symptom**: Error: "Could not discover provider for alice@university-a.edu"

**Check**:
1. Verify domain configured in trust network
2. Check WebFinger endpoint if enabled
3. Review discovery cache (may be stale)
4. Enable debug logging to see discovery attempts

**Debug**:
```java
logger.debug("Attempting discovery for: {}", userIdentifier);
logger.debug("Email domain: {}", extractDomain(userIdentifier));
logger.debug("Providers with domains: {}", getProvidersWithDomains());
logger.debug("WebFinger enabled: {}", webfingerEnabled);
```

### WebFinger Timeout

**Symptom**: Slow authentication, WebFinger errors

**Solutions**:
1. Increase timeout: `-Dauth41.discovery.webfinger.timeout=10000`
2. Disable WebFinger for problematic domains
3. Use email domain mapping instead
4. Check network connectivity to domain

### Wrong Provider Discovered

**Symptom**: User redirected to wrong identity provider

**Check**:
1. Multiple providers with same email domain
2. Stale cache entry
3. WebFinger returning incorrect issuer
4. Login hint overriding correct discovery

**Fix**:
1. Ensure unique email domains per provider
2. Clear discovery cache
3. Verify WebFinger response
4. Check login_hint handling

## Testing

### Unit Tests

Located in: `src/test/java/org/apifocal/auth41/plugin/discovery/`

**Test Coverage**:
- Email domain discovery
- WebFinger discovery
- Login hint parsing
- Cache hit/miss
- Fallback provider
- Invalid inputs

**Example Test**:
```java
@Test
void testEmailDomainDiscovery() {
    TrustNetwork network = createNetworkWithDomains();
    DiscoveryProvider discovery = new DiscoveryProviderImpl(network);

    ProviderInfo provider = discovery.discoverProvider("alice@university-a.edu");

    assertNotNull(provider);
    assertEquals("university-a", provider.getProviderId());
}

@Test
void testWebFingerDiscovery() {
    mockWebFingerServer();
    DiscoveryProvider discovery = new DiscoveryProviderImpl(network);

    ProviderInfo provider = discovery.discoverProvider("bob@external.com");

    assertNotNull(provider);
    assertEquals("external-provider", provider.getProviderId());
}
```

**Run Tests**:
```bash
mvn test -pl plugins/auth41-discovery
```

## Integration with Other Plugins

### Trust Network Plugin

Discovery queries trust network for providers:

```java
TrustNetworkProvider trustNetwork = session.getProvider(TrustNetworkProvider.class);
Collection<ProviderInfo> providers = trustNetwork.getAllProviders();

for (ProviderInfo provider : providers) {
    List<String> domains = provider.getDiscovery().getEmailDomains();
    if (domains.contains(emailDomain)) {
        return provider;
    }
}
```

### Federation Broker

Federation broker uses discovery to find home provider:

```java
DiscoveryProvider discovery = session.getProvider(DiscoveryProvider.class);
ProviderInfo homeProvider = discovery.discoverProvider(username);

if (homeProvider == null) {
    // Show account chooser or error
}
```

### Accounts Plugin

Accounts plugin stores discovered provider for future lookups:

```java
ProviderInfo homeProvider = discovery.discoverProvider(email);
user.setSingleAttribute("home_provider", homeProvider.getProviderId());
```

## Next Steps

- [Trust Network Plugin](trust-network.md) - Configure email domains
- [Federation Broker](federation-broker.md) - Use discovery in authentication
- [Configuration Guide](../configuration.md) - Configure discovery methods
- [Troubleshooting](../troubleshooting.md) - Discovery troubleshooting
