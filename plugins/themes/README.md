# Auth41 Themes Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.apifocal.auth41.plugin/auth41-themes.svg)](https://central.sonatype.com/artifact/org.apifocal.auth41.plugin/auth41-themes)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Keycloak plugin providing dynamic theme selection based on realm, client, and user context, with three pre-built Auth41 themes.

## Overview

The Auth41 Themes plugin extends Keycloak's theme system with dynamic, context-aware theme selection. Instead of setting a static theme per realm, you can configure themes based on:

- **User attributes** - Different themes for VIP users, beta testers, or user roles
- **Client applications** - Mobile apps get mobile-optimized themes, admin consoles get minimal themes
- **Realm configuration** - Different realms use different branding

## Features

- ✅ **Dynamic Theme Selection** - Context-based theme switching using Keycloak's `ThemeSelectorProvider` SPI
- ✅ **Priority-Based Matching** - User → Client → Realm → Default fallback
- ✅ **Three Pre-Built Themes** - Classic, Modern, and Minimal themes included
- ✅ **Flexible Configuration** - Configure via realm attributes or user attributes
- ✅ **Multiple Theme Types** - Support for login, account, and email themes

## Included Themes

### auth41-classic

**Professional, enterprise-ready styling**

- **Color Scheme**: Navy blue (#1e3a5f), Light blue (#4a90e2), Gold accent (#d4af37)
- **Use Cases**: Enterprise applications, financial services, government portals, professional services
- **Theme Types**: Login, Account, Email

### auth41-modern

**Contemporary gradient design**

- **Color Scheme**: Purple gradient (#6366f1 → #8b5cf6), Indigo (#4f46e5), Pink accent (#ec4899)
- **Features**: Rounded corners, gradient backgrounds, modern card layout, smooth animations
- **Use Cases**: Startups, tech companies, consumer applications, creative industries
- **Theme Types**: Login, Account, Email

### auth41-minimal

**Clean, accessibility-focused design**

- **Color Scheme**: Black (#000000), White (#ffffff), Blue accent (#0066cc)
- **Features**: Minimal visual elements, high contrast, fast loading, accessibility-optimized
- **Use Cases**: Accessibility requirements, low-bandwidth environments, simple branding, internal tools
- **Theme Types**: Login, Email

## Installation

**From Maven Central**:

```bash
mvn dependency:get -Dartifact=org.apifocal.auth41.plugin:auth41-themes:1.0.0-alpha.2

cp ~/.m2/repository/org/apifocal/auth41/plugin/auth41-themes/1.0.0-alpha.2/*.jar \
   $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

**From Source**:

```bash
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins
mvn clean install -pl plugins/auth41-themes -am

cp plugins/auth41-themes/target/auth41-themes-*.jar $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

## Configuration

### Theme Selection Priority

Themes are selected based on the following priority order (highest to lowest):

1. **User Attribute** - `theme` attribute on the user
2. **Client Mapping** - Realm attribute `auth41.theme.client.{client-id}`
3. **Realm Default** - Realm attribute `auth41.theme.default`
4. **Keycloak Default** - Standard realm theme settings

### User-Based Theme Selection

Assign themes to individual users via user attributes.

**Via Admin Console**:

1. Navigate to **Users** → Select user → **Attributes** tab
2. Add new attribute:
   - **Key**: `theme`
   - **Value**: `auth41-modern` (or `auth41-classic`, `auth41-minimal`)
3. Click **Add**, then **Save**

**Programmatically** (Java):

```java
UserModel user = session.users().getUserById(realm, userId);
user.setSingleAttribute("theme", "auth41-modern");
```

**Use Cases**:
- Premium/VIP users get the modern theme
- Beta testers get experimental themes
- Different themes for different user roles
- Personalized branding per user

### Client-Based Theme Selection

Configure different themes for different client applications.

**Via Admin Console**:

1. Navigate to **Realm Settings** → **General** tab → **Realm Attributes** section
2. Add attributes for each client:
   - **Key**: `auth41.theme.client.mobile-app`
   - **Value**: `auth41-modern`
3. Repeat for other clients

**Example Configuration**:

```
auth41.theme.client.web-portal     = auth41-classic
auth41.theme.client.mobile-app     = auth41-modern
auth41.theme.client.admin-console  = auth41-minimal
auth41.theme.client.partner-api    = auth41-classic
```

**Use Cases**:
- Mobile apps get mobile-optimized themes
- Admin consoles get minimal, fast-loading themes
- Customer portals get professional classic themes
- Partner applications get custom branding

### Realm Default Theme

Set a default theme for the entire realm.

**Via Admin Console**:

1. Navigate to **Realm Settings** → **General** tab → **Realm Attributes**
2. Add attribute:
   - **Key**: `auth41.theme.default`
   - **Value**: `auth41-classic`

**Use Cases**:
- Different realms represent different brands
- Multi-tenant deployments with per-realm branding
- Testing different themes per realm

### Static Theme Configuration (Keycloak Standard)

You can also use Keycloak's built-in theme settings:

1. Navigate to **Realm Settings** → **Themes** tab
2. Set:
   - **Login Theme**: `auth41-classic`
   - **Account Theme**: `auth41-classic`
   - **Email Theme**: `auth41-classic`

**Note**: Dynamic theme selection (user/client-based) takes precedence over static configuration.

## Usage Examples

### Example 1: VIP User Theme

Give VIP users a premium theme while others use the default:

```java
// Mark user as VIP
UserModel vipUser = session.users().getUserByUsername(realm, "vip.user@company.com");
vipUser.setSingleAttribute("theme", "auth41-modern");

// Regular users don't have theme attribute, get realm default (auth41-classic)
```

### Example 2: Multi-Client Deployment

Different clients get different themes:

**Realm Attributes** (via Admin Console → Realm Settings → Attributes):

```
auth41.theme.client.ios-app        = auth41-modern
auth41.theme.client.android-app    = auth41-modern
auth41.theme.client.web-app        = auth41-classic
auth41.theme.client.admin-tool     = auth41-minimal
auth41.theme.default               = auth41-classic
```

Result:
- iOS/Android apps: Modern gradient theme
- Web app: Classic professional theme
- Admin tool: Minimal fast-loading theme
- Any other client: Classic theme (default)

### Example 3: Multi-Realm Multi-Brand

Different realms for different brands/organizations:

**Realm "acme-corp"**:
- Default theme: `auth41-classic`
- Client "acme-mobile": `auth41-modern`

**Realm "startup-inc"**:
- Default theme: `auth41-modern`
- All clients inherit modern theme

**Realm "internal"**:
- Default theme: `auth41-minimal`
- Fast, simple theme for internal tools

## Theme Customization

### Extending Included Themes

Create a custom theme that extends one of the Auth41 themes:

**1. Create theme directory**:

```bash
mkdir -p $KEYCLOAK_HOME/themes/my-company-theme/login
```

**2. Create `theme.properties`**:

```properties
# Extend auth41-classic
parent=auth41-classic

# Override specific styles
styles=css/custom.css
```

**3. Add custom CSS**:

```bash
mkdir -p $KEYCLOAK_HOME/themes/my-company-theme/login/resources/css
```

```css
/* css/custom.css */
/* Override primary color */
:root {
  --auth41-primary: #your-brand-color;
}
```

**4. Configure realm to use custom theme**:

Set `auth41.theme.default` to `my-company-theme`

### Creating New Themes from Scratch

See [Keycloak Theme Documentation](https://www.keycloak.org/docs/latest/server_development/#_themes) for creating custom themes.

## Architecture

```
┌─────────────────────────────────────────────────┐
│           Keycloak Request                      │
│    (realm, client, user context)                │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│    Auth41ThemeSelectorProvider (SPI)            │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │  1. Check user.theme attribute            │ │
│  │  2. Check realm.auth41.theme.client.X     │ │
│  │  3. Check realm.auth41.theme.default      │ │
│  │  4. Return default theme                  │ │
│  └───────────────────────────────────────────┘ │
└────────────────┬────────────────────────────────┘
                 │
                 ▼
         Selected Theme Name
         (auth41-classic, auth41-modern, etc.)
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│         Keycloak Theme Rendering                │
│  (FreeMarker templates, CSS, resources)         │
└─────────────────────────────────────────────────┘
```

## API Reference

### ThemeSelectorProvider SPI

The plugin implements Keycloak's `ThemeSelectorProvider` SPI:

```java
public interface ThemeSelectorProvider extends Provider {
    String getThemeName(Theme.Type type);
}
```

**Provider ID**: `auth41-theme-selector`

### Configuration Attributes

| Attribute | Scope | Format | Example |
|-----------|-------|--------|---------|
| `theme` | User | Theme name | `auth41-modern` |
| `auth41.theme.client.{client-id}` | Realm | Theme name | `auth41-classic` |
| `auth41.theme.default` | Realm | Theme name | `auth41-minimal` |

## Monitoring and Debugging

### Enable Debug Logging

Add to `$KEYCLOAK_HOME/conf/keycloak.conf`:

```properties
log-level=org.apifocal.auth41.plugin.themes:DEBUG
```

### Log Messages

```
DEBUG: Theme selection for user 'john@example.com', client 'web-app', realm 'master'
DEBUG: Selected theme: auth41-modern (source: user-attribute)
DEBUG: Selected theme: auth41-classic (source: client-mapping)
DEBUG: Selected theme: auth41-minimal (source: realm-default)
```

### Verify Theme Loading

Check Keycloak startup logs:

```
INFO: Themes: auth41-classic, auth41-modern, auth41-minimal
INFO: ThemeSelectorProvider registered: auth41-theme-selector
```

## Troubleshooting

### Theme not changing

**Cause**: Browser cache or incorrect configuration.

**Solution**:
1. Clear browser cache (Ctrl+Shift+Delete)
2. Use incognito/private browsing window
3. Verify attribute key exactly matches (case-sensitive)
4. Check Keycloak logs for theme selection messages

### "Theme not found" error

**Cause**: Theme JAR not deployed or Keycloak not rebuilt.

**Solution**:
1. Ensure JAR is in `$KEYCLOAK_HOME/providers/`
2. Run `kc.sh build` to rebuild Keycloak
3. Restart Keycloak
4. Check available themes in Admin Console → Realm Settings → Themes

### User attribute not taking effect

**Cause**: Attribute key typo or user not logged out.

**Solution**:
1. Verify attribute key is exactly `theme` (lowercase)
2. Log out and log back in
3. Check user has the attribute set: Admin Console → Users → [User] → Attributes

### Client-based selection not working

**Cause**: Incorrect realm attribute key format.

**Solution**:
1. Verify format: `auth41.theme.client.{exact-client-id}`
2. Client ID is case-sensitive
3. Use the client ID from Admin Console → Clients → [Client] → Settings → Client ID

## Security Considerations

### Theme Injection

User-provided theme names are validated against available themes. Arbitrary theme names cannot be injected.

### XSS Protection

All themes inherit Keycloak's built-in XSS protection and Content Security Policy.

## Performance

### Theme Caching

Themes are cached by Keycloak. Changes require:
- Cache clear: `kc.sh build --cache=local`
- Or restart Keycloak in dev mode: `kc.sh start-dev`

### Production Optimization

For production deployments:
1. Use static theme configuration when possible (faster than dynamic)
2. Minimize user attribute queries by caching theme at session level
3. Use Keycloak's theme caching features

## Related Documentation

- [Themes Plugin Documentation](../../docs/plugins/themes.md) - Full guide
- [Keycloak Theme Documentation](https://www.keycloak.org/docs/latest/server_development/#_themes)
- [Architecture Overview](../../docs/architecture.md)

## Support

- **Issues**: [GitHub Issues](https://github.com/apifocal/auth41-plugins/issues)
- **Documentation**: [Full Documentation](../../docs/README.md)
- **License**: Apache License 2.0

## Version History

### 1.0.0-alpha.2 (2025-12-31)
- Published to Maven Central
- Production-ready theme selector SPI
- Three complete themes (classic, modern, minimal)

### 1.0.0-alpha.1 (2024-12-23)
- Initial release
- Dynamic theme selection based on user/client/realm
- Priority-based theme matching
- Three Auth41 themes included
