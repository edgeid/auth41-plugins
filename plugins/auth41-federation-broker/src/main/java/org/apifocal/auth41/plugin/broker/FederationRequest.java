package org.apifocal.auth41.plugin.broker;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a federated authentication request.
 *
 * <p>Contains all information needed to route an authentication request through the federation,
 * including user identifier, scopes, and the trust path to the home provider.
 */
public class FederationRequest {

    private final String userIdentifier;
    private final String homeProviderId;
    private final String currentProviderId;
    private final String clientId;
    private final String scope;
    private final String redirectUri;
    private final String state;
    private final String nonce;
    private final Map<String, String> additionalParams;

    private FederationRequest(Builder builder) {
        this.userIdentifier = Objects.requireNonNull(builder.userIdentifier, "userIdentifier cannot be null");
        this.homeProviderId = Objects.requireNonNull(builder.homeProviderId, "homeProviderId cannot be null");
        this.currentProviderId = Objects.requireNonNull(builder.currentProviderId, "currentProviderId cannot be null");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId cannot be null");
        this.scope = builder.scope != null ? builder.scope : "openid";
        this.redirectUri = builder.redirectUri;
        this.state = builder.state;
        this.nonce = builder.nonce;
        this.additionalParams = Collections.unmodifiableMap(new HashMap<>(builder.additionalParams));
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public String getHomeProviderId() {
        return homeProviderId;
    }

    public String getCurrentProviderId() {
        return currentProviderId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getState() {
        return state;
    }

    public String getNonce() {
        return nonce;
    }

    public Map<String, String> getAdditionalParams() {
        return Collections.unmodifiableMap(new HashMap<>(additionalParams));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userIdentifier;
        private String homeProviderId;
        private String currentProviderId;
        private String clientId;
        private String scope;
        private String redirectUri;
        private String state;
        private String nonce;
        private Map<String, String> additionalParams = new HashMap<>();

        public Builder userIdentifier(String userIdentifier) {
            this.userIdentifier = userIdentifier;
            return this;
        }

        public Builder homeProviderId(String homeProviderId) {
            this.homeProviderId = homeProviderId;
            return this;
        }

        public Builder currentProviderId(String currentProviderId) {
            this.currentProviderId = currentProviderId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder redirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder additionalParam(String key, String value) {
            this.additionalParams.put(key, value);
            return this;
        }

        public Builder additionalParams(Map<String, String> additionalParams) {
            this.additionalParams.putAll(additionalParams);
            return this;
        }

        public FederationRequest build() {
            return new FederationRequest(this);
        }
    }
}
