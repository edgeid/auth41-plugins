# Auth41 Mock Backchannel Plugin

**⚠️ DO NOT USE IN PRODUCTION ⚠️**

This plugin provides a mock backchannel implementation for CIBA testing. It simulates authentication responses with configurable delays and outcomes.

## Purpose

The mock backchannel is designed for:
- **Development testing** - Test CIBA flows without real push infrastructure
- **Integration testing** - Automated tests with predictable behavior
- **Performance testing** - Test with adjustable delays
- **Failure scenario testing** - Simulate denials and errors

## How It Works

The mock backchannel:
1. Stores authentication requests in memory
2. Automatically responds after a configurable delay
3. Returns APPROVED, DENIED, or ERROR based on configured rates
4. Logs prominent warnings to prevent accidental production use

## Configuration

### Basic Configuration

```bash
# Use mock backchannel (TEST ONLY)
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only
```

### Advanced Configuration

```bash
# Full configuration with custom behavior
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=5000 \
  --spi-backchannel-mock-test-only-approval-rate=80 \
  --spi-backchannel-mock-test-only-error-rate=10 \
  --spi-backchannel-mock-test-only-auto-approve=true
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `delay` | 3000 | Delay in milliseconds before responding |
| `approvalRate` | 100 | Percentage of requests that are approved (0-100) |
| `errorRate` | 0 | Percentage of requests that fail with errors (0-100) |
| `autoApprove` | true | Whether to automatically process requests |

**Note**: `approvalRate + errorRate` must not exceed 100. Remaining percentage will be denied.

## Example Scenarios

### 1. Always Approve (Default)

```bash
# Approves all requests after 3 seconds
--spi-backchannel-provider=mock-test-only
```

### 2. Realistic Scenario (80% Approve, 10% Deny, 10% Error)

```bash
--spi-backchannel-provider=mock-test-only \
--spi-backchannel-mock-test-only-approval-rate=80 \
--spi-backchannel-mock-test-only-error-rate=10
```

### 3. Fast Testing (100ms delay)

```bash
--spi-backchannel-provider=mock-test-only \
--spi-backchannel-mock-test-only-delay=100
```

### 4. Manual Approval (autoApprove=false)

```bash
--spi-backchannel-provider=mock-test-only \
--spi-backchannel-mock-test-only-auto-approve=false
```

With `autoApprove=false`, requests stay PENDING indefinitely - useful for manual testing scenarios.

## Safety Features

The mock backchannel includes multiple safeguards:

1. **Prominent Provider ID**: `mock-test-only` makes it clear this is for testing
2. **Startup Warnings**: Large ASCII-art warnings in logs on startup
3. **Per-Request Warnings**: Every operation logs `[MOCK]` prefix
4. **Configuration Validation**: Invalid configs are rejected with errors

### Example Log Output

```
═══════════════════════════════════════════════════════════
  MOCK BACKCHANNEL PROVIDER FACTORY INITIALIZED
  Provider ID: mock-test-only
  DO NOT USE THIS PROVIDER IN PRODUCTION
  Configuration:
    - Delay: 3000ms
    - Approval Rate: 100%
    - Error Rate: 0%
    - Auto-Approve: true
═══════════════════════════════════════════════════════════
```

## Testing Example

```bash
# 1. Start Keycloak with mock backchannel
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-backchannel-provider=mock-test-only \
  --spi-backchannel-mock-test-only-delay=2000

# 2. Initiate CIBA request
curl -X POST http://localhost:8080/realms/test/ext/ciba/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=test-client" \
  -d "client_secret=secret" \
  -d "login_hint=testuser@example.com"

# Response:
# {
#   "auth_req_id": "urn:uuid:...",
#   "expires_in": 300,
#   "interval": 5
# }

# 3. Wait 2 seconds (configured delay)

# 4. Poll for status
curl -X POST http://localhost:8080/realms/test/protocol/openid-connect/token \
  -d "grant_type=urn:openid:params:grant-type:ciba" \
  -d "client_id=test-client" \
  -d "client_secret=secret" \
  -d "auth_req_id=urn:uuid:..."

# After 2 seconds, will return tokens (auto-approved)
```

## Comparison with Other Backchannels

| Feature | Mock | File-Based | Push (Future) |
|---------|------|------------|---------------|
| **Purpose** | Automated testing | Manual testing | Production |
| **Setup** | Zero setup | Create directories | Configure FCM/APNS |
| **Control** | Configuration | External scripts | User devices |
| **Speed** | Configurable | Manual | Real-time |
| **Production Use** | ❌ NEVER | ❌ NO | ✅ YES |

## When to Use Each Backchannel

- **Mock Backchannel**: Automated integration tests, CI/CD pipelines, development testing
- **File Backchannel**: Manual testing, debugging specific flows, external test harnesses
- **Push Backchannel**: Production deployments, real user authentication

## Limitations

- **In-memory only** - Requests lost on restart
- **Single-instance** - Not cluster-safe
- **No persistence** - Cannot survive failures
- **Deterministic outcomes** - Not true user behavior

## Production Alternative

For production, use:
- `auth41-backchannel-push` - Push notification implementation (planned)
- Custom implementation of `BackchannelProviderFactory`

## Related

- [CIBA Plugin Documentation](../../docs/plugins/ciba.md)
- [File Backchannel Plugin](../auth41-backchannel-file/README.md)
- [CIBA Specification](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
