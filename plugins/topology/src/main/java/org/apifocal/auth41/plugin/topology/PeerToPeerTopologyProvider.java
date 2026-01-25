package org.apifocal.auth41.plugin.topology;

import org.apifocal.auth41.plugin.trustnetwork.TrustEdge;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.util.*;

/**
 * Peer-to-peer topology implementation.
 *
 * In this topology:
 * - All providers are peers (no hierarchical structure)
 * - Providers can have direct trust relationships with any other provider
 * - Trust is transitive through the network
 * - Uses breadth-first search (BFS) to find shortest trust path
 *
 * Trust path examples:
 * - Peer A → Peer B (1 hop, direct trust)
 * - Peer A → Peer C → Peer B (2 hops, transitive through C)
 */
public class PeerToPeerTopologyProvider implements TopologyProvider {

    private static final Logger logger = Logger.getLogger(PeerToPeerTopologyProvider.class);
    private static final String TOPOLOGY_TYPE = "peer-to-peer";
    private static final int MAX_PATH_LENGTH = 10; // Prevent infinite loops

    private final KeycloakSession session;

    public PeerToPeerTopologyProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public TrustPath computeTrustPath(TrustNetwork network, String sourceProvider, String targetProvider) {
        if (network == null || sourceProvider == null || targetProvider == null) {
            logger.warnf("Invalid input: network=%s, source=%s, target=%s", network, sourceProvider, targetProvider);
            return TrustPath.builder()
                .sourceProvider(sourceProvider != null ? sourceProvider : "unknown")
                .targetProvider(targetProvider != null ? targetProvider : "unknown")
                .reachable(false)
                .build();
        }

        // Same provider - direct path
        if (sourceProvider.equals(targetProvider)) {
            return TrustPath.builder()
                .sourceProvider(sourceProvider)
                .targetProvider(targetProvider)
                .path(List.of(sourceProvider))
                .reachable(true)
                .build();
        }

        // Check if providers exist in network
        if (!network.isMember(sourceProvider) || !network.isMember(targetProvider)) {
            logger.warnf("Provider not in network: source=%s (exists=%s), target=%s (exists=%s)",
                sourceProvider, network.isMember(sourceProvider),
                targetProvider, network.isMember(targetProvider));
            return unreachablePath(sourceProvider, targetProvider);
        }

        // Use BFS to find shortest path
        List<String> path = findShortestPath(network, sourceProvider, targetProvider);

        if (path == null || path.isEmpty()) {
            logger.debugf("No trust path found from %s to %s", sourceProvider, targetProvider);
            return unreachablePath(sourceProvider, targetProvider);
        }

        return TrustPath.builder()
            .sourceProvider(sourceProvider)
            .targetProvider(targetProvider)
            .path(path)
            .reachable(true)
            .build();
    }

    /**
     * Use breadth-first search to find the shortest trust path.
     * Uses parent pointer map for memory efficiency and adjacency list for O(V+E) complexity.
     */
    private List<String> findShortestPath(TrustNetwork network, String source, String target) {
        // Build adjacency list once for O(E) instead of O(V*E)
        Map<String, List<String>> adjacencyList = buildAdjacencyList(network);

        // Queue for BFS: only store current provider (not entire path)
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parentMap = new HashMap<>(); // child -> parent mapping
        Map<String, Integer> depthMap = new HashMap<>(); // track depth to enforce max path length

        // Start with the source provider
        queue.add(source);
        visited.add(source);
        parentMap.put(source, null); // source has no parent
        depthMap.put(source, 0);

        while (!queue.isEmpty()) {
            String currentProvider = queue.poll();
            int currentDepth = depthMap.get(currentProvider);

            // Prevent infinite loops
            if (currentDepth >= MAX_PATH_LENGTH) {
                logger.warnf("Path length exceeded maximum (%d) while searching from %s to %s",
                    MAX_PATH_LENGTH, source, target);
                continue;
            }

            // Found the target! Reconstruct path from parent pointers
            if (currentProvider.equals(target)) {
                return reconstructPath(parentMap, target);
            }

            // Explore neighbors using adjacency list (O(1) lookup)
            List<String> neighbors = adjacencyList.getOrDefault(currentProvider, Collections.emptyList());
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, currentProvider);
                    depthMap.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }

        // No path found
        return null;
    }

    /**
     * Build adjacency list from trust relationships for O(E) preprocessing.
     * This allows O(1) neighbor lookup during BFS instead of O(E) per node.
     *
     * @return Map of provider ID to list of neighbors (providers they trust)
     */
    private Map<String, List<String>> buildAdjacencyList(TrustNetwork network) {
        Map<String, List<String>> adjacencyList = new HashMap<>();

        for (TrustEdge edge : network.getTrustRelationships()) {
            String from = edge.getFromProvider();
            String to = edge.getToProvider();

            adjacencyList.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }

        return adjacencyList;
    }

    /**
     * Reconstruct path from parent pointer map by walking backwards from target to source.
     */
    private List<String> reconstructPath(Map<String, String> parentMap, String target) {
        List<String> path = new ArrayList<>();
        String current = target;

        // Walk backwards from target to source
        while (current != null) {
            path.add(current);
            current = parentMap.get(current);
        }

        // Reverse to get source -> target order
        Collections.reverse(path);
        return path;
    }

    @Override
    public boolean validateTopology(TrustNetwork network) {
        if (network == null) {
            return false;
        }

        // Peer-to-peer has minimal constraints:
        // - All providers should have role "peer" (optional, not enforced)
        // - Cycles are allowed and handled via MAX_PATH_LENGTH in path computation

        // Note: Cycle detection is intentionally NOT performed here for performance reasons.
        // Cycles are valid in peer-to-peer networks and are handled during path computation
        // via the MAX_PATH_LENGTH check and visited set tracking. If cycle information is
        // needed, it can be computed separately on-demand.

        return true;
    }

    @Override
    public String getTopologyType() {
        return TOPOLOGY_TYPE;
    }

    @Override
    public void close() {
        // No resources to close
    }

    private TrustPath unreachablePath(String source, String target) {
        return TrustPath.builder()
            .sourceProvider(source)
            .targetProvider(target)
            .reachable(false)
            .build();
    }
}
