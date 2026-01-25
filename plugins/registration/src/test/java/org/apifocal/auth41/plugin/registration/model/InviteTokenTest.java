package org.apifocal.auth41.plugin.registration.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class InviteTokenTest {

    @Test
    void shouldBuildInviteTokenWithAllFields() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);
        Instant usedAt = now.plus(2, ChronoUnit.MINUTES);

        InviteToken token = InviteToken.builder()
                .inviteToken("test-token-123")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .createdAt(now)
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .used(true)
                .build();

        assertThat(token.getInviteToken()).isEqualTo("test-token-123");
        assertThat(token.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(token.getRealmId()).isEqualTo("test-realm");
        assertThat(token.getCreatedAt()).isEqualTo(now);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getUsedAt()).isEqualTo(usedAt);
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void shouldUseDefaultCreatedAtWhenNotProvided() {
        Instant before = Instant.now();

        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        Instant after = Instant.now();

        assertThat(token.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void shouldThrowExceptionWhenInviteTokenIsNull() {
        assertThatThrownBy(() -> InviteToken.builder()
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("inviteToken cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenIpAddressIsNull() {
        assertThatThrownBy(() -> InviteToken.builder()
                .inviteToken("test-token")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ipAddress cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenRealmIdIsNull() {
        assertThatThrownBy(() -> InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("realmId cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenExpiresAtIsNull() {
        assertThatThrownBy(() -> InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt cannot be null");
    }

    @Test
    void shouldReturnTrueForExpiredToken() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void shouldReturnFalseForNonExpiredToken() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void shouldReturnTrueForValidToken() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .used(false)
                .build();

        assertThat(token.isValid()).isTrue();
    }

    @Test
    void shouldReturnFalseForValidWhenUsed() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .used(true)
                .build();

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void shouldReturnFalseForValidWhenExpired() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .used(false)
                .build();

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void shouldBeEqualWhenSameInviteToken() {
        InviteToken token1 = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        InviteToken token2 = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.2")
                .realmId("other-realm")
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(token1).isEqualTo(token2);
        assertThat(token1.hashCode()).isEqualTo(token2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentInviteToken() {
        InviteToken token1 = InviteToken.builder()
                .inviteToken("test-token-1")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        InviteToken token2 = InviteToken.builder()
                .inviteToken("test-token-2")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void shouldContainKeyFieldsInToString() {
        InviteToken token = InviteToken.builder()
                .inviteToken("test-token")
                .ipAddress("192.168.1.1")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        String toString = token.toString();
        assertThat(toString).contains("test-token");
        assertThat(toString).contains("192.168.1.1");
        assertThat(toString).contains("test-realm");
    }
}
