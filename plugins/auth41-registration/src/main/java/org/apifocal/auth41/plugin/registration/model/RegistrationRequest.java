package org.apifocal.auth41.plugin.registration.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable registration request for account creation workflow.
 *
 * <p>Stores user registration data and tracks approval status.
 * Processed by approval processor to create Keycloak users.
 *
 * <p>Design: Immutable with builder pattern, defensive copying for attributes map.
 */
public class RegistrationRequest {

    private final String requestId;
    private final String email;
    private final String realmId;
    private final Map<String, Object> attributes;
    private final Status status;
    private final Instant createdAt;
    private final Instant approvedAt;
    private final Instant expiresAt;
    private final String userId;

    /**
     * Registration request status.
     */
    public enum Status {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED,
        ERROR
    }

    /**
     * Private constructor - use builder.
     */
    private RegistrationRequest(Builder builder) {
        this.requestId = Objects.requireNonNull(builder.requestId, "requestId cannot be null");
        this.email = Objects.requireNonNull(builder.email, "email cannot be null");
        this.realmId = Objects.requireNonNull(builder.realmId, "realmId cannot be null");
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.approvedAt = builder.approvedAt;
        this.expiresAt = Objects.requireNonNull(builder.expiresAt, "expiresAt cannot be null");
        this.userId = builder.userId;
    }

    // Getters

    /**
     * @return Unique request ID (UUID)
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * @return User's email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * @return Realm ID where registration was requested
     */
    public String getRealmId() {
        return realmId;
    }

    /**
     * @return User profile attributes (defensive copy)
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * @return Current status of the request
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return Timestamp when this request was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @return Timestamp when this request was approved (null if not approved)
     */
    public Instant getApprovedAt() {
        return approvedAt;
    }

    /**
     * @return Timestamp when this request expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * @return Keycloak user ID (null if not yet created)
     */
    public String getUserId() {
        return userId;
    }

    /**
     * @return Whether this request has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * @return Whether this request is pending
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * @return Whether this request is approved
     */
    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    // Builder

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String email;
        private String realmId;
        private Map<String, Object> attributes = new HashMap<>();
        private Status status;
        private Instant createdAt;
        private Instant approvedAt;
        private Instant expiresAt;
        private String userId;

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder realmId(String realmId) {
            this.realmId = realmId;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = new HashMap<>(attributes);
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder approvedAt(Instant approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public RegistrationRequest build() {
            return new RegistrationRequest(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistrationRequest that = (RegistrationRequest) o;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "RegistrationRequest{" +
                "requestId='" + requestId + '\'' +
                ", email='" + email + '\'' +
                ", realmId='" + realmId + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
