# Auth41 CIBA Plugin

The Auth41 CIBA (Client-Initiated Backchannel Authentication) plugin implements the OpenID Connect CIBA specification, enabling decoupled authentication flows where users authenticate on a different device than the one requesting authentication.

## Overview

CIBA allows a client application to initiate an authentication request for a user, where the actual authentication happens on the user's registered authentication device (such as a mobile phone). This is particularly useful for:

- **IoT devices** without input capabilities
- **Call center scenarios** where an agent initiates authentication for a customer
- **Command-line tools** and headless applications
- **Mobile wallet authentication** flows

## Architecture

The CIBA implementation consists of three main components:

### 1. CIBA SPI (auth41-ciba-spi)

Core abstraction layer defining the backchannel provider interface:

```java
public interface BackchannelProvider extends Provider {
    void initiateAuthentication(BackchannelAuthRequest request) throws BackchannelException;
    BackchannelAuthStatus getAuthenticationStatus(String authReqId) throws BackchannelException;
    void cancelAuthentication(String authReqId) throws BackchannelException;
    int cleanupExpiredRequests(int maxAgeSeconds);
    Set<String> getSupportedDeliveryModes();
}
```

### 2. CIBA Authentication Flow (auth41-ciba)

Implements the CIBA authentication endpoint and token exchange:

- **Backchannel Authentication Endpoint**: `POST /realms/{realm}/ext/ciba/auth`
- Request validation and user discovery
- Integration with BackchannelProvider implementations
- OAuth2-compliant error responses

### 3. Backchannel Implementations

Pluggable implementations of the BackchannelProvider SPI:

- **auth41-backchannel-file**: File-based implementation for testing (inbox/outbox pattern)
- **auth41-backchannel-push** (future): Push notification implementation for production

## Installation

### 1. Build the Plugins

```bash
cd auth41-plugins
mvn clean install
```

### 2. Deploy to Keycloak

Copy the required JARs to Keycloak's providers directory:

```bash
# Core CIBA components
cp lib/auth41-ciba-spi/target/auth41-ciba-spi-*.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-ciba/target/auth41-ciba-*.jar $KEYCLOAK_HOME/providers/

# File-based backchannel (for testing)
cp plugins/auth41-backchannel-file/target/auth41-backchannel-file-*.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### 3. Configure Backchannel Provider

Configure the file-based backchannel (for testing):

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-file-base-directory=/var/auth41/backchannel
```

## Usage

### CIBA Authentication Flow

1. **Client initiates authentication**:

```bash
curl -X POST https://keycloak.example.com/realms/myrealm/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=my-client" \
  -d "client_secret=secret" \
  -d "scope=openid profile" \
  -d "login_hint=user@example.com" \
  -d "binding_message=Login to MyApp"
```

Response:
```json
{
  "auth_req_id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "expires_in": 300,
  "interval": 5
}
```

2. **Backchannel notifies user** (via configured BackchannelProvider)

For file-based backchannel, the request is written to:
```
/var/auth41/backchannel/inbox/550e8400-e29b-41d4-a716-446655440000.json
```

3. **User authenticates** (on their device/via test script)

For file-based testing, write a response to the outbox:
```bash
cat > /var/auth41/backchannel/outbox/550e8400-e29b-41d4-a716-446655440000.json <<EOF
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "userId": "user-123",
  "updatedAt": "2025-12-27T22:00:00Z"
}
EOF
```

4. **Client polls for status** (poll mode):

```bash
curl -X POST https://keycloak.example.com/realms/myrealm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn:openid:params:grant-type:ciba" \
  -d "client_id=my-client" \
  -d "client_secret=secret" \
  -d "auth_req_id=urn:uuid:550e8400-e29b-41d4-a716-446655440000"
```

## File-Based Backchannel (Testing)

The file-based backchannel uses an inbox/outbox pattern for local testing:

### Directory Structure

```
/var/auth41/backchannel/
├── inbox/                  # Keycloak writes auth requests here
│   └── {auth_req_id}.json
└── outbox/                 # Test scripts write responses here
    └── {auth_req_id}.json
```

### Request Format (Inbox)

```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "clientId": "my-client",
  "scope": "openid profile",
  "loginHint": "user@example.com",
  "bindingMessage": "Login to MyApp",
  "requestedExpiry": 300,
  "createdAt": "2025-12-27T22:00:00Z"
}
```

### Response Format (Outbox)

**Approved**:
```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "userId": "user-123",
  "updatedAt": "2025-12-27T22:05:00Z"
}
```

**Denied**:
```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "DENIED",
  "errorCode": "access_denied",
  "errorDescription": "User denied the authentication request",
  "updatedAt": "2025-12-27T22:05:00Z"
}
```

### Test Script Example

```bash
#!/bin/bash
# Simple CIBA test responder

INBOX="/var/auth41/backchannel/inbox"
OUTBOX="/var/auth41/backchannel/outbox"

# Watch for new auth requests
inotifywait -m "$INBOX" -e create |
while read path action file; do
    if [[ "$file" == *.json ]]; then
        AUTH_REQ_ID="${file%.json}"

        # Simulate user approval after 2 seconds
        sleep 2

        # Write approval response
        cat > "$OUTBOX/$file" <<EOF
{
  "authReqId": "$AUTH_REQ_ID",
  "status": "APPROVED",
  "userId": "test-user",
  "updatedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
        echo "Approved authentication: $AUTH_REQ_ID"
    fi
done
```

## Configuration

### Backchannel Provider Selection

Keycloak will use the first available BackchannelProvider. To specify a particular provider:

```bash
# Use file-based backchannel
-Dspi-backchannel-provider=file
```

### File Backchannel Configuration

```bash
# Custom base directory
-Dspi-backchannel-file-base-directory=/custom/path

# Example with all options
$KEYCLOAK_HOME/bin/kc.sh start \
  --spi-backchannel-provider=file \
  --spi-backchannel-file-base-directory=/var/auth41/backchannel
```

## Security Considerations

### Client Authentication

The current implementation performs basic client validation. For production use:

1. Implement proper client authentication (client_secret, JWT, mTLS)
2. Validate client is authorized for CIBA
3. Enforce rate limiting on authentication requests

### Request Validation

- **login_hint**: Required. Can be username or email address
- **binding_message**: Optional. Displayed to user during authentication
- **user_code**: Optional. Short code user must enter to confirm
- **requested_expiry**: Optional. Request expiry in seconds (default: 300)

### Response Security

- Auth request IDs use UUID format with URN prefix
- Expired requests are automatically cleaned up
- Status checks are rate-limited (default: 5 second interval)

## Monitoring

### Logging

CIBA operations are logged with the following markers:

```
INFO: CIBA authentication initiated: auth_req_id=..., client=..., user=...
WARN: User not found for login_hint: ...
ERROR: No BackchannelProvider available
```

### Metrics

Monitor the following:

- Authentication request rate
- Success/failure ratio
- Average response time
- Expired request count (from cleanup operations)

## Troubleshooting

### "No BackchannelProvider available"

**Cause**: No backchannel provider is deployed or registered.

**Solution**: Ensure at least one backchannel implementation JAR is in the providers directory and Keycloak has been rebuilt.

### Authentication requests never complete

**Cause**: Backchannel provider is not processing requests or responses are not being written.

**Solution**:
- Check backchannel provider logs
- For file-based: verify inbox/outbox directories exist and are writable
- For file-based: verify response files are being created with correct format

### "Unknown user" error

**Cause**: User specified in login_hint doesn't exist in the realm.

**Solution**: Ensure the user exists and the login_hint matches either username or email.

## Specification Compliance

This implementation follows the [OpenID Connect CIBA Core specification](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html):

- ✅ Backchannel authentication endpoint
- ✅ Poll delivery mode
- ✅ Auth request ID generation
- ✅ Request parameter validation
- ✅ OAuth2 error responses
- ⏳ Ping mode (planned)
- ⏳ Push mode (planned)
- ⏳ Token endpoint integration (in progress)

## Examples

See [CIBA Examples](../examples/ciba.md) for complete end-to-end scenarios.

## Related

- [Trust Network Plugin](trust-network.md) - CIBA can be combined with federation
- [Accounts Plugin](accounts.md) - Shadow accounts support CIBA flows
- [Architecture](../architecture.md) - Overall system design
