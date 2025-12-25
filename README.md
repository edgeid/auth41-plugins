# Auth41 Keycloak Plugins

A collection of Keycloak plugins for OIDC federation and decentralized identity.

## Documentation

📋 **[Project Plan](.plan/PLAN.md)** - Project overview, status, and quick reference

📐 **[Architecture](.plan/ARCHITECTURE.md)** - High-level design, flows, and decisions

🔨 **[Implementation Roadmap](.plan/IMPLEMENTATION.md)** - Detailed plugin specifications and timeline

🔐 **[CIBA Integration](.plan/CIBA.md)** - Client Initiated Backchannel Authentication plan

📝 **[Session Summary](.plan/SESSION_SUMMARY.md)** - Development history and milestones

## Current Status

**Completed Plugins (3/7 core plugins):**
- ✅ auth41-themes - Dynamic theme selection (52 tests)
- ✅ auth41-trust-network - Trust network management (41 tests)
- ✅ auth41-topology - Topology implementations (25 tests)

**Next to Implement:**
- ⚠️ auth41-accounts - User record storage and provider associations

## Project Structure

```
auth41-plugins/
├── .plan/               # Project documentation
│   ├── PLAN.md         # Project overview and index
│   ├── ARCHITECTURE.md # High-level architecture
│   ├── IMPLEMENTATION.md # Implementation roadmap
│   ├── CIBA.md         # CIBA integration plan
│   └── SESSION_SUMMARY.md # Development history
├── parent/              # Parent POM with dependency management
├── plugins/             # Plugin modules
│   ├── auth41-themes/
│   ├── auth41-trust-network/
│   ├── auth41-topology/
│   └── auth41-accounts/ (next)
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
