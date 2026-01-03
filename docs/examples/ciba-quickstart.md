# CIBA Quick Start Guide

This guide walks you through setting up and testing the CIBA (Client-Initiated Backchannel Authentication) plugin with Auth41.

## Prerequisites

- Keycloak 26.x or later installed
- Java 17+
- Maven 3.8+ (for building)
- `curl` for testing
- Auth41 plugins built (`mvn clean install`)

## Scenario: Testing CIBA with Mock Backchannel

This is the fastest way to get CIBA running for development and testing.

### Step 1: Deploy the Plugins

```bash
# Set Keycloak home
export KEYCLOAK_HOME=/path/to/keycloak

# Copy core CIBA components
cp lib/auth41-ciba-spi/target/auth41-ciba-spi-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
cp plugins/auth41-ciba/target/auth41-ciba-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/

# Copy mock backchannel for automated testing
cp plugins/auth41-backchannel-mock/target/auth41-backchannel-mock-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### Step 2: Start Keycloak with Mock Backchannel

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=2000 \
  --spi-backchannel-mock-test-only-approval-rate=100
```

**What this does:**
- Enables the mock backchannel provider
- Configures 2-second delay before auto-approval
- Sets 100% approval rate (all requests approved)

### Step 3: Create a Test Realm and Client

Using Keycloak Admin Console (`http://localhost:8080`):

1. **Create a test realm**:
   - Name: `ciba-test`

2. **Create a test user**:
   - Username: `testuser`
   - Email: `testuser@example.com`
   - Set password: `password` (temporary: off)

3. **Create a CIBA client**:
   - Client ID: `ciba-client`
   - Client authentication: ON
   - Authentication flow: Enable "Direct access grants"
   - Set client secret (e.g., `secret`)

### Step 4: Initiate CIBA Authentication

```bash
# Initiate backchannel authentication
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret" \
  -d "scope=openid profile email" \
  -d "login_hint=testuser@example.com" \
  -d "binding_message=Please approve login to TestApp"
```

**Expected response:**
```json
{
  "auth_req_id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "expires_in": 300,
  "interval": 5
}
```

**Save the `auth_req_id`** - you'll need it for polling.

### Step 5: Wait for Auto-Approval

The mock backchannel will automatically approve after 2 seconds (configured delay).

Check Keycloak logs - you should see:
```
WARN: [MOCK] Auto-approving request: urn:uuid:550e8400-e29b-41d4-a716-446655440000 (user=testuser)
```

### Step 6: Poll for Status (Optional - Token Endpoint Not Yet Implemented)

**Note**: The token endpoint integration is not yet implemented. Currently, you can verify the authentication was initiated and approved via logs and the `getAuthenticationStatus()` method in tests.

**Future implementation** (planned):
```bash
# This will work once token endpoint is implemented
curl -X POST http://localhost:8080/realms/ciba-test/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn:openid:params:grant-type:ciba" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret" \
  -d "auth_req_id=urn:uuid:550e8400-e29b-41d4-a716-446655440000"
```

## Scenario: Manual Testing with File-Based Backchannel

For integration testing or manual approval workflows.

### Step 1: Deploy with File Backchannel

```bash
# Copy file backchannel instead of mock
cp plugins/auth41-backchannel-file/target/auth41-backchannel-file-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/

# Rebuild
$KEYCLOAK_HOME/bin/kc.sh build
```

### Step 2: Create Backchannel Directories

```bash
mkdir -p /tmp/auth41-backchannel/{inbox,outbox}
chmod 777 /tmp/auth41-backchannel/{inbox,outbox}
```

### Step 3: Start Keycloak with File Backchannel

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=file-test-only \
  --spi-backchannel-file-base-directory=/tmp/auth41-backchannel
```

### Step 4: Initiate Authentication

```bash
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret" \
  -d "scope=openid profile" \
  -d "login_hint=testuser@example.com" \
  -d "binding_message=Login to MyApp"
```

### Step 5: Check the Inbox

```bash
ls -la /tmp/auth41-backchannel/inbox/

# View the request
cat /tmp/auth41-backchannel/inbox/urn:uuid:*.json
```

**Example inbox content:**
```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "clientId": "ciba-client",
  "scope": "openid profile",
  "loginHint": "testuser@example.com",
  "bindingMessage": "Login to MyApp",
  "createdAt": "2025-12-28T04:00:00Z"
}
```

### Step 6: Approve the Request

Create a response file in the outbox:

```bash
AUTH_REQ_ID="urn:uuid:550e8400-e29b-41d4-a716-446655440000"  # Use actual ID from response

cat > "/tmp/auth41-backchannel/outbox/${AUTH_REQ_ID}.json" <<EOF
{
  "authReqId": "${AUTH_REQ_ID}",
  "status": "APPROVED",
  "userId": "testuser",
  "updatedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
```

**To deny instead:**
```bash
cat > "/tmp/auth41-backchannel/outbox/${AUTH_REQ_ID}.json" <<EOF
{
  "authReqId": "${AUTH_REQ_ID}",
  "status": "DENIED",
  "errorCode": "access_denied",
  "errorDescription": "User denied the authentication request",
  "updatedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
```

## Testing Different Scenarios

### 1. Fast Testing (100ms delay)

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=100
```

### 2. Realistic Testing (80% approve, 10% deny, 10% error)

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-approval-rate=80 \
  --spi-backchannel-mock-test-only-error-rate=10
```

### 3. Test Binding Message Validation

```bash
# This should succeed (256 chars max)
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret" \
  -d "login_hint=testuser@example.com" \
  -d "binding_message=Short message"

# This should fail (exceeds 256 chars)
LONG_MSG=$(printf 'A%.0s' {1..300})
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret" \
  -d "login_hint=testuser@example.com" \
  -d "binding_message=${LONG_MSG}"
```

**Expected error for too-long message:**
```json
{
  "error": "invalid_request",
  "error_description": "binding_message exceeds maximum length of 256 characters"
}
```

### 4. Test Missing Required Parameters

```bash
# Missing login_hint
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "client_secret=secret"
```

**Expected error:**
```json
{
  "error": "invalid_request",
  "error_description": "Missing login_hint parameter"
}
```

### 5. Test Token Polling

After initiating authentication, poll the token endpoint:

```bash
AUTH_REQ_ID="urn:uuid:550e8400-e29b-41d4-a716-446655440000"  # Use actual ID from auth response

# Poll while pending
curl -X POST http://localhost:8080/realms/ciba-test/ext/ciba/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ciba-client" \
  -d "auth_req_id=${AUTH_REQ_ID}"
```

**Response when still pending:**
```json
{
  "error": "authorization_pending",
  "error_description": "The authorization request is still pending"
}
```

**Response when approved** (after user approves via backchannel):
```json
{
  "status": "APPROVED",
  "auth_req_id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "user_id": "testuser",
  "username": "testuser@example.com",
  "message": "Authentication approved. Token generation will be implemented in next version."
}
```

**Response when denied:**
```json
{
  "error": "access_denied",
  "error_description": "User denied the authentication request"
}
```

## Automated Test Script

Create a simple test script:

```bash
#!/bin/bash
# test-ciba.sh

REALM="ciba-test"
CLIENT_ID="ciba-client"
CLIENT_SECRET="secret"
LOGIN_HINT="testuser@example.com"

echo "=== Testing CIBA Authentication ==="

# Initiate authentication
echo "1. Initiating authentication..."
RESPONSE=$(curl -s -X POST http://localhost:8080/realms/$REALM/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "scope=openid profile" \
  -d "login_hint=$LOGIN_HINT" \
  -d "binding_message=Test login")

echo "Response: $RESPONSE"

# Extract auth_req_id (prefer jq for robust JSON parsing, fallback to grep if jq is unavailable)
if command -v jq >/dev/null 2>&1; then
    AUTH_REQ_ID=$(echo "$RESPONSE" | jq -r '.auth_req_id // empty')
else
    AUTH_REQ_ID=$(echo "$RESPONSE" | grep -o '"auth_req_id":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$AUTH_REQ_ID" ]; then
    echo "❌ Failed to get auth_req_id"
    exit 1
fi

echo "✓ Got auth_req_id: $AUTH_REQ_ID"

# Poll for status
echo "2. Polling for authentication status..."
for i in {1..10}; do
    echo "   Attempt $i..."
    TOKEN_RESPONSE=$(curl -s -X POST http://localhost:8080/realms/$REALM/ext/ciba/token \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "client_id=$CLIENT_ID" \
      -d "auth_req_id=$AUTH_REQ_ID")

    # Check if approved
    if command -v jq >/dev/null 2>&1; then
        STATUS=$(echo "$TOKEN_RESPONSE" | jq -r '.status // .error')
    else
        STATUS=$(echo "$TOKEN_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        [ -z "$STATUS" ] && STATUS=$(echo "$TOKEN_RESPONSE" | grep -o '"error":"[^"]*"' | cut -d'"' -f4)
    fi

    if [ "$STATUS" = "APPROVED" ]; then
        echo "✓ Authentication approved!"
        echo "Response: $TOKEN_RESPONSE"
        exit 0
    elif [ "$STATUS" = "authorization_pending" ]; then
        echo "   Still pending, waiting..."
        sleep 2
    else
        echo "❌ Authentication failed: $STATUS"
        echo "Response: $TOKEN_RESPONSE"
        exit 1
    fi
done

echo "❌ Timeout: Authentication not completed within polling period"
```

Make it executable:
```bash
chmod +x test-ciba.sh
./test-ciba.sh
```

## Troubleshooting

### "No BackchannelProvider available"

**Solution**: Ensure you deployed at least one backchannel JAR and ran `kc.sh build`.

### Authentication never completes (file-based)

**Solution**: Check that:
1. Directories exist: `/tmp/auth41-backchannel/{inbox,outbox}`
2. Permissions are correct (writable by Keycloak process)
3. Response file was created in outbox with correct auth_req_id

### Mock provider not auto-approving

**Solution**: Check:
1. Provider ID is `mock-test-only` (not just `mock`)
2. `auto-approve=true` (default)
3. Sufficient delay has passed
4. Check Keycloak logs for `[MOCK]` messages

## Next Steps

1. **Token Generation**: Integrate with Keycloak's TokenManager to generate OAuth2 access_token, refresh_token, and id_token
2. **Client Authentication**: Implement proper client_secret, JWT, or mTLS authentication for token endpoint
3. **Push Notification Backchannel**: Implement production-ready push notification provider
4. **Federation Integration**: Combine CIBA with Auth41 federation for cross-organization authentication

## Related Documentation

- [CIBA Plugin Documentation](../plugins/ciba.md)
- [Mock Backchannel README](../../plugins/auth41-backchannel-mock/README.md)
- [File Backchannel README](../../plugins/auth41-backchannel-file/README.md)
- [CIBA Specification](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
