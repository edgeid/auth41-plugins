# auth41-trust-network

Trust network management plugin for OIDC federation.

## Overview

This plugin provides the foundation for OIDC federation by managing trust networks - graphs of OIDC providers and their trust relationships.

**Key Features:**
- Load trust network configuration from JSON
- Query trust relationships between providers
- In-memory caching for fast lookups
- Thread-safe operations
- Pluggable topology support (via topology type field)

## What is a Trust Network?

A **trust network** is a directed graph where:
- **Nodes** are OIDC providers (identity providers or hubs)
- **Edges** are trust relationships between providers
- **Topology** defines how trust flows (hub-and-spoke, peer-to-peer, etc.)

Example:
```
Hub A trusts Provider B (explicit)
Hub A trusts Provider C (explicit)
Provider B trusts Hub A (explicit)
```

## Data Model

### TrustNetwork
- `networkId`: Unique identifier
- `topologyType`: Topology type (e.g., "hub-and-spoke")
- `providers`: Map of provider nodes
- `trustRelationships`: Set of trust edges
- `version`: Registry version timestamp

### ProviderNode
- `providerId`: Unique provider ID
- `issuer`: OIDC issuer URL
- `role`: Provider role (e.g., "hub", "spoke")
- `metadata`: Provider endpoints (JWKS URI, etc.)

### TrustEdge
- `fromProvider`: Source provider
- `toProvider`: Target provider
- `level`: Trust level (EXPLICIT or TRANSITIVE)

## Configuration

### JSON Configuration Format

```json
{
  "network_id": "healthcare-federation",
  "topology_type": "hub-and-spoke",
  "registry_version": "2024-12-24T10:00:00Z",
  "providers": {
    "hub-a": {
      "issuer": "https://hub-a.healthcare.org",
      "role": "hub"
    },
    "provider-b": {
      "issuer": "https://provider-b.healthcare.org",
      "role": "spoke"
    }
  },
  "trust_relationships": [
    {
      "from": "hub-a",
      "to": "provider-b",
      "level": "explicit"
    },
    {
      "from": "provider-b",
      "to": "hub-a",
      "level": "explicit"
    }
  ]
}
```

### Loading Configuration

**Current Implementation: JSON-based loading only**

Place your trust network configuration JSON file in `src/main/resources/` and reference it:

```java
TrustNetworkConfigLoader loader = new TrustNetworkConfigLoader();
TrustNetwork network = loader.loadFromResource("my-network.json");
```

**Note:** System properties-based configuration (Config.Scope) is not yet fully implemented.
The config loader currently only reads the topology type from system properties. For full
functionality (providers and trust relationships), use JSON-based loading.

## API Usage

### Get Provider Instance

```java
TrustNetworkProvider provider = session.getProvider(TrustNetworkProvider.class);
```

### Query Network

```java
// Load network
TrustNetwork network = provider.loadNetwork("healthcare-federation");

// Check provider membership
boolean isMember = provider.isMember("provider-b", "healthcare-federation");

// Get provider metadata
ProviderNode node = provider.getProviderMetadata("provider-b", "healthcare-federation");

// Check trust relationship
boolean hasTrust = provider.hasTrustRelationship("hub-a", "provider-b", "healthcare-federation");

// Get all trust relationships
Set<TrustEdge> edges = provider.getTrustRelationships("healthcare-federation");
```

## Testing

Run unit tests:
```bash
mvn test -pl plugins/auth41-trust-network
```

Current test coverage: **58%** (41 tests passing)

## Building

```bash
mvn clean install -pl plugins/auth41-trust-network
```

Output: `target/auth41-trust-network-1.0.0-SNAPSHOT.jar`

## Deployment

1. Build the JAR
2. Copy to `$KEYCLOAK_HOME/providers/`
3. Run `$KEYCLOAK_HOME/bin/kc.sh build`
4. Restart Keycloak

## Dependencies

This plugin depends on:
- Keycloak 23.0.4 (provided)
- Jackson 2.16.1 (for JSON parsing)
- JBoss Logging 3.5.3 (provided)

## Architecture

```
TrustNetworkProviderFactory
    ↓
ConfigBasedTrustNetworkProviderFactory
    ↓ creates
ConfigBasedTrustNetworkProvider
    ↓ uses
TrustNetworkConfigLoader → loads → TrustNetwork
```

## Thread Safety

All operations are thread-safe:
- Network cache uses `ConcurrentHashMap`
- Trust relationships use concurrent collections
- Immutable data structures (unmodifiable maps/sets)

## Current Limitations

- **Config.Scope-based loading**: Only topology type can be loaded from system properties. Providers and trust relationships must be loaded from JSON files. Config key discovery mechanism is not yet implemented.
- **No remote loading**: Networks must be bundled with the plugin JAR. Remote fetching from governance registry planned for future release.
- **No cache expiration**: Networks are cached indefinitely once loaded. TTL-based expiration planned for future release.

## Future Enhancements

- **Config.Scope discovery**: Implement mechanism to discover providers and trust relationships from system properties
- **Integration with governance registry**: Fetch networks remotely from central registry
- **TTL-based cache expiration**: Automatic refresh of stale network data
- **Network update notifications**: Real-time updates when trust network changes
- **Realm attributes support**: Load configuration from Keycloak realm attributes for dynamic management
- **Additional topology validation**: Validate network structure against topology rules

## See Also

- [PLAN.md](../../PLAN.md) - Overall federation architecture
- [auth41-topology](../auth41-topology/README.md) - Topology implementations (next plugin)
- [PLUGIN_DEPLOYMENT.md](../../PLUGIN_DEPLOYMENT.md) - Deployment guide
