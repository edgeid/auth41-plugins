package org.apifocal.auth41.plugin.themes.provider;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeSelectorProvider;
import org.apifocal.auth41.plugin.themes.config.ThemeMappingConfig;

/**
 * Theme selector provider that dynamically selects themes based on realm and client.
 * Selection priority: Client > Realm > Default
 */
public class Auth41ThemeSelectorProvider implements ThemeSelectorProvider {
    private static final Logger logger = Logger.getLogger(Auth41ThemeSelectorProvider.class);

    private final KeycloakSession session;
    private final ThemeMappingConfig config;

    public Auth41ThemeSelectorProvider(KeycloakSession session, ThemeMappingConfig config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public String getThemeName(Theme.Type type) {
        ThemeSelectionContext context = buildContext(type);

        logger.debugf("Selecting theme for context: %s", context);

        // Priority 1: Client-based selection
        String theme = matchClient(context);
        if (theme != null) {
            logger.debugf("Selected theme '%s' based on client", theme);
            return theme;
        }

        // Priority 2: Realm-based selection
        theme = matchRealm(context);
        if (theme != null) {
            logger.debugf("Selected theme '%s' based on realm", theme);
            return theme;
        }

        // Priority 3: Default fallback
        theme = getDefaultTheme(type);
        if (theme != null) {
            logger.debugf("Selected default theme '%s' for type %s", theme, type);
            return theme;
        }

        // Ultimate fallback: return null to use Keycloak's default
        logger.debugf("No theme configured, using Keycloak default for type %s", type);
        return null;
    }

    private ThemeSelectionContext buildContext(Theme.Type type) {
        ThemeSelectionContext.Builder builder = ThemeSelectionContext.builder()
                .themeType(type);

        // Get realm
        RealmModel realm = session.getContext().getRealm();
        if (realm != null) {
            builder.realm(realm);

            // Merge realm-specific configuration
            config.mergeFromRealmAttributes(realm);
        }

        // Get client
        ClientModel client = session.getContext().getClient();
        if (client != null) {
            builder.client(client);
        }

        // Note: User is not available from KeycloakContext during theme selection
        // User attribute matching will need to be implemented differently if needed

        return builder.build();
    }

    private String matchClient(ThemeSelectionContext context) {
        ClientModel client = context.getClient();
        if (client == null) {
            return null;
        }

        String clientId = client.getClientId();
        return config.getClientMapping(clientId);
    }

    private String matchRealm(ThemeSelectionContext context) {
        RealmModel realm = context.getRealm();
        if (realm == null) {
            return null;
        }

        String realmName = realm.getName();
        return config.getRealmMapping(realmName);
    }

    private String getDefaultTheme(Theme.Type type) {
        return config.getDefaultTheme(type);
    }

    @Override
    public void close() {
        // No resources to clean up
    }
}
