package org.apifocal.auth41.plugin.trustnetwork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.Config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads trust network configuration from various sources.
 *
 * Currently supports loading from:
 * - JSON files on classpath
 * - JSON strings
 * - JSON input streams
 *
 * Future support planned for:
 * - Keycloak Config.Scope (system properties) - TODO: implement config key discovery
 */
public class TrustNetworkConfigLoader {

    private static final Logger logger = Logger.getLogger(TrustNetworkConfigLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load trust network from Keycloak configuration.
     *
     * NOTE: Currently only supports topology type from config. Provider and trust relationship
     * discovery from config keys is not yet implemented. Use JSON-based loading for full functionality.
     *
     * Future expected configuration format:
     * spi-trust-network-config-based-network-<networkId>-topology-type=hub-and-spoke
     * spi-trust-network-config-based-network-<networkId>-provider-<providerId>-issuer=https://...
     * spi-trust-network-config-based-network-<networkId>-provider-<providerId>-role=hub
     * spi-trust-network-config-based-network-<networkId>-trust-<fromProvider>-<toProvider>=explicit
     *
     * TODO: Implement config key discovery mechanism for providers and trust relationships
     */
    public TrustNetwork loadFromConfig(Config.Scope config, String networkId) {
        logger.infof("Loading network %s from configuration", networkId);

        String prefix = "network-" + networkId + "-";

        // Get topology type
        String topologyType = config.get(prefix + "topology-type", "hub-and-spoke");

        TrustNetwork.Builder builder = TrustNetwork.builder()
            .networkId(networkId)
            .topologyType(topologyType);

        // Load providers
        loadProvidersFromConfig(config, prefix, builder);

        // Load trust relationships
        loadTrustRelationshipsFromConfig(config, prefix, builder);

        return builder.build();
    }

    private void loadProvidersFromConfig(Config.Scope config, String prefix, TrustNetwork.Builder builder) {
        String providerPrefix = prefix + "provider-";

        // Scan for provider configurations
        // We need to discover provider IDs from config keys
        // This is a simplified approach - in production, you'd want a more robust discovery mechanism

        for (String providerId : discoverProviderIds(config, providerPrefix)) {
            String issuer = config.get(providerPrefix + providerId + "-issuer");
            String role = config.get(providerPrefix + providerId + "-role", "spoke");

            if (issuer != null && !issuer.isEmpty()) {
                builder.addProvider(providerId, issuer, role);
                logger.debugf("Loaded provider: %s (issuer=%s, role=%s)", providerId, issuer, role);
            }
        }
    }

    private void loadTrustRelationshipsFromConfig(Config.Scope config, String prefix, TrustNetwork.Builder builder) {
        String trustPrefix = prefix + "trust-";

        // Scan for trust relationship configurations
        for (String key : discoverTrustKeys(config, trustPrefix)) {
            // Parse key format: trust-<fromProvider>-to-<toProvider>
            String[] parts = key.substring(trustPrefix.length()).split("-to-");
            if (parts.length == 2) {
                String fromProvider = parts[0];
                String toProvider = parts[1];
                String levelStr = config.get(key, "explicit");

                TrustLevel level = TrustLevel.valueOf(levelStr.toUpperCase());
                builder.addTrustRelationship(fromProvider, toProvider, level);
                logger.debugf("Loaded trust relationship: %s → %s (level=%s)", fromProvider, toProvider, level);
            }
        }
    }

    /**
     * Load trust network from JSON resource on classpath
     */
    public TrustNetwork loadFromResource(String resourcePath) throws IOException {
        logger.infof("Loading network from resource: %s", resourcePath);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return loadFromJson(is);
        }
    }

    /**
     * Load trust network from JSON input stream
     */
    public TrustNetwork loadFromJson(InputStream jsonInputStream) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonInputStream);
        return parseJsonNetwork(rootNode);
    }

    /**
     * Load trust network from JSON string
     */
    public TrustNetwork loadFromJsonString(String json) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        return parseJsonNetwork(rootNode);
    }

    private TrustNetwork parseJsonNetwork(JsonNode rootNode) {
        String networkId = rootNode.path("network_id").asText();
        String topologyType = rootNode.path("topology_type").asText("hub-and-spoke");

        TrustNetwork.Builder builder = TrustNetwork.builder()
            .networkId(networkId)
            .topologyType(topologyType);

        // Parse version if present
        if (rootNode.has("registry_version")) {
            String versionStr = rootNode.path("registry_version").asText();
            try {
                builder.version(Instant.parse(versionStr));
            } catch (Exception e) {
                logger.warnf("Failed to parse version timestamp: %s", versionStr);
            }
        }

        // Parse providers
        JsonNode providersNode = rootNode.path("providers");
        if (providersNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = providersNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String providerId = entry.getKey();
                JsonNode providerData = entry.getValue();

                String issuer = providerData.path("issuer").asText();
                String role = providerData.path("role").asText("spoke");

                if (issuer != null && !issuer.isEmpty()) {
                    builder.addProvider(providerId, issuer, role);
                }
            }
        }

        // Parse trust relationships
        JsonNode trustNode = rootNode.path("trust_relationships");
        if (trustNode.isArray()) {
            for (JsonNode edge : trustNode) {
                String fromProvider = edge.path("from").asText();
                String toProvider = edge.path("to").asText();
                String levelStr = edge.path("level").asText("explicit");

                if (!fromProvider.isEmpty() && !toProvider.isEmpty()) {
                    try {
                        TrustLevel level = TrustLevel.valueOf(levelStr.toUpperCase());
                        builder.addTrustRelationship(fromProvider, toProvider, level);
                    } catch (IllegalArgumentException e) {
                        logger.warnf("Invalid trust level '%s' for relationship from '%s' to '%s'; skipping...",
                            levelStr, fromProvider, toProvider
                        );
                    }
                }
            }
        }

        return builder.build();
    }

    /**
     * Discover provider IDs from configuration keys.
     *
     * TODO: Not yet implemented. Keycloak's Config.Scope doesn't provide a way to enumerate
     * all keys with a given prefix. Possible solutions:
     * 1. Use system properties directly (System.getProperties()) and filter by prefix
     * 2. Require explicit provider list in config (e.g., provider-ids=hub-a,provider-b,provider-c)
     * 3. Use realm attributes instead of Config.Scope for dynamic configuration
     *
     * @return Empty set (providers must be loaded via JSON until this is implemented)
     */
    private java.util.Set<String> discoverProviderIds(Config.Scope config, String providerPrefix) {
        // TODO: Implement config key discovery
        logger.warnf("Config-based provider discovery not yet implemented; returning empty set");
        return java.util.Set.of();
    }

    /**
     * Discover trust relationship keys from configuration.
     *
     * TODO: Not yet implemented. See discoverProviderIds() for details.
     *
     * @return Empty set (trust relationships must be loaded via JSON until this is implemented)
     */
    private java.util.Set<String> discoverTrustKeys(Config.Scope config, String trustPrefix) {
        // TODO: Implement config key discovery
        logger.warnf("Config-based trust relationship discovery not yet implemented; returning empty set");
        return java.util.Set.of();
    }
}
