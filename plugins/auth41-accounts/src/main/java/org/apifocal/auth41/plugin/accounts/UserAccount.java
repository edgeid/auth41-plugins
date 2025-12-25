package org.apifocal.auth41.plugin.accounts;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable user account record for federated authentication.
 *
 * <p>Stores user information and tracks which provider has custody of the user's account.
 * Used by discovery service to route authentication requests to the correct home provider.
 *
 * <p>Design: Immutable with builder pattern, defensive copying in getters.
 */
public class UserAccount {

    private final String userIdentifier;
    private final String email;
    private final String name;
    private final String homeProviderId;
    private final Map<String, Object> attributes;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Private constructor - use builder.
     */
    private UserAccount(Builder builder) {
        this.userIdentifier = Objects.requireNonNull(builder.userIdentifier, "userIdentifier cannot be null");
        this.email = builder.email;
        this.name = builder.name;
        this.homeProviderId = Objects.requireNonNull(builder.homeProviderId, "homeProviderId cannot be null");
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    // Getters

    /**
     * @return Unique identifier for this user (email or DID)
     */
    public String getUserIdentifier() {
        return userIdentifier;
    }

    /**
     * @return User's email address (optional)
     */
    public String getEmail() {
        return email;
    }

    /**
     * @return User's display name (optional)
     */
    public String getName() {
        return name;
    }

    /**
     * @return ID of the provider that has custody of this user's account
     */
    public String getHomeProviderId() {
        return homeProviderId;
    }

    /**
     * @return User profile attributes (defensive copy)
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * @return Timestamp when this record was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @return Timestamp when this record was last updated
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Builder

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userIdentifier;
        private String email;
        private String name;
        private String homeProviderId;
        private final Map<String, Object> attributes = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder userIdentifier(String userIdentifier) {
            this.userIdentifier = userIdentifier;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder homeProviderId(String homeProviderId) {
            this.homeProviderId = homeProviderId;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserAccount build() {
            return new UserAccount(this);
        }
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAccount that = (UserAccount) o;
        return Objects.equals(userIdentifier, that.userIdentifier) &&
               Objects.equals(homeProviderId, that.homeProviderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userIdentifier, homeProviderId);
    }

    @Override
    public String toString() {
        return "UserAccount{" +
                "userIdentifier='" + userIdentifier + '\'' +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", homeProviderId='" + homeProviderId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
