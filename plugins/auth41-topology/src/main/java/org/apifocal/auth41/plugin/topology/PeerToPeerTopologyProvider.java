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
     */
    private List<String> findShortestPath(TrustNetwork network, String source, String target) {
        // Queue for BFS: each entry is the current path being explored
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        // Start with the source provider
        queue.add(List.of(source));
        visited.add(source);

        while (!queue.isEmpty()) {
            List<String> currentPath = queue.poll();
            String currentProvider = currentPath.get(currentPath.size() - 1);

            // Prevent infinite loops
            if (currentPath.size() > MAX_PATH_LENGTH) {
                logger.warnf("Path length exceeded maximum (%d) while searching from %s to %s",
                    MAX_PATH_LENGTH, source, target);
                continue;
            }

            // Found the target!
            if (currentProvider.equals(target)) {
                return currentPath;
            }

            // Explore neighbors (providers that current provider trusts)
            for (TrustEdge edge : network.getTrustRelationships()) {
                if (edge.getFromProvider().equals(currentProvider)) {
                    String neighbor = edge.getToProvider();

                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        List<String> newPath = new ArrayList<>(currentPath);
                        newPath.add(neighbor);
                        queue.add(newPath);
                    }
                }
            }
        }

        // No path found
        return null;
    }

    @Override
    public boolean validateTopology(TrustNetwork network) {
        if (network == null) {
            return false;
        }

        // Peer-to-peer has minimal constraints:
        // - All providers should have role "peer" (optional, not enforced)
        // - Network should not have cycles that could cause issues (we handle this with MAX_PATH_LENGTH)

        // Check for cycles (optional validation)
        boolean hasCycles = detectCycles(network);
        if (hasCycles) {
            logger.infof("Peer-to-peer network contains cycles (this is allowed but may impact performance)");
        }

        return true;
    }

    /**
     * Detect if the network contains any cycles.
     * This is informational only - cycles are allowed in peer-to-peer networks.
     */
    private boolean detectCycles(TrustNetwork network) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String providerId : network.getProviders().keySet()) {
            if (hasCycleDFS(network, providerId, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleDFS(TrustNetwork network, String provider,
                                 Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(provider)) {
            return true; // Cycle detected
        }

        if (visited.contains(provider)) {
            return false; // Already processed
        }

        visited.add(provider);
        recursionStack.add(provider);

        // Check all neighbors
        for (TrustEdge edge : network.getTrustRelationships()) {
            if (edge.getFromProvider().equals(provider)) {
                String neighbor = edge.getToProvider();
                if (hasCycleDFS(network, neighbor, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(provider);
        return false;
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
