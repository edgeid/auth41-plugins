package org.apifocal.auth41.plugin.trustnetwork;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a trust network graph with providers and their trust relationships.
 *
 * A trust network is a directed graph where:
 * - Nodes are OIDC providers
 * - Edges are trust relationships between providers
 * - Each network has a unique ID and topology type
 */
public class TrustNetwork {

    private final String networkId;
    private final String topologyType;
    private final Map<String, ProviderNode> providers;
    private final Set<TrustEdge> trustRelationships;
    private final Map<String, String> metadata;
    private final Instant version;

    private TrustNetwork(Builder builder) {
        this.networkId = Objects.requireNonNull(builder.networkId, "networkId cannot be null");
        this.topologyType = builder.topologyType;
        this.providers = Collections.unmodifiableMap(new java.util.HashMap<>(builder.providers));

        // Create a defensive copy as a HashSet, then make it unmodifiable
        this.trustRelationships = Collections.unmodifiableSet(new java.util.HashSet<>(builder.trustRelationships));

        this.metadata = Collections.unmodifiableMap(new java.util.HashMap<>(builder.metadata));
        this.version = builder.version != null ? builder.version : Instant.now();
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getTopologyType() {
        return topologyType;
    }

    public Map<String, ProviderNode> getProviders() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(providers));
    }

    public Set<TrustEdge> getTrustRelationships() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(trustRelationships));
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(metadata));
    }

    public Instant getVersion() {
        return version;
    }

    /**
     * Check if a provider is a member of this network
     */
    public boolean isMember(String providerId) {
        return providers.containsKey(providerId);
    }

    /**
     * Get provider node by ID
     */
    public ProviderNode getProvider(String providerId) {
        return providers.get(providerId);
    }

    /**
     * Check if there is a direct trust relationship from source to target
     */
    public boolean hasTrustRelationship(String fromProvider, String toProvider) {
        return trustRelationships.stream()
            .anyMatch(edge -> edge.getFromProvider().equals(fromProvider)
                && edge.getToProvider().equals(toProvider));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String networkId;
        private String topologyType;
        private final Map<String, ProviderNode> providers = new java.util.HashMap<>();
        private final Set<TrustEdge> trustRelationships = new java.util.HashSet<>();
        private final Map<String, String> metadata = new java.util.HashMap<>();
        private Instant version;

        public Builder networkId(String networkId) {
            this.networkId = networkId;
            return this;
        }

        public Builder topologyType(String topologyType) {
            this.topologyType = topologyType;
            return this;
        }

        public Builder addProvider(ProviderNode provider) {
            this.providers.put(provider.getProviderId(), provider);
            return this;
        }

        public Builder addProvider(String providerId, String issuer, String role) {
            return addProvider(ProviderNode.builder()
                .providerId(providerId)
                .issuer(issuer)
                .role(role)
                .build());
        }

        public Builder addTrustRelationship(TrustEdge edge) {
            this.trustRelationships.add(edge);
            return this;
        }

        public Builder addTrustRelationship(String fromProvider, String toProvider, TrustLevel level) {
            return addTrustRelationship(TrustEdge.builder()
                .fromProvider(fromProvider)
                .toProvider(toProvider)
                .level(level)
                .build());
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder version(Instant version) {
            this.version = version;
            return this;
        }

        public TrustNetwork build() {
            return new TrustNetwork(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustNetwork that = (TrustNetwork) o;
        return Objects.equals(networkId, that.networkId) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId, version);
    }

    @Override
    public String toString() {
        return "TrustNetwork{" +
               "networkId='" + networkId + '\'' +
               ", topologyType='" + topologyType + '\'' +
               ", providers=" + providers.size() +
               ", trustRelationships=" + trustRelationships.size() +
               ", version=" + version +
               '}';
    }
}
