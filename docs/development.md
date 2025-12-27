# Development Guide

This guide covers how to develop, extend, and contribute to Auth41 plugins.

## Development Setup

### Prerequisites

- **JDK 17 or later** - Required for Keycloak 23.x development
- **Maven 3.8+** - Build tool
- **Git** - Version control
- **IDE** - IntelliJ IDEA (recommended), Eclipse, or VS Code with Java extensions
- **Keycloak 23.x** - For testing

### Clone Repository

```bash
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins
```

### Import into IDE

#### IntelliJ IDEA

1. File → Open
2. Select `auth41-plugins` directory
3. Choose "Open as Project"
4. Wait for Maven import to complete
5. IDE will auto-detect Maven structure

#### Eclipse

1. File → Import → Maven → Existing Maven Projects
2. Select `auth41-plugins` directory
3. Click Finish

#### VS Code

1. Open folder: `auth41-plugins`
2. Install "Extension Pack for Java"
3. Maven integration will auto-activate

### Build Project

```bash
# Build all plugins
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build specific plugin
mvn clean install -pl plugins/auth41-trust-network

# Build with specific Keycloak version
mvn clean install -Dkeycloak.version=23.0.5
```

### Run Tests

```bash
# Run all tests
mvn test

# Run tests for specific plugin
mvn test -pl plugins/auth41-federation-broker

# Run specific test class
mvn test -Dtest=TrustNetworkProviderTest

# Run with coverage
mvn clean verify
```

## Project Structure

```
auth41-plugins/
├── parent/                          # Parent POM
│   └── pom.xml                     # Dependency management
├── plugins/                         # Plugin modules
│   ├── auth41-trust-network/       # Trust network configuration
│   │   ├── src/main/java/          # Java sources
│   │   ├── src/main/resources/     # Resources, SPI registrations
│   │   └── src/test/java/          # Unit tests
│   ├── auth41-topology/            # Topology provider
│   ├── auth41-discovery/           # Discovery service
│   ├── auth41-accounts/            # Account management
│   ├── auth41-federation-broker/   # Federation authenticator
│   └── auth41-themes/              # Theme provider
├── tests/                          # Integration tests
│   └── src/test/java/              # Test classes
├── docs/                           # Documentation
└── pom.xml                         # Root POM
```

### Plugin Structure

Each plugin follows standard Maven structure:

```
auth41-{plugin-name}/
├── pom.xml                         # Plugin POM
├── README.md                       # Plugin documentation
└── src/
    ├── main/
    │   ├── java/org/apifocal/auth41/plugin/{name}/
    │   │   ├── provider/           # SPI implementations
    │   │   ├── model/              # Data models
    │   │   ├── config/             # Configuration
    │   │   └── util/               # Utilities
    │   └── resources/
    │       ├── META-INF/
    │       │   └── services/       # SPI registration files
    │       └── ...                 # Other resources
    └── test/
        └── java/org/apifocal/auth41/plugin/{name}/
            └── ...                 # Test classes
```

## Creating a New Plugin

### 1. Create Maven Module

Create `plugins/auth41-myplugin/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.apifocal.auth41</groupId>
        <artifactId>auth41-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../parent/pom.xml</relativePath>
    </parent>

    <artifactId>auth41-myplugin</artifactId>
    <packaging>jar</packaging>
    <name>Auth41 :: My Plugin</name>

    <dependencies>
        <!-- Keycloak dependencies inherited from parent -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-core</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi-private</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

Add to `plugins/pom.xml`:

```xml
<modules>
    <!-- existing modules -->
    <module>auth41-myplugin</module>
</modules>
```

### 2. Define SPI

Create interface for your SPI:

```java
package org.apifocal.auth41.plugin.myplugin.spi;

import org.keycloak.provider.Provider;

public interface MyPluginProvider extends Provider {
    String doSomething(String input);
}
```

Create provider factory interface:

```java
package org.apifocal.auth41.plugin.myplugin.spi;

import org.keycloak.provider.ProviderFactory;

public interface MyPluginProviderFactory extends ProviderFactory<MyPluginProvider> {
}
```

### 3. Implement Provider

```java
package org.apifocal.auth41.plugin.myplugin.provider;

import org.apifocal.auth41.plugin.myplugin.spi.MyPluginProvider;
import org.keycloak.models.KeycloakSession;

public class MyPluginProviderImpl implements MyPluginProvider {

    private final KeycloakSession session;

    public MyPluginProviderImpl(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public String doSomething(String input) {
        // Implementation
        return "processed: " + input;
    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
```

### 4. Implement Provider Factory

```java
package org.apifocal.auth41.plugin.myplugin.provider;

import org.apifocal.auth41.plugin.myplugin.spi.MyPluginProvider;
import org.apifocal.auth41.plugin.myplugin.spi.MyPluginProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class MyPluginProviderFactoryImpl implements MyPluginProviderFactory {

    private static final String PROVIDER_ID = "auth41-myplugin";

    @Override
    public MyPluginProvider create(KeycloakSession session) {
        return new MyPluginProviderImpl(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Load configuration
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization
    }

    @Override
    public void close() {
        // Cleanup
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
```

### 5. Register SPI

Create `src/main/resources/META-INF/services/org.apifocal.auth41.plugin.myplugin.spi.MyPluginProviderFactory`:

```
org.apifocal.auth41.plugin.myplugin.provider.MyPluginProviderFactoryImpl
```

### 6. Add Tests

```java
package org.apifocal.auth41.plugin.myplugin.provider;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyPluginProviderTest {

    @Test
    void testDoSomething() {
        // Mock KeycloakSession
        KeycloakSession session = mock(KeycloakSession.class);

        MyPluginProvider provider = new MyPluginProviderImpl(session);
        String result = provider.doSomething("test");

        assertEquals("processed: test", result);
    }
}
```

## Keycloak SPI Development

### Common SPIs

| SPI | Interface | Use Case |
|-----|-----------|----------|
| Authenticator | `Authenticator`, `AuthenticatorFactory` | Custom authentication logic |
| User Storage | `UserStorageProvider`, `UserStorageProviderFactory` | External user stores |
| Event Listener | `EventListenerProvider`, `EventListenerProviderFactory` | Audit and monitoring |
| Theme Selector | `ThemeSelectorProvider`, `ThemeSelectorProviderFactory` | Dynamic theme selection |
| Protocol Mapper | `ProtocolMapper` | Token claim mapping |

### Accessing Keycloak Context

```java
public class MyAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Access session
        KeycloakSession session = context.getSession();

        // Access realm
        RealmModel realm = context.getRealm();

        // Access user
        UserModel user = context.getUser();

        // Access HTTP request
        HttpRequest request = context.getHttpRequest();

        // Access authentication session
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
    }
}
```

### Configuration from Keycloak Config

```java
@Override
public void init(Config.Scope config) {
    String setting = config.get("mySetting", "defaultValue");
    int timeout = config.getInt("timeout", 30);
    boolean enabled = config.getBoolean("enabled", true);
}
```

Set configuration via system properties:

```bash
-Dspi-myplugin-auth41-myplugin-my-setting=value
-Dspi-myplugin-auth41-myplugin-timeout=60
```

## Testing

### Unit Testing

Use JUnit 5 and Mockito:

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

class MyProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    private MyProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        provider = new MyProviderImpl(session);
    }

    @Test
    void testFeature() {
        when(session.getContext()).thenReturn(mock(KeycloakContext.class));

        // Test logic

        verify(session).getContext();
    }
}
```

### Integration Testing

Deploy to test Keycloak instance:

```bash
# Build plugin
mvn clean install -pl plugins/auth41-myplugin

# Deploy to Keycloak
cp plugins/auth41-myplugin/target/auth41-myplugin-*.jar $KEYCLOAK_HOME/providers/

# Rebuild and restart
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

### Test with Docker

Use Testcontainers for integration tests:

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IntegrationTest {

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
        .withExposedPorts(8080)
        .withEnv("KEYCLOAK_ADMIN", "admin")
        .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
        .withCommand("start-dev");

    @Test
    void testIntegration() {
        String url = "http://localhost:" + keycloak.getMappedPort(8080);
        // Test against Keycloak
    }
}
```

## Code Style

### Java Coding Standards

- **Formatting**: Follow standard Java conventions
- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters max
- **Braces**: K&R style (opening brace on same line)
- **Naming**:
  - Classes: `PascalCase`
  - Methods: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: lowercase, no underscores

### Documentation

- **JavaDoc**: Required for all public classes and methods
- **Comments**: Explain "why", not "what"
- **TODOs**: Include issue reference: `// TODO: ISSUE-123 - Fix this`

Example:

```java
/**
 * Discovers a user's home identity provider based on their identifier.
 *
 * <p>This provider supports multiple discovery methods including email domain
 * mapping, WebFinger, and user attribute hints. Discovery methods are tried
 * in order until a provider is found.
 *
 * @param userIdentifier the user identifier (email or username)
 * @return provider information, or null if not found
 * @throws DiscoveryException if discovery process fails
 */
public ProviderInfo discoverProvider(String userIdentifier) throws DiscoveryException {
    // Implementation
}
```

### Logging

Use SLF4J:

```java
import org.jboss.logging.Logger;

public class MyProvider {
    private static final Logger log = Logger.getLogger(MyProvider.class);

    public void doSomething() {
        log.debug("Starting operation");

        try {
            // Logic
            log.info("Operation completed successfully");
        } catch (Exception e) {
            log.error("Operation failed", e);
        }
    }
}
```

**Log Levels**:
- `ERROR`: Failures requiring attention
- `WARN`: Unexpected but recoverable situations
- `INFO`: Important business events
- `DEBUG`: Detailed flow information
- `TRACE`: Very detailed debugging

## Debugging

### Local Development

Run Keycloak in debug mode:

```bash
export JAVA_OPTS_APPEND="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8787"
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

Connect debugger from IDE:
- IntelliJ: Run → Attach to Process → Select Keycloak
- Eclipse: Debug → Debug Configurations → Remote Java Application
- VS Code: Use "Java: Attach to Remote Program"

### Remote Debugging

For Docker containers:

```yaml
# docker-compose.yml
services:
  keycloak:
    ports:
      - "8080:8080"
      - "8787:8787"
    environment:
      JAVA_OPTS_APPEND: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8787"
```

### Hot Reload (Development Mode)

Keycloak's dev mode supports theme hot reload:

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

Changes to themes are picked up without restart. For Java code changes, rebuild and redeploy JAR.

## Contributing

### Contribution Workflow

1. **Fork** repository on GitHub
2. **Clone** your fork:
   ```bash
   git clone https://github.com/YOUR-USERNAME/auth41-plugins.git
   cd auth41-plugins
   ```
3. **Create branch**:
   ```bash
   git checkout -b feature/my-feature
   ```
4. **Make changes** and commit:
   ```bash
   git add .
   git commit -m "Add my feature"
   ```
5. **Push** to your fork:
   ```bash
   git push origin feature/my-feature
   ```
6. **Create Pull Request** on GitHub

### Commit Messages

Follow conventional commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Build process or auxiliary tool changes

**Examples**:

```
feat(discovery): add WebFinger discovery support

Implements WebFinger-based provider discovery as specified in RFC 7033.
Adds configuration option to enable/disable per provider.

Closes #42
```

```
fix(broker): handle missing email claim in ID token

Previously threw NullPointerException when email claim was missing.
Now falls back to 'sub' claim for username.

Fixes #56
```

### Pull Request Guidelines

- **Title**: Descriptive, following commit message format
- **Description**:
  - What changes were made
  - Why changes were needed
  - How to test
  - Related issues
- **Tests**: Include unit tests for new features
- **Documentation**: Update docs if needed
- **Code Style**: Follow project conventions
- **Small PRs**: Keep changes focused and reviewable

## Release Process

### Versioning

Auth41 follows [Semantic Versioning](https://semver.org/):

- **Major**: Breaking changes
- **Minor**: New features, backward compatible
- **Patch**: Bug fixes, backward compatible

### Creating a Release

1. **Update version** in POMs:
   ```bash
   mvn versions:set -DnewVersion=1.1.0
   mvn versions:commit
   ```

2. **Update CHANGELOG.md**:
   ```markdown
   ## [1.1.0] - 2025-01-15
   ### Added
   - WebFinger discovery support
   - Mesh topology provider

   ### Fixed
   - Token validation with clock skew
   ```

3. **Commit and tag**:
   ```bash
   git add .
   git commit -m "Release version 1.1.0"
   git tag -a v1.1.0 -m "Version 1.1.0"
   git push origin main --tags
   ```

4. **Build release**:
   ```bash
   mvn clean install -DskipTests
   ```

5. **Create GitHub release**:
   - Go to Releases → Draft a new release
   - Select tag: v1.1.0
   - Add release notes from CHANGELOG
   - Upload JARs from `plugins/*/target/`

## Resources

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak SPI Guide](https://www.keycloak.org/docs/latest/server_development/)
- [Auth41 Architecture](architecture.md)
- [Auth41 GitHub Repository](https://github.com/apifocal/auth41-plugins)

## Next Steps

- [Architecture Documentation](architecture.md) - Understand how Auth41 works
- [Plugin Documentation](plugins/trust-network.md) - Learn about each plugin's internals
- [Configuration Guide](configuration.md) - Configure Auth41 for development
