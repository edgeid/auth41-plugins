package org.apifocal.auth41.plugin.registration.config;

import org.junit.jupiter.api.Test;
import org.keycloak.Config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationConfigTest {

    @Test
    void shouldUseDefaultValues() {
        RegistrationConfig config = RegistrationConfig.withDefaults();

        assertThat(config.getInviteTtlSeconds()).isEqualTo(300);
        assertThat(config.getRequestTtlSeconds()).isEqualTo(600);
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(30);
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(300);
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(5);
    }

    @Test
    void shouldLoadFromConfigScope() {
        Config.Scope scope = mock(Config.Scope.class);
        when(scope.getInt("invite-ttl-seconds", 300)).thenReturn(600);
        when(scope.getInt("request-ttl-seconds", 600)).thenReturn(900);
        when(scope.getInt("approval-delay-seconds", 30)).thenReturn(60);
        when(scope.getInt("rate-limit-window-seconds", 300)).thenReturn(180);
        when(scope.getInt("polling-interval-seconds", 5)).thenReturn(10);

        RegistrationConfig config = RegistrationConfig.fromConfig(scope);

        assertThat(config.getInviteTtlSeconds()).isEqualTo(600);
        assertThat(config.getRequestTtlSeconds()).isEqualTo(900);
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(60);
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(180);
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(10);
    }

    @Test
    void shouldUseDefaultsWhenConfigNotProvided() {
        Config.Scope scope = mock(Config.Scope.class);
        when(scope.getInt("invite-ttl-seconds", 300)).thenReturn(300);
        when(scope.getInt("request-ttl-seconds", 600)).thenReturn(600);
        when(scope.getInt("approval-delay-seconds", 30)).thenReturn(30);
        when(scope.getInt("rate-limit-window-seconds", 300)).thenReturn(300);
        when(scope.getInt("polling-interval-seconds", 5)).thenReturn(5);

        RegistrationConfig config = RegistrationConfig.fromConfig(scope);

        // Should match defaults even when explicitly returned by mock
        assertThat(config.getInviteTtlSeconds()).isEqualTo(300);
        assertThat(config.getRequestTtlSeconds()).isEqualTo(600);
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(30);
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(300);
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(5);
    }

    @Test
    void shouldLoadPartialConfiguration() {
        Config.Scope scope = mock(Config.Scope.class);
        // Only override some values, rest should use defaults
        when(scope.getInt("invite-ttl-seconds", 300)).thenReturn(450);
        when(scope.getInt("request-ttl-seconds", 600)).thenReturn(600); // default
        when(scope.getInt("approval-delay-seconds", 30)).thenReturn(45);
        when(scope.getInt("rate-limit-window-seconds", 300)).thenReturn(300); // default
        when(scope.getInt("polling-interval-seconds", 5)).thenReturn(5); // default

        RegistrationConfig config = RegistrationConfig.fromConfig(scope);

        assertThat(config.getInviteTtlSeconds()).isEqualTo(450);      // custom
        assertThat(config.getRequestTtlSeconds()).isEqualTo(600);      // default
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(45);    // custom
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(300); // default
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(5);   // default
    }

    @Test
    void shouldHandleNullConfigScope() {
        RegistrationConfig config = RegistrationConfig.fromConfig(null);

        // Should use all defaults when config is null
        assertThat(config.getInviteTtlSeconds()).isEqualTo(300);
        assertThat(config.getRequestTtlSeconds()).isEqualTo(600);
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(30);
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(300);
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(5);
    }

    @Test
    void shouldProvideToStringWithAllValues() {
        RegistrationConfig config = RegistrationConfig.withDefaults();

        String toString = config.toString();
        assertThat(toString).contains("inviteTtl=300s");
        assertThat(toString).contains("requestTtl=600s");
        assertThat(toString).contains("approvalDelay=30s");
        assertThat(toString).contains("rateLimitWindow=300s");
        assertThat(toString).contains("pollingInterval=5s");
    }

    @Test
    void shouldAllowCustomApprovalDelay() {
        Config.Scope scope = mock(Config.Scope.class);
        // Very short approval delay for testing
        when(scope.getInt("invite-ttl-seconds", 300)).thenReturn(300);
        when(scope.getInt("request-ttl-seconds", 600)).thenReturn(600);
        when(scope.getInt("approval-delay-seconds", 30)).thenReturn(1);
        when(scope.getInt("rate-limit-window-seconds", 300)).thenReturn(300);
        when(scope.getInt("polling-interval-seconds", 5)).thenReturn(5);

        RegistrationConfig config = RegistrationConfig.fromConfig(scope);

        assertThat(config.getApprovalDelaySeconds()).isEqualTo(1);
    }

    @Test
    void shouldAllowLongTimeouts() {
        Config.Scope scope = mock(Config.Scope.class);
        // Very long timeouts for special use cases
        when(scope.getInt("invite-ttl-seconds", 300)).thenReturn(3600);      // 1 hour
        when(scope.getInt("request-ttl-seconds", 600)).thenReturn(7200);     // 2 hours
        when(scope.getInt("approval-delay-seconds", 30)).thenReturn(300);    // 5 minutes
        when(scope.getInt("rate-limit-window-seconds", 300)).thenReturn(900); // 15 minutes
        when(scope.getInt("polling-interval-seconds", 5)).thenReturn(30);    // 30 seconds

        RegistrationConfig config = RegistrationConfig.fromConfig(scope);

        assertThat(config.getInviteTtlSeconds()).isEqualTo(3600);
        assertThat(config.getRequestTtlSeconds()).isEqualTo(7200);
        assertThat(config.getApprovalDelaySeconds()).isEqualTo(300);
        assertThat(config.getRateLimitWindowSeconds()).isEqualTo(900);
        assertThat(config.getPollingIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void shouldProvideConvenientFactoryMethods() {
        // Test both factory methods exist and work
        RegistrationConfig config1 = RegistrationConfig.withDefaults();
        RegistrationConfig config2 = RegistrationConfig.fromConfig(null);

        // Both should produce the same default configuration
        assertThat(config1.getInviteTtlSeconds()).isEqualTo(config2.getInviteTtlSeconds());
        assertThat(config1.getRequestTtlSeconds()).isEqualTo(config2.getRequestTtlSeconds());
        assertThat(config1.getApprovalDelaySeconds()).isEqualTo(config2.getApprovalDelaySeconds());
        assertThat(config1.getRateLimitWindowSeconds()).isEqualTo(config2.getRateLimitWindowSeconds());
        assertThat(config1.getPollingIntervalSeconds()).isEqualTo(config2.getPollingIntervalSeconds());
    }
}
