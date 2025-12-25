package org.apifocal.auth41.plugin.topology;

import org.apifocal.auth41.plugin.trustnetwork.ProviderNode;
import org.apifocal.auth41.plugin.trustnetwork.TrustNetwork;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hub-and-spoke topology implementation.
 *
 * In this topology:
 * - One or more hubs act as central trust anchors
 * - Spokes (providers) trust their hub(s)
 * - Hubs trust their spokes
 * - Spokes do NOT directly trust other spokes
 * - Communication between spokes goes through the hub
 *
 * Trust path examples:
 * - Spoke A → Hub → Spoke B (2 hops)
 * - Hub → Spoke A (1 hop, direct)
 * - Spoke A → Hub (1 hop, direct)
 */
public class HubAndSpokeTopologyProvider implements TopologyProvider {

    private static final Logger logger = Logger.getLogger(HubAndSpokeTopologyProvider.class);
    private static final String TOPOLOGY_TYPE = "hub-and-spoke";

    private final KeycloakSession session;

    public HubAndSpokeTopologyProvider(KeycloakSession session) {
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

        ProviderNode source = network.getProvider(sourceProvider);
        ProviderNode target = network.getProvider(targetProvider);

        String sourceRole = source.getRole();
        String targetRole = target.getRole();

        // Direct trust exists?
        if (network.hasTrustRelationship(sourceProvider, targetProvider)) {
            return TrustPath.builder()
                .sourceProvider(sourceProvider)
                .targetProvider(targetProvider)
                .path(List.of(sourceProvider, targetProvider))
                .reachable(true)
                .build();
        }

        // Hub-and-spoke routing logic
        if ("hub".equals(sourceRole)) {
            // Hub to spoke: should have direct trust, otherwise unreachable
            return unreachablePath(sourceProvider, targetProvider);
        } else if ("hub".equals(targetRole)) {
            // Spoke to hub: should have direct trust, otherwise unreachable
            return unreachablePath(sourceProvider, targetProvider);
        } else {
            // Spoke to spoke: route through hub
            return computeSpokeToSpokePath(network, sourceProvider, targetProvider, source, target);
        }
    }

    private TrustPath computeSpokeToSpokePath(TrustNetwork network, String sourceProvider,
                                               String targetProvider, ProviderNode source, ProviderNode target) {
        // Find hub(s) that both spokes trust
        List<String> commonHubs = findCommonHubs(network, sourceProvider, targetProvider);

        if (commonHubs.isEmpty()) {
            logger.debugf("No common hub found between %s and %s", sourceProvider, targetProvider);
            return unreachablePath(sourceProvider, targetProvider);
        }

        // Use first common hub (in a production system, you might want to choose based on cost/latency)
        String hubId = commonHubs.get(0);

        // Verify bidirectional trust: spoke -> hub -> spoke
        if (network.hasTrustRelationship(sourceProvider, hubId) &&
            network.hasTrustRelationship(hubId, targetProvider)) {

            List<String> path = List.of(sourceProvider, hubId, targetProvider);
            logger.debugf("Computed spoke-to-spoke path: %s", path);

            return TrustPath.builder()
                .sourceProvider(sourceProvider)
                .targetProvider(targetProvider)
                .path(path)
                .reachable(true)
                .build();
        }

        return unreachablePath(sourceProvider, targetProvider);
    }

    private List<String> findCommonHubs(TrustNetwork network, String sourceProvider, String targetProvider) {
        List<String> commonHubs = new ArrayList<>();

        for (Map.Entry<String, ProviderNode> entry : network.getProviders().entrySet()) {
            String providerId = entry.getKey();
            ProviderNode node = entry.getValue();

            if ("hub".equals(node.getRole())) {
                // Check if both spokes have trust relationship with this hub
                if (network.hasTrustRelationship(sourceProvider, providerId) &&
                    network.hasTrustRelationship(providerId, targetProvider)) {
                    commonHubs.add(providerId);
                }
            }
        }

        return commonHubs;
    }

    @Override
    public boolean validateTopology(TrustNetwork network) {
        if (network == null) {
            return false;
        }

        // Find all hubs
        long hubCount = network.getProviders().values().stream()
            .filter(p -> "hub".equals(p.getRole()))
            .count();

        if (hubCount == 0) {
            logger.warnf("Hub-and-spoke topology requires at least one hub");
            return false;
        }

        // All valid for hub-and-spoke
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
