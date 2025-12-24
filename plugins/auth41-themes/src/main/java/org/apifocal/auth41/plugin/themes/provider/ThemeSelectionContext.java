package org.apifocal.auth41.plugin.themes.provider;

import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;

/**
 * Context object containing all information needed for theme selection.
 */
public class ThemeSelectionContext {
    private final RealmModel realm;
    private final ClientModel client;
    private final UserModel user;
    private final Theme.Type themeType;

    private ThemeSelectionContext(Builder builder) {
        this.realm = builder.realm;
        this.client = builder.client;
        this.user = builder.user;
        this.themeType = builder.themeType;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public ClientModel getClient() {
        return client;
    }

    public UserModel getUser() {
        return user;
    }

    public Theme.Type getThemeType() {
        return themeType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RealmModel realm;
        private ClientModel client;
        private UserModel user;
        private Theme.Type themeType;

        public Builder realm(RealmModel realm) {
            this.realm = realm;
            return this;
        }

        public Builder client(ClientModel client) {
            this.client = client;
            return this;
        }

        public Builder user(UserModel user) {
            this.user = user;
            return this;
        }

        public Builder themeType(Theme.Type themeType) {
            this.themeType = themeType;
            return this;
        }

        public ThemeSelectionContext build() {
            return new ThemeSelectionContext(this);
        }
    }

    @Override
    public String toString() {
        return "ThemeSelectionContext{" +
                "realm=" + (realm != null ? realm.getName() : "null") +
                ", client=" + (client != null ? client.getClientId() : "null") +
                ", user=" + (user != null ? user.getUsername() : "null") +
                ", themeType=" + themeType +
                '}';
    }
}
