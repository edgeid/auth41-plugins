package org.apifocal.auth41.plugin.themes.config;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.RealmModel;
import org.keycloak.theme.Theme;

import java.util.*;

/**
 * Configuration for theme mappings from multiple sources.
 * Supports loading from:
 * - System properties (static configuration)
 * - Keycloak Config.Scope (startup configuration)
 * - Realm attributes (dynamic configuration)
 */
public class ThemeMappingConfig {
    private static final Logger logger = Logger.getLogger(ThemeMappingConfig.class);

    private static final String REALM_PREFIX = "realm-";
    private static final String CLIENT_PREFIX = "client-";
    private static final String USER_ATTR_PREFIX = "user-attr-";
    private static final String DEFAULT_PREFIX = "default-";

    private final Map<String, String> realmMappings = new HashMap<>();
    private final Map<String, String> clientMappings = new HashMap<>();
    private final Map<String, String> userAttributeMappings = new HashMap<>();
    private final Map<Theme.Type, String> defaultThemes = new EnumMap<>(Theme.Type.class);
    private boolean userAttributeEnabled = false;
    private String userAttributeName = "theme";

    /**
     * Load configuration from Keycloak Config.Scope
     */
    public static ThemeMappingConfig loadFromConfig(Config.Scope config) {
        ThemeMappingConfig mappingConfig = new ThemeMappingConfig();

        if (config == null) {
            logger.info("No configuration provided, using defaults");
            return mappingConfig;
        }

        // Load all configuration properties
        for (String key : config.getPropertyNames()) {
            String value = config.get(key);

            if (key.startsWith(REALM_PREFIX)) {
                String realmName = key.substring(REALM_PREFIX.length());
                mappingConfig.realmMappings.put(realmName, value);
                logger.infof("Loaded realm mapping: %s -> %s", realmName, value);
            } else if (key.startsWith(CLIENT_PREFIX)) {
                String clientId = key.substring(CLIENT_PREFIX.length());
                mappingConfig.clientMappings.put(clientId, value);
                logger.infof("Loaded client mapping: %s -> %s", clientId, value);
            } else if (key.startsWith(USER_ATTR_PREFIX)) {
                String attrName = key.substring(USER_ATTR_PREFIX.length());
                if ("enabled".equals(attrName)) {
                    mappingConfig.userAttributeEnabled = Boolean.parseBoolean(value);
                    logger.infof("User attribute theme selection enabled: %s", value);
                } else if ("name".equals(attrName)) {
                    mappingConfig.userAttributeName = value;
                    logger.infof("User attribute name set to: %s", value);
                } else {
                    mappingConfig.userAttributeMappings.put(attrName, value);
                    logger.infof("Loaded user attribute mapping: %s -> %s", attrName, value);
                }
            } else if (key.startsWith(DEFAULT_PREFIX)) {
                String typeStr = key.substring(DEFAULT_PREFIX.length()).toUpperCase();
                try {
                    Theme.Type type = Theme.Type.valueOf(typeStr);
                    mappingConfig.defaultThemes.put(type, value);
                    logger.infof("Loaded default theme for %s: %s", type, value);
                } catch (IllegalArgumentException e) {
                    logger.warnf("Unknown theme type: %s", typeStr);
                }
            }
        }

        logger.infof("Loaded theme configuration: %d realm mappings, %d client mappings, %d user attr mappings",
                mappingConfig.realmMappings.size(),
                mappingConfig.clientMappings.size(),
                mappingConfig.userAttributeMappings.size());

        return mappingConfig;
    }

    /**
     * Load additional configuration from realm attributes
     */
    public void mergeFromRealmAttributes(RealmModel realm) {
        if (realm == null) {
            return;
        }

        Map<String, String> attributes = realm.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        // Look for auth41.theme.* attributes
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (!key.startsWith("auth41.theme.")) {
                continue;
            }

            String subKey = key.substring("auth41.theme.".length());

            if (subKey.startsWith("client.")) {
                String clientId = subKey.substring("client.".length());
                clientMappings.put(clientId, value);
                logger.debugf("Loaded client mapping from realm attribute: %s -> %s", clientId, value);
            } else if (subKey.equals("default")) {
                // Apply as default for all types
                for (Theme.Type type : Theme.Type.values()) {
                    defaultThemes.putIfAbsent(type, value);
                }
                logger.debugf("Loaded default theme from realm attribute: %s", value);
            }
        }
    }

    public String getRealmMapping(String realmName) {
        return realmMappings.get(realmName);
    }

    public String getClientMapping(String clientId) {
        return clientMappings.get(clientId);
    }

    public String getUserAttributeMapping(String attributeValue) {
        return userAttributeMappings.get(attributeValue);
    }

    public String getDefaultTheme(Theme.Type type) {
        return defaultThemes.get(type);
    }

    public boolean isUserAttributeEnabled() {
        return userAttributeEnabled;
    }

    public String getUserAttributeName() {
        return userAttributeName;
    }

    public Map<String, String> getRealmMappings() {
        return Collections.unmodifiableMap(realmMappings);
    }

    public Map<String, String> getClientMappings() {
        return Collections.unmodifiableMap(clientMappings);
    }
}
