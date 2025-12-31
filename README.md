# Auth41 Keycloak Plugins

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Keycloak](https://img.shields.io/badge/Keycloak-26.x-blue.svg)](https://www.keycloak.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.apifocal.auth41/auth41-root.svg)](https://central.sonatype.com/namespace/org.apifocal.auth41)

A collection of Keycloak plugins that enable federated authentication across multiple identity providers using a trust network model.

## Overview

Auth41 extends Keycloak with capabilities for multi-organization federation, allowing users to authenticate at their home identity provider while accessing services at other trusted providers. The system supports various network topologies (hub-and-spoke, mesh) and handles automatic provider discovery, trust path validation, and federated user provisioning.

### Key Features

- **🌐 Trust Network Management** - Define and manage explicit trust relationships between identity providers
- **🔍 Automatic Provider Discovery** - Discover user home providers based on email domain or identifier
- **🗺️ Topology Support** - Hub-and-spoke, mesh, and custom network topologies with trust path computation
- **🔐 Federation Broker** - Transparent authentication redirection to home providers
- **👤 Shadow Account Management** - Automatic federated user provisioning and synchronization
- **🎨 Dynamic Theming** - Realm, client, and user-based theme selection
- **📱 CIBA Support** - Client-Initiated Backchannel Authentication for decoupled authentication flows

## Architecture

Auth41 consists of eight integrated plugins:

```
┌──────────────────────────────────────────────────────────────────┐
│                       Auth41 Federation                          │
├──────────────────────────────────────────────────────────────────┤
│  Trust Network  │  Topology  │  Discovery  │  Accounts          │
│  Configuration  │  Provider  │  Service    │  Management        │
├──────────────────────────────────────────────────────────────────┤
│  Federation     │    Theme   │    CIBA     │  Backchannel       │
│  Broker         │  Selector  │  Provider   │  Implementations   │
└──────────────────────────────────────────────────────────────────┘
                            │
                    ┌───────┴───────┐
                    │   Keycloak    │
                    │   SPI Layer   │
                    └───────────────┘
```

See [Architecture Documentation](docs/architecture.md) for details.

## Quick Start

### Prerequisites

- Keycloak 26.x or later
- Java 21+
- Maven 3.8+ (for building from source)

### Installation

#### Option 1: From Maven Central (Recommended)


```bash
# Download all plugins
 * org.apifocal.auth41:auth41-commons:<latest>
 * org.apifocal.auth41:auth41-spi-commons:<latest>
 * org.apifocal.auth41:auth41-ciba-spi:<latest>
 * org.apifocal.auth41.plugin:auth41-trust-network:<latest>
 * org.apifocal.auth41.plugin:auth41-topology:<latest>
 * org.apifocal.auth41.plugin:auth41-discovery:<latest>
 * org.apifocal.auth41.plugin:auth41-accounts:<latest>
 * org.apifocal.auth41.plugin:auth41-federation-broker:<latest>
 * org.apifocal.auth41.plugin:auth41-themes:<latest>
 * org.apifocal.auth41.plugin:auth41-ciba:<latest>
 * org.apifocal.auth41.plugin:auth41-backchannel-mock:<latest>

# Copy to Keycloak providers directory
cp ~/.m2/repository/org/apifocal/auth41/*/*/*.jar $KEYCLOAK_HOME/providers/

# Rebuild and start Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

#### Option 2: Build from Source

```bash
# Clone repository
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins

# Build all plugins
mvn clean install

# Deploy to Keycloak
cp plugins/*/target/*.jar $KEYCLOAK_HOME/providers/
cp lib/*/target/*.jar $KEYCLOAK_HOME/providers/

# Rebuild and start Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

**Verify installation**: Check Keycloak logs for Auth41 plugin loading messages.

See [Installation Guide](docs/installation.md) for detailed instructions.

### Basic Configuration

Create a trust network configuration file (`trust-network.json`):

```json
{
  "network_id": "my-federation",
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub": {
      "provider_id": "hub",
      "issuer": "https://hub.example.com/realms/main",
      "role": "hub"
    },
    "org-a": {
      "provider_id": "org-a",
      "issuer": "https://org-a.example.com/realms/main",
      "role": "spoke"
    }
  },
  "trust_relationships": [
    {"from": "hub", "to": "org-a", "trust_level": "EXPLICIT"},
    {"from": "org-a", "to": "hub", "trust_level": "EXPLICIT"}
  ]
}
```

Configure Keycloak to load the trust network:

```bash
export AUTH41_TRUST_NETWORK_PATH=/path/to/trust-network.json
$KEYCLOAK_HOME/bin/kc.sh start
```

See [Configuration Guide](docs/configuration.md) for complete setup.

## Plugins

### Core Federation Plugins

| Plugin | Description | Documentation |
|--------|-------------|---------------|
| **Trust Network** | Manages trust relationships between providers | [📖 Docs](docs/plugins/trust-network.md) |
| **Topology** | Computes trust paths in various network topologies | [📖 Docs](docs/plugins/topology.md) |
| **Discovery** | Discovers user home providers | [📖 Docs](docs/plugins/discovery.md) |
| **Accounts** | Manages federated user accounts | [📖 Docs](docs/plugins/accounts.md) |
| **Federation Broker** | Authenticator for federated login flows | [📖 Docs](docs/plugins/federation-broker.md) |

### Authentication Enhancement Plugins

| Plugin | Description | Documentation |
|--------|-------------|---------------|
| **CIBA** | Client-Initiated Backchannel Authentication for decoupled flows | [📖 Docs](docs/plugins/ciba.md) |
| **Backchannel (File)** | File-based backchannel for manual/integration testing | [📖 Docs](plugins/auth41-backchannel-file/README.md) |
| **Backchannel (Mock)** | Mock backchannel for automated testing | [📖 Docs](plugins/auth41-backchannel-mock/README.md) |

### UI Enhancement Plugins

| Plugin | Description | Documentation |
|--------|-------------|---------------|
| **Themes** | Dynamic theme selection based on context | [📖 Docs](docs/plugins/themes.md) |

## Use Cases

### Multi-Organization Federation

Organizations A, B, and C want to allow their users to access each other's services while authenticating at their home organization:

```
Organization A (Hub)
    ↕ Trust
Organization B (Spoke) ←→ Organization C (Spoke)
```

Users from Organization B accessing services at Organization A are automatically redirected to Organization B for authentication, then returned to Organization A with a federated identity.

### Educational Federation

Universities in a consortium share access to research resources:

```
        Consortium Hub
           ↙  ↓  ↘
    Univ-A Univ-B Univ-C
```

Students authenticate at their home university but can access resources at any consortium member.

See [Examples](docs/examples/) for detailed scenarios.

## Documentation

- [📘 Full Documentation](docs/README.md)
- [🚀 Installation Guide](docs/installation.md)
- [⚙️ Configuration Guide](docs/configuration.md)
- [🏗️ Architecture Overview](docs/architecture.md)
- [🔧 Troubleshooting](docs/troubleshooting.md)
- [💻 Development Guide](docs/development.md)

## Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins

# Build all plugins
mvn clean install

# Build specific plugin
mvn clean install -pl plugins/auth41-trust-network

# Skip tests
mvn clean install -DskipTests
```

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for specific plugin
mvn test -pl plugins/auth41-federation-broker

# Run with coverage
mvn clean verify
```

### Project Structure

```
auth41-plugins/
├── parent/                  # Parent POM with dependency management
├── lib/                     # Shared libraries
│   ├── auth41-commons/      # Common utilities
│   ├── auth41-spi-commons/  # Keycloak SPI base classes
│   └── auth41-ciba-spi/     # CIBA abstraction interfaces
├── plugins/                 # Plugin modules
│   ├── auth41-trust-network/
│   ├── auth41-topology/
│   ├── auth41-discovery/
│   ├── auth41-accounts/
│   ├── auth41-federation-broker/
│   ├── auth41-themes/
│   ├── auth41-ciba/         # CIBA authentication flow
│   ├── auth41-backchannel-file/  # File-based backchannel for testing
│   └── auth41-backchannel-mock/  # Mock backchannel for automated tests
├── test/                    # Integration tests (manual for now)
└── docs/                    # Documentation
```

## Contributing

Contributions are welcome! Please see [Development Guide](docs/development.md) for:

- Code style guidelines
- Testing requirements
- Pull request process
- Issue reporting

## Roadmap

- [x] CIBA (Client-Initiated Backchannel Authentication) support
- [x] File-based backchannel for CIBA testing
- [x] Mock backchannel for automated testing
- [x] Comprehensive unit tests for CIBA components
- [x] Token endpoint implementation for CIBA
- [x] OAuth2 token generation (access, refresh, ID tokens)
- [ ] Integration tests with file-based backchannel
- [ ] Enhanced client authentication (JWT, mTLS)
- [ ] CIBA ping and push delivery modes
- [ ] Push notification backchannel for production
- [ ] Admin UI extensions for trust network management
- [ ] Metrics and monitoring integration
- [ ] Performance optimizations
- [ ] Additional topology types (full mesh, hierarchical)

## License

Apache License 2.0 - See [LICENSE](LICENSE) file for details.

## Support

- 📧 Email: [hadrian@apache.org](mailto:hadrian@apache.org)
- 🐛 Issues: [GitHub Issues](https://github.com/apifocal/auth41-plugins/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/apifocal/auth41-plugins/discussions)

## Acknowledgments

Built on [Keycloak](https://www.keycloak.org/), the open-source identity and access management solution.

---

**Made with ❤️ for federated authentication**
