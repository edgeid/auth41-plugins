# Auth41 Themes Plugin

A Keycloak theme provider plugin with dynamic theme switching capabilities based on realm, client, and user attributes.

## Features

- **Dynamic Theme Selection**: Automatically select themes based on context
- **Priority-Based Matching**: User attributes > Client > Realm > Default
- **Three Demo Themes Included**:
  - **auth41-classic**: Professional enterprise theme with navy blue colors
  - **auth41-modern**: Contemporary theme with purple gradients
  - **auth41-minimal**: Clean and simple theme with minimal styling
- **Multiple Configuration Sources**: System properties and realm attributes
- **Full Theme Coverage**: Login, account console, and email templates

## Installation

### 1. Build the Plugin

```bash
cd plugins/auth41-themes
mvn clean install
```

The JAR will be created at: `target/auth41-themes.jar`

### 2. Deploy to Keycloak

```bash
# Copy JAR to Keycloak providers directory
cp target/auth41-themes.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak (for optimized distribution)
$KEYCLOAK_HOME/bin/kc.sh build

# Or start in development mode (auto-loads providers)
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

### 3. Verify Installation

1. Start Keycloak
2. Log in to Admin Console
3. Go to **Realm Settings → Themes**
4. Check that `auth41-classic`, `auth41-modern`, and `auth41-minimal` appear in theme dropdowns

## Deployment & Testing Guide

### Prerequisites

- Keycloak 23.x or later
- Java 17 or later
- Maven 3.8+

### Step-by-Step Deployment

#### 1. Download and Setup Keycloak

```bash
# Download Keycloak
wget https://github.com/keycloak/keycloak/releases/download/23.0.4/keycloak-23.0.4.zip
unzip keycloak-23.0.4.zip
export KEYCLOAK_HOME=$(pwd)/keycloak-23.0.4
```

#### 2. Build the Plugin

```bash
# From the project root
cd plugins/auth41-themes
mvn clean install

# Or build from project root
cd /path/to/auth41-plugins
mvn clean install
```

The plugin JAR will be created at: `plugins/auth41-themes/target/auth41-themes.jar`

#### 3. Deploy the Plugin

```bash
# Copy the plugin to Keycloak's providers directory
cp plugins/auth41-themes/target/auth41-themes.jar $KEYCLOAK_HOME/providers/

# Build Keycloak (required for production mode)
$KEYCLOAK_HOME/bin/kc.sh build
```

#### 4. Start Keycloak

**Development Mode** (recommended for testing):
```bash
# Auto-loads providers without rebuild
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

**Production Mode**:
```bash
# Must run kc.sh build after any provider changes
$KEYCLOAK_HOME/bin/kc.sh start
```

Access Keycloak at: `http://localhost:8080`

#### 5. Initial Admin Setup

If this is your first time running Keycloak:

1. Navigate to `http://localhost:8080`
2. Click **Administration Console**
3. Create initial admin user (e.g., username: `admin`, password: `admin`)
4. Log in with your credentials

### Testing the Themes

#### Test 1: Verify Themes Are Available

1. Log in to Keycloak Admin Console (`http://localhost:8080`)
2. Select a realm (or use `master` realm)
3. Go to **Realm Settings → Themes** tab
4. Check the dropdowns for:
   - **Login theme**: Should show `auth41-classic`, `auth41-modern`, `auth41-minimal`
   - **Account theme**: Should show `auth41-classic`, `auth41-modern`
   - **Email theme**: Should show `auth41-classic`, `auth41-modern`, `auth41-minimal`

#### Test 2: Apply a Theme to Login Pages

1. In **Realm Settings → Themes**:
   - Set **Login theme** to `auth41-classic`
   - Click **Save**
2. Log out of Admin Console
3. Navigate to login page: `http://localhost:8080/realms/master/account`
4. You should see the classic theme (navy blue styling)

#### Test 3: Test All Three Themes

**Classic Theme**:
```bash
# Set via Admin Console
Realm Settings → Themes → Login theme = auth41-classic
```
- **Expected**: Navy blue (#1e3a5f), professional, traditional design
- **Look for**: Formal styling, subtle shadows, rounded corners

**Modern Theme**:
```bash
# Set via Admin Console
Realm Settings → Themes → Login theme = auth41-modern
```
- **Expected**: Purple gradients (#6366f1 to #8b5cf6), contemporary design
- **Look for**: Gradient backgrounds, smooth animations, card-based layouts

**Minimal Theme**:
```bash
# Set via Admin Console
Realm Settings → Themes → Login theme = auth41-minimal
```
- **Expected**: Black/white with blue accent (#0066cc), clean design
- **Look for**: Simple aesthetics, minimal styling

#### Test 4: Test Dynamic Theme Selection

**Prerequisites**: Configure dynamic theme selection (see Configuration section below)

**Test Realm-Based Selection**:
```bash
# Add to keycloak.conf or as startup arguments
--spi-theme-selector-auth41-theme-selector-realm-master=auth41-classic
--spi-theme-selector-auth41-theme-selector-realm-test=auth41-modern
```

1. Create a new realm called `test`
2. Don't set any theme in Realm Settings
3. Navigate to `http://localhost:8080/realms/test/account`
4. Should automatically show `auth41-modern` theme

**Test Client-Based Selection**:
```bash
# Configuration
--spi-theme-selector-auth41-theme-selector-client-account-console=auth41-minimal
```

1. The `account-console` client should use minimal theme
2. Other clients in the same realm use the realm's default theme

#### Test 5: Test Email Templates

1. In Admin Console, go to **Realm Settings → Themes**
2. Set **Email theme** to `auth41-classic`
3. Configure SMTP settings: **Realm Settings → Email** tab
4. Create a test user: **Users → Add user**
5. In user's **Credentials** tab, set temporary password with "Temporary" toggle ON
6. Check email for password reset - should use classic theme styling

### Viewing Logs

Check Keycloak logs for theme selection messages:

```bash
# View logs
tail -f $KEYCLOAK_HOME/data/log/keycloak.log

# Look for messages like:
# INFO  [org.apifocal.auth41.plugin.themes] Theme selector initialized
# DEBUG [org.apifocal.auth41.plugin.themes] Selected theme 'auth41-classic' for realm 'master'
```

### Enable Debug Logging

To see detailed theme selection logic:

```bash
# Add to conf/keycloak.conf
log-level=org.apifocal.auth41:DEBUG

# Or as startup argument
$KEYCLOAK_HOME/bin/kc.sh start-dev --log-level=org.apifocal.auth41:DEBUG
```

### Hot Reload During Development

When making changes to themes:

**Development Mode** (kc.sh start-dev):
```bash
# 1. Make changes to theme files
# 2. Rebuild plugin
mvn clean install

# 3. Copy to Keycloak
cp target/auth41-themes.jar $KEYCLOAK_HOME/providers/

# 4. Restart Keycloak
# Ctrl+C to stop, then:
$KEYCLOAK_HOME/bin/kc.sh start-dev

# 5. Clear browser cache and test
```

**Production Mode** (kc.sh start):
```bash
# Must run build after provider changes
mvn clean install
cp target/auth41-themes.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

### Common Testing Issues

**Issue**: Themes don't appear in dropdown
- **Solution**: Ensure `META-INF/keycloak-themes.json` is in the JAR
- **Check**: `jar tf auth41-themes.jar | grep keycloak-themes.json`
- **Solution**: Run `kc.sh build` after copying JAR

**Issue**: Theme shows but looks like default Keycloak theme
- **Solution**: Check that CSS files are in the JAR
- **Check**: `jar tf auth41-themes.jar | grep css`
- **Solution**: Clear browser cache (Ctrl+Shift+R or Cmd+Shift+R)

**Issue**: Dynamic theme selection not working
- **Solution**: Verify provider is enabled
- **Check**: Look for `--spi-theme-selector-provider=auth41-theme-selector` in config
- **Solution**: Check logs for theme selector initialization messages

**Issue**: Changes not appearing after rebuild
- **Solution**: Keycloak caches themes aggressively
- **Solution**: Restart Keycloak completely
- **Solution**: Clear browser cache
- **Solution**: Try incognito/private browsing window

## Configuration

### Theme Selection Priority

The plugin selects themes in this priority order:

1. **User Attribute** (highest priority)
2. **Client ID**
3. **Realm Name**
4. **Default Theme** (lowest priority)

### Option 1: System Properties (Static Configuration)

Add to Keycloak startup configuration:

```bash
# Realm-based mapping
--spi-theme-selector-provider=auth41-theme-selector \
--spi-theme-selector-auth41-theme-selector-realm-master=auth41-classic \
--spi-theme-selector-auth41-theme-selector-realm-customer1=auth41-modern \
--spi-theme-selector-auth41-theme-selector-realm-demo=auth41-minimal \

# Client-based mapping
--spi-theme-selector-auth41-theme-selector-client-mobile-app=auth41-modern \
--spi-theme-selector-auth41-theme-selector-client-admin-portal=auth41-classic \

# User attribute configuration
--spi-theme-selector-auth41-theme-selector-user-attr-enabled=true \
--spi-theme-selector-auth41-theme-selector-user-attr-name=theme \

# Default themes by type
--spi-theme-selector-auth41-theme-selector-default-login=auth41-classic \
--spi-theme-selector-auth41-theme-selector-default-account=auth41-classic \
--spi-theme-selector-auth41-theme-selector-default-email=auth41-classic
```

Or in `conf/keycloak.conf`:

```properties
spi-theme-selector-provider=auth41-theme-selector
spi-theme-selector-auth41-theme-selector-realm-master=auth41-classic
spi-theme-selector-auth41-theme-selector-client-mobile-app=auth41-modern
spi-theme-selector-auth41-theme-selector-user-attr-enabled=true
```

### Option 2: Realm Attributes (Dynamic Configuration)

Configure in Keycloak Admin Console:

1. Go to **Realm Settings → General → Realm attributes**
2. Add attributes:
   - `auth41.theme.default` = `auth41-classic`
   - `auth41.theme.client.mobile-app` = `auth41-modern`
   - `auth41.theme.client.web-portal` = `auth41-classic`

### Option 3: User Attributes (Per-User Themes)

1. Enable user attribute selection (see system properties above)
2. Add attribute to user profile:
   - Go to **Users → [Select User] → Attributes**
   - Add attribute: `theme` = `auth41-modern`

## Configuration Examples

### Example 1: Different Theme Per Realm

```properties
# Master realm uses classic theme
spi-theme-selector-auth41-theme-selector-realm-master=auth41-classic

# Customer realms use modern theme
spi-theme-selector-auth41-theme-selector-realm-acme=auth41-modern
spi-theme-selector-auth41-theme-selector-realm-widgets-inc=auth41-modern

# Demo realm uses minimal theme
spi-theme-selector-auth41-theme-selector-realm-demo=auth41-minimal
```

### Example 2: Different Theme Per Application

```properties
# Mobile app uses modern theme
spi-theme-selector-auth41-theme-selector-client-mobile-app=auth41-modern

# Admin portal uses classic theme
spi-theme-selector-auth41-theme-selector-client-admin-console=auth41-classic

# Customer portal uses minimal theme
spi-theme-selector-auth41-theme-selector-client-customer-portal=auth41-minimal
```

### Example 3: User-Selectable Themes

```properties
# Enable user attribute theme selection
spi-theme-selector-auth41-theme-selector-user-attr-enabled=true
spi-theme-selector-auth41-theme-selector-user-attr-name=preferred-theme

# Users can set their preferred-theme attribute to:
# - auth41-classic
# - auth41-modern
# - auth41-minimal
```

## Theme Details

### auth41-classic

**Purpose**: Professional enterprise applications
**Color Scheme**: Navy blue (#1e3a5f), white, light blue accents
**Style**: Traditional, professional, trustworthy
**Best For**: Classic portals, B2B applications, enterprise SSO

**Features**:
- Professional color palette
- Clean, formal layouts
- High contrast for accessibility
- Classic logo placement
- Responsive design

### auth41-modern

**Purpose**: Contemporary tech applications
**Color Scheme**: Purple gradients (#6366f1 to #8b5cf6)
**Style**: Modern, dynamic, engaging
**Best For**: SaaS applications, startups, consumer apps

**Features**:
- Gradient backgrounds
- Smooth animations
- Rounded corners
- Modern typography
- Card-based layouts

### auth41-minimal

**Purpose**: Simple, lightweight applications
**Color Scheme**: Black, white, blue accent (#0066cc)
**Style**: Clean, minimal, functional
**Best For**: Internal tools, rapid deployment, minimalist apps

**Features**:
- Minimal CSS overrides
- Fast loading
- Simple aesthetics
- Easy to customize

## Customizing Themes

### Override an Existing Theme

1. Create a new theme that extends one of the provided themes
2. In your theme's `theme.properties`:

```properties
parent=auth41-classic
# Your overrides here
```

### Create a New Theme

1. Add a new directory under `src/main/resources/theme/your-theme-name/`
2. Create theme types: `login/`, `account/`, `email/`
3. Add `theme.properties` to each type
4. Register in `META-INF/keycloak-themes.json`
5. Rebuild and redeploy

## Troubleshooting

### Theme Not Appearing in Admin Console

1. Check that JAR is in `providers/` directory
2. Run `kc.sh build` to rebuild Keycloak
3. Check Keycloak logs for errors
4. Verify `META-INF/keycloak-themes.json` is in JAR

### Theme Not Switching Dynamically

1. Verify theme selector provider is enabled:
   ```bash
   --spi-theme-selector-provider=auth41-theme-selector
   ```
2. Check Keycloak logs for theme selection messages
3. Verify configuration matches your realm/client/user setup
4. Enable DEBUG logging:
   ```bash
   --log-level=org.apifocal.auth41:DEBUG
   ```

### Check Current Theme Selection

Monitor Keycloak logs during login to see theme selection:

```
INFO  [org.apifocal.auth41.plugin.themes.provider.Auth41ThemeSelectorProviderFactory] Auth41 Theme Selector initialized
DEBUG [org.apifocal.auth41.plugin.themes.provider.Auth41ThemeSelectorProvider] Selected theme 'auth41-classic' based on realm
```

## Development

### Build

```bash
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Package for Distribution

```bash
mvn clean package
```

## Technical Details

### SPI Provider

- **Provider ID**: `auth41-theme-selector`
- **Factory**: `Auth41ThemeSelectorProviderFactory`
- **Provider**: `Auth41ThemeSelectorProvider`
- **Interface**: `org.keycloak.theme.ThemeSelectorProvider`

### Configuration Keys

| Key Pattern | Description | Example |
|-------------|-------------|---------|
| `realm-{name}` | Maps realm to theme | `realm-master=auth41-classic` |
| `client-{id}` | Maps client to theme | `client-mobile-app=auth41-modern` |
| `user-attr-enabled` | Enable user attribute selection | `user-attr-enabled=true` |
| `user-attr-name` | User attribute name | `user-attr-name=theme` |
| `default-{type}` | Default theme for type | `default-login=auth41-classic` |

## License

Apache License 2.0

## Support

For issues and questions, please open an issue in the project repository.
