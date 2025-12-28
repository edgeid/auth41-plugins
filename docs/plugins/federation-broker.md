# Federation Broker Plugin

The Federation Broker is the core orchestrator of Auth41 federated authentication flows.

## Overview

**Plugin Name**: `auth41-federation-broker`
**SPI**: Keycloak `Authenticator`, `AuthenticatorFactory`
**Purpose**: Orchestrate federated authentication across multiple identity providers

The Federation Broker intercepts authentication requests, discovers the user's home provider, validates trust, redirects for authentication, and creates shadow accounts.

## Authentication Flow

```
1. User → Service: Access protected resource
2. Service → Keycloak: OIDC auth request
3. Keycloak → Federation Broker: Execute authenticator
4. Federation Broker → Discovery: Find user's home provider
5. Federation Broker → Topology: Validate trust path
6. Federation Broker → User: Redirect to home provider
7. User ↔ Home Provider: Authenticate (login, MFA, etc.)
8. Home Provider → Federation Broker: Return with ID token
9. Federation Broker: Validate ID token
10. Federation Broker → Accounts: Create/update shadow account
11. Federation Broker: Complete authentication
12. Keycloak → Service: Return with session/tokens
```

## Configuration

### Add to Authentication Flow

1. **Admin Console** → **Authentication** → **Flows**
2. **Duplicate** "Browser" flow or create new
3. Click **Add execution**
4. Select "Auth41 Federation Broker"
5. Set requirement: **REQUIRED** or **ALTERNATIVE**
6. Click **Actions** → **Config**
7. Configure:
   - Enable/disable auto-redirect
   - Prompt for provider selection
   - Configure redirect template

### Execution Requirements

- **REQUIRED**: All users must use federation
- **ALTERNATIVE**: Federation or local authentication
- **CONDITIONAL**: Use with conditions (e.g., email domain)

### System Properties

```bash
# Auto-redirect to home provider (skip account chooser)
-Dauth41.broker.auto-redirect=true

# Show account chooser when multiple providers match
-Dauth41.broker.show-account-chooser=false

# Token validation strictness
-Dauth41.broker.strict-validation=true

# OIDC state timeout (seconds)
-Dauth41.broker.state-timeout=300
```

## Key Responsibilities

### 1. User Identifier Extraction

Extract user identifier from authentication context:

```java
// From username form
String username = context.getHttpRequest()
    .getDecodedFormParameters()
    .getFirst("username");

// From login_hint parameter
String loginHint = context.getAuthenticationSession()
    .getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);

// From existing user (re-authentication)
UserModel user = context.getUser();
if (user != null) {
    username = user.getUsername();
}
```

### 2. Provider Discovery

Use Discovery plugin to find home provider:

```java
DiscoveryProvider discovery = session.getProvider(DiscoveryProvider.class);
ProviderInfo homeProvider = discovery.discoverProvider(username, loginHint);

if (homeProvider == null) {
    // Show account chooser or error
    context.challenge(createAccountChooserResponse());
    return;
}
```

### 3. Trust Validation

Validate trust path using Topology plugin:

```java
TrustNetworkProvider trustNetwork = session.getProvider(TrustNetworkProvider.class);
TopologyProvider topology = session.getProvider(TopologyProvider.class);

boolean hasTrust = topology.hasTrustPath(
    trustNetwork.getTrustNetwork(),
    localProviderId,
    homeProvider.getProviderId()
);

if (!hasTrust) {
    context.failure(AuthenticationFlowError.ACCESS_DENIED,
        Response.status(403).entity("Provider not trusted").build());
    return;
}
```

### 4. OIDC Redirection

Redirect user to home provider for authentication:

```java
// Build authorization URL
String authUrl = buildAuthorizationUrl(homeProvider, context);

// Store state for callback validation
String state = generateState();
context.getAuthenticationSession().setAuthNote("oidc_state", state);
context.getAuthenticationSession().setAuthNote("home_provider_id", homeProvider.getProviderId());

// Redirect
Response response = Response.status(302).location(URI.create(authUrl)).build();
context.challenge(response);
```

**Authorization URL**:
```
https://home-provider.edu/realms/students/protocol/openid-connect/auth
  ?client_id=service-provider-client-id
  &redirect_uri=https://service-provider.com/auth/realms/main/broker/callback
  &response_type=code
  &scope=openid profile email
  &state=random-state-value
  &nonce=random-nonce-value
```

### 5. Token Validation

Validate ID token from home provider:

```java
// Fetch JWKS from home provider
PublicKey publicKey = fetchJWKS(homeProvider.getJwksUri(), idToken.getKeyId());

// Verify signature
if (!verifySignature(idToken, publicKey)) {
    throw new AuthenticationException("Invalid token signature");
}

// Validate claims
validateClaim(idToken, "iss", homeProvider.getIssuer());
validateClaim(idToken, "aud", clientId);
validateExpiration(idToken);
validateNonce(idToken, expectedNonce);
```

### 6. Shadow Account Management

Create or update shadow account:

```java
FederatedAccountProvider accounts = session.getProvider(FederatedAccountProvider.class);

Map<String, Object> claims = idToken.getOtherClaims();
claims.put("sub", idToken.getSubject());
claims.put("email", idToken.getEmail());

UserModel user = accounts.createOrUpdateAccount(
    context.getRealm(),
    homeProvider.getProviderId(),
    claims
);

context.setUser(user);
```

### 7. Session Completion

Complete authentication and create session:

```java
context.success();
// Keycloak creates session and redirects to application
```

## Account Chooser UI

When multiple providers match or discovery fails, show account chooser:

### Template

Located at: `src/main/resources/theme/base/login/account-chooser.ftl`

```html
<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
        <form action="${url.loginAction}" method="post">
            <div class="provider-list">
                <#list providers as provider>
                    <button type="submit" name="provider" value="${provider.providerId}">
                        <span>${provider.metadata.name}</span>
                    </button>
                </#list>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
```

### Configuration

```bash
# Show account chooser when discovery ambiguous
-Dauth41.broker.show-account-chooser=true

# Provider selection timeout (seconds)
-Dauth41.broker.chooser-timeout=120
```

## Error Handling

### Discovery Failures

```java
if (homeProvider == null) {
    if (showAccountChooser) {
        context.challenge(createAccountChooserResponse());
    } else {
        context.failure(AuthenticationFlowError.UNKNOWN_USER);
    }
    return;
}
```

### Trust Validation Failures

```java
if (!hasTrustPath) {
    context.failure(
        AuthenticationFlowError.ACCESS_DENIED,
        Response.status(403)
            .entity("Provider not in trust network")
            .build()
    );
    return;
}
```

### Token Validation Failures

```java
try {
    validateToken(idToken);
} catch (TokenValidationException e) {
    logger.error("Token validation failed", e);
    context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
    return;
}
```

### Home Provider Unavailable

```java
try {
    response = callHomeProvider(authUrl);
} catch (IOException e) {
    logger.error("Home provider unavailable", e);
    context.failure(
        AuthenticationFlowError.INTERNAL_ERROR,
        Response.status(503)
            .entity("Identity provider temporarily unavailable")
            .build()
    );
    return;
}
```

## Security

### State Parameter

Prevents CSRF attacks:

```java
String state = UUID.randomUUID().toString();
session.setAuthNote("oidc_state", state);

// On callback
String returnedState = request.getParameter("state");
String expectedState = session.getAuthNote("oidc_state");

if (!state.equals(returnedState)) {
    throw new AuthenticationException("State mismatch");
}
```

### Nonce Parameter

Prevents replay attacks:

```java
String nonce = UUID.randomUUID().toString();
session.setAuthNote("oidc_nonce", nonce);

// In ID token validation
String tokenNonce = idToken.getNonce();
String expectedNonce = session.getAuthNote("oidc_nonce");

if (!nonce.equals(tokenNonce)) {
    throw new AuthenticationException("Nonce mismatch");
}
```

### Token Expiration

```java
Instant expiration = Instant.ofEpochSecond(idToken.getExp());
Instant now = Instant.now();

if (now.isAfter(expiration)) {
    throw new AuthenticationException("Token expired");
}

// Allow clock skew (default: 60 seconds)
if (now.isAfter(expiration.plusSeconds(clockSkew))) {
    throw new AuthenticationException("Token expired beyond clock skew");
}
```

## Testing

### Unit Tests

```bash
mvn test -pl plugins/auth41-federation-broker
```

**Test Coverage**:
- Provider discovery integration
- Trust path validation
- OIDC authorization URL construction
- Token validation
- Shadow account creation
- Error handling scenarios

### Integration Tests

Test with actual Keycloak:

1. Configure trust network
2. Set up identity providers
3. Add Federation Broker to authentication flow
4. Test end-to-end authentication
5. Verify shadow account created
6. Test with invalid scenarios (wrong provider, expired token, etc.)

## Performance

- **Discovery**: 5-10ms (cached)
- **Trust Validation**: 1-5ms
- **Redirection**: < 1ms
- **Token Validation**: 10-50ms (includes JWKS fetch)
- **Account Creation**: 20-100ms (database write)
- **Total Overhead**: 50-200ms per authentication

## Troubleshooting

### User Not Redirected

**Check**: Federation Broker in authentication flow, trust path valid, discovery successful

### Token Validation Fails

**Check**: Clock synchronization, JWKS endpoint accessible, issuer matches configuration

### Shadow Account Not Created

**Check**: Accounts plugin installed, attribute mappers configured, claims present in ID token

## Next Steps

- [Discovery Plugin](discovery.md) - Configure provider discovery
- [Topology Plugin](topology.md) - Validate trust paths
- [Accounts Plugin](accounts.md) - Manage shadow accounts
- [Configuration Guide](../configuration.md) - Complete federation setup
