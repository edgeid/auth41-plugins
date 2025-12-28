# Topology Plugin

The Topology plugin computes trust paths through the Auth41 federation network based on different network topologies.

## Overview

**Plugin Name**: `auth41-topology`
**SPI**: Custom `TopologyProvider` and `TopologyProviderFactory`
**Purpose**: Validate trust paths between identity providers

The Topology plugin determines whether a trust path exists between two providers based on the network topology type. It supports multiple topology patterns and provides path computation algorithms.

## Key Concepts

### Trust Path

A trust path is a sequence of providers connected by trust relationships from source to target:

```
Source → Intermediate → Target
```

Example: User at Provider B wants to access service at Provider A via Hub:
```
Provider A (service) → Hub → Provider B (user's home)
```

### Topology Types

Auth41 supports multiple network topologies:

1. **Hub-and-Spoke**: Central hub with multiple spokes
2. **Mesh** (Peer-to-Peer): Direct connections between providers
3. **Hierarchical** (Future): Multi-level trust hierarchies

## Supported Topologies

### Hub-and-Spoke Topology

**Structure**:
```
        Hub
       ↙  ↓  ↘
   Spoke Spoke Spoke
```

**Characteristics**:
- One or more hub providers
- Multiple spoke providers
- All spokes trust hub
- Spokes may trust each other through hub (transitive)

**Trust Rules**:
- Direct path: Hub ↔ Spoke (EXPLICIT)
- Transitive path: Spoke A → Hub → Spoke B (via hub)
- No direct spoke-to-spoke connections

**Configuration**:
```json
{
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub": {"provider_id": "hub", "role": "hub", ...},
    "spoke-a": {"provider_id": "spoke-a", "role": "spoke", ...},
    "spoke-b": {"provider_id": "spoke-b", "role": "spoke", ...}
  },
  "trust_relationships": [
    {"from": "hub", "to": "spoke-a", "trust_level": "EXPLICIT"},
    {"from": "spoke-a", "to": "hub", "trust_level": "EXPLICIT"},
    {"from": "hub", "to": "spoke-b", "trust_level": "EXPLICIT"},
    {"from": "spoke-b", "to": "hub", "trust_level": "EXPLICIT"}
  ]
}
```

**Path Examples**:
- Hub → Spoke A: Direct (1 hop)
- Spoke A → Hub: Direct (1 hop)
- Spoke A → Spoke B: Via Hub (2 hops)
- Spoke B → Spoke A: Via Hub (2 hops)

### Mesh Topology (Peer-to-Peer)

**Structure**:
```
   A ←→ B
   ↕     ↕
   C ←→ D
```

**Characteristics**:
- No central hub
- Peers connected directly
- Flexible trust relationships
- Explicit trust required for each connection

**Trust Rules**:
- Only EXPLICIT trust relationships honored
- Multi-hop paths supported (e.g., A → B → D)
- No transitive trust assumptions

**Configuration**:
```json
{
  "topology_type": "mesh",
  "providers": {
    "org-a": {"provider_id": "org-a", "role": "peer", ...},
    "org-b": {"provider_id": "org-b", "role": "peer", ...},
    "org-c": {"provider_id": "org-c", "role": "peer", ...}
  },
  "trust_relationships": [
    {"from": "org-a", "to": "org-b", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-a", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-c", "trust_level": "EXPLICIT"},
    {"from": "org-c", "to": "org-b", "trust_level": "EXPLICIT"}
  ]
}
```

**Path Examples**:
- Org A → Org B: Direct (1 hop)
- Org A → Org C: Via Org B (2 hops)
- Org C → Org A: Via Org B (2 hops)

## API Reference

### TopologyProvider SPI

Implemented by topology-specific providers:
- `HubAndSpokeTopologyProvider`
- `MeshTopologyProvider`

```java
public interface TopologyProvider extends Provider {
    /**
     * Compute trust path from source to target
     *
     * @param network The trust network
     * @param source Source provider ID
     * @param target Target provider ID
     * @return Trust path if exists, null otherwise
     */
    TrustPath computeTrustPath(
        TrustNetwork network,
        String source,
        String target
    );

    /**
     * Check if trust path exists (faster than computing full path)
     *
     * @param network The trust network
     * @param source Source provider ID
     * @param target Target provider ID
     * @return true if path exists
     */
    boolean hasTrustPath(
        TrustNetwork network,
        String source,
        String target
    );

    /**
     * Validate that network structure matches this topology
     *
     * @param network The trust network
     * @return true if valid
     */
    boolean validateTopology(TrustNetwork network);

    /**
     * Get topology type identifier
     *
     * @return topology type (e.g., "hub-and-spoke", "mesh")
     */
    String getTopologyType();
}
```

### Usage Example

```java
public class FederationBroker implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        KeycloakSession session = context.getSession();

        // Get trust network
        TrustNetworkProvider trustNetworkProvider =
            session.getProvider(TrustNetworkProvider.class);
        TrustNetwork network = trustNetworkProvider.getTrustNetwork();

        // Get topology provider
        TopologyProvider topologyProvider =
            session.getProvider(TopologyProvider.class);

        // Check if trust path exists
        boolean pathExists = topologyProvider.hasTrustPath(
            network,
            "service-provider",
            "user-home-provider"
        );

        if (!pathExists) {
            throw new AuthenticationException("No trust path found");
        }

        // Compute full path for logging/auditing
        TrustPath path = topologyProvider.computeTrustPath(
            network,
            "service-provider",
            "user-home-provider"
        );

        logger.info("Trust path: {}", path.getProviderSequence());
    }
}
```

## Data Model

### TrustPath

Represents a computed path through the network:

```java
public class TrustPath {
    private final List<String> providerSequence;
    private final int hopCount;
    private final TrustLevel overallTrustLevel;

    public boolean isDirectPath() {
        return hopCount == 1;
    }

    public boolean isValid() {
        return providerSequence != null && !providerSequence.isEmpty();
    }

    // Getters...
}
```

**Example**:
```java
TrustPath path = new TrustPath(
    Arrays.asList("spoke-a", "hub", "spoke-b"),
    2,  // 2 hops
    TrustLevel.EXPLICIT
);

path.getProviderSequence();  // ["spoke-a", "hub", "spoke-b"]
path.getHopCount();           // 2
path.isDirectPath();          // false
```

## Algorithms

### Hub-and-Spoke Path Computation

**Algorithm**: Special-case routing through hub

```java
public TrustPath computeTrustPath(
    TrustNetwork network,
    String source,
    String target
) {
    // Case 1: Same provider
    if (source.equals(target)) {
        return new TrustPath(Arrays.asList(source), 0, TrustLevel.EXPLICIT);
    }

    ProviderInfo sourceProvider = network.getProvider(source);
    ProviderInfo targetProvider = network.getProvider(target);

    // Case 2: Direct connection (hub-spoke or spoke-hub)
    if (hasDirectTrust(network, source, target)) {
        return new TrustPath(Arrays.asList(source, target), 1, TrustLevel.EXPLICIT);
    }

    // Case 3: Via hub (spoke-hub-spoke)
    String hub = findHub(network);
    if (hub != null &&
        hasDirectTrust(network, source, hub) &&
        hasDirectTrust(network, hub, target)) {
        return new TrustPath(
            Arrays.asList(source, hub, target),
            2,
            TrustLevel.TRANSITIVE
        );
    }

    // No path found
    return null;
}
```

**Complexity**: O(1) - Constant time with caching

### Mesh Path Computation

**Algorithm**: Breadth-First Search (BFS)

```java
public TrustPath computeTrustPath(
    TrustNetwork network,
    String source,
    String target
) {
    // BFS to find shortest path
    Queue<String> queue = new LinkedList<>();
    Map<String, String> predecessors = new HashMap<>();
    Set<String> visited = new HashSet<>();

    queue.add(source);
    visited.add(source);

    while (!queue.isEmpty()) {
        String current = queue.poll();

        if (current.equals(target)) {
            // Reconstruct path from predecessors
            return buildPath(predecessors, source, target);
        }

        // Explore neighbors
        for (TrustRelationship rel : network.getTrustRelationships(current)) {
            String neighbor = rel.getTo();

            if (!visited.contains(neighbor) &&
                rel.getTrustLevel() == TrustLevel.EXPLICIT) {
                visited.add(neighbor);
                predecessors.put(neighbor, current);
                queue.add(neighbor);
            }
        }
    }

    // No path found
    return null;
}
```

**Complexity**: O(V + E) where V = providers, E = trust relationships

**Optimizations**:
- Bidirectional BFS for large networks
- Path caching for repeated queries
- Early termination when path found

## Configuration

### Selecting Topology Provider

The topology provider is selected automatically based on `topology_type` in trust network configuration:

```json
{
  "topology_type": "hub-and-spoke"  // or "mesh"
}
```

Keycloak loads the matching provider:
- `hub-and-spoke` → `HubAndSpokeTopologyProvider`
- `mesh` → `MeshTopologyProvider`

### System Properties

Configure topology-specific settings:

```bash
# Maximum path length (hops) for mesh topology
-Dauth41.topology.mesh.max-hops=3

# Enable path caching
-Dauth41.topology.cache.enabled=true
-Dauth41.topology.cache.ttl=300  # seconds
```

## Validation

### Topology Validation

Each topology provider validates network structure:

**Hub-and-Spoke Validation**:
- At least one provider with role="hub"
- All spokes have trust relationship to hub
- No spoke-to-spoke direct trust (warning only)

**Mesh Validation**:
- All providers have role="peer"
- Trust relationships are symmetric (bidirectional)
- No isolated providers (disconnected from network)

**Validation Errors**:
```
ERROR: Hub-and-spoke topology requires at least one hub provider
ERROR: Spoke 'spoke-a' has no trust relationship to any hub
WARNING: Spoke-to-spoke direct trust detected (may bypass hub)
ERROR: Mesh topology found isolated provider: 'org-d' (no connections)
WARNING: Asymmetric trust relationship: A→B exists but B→A missing
```

### Trust Path Validation

When computing path:
- Source and target must exist in network
- All intermediate providers must exist
- All trust relationships must be EXPLICIT or TRANSITIVE
- Path length must not exceed configured maximum

## Performance

### Path Computation Performance

| Topology | Algorithm | Complexity | Typical Latency |
|----------|-----------|------------|-----------------|
| Hub-and-Spoke | Special-case | O(1) | < 1ms |
| Mesh (10 providers) | BFS | O(V+E) | < 5ms |
| Mesh (100 providers) | BFS | O(V+E) | < 20ms |

### Caching

Path results can be cached:

```java
// Cache key: source + target
String cacheKey = source + ":" + target;

// Check cache
TrustPath cachedPath = pathCache.get(cacheKey);
if (cachedPath != null) {
    return cachedPath;
}

// Compute and cache
TrustPath path = computeTrustPath(network, source, target);
pathCache.put(cacheKey, path, TTL_SECONDS);
```

**Cache Invalidation**:
- Invalidate when trust network changes
- TTL-based expiration (default: 5 minutes)
- LRU eviction for memory management

## Security Considerations

### Trust Level Enforcement

- Only EXPLICIT and TRANSITIVE trust levels allow authentication
- NONE trust level blocks path even if edges exist
- Transitive trust requires multi-hop path validation

### Path Length Limits

Limit maximum path length to prevent:
- Long computation times
- Potential trust chain abuse
- Circular references (in mesh)

Default limits:
- Hub-and-spoke: Max 2 hops (spoke → hub → spoke)
- Mesh: Max 3 hops (configurable)

### Circular Path Prevention

BFS algorithm naturally prevents circular paths via visited set:
- Each provider visited at most once
- No infinite loops
- Shortest path guaranteed

## Troubleshooting

### No Trust Path Found

**Symptom**: Error: "No trust path from A to B"

**Check**:
1. Verify both providers exist in network
2. Check trust relationships configured in both directions
3. Verify trust level is EXPLICIT or TRANSITIVE (not NONE)
4. For hub-and-spoke: Check hub role and relationships
5. For mesh: Check path exists via intermediate providers

**Debug**:
```java
// Enable debug logging
logger.debug("Computing path from {} to {}", source, target);
logger.debug("Source provider: {}", network.getProvider(source));
logger.debug("Target provider: {}", network.getProvider(target));
logger.debug("Trust relationships: {}", network.getTrustRelationships(source));
```

### Invalid Topology

**Symptom**: Error: "Topology validation failed"

**Check**:
1. Verify `topology_type` matches network structure
2. For hub-and-spoke: Ensure at least one hub exists
3. For mesh: Ensure all providers have role="peer"
4. Check for isolated providers (no connections)

### Performance Issues

**Symptom**: Slow path computation

**Solutions**:
1. Enable path caching
2. Reduce maximum hop count for mesh
3. Simplify network topology (fewer providers)
4. Consider hub-and-spoke instead of mesh for large networks

## Testing

### Unit Tests

Located in: `src/test/java/org/apifocal/auth41/plugin/topology/`

**Test Coverage**:
- Path computation for each topology
- Direct paths (1 hop)
- Multi-hop paths (2-3 hops)
- No path scenarios
- Topology validation
- Edge cases (same provider, circular paths)

**Run Tests**:
```bash
mvn test -pl plugins/auth41-topology
```

**Example Test**:
```java
@Test
void testHubAndSpokePath() {
    TrustNetwork network = createHubSpokeNetwork();
    TopologyProvider topology = new HubAndSpokeTopologyProvider();

    // Test spoke-to-spoke via hub
    TrustPath path = topology.computeTrustPath(network, "spoke-a", "spoke-b");

    assertNotNull(path);
    assertEquals(2, path.getHopCount());
    assertEquals(Arrays.asList("spoke-a", "hub", "spoke-b"), path.getProviderSequence());
}
```

## Extension Points

### Custom Topology Providers

Implement custom topologies:

```java
public class HierarchicalTopologyProvider implements TopologyProvider {

    @Override
    public String getTopologyType() {
        return "hierarchical";
    }

    @Override
    public TrustPath computeTrustPath(
        TrustNetwork network,
        String source,
        String target
    ) {
        // Custom hierarchical path algorithm
        // e.g., up tree to common ancestor, then down to target
    }

    @Override
    public boolean validateTopology(TrustNetwork network) {
        // Validate hierarchical structure (no cycles, single root, etc.)
    }
}
```

Register via SPI:
```
# META-INF/services/org.apifocal.auth41.plugin.topology.spi.TopologyProviderFactory
org.example.HierarchicalTopologyProviderFactory
```

## Integration with Other Plugins

### Trust Network Plugin

Topology provider receives trust network from Trust Network plugin:

```java
TrustNetworkProvider trustNetwork = session.getProvider(TrustNetworkProvider.class);
TrustNetwork network = trustNetwork.getTrustNetwork();

TopologyProvider topology = session.getProvider(TopologyProvider.class);
TrustPath path = topology.computeTrustPath(network, source, target);
```

### Federation Broker

Federation broker validates trust path before authenticating:

```java
if (!topologyProvider.hasTrustPath(network, serviceProvider, homeProvider)) {
    context.failure(AuthenticationFlowError.ACCESS_DENIED);
    return;
}
```

## Next Steps

- [Trust Network Plugin](trust-network.md) - Define trust relationships
- [Federation Broker](federation-broker.md) - Use topology for authentication
- [Configuration Guide](../configuration.md) - Configure different topologies
- [Examples](../examples/hub-spoke.md) - Complete topology examples
