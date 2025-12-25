package org.apifocal.auth41.plugin.accounts;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {

    @Test
    void testBuilderWithRequiredFields() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .build();

        assertThat(account.getUserIdentifier()).isEqualTo("user@example.com");
        assertThat(account.getHomeProviderId()).isEqualTo("provider-a");
        assertThat(account.getEmail()).isNull();
        assertThat(account.getName()).isNull();
        assertThat(account.getAttributes()).isEmpty();
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .name("John Doe")
            .homeProviderId("provider-a")
            .attribute("role", "admin")
            .attribute("department", "engineering")
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(account.getUserIdentifier()).isEqualTo("user@example.com");
        assertThat(account.getEmail()).isEqualTo("user@example.com");
        assertThat(account.getName()).isEqualTo("John Doe");
        assertThat(account.getHomeProviderId()).isEqualTo("provider-a");
        assertThat(account.getAttributes()).hasSize(2);
        assertThat(account.getAttributes().get("role")).isEqualTo("admin");
        assertThat(account.getAttributes().get("department")).isEqualTo("engineering");
        assertThat(account.getCreatedAt()).isEqualTo(now);
        assertThat(account.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void testBuilderWithAttributesMap() {
        Map<String, Object> attrs = Map.of(
            "key1", "value1",
            "key2", 123,
            "key3", true
        );

        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .attributes(attrs)
            .build();

        assertThat(account.getAttributes()).hasSize(3);
        assertThat(account.getAttributes().get("key1")).isEqualTo("value1");
        assertThat(account.getAttributes().get("key2")).isEqualTo(123);
        assertThat(account.getAttributes().get("key3")).isEqualTo(true);
    }

    @Test
    void testBuilderRequiresUserIdentifier() {
        assertThatThrownBy(() ->
            UserAccount.builder()
                .homeProviderId("provider-a")
                .build()
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("userIdentifier");
    }

    @Test
    void testBuilderRequiresHomeProviderId() {
        assertThatThrownBy(() ->
            UserAccount.builder()
                .userIdentifier("user@example.com")
                .build()
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("homeProviderId");
    }

    @Test
    void testAttributesAreImmutable() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .attribute("key", "value")
            .build();

        Map<String, Object> attrs = account.getAttributes();

        assertThatThrownBy(() ->
            attrs.put("new-key", "new-value")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testAttributesDefensiveCopy() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .attribute("key", "value")
            .build();

        // Get attributes twice
        Map<String, Object> attrs1 = account.getAttributes();
        Map<String, Object> attrs2 = account.getAttributes();

        // Should be different instances (defensive copy)
        assertThat(attrs1).isNotSameAs(attrs2);
        assertThat(attrs1).isEqualTo(attrs2);
    }

    @Test
    void testEqualsAndHashCode() {
        UserAccount account1 = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .build();

        UserAccount account2 = UserAccount.builder()
            .userIdentifier("user@example.com")
            .homeProviderId("provider-a")
            .name("Different Name")
            .build();

        UserAccount account3 = UserAccount.builder()
            .userIdentifier("different@example.com")
            .homeProviderId("provider-a")
            .build();

        // Same user identifier and provider -> equal
        assertThat(account1).isEqualTo(account2);
        assertThat(account1.hashCode()).isEqualTo(account2.hashCode());

        // Different user identifier -> not equal
        assertThat(account1).isNotEqualTo(account3);
    }

    @Test
    void testToString() {
        UserAccount account = UserAccount.builder()
            .userIdentifier("user@example.com")
            .email("user@example.com")
            .name("John Doe")
            .homeProviderId("provider-a")
            .build();

        String str = account.toString();
        assertThat(str).contains("user@example.com");
        assertThat(str).contains("John Doe");
        assertThat(str).contains("provider-a");
    }
}
