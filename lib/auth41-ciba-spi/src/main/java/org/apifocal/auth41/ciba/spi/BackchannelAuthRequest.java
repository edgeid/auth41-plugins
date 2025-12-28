package org.apifocal.auth41.ciba.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a CIBA backchannel authentication request.
 * This is the data model passed to BackchannelProvider implementations.
 */
public class BackchannelAuthRequest {

    private final String authReqId;
    private final String clientId;
    private final String scope;
    private final String loginHint;
    private final String bindingMessage;
    private final String userCode;
    private final Integer requestedExpiry;
    private final Instant createdAt;
    private final Map<String, Object> additionalParams;

    private BackchannelAuthRequest(Builder builder) {
        this.authReqId = Objects.requireNonNull(builder.authReqId, "authReqId must not be null");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId must not be null");
        this.scope = builder.scope;
        this.loginHint = builder.loginHint;
        this.bindingMessage = builder.bindingMessage;
        this.userCode = builder.userCode;
        this.requestedExpiry = builder.requestedExpiry;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.additionalParams = builder.additionalParams != null
            ? Collections.unmodifiableMap(builder.additionalParams)
            : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAuthReqId() {
        return authReqId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public String getBindingMessage() {
        return bindingMessage;
    }

    public String getUserCode() {
        return userCode;
    }

    public Integer getRequestedExpiry() {
        return requestedExpiry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getAdditionalParams() {
        return additionalParams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackchannelAuthRequest that = (BackchannelAuthRequest) o;
        return Objects.equals(authReqId, that.authReqId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authReqId);
    }

    @Override
    public String toString() {
        return "BackchannelAuthRequest{" +
                "authReqId='" + authReqId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", loginHint='" + loginHint + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    public static class Builder {
        private String authReqId;
        private String clientId;
        private String scope;
        private String loginHint;
        private String bindingMessage;
        private String userCode;
        private Integer requestedExpiry;
        private Instant createdAt;
        private Map<String, Object> additionalParams;

        public Builder authReqId(String authReqId) {
            this.authReqId = authReqId;
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

        public Builder loginHint(String loginHint) {
            this.loginHint = loginHint;
            return this;
        }

        public Builder bindingMessage(String bindingMessage) {
            this.bindingMessage = bindingMessage;
            return this;
        }

        public Builder userCode(String userCode) {
            this.userCode = userCode;
            return this;
        }

        public Builder requestedExpiry(Integer requestedExpiry) {
            this.requestedExpiry = requestedExpiry;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder additionalParams(Map<String, Object> additionalParams) {
            this.additionalParams = additionalParams;
            return this;
        }

        public BackchannelAuthRequest build() {
            return new BackchannelAuthRequest(this);
        }
    }
}
