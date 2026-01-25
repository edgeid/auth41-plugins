package org.apifocal.auth41.plugin.registration.entity;

import jakarta.persistence.*;
import org.apifocal.auth41.plugin.registration.model.InviteToken;

import java.time.Instant;

/**
 * JPA entity for storing invite tokens.
 *
 * <p>Table: auth41_invite_tokens
 * <p>Stores invite tokens used for rate-limited registration workflow.
 */
@Entity
@Table(name = "auth41_invite_tokens", indexes = {
    @Index(name = "idx_ip_created", columnList = "ip_address, created_at")
})
public class InviteTokenEntity {

    @Id
    @Column(name = "invite_token", length = 255, nullable = false)
    private String inviteToken;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(name = "realm_id", length = 255, nullable = false)
    private String realmId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    // JPA requires default constructor
    protected InviteTokenEntity() {
    }

    public InviteTokenEntity(String inviteToken, String ipAddress, String realmId, Instant expiresAt) {
        this.inviteToken = inviteToken;
        this.ipAddress = ipAddress;
        this.realmId = realmId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.used = false;
    }

    // Getters and Setters

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    // Conversion methods

    /**
     * Convert entity to domain model.
     */
    public InviteToken toInviteToken() {
        return InviteToken.builder()
                .inviteToken(inviteToken)
                .ipAddress(ipAddress)
                .realmId(realmId)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .used(used)
                .build();
    }

    /**
     * Convert domain model to entity.
     */
    public static InviteTokenEntity fromInviteToken(InviteToken token) {
        InviteTokenEntity entity = new InviteTokenEntity();
        entity.setInviteToken(token.getInviteToken());
        entity.setIpAddress(token.getIpAddress());
        entity.setRealmId(token.getRealmId());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.getUsedAt());
        entity.setUsed(token.isUsed());
        return entity;
    }
}
