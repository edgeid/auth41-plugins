# Auth41 Keycloak Plugins Documentation

Welcome to the Auth41 documentation. This collection of Keycloak plugins enables federated authentication across multiple identity providers using a trust network model.

## Documentation Structure

### Getting Started
- [Installation](installation.md) - How to install Auth41 plugins in Keycloak
- [Configuration](configuration.md) - Configuring trust networks and plugins
- [Architecture](architecture.md) - Understanding how Auth41 works

### Plugin Reference
- [Trust Network Plugin](plugins/trust-network.md) - Trust network configuration and management
- [Topology Plugin](plugins/topology.md) - Network topology and trust path computation
- [Discovery Plugin](plugins/discovery.md) - Provider discovery service
- [Accounts Plugin](plugins/accounts.md) - Federated account management
- [Federation Broker Plugin](plugins/federation-broker.md) - Authentication broker for federated flows
- [Themes Plugin](plugins/themes.md) - Dynamic theme selection

### Examples
- [Hub-and-Spoke Federation](examples/hub-spoke.md) - Setting up a hub-and-spoke federation topology

### Additional Resources
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
- [Development Guide](development.md) - Contributing to Auth41

## Quick Links

- [Project README](../README.md) - Project overview and quick start
- [GitHub Repository](https://github.com/apifocal/auth41-plugins)
- [Issue Tracker](https://github.com/apifocal/auth41-plugins/issues)

## What is Auth41?

Auth41 is a collection of Keycloak plugins that enable **federated authentication** across multiple identity providers. Key features:

- **Trust Network Model** - Define trust relationships between identity providers
- **Provider Discovery** - Automatically discover user home providers
- **Topology Support** - Hub-and-spoke, mesh, and custom topologies
- **Federation Broker** - Transparent authentication redirection
- **Shadow Accounts** - Automatic federated user provisioning
- **Dynamic Themes** - Customize UI based on realm, client, or user

## Use Cases

Auth41 is designed for scenarios where:

- Multiple organizations need to share authentication
- Users should authenticate at their home organization
- Trust relationships need to be explicitly managed
- Federation should work transparently to end users
- Support for hub-and-spoke or mesh topologies is needed

## License

Apache License 2.0 - See [LICENSE](../LICENSE) file for details.
