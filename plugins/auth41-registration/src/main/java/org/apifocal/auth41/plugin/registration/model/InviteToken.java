package org.apifocal.auth41.plugin.registration.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable invite token for rate-limited registration requests.
 *
 * <p>Generated in response to invite requests and validated before allowing account registration.
 * Used to prevent abuse through IP-based rate limiting.
 *
 * <p>Design: Immutable with builder pattern.
 */
public class InviteToken {

    private final String inviteToken;
    private final String ipAddress;
    private final String realmId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant usedAt;
    private final boolean used;

    /**
     * Private constructor - use builder.
     */
    private InviteToken(Builder builder) {
        this.inviteToken = Objects.requireNonNull(builder.inviteToken, "inviteToken cannot be null");
        this.ipAddress = Objects.requireNonNull(builder.ipAddress, "ipAddress cannot be null");
        this.realmId = Objects.requireNonNull(builder.realmId, "realmId cannot be null");
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(builder.expiresAt, "expiresAt cannot be null");
        this.usedAt = builder.usedAt;
        this.used = builder.used;
    }

    // Getters

    /**
     * @return Unique invite token (UUID)
     */
    public String getInviteToken() {
        return inviteToken;
    }

    /**
     * @return IP address that requested this invite
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @return Realm ID where this invite was requested
     */
    public String getRealmId() {
        return realmId;
    }

    /**
     * @return Timestamp when this invite was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @return Timestamp when this invite expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * @return Timestamp when this invite was used (null if not used)
     */
    public Instant getUsedAt() {
        return usedAt;
    }

    /**
     * @return Whether this invite has been used
     */
    public boolean isUsed() {
        return used;
    }

    /**
     * @return Whether this invite has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * @return Whether this invite is valid (not used and not expired)
     */
    public boolean isValid() {
        return !used && !isExpired();
    }

    // Builder

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String inviteToken;
        private String ipAddress;
        private String realmId;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant usedAt;
        private boolean used;

        private Builder() {
        }

        public Builder inviteToken(String inviteToken) {
            this.inviteToken = inviteToken;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder realmId(String realmId) {
            this.realmId = realmId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder usedAt(Instant usedAt) {
            this.usedAt = usedAt;
            return this;
        }

        public Builder used(boolean used) {
            this.used = used;
            return this;
        }

        public InviteToken build() {
            return new InviteToken(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InviteToken that = (InviteToken) o;
        return Objects.equals(inviteToken, that.inviteToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inviteToken);
    }

    @Override
    public String toString() {
        return "InviteToken{" +
                "inviteToken='" + inviteToken + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", realmId='" + realmId + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", used=" + used +
                '}';
    }
}
