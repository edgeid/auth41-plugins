package org.apifocal.auth41.plugin.trustnetwork;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a provider node in the trust network.
 *
 * Each provider has:
 * - Unique provider ID
 * - Issuer URL (OIDC issuer identifier)
 * - Role (e.g., "hub", "spoke")
 * - Provider metadata (JWKS URI, endpoints, etc.)
 * - Custom attributes
 */
public class ProviderNode {

    private final String providerId;
    private final String issuer;
    private final ProviderMetadata metadata;
    private final Map<String, String> attributes;

    private ProviderNode(Builder builder) {
        this.providerId = Objects.requireNonNull(builder.providerId, "providerId cannot be null");
        this.issuer = Objects.requireNonNull(builder.issuer, "issuer cannot be null");
        this.metadata = builder.metadata != null ? builder.metadata : new ProviderMetadata();
        this.attributes = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.attributes));
    }

    public String getProviderId() {
        return providerId;
    }

    public String getIssuer() {
        return issuer;
    }

    public ProviderMetadata getMetadata() {
        return metadata;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Get provider role (e.g., "hub", "spoke")
     */
    public String getRole() {
        return attributes.get("role");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String providerId;
        private String issuer;
        private ProviderMetadata metadata;
        private final Map<String, String> attributes = new ConcurrentHashMap<>();

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder metadata(ProviderMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder role(String role) {
            this.attributes.put("role", role);
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public ProviderNode build() {
            return new ProviderNode(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProviderNode that = (ProviderNode) o;
        return Objects.equals(providerId, that.providerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId);
    }

    @Override
    public String toString() {
        return "ProviderNode{" +
               "providerId='" + providerId + '\'' +
               ", issuer='" + issuer + '\'' +
               ", role='" + getRole() + '\'' +
               '}';
    }
}
