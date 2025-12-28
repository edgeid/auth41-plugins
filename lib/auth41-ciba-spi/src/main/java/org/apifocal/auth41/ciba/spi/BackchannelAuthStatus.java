package org.apifocal.auth41.ciba.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the status of a CIBA backchannel authentication request.
 * Returned by BackchannelProvider when checking authentication status.
 */
public class BackchannelAuthStatus {

    public enum Status {
        /**
         * Authentication is still pending user action
         */
        PENDING,

        /**
         * User has approved the authentication
         */
        APPROVED,

        /**
         * User has denied the authentication
         */
        DENIED,

        /**
         * Authentication request has expired
         */
        EXPIRED,

        /**
         * An error occurred during authentication
         */
        ERROR
    }

    private final String authReqId;
    private final Status status;
    private final String userId;
    private final String errorCode;
    private final String errorDescription;
    private final Instant updatedAt;

    private BackchannelAuthStatus(Builder builder) {
        this.authReqId = Objects.requireNonNull(builder.authReqId, "authReqId must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.userId = builder.userId;
        this.errorCode = builder.errorCode;
        this.errorDescription = builder.errorDescription;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAuthReqId() {
        return authReqId;
    }

    public Status getStatus() {
        return status;
    }

    public String getUserId() {
        return userId;
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

    public boolean isComplete() {
        return status != Status.PENDING;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BackchannelAuthStatus that = (BackchannelAuthStatus) o;
        return Objects.equals(authReqId, that.authReqId) &&
               status == that.status &&
               Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authReqId, status, updatedAt);
    }

    @Override
    public String toString() {
        return "BackchannelAuthStatus{" +
                "authReqId='" + authReqId + '\'' +
                ", status=" + status +
                ", userId='" + userId + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }

    public static class Builder {
        private String authReqId;
        private Status status;
        private String userId;
        private String errorCode;
        private String errorDescription;
        private Instant updatedAt;

        public Builder authReqId(String authReqId) {
            this.authReqId = authReqId;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public BackchannelAuthStatus build() {
            return new BackchannelAuthStatus(this);
        }
    }
}
