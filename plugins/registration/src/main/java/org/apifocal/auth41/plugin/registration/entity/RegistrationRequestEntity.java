package org.apifocal.auth41.plugin.registration.entity;

import jakarta.persistence.*;
import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.apifocal.auth41.plugin.registration.util.AttributesConverter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for storing registration requests.
 *
 * <p>Table: auth41_registration_requests
 * <p>Stores account creation requests and tracks their approval status.
 */
@Entity
@Table(name = "auth41_registration_requests", indexes = {
    @Index(name = "idx_status_created", columnList = "status, created_at")
})
public class RegistrationRequestEntity {

    @Id
    @Column(name = "request_id", length = 255, nullable = false)
    private String requestId;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "realm_id", length = 255, nullable = false)
    private String realmId;

    @Column(name = "attributes", columnDefinition = "TEXT")
    @Convert(converter = AttributesConverter.class)
    private Map<String, Object> attributes = new HashMap<>();

    @Column(name = "status", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private RegistrationRequest.Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "user_id", length = 255)
    private String userId;

    // JPA requires default constructor
    protected RegistrationRequestEntity() {
    }

    public RegistrationRequestEntity(String requestId, String email, String realmId, Instant expiresAt) {
        this.requestId = requestId;
        this.email = email;
        this.realmId = realmId;
        this.status = RegistrationRequest.Status.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    // Getters and Setters

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public RegistrationRequest.Status getStatus() {
        return status;
    }

    public void setStatus(RegistrationRequest.Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Conversion methods

    /**
     * Convert entity to domain model.
     */
    public RegistrationRequest toRegistrationRequest() {
        return RegistrationRequest.builder()
                .requestId(requestId)
                .email(email)
                .realmId(realmId)
                .attributes(attributes)
                .status(status)
                .createdAt(createdAt)
                .approvedAt(approvedAt)
                .expiresAt(expiresAt)
                .userId(userId)
                .build();
    }

    /**
     * Convert domain model to entity.
     */
    public static RegistrationRequestEntity fromRegistrationRequest(RegistrationRequest request) {
        RegistrationRequestEntity entity = new RegistrationRequestEntity();
        entity.setRequestId(request.getRequestId());
        entity.setEmail(request.getEmail());
        entity.setRealmId(request.getRealmId());
        entity.setAttributes(new HashMap<>(request.getAttributes()));
        entity.setStatus(request.getStatus());
        entity.setCreatedAt(request.getCreatedAt());
        entity.setApprovedAt(request.getApprovedAt());
        entity.setExpiresAt(request.getExpiresAt());
        entity.setUserId(request.getUserId());
        return entity;
    }
}
