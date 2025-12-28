# Auth41 Shared Libraries

This directory contains shared libraries used by all Auth41 Keycloak plugins.

## Modules

### auth41-commons

Common utilities with no Keycloak dependencies. Can be used in any Java project.

**Packages**:
- `validation` - Input validation utilities
- `exception` - Common exception hierarchy
- `capabilities` - Provider capability constants (including CIBA)
- `dto` - DTO utilities (planned)
- `config` - Configuration utilities (planned)
- `json` - JSON processing utilities (planned)

**Key Classes**:
- `ValidationUtils` - String/collection validation, email validation
- `Auth41Exception` - Base exception for Auth41 operations
- `CibaCapability` - CIBA-related constants and utilities

**Dependencies**: jackson-databind, jboss-logging

### auth41-spi-commons

Common base classes for Keycloak SPI implementations.

**Packages**:
- `spi` - Abstract base classes for providers

**Key Classes**:
- `AbstractProviderFactory<T>` - Base class for all Auth41 provider factories
  - Standardized lifecycle logging
  - Configuration helper methods
  - Error handling

**Dependencies**: keycloak-server-spi, keycloak-server-spi-private, jboss-logging

## Usage

### Using auth41-commons

Add to your plugin's `pom.xml`:

```xml
<dependency>
    <groupId>org.apifocal.auth41</groupId>
    <artifactId>auth41-commons</artifactId>
</dependency>
```

Example:
```java
import org.apifocal.auth41.common.validation.ValidationUtils;
import org.apifocal.auth41.common.capabilities.CibaCapability;

// Validate email
if (!ValidationUtils.isValidEmail(email)) {
    throw new IllegalArgumentException("Invalid email");
}

// Check CIBA support
if (CibaCapability.isTruthy(provider.getAttribute("ciba_supported"))) {
    // Provider supports CIBA
}
```

### Using auth41-spi-commons

Add to your plugin's `pom.xml`:

```xml
<dependency>
    <groupId>org.apifocal.auth41</groupId>
    <artifactId>auth41-spi-commons</artifactId>
</dependency>
```

Example:
```java
import org.apifocal.auth41.spi.AbstractProviderFactory;

public class MyProviderFactory extends AbstractProviderFactory<MyProvider> {

    @Override
    protected void doInit(Config.Scope config) {
        // Load configuration
        String endpoint = getConfig(config, "endpoint", "https://default.com");
        int timeout = getConfigInt(config, "timeout", 30);
        boolean enabled = getConfigBoolean(config, "enabled", true);
    }

    @Override
    public MyProvider create(KeycloakSession session) {
        return new MyProvider(session);
    }

    @Override
    public String getId() {
        return "my-provider";
    }
}
```

## Build

```bash
# Build shared libraries
mvn clean install -pl lib -am

# Build everything including plugins
mvn clean install
```

## CIBA Preparation

The `CibaCapability` class provides constants and utilities for CIBA (Client-Initiated Backchannel Authentication) support:

- Metadata keys for CIBA configuration
- Token delivery mode constants (poll, ping, push)
- Validation utilities for CIBA parameters

This prepares the foundation for CIBA implementation across Auth41 plugins.
