package org.apifocal.auth41.plugin.themes.provider;

import org.apifocal.auth41.plugin.themes.config.ThemeMappingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.*;
import org.keycloak.theme.Theme;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth41ThemeSelectorProvider
 */
class Auth41ThemeSelectorProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private ClientModel client;

    @Mock
    private UserModel user;

    private ThemeMappingConfig config;
    private Auth41ThemeSelectorProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup basic mocks
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");

        // Create a simple config
        config = new ThemeMappingConfig();

        provider = new Auth41ThemeSelectorProvider(session, config);
    }

    @Test
    void testGetThemeName_ReturnsNullWhenNoConfigured() {
        String themeName = provider.getThemeName(Theme.Type.LOGIN);
        assertNull(themeName, "Should return null when no theme is configured");
    }

    @Test
    void testGetThemeName_WithRealmMapping() {
        // Configure a realm mapping
        ThemeMappingConfig configWithMapping = new ThemeMappingConfig();
        // Use reflection to add a realm mapping for testing
        try {
            java.lang.reflect.Field realmMappingsField = ThemeMappingConfig.class.getDeclaredField("realmMappings");
            realmMappingsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> realmMappings = (java.util.Map<String, String>) realmMappingsField.get(configWithMapping);
            realmMappings.put("test-realm", "custom-theme");
        } catch (Exception e) {
            fail("Failed to set up test configuration: " + e.getMessage());
        }
        
        // Create provider with the configured mapping
        Auth41ThemeSelectorProvider providerWithMapping = new Auth41ThemeSelectorProvider(session, configWithMapping);
        
        // Call getThemeName which should use the realm mapping
        String themeName = providerWithMapping.getThemeName(Theme.Type.LOGIN);
        
        // Verify the correct theme is returned based on realm mapping
        assertEquals("custom-theme", themeName, "Should return the theme mapped to test-realm");
    }

    @Test
    void testProvider_Closes() {
        assertDoesNotThrow(() -> provider.close());
    }

    @Test
    void testContextBuilder() {
        when(context.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("test-client");
        when(user.getUsername()).thenReturn("test-user");

        // Build context (note: user is not available from KeycloakContext)
        ThemeSelectionContext testContext = ThemeSelectionContext.builder()
                .realm(realm)
                .client(client)
                .user(user)
                .themeType(Theme.Type.LOGIN)
                .build();

        assertNotNull(testContext);
        assertEquals(realm, testContext.getRealm());
        assertEquals(client, testContext.getClient());
        assertEquals(user, testContext.getUser());
        assertEquals(Theme.Type.LOGIN, testContext.getThemeType());
    }
}
