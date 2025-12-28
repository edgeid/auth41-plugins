# Trust Network Plugin

The Trust Network plugin manages trust relationships between identity providers in the Auth41 federation.

## Overview

**Plugin Name**: `auth41-trust-network`
**SPI**: Custom `TrustNetworkProvider` and `TrustNetworkProviderFactory`
**Purpose**: Load, store, and query trust network configuration

The Trust Network plugin is the foundation of Auth41 federation. It defines which identity providers are part of the federation network and what trust relationships exist between them.

## Key Concepts

### Trust Network

A trust network is a collection of identity providers (nodes) connected by trust relationships (edges). Each network has:

- **Network ID**: Unique identifier for the federation
- **Topology Type**: Network structure (hub-and-spoke, mesh, hierarchical)
- **Providers**: Identity provider definitions
- **Trust Relationships**: Explicit trust connections between providers

### Provider

An identity provider in the trust network:

```json
{
  "provider_id": "university-a",
  "issuer": "https://sso.university-a.edu/realms/students",
  "role": "spoke",
  "discovery": {
    "email_domains": ["university-a.edu"],
    "webfinger_enabled": true
  },
  "metadata": {
    "name": "University A",
    "contact": "admin@university-a.edu"
  }
}
```

**Fields**:
- `provider_id` (required): Unique identifier within the network
- `issuer` (required): OIDC issuer URL (must match token `iss` claim)
- `role` (required): Provider role in topology (`hub`, `spoke`, `peer`)
- `discovery` (optional): Discovery configuration for this provider
- `metadata` (optional): Custom metadata (name, description, contact, etc.)

### Trust Relationship

A directed trust edge between two providers:

```json
{
  "from": "hub",
  "to": "university-a",
  "trust_level": "EXPLICIT",
  "metadata": {
    "established": "2025-01-01",
    "expires": "2026-01-01"
  }
}
```

**Fields**:
- `from` (required): Source provider ID
- `to` (required): Target provider ID
- `trust_level` (required): Trust level (EXPLICIT, TRANSITIVE, NONE)
- `metadata` (optional): Custom metadata (dates, notes, etc.)

### Trust Levels

- **EXPLICIT**: Direct trust relationship, authentication allowed
- **TRANSITIVE**: Indirect trust via intermediary (requires topology support)
- **NONE**: No trust, authentication blocked (can be used to override transitive trust)

## Configuration

### Trust Network File

Create a JSON file defining your trust network:

```json
{
  "network_id": "university-consortium",
  "topology_type": "hub-and-spoke",
  "providers": {
    "consortium-hub": {
      "provider_id": "consortium-hub",
      "issuer": "https://auth.consortium.edu/realms/federation",
      "role": "hub",
      "discovery": {
        "email_domains": ["consortium.edu"]
      },
      "metadata": {
        "name": "University Consortium Hub"
      }
    },
    "university-a": {
      "provider_id": "university-a",
      "issuer": "https://sso.university-a.edu/realms/students",
      "role": "spoke",
      "discovery": {
        "email_domains": ["university-a.edu", "univ-a.edu"],
        "webfinger_enabled": true
      },
      "metadata": {
        "name": "University A"
      }
    },
    "university-b": {
      "provider_id": "university-b",
      "issuer": "https://idp.university-b.edu/realms/main",
      "role": "spoke",
      "discovery": {
        "email_domains": ["university-b.edu"]
      },
      "metadata": {
        "name": "University B"
      }
    }
  },
  "trust_relationships": [
    {
      "from": "consortium-hub",
      "to": "university-a",
      "trust_level": "EXPLICIT"
    },
    {
      "from": "university-a",
      "to": "consortium-hub",
      "trust_level": "EXPLICIT"
    },
    {
      "from": "consortium-hub",
      "to": "university-b",
      "trust_level": "EXPLICIT"
    },
    {
      "from": "university-b",
      "to": "consortium-hub",
      "trust_level": "EXPLICIT"
    }
  ],
  "metadata": {
    "version": "1.0",
    "description": "University Consortium Federation Network",
    "updated": "2025-01-15"
  }
}
```

### Loading Configuration

Set the file path via environment variable:

```bash
export AUTH41_TRUST_NETWORK_PATH=/path/to/trust-network.json
```

Or system property:

```bash
-Dauth41.trust.network.path=/path/to/trust-network.json
```

The plugin loads the configuration at Keycloak startup.

## API Reference

### TrustNetworkProvider SPI

Implemented by: `org.apifocal.auth41.plugin.trustnetwork.provider.TrustNetworkProviderImpl`

```java
public interface TrustNetworkProvider extends Provider {
    /**
     * Get the loaded trust network
     */
    TrustNetwork getTrustNetwork();

    /**
     * Get a specific provider by ID
     */
    ProviderInfo getProvider(String providerId);

    /**
     * Get all trust relationships for a provider
     */
    List<TrustRelationship> getTrustRelationships(String providerId);

    /**
     * Check if a provider is a member of the network
     */
    boolean isMember(String providerId);

    /**
     * Get all providers in the network
     */
    Collection<ProviderInfo> getAllProviders();
}
```

### Usage Example

Other plugins can access the trust network:

```java
public class MyAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        KeycloakSession session = context.getSession();

        // Get trust network provider
        TrustNetworkProvider trustNetwork = session.getProvider(
            TrustNetworkProvider.class
        );

        // Query trust network
        ProviderInfo provider = trustNetwork.getProvider("university-a");
        String issuer = provider.getIssuer();

        // Check if provider is trusted
        boolean isMember = trustNetwork.isMember("university-a");
    }
}
```

## Data Model

### TrustNetwork

Immutable data model representing the complete trust network:

```java
public class TrustNetwork {
    private final String networkId;
    private final String topologyType;
    private final Map<String, ProviderInfo> providers;
    private final List<TrustRelationship> trustRelationships;
    private final Map<String, Object> metadata;

    // Getters and builder...
}
```

### ProviderInfo

Immutable data model representing a single provider:

```java
public class ProviderInfo {
    private final String providerId;
    private final String issuer;
    private final String role;
    private final DiscoveryConfig discovery;
    private final Map<String, Object> metadata;

    // Getters and builder...
}
```

### TrustRelationship

Immutable data model representing a trust edge:

```java
public class TrustRelationship {
    private final String from;
    private final String to;
    private final TrustLevel trustLevel;
    private final Map<String, Object> metadata;

    // Getters and builder...
}
```

### TrustLevel Enum

```java
public enum TrustLevel {
    EXPLICIT,    // Direct trust
    TRANSITIVE,  // Indirect trust via intermediary
    NONE         // No trust (blocks transitive)
}
```

## Design Patterns

### Immutability

All data models are immutable:
- Thread-safe by design
- No defensive copying needed
- Safe to cache and share

```java
TrustNetwork network = trustNetworkProvider.getTrustNetwork();
// network is immutable, safe to use across threads
```

### Builder Pattern

Fluent builder API for constructing models:

```java
TrustNetwork network = TrustNetwork.builder()
    .networkId("my-federation")
    .topologyType("hub-and-spoke")
    .addProvider(ProviderInfo.builder()
        .providerId("hub")
        .issuer("https://hub.example.com/realms/main")
        .role("hub")
        .build())
    .addTrustRelationship(TrustRelationship.builder()
        .from("hub")
        .to("spoke-a")
        .trustLevel(TrustLevel.EXPLICIT)
        .build())
    .build();
```

### Factory Pattern

Provider factory manages lifecycle:

```java
public class TrustNetworkProviderFactory
    implements TrustNetworkProviderFactory {

    private TrustNetwork network;

    @Override
    public void init(Config.Scope config) {
        String path = config.get("path");
        this.network = loadNetworkFromFile(path);
    }

    @Override
    public TrustNetworkProvider create(KeycloakSession session) {
        return new TrustNetworkProviderImpl(network);
    }
}
```

## Validation

The plugin validates configuration at load time:

### Required Fields

- `network_id` must be non-empty
- `topology_type` must be valid
- Each provider must have `provider_id`, `issuer`, and `role`
- Each trust relationship must have `from`, `to`, and `trust_level`

### Referential Integrity

- Trust relationship `from` and `to` must reference existing providers
- No duplicate provider IDs
- No duplicate trust relationships

### Format Validation

- `issuer` must be valid HTTPS URL
- `role` must be one of: hub, spoke, peer
- `trust_level` must be one of: EXPLICIT, TRANSITIVE, NONE

### Example Validation Errors

```
ERROR: Provider 'provider-id' is required
ERROR: Trust relationship references unknown provider: 'unknown-provider'
ERROR: Invalid trust level: 'INVALID' (must be EXPLICIT, TRANSITIVE, or NONE)
ERROR: Invalid issuer URL: 'not-a-url'
```

## Performance Considerations

### Loading

- Network loaded once at startup
- Cached in memory for duration of Keycloak lifecycle
- No database queries required

### Lookups

- Provider lookup: O(1) HashMap lookup
- Trust relationship query: O(n) where n = number of relationships for provider
- Network validation: O(p + r) where p = providers, r = relationships

### Memory

- Typical network (10 providers, 20 relationships): ~10KB
- Large network (100 providers, 500 relationships): ~100KB
- Memory usage is negligible

## Hot Reload (Future)

Currently, trust network changes require Keycloak restart. Future enhancement will support:

```java
// Watch file for changes
trustNetworkProvider.reloadNetwork();

// Or via admin API
POST /admin/realms/{realm}/auth41/trust-network/reload
```

## Integration with Other Plugins

### Topology Plugin

Topology plugin queries trust network to compute paths:

```java
TrustNetwork network = trustNetworkProvider.getTrustNetwork();
TrustPath path = topologyProvider.computeTrustPath(
    network,
    "hub",
    "spoke-a"
);
```

### Discovery Plugin

Discovery plugin uses provider metadata for email domain mapping:

```java
ProviderInfo provider = trustNetworkProvider.getProvider("university-a");
List<String> domains = provider.getDiscovery().getEmailDomains();
```

### Federation Broker

Federation broker validates trust before redirecting:

```java
boolean isTrusted = trustNetworkProvider.isMember("external-provider");
if (!isTrusted) {
    throw new AuthenticationException("Provider not in trust network");
}
```

## Security Considerations

### Configuration File Security

- Store trust network file in secure location
- Restrict file permissions (read-only for Keycloak process)
- Use version control for change tracking
- Audit changes to trust network

### Issuer Validation

- `issuer` URLs must use HTTPS (enforced)
- Issuer must exactly match token `iss` claim
- No wildcards or pattern matching

### Trust Level Enforcement

- EXPLICIT trust required for direct authentication
- TRANSITIVE trust requires topology validation
- NONE trust blocks all authentication attempts

## Troubleshooting

### Trust Network Not Loading

**Symptom**: Error: "Failed to load trust network"

**Check**:
1. Verify `AUTH41_TRUST_NETWORK_PATH` is set
2. Check file exists and is readable
3. Validate JSON syntax
4. Review Keycloak logs for specific error

### Provider Not Found

**Symptom**: Error: "Provider not found in trust network"

**Check**:
1. Verify provider_id matches exactly (case-sensitive)
2. Check provider exists in configuration
3. Ensure network was loaded successfully

### Invalid Trust Relationship

**Symptom**: Error: "Trust relationship references unknown provider"

**Check**:
1. Verify `from` and `to` provider IDs exist
2. Check for typos in provider references
3. Ensure providers defined before relationships

## Testing

### Unit Tests

Located in: `src/test/java/org/apifocal/auth41/plugin/trustnetwork/`

**Test Coverage**:
- Network loading and parsing
- Provider queries
- Trust relationship queries
- Validation rules
- Immutability guarantees
- Thread safety

**Run Tests**:
```bash
mvn test -pl plugins/auth41-trust-network
```

### Integration Tests

Test with Keycloak:

1. Deploy plugin
2. Configure trust network file
3. Verify provider queries work
4. Test with federation broker

## Example Configurations

### Minimal Network (2 Providers)

```json
{
  "network_id": "minimal-federation",
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub": {
      "provider_id": "hub",
      "issuer": "https://hub.example.com/realms/main",
      "role": "hub"
    },
    "spoke": {
      "provider_id": "spoke",
      "issuer": "https://spoke.example.com/realms/main",
      "role": "spoke"
    }
  },
  "trust_relationships": [
    {"from": "hub", "to": "spoke", "trust_level": "EXPLICIT"},
    {"from": "spoke", "to": "hub", "trust_level": "EXPLICIT"}
  ]
}
```

### Mesh Network (3 Peers)

```json
{
  "network_id": "mesh-federation",
  "topology_type": "mesh",
  "providers": {
    "org-a": {
      "provider_id": "org-a",
      "issuer": "https://org-a.example.com/realms/main",
      "role": "peer"
    },
    "org-b": {
      "provider_id": "org-b",
      "issuer": "https://org-b.example.com/realms/main",
      "role": "peer"
    },
    "org-c": {
      "provider_id": "org-c",
      "issuer": "https://org-c.example.com/realms/main",
      "role": "peer"
    }
  },
  "trust_relationships": [
    {"from": "org-a", "to": "org-b", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-a", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-c", "trust_level": "EXPLICIT"},
    {"from": "org-c", "to": "org-b", "trust_level": "EXPLICIT"}
  ]
}
```

## Next Steps

- [Topology Plugin](topology.md) - Use trust network to compute paths
- [Discovery Plugin](discovery.md) - Use provider metadata for discovery
- [Federation Broker](federation-broker.md) - Validate trust before federation
- [Configuration Guide](../configuration.md) - Complete configuration examples
