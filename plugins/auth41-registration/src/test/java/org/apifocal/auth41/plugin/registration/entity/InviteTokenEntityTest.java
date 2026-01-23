package org.apifocal.auth41.plugin.registration.entity;

import org.apifocal.auth41.plugin.registration.model.InviteToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class InviteTokenEntityTest {

    @Test
    void shouldConvertEntityToDomainModel() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);
        Instant usedAt = now.plus(2, ChronoUnit.MINUTES);

        InviteTokenEntity entity = new InviteTokenEntity();
        entity.setInviteToken("test-token");
        entity.setIpAddress("192.168.1.1");
        entity.setRealmId("test-realm");
        entity.setCreatedAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setUsedAt(usedAt);
        entity.setUsed(true);

        InviteToken token = entity.toInviteToken();

        assertThat(token.getInviteToken()).isEqualTo("test-token");
        assertThat(token.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(token.getRealmId()).isEqualTo("test-realm");
        assertThat(token.getCreatedAt()).isEqualTo(now);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void shouldConvertDomainModelToEntity() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);
        Instant usedAt = now.plus(2, ChronoUnit.MINUTES);

        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(now)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .used(true)
                .build();

        InviteTokenEntity entity = InviteTokenEntity.fromInviteToken(token);

        assertThat(entity.getInviteToken()).isEqualTo("test-token");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(entity.getRealmId()).isEqualTo("test-realm");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getUsedAt()).isEqualTo(usedAt);
        assertThat(entity.isUsed()).isTrue();
    }

    @Test
    void shouldRoundTripConversion() {
        InviteToken original = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .usedAt(Instant.now().plus(2, ChronoUnit.MINUTES))
                .used(true)
                .build();

        InviteTokenEntity entity = InviteTokenEntity.fromInviteToken(original);
        InviteToken converted = entity.toInviteToken();

        assertThat(converted.getInviteToken()).isEqualTo(original.getInviteToken());
        assertThat(converted.getIpAddress()).isEqualTo(original.getIpAddress());
        assertThat(converted.getRealmId()).isEqualTo(original.getRealmId());
        assertThat(converted.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(converted.getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(converted.getUsedAt()).isEqualTo(original.getUsedAt());
        assertThat(converted.isUsed()).isEqualTo(original.isUsed());
    }

    @Test
    void shouldCreateEntityWithConstructor() {
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        InviteTokenEntity entity = new InviteTokenEntity("test-token", "192.168.1.1", "test-realm", expiresAt);

        assertThat(entity.getInviteToken()).isEqualTo("test-token");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(entity.getRealmId()).isEqualTo("test-realm");
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.isUsed()).isFalse();
    }
}
