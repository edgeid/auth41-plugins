package org.apifocal.auth41.plugin.accounts;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for storing user account records.
 *
 * <p>Table: auth41_user_accounts
 * <p>Stores user information and provider associations for federated authentication.
 */
@Entity
@Table(name = "auth41_user_accounts", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_home_provider_id", columnList = "home_provider_id")
})
public class UserAccountEntity {

    @Id
    @Column(name = "user_identifier", length = 255, nullable = false)
    private String userIdentifier;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "home_provider_id", length = 255, nullable = false)
    private String homeProviderId;

    @Column(name = "attributes", columnDefinition = "TEXT")
    @Convert(converter = AttributesConverter.class)
    private Map<String, Object> attributes = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA requires default constructor
    protected UserAccountEntity() {
    }

    public UserAccountEntity(String userIdentifier, String homeProviderId) {
        this.userIdentifier = userIdentifier;
        this.homeProviderId = homeProviderId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public void setUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHomeProviderId() {
        return homeProviderId;
    }

    public void setHomeProviderId(String homeProviderId) {
        this.homeProviderId = homeProviderId;
    }

    public Map<String, Object> getAttributes() {
        return attributes != null ? java.util.Collections.unmodifiableMap(attributes) : java.util.Collections.emptyMap();
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Lifecycle callback
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Conversion methods

    /**
     * Convert entity to domain model.
     */
    public UserAccount toUserAccount() {
        return UserAccount.builder()
            .userIdentifier(userIdentifier)
            .email(email)
            .name(name)
            .homeProviderId(homeProviderId)
            .attributes(attributes != null ? new HashMap<>(attributes) : null)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }

    /**
     * Create entity from domain model.
     */
    public static UserAccountEntity fromUserAccount(UserAccount account) {
        UserAccountEntity entity = new UserAccountEntity(
            account.getUserIdentifier(),
            account.getHomeProviderId()
        );
        entity.setEmail(account.getEmail());
        entity.setName(account.getName());
        entity.setAttributes(new HashMap<>(account.getAttributes()));
        entity.setCreatedAt(account.getCreatedAt());
        entity.setUpdatedAt(account.getUpdatedAt());
        return entity;
    }
}
