package org.apifocal.auth41.plugin.broker;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a set of tokens received from a provider.
 *
 * <p>Contains access token, ID token, and optionally a refresh token along with metadata.
 */
public class TokenSet {

    private final String accessToken;
    private final String idToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final String scope;
    private final Map<String, Object> additionalClaims;

    private TokenSet(Builder builder) {
        this.accessToken = Objects.requireNonNull(builder.accessToken, "accessToken cannot be null");
        this.idToken = builder.idToken;
        this.refreshToken = builder.refreshToken;
        this.tokenType = builder.tokenType != null ? builder.tokenType : "Bearer";
        this.expiresIn = builder.expiresIn;
        this.scope = builder.scope;
        this.additionalClaims = Collections.unmodifiableMap(new HashMap<>(builder.additionalClaims));
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public Map<String, Object> getAdditionalClaims() {
        return Collections.unmodifiableMap(new HashMap<>(additionalClaims));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String idToken;
        private String refreshToken;
        private String tokenType;
        private long expiresIn;
        private String scope;
        private Map<String, Object> additionalClaims = new HashMap<>();

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder idToken(String idToken) {
            this.idToken = idToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder additionalClaim(String key, Object value) {
            this.additionalClaims.put(key, value);
            return this;
        }

        public Builder additionalClaims(Map<String, Object> additionalClaims) {
            this.additionalClaims.putAll(additionalClaims);
            return this;
        }

        public TokenSet build() {
            return new TokenSet(this);
        }
    }
}
