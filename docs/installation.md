# Installation Guide

This guide covers how to install and deploy Auth41 Keycloak plugins.

## Prerequisites

Before installing Auth41, ensure you have:

- **Keycloak 23.x or later** - Auth41 is built for Keycloak 23+ using the new Quarkus distribution
- **Java 17 or later** - Required for building from source and running Keycloak
- **Maven 3.8+** - Required for building from source
- **Database** - PostgreSQL, MySQL, or another Keycloak-supported database (recommended for production)

## Installation Methods

### Method 1: Build from Source (Recommended)

This method builds the latest version from source.

#### Step 1: Clone the Repository

```bash
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins
```

#### Step 2: Build All Plugins

```bash
mvn clean install
```

This will:
- Compile all 6 plugins
- Run unit tests
- Package each plugin as a JAR file
- Install artifacts to your local Maven repository (~/.m2)

To skip tests during build:

```bash
mvn clean install -DskipTests
```

To build a specific plugin only:

```bash
mvn clean install -pl plugins/auth41-trust-network
```

#### Step 3: Locate Built JARs

After successful build, plugin JARs are located at:

```
plugins/auth41-trust-network/target/auth41-trust-network-<version>.jar
plugins/auth41-topology/target/auth41-topology-<version>.jar
plugins/auth41-discovery/target/auth41-discovery-<version>.jar
plugins/auth41-accounts/target/auth41-accounts-<version>.jar
plugins/auth41-federation-broker/target/auth41-federation-broker-<version>.jar
plugins/auth41-themes/target/auth41-themes-<version>.jar
```

### Method 2: Download Release JARs

Download pre-built JARs from the [GitHub Releases](https://github.com/apifocal/auth41-plugins/releases) page (when available).

## Deployment to Keycloak

### Step 1: Copy Plugin JARs

Copy all plugin JARs to Keycloak's providers directory:

```bash
# Set KEYCLOAK_HOME to your Keycloak installation directory
export KEYCLOAK_HOME=/opt/keycloak

# Copy all plugin JARs
cp plugins/*/target/*.jar $KEYCLOAK_HOME/providers/
```

Or copy individual plugins:

```bash
cp plugins/auth41-trust-network/target/auth41-trust-network-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-topology/target/auth41-topology-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-discovery/target/auth41-discovery-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-federation-broker/target/auth41-federation-broker-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-accounts/target/auth41-accounts-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-themes/target/auth41-themes-*.jar $KEYCLOAK_HOME/providers/
```

### Step 2: Rebuild Keycloak

Keycloak needs to rebuild its internal module structure to recognize new providers:

```bash
$KEYCLOAK_HOME/bin/kc.sh build
```

### Step 3: Start Keycloak

For development (with auto-configuration):

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

For production:

```bash
$KEYCLOAK_HOME/bin/kc.sh start \
  --hostname=keycloak.example.com \
  --db=postgres \
  --db-url=jdbc:postgresql://localhost/keycloak \
  --db-username=keycloak \
  --db-password=password
```

### Step 4: Verify Installation

Check Keycloak logs for Auth41 plugin loading messages:

```bash
tail -f $KEYCLOAK_HOME/data/log/keycloak.log
```

Look for log entries like:

```
INFO  [org.keycloak.provider] (build-XX) Loaded SPI trust-network (provider = auth41-trust-network)
INFO  [org.keycloak.provider] (build-XX) Loaded SPI topology (provider = auth41-topology)
INFO  [org.keycloak.provider] (build-XX) Loaded SPI discovery (provider = auth41-discovery)
INFO  [org.keycloak.provider] (build-XX) Loaded SPI accounts (provider = auth41-accounts)
INFO  [org.keycloak.provider] (build-XX) Loaded SPI authenticator (provider = auth41-federation-broker)
INFO  [org.keycloak.provider] (build-XX) Loaded SPI theme-selector (provider = auth41-theme-selector)
```

You can also verify in the Admin Console:

1. Log in to Keycloak Admin Console
2. Navigate to **Authentication** → **Flows**
3. Click **Create flow** and verify "Auth41 Federation Broker" appears in the list of authenticators
4. Navigate to **Realm Settings** → **Themes** and verify Auth41 themes appear in dropdown

## Docker Deployment

### Using Docker Compose

Create a `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data

  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: password
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      AUTH41_TRUST_NETWORK_PATH: /opt/keycloak/data/trust-network.json
    volumes:
      - ./plugins/auth41-trust-network/target/auth41-trust-network.jar:/opt/keycloak/providers/auth41-trust-network.jar
      - ./plugins/auth41-topology/target/auth41-topology.jar:/opt/keycloak/providers/auth41-topology.jar
      - ./plugins/auth41-discovery/target/auth41-discovery.jar:/opt/keycloak/providers/auth41-discovery.jar
      - ./plugins/auth41-accounts/target/auth41-accounts.jar:/opt/keycloak/providers/auth41-accounts.jar
      - ./plugins/auth41-federation-broker/target/auth41-federation-broker.jar:/opt/keycloak/providers/auth41-federation-broker.jar
      - ./plugins/auth41-themes/target/auth41-themes.jar:/opt/keycloak/providers/auth41-themes.jar
      - ./config/trust-network.json:/opt/keycloak/data/trust-network.json
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

Start the stack:

```bash
docker-compose up -d
```

### Custom Dockerfile

Create a custom Keycloak image with Auth41 pre-installed:

```dockerfile
FROM quay.io/keycloak/keycloak:23.0

# Copy Auth41 plugins
COPY plugins/auth41-trust-network/target/auth41-trust-network.jar /opt/keycloak/providers/
COPY plugins/auth41-topology/target/auth41-topology.jar /opt/keycloak/providers/
COPY plugins/auth41-discovery/target/auth41-discovery.jar /opt/keycloak/providers/
COPY plugins/auth41-accounts/target/auth41-accounts.jar /opt/keycloak/providers/
COPY plugins/auth41-federation-broker/target/auth41-federation-broker.jar /opt/keycloak/providers/
COPY plugins/auth41-themes/target/auth41-themes.jar /opt/keycloak/providers/

# Build Keycloak with plugins
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
```

Build and run:

```bash
docker build -t keycloak-auth41 .
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  keycloak-auth41 start-dev
```

## Kubernetes Deployment

### Using Helm

Add Auth41 plugins to the Keycloak Helm chart values:

```yaml
# values.yaml
image:
  repository: quay.io/keycloak/keycloak
  tag: "23.0"

extraInitContainers: |
  - name: plugin-provider
    image: curlimages/curl:latest
    imagePullPolicy: IfNotPresent
    command:
      - sh
    args:
      - -c
      - |
        curl -L -o /providers/auth41-trust-network.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-trust-network.jar
        curl -L -o /providers/auth41-topology.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-topology.jar
        curl -L -o /providers/auth41-discovery.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-discovery.jar
        curl -L -o /providers/auth41-accounts.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-accounts.jar
        curl -L -o /providers/auth41-federation-broker.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-federation-broker.jar
        curl -L -o /providers/auth41-themes.jar https://github.com/apifocal/auth41-plugins/releases/download/v1.0.0/auth41-themes.jar
    volumeMounts:
      - name: providers
        mountPath: /providers

extraVolumeMounts: |
  - name: providers
    mountPath: /opt/keycloak/providers

extraVolumes: |
  - name: providers
    emptyDir: {}
```

Deploy:

```bash
helm install keycloak codecentric/keycloak -f values.yaml
```

## Initial Configuration

After installation, configure Auth41:

### 1. Create Trust Network Configuration

Create `trust-network.json`:

```json
{
  "network_id": "my-federation",
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub": {
      "provider_id": "hub",
      "issuer": "https://hub.example.com/realms/main",
      "role": "hub"
    }
  },
  "trust_relationships": []
}
```

### 2. Configure Keycloak to Load Trust Network

Set environment variable:

```bash
export AUTH41_TRUST_NETWORK_PATH=/path/to/trust-network.json
```

Or set as system property:

```bash
$KEYCLOAK_HOME/bin/kc.sh start \
  -Dauth41.trust.network.path=/path/to/trust-network.json
```

### 3. Enable Federation Broker in Authentication Flow

1. Login to Admin Console
2. Navigate to **Authentication** → **Flows**
3. Create a new flow or duplicate "Browser" flow
4. Add "Auth41 Federation Broker" execution
5. Set it as **REQUIRED** or **ALTERNATIVE**
6. Bind the flow to your realm under **Realm Settings** → **Authentication** → **Browser Flow**

See [Configuration Guide](configuration.md) for detailed setup instructions.

## Upgrading

To upgrade Auth41 plugins:

1. Build or download new version JARs
2. Stop Keycloak
3. Replace old JARs in `providers/` directory
4. Run `kc.sh build`
5. Start Keycloak
6. Check logs for successful loading

## Uninstallation

To remove Auth41 plugins:

1. Stop Keycloak
2. Remove plugin JARs from `providers/` directory:
   ```bash
   rm $KEYCLOAK_HOME/providers/auth41-*.jar
   ```
3. Run `kc.sh build`
4. Start Keycloak

## Troubleshooting

### Plugins Not Loading

**Symptom**: No Auth41 log messages in Keycloak logs

**Solutions**:
- Verify JARs are in `$KEYCLOAK_HOME/providers/` directory
- Run `kc.sh build` after copying JARs
- Check file permissions (JARs must be readable by Keycloak process)
- Review Keycloak logs for class loading errors

### ClassNotFoundException or NoClassDefFoundError

**Symptom**: Keycloak fails to start with class loading errors

**Solutions**:
- Ensure all 6 plugin JARs are deployed (some plugins depend on others)
- Verify JARs are not corrupted (re-download or rebuild)
- Check Keycloak version compatibility (requires 23.x+)

### Trust Network Not Loading

**Symptom**: Federation broker doesn't work, no trust network found

**Solutions**:
- Verify `AUTH41_TRUST_NETWORK_PATH` environment variable is set
- Check file path is accessible to Keycloak process
- Validate JSON syntax of trust network configuration
- Review logs for trust network loading errors

See [Troubleshooting Guide](troubleshooting.md) for more issues and solutions.

## Next Steps

- [Configuration Guide](configuration.md) - Configure trust networks and plugins
- [Architecture Overview](architecture.md) - Understand how Auth41 works
- [Plugin Documentation](plugins/trust-network.md) - Learn about individual plugins
