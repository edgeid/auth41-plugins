package org.apifocal.auth41.plugin.registration.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RegistrationRequestTest {

    @Test
    void shouldBuildRegistrationRequestWithAllFields() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);
        Instant approvedAt = now.plus(5, ChronoUnit.MINUTES);

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("firstName", "John");
        attrs.put("lastName", "Doe");

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .attributes(attrs)
                .status(RegistrationRequest.Status.APPROVED)
                .createdAt(now)
                .approvedAt(approvedAt)
                .expiresAt(expiresAt)
                .userId("user-456")
                .build();

        assertThat(request.getRequestId()).isEqualTo("req-123");
        assertThat(request.getEmail()).isEqualTo("john@example.com");
        assertThat(request.getRealmId()).isEqualTo("test-realm");
        assertThat(request.getAttributes()).containsEntry("firstName", "John");
        assertThat(request.getAttributes()).containsEntry("lastName", "Doe");
        assertThat(request.getStatus()).isEqualTo(RegistrationRequest.Status.APPROVED);
        assertThat(request.getCreatedAt()).isEqualTo(now);
        assertThat(request.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(request.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(request.getUserId()).isEqualTo("user-456");
    }

    @Test
    void shouldUseDefaultCreatedAtWhenNotProvided() {
        Instant before = Instant.now();

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        Instant after = Instant.now();

        assertThat(request.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void shouldProvideImmutableAttributesMap() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("firstName", "John");

        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .attributes(attrs)
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        // Modify original map
        attrs.put("lastName", "Doe");

        // Request should not be affected
        assertThat(request.getAttributes()).hasSize(1);
        assertThat(request.getAttributes()).containsOnlyKeys("firstName");

        // Returned map should be unmodifiable
        assertThatThrownBy(() -> request.getAttributes().put("test", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldSupportAttributeBuilderMethod() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .attribute("firstName", "John")
                .attribute("lastName", "Doe")
                .attribute("age", 30)
                .build();

        assertThat(request.getAttributes()).hasSize(3);
        assertThat(request.getAttributes()).containsEntry("firstName", "John");
        assertThat(request.getAttributes()).containsEntry("lastName", "Doe");
        assertThat(request.getAttributes()).containsEntry("age", 30);
    }

    @Test
    void shouldThrowExceptionWhenRequestIdIsNull() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestId cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenEmailIsNull() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .requestId("req-123")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenRealmIdIsNull() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("realmId cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenStatusIsNull() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenExpiresAtIsNull() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt cannot be null");
    }

    @Test
    void shouldReturnTrueForExpiredRequest() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isExpired()).isTrue();
    }

    @Test
    void shouldReturnFalseForNonExpiredRequest() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isExpired()).isFalse();
    }

    @Test
    void shouldReturnTrueForIsPending() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isPending()).isTrue();
    }

    @Test
    void shouldReturnFalseForIsPendingWhenApproved() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.APPROVED)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isPending()).isFalse();
    }

    @Test
    void shouldReturnTrueForIsApproved() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.APPROVED)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isApproved()).isTrue();
    }

    @Test
    void shouldReturnFalseForIsApprovedWhenPending() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request.isApproved()).isFalse();
    }

    @Test
    void shouldBeEqualWhenSameRequestId() {
        RegistrationRequest request1 = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        RegistrationRequest request2 = RegistrationRequest.builder()
                .requestId("req-123")
                .email("jane@example.com")
                .realmId("other-realm")
                .status(RegistrationRequest.Status.APPROVED)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();

        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentRequestId() {
        RegistrationRequest request1 = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        RegistrationRequest request2 = RegistrationRequest.builder()
                .requestId("req-456")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        assertThat(request1).isNotEqualTo(request2);
    }

    @Test
    void shouldContainKeyFieldsInToString() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        String toString = request.toString();
        assertThat(toString).contains("req-123");
        assertThat(toString).contains("john@example.com");
        assertThat(toString).contains("test-realm");
        assertThat(toString).contains("PENDING");
    }
}
