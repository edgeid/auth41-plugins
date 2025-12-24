package org.apifocal.auth41.plugin.themes.config;

/**
 * Represents a theme mapping rule with match criteria and target theme.
 */
public class ThemeMapping {
    private final String key;
    private final String value;
    private final String themeName;
    private final MappingType type;

    public enum MappingType {
        REALM,
        CLIENT,
        USER_ATTRIBUTE
    }

    public ThemeMapping(String key, String value, String themeName, MappingType type) {
        this.key = key;
        this.value = value;
        this.themeName = themeName;
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getThemeName() {
        return themeName;
    }

    public MappingType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ThemeMapping{" +
                "type=" + type +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", themeName='" + themeName + '\'' +
                '}';
    }
}
