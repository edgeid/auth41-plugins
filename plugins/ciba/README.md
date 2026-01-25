# Auth41 CIBA Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.apifocal.auth41.plugin/auth41-ciba.svg)](https://central.sonatype.com/artifact/org.apifocal.auth41.plugin/auth41-ciba)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Keycloak plugin implementing Client-Initiated Backchannel Authentication (CIBA) for decoupled authentication flows.

## Overview

CIBA enables authentication scenarios where the user authenticates on a different device than the one initiating the request. This is useful for:

- **IoT devices** without input capabilities
- **Call center scenarios** where agents initiate authentication for customers
- **Mobile wallet authentication** flows
- **Command-line tools** and headless applications

## Features

- ✅ **CIBA Authentication Endpoint** - Initiate backchannel authentication requests
- ✅ **CIBA Token Endpoint** - Poll for authentication status and retrieve OAuth2 tokens
- ✅ **OAuth2 Token Generation** - Full integration with Keycloak's token system
- ✅ **Poll Delivery Mode** - Client polling-based authentication flow
- ✅ **Pluggable Backchannel Providers** - Support for different notification mechanisms
- ✅ **Request Validation** - Complete parameter validation per CIBA spec
- ✅ **User Discovery** - Resolve users by username or email via login_hint
- ✅ **Scope Support** - Honor requested OAuth2 scopes
- ⏳ **Ping/Push Modes** - Planned for future releases

## Quick Start

### Installation

**From Maven Central** (recommended):

```bash
# Download artifacts
mvn dependency:get -Dartifact=org.apifocal.auth41:auth41-ciba-spi:1.0.0-alpha.2
mvn dependency:get -Dartifact=org.apifocal.auth41.plugin:auth41-ciba:1.0.0-alpha.2
mvn dependency:get -Dartifact=org.apifocal.auth41.plugin:auth41-backchannel-mock:1.0.0-alpha.2

# Copy to Keycloak
cp ~/.m2/repository/org/apifocal/auth41/auth41-ciba-spi/1.0.0-alpha.2/*.jar $KEYCLOAK_HOME/providers/
cp ~/.m2/repository/org/apifocal/auth41/plugin/auth41-ciba/1.0.0-alpha.2/*.jar $KEYCLOAK_HOME/providers/
cp ~/.m2/repository/org/apifocal/auth41/plugin/auth41-backchannel-mock/1.0.0-alpha.2/*.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

**From Source**:

```bash
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins
mvn clean install

cp lib/auth41-ciba-spi/target/auth41-ciba-spi-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-ciba/target/auth41-ciba-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-backchannel-mock/target/auth41-backchannel-mock-*.jar $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
```

### Configuration

Start Keycloak with a backchannel provider:

```bash
# Using mock provider for testing (recommended for development)
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=2000
```

## Usage

### 1. Initiate Authentication

```bash
curl -X POST https://keycloak.example.com/realms/myrealm/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=my-client" \
  -d "client_secret=secret" \
  -d "scope=openid profile email" \
  -d "login_hint=user@example.com" \
  -d "binding_message=Login to MyApp"
```

**Response**:
```json
{
  "auth_req_id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "expires_in": 300,
  "interval": 5
}
```

### 2. Poll for Token

```bash
curl -X POST https://keycloak.example.com/realms/myrealm/ext/ciba/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=my-client" \
  -d "auth_req_id=urn:uuid:550e8400-e29b-41d4-a716-446655440000"
```

**Response (pending)**:
```json
{
  "error": "authorization_pending",
  "error_description": "The authorization request is still pending"
}
```

**Response (approved)**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_expires_in": 1800,
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid profile email"
}
```

**Response (denied)**:
```json
{
  "error": "access_denied",
  "error_description": "User denied the authentication request"
}
```

## API Reference

### Backchannel Authentication Endpoint

**Endpoint**: `POST /realms/{realm}/ext/ciba/auth`

**Request Parameters**:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `client_id` | Yes | OAuth2 client identifier |
| `client_secret` | Yes* | Client secret (*currently required; JWT/mTLS planned) |
| `scope` | No | OAuth2 scopes (default: "openid") |
| `login_hint` | Yes | User identifier (username or email) |
| `binding_message` | No | Message shown to user (max 256 chars) |
| `user_code` | No | User confirmation code (not yet implemented) |
| `requested_expiry` | No | Request expiry in seconds (default: 300) |

**Response** (200 OK):
```json
{
  "auth_req_id": "urn:uuid:...",
  "expires_in": 300,
  "interval": 5
}
```

**Error Responses**:
- `400 invalid_request` - Missing or invalid parameters
- `401 unauthorized_client` - Invalid client credentials
- `404 unknown_user_id` - User not found
- `500 server_error` - Internal error or no backchannel provider

### Token Polling Endpoint

**Endpoint**: `POST /realms/{realm}/ext/ciba/token`

**Request Parameters**:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `client_id` | Yes | OAuth2 client identifier |
| `auth_req_id` | Yes | Authentication request ID from auth endpoint |

**Response** (200 OK - approved):
```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "...",
  "refresh_expires_in": 1800,
  "id_token": "...",
  "scope": "openid profile email"
}
```

**Error Responses**:
- `400 authorization_pending` - Authentication still pending (client should retry)
- `400 invalid_request` - Missing or invalid auth_req_id
- `403 access_denied` - User denied the request
- `401 unauthorized_client` - Invalid client

## Backchannel Providers

CIBA uses pluggable backchannel providers to notify users. Choose one:

### Mock Provider (Testing Only)

**⚠️ DO NOT USE IN PRODUCTION**

Auto-approves/denies requests after a configurable delay.

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=2000 \
  --spi-backchannel-mock-test-only-approval-rate=100 \
  --spi-backchannel-mock-test-only-error-rate=0
```

See [auth41-backchannel-mock README](../auth41-backchannel-mock/README.md) for details.

### File Provider (Integration Testing)

Uses file system inbox/outbox for manual testing and integration tests.

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=file \
  --spi-backchannel-file-base-directory=/var/auth41/backchannel
```

See [auth41-backchannel-file README](../auth41-backchannel-file/README.md) for details.

### Production Providers (Future)

Production-ready providers planned:
- **Push Notification Provider** - Mobile push notifications
- **SMS Provider** - SMS-based authentication
- **Email Provider** - Email-based authentication links

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Client Application                 │
└────────────────┬────────────────────────────────┘
                 │
        1. POST /ext/ciba/auth
        2. Poll /ext/ciba/token
                 │
                 ▼
┌─────────────────────────────────────────────────┐
│         Auth41 CIBA Plugin (Keycloak)           │
│  ┌──────────────────┐  ┌────────────────────┐  │
│  │ CibaAuthResource │  │ CibaTokenResource  │  │
│  └────────┬─────────┘  └──────────┬─────────┘  │
│           │                       │             │
│           ▼                       ▼             │
│  ┌────────────────────────────────────────┐    │
│  │   BackchannelProvider SPI Interface    │    │
│  └────────┬───────────────────────────────┘    │
└───────────┼────────────────────────────────────┘
            │
            ▼
┌───────────────────────────────────────────────┐
│       Backchannel Implementation              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐   │
│  │   Mock   │  │   File   │  │Push (TBD) │   │
│  └──────────┘  └──────────┘  └───────────┘   │
└───────────────────────────────────────────────┘
            │
            ▼
    User Authentication Device
```

## Configuration Reference

### System Properties

```bash
# Select backchannel provider
--spi-backchannel-provider=mock-test-only

# Mock provider options
--spi-backchannel-mock-test-only-delay=2000
--spi-backchannel-mock-test-only-approval-rate=100
--spi-backchannel-mock-test-only-error-rate=0
--spi-backchannel-mock-test-only-auto-approve=true

# File provider options
--spi-backchannel-file-base-directory=/var/auth41/backchannel
```

### Realm Configuration

Configure CIBA settings in Keycloak Admin Console:

1. Go to **Realm Settings** → **Tokens**
2. Set **Access Token Lifespan** (affects CIBA token expiry)
3. Set **Refresh Token Lifespan** (affects CIBA refresh token)

Configure client for CIBA:

1. Go to **Clients** → Select your client
2. Enable **Client authentication**
3. Configure **Valid redirect URIs** (for standard flows)

## Security Considerations

### Client Authentication

Current implementation supports `client_secret` authentication. Future releases will add:
- JWT client assertion (`client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer`)
- Mutual TLS (mTLS)

### Request Expiry

- Default expiry: 300 seconds (5 minutes)
- Clients should respect the `interval` parameter when polling (default: 5 seconds)
- Expired requests are automatically cleaned up

### Rate Limiting

Implement rate limiting at the reverse proxy level:
- Limit authentication initiation requests per client
- Limit polling frequency (respect `interval` parameter)

## Troubleshooting

### "No BackchannelProvider available"

**Cause**: No backchannel implementation is deployed.

**Solution**:
1. Ensure a backchannel provider JAR is in `$KEYCLOAK_HOME/providers/`
2. Run `kc.sh build` to rebuild Keycloak
3. Check logs for provider loading messages

### "Unknown user" error

**Cause**: User specified in `login_hint` doesn't exist.

**Solution**:
- Verify user exists in the realm
- Check `login_hint` matches username or email
- Check realm user federation settings

### Authentication never completes

**Cause**: Backchannel provider not processing requests.

**Solution**:
- Check backchannel provider logs
- For mock: verify auto-approve is enabled
- For file: verify inbox/outbox directories exist and are writable

### Token generation fails

**Cause**: Missing required client/realm configuration.

**Solution**:
- Verify client has "Client authentication" enabled
- Check token lifespan settings in realm
- Review Keycloak logs for detailed error messages

## Examples

### Complete Flow with Mock Provider

```bash
# 1. Start Keycloak with mock provider
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=1000

# 2. Initiate authentication
AUTH_RESPONSE=$(curl -s -X POST http://localhost:8080/realms/master/ext/ciba/auth \
  -d "client_id=admin-cli" \
  -d "scope=openid profile" \
  -d "login_hint=admin")

AUTH_REQ_ID=$(echo $AUTH_RESPONSE | jq -r '.auth_req_id')
echo "Auth Request ID: $AUTH_REQ_ID"

# 3. Wait for mock delay
sleep 2

# 4. Poll for token
TOKEN_RESPONSE=$(curl -s -X POST http://localhost:8080/realms/master/ext/ciba/token \
  -d "client_id=admin-cli" \
  -d "auth_req_id=$AUTH_REQ_ID")

echo $TOKEN_RESPONSE | jq '.'

# 5. Extract and use access token
ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/realms/master/protocol/openid-connect/userinfo
```

## Specification Compliance

Implements [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html):

- ✅ Backchannel authentication endpoint
- ✅ Poll delivery mode
- ✅ Token endpoint with `auth_req_id`
- ✅ OAuth2 token generation (access, refresh, ID tokens)
- ✅ Request validation
- ✅ Standard error codes
- ⏳ Ping mode (planned)
- ⏳ Push mode (planned)
- ⏳ User code verification (planned)

## Related Documentation

- [CIBA Plugin Documentation](../../docs/plugins/ciba.md) - Comprehensive guide
- [Architecture Overview](../../docs/architecture.md) - System design
- [Development Guide](../../docs/development.md) - Contributing

## Support

- **Issues**: [GitHub Issues](https://github.com/apifocal/auth41-plugins/issues)
- **Documentation**: [Full Documentation](../../docs/README.md)
- **License**: Apache License 2.0

## Version History

### 1.0.0-alpha.2 (2025-12-31)
- ✅ Implemented OAuth2 token generation
- ✅ Full integration with Keycloak TokenManager
- ✅ Support for access tokens, refresh tokens, and ID tokens
- ✅ Scope-based token claims
- ✅ Comprehensive unit tests

### 1.0.0-alpha.1 (2024-12-27)
- Initial release
- CIBA authentication endpoint
- Token polling endpoint (status only)
- Mock and file backchannel providers
