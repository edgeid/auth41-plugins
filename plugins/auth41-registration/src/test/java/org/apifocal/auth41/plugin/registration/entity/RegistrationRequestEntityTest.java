package org.apifocal.auth41.plugin.registration.entity;

import org.apifocal.auth41.plugin.registration.model.RegistrationRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class RegistrationRequestEntityTest {

    @Test
    void shouldConvertEntityToDomainModel() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);
        Instant approvedAt = now.plus(5, ChronoUnit.MINUTES);

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("firstName", "John");
        attrs.put("lastName", "Doe");

        RegistrationRequestEntity entity = new RegistrationRequestEntity();
        entity.setRequestId("req-123");
        entity.setEmail("john@example.com");
        entity.setRealmId("test-realm");
        entity.setAttributes(attrs);
        entity.setStatus(RegistrationRequest.Status.APPROVED);
        entity.setCreatedAt(now);
        entity.setApprovedAt(approvedAt);
        entity.setExpiresAt(expiresAt);
        entity.setUserId("user-456");

        RegistrationRequest request = entity.toRegistrationRequest();

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
    void shouldConvertDomainModelToEntity() {
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

        RegistrationRequestEntity entity = RegistrationRequestEntity.fromRegistrationRequest(request);

        assertThat(entity.getRequestId()).isEqualTo("req-123");
        assertThat(entity.getEmail()).isEqualTo("john@example.com");
        assertThat(entity.getRealmId()).isEqualTo("test-realm");
        assertThat(entity.getAttributes()).containsEntry("firstName", "John");
        assertThat(entity.getAttributes()).containsEntry("lastName", "Doe");
        assertThat(entity.getStatus()).isEqualTo(RegistrationRequest.Status.APPROVED);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getUserId()).isEqualTo("user-456");
    }

    @Test
    void shouldRoundTripConversion() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("firstName", "John");
        attrs.put("custom", "value");

        RegistrationRequest original = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .attributes(attrs)
                .status(RegistrationRequest.Status.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        RegistrationRequestEntity entity = RegistrationRequestEntity.fromRegistrationRequest(original);
        RegistrationRequest converted = entity.toRegistrationRequest();

        assertThat(converted.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(converted.getEmail()).isEqualTo(original.getEmail());
        assertThat(converted.getRealmId()).isEqualTo(original.getRealmId());
        assertThat(converted.getAttributes()).isEqualTo(original.getAttributes());
        assertThat(converted.getStatus()).isEqualTo(original.getStatus());
        assertThat(converted.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(converted.getExpiresAt()).isEqualTo(original.getExpiresAt());
    }

    @Test
    void shouldCreateEntityWithConstructor() {
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        RegistrationRequestEntity entity = new RegistrationRequestEntity(
                "req-123",
                "john@example.com",
                "test-realm",
                expiresAt
        );

        assertThat(entity.getRequestId()).isEqualTo("req-123");
        assertThat(entity.getEmail()).isEqualTo("john@example.com");
        assertThat(entity.getRealmId()).isEqualTo("test-realm");
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getStatus()).isEqualTo(RegistrationRequest.Status.PENDING);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getAttributes()).isEmpty();
    }

    @Test
    void shouldHandleEmptyAttributes() {
        RegistrationRequest request = RegistrationRequest.builder()
                .requestId("req-123")
                .email("john@example.com")
                .realmId("test-realm")
                .status(RegistrationRequest.Status.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();

        RegistrationRequestEntity entity = RegistrationRequestEntity.fromRegistrationRequest(request);
        RegistrationRequest converted = entity.toRegistrationRequest();

        assertThat(converted.getAttributes()).isEmpty();
    }
}
