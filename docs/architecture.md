# Architecture Overview

This document describes the architecture of Auth41 and how its components work together to enable federated authentication across multiple identity providers.

## High-Level Architecture

Auth41 extends Keycloak through six integrated plugins that work together to provide federated authentication:

```
┌─────────────────────────────────────────────────────────────────┐
│                      User Authentication Flow                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Federation Broker (Authenticator)             │
│  • Intercepts login requests                                     │
│  • Discovers user home provider                                  │
│  • Redirects to home provider for authentication                 │
│  • Creates/updates shadow account on return                      │
└─────────────────────────────────────────────────────────────────┘
         ↓                    ↓                    ↓
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   Discovery      │  │   Trust Network  │  │    Accounts      │
│   Service        │  │   Configuration  │  │   Management     │
│                  │  │                  │  │                  │
│ • Email domain   │  │ • Providers      │  │ • Shadow users   │
│ • WebFinger      │  │ • Trust rels     │  │ • Attribute sync │
│ • User hints     │  │ • Trust levels   │  │ • Linking        │
└──────────────────┘  └──────────────────┘  └──────────────────┘
                              ↓
                      ┌──────────────────┐
                      │    Topology      │
                      │    Provider      │
                      │                  │
                      │ • Path finding   │
                      │ • Hub/spoke      │
                      │ • Mesh support   │
                      └──────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        Keycloak SPI Layer                        │
│  • Authentication SPI                                            │
│  • User Storage SPI                                              │
│  • Theme SPI                                                     │
└─────────────────────────────────────────────────────────────────┘
```

## Plugin Components

### 1. Federation Broker (Authenticator)

**Purpose**: Orchestrates the federated authentication flow

**Keycloak SPI**: `AuthenticatorFactory`, `Authenticator`

**Key Responsibilities**:
- Intercepts authentication requests during the browser login flow
- Calls Discovery Service to find user's home provider
- Validates trust path to home provider using Topology Provider
- Redirects user to home provider for authentication via OIDC
- Receives authentication response and validates tokens
- Creates or updates shadow account via Accounts Management
- Completes local authentication session

**Integration Points**:
- Added to Keycloak authentication flows (Browser, Direct Grant, etc.)
- Uses Keycloak's built-in OIDC client capabilities
- Integrates with existing identity brokering infrastructure

**Configuration**:
- Configured per realm in authentication flows
- Can be set as REQUIRED, ALTERNATIVE, or CONDITIONAL

### 2. Trust Network Configuration

**Purpose**: Manages trust relationships between identity providers

**Keycloak SPI**: Custom SPI (`TrustNetworkProvider`)

**Key Responsibilities**:
- Loads trust network configuration from JSON file
- Provides API to query providers and trust relationships
- Validates trust levels (EXPLICIT, TRANSITIVE, NONE)
- Serves as single source of truth for federation topology

**Data Model**:
```json
{
  "network_id": "federation-network",
  "topology_type": "hub-and-spoke",
  "providers": {
    "provider-id": {
      "provider_id": "provider-id",
      "issuer": "https://provider.example.com/realms/main",
      "role": "hub|spoke|peer",
      "metadata": {}
    }
  },
  "trust_relationships": [
    {
      "from": "provider-a",
      "to": "provider-b",
      "trust_level": "EXPLICIT",
      "metadata": {}
    }
  ]
}
```

**Configuration**:
- Loaded via `AUTH41_TRUST_NETWORK_PATH` environment variable
- Can be updated and reloaded without Keycloak restart (future enhancement)

### 3. Topology Provider

**Purpose**: Computes trust paths in different network topologies

**Keycloak SPI**: Custom SPI (`TopologyProvider`)

**Key Responsibilities**:
- Determines if a trust path exists between two providers
- Supports multiple topology types:
  - **Hub-and-Spoke**: All communication goes through hub
  - **Mesh**: Direct peer-to-peer trust relationships
  - **Hierarchical**: Multi-level trust hierarchies
- Implements trust path algorithms (BFS, shortest path)
- Considers trust levels when computing paths

**Topology Types**:

**Hub-and-Spoke**:
```
        Hub
       ↙  ↓  ↘
   Spoke Spoke Spoke
```
- All spokes trust hub
- Spokes may trust each other through hub (transitive)

**Mesh**:
```
   A ←→ B
   ↕     ↕
   C ←→ D
```
- Direct peer-to-peer trust relationships
- Flexible but requires more configuration

**Trust Path Example**:
- User at Provider A wants to access service at Provider C
- Direct trust: A → C (EXPLICIT)
- Transitive trust: A → B → C (via hub B)
- No trust: Path not found, access denied

### 4. Discovery Service

**Purpose**: Discovers a user's home identity provider

**Keycloak SPI**: Custom SPI (`DiscoveryProvider`)

**Key Responsibilities**:
- Discovers provider based on user identifier (email, username)
- Supports multiple discovery methods:
  - **Email domain mapping**: `user@example.com` → provider with domain `example.com`
  - **WebFinger**: Query `https://example.com/.well-known/webfinger?resource=acct:user@example.com`
  - **User attribute hints**: Check for `home_provider` user attribute
  - **Realm mappings**: Static realm-to-provider mappings
- Returns provider information (issuer URL, OIDC endpoints)
- Falls back through discovery methods if primary fails

**Discovery Flow**:
```
1. User enters email: user@example.com
2. Extract domain: example.com
3. Check domain mappings in trust network
4. If not found, try WebFinger discovery
5. If not found, check user attribute (if user exists)
6. Return provider info or null
```

**Configuration**:
- Domain mappings in trust network configuration
- WebFinger enabled/disabled via realm settings
- Fallback provider for unknown domains

### 5. Accounts Management

**Purpose**: Manages federated user accounts (shadow accounts)

**Keycloak SPI**: `UserStorageProvider`, Custom SPI (`FederatedAccountProvider`)

**Key Responsibilities**:
- Creates shadow accounts for federated users
- Links federated identity to local user account
- Synchronizes user attributes from home provider
- Manages account lifecycle (create, update, disable)
- Handles account linking for existing users

**Shadow Account Model**:
- Local Keycloak user representing federated identity
- Linked to federated identity provider
- Attributes synchronized from home provider tokens
- Can have local attributes and role mappings
- Federated identity stored in `FederatedIdentityModel`

**Account Creation Flow**:
```
1. User authenticates at home provider
2. Federation Broker receives ID token
3. Accounts plugin checks if shadow account exists
4. If not exists:
   a. Create new user in Keycloak
   b. Set username (email or unique identifier)
   c. Link to federated identity
   d. Sync attributes from token
5. If exists:
   a. Update last login timestamp
   b. Sync changed attributes
6. Return user for session creation
```

**Configuration**:
- Attribute mappings (token claim → user attribute)
- Username template (`${email}`, `${preferred_username}`, etc.)
- Account linking strategy (automatic, prompt, disabled)

### 6. Theme Selector

**Purpose**: Provides dynamic theme selection based on context

**Keycloak SPI**: `ThemeSelectorProvider`

**Key Responsibilities**:
- Selects theme based on realm, client, or user attributes
- Supports multiple theme types (login, account, email, admin)
- Priority-based selection: user > client > realm > default
- Provides custom themes for Auth41 branding

**Selection Logic**:
```
1. Check user attribute: theme="auth41-modern"
2. If not set, check client mapping: client-id → theme
3. If not set, check realm mapping: realm → theme
4. If not set, use default theme
5. Return theme name for requested type (login, account, etc.)
```

**Included Themes**:
- **auth41-corporate**: Professional corporate styling (navy blue)
- **auth41-modern**: Modern gradient design (purple gradients)
- **auth41-minimal**: Minimal clean design (black/white)

## Authentication Flow

### Standard Browser Login with Federation

```
┌──────┐                 ┌──────────────┐                 ┌──────────────┐
│ User │                 │  Keycloak    │                 │ Home Provider│
│      │                 │ (Service)    │                 │              │
└──┬───┘                 └──────┬───────┘                 └──────┬───────┘
   │                            │                                │
   │ 1. Access protected app    │                                │
   ├────────────────────────────>                                │
   │                            │                                │
   │ 2. Redirect to login       │                                │
   <────────────────────────────┤                                │
   │                            │                                │
   │ 3. Enter email             │                                │
   ├────────────────────────────>                                │
   │                            │                                │
   │                 4. Discover home provider                   │
   │                            ├─ Discovery Service             │
   │                            │  (email domain → provider)      │
   │                            │                                │
   │                 5. Validate trust path                      │
   │                            ├─ Topology Provider             │
   │                            │  (check trust relationship)     │
   │                            │                                │
   │ 6. Redirect to home IDP    │                                │
   <────────────────────────────┤                                │
   │                            │                                │
   │ 7. Authenticate at home IDP│                                │
   ├─────────────────────────────────────────────────────────────>
   │                            │                                │
   │ 8. Return with auth code   │                                │
   <─────────────────────────────────────────────────────────────┤
   │                            │                                │
   │ 9. Exchange code for tokens│                                │
   ├────────────────────────────>────────────────────────────────>
   │                            │                                │
   │ 10. ID token + access token│                                │
   <────────────────────────────<────────────────────────────────┤
   │                            │                                │
   │               11. Create/update shadow account              │
   │                            ├─ Accounts Management           │
   │                            │  (federated user provisioning)  │
   │                            │                                │
   │ 12. Create local session   │                                │
   <────────────────────────────┤                                │
   │                            │                                │
   │ 13. Redirect to app        │                                │
   <────────────────────────────┤                                │
```

### Step-by-Step Breakdown

1. **User Access**: User tries to access protected application
2. **Login Redirect**: Application redirects to Keycloak login
3. **Identifier Entry**: User enters email (e.g., `user@org-a.com`)
4. **Provider Discovery**:
   - Federation Broker calls Discovery Service
   - Discovery extracts domain: `org-a.com`
   - Looks up provider in trust network
   - Returns provider info (issuer, endpoints)
5. **Trust Validation**:
   - Federation Broker calls Topology Provider
   - Checks if trust path exists: service provider → home provider
   - Validates trust level (EXPLICIT, TRANSITIVE)
6. **Redirect to Home IDP**:
   - Constructs OIDC authorization request
   - Redirects user to home provider's authorization endpoint
7. **Home Authentication**: User authenticates at their home provider
8. **Authorization Code**: Home provider redirects back with auth code
9. **Token Exchange**: Keycloak exchanges auth code for tokens
10. **Token Response**: Receives ID token and access token
11. **Account Provisioning**:
    - Accounts Management checks for existing shadow account
    - Creates new or updates existing account
    - Syncs attributes from ID token
    - Links federated identity
12. **Session Creation**: Keycloak creates local authentication session
13. **App Access**: User redirected back to application with session

## Plugin Dependencies

```
Federation Broker (orchestrator)
    ├── Discovery Service (find home provider)
    ├── Trust Network (provider info)
    ├── Topology Provider (validate trust path)
    └── Accounts Management (shadow accounts)

Topology Provider
    └── Trust Network (trust relationships)

Discovery Service
    └── Trust Network (provider metadata)

Accounts Management
    └── Trust Network (provider info)
```

## Data Flow

### Trust Network Configuration → Runtime

```
trust-network.json
    ↓ (load at startup)
Trust Network Provider
    ↓ (query providers)
Discovery Service, Topology Provider
    ↓ (use in authentication)
Federation Broker
```

### User Authentication → Shadow Account

```
User Login
    ↓
Federation Broker (authenticate execution)
    ↓
Discovery Service (find provider)
    ↓
Topology Provider (validate trust)
    ↓
OIDC Authentication (home provider)
    ↓
ID Token (user claims)
    ↓
Accounts Management (create/update)
    ↓
Keycloak UserModel (local session)
```

## Keycloak Integration Points

### SPIs Used

| Plugin | Keycloak SPI | Purpose |
|--------|--------------|---------|
| Federation Broker | `AuthenticatorFactory`, `Authenticator` | Authentication flow execution |
| Trust Network | Custom SPI | Trust configuration provider |
| Topology | Custom SPI | Trust path computation |
| Discovery | Custom SPI | Provider discovery |
| Accounts | `UserStorageProvider`, Custom SPI | Federated user management |
| Themes | `ThemeSelectorProvider` | Dynamic theme selection |

### Custom SPIs

Auth41 defines three custom SPIs:

**TrustNetworkProvider**:
```java
public interface TrustNetworkProvider extends Provider {
    TrustNetwork getTrustNetwork();
    Provider getProvider(String providerId);
    List<TrustRelationship> getTrustRelationships(String providerId);
}
```

**TopologyProvider**:
```java
public interface TopologyProvider extends Provider {
    boolean hasTrustPath(String fromProvider, String toProvider);
    TrustPath computeTrustPath(String fromProvider, String toProvider);
}
```

**DiscoveryProvider**:
```java
public interface DiscoveryProvider extends Provider {
    ProviderInfo discoverProvider(String userIdentifier);
}
```

**FederatedAccountProvider**:
```java
public interface FederatedAccountProvider extends Provider {
    UserModel createOrUpdateAccount(RealmModel realm, String providerId, Map<String, Object> claims);
}
```

## Configuration Architecture

### Three-Level Configuration

**1. Static Configuration** (system properties, environment variables):
- Trust network file path
- Plugin-wide settings
- Feature flags

**2. Realm Configuration** (Admin Console):
- Authentication flows
- Identity provider mappings
- Attribute mappings
- Theme selections

**3. Runtime Configuration** (trust network JSON):
- Provider definitions
- Trust relationships
- Topology type
- Metadata

### Configuration Precedence

```
Runtime (trust network) > Realm settings > System properties > Defaults
```

## Security Considerations

### Trust Validation

- All federated authentications validate trust path before redirecting
- Trust levels enforced: EXPLICIT (direct), TRANSITIVE (indirect), NONE (blocked)
- No authentication possible without valid trust relationship

### Token Validation

- ID tokens validated using JWKS from home provider
- Issuer claim must match configured provider issuer
- Audience claim must include service provider client ID
- Signature verification required
- Token expiration enforced

### Shadow Account Security

- Shadow accounts cannot be used for direct authentication
- Federated identity link prevents local password authentication
- Attributes synced only from trusted home provider
- Account linking requires user consent (configurable)

### Network Security

- All provider communication over HTTPS (enforced)
- OIDC state parameter prevents CSRF
- Nonce parameter prevents replay attacks
- PKCE support for mobile/SPA clients

## Scalability

### Performance Characteristics

- Trust network loaded once at startup (cached)
- Topology computations cached per request
- Discovery results can be cached (TTL configurable)
- Shadow accounts stored in Keycloak database (scalable)

### Horizontal Scaling

- All plugins are stateless (no local state)
- Can run on multiple Keycloak instances
- Trust network configuration shared via file or database
- Session affinity not required

## Extension Points

Auth41 can be extended through:

1. **Custom Discovery Methods**: Implement `DiscoveryProvider` SPI
2. **Custom Topology Algorithms**: Extend `TopologyProvider`
3. **Custom Attribute Mappers**: Use Keycloak's mapper framework
4. **Custom Themes**: Add new themes in resources/theme directory
5. **Custom Trust Validation**: Extend `TrustNetworkProvider` with additional logic

## Monitoring and Observability

### Logging

All plugins use SLF4J with structured logging:
- `INFO`: Successful operations (authentication, discovery, account creation)
- `WARN`: Recoverable errors (trust path not found, discovery fallback)
- `ERROR`: Failures (invalid configuration, provider unreachable)
- `DEBUG`: Detailed flow information (development/troubleshooting)

### Metrics (Future)

Planned metrics integration:
- Authentication success/failure rates
- Discovery method hit rates
- Trust path computation time
- Shadow account creation/update counts
- Provider availability

### Audit Events

Keycloak events logged:
- `FEDERATED_IDENTITY_LINK` - Shadow account created
- `FEDERATED_IDENTITY_LINK_UPDATED` - Account synchronized
- `LOGIN` - Federated authentication successful
- `LOGIN_ERROR` - Federated authentication failed

## Next Steps

- [Configuration Guide](configuration.md) - Configure trust networks and plugins
- [Plugin Documentation](plugins/trust-network.md) - Learn about individual plugins
- [Development Guide](development.md) - Extend Auth41
