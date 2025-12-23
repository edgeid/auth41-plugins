# Auth41 Keycloak Plugins

A collection of Keycloak plugins for Auth41.

## Project Structure

```
auth41-plugins/
├── parent/              # Parent POM with dependency management
├── plugins/             # Plugin modules
│   └── auth41-example/  # Example plugin structure
└── pom.xml              # Root aggregator POM
```

## Building

Build the entire project:

```bash
mvn clean install
```

Build a specific plugin:

```bash
cd plugins/auth41-example
mvn clean install
```

## Releasing

This project is configured to publish to Maven Central via Sonatype OSSRH.

### Prerequisites

1. Configure your `~/.m2/settings.xml` with Sonatype credentials:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>your-jira-id</username>
      <password>your-jira-password</password>
    </server>
  </servers>
</settings>
```

2. Set up GPG for signing artifacts:

```bash
gpg --gen-key
gpg --list-keys
```

### Release Process

Build and deploy a snapshot:

```bash
mvn clean deploy
```

Build and stage a release:

```bash
mvn clean deploy -P release
```

## Plugin Development

Each plugin should:

1. Be located in `plugins/auth41-{name}/`
2. Have `artifactId` of `auth41-{name}`
3. Have `groupId` of `org.apifocal.auth41.plugin`
4. Inherit from `auth41-parent`
5. Include a `META-INF/services` file for Keycloak SPI discovery

## Requirements

- Java 17 or later
- Maven 3.6 or later
- Keycloak 23.x

## License

Apache License 2.0
