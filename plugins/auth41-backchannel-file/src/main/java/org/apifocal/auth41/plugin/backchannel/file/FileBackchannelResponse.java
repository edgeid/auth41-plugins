package org.apifocal.auth41.plugin.backchannel.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * JSON representation of a CIBA authentication response read from the outbox.
 * External test processes write this file to simulate user authentication responses.
 */
public class FileBackchannelResponse {

    private final String authReqId;
    private final String status;
    private final String userId;
    private final String scope;
    private final String errorCode;
    private final String errorDescription;
    private final Instant updatedAt;

    @JsonCreator
    public FileBackchannelResponse(
        @JsonProperty("authReqId") String authReqId,
        @JsonProperty("status") String status,
        @JsonProperty("userId") String userId,
        @JsonProperty("scope") String scope,
        @JsonProperty("errorCode") String errorCode,
        @JsonProperty("errorDescription") String errorDescription,
        @JsonProperty("updatedAt") Instant updatedAt
    ) {
        this.authReqId = authReqId;
        this.status = status;
        this.userId = userId;
        this.scope = scope;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public String getAuthReqId() {
        return authReqId;
    }

    public String getStatus() {
        return status;
    }

    public String getUserId() {
        return userId;
    }

    public String getScope() {
        return scope;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Helper method to create an APPROVED response
     */
    public static FileBackchannelResponse approved(String authReqId, String userId, String scope) {
        return new FileBackchannelResponse(
            authReqId,
            "APPROVED",
            userId,
            scope,
            null,
            null,
            Instant.now()
        );
    }

    /**
     * Helper method to create a DENIED response
     */
    public static FileBackchannelResponse denied(String authReqId) {
        return new FileBackchannelResponse(
            authReqId,
            "DENIED",
            null,
            null,
            "access_denied",
            "User denied the authentication request",
            Instant.now()
        );
    }

    /**
     * Helper method to create an EXPIRED response
     */
    public static FileBackchannelResponse expired(String authReqId) {
        return new FileBackchannelResponse(
            authReqId,
            "EXPIRED",
            null,
            null,
            "expired_token",
            "Authentication request has expired",
            Instant.now()
        );
    }
}
