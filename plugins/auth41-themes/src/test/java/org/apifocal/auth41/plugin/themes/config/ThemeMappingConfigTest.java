package org.apifocal.auth41.plugin.themes.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.RealmModel;
import org.keycloak.theme.Theme;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ThemeMappingConfig
 */
class ThemeMappingConfigTest {

    @Mock
    private Config.Scope config;

    @Mock
    private RealmModel realm;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testLoadFromConfig_WithNullConfig() {
        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(null);

        assertNotNull(result);
        assertTrue(result.getRealmMappings().isEmpty());
        assertTrue(result.getClientMappings().isEmpty());
    }

    @Test
    void testLoadFromConfig_WithRealmMappings() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn("auth41-modern");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getRealmMapping("master"));
        assertEquals("auth41-modern", result.getRealmMapping("test"));
        assertEquals(2, result.getRealmMappings().size());
    }

    @Test
    void testLoadFromConfig_WithClientMappings() {
        when(config.getPropertyNames()).thenReturn(Set.of("client-mobile-app", "client-web-portal"));
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");
        when(config.get("client-web-portal")).thenReturn("auth41-classic");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-modern", result.getClientMapping("mobile-app"));
        assertEquals("auth41-classic", result.getClientMapping("web-portal"));
        assertEquals(2, result.getClientMappings().size());
    }

    @Test
    void testLoadFromConfig_WithDefaultThemes() {
        when(config.getPropertyNames()).thenReturn(Set.of("default-login", "default-account", "default-email"));
        when(config.get("default-login")).thenReturn("auth41-classic");
        when(config.get("default-account")).thenReturn("auth41-modern");
        when(config.get("default-email")).thenReturn("auth41-minimal");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getDefaultTheme(Theme.Type.LOGIN));
        assertEquals("auth41-modern", result.getDefaultTheme(Theme.Type.ACCOUNT));
        assertEquals("auth41-minimal", result.getDefaultTheme(Theme.Type.EMAIL));
    }

    @Test
    void testLoadFromConfig_IgnoresEmptyValues() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn("");  // Empty value

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getRealmMapping("master"));
        assertNull(result.getRealmMapping("test"));  // Should be ignored
        assertEquals(1, result.getRealmMappings().size());
    }

    @Test
    void testLoadFromConfig_IgnoresNullValues() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn(null);  // Null value

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getRealmMapping("master"));
        assertNull(result.getRealmMapping("test"));  // Should be ignored
        assertEquals(1, result.getRealmMappings().size());
    }

    @Test
    void testLoadFromConfig_IgnoresWhitespaceOnlyValues() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master", "realm-test"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("realm-test")).thenReturn("   ");  // Whitespace only

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getRealmMapping("master"));
        assertNull(result.getRealmMapping("test"));  // Should be ignored
        assertEquals(1, result.getRealmMappings().size());
    }

    @Test
    void testLoadFromConfig_IgnoresEmptyRealmName() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-", "realm-master"));
        when(config.get("realm-")).thenReturn("auth41-classic");  // Empty realm name
        when(config.get("realm-master")).thenReturn("auth41-modern");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-modern", result.getRealmMapping("master"));
        assertNull(result.getRealmMapping(""));  // Should be ignored
        assertEquals(1, result.getRealmMappings().size());
    }

    @Test
    void testLoadFromConfig_IgnoresEmptyClientId() {
        when(config.getPropertyNames()).thenReturn(Set.of("client-", "client-mobile-app"));
        when(config.get("client-")).thenReturn("auth41-classic");  // Empty client ID
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-modern", result.getClientMapping("mobile-app"));
        assertNull(result.getClientMapping(""));  // Should be ignored
        assertEquals(1, result.getClientMappings().size());
    }

    @Test
    void testLoadFromConfig_IgnoresEmptyThemeType() {
        when(config.getPropertyNames()).thenReturn(Set.of("default-", "default-login"));
        when(config.get("default-")).thenReturn("auth41-classic");  // Empty type
        when(config.get("default-login")).thenReturn("auth41-modern");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-modern", result.getDefaultTheme(Theme.Type.LOGIN));
        assertNull(result.getDefaultTheme(Theme.Type.ACCOUNT));
    }

    @Test
    void testLoadFromConfig_IgnoresInvalidThemeType() {
        when(config.getPropertyNames()).thenReturn(Set.of("default-invalid", "default-login"));
        when(config.get("default-invalid")).thenReturn("auth41-classic");  // Invalid type
        when(config.get("default-login")).thenReturn("auth41-modern");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-modern", result.getDefaultTheme(Theme.Type.LOGIN));
        // Invalid type should be ignored, not throw exception
    }

    @Test
    void testLoadFromConfig_MixedConfiguration() {
        when(config.getPropertyNames()).thenReturn(Set.of(
            "realm-master",
            "client-mobile-app",
            "default-login",
            "unknown-key"
        ));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");
        when(config.get("default-login")).thenReturn("auth41-minimal");
        when(config.get("unknown-key")).thenReturn("some-value");

        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", result.getRealmMapping("master"));
        assertEquals("auth41-modern", result.getClientMapping("mobile-app"));
        assertEquals("auth41-minimal", result.getDefaultTheme(Theme.Type.LOGIN));
        assertEquals(1, result.getRealmMappings().size());
        assertEquals(1, result.getClientMappings().size());
    }

    @Test
    void testMergeFromRealmAttributes_WithNullRealm() {
        ThemeMappingConfig config = new ThemeMappingConfig();

        // Should not throw exception
        assertDoesNotThrow(() -> config.mergeFromRealmAttributes(null));
    }

    @Test
    void testMergeFromRealmAttributes_WithEmptyAttributes() {
        when(realm.getAttributes()).thenReturn(new HashMap<>());
        ThemeMappingConfig config = new ThemeMappingConfig();

        config.mergeFromRealmAttributes(realm);

        assertTrue(config.getClientMappings().isEmpty());
    }

    @Test
    void testMergeFromRealmAttributes_WithClientMapping() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.client.mobile-app", "auth41-modern");
        attributes.put("auth41.theme.client.web-portal", "auth41-classic");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertEquals("auth41-modern", config.getClientMapping("mobile-app"));
        assertEquals("auth41-classic", config.getClientMapping("web-portal"));
    }

    @Test
    void testMergeFromRealmAttributes_WithDefaultTheme() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.default", "auth41-classic");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertEquals("auth41-classic", config.getDefaultTheme(Theme.Type.LOGIN));
        assertEquals("auth41-classic", config.getDefaultTheme(Theme.Type.ACCOUNT));
        assertEquals("auth41-classic", config.getDefaultTheme(Theme.Type.EMAIL));
    }

    @Test
    void testMergeFromRealmAttributes_IgnoresEmptyValues() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.client.mobile-app", "");  // Empty value
        attributes.put("auth41.theme.client.web-portal", "auth41-classic");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertNull(config.getClientMapping("mobile-app"));  // Should be ignored
        assertEquals("auth41-classic", config.getClientMapping("web-portal"));
    }

    @Test
    void testMergeFromRealmAttributes_IgnoresNullValues() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.client.mobile-app", null);  // Null value
        attributes.put("auth41.theme.client.web-portal", "auth41-classic");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertNull(config.getClientMapping("mobile-app"));  // Should be ignored
        assertEquals("auth41-classic", config.getClientMapping("web-portal"));
    }

    @Test
    void testMergeFromRealmAttributes_IgnoresEmptyClientId() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.client.", "auth41-classic");  // Empty client ID
        attributes.put("auth41.theme.client.mobile-app", "auth41-modern");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertNull(config.getClientMapping(""));  // Should be ignored
        assertEquals("auth41-modern", config.getClientMapping("mobile-app"));
    }

    @Test
    void testMergeFromRealmAttributes_IgnoresNonAuth41Attributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("some.other.attribute", "value");
        attributes.put("auth41.theme.client.mobile-app", "auth41-modern");
        when(realm.getAttributes()).thenReturn(attributes);

        ThemeMappingConfig config = new ThemeMappingConfig();
        config.mergeFromRealmAttributes(realm);

        assertEquals(1, config.getClientMappings().size());
        assertEquals("auth41-modern", config.getClientMapping("mobile-app"));
    }

    @Test
    void testMergeFromRealmAttributes_UpdatesExistingMappings() {
        // Load initial config from Config.Scope
        when(config.getPropertyNames()).thenReturn(Set.of("client-mobile-app"));
        when(config.get("client-mobile-app")).thenReturn("auth41-classic");
        ThemeMappingConfig mappingConfig = ThemeMappingConfig.loadFromConfig(config);

        assertEquals("auth41-classic", mappingConfig.getClientMapping("mobile-app"));

        // Merge realm attributes that override the config
        Map<String, String> attributes = new HashMap<>();
        attributes.put("auth41.theme.client.mobile-app", "auth41-modern");
        when(realm.getAttributes()).thenReturn(attributes);

        mappingConfig.mergeFromRealmAttributes(realm);

        // Should be updated to realm attribute value
        assertEquals("auth41-modern", mappingConfig.getClientMapping("mobile-app"));
    }

    @Test
    void testGetRealmMappings_ReturnsUnmodifiableMap() {
        when(config.getPropertyNames()).thenReturn(Set.of("realm-master"));
        when(config.get("realm-master")).thenReturn("auth41-classic");
        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        Map<String, String> realmMappings = result.getRealmMappings();

        assertThrows(UnsupportedOperationException.class, () ->
            realmMappings.put("test", "auth41-modern")
        );
    }

    @Test
    void testGetClientMappings_ReturnsUnmodifiableMap() {
        when(config.getPropertyNames()).thenReturn(Set.of("client-mobile-app"));
        when(config.get("client-mobile-app")).thenReturn("auth41-modern");
        ThemeMappingConfig result = ThemeMappingConfig.loadFromConfig(config);

        Map<String, String> clientMappings = result.getClientMappings();

        assertThrows(UnsupportedOperationException.class, () ->
            clientMappings.put("test", "auth41-classic")
        );
    }
}
