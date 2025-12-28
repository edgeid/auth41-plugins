# Themes Plugin

The Themes plugin provides dynamic theme selection based on realm, client, or user context, with three pre-built themes for Auth41.

## Overview

**Plugin Name**: `auth41-themes`
**SPI**: Keycloak `ThemeSelectorProvider`, `ThemeSelectorProviderFactory`
**Purpose**: Enable dynamic, context-based theme selection

The Themes plugin allows different visual experiences based on who is logging in, which application they're accessing, or which realm they're in.

## Included Themes

### 1. auth41-classic

Classic professional styling:

**Color Scheme**:
- Primary: Navy blue (#1e3a5f)
- Secondary: Light blue (#4a90e2)
- Accent: Gold (#d4af37)

**Use Cases**:
- Enterprise applications
- Financial services
- Government portals
- Professional services

**Supports**:
- Login pages
- Account console
- Email templates

### 2. auth41-modern

Modern gradient design:

**Color Scheme**:
- Primary: Purple gradient (#6366f1 → #8b5cf6)
- Secondary: Indigo (#4f46e5)
- Accent: Pink (#ec4899)

**Features**:
- Rounded corners
- Gradient backgrounds
- Modern card-based layout
- Smooth animations

**Use Cases**:
- Startups
- Tech companies
- Consumer applications
- Creative industries

**Supports**:
- Login pages
- Account console
- Email templates

### 3. auth41-minimal

Clean, minimal design:

**Color Scheme**:
- Primary: Black (#000000)
- Secondary: White (#ffffff)
- Accent: Blue (#0066cc)

**Features**:
- Minimal visual elements
- High contrast
- Fast loading
- Accessibility-focused

**Use Cases**:
- Accessibility requirements
- Low-bandwidth environments
- Simple branding needs
- Internal tools

**Supports**:
- Login pages
- Email templates

## Theme Selection

### Priority Order

Themes are selected based on priority (highest to lowest):

1. **User Attribute** - `theme` attribute on user
2. **Client Mapping** - Realm attribute `auth41.theme.client.{client-id}`
3. **Realm Default** - Realm attribute `auth41.theme.default`
4. **System Default** - Keycloak default theme

### User-Based Selection

Set theme in user attributes:

**Admin Console**:
1. Navigate to **Users** → [User] → **Attributes**
2. Add attribute:
   - Key: `theme`
   - Value: `auth41-modern`

**Programmatically**:
```java
user.setSingleAttribute("theme", "auth41-modern");
```

**Use Cases**:
- VIP users get premium theme
- Beta testers get experimental theme
- Different themes for different user roles

### Client-Based Selection

Set theme per client application:

**Admin Console**:
1. Navigate to **Realm Settings** → **General** → **Realm Attributes**
2. Add attribute:
   - Key: `auth41.theme.client.mobile-app`
   - Value: `auth41-modern`
3. Repeat for each client

**Configuration**:
```json
{
  "auth41.theme.client.web-app": "auth41-classic",
  "auth41.theme.client.mobile-app": "auth41-modern",
  "auth41.theme.client.admin-console": "auth41-minimal"
}
```

**Use Cases**:
- Mobile apps get mobile-optimized theme
- Admin consoles get minimal theme
- Customer portals get classic theme

### Realm-Based Selection

Set default theme for entire realm:

**Admin Console**:
1. Navigate to **Realm Settings** → **Themes**
2. Set themes:
   - **Login Theme**: `auth41-classic`
   - **Account Theme**: `auth41-classic`
   - **Email Theme**: `auth41-classic`

Or via realm attributes:
1. **Realm Settings** → **General** → **Realm Attributes**
2. Add attribute:
   - Key: `auth41.theme.default`
   - Value: `auth41-classic`

## Configuration

### System Properties

```bash
# Enable user attribute-based selection
-Dspi-theme-selector-auth41-theme-selector-user-attr-theme=enabled

# Default theme when no match
-Dspi-theme-selector-auth41-theme-selector-default-theme=keycloak.v2
```

### Realm Attributes

```json
{
  "auth41.theme.default": "auth41-classic",
  "auth41.theme.client.web-app": "auth41-modern",
  "auth41.theme.client.mobile-app": "auth41-modern",
  "auth41.theme.client.admin-app": "auth41-minimal"
}
```

## Theme Customization

### Extending Themes

Create custom theme based on Auth41 themes:

```
themes/my-custom-theme/
├── login/
│   ├── theme.properties
│   │   parent=auth41-classic
│   │   styles=css/custom.css
│   ├── resources/css/custom.css
│   └── messages/messages_en.properties
└── email/
    ├── theme.properties
    └── html/template.ftl
```

### Overriding Styles

**`resources/css/custom.css`**:
```css
/* Override classic colors */
:root {
    --primary-color: #2c5aa0;  /* Custom blue */
    --accent-color: #ff6b35;   /* Custom orange */
}

.login-pf-page {
    background: linear-gradient(135deg, var(--primary-color), var(--accent-color));
}
```

### Custom Templates

Override specific templates:

```
login/
├── login.ftl              (custom login page)
├── register.ftl           (custom registration)
└── login-reset-password.ftl
```

### Custom Messages

**`messages/messages_en.properties`**:
```properties
loginTitle=Welcome to Acme Corp
loginWelcomeMessage=Sign in to your account
doLogIn=Sign In
```

## Theme Structure

### Login Theme

```
theme/auth41-classic/login/
├── theme.properties          # Theme configuration
├── resources/
│   ├── css/
│   │   └── login.css        # Styles
│   └── img/
│       └── logo.png         # Logo image
├── messages/
│   └── messages_en.properties  # Text labels
└── *.ftl                     # FreeMarker templates
    ├── template.ftl          # Base template
    ├── login.ftl             # Login form
    ├── register.ftl          # Registration form
    ├── login-otp.ftl         # 2FA page
    └── ...
```

### Email Theme

```
theme/auth41-classic/email/
├── theme.properties
├── html/
│   ├── template.ftl          # HTML email template
│   ├── email-verification.ftl
│   └── password-reset.ftl
└── text/
    ├── email-verification.ftl  # Plain text version
    └── password-reset.ftl
```

## API Reference

### ThemeSelectorProvider SPI

```java
public interface ThemeSelectorProvider extends Provider {
    /**
     * Get theme name for the given type and context
     *
     * @param type Theme type (LOGIN, ACCOUNT, EMAIL, ADMIN)
     * @return Theme name, or null to use default
     */
    String getThemeName(Theme.Type type);
}
```

### Implementation

```java
public class Auth41ThemeSelectorProvider implements ThemeSelectorProvider {

    private final KeycloakSession session;

    @Override
    public String getThemeName(Theme.Type type) {
        // 1. Check user attribute
        UserModel user = session.getContext().getUser();
        if (user != null) {
            String userTheme = user.getFirstAttribute("theme");
            if (userTheme != null) {
                return userTheme;
            }
        }

        // 2. Check client mapping
        ClientModel client = session.getContext().getClient();
        if (client != null) {
            String clientTheme = realm.getAttribute(
                "auth41.theme.client." + client.getClientId()
            );
            if (clientTheme != null) {
                return clientTheme;
            }
        }

        // 3. Check realm default
        RealmModel realm = session.getContext().getRealm();
        String realmTheme = realm.getAttribute("auth41.theme.default");
        if (realmTheme != null) {
            return realmTheme;
        }

        // 4. System default
        return null;  // Use Keycloak default
    }
}
```

## Testing

### Theme Preview

Test theme without changing settings:

```
https://keycloak.example.com/auth/realms/master/login-actions/authenticate
  ?kc_locale=en
  &kc_theme=auth41-classic
```

### Unit Tests

```bash
mvn test -pl plugins/auth41-themes
```

**Test Coverage**:
- Theme selection priority
- User attribute selection
- Client mapping
- Realm default
- Configuration loading

## Performance

- **Theme Loading**: Once at first request, cached
- **Selection Logic**: < 1ms per request
- **Template Rendering**: 5-20ms per page
- **CSS/Images**: Cached by browser

## Troubleshooting

### Theme Not Applied

**Check**:
1. Theme exists in `providers/` directory
2. Keycloak rebuilt: `kc.sh build`
3. Theme listed in Admin Console dropdown
4. Browser cache cleared

### Wrong Theme Displayed

**Check**:
1. User attribute priority (highest)
2. Client mapping configured correctly
3. Realm default set
4. Browser cache cleared (hard refresh)

### Theme Styling Issues

**Check**:
1. CSS file path correct in `theme.properties`
2. Parent theme specified
3. Browser console for CSS errors
4. Clear Keycloak theme cache (dev mode)

## Best Practices

### Theme Development

1. **Start with parent theme**: Extend existing themes
2. **Override minimally**: Only change what's needed
3. **Test all pages**: Login, registration, error pages, etc.
4. **Mobile responsive**: Test on different screen sizes
5. **Accessibility**: Ensure WCAG compliance

### Performance

1. **Optimize images**: Compress logos and backgrounds
2. **Minimize CSS**: Remove unused styles
3. **Use CDN**: For fonts and icons
4. **Enable caching**: Set appropriate cache headers

### Maintenance

1. **Version control**: Track theme changes in Git
2. **Document changes**: Comment custom CSS
3. **Test upgrades**: Verify themes work with new Keycloak versions
4. **Backup**: Keep copies of working themes

## Next Steps

- [Configuration Guide](../configuration.md) - Configure theme selection
- [Development Guide](../development.md) - Create custom themes
- [Keycloak Theme Guide](https://www.keycloak.org/docs/latest/server_development/#_themes)
