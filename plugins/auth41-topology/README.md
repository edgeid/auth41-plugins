# auth41-topology

Topology implementations for computing trust paths in OIDC federation networks.

## Overview

This plugin provides different topology implementations for determining how trust flows through a federation network. Each topology defines different rules for how providers can communicate and trust each other.

**Key Features:**
- Pluggable topology implementations via SPI
- Trust path computation with shortest-path algorithms
- Topology validation
- Support for hub-and-spoke and peer-to-peer topologies
- Thread-safe operations

## Supported Topologies

### 1. Hub-and-Spoke

In a hub-and-spoke topology:
- **Hubs** act as central trust anchors
- **Spokes** (providers) trust their hub(s) and vice versa
- **Spokes do NOT directly trust other spokes**
- Communication between spokes **must** go through a hub

**Trust Path Examples:**
```
Spoke A → Spoke B:  Spoke A → Hub → Spoke B (2 hops)
Spoke A → Hub:      Spoke A → Hub (1 hop, direct)
Hub → Spoke B:      Hub → Spoke B (1 hop, direct)
```

**Use Cases:**
- Healthcare networks with regional hubs
- Enterprise federations with central IT
- Regulated industries requiring central oversight

### 2. Peer-to-Peer

In a peer-to-peer topology:
- All providers are equal peers
- Providers can trust any other provider
- Trust is **transitive** through the network
- Uses BFS (breadth-first search) to find shortest path

**Trust Path Examples:**
```
Peer A → Peer B:           Peer A → Peer B (1 hop, direct)
Peer A → Peer C:           Peer A → Peer B → Peer C (2 hops, transitive)
Peer A → Peer D:           Peer A → Peer B → Peer C → Peer D (3 hops)
```

**Features:**
- Shortest path computation (BFS algorithm)
- Cycle detection (informational)
- Maximum path length protection (prevents infinite loops)

**Use Cases:**
- Academic federations (InCommon, eduGAIN)
- Research collaborations
- Decentralized networks

## Data Model

### TrustPath

Represents a computed trust path between two providers.

```java
public class TrustPath {
    String getSourceProvider();      // Starting provider
    String getTargetProvider();      // Destination provider
    List<String> getPath();          // Ordered list of provider IDs
    boolean isReachable();           // Whether target is reachable
    int getHopCount();               // Number of hops (-1 if unreachable)
}
```

## API Usage

### Get Topology Provider

```java
// Get appropriate topology provider based on network type
TopologyProvider provider = session.getProvider(TopologyProvider.class, "hub-and-spoke");
// or
TopologyProvider provider = session.getProvider(TopologyProvider.class, "peer-to-peer");
```

### Compute Trust Path

```java
// Load trust network (from auth41-trust-network plugin)
TrustNetworkProvider trustProvider = session.getProvider(TrustNetworkProvider.class);
TrustNetwork network = trustProvider.loadNetwork("healthcare-federation");

// Compute trust path
TopologyProvider topology = session.getProvider(TopologyProvider.class, "hub-and-spoke");
TrustPath path = topology.computeTrustPath(network, "provider-a", "provider-b");

if (path.isReachable()) {
    System.out.println("Trust path: " + path.getPath());
    System.out.println("Hops: " + path.getHopCount());
} else {
    System.out.println("No trust path exists");
}
```

### Validate Topology

```java
TopologyProvider provider = session.getProvider(TopologyProvider.class, "hub-and-spoke");
boolean isValid = provider.validateTopology(network);

if (!isValid) {
    // Network structure doesn't conform to hub-and-spoke topology
    // e.g., no hubs defined
}
```

## Examples

### Hub-and-Spoke Example

```java
// Create a hub-and-spoke network
TrustNetwork network = TrustNetwork.builder()
    .networkId("healthcare-federation")
    .topologyType("hub-and-spoke")
    .addProvider("regional-hub", "https://hub.healthcare.org", "hub")
    .addProvider("hospital-a", "https://hospital-a.org", "spoke")
    .addProvider("hospital-b", "https://hospital-b.org", "spoke")
    .addTrustRelationship("hospital-a", "regional-hub", TrustLevel.EXPLICIT)
    .addTrustRelationship("regional-hub", "hospital-a", TrustLevel.EXPLICIT)
    .addTrustRelationship("hospital-b", "regional-hub", TrustLevel.EXPLICIT)
    .addTrustRelationship("regional-hub", "hospital-b", TrustLevel.EXPLICIT)
    .build();

// Compute path from hospital-a to hospital-b
TopologyProvider topology = new HubAndSpokeTopologyProvider(session);
TrustPath path = topology.computeTrustPath(network, "hospital-a", "hospital-b");

// Result: hospital-a → regional-hub → hospital-b (2 hops)
```

### Peer-to-Peer Example

```java
// Create a peer-to-peer network
TrustNetwork network = TrustNetwork.builder()
    .networkId("research-federation")
    .topologyType("peer-to-peer")
    .addProvider("university-a", "https://uni-a.edu", "peer")
    .addProvider("university-b", "https://uni-b.edu", "peer")
    .addProvider("university-c", "https://uni-c.edu", "peer")
    .addTrustRelationship("university-a", "university-b", TrustLevel.EXPLICIT)
    .addTrustRelationship("university-b", "university-c", TrustLevel.EXPLICIT)
    .build();

// Compute path from university-a to university-c
TopologyProvider topology = new PeerToPeerTopologyProvider(session);
TrustPath path = topology.computeTrustPath(network, "university-a", "university-c");

// Result: university-a → university-b → university-c (2 hops, transitive)
```

## Testing

Run unit tests:
```bash
mvn test -pl plugins/auth41-topology
```

Current test coverage: **25 tests passing**
- TrustPath: 5 tests
- HubAndSpokeTopologyProvider: 9 tests
- PeerToPeerTopologyProvider: 11 tests

## Building

```bash
mvn clean install -pl plugins/auth41-topology
```

Output: `target/auth41-topology-1.0.0-SNAPSHOT.jar`

## Deployment

1. Build the JAR (requires auth41-trust-network dependency)
2. Copy to `$KEYCLOAK_HOME/providers/`
3. Run `$KEYCLOAK_HOME/bin/kc.sh build`
4. Restart Keycloak

## Dependencies

This plugin depends on:
- **auth41-trust-network** - Trust network data model
- Keycloak 23.0.4 (provided)
- JBoss Logging 3.5.3 (provided)

## Architecture

```
TopologyProviderFactory (SPI)
    ↓ creates
TopologyProvider
    ↓ implements
┌─────────────────────────┬──────────────────────────┐
│ HubAndSpokeTopology     │ PeerToPeerTopology       │
│ - Hub routing logic     │ - BFS shortest path      │
│ - Spoke validation      │ - Cycle detection        │
└─────────────────────────┴──────────────────────────┘
```

## Algorithm Details

### Hub-and-Spoke Path Computation

1. **Direct trust**: If source and target have direct trust, return 1-hop path
2. **Hub to spoke**: Must have direct trust (validated)
3. **Spoke to hub**: Must have direct trust (validated)
4. **Spoke to spoke**: Route through common hub
   - Find hubs that both spokes trust
   - Verify bidirectional trust: spoke → hub → spoke
   - Return 2-hop path if valid

**Time Complexity**: O(H) where H = number of hubs

### Peer-to-Peer Path Computation (BFS)

1. Start at source provider
2. Explore all neighbors level by level
3. Track visited nodes to avoid cycles
4. Return first path that reaches target (shortest)
5. Enforce maximum path length (10 hops) to prevent infinite loops

**Time Complexity**: O(V + E) where V = providers, E = trust relationships
**Space Complexity**: O(V) for visited set

## Performance Considerations

- **Hub-and-Spoke**: Very fast (O(H) where H is small)
- **Peer-to-Peer**: Efficient for sparse networks, slower for dense networks with many relationships
- **Caching**: Consider caching computed paths for frequently-queried provider pairs
- **Max Path Length**: Prevents runaway searches in cyclic networks

## Future Enhancements

- **Multi-Hub topology**: Support for multiple regional hubs with inter-hub trust
- **Hierarchical topology**: Tree-structured federation with multiple levels
- **Path cost metrics**: Prefer paths based on cost/latency/trust level
- **Path caching**: Cache frequently-computed paths
- **Dynamic topology selection**: Auto-detect topology from network structure
- **Path validation**: Verify trust relationships are still valid
- **Alternative paths**: Return multiple paths for redundancy

## See Also

- [auth41-trust-network](../auth41-trust-network/README.md) - Trust network data model (required dependency)
- [PLAN.md](../../PLAN.md) - Overall federation architecture
- [PLUGIN_DEPLOYMENT.md](../../PLUGIN_DEPLOYMENT.md) - Deployment guide
