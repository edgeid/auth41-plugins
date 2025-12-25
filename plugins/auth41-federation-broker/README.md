# Auth41 Federation Broker Plugin

Federated authentication broker for Auth41 OIDC federation. Handles routing authentication requests through the trust network, validating tokens from federated providers, and re-issuing tokens to relying parties.

## Overview

The Federation Broker is the core component that ties together all Auth41 plugins to enable federated authentication. It acts as an intermediary between relying parties (RPs) and users' home identity providers, managing the OAuth 2.0 / OIDC authentication flow across the federation.

### Key Responsibilities

- **Authentication Request Routing**: Constructs and initiates authentication requests to users' home providers
- **Code-to-Token Exchange**: Exchanges authorization codes for access tokens and ID tokens
- **Token Validation**: Validates JWT tokens from federated providers using JWKS
- **Token Re-issuance**: Issues new tokens to RPs with federation metadata
- **CIBA Support**: Client Initiated Backchannel Authentication for server-to-server flows
- **Trust Path Verification**: Ensures valid trust relationships exist between providers

## Architecture

```
┌─────────────┐
│ Relying     │
│ Party (RP)  │
└──────┬──────┘
       │ 1. Auth request (login_hint)
       ▼
┌─────────────────────┐
│ Federation Broker   │ ← THIS PLUGIN
│ (Hub/Trust Anchor)  │
└──────┬──────────────┘
       │ 2. Discover home provider
       │ 3. Validate trust path
       │ 4. Initiate auth request
       ▼
┌──────────────┐
│ Home         │
│ Provider     │
└──────┬───────┘
       │ 5. User authenticates
       │ 6. Return token
       ▼
┌─────────────────────┐
│ Federation Broker   │
│ - Validate token    │
│ - Re-issue with     │
│   federation claims │
└──────┬──────────────┘
       │ 7. Return federated token
       ▼
┌─────────────┐
│ Relying     │
│ Party (RP)  │
└─────────────┘
```

## Components

### Keycloak Authenticator

**FederatedAuthenticator** - Keycloak authentication flow integration

The `FederatedAuthenticator` integrates the federation broker into Keycloak's browser authentication flow. It:

1. **Extracts user identifier** from `login_hint` parameter or displays account chooser
2. **Discovers home provider** using ProviderDiscoveryService
3. **Validates trust path** via TopologyProvider
4. **Redirects to home provider** for authentication
5. **Handles callback** with authorization code
6. **Exchanges code for tokens** using FederationBrokerProvider
7. **Validates tokens** from home provider
8. **Creates shadow user** in local realm with federation metadata
9. **Establishes session** for the authenticated user

The authenticator is registered as `auth41-federated` and can be added to any Keycloak authentication flow.

### Domain Models

**FederationRequest** - Represents a federated authentication request
```java
FederationRequest request = FederationRequest.builder()
    .userIdentifier("user@example.com")
    .homeProviderId("provider-a")
    .currentProviderId("hub-a")
    .clientId("test-client")
    .scope("openid profile email")
    .redirectUri("https://example.com/callback")
    .state("random-state")
    .nonce("random-nonce")
    .build();
```

**TokenSet** - Contains access token, ID token, and metadata
```java
TokenSet tokens = TokenSet.builder()
    .accessToken("eyJhbGc...")
    .idToken("eyJhbGc...")
    .refreshToken("eyJhbGc...")
    .tokenType("Bearer")
    .expiresIn(3600)
    .build();
```

**TokenValidationResult** - Result of token validation
```java
TokenValidationResult result = provider.validateToken(token, homeProviderId, network);
if (result.isValid()) {
    String subject = result.getSubject();
    String issuer = result.getIssuer();
    Map<String, Object> claims = result.getClaims();
}
```

### Service Provider Interface

**FederationBrokerProvider** - Main SPI for federation operations

```java
public interface FederationBrokerProvider extends Provider {
    // Initiate authentication request to home provider
    String initiateAuthenticationRequest(FederationRequest request, TrustNetwork network);

    // Exchange authorization code for tokens
    TokenSet exchangeCodeForToken(String code, String homeProviderId, TrustNetwork network);

    // Validate token from federated provider
    TokenValidationResult validateToken(String token, String homeProviderId, TrustNetwork network);

    // Re-issue token with federation metadata
    TokenSet reissueToken(TokenSet homeTokens, FederationRequest request, TrustNetwork network);

    // CIBA operations
    String initiateCibaRequest(FederationRequest request, TrustNetwork network);
    TokenSet pollCibaToken(String authReqId, String homeProviderId, TrustNetwork network);
}
```

## Usage

### Basic Federation Flow

```java
// 1. Get services
FederationBrokerProvider broker = session.getProvider(FederationBrokerProvider.class);
ProviderDiscoveryService discovery = session.getProvider(ProviderDiscoveryService.class);
TrustNetworkProvider trustProvider = session.getProvider(TrustNetworkProvider.class);

// 2. Load trust network
TrustNetwork network = trustProvider.loadNetwork("production-network");

// 3. Discover user's home provider
Set<String> providers = discovery.findProvidersByUser("user@example.com");
String homeProviderId = providers.iterator().next();

// 4. Create federation request
FederationRequest request = FederationRequest.builder()
    .userIdentifier("user@example.com")
    .homeProviderId(homeProviderId)
    .currentProviderId("hub-a")
    .clientId("rp-client-id")
    .scope("openid profile")
    .redirectUri("https://rp.example.com/callback")
    .state("random-state")
    .nonce("random-nonce")
    .build();

// 5. Initiate authentication request
String authUrl = broker.initiateAuthenticationRequest(request, network);

// 6. Redirect user to authUrl
// ... user authenticates at home provider ...

// 7. Handle callback with authorization code
TokenSet homeTokens = broker.exchangeCodeForToken(code, homeProviderId, network);

// 8. Validate tokens
TokenValidationResult validation = broker.validateToken(
    homeTokens.getIdToken(),
    homeProviderId,
    network
);

if (!validation.isValid()) {
    throw new AuthenticationException(validation.getErrorMessage());
}

// 9. Re-issue tokens with federation metadata
TokenSet federatedTokens = broker.reissueToken(homeTokens, request, network);

// 10. Return tokens to RP
return federatedTokens;
```

### CIBA Flow

For server-to-server authentication without browser redirects:

```java
// 1. Initiate CIBA request at home provider
String authReqId = broker.initiateCibaRequest(request, network);

// 2. Poll for authentication completion
TokenSet tokens = null;
int attempts = 0;
while (tokens == null && attempts < 24) { // Poll for 2 minutes
    Thread.sleep(5000); // Wait 5 seconds between polls
    tokens = broker.pollCibaToken(authReqId, homeProviderId, network);
    attempts++;
}

if (tokens == null) {
    throw new AuthenticationException("CIBA authentication timeout");
}

// 3. Validate and re-issue tokens
TokenValidationResult validation = broker.validateToken(tokens.getIdToken(), homeProviderId, network);
if (validation.isValid()) {
    TokenSet federatedTokens = broker.reissueToken(tokens, request, network);
    return federatedTokens;
}
```

## Federation Metadata Claims

Tokens re-issued by the federation broker include additional claims:

```json
{
  "iss": "https://hub-a.example.com",
  "sub": "user-123",
  "aud": "rp-client-id",
  "exp": 1234567890,

  "federated_from": "provider-a",
  "home_subject": "original-user-id-at-provider-a",
  "trust_path": "hub-a→provider-a",
  "hop_count": 1,

  "email": "user@example.com",
  "name": "John Doe"
}
```

These federation claims allow RPs to:
- Track which provider authenticated the user
- Understand the trust chain
- Implement provider-specific policies
- Audit authentication sources

## Configuration

### Broker Client ID

By default, the federation broker uses `"federation-broker"` as its client ID when communicating with federated providers. This can be customized via Keycloak configuration:

```bash
kc.sh start --spi-federation-broker-default-broker-client-id=custom-broker-id
```

This client ID is used when:
- Exchanging authorization codes for tokens
- Polling for CIBA token completion

**Note**: The broker client ID must be registered at all federated providers in the trust network.

### Trust Network Setup

The federation broker requires a properly configured trust network with provider metadata:

```json
{
  "network_id": "production",
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub-a": {
      "provider_id": "hub-a",
      "issuer": "https://hub-a.example.com",
      "role": "hub",
      "metadata": {
        "authorization_endpoint": "https://hub-a.example.com/auth",
        "token_endpoint": "https://hub-a.example.com/token",
        "jwks_uri": "https://hub-a.example.com/jwks",
        "backchannel_authentication_endpoint": "https://hub-a.example.com/ciba/auth"
      },
      "attributes": {
        "ciba_supported": "true"
      }
    },
    "provider-a": {
      "provider_id": "provider-a",
      "issuer": "https://provider-a.example.com",
      "role": "spoke",
      "metadata": {
        "authorization_endpoint": "https://provider-a.example.com/auth",
        "token_endpoint": "https://provider-a.example.com/token",
        "jwks_uri": "https://provider-a.example.com/jwks",
        "backchannel_authentication_endpoint": "https://provider-a.example.com/ciba/auth"
      },
      "attributes": {
        "ciba_supported": "true"
      }
    }
  },
  "trust_relationships": [
    {"from": "hub-a", "to": "provider-a"},
    {"from": "provider-a", "to": "hub-a"}
  ]
}
```

### CIBA Requirements

For CIBA support, providers must:
1. Have `ciba_supported: "true"` in attributes
2. Configure `backchannel_authentication_endpoint` in metadata
3. Be reachable within 2 hops (direct connection or single intermediary)

## Dependencies

This plugin requires:
- **auth41-trust-network**: Provider metadata and trust relationships
- **auth41-topology**: Trust path computation
- **auth41-discovery**: User-to-provider association lookup

## Error Handling

The broker throws `FederationException` for various error conditions:

```java
try {
    String authUrl = broker.initiateAuthenticationRequest(request, network);
} catch (FederationException e) {
    // Handle errors:
    // - Provider not found in network
    // - No authorization endpoint configured
    // - No trust path exists between providers
    // - CIBA not supported by provider
    log.error("Federation error: " + e.getMessage(), e);
}
```

## Security Considerations

### Token Validation

The broker validates tokens by:
1. Decoding the JWT structure
2. Verifying the issuer matches expected provider
3. Checking token expiration
4. Validating signature using provider's JWKS (in production)

**Note**: The current implementation uses simplified JWT validation. Production deployments should use a proper JWT library (e.g., auth0/java-jwt, nimbus-jose-jwt) for full validation including:
- Signature verification with RS256/ES256
- Clock skew tolerance
- Audience validation
- Nonce validation

### Trust Path Validation

Before initiating authentication, the broker:
1. Verifies both providers exist in the trust network
2. Computes a valid trust path using TopologyProvider
3. Rejects requests if no path exists or path is unreachable

### CIBA Hop Limits

CIBA requests are limited to 2 hops maximum to ensure acceptable latency for backchannel authentication.

## Testing

Run tests:
```bash
mvn test -pl plugins/auth41-federation-broker
```

Test coverage: 25 unit tests covering:
- **FederationBrokerProvider** (14 tests):
  - Authentication request initiation
  - Token validation (format, issuer, expiration)
  - CIBA request initiation and polling
  - Error handling (provider not found, no trust path, etc.)
  - Domain model builders
- **FederatedAuthenticator** (11 tests):
  - Authentication flow with login_hint
  - Account chooser fallback
  - Callback handling and token exchange
  - Shadow user creation
  - Error scenarios (user not found, trust path validation, state mismatch, invalid tokens)

## Integration

### Keycloak Deployment

1. Build the plugin:
   ```bash
   mvn clean install -pl plugins/auth41-federation-broker
   ```

2. Copy JAR to Keycloak:
   ```bash
   cp target/auth41-federation-broker-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
   ```

3. Also deploy dependency plugins:
   ```bash
   cp ../auth41-trust-network/target/*.jar $KEYCLOAK_HOME/providers/
   cp ../auth41-topology/target/*.jar $KEYCLOAK_HOME/providers/
   cp ../auth41-discovery/target/*.jar $KEYCLOAK_HOME/providers/
   ```

4. Rebuild Keycloak:
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```

5. Start Keycloak:
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh start-dev
   ```

### Configuring the Authenticator in Keycloak

After deploying the plugin, configure the `auth41-federated` authenticator in your realm's browser authentication flow:

#### Option 1: Add to Existing Browser Flow

1. Navigate to **Authentication** → **Flows** in the Keycloak admin console
2. Select the **browser** flow
3. Click **Add execution**
4. Select **Auth41 Federated Authentication** from the provider list
5. Set the requirement level:
   - **REQUIRED** - Always use federated authentication
   - **ALTERNATIVE** - Allow federated OR local authentication
   - **DISABLED** - Disable federated authentication
6. Bind the flow to your client or realm

#### Option 2: Create New Federated Flow

1. Navigate to **Authentication** → **Flows**
2. Click **Create flow**
3. Name: `Federated Browser Flow`
4. Flow type: **Basic flow**
5. Click **Add execution**
6. Select **Auth41 Federated Authentication**
7. Set requirement to **REQUIRED**
8. Bind this flow in **Authentication** → **Bindings** → **Browser Flow**

#### Client-Specific Configuration

To use federated authentication for specific clients only:

1. Navigate to **Clients** → Select client → **Advanced** tab
2. **Authentication Flow Overrides**:
   - Browser Flow: `Federated Browser Flow`
3. Save changes

#### Testing the Authenticator

To test the federated authentication flow:

1. Configure a trust network with at least one federated provider
2. Register user associations in the discovery service
3. Navigate to the client's login URL with `login_hint` parameter:
   ```
   https://keycloak.example.com/realms/myrealm/protocol/openid-connect/auth?
     client_id=my-client&
     response_type=code&
     scope=openid&
     redirect_uri=https://myapp.example.com/callback&
     login_hint=user@federatedprovider.com
   ```
4. The authenticator will:
   - Discover the user's home provider (`federatedprovider.com`)
   - Validate trust path
   - Redirect to home provider for authentication
   - Handle callback and create shadow user

## Future Enhancements

- **Production JWT Validation**: Integrate proper JWT library for full signature verification
- **Token Caching**: Cache validated tokens to reduce validation overhead
- **Metrics**: Track federation flows, hop counts, latencies
- **Dynamic Client Registration**: Support RFC 7591 for on-demand client registration
- **Claim Mapping**: Transform claims between provider-specific schemas
- **Push Mode CIBA**: Support ping/push notification modes in addition to poll

## License

Apache License 2.0
