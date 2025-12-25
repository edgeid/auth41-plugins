package org.apifocal.auth41.plugin.broker;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of token validation.
 *
 * <p>Contains validation status and extracted claims from the token.
 */
public class TokenValidationResult {

    private final boolean valid;
    private final String subject;
    private final String issuer;
    private final long expiresAt;
    private final Map<String, Object> claims;
    private final String errorMessage;

    private TokenValidationResult(Builder builder) {
        this.valid = builder.valid;
        this.subject = builder.subject;
        this.issuer = builder.issuer;
        this.expiresAt = builder.expiresAt;
        this.claims = Collections.unmodifiableMap(new HashMap<>(builder.claims));
        this.errorMessage = builder.errorMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getClaims() {
        return Collections.unmodifiableMap(new HashMap<>(claims));
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TokenValidationResult invalid(String errorMessage) {
        return builder()
            .valid(false)
            .errorMessage(errorMessage)
            .build();
    }

    public static class Builder {
        private boolean valid;
        private String subject;
        private String issuer;
        private long expiresAt;
        private Map<String, Object> claims = new HashMap<>();
        private String errorMessage;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder expiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder claim(String key, Object value) {
            this.claims.put(key, value);
            return this;
        }

        public Builder claims(Map<String, Object> claims) {
            this.claims.putAll(claims);
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public TokenValidationResult build() {
            return new TokenValidationResult(this);
        }
    }
}
