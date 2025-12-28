package org.apifocal.auth41.plugin.backchannel.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * JSON representation of a CIBA authentication request written to the inbox.
 */
public class FileBackchannelRequest {

    private final String authReqId;
    private final String clientId;
    private final String scope;
    private final String loginHint;
    private final String bindingMessage;
    private final String userCode;
    private final Integer requestedExpiry;
    private final Instant createdAt;

    @JsonCreator
    public FileBackchannelRequest(
        @JsonProperty("authReqId") String authReqId,
        @JsonProperty("clientId") String clientId,
        @JsonProperty("scope") String scope,
        @JsonProperty("loginHint") String loginHint,
        @JsonProperty("bindingMessage") String bindingMessage,
        @JsonProperty("userCode") String userCode,
        @JsonProperty("requestedExpiry") Integer requestedExpiry,
        @JsonProperty("createdAt") Instant createdAt
    ) {
        this.authReqId = authReqId;
        this.clientId = clientId;
        this.scope = scope;
        this.loginHint = loginHint;
        this.bindingMessage = bindingMessage;
        this.userCode = userCode;
        this.requestedExpiry = requestedExpiry;
        this.createdAt = createdAt;
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
}
