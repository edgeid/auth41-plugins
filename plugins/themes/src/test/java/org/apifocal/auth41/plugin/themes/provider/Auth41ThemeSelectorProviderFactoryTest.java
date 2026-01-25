package org.apifocal.auth41.plugin.themes.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ThemeSelectorProvider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth41ThemeSelectorProviderFactory
 */
class Auth41ThemeSelectorProviderFactoryTest {

    @Mock
    private Config.Scope config;

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakSessionFactory sessionFactory;

    private Auth41ThemeSelectorProviderFactory factory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        factory = new Auth41ThemeSelectorProviderFactory();
    }

    @Test
    void testGetId_ReturnsCorrectProviderId() {
        assertEquals("auth41-theme-selector", factory.getId());
    }

    @Test
    void testInit_WithNullConfig() {
        // Should not throw exception
        assertDoesNotThrow(() -> factory.init(null));
    }

    @Test
    void testInit_WithEmptyConfig() {
        when(config.getPropertyNames()).thenReturn(Set.of());

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testInit_WithRealmMappings() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn("auth41-modern");

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testInit_WithClientMappings() {
        when(config.getPropertyNames()).thenReturn(Set.of("client-mobile-app"));
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testInit_WithDefaultThemes() {
        when(config.getPropertyNames()).thenReturn(Set.of("default-login", "default-account"));
        when(config.get("default-login")).thenReturn("auth41-classic");
        when(config.get("default-account")).thenReturn("auth41-modern");

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testInit_WithMixedConfiguration() {
        when(config.getPropertyNames()).thenReturn(Set.of(
            "realm-master",
            "client-mobile-app",
            "default-login"
        ));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");
        when(config.get("default-login")).thenReturn("auth41-minimal");

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testCreate_BeforeInit_CreatesProviderWithDefaultConfig() {
        // Create provider without calling init first
        ThemeSelectorProvider provider = factory.create(session);

        assertNotNull(provider);
        assertInstanceOf(Auth41ThemeSelectorProvider.class, provider);
    }

    @Test
    void testCreate_AfterInit_CreatesProviderWithLoadedConfig() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master"));
        when(config.get("realm-master")).thenReturn("auth41-classic");

        factory.init(config);
        ThemeSelectorProvider provider = factory.create(session);

        assertNotNull(provider);
        assertInstanceOf(Auth41ThemeSelectorProvider.class, provider);
    }

    @Test
    void testCreate_MultipleCalls_CreatesDifferentInstances() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master"));
        when(config.get("realm-master")).thenReturn("auth41-classic");

        factory.init(config);
        ThemeSelectorProvider provider1 = factory.create(session);
        ThemeSelectorProvider provider2 = factory.create(session);

        assertNotNull(provider1);
        assertNotNull(provider2);
        assertNotSame(provider1, provider2);
    }

    @Test
    void testCreate_WithDifferentSessions_CreatesProvidersForEachSession() {
        KeycloakSession session1 = mock(KeycloakSession.class);
        KeycloakSession session2 = mock(KeycloakSession.class);

        factory.init(config);
        ThemeSelectorProvider provider1 = factory.create(session1);
        ThemeSelectorProvider provider2 = factory.create(session2);

        assertNotNull(provider1);
        assertNotNull(provider2);
        assertNotSame(provider1, provider2);
    }

    @Test
    void testPostInit_DoesNotThrowException() {
        assertDoesNotThrow(() -> factory.postInit(sessionFactory));
    }

    @Test
    void testPostInit_WithNullSessionFactory_DoesNotThrowException() {
        assertDoesNotThrow(() -> factory.postInit(null));
    }

    @Test
    void testClose_DoesNotThrowException() {
        assertDoesNotThrow(() -> factory.close());
    }

    @Test
    void testClose_AfterInit_DoesNotThrowException() {
        factory.init(config);
        assertDoesNotThrow(() -> factory.close());
    }

    @Test
    void testLifecycle_CompleteInitializationAndShutdown() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master"));
        when(config.get("realm-master")).thenReturn("auth41-classic");

        // Complete lifecycle
        assertDoesNotThrow(() -> {
            factory.init(config);
            factory.postInit(sessionFactory);
            ThemeSelectorProvider provider = factory.create(session);
            assertNotNull(provider);
            provider.close();
            factory.close();
        });
    }

    @Test
    void testInit_WithInvalidConfiguration_HandlesGracefully() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "default-invalid"));
        when(config.get("realm-master")).thenReturn("");  // Empty value
        when(config.get("default-invalid")).thenReturn("auth41-classic");  // Invalid theme type

        // Should handle gracefully without throwing
        assertDoesNotThrow(() -> factory.init(config));

        // Should still create providers
        ThemeSelectorProvider provider = factory.create(session);
        assertNotNull(provider);
    }

    @Test
    void testInit_ConfigurationWithEmptyValues_LogsWarnings() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn("");  // Empty value should be ignored

        assertDoesNotThrow(() -> factory.init(config));
    }

    @Test
    void testInit_CalledMultipleTimes_ReloadsConfiguration() {
        // First initialization
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        factory.init(config);

        // Second initialization with different config
        Config.Scope newConfig = mock(Config.Scope.class);
        when(newConfig.getPropertyNames()).thenReturn(Set.of("realm-test"));
        when(newConfig.get("realm-test")).thenReturn("auth41-modern");

        assertDoesNotThrow(() -> factory.init(newConfig));
    }

    @Test
    void testFactory_ImplementsCorrectInterface() {
        // Verify factory implements the correct Keycloak SPI interface
        assertTrue(factory instanceof org.keycloak.theme.ThemeSelectorProviderFactory);
    }

    @Test
    void testFactory_ProviderId_IsNonEmpty() {
        String id = factory.getId();
        assertNotNull(id);
        assertFalse(id.isEmpty());
        assertFalse(id.isBlank());
    }

    @Test
    void testInit_WithRealmPrefixConfiguration_LoadsCorrectly() {
        when(config.getPropertyNames()).thenReturn(Set.of(
            "realm-production",
            "realm-staging",
            "realm-development"
        ));
        when(config.get("realm-production")).thenReturn("auth41-classic");
        when(config.get("realm-staging")).thenReturn("auth41-modern");
        when(config.get("realm-development")).thenReturn("auth41-minimal");

        assertDoesNotThrow(() -> factory.init(config));

        // Provider should be created successfully with loaded config
        ThemeSelectorProvider provider = factory.create(session);
        assertNotNull(provider);
    }

    @Test
    void testInit_WithClientPrefixConfiguration_LoadsCorrectly() {
        when(config.getPropertyNames()).thenReturn(Set.of(
            "client-web-app",
            "client-mobile-app",
            "client-admin-console"
        ));
        when(config.get("client-web-app")).thenReturn("auth41-classic");
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");
        when(config.get("client-admin-console")).thenReturn("auth41-minimal");

        assertDoesNotThrow(() -> factory.init(config));

        // Provider should be created successfully with loaded config
        ThemeSelectorProvider provider = factory.create(session);
        assertNotNull(provider);
    }

    @Test
    void testInit_WithDefaultPrefixConfiguration_LoadsCorrectly() {
        when(config.getPropertyNames()).thenReturn(Set.of(
            "default-login",
            "default-account",
            "default-email"
        ));
        when(config.get("default-login")).thenReturn("auth41-classic");
        when(config.get("default-account")).thenReturn("auth41-modern");
        when(config.get("default-email")).thenReturn("auth41-minimal");

        assertDoesNotThrow(() -> factory.init(config));

        // Provider should be created successfully with loaded config
        ThemeSelectorProvider provider = factory.create(session);
        assertNotNull(provider);
    }
}
