# Auth41 File-Based Backchannel Provider

[![Maven Central](https://img.shields.io/maven-central/v/org.apifocal.auth41.plugin/auth41-backchannel-file.svg)](https://central.sonatype.com/artifact/org.apifocal.auth41.plugin/auth41-backchannel-file)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

File-based backchannel provider for CIBA testing and integration scenarios using an inbox/outbox pattern.

## Overview

This provider implements the `BackchannelProvider` SPI using file system storage, enabling manual testing and integration testing of CIBA flows without requiring mobile apps or push notification infrastructure.

**Use Cases:**
- Manual testing of CIBA authentication flows
- Integration testing with test automation scripts
- Development and debugging of CIBA implementations
- Demonstration and prototyping scenarios

**⚠️ Not Recommended for Production** - This provider is designed for testing. For production deployments, use a push notification or other real-time backchannel provider.

## How It Works

### Inbox/Outbox Pattern

```
/var/auth41/backchannel/
├── inbox/                    # Keycloak writes auth requests here
│   └── {auth_req_id}.json   # One file per authentication request
└── outbox/                   # Test scripts/users write responses here
    └── {auth_req_id}.json   # Response file (approved/denied/error)
```

**Flow:**

1. **Client initiates CIBA authentication** → Keycloak calls `initiateAuthentication()`
2. **Provider writes request to inbox/** → `{auth_req_id}.json` contains authentication details
3. **External process/user reviews request** → Read from inbox, decide to approve/deny
4. **External process writes response to outbox/** → Create `{auth_req_id}.json` with status
5. **Client polls token endpoint** → Provider reads response from outbox
6. **Provider returns status to Keycloak** → Client receives tokens or error

## Installation

**From Maven Central**:

```bash
mvn dependency:get -Dartifact=org.apifocal.auth41.plugin:auth41-backchannel-file:1.0.0-alpha.2

cp ~/.m2/repository/org/apifocal/auth41/plugin/auth41-backchannel-file/1.0.0-alpha.2/*.jar \
   $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
```

**From Source**:

```bash
git clone https://github.com/apifocal/auth41-plugins.git
cd auth41-plugins
mvn clean install -pl plugins/auth41-backchannel-file -am

cp plugins/auth41-backchannel-file/target/auth41-backchannel-file-*.jar \
   $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
```

## Configuration

### Basic Configuration

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=file \
  --spi-backchannel-file-base-directory=/var/auth41/backchannel
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `base-directory` | `/tmp/auth41-backchannel` | Base directory for inbox/outbox subdirectories |

### Directory Setup

The provider automatically creates `inbox/` and `outbox/` subdirectories if they don't exist. Ensure the Keycloak process has read/write permissions:

```bash
mkdir -p /var/auth41/backchannel/{inbox,outbox}
chown -R keycloak:keycloak /var/auth41/backchannel
chmod 755 /var/auth41/backchannel/{inbox,outbox}
```

## File Formats

### Request File (Inbox)

**Location**: `inbox/{auth_req_id}.json`

**Format**:
```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "clientId": "my-client",
  "scope": "openid profile email",
  "loginHint": "user@example.com",
  "bindingMessage": "Login to MyApp",
  "userCode": null,
  "requestedExpiry": 300,
  "createdAt": "2025-12-31T10:00:00Z"
}
```

**Fields:**
- `authReqId` - Unique authentication request identifier (correlation ID)
- `clientId` - OAuth2 client initiating the request
- `scope` - Requested OAuth2 scopes
- `loginHint` - User identifier (email or username)
- `bindingMessage` - Message to display to user during authentication
- `userCode` - Optional user confirmation code
- `requestedExpiry` - Expiry time in seconds
- `createdAt` - Timestamp when request was created

### Response File (Outbox)

**Location**: `outbox/{auth_req_id}.json`

Create this file to approve, deny, or error the authentication request.

#### Approved Response

```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "userId": "user-123",
  "scope": "openid profile email",
  "updatedAt": "2025-12-31T10:01:00Z"
}
```

**Fields:**
- `authReqId` - Must match the request ID (correlation)
- `status` - Must be `"APPROVED"`
- `userId` - Keycloak user ID (required for token generation)
- `scope` - Approved scopes (optional, defaults to requested scope)
- `updatedAt` - Timestamp of approval

#### Denied Response

```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "DENIED",
  "errorCode": "access_denied",
  "errorDescription": "User declined the authentication request",
  "updatedAt": "2025-12-31T10:01:00Z"
}
```

**Fields:**
- `status` - Must be `"DENIED"`
- `errorCode` - OAuth2 error code (e.g., "access_denied")
- `errorDescription` - Human-readable error description

#### Error Response

```json
{
  "authReqId": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "status": "ERROR",
  "errorCode": "expired_token",
  "errorDescription": "The authentication request has expired",
  "updatedAt": "2025-12-31T10:06:00Z"
}
```

**Fields:**
- `status` - Must be `"ERROR"`
- `errorCode` - Error code (e.g., "expired_token", "server_error")
- `errorDescription` - Error details

## Usage Examples

### Manual Testing

**Step 1: Start Keycloak**

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=file \
  --spi-backchannel-file-base-directory=/tmp/ciba-test
```

**Step 2: Initiate CIBA Authentication**

```bash
curl -X POST http://localhost:8080/realms/master/ext/ciba/auth \
  -d "client_id=admin-cli" \
  -d "scope=openid profile" \
  -d "login_hint=admin"
```

Response:
```json
{
  "auth_req_id": "urn:uuid:abc123...",
  "expires_in": 300,
  "interval": 5
}
```

**Step 3: Check Inbox**

```bash
ls /tmp/ciba-test/inbox/
# urn:uuid:abc123....json

cat /tmp/ciba-test/inbox/urn:uuid:abc123....json
```

**Step 4: Approve the Request**

```bash
cat > /tmp/ciba-test/outbox/urn:uuid:abc123....json <<EOF
{
  "authReqId": "urn:uuid:abc123...",
  "status": "APPROVED",
  "userId": "user-id-from-keycloak",
  "updatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
```

**Step 5: Poll for Token**

```bash
curl -X POST http://localhost:8080/realms/master/ext/ciba/token \
  -d "client_id=admin-cli" \
  -d "auth_req_id=urn:uuid:abc123..."
```

### Automated Testing Script

```bash
#!/bin/bash
# auto-approve.sh - Automatically approve CIBA requests

INBOX="/var/auth41/backchannel/inbox"
OUTBOX="/var/auth41/backchannel/outbox"

# Watch inbox for new requests
inotifywait -m "$INBOX" -e create -e moved_to |
while read -r path action file; do
    if [[ "$file" == *.json ]]; then
        echo "New CIBA request: $file"

        # Extract auth_req_id (filename without .json)
        AUTH_REQ_ID="${file%.json}"

        # Read request details
        REQUEST=$(cat "$INBOX/$file")
        LOGIN_HINT=$(echo "$REQUEST" | jq -r '.loginHint')

        echo "Login hint: $LOGIN_HINT"

        # Auto-approve after 2 seconds
        sleep 2

        # Look up user ID (example: query Keycloak REST API)
        USER_ID=$(curl -s "http://localhost:8080/admin/realms/master/users?username=$LOGIN_HINT" \
                  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

        # Write approval response
        cat > "$OUTBOX/$file" <<EOF
{
  "authReqId": "$AUTH_REQ_ID",
  "status": "APPROVED",
  "userId": "$USER_ID",
  "updatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

        echo "Approved: $AUTH_REQ_ID for user $USER_ID"
    fi
done
```

### Integration Test Example

```python
import time
import json
import requests
from pathlib import Path

KEYCLOAK_URL = "http://localhost:8080"
REALM = "master"
CLIENT_ID = "admin-cli"
BACKCHANNEL_DIR = Path("/tmp/ciba-test")

def test_ciba_flow():
    # 1. Initiate authentication
    response = requests.post(
        f"{KEYCLOAK_URL}/realms/{REALM}/ext/ciba/auth",
        data={
            "client_id": CLIENT_ID,
            "scope": "openid profile",
            "login_hint": "testuser@example.com"
        }
    )
    auth_data = response.json()
    auth_req_id = auth_data["auth_req_id"]

    # 2. Verify request file exists
    inbox_file = BACKCHANNEL_DIR / "inbox" / f"{auth_req_id}.json"
    assert inbox_file.exists()

    request_data = json.loads(inbox_file.read_text())
    assert request_data["loginHint"] == "testuser@example.com"

    # 3. Simulate user approval
    outbox_file = BACKCHANNEL_DIR / "outbox" / f"{auth_req_id}.json"
    outbox_file.write_text(json.dumps({
        "authReqId": auth_req_id,
        "status": "APPROVED",
        "userId": "test-user-id-123",
        "updatedAt": "2025-12-31T10:00:00Z"
    }))

    # 4. Poll for token
    time.sleep(1)
    token_response = requests.post(
        f"{KEYCLOAK_URL}/realms/{REALM}/ext/ciba/token",
        data={
            "client_id": CLIENT_ID,
            "auth_req_id": auth_req_id
        }
    )

    token_data = token_response.json()
    assert "access_token" in token_data
    assert token_data["token_type"] == "Bearer"
```

## Monitoring

### Log Messages

The provider logs the following events:

```
INFO: File backchannel initialized: inbox=/var/auth41/backchannel/inbox, outbox=/var/auth41/backchannel/outbox
INFO: CIBA request written to inbox: urn:uuid:abc123... (loginHint=user@example.com)
INFO: CIBA response found in outbox: urn:uuid:abc123... (status=APPROVED)
WARN: Failed to read response file: /var/auth41/backchannel/outbox/urn:uuid:abc123....json
ERROR: Failed to write CIBA request to inbox: urn:uuid:abc123...
```

### Monitoring Script

Monitor inbox/outbox activity:

```bash
#!/bin/bash
# monitor-ciba.sh

BACKCHANNEL_DIR="/var/auth41/backchannel"

watch -n 1 '
echo "=== Pending Requests (Inbox) ==="
ls -lh '"$BACKCHANNEL_DIR"'/inbox/ | tail -n +2

echo ""
echo "=== Processed Responses (Outbox) ==="
ls -lh '"$BACKCHANNEL_DIR"'/outbox/ | tail -n +2
'
```

## Troubleshooting

### Request files not appearing in inbox

**Cause**: Directory permissions or path misconfiguration.

**Solution**:
1. Check Keycloak logs for initialization messages
2. Verify `base-directory` path is correct
3. Ensure Keycloak process has write permissions to inbox directory
4. Check disk space

### Response files not being read from outbox

**Cause**: Incorrect file format or filename mismatch.

**Solution**:
1. Verify `authReqId` in response JSON matches filename (without .json extension)
2. Validate JSON format with `jq`
3. Check file permissions (must be readable by Keycloak)
4. Ensure `status` field is uppercase: "APPROVED", "DENIED", or "ERROR"

### JSON parsing errors

**Cause**: Invalid JSON format in response file.

**Solution**:
1. Validate JSON: `cat outbox/file.json | jq .`
2. Check for trailing commas
3. Ensure proper date format: `2025-12-31T10:00:00Z`
4. Verify required fields are present

### Responses expire before being processed

**Cause**: Slow processing or long delays in writing response files.

**Solution**:
1. Reduce `requestedExpiry` in auth request
2. Process requests faster
3. Monitor for file system performance issues

## Security Considerations

### File System Permissions

- Inbox/outbox directories should only be accessible to:
  - Keycloak process (read/write on inbox, read on outbox)
  - Test automation processes (read on inbox, write on outbox)
- Set appropriate permissions:
  ```bash
  chmod 700 /var/auth41/backchannel
  chmod 755 /var/auth41/backchannel/{inbox,outbox}
  ```

### Production Use

**This provider is NOT recommended for production** because:
- File system operations are slower than network-based backchannels
- No built-in encryption for sensitive authentication data
- Limited scalability across multiple Keycloak nodes
- Manual intervention required for each authentication

For production, use a push notification provider or other real-time backchannel implementation.

## Cleanup

Expired request/response files are automatically cleaned up by the CIBA plugin's cleanup task. Manual cleanup:

```bash
# Remove files older than 1 hour
find /var/auth41/backchannel/inbox -name "*.json" -mmin +60 -delete
find /var/auth41/backchannel/outbox -name "*.json" -mmin +60 -delete
```

## Related Documentation

- [CIBA Plugin](../auth41-ciba/README.md) - Main CIBA implementation
- [CIBA Documentation](../../docs/plugins/ciba.md) - Complete CIBA guide
- [Mock Backchannel](../auth41-backchannel-mock/README.md) - Alternative testing provider

## Support

- **Issues**: [GitHub Issues](https://github.com/apifocal/auth41-plugins/issues)
- **Documentation**: [Full Documentation](../../docs/README.md)
- **License**: Apache License 2.0

## Version History

### 1.0.0-alpha.2 (2025-12-31)
- Updated for OAuth2 token generation support
- Enhanced file format documentation
- Added integration testing examples

### 1.0.0-alpha.1 (2024-12-27)
- Initial release
- Inbox/outbox file-based backchannel
- JSON request/response format
- Poll mode support
