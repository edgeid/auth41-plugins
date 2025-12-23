# Plugin Development Guide

This guide explains how to create a new Keycloak plugin in this project.

## Creating a New Plugin

### 1. Create Plugin Directory Structure

```bash
cd plugins
mkdir -p auth41-{name}/src/main/java/org/apifocal/auth41/plugin/{name}
mkdir -p auth41-{name}/src/main/resources/META-INF/services
mkdir -p auth41-{name}/src/test/java/org/apifocal/auth41/plugin/{name}
```

### 2. Create Plugin POM

Create `plugins/auth41-{name}/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.apifocal.auth41</groupId>
        <artifactId>auth41-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../parent/pom.xml</relativePath>
    </parent>

    <groupId>org.apifocal.auth41.plugin</groupId>
    <artifactId>auth41-{name}</artifactId>

    <name>Auth41 {Name} Plugin</name>
    <description>Keycloak {Name} plugin for Auth41</description>

    <dependencies>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi</artifactId>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-server-spi-private</artifactId>
        </dependency>
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-services</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 3. Register Plugin Module

Add your plugin module to `plugins/pom.xml`:

```xml
<modules>
    <module>auth41-{name}</module>
</modules>
```

### 4. Create SPI Provider File

For each Keycloak SPI your plugin implements, create a service provider file in:
`src/main/resources/META-INF/services/`

Example for an Authenticator:
`src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`

Content:
```
org.apifocal.auth41.plugin.{name}.YourAuthenticatorFactory
```

## Common Keycloak SPIs

- `org.keycloak.authentication.AuthenticatorFactory` - Custom authenticators
- `org.keycloak.authentication.FormActionFactory` - Form actions
- `org.keycloak.events.EventListenerProviderFactory` - Event listeners
- `org.keycloak.storage.UserStorageProviderFactory` - User federation
- `org.keycloak.protocol.ProtocolMapperFactory` - Protocol mappers
- `org.keycloak.provider.ProviderFactory` - Generic providers

## Project Conventions

1. **Package naming**: `org.apifocal.auth41.plugin.{name}`
2. **GroupId**: `org.apifocal.auth41.plugin`
3. **ArtifactId**: `auth41-{name}`
4. **Version**: Inherited from parent (1.0.0-SNAPSHOT)
5. **Java version**: 17
6. **Dependencies**: All Keycloak dependencies use `provided` scope

## Building and Testing

Build your plugin:
```bash
cd plugins/auth41-{name}
mvn clean install
```

Build all plugins:
```bash
cd ../..
mvn clean install
```

## Deploying to Keycloak

Copy the built JAR to your Keycloak providers directory:

```bash
cp plugins/auth41-{name}/target/auth41-{name}-1.0.0-SNAPSHOT.jar \
   $KEYCLOAK_HOME/providers/
```

Rebuild Keycloak (for optimized distribution):
```bash
$KEYCLOAK_HOME/bin/kc.sh build
```

Or start in development mode (auto-loads providers):
```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

## Example Plugin Structure

```
plugins/auth41-example/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/apifocal/auth41/plugin/example/
│   │   │       ├── ExampleAuthenticator.java
│   │   │       └── ExampleAuthenticatorFactory.java
│   │   └── resources/
│   │       └── META-INF/
│   │           └── services/
│   │               └── org.keycloak.authentication.AuthenticatorFactory
│   └── test/
│       └── java/
│           └── org/apifocal/auth41/plugin/example/
│               └── ExampleAuthenticatorTest.java
└── README.md
```
