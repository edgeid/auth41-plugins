# Changelog Draft - Version 1.0.0-SNAPSHOT

## New Features

### Per-Realm CIBA Configuration

Added support for configuring different backchannel providers per realm using realm attributes. This enables:
- Development realms to use `mock-test-only` for fast automated testing
- Integration/staging realms to use `file-test-only` for manual testing
- Production realms to use different providers (e.g., future `push-notifications`)

**Configuration**:
- Realm attribute key: `ciba.backchannel.provider`
- Supported values: `mock-test-only`, `file-test-only`, or any custom provider ID
- Default: `mock-test-only` (if not configured)

**Implementation**: `CibaAuthenticationResource.getBackchannelProvider()` reads realm attributes and falls back to sensible defaults.

See: [CIBA Plugin Documentation](plugins/ciba.md#per-realm-backchannel-provider-selection)

## Breaking Changes

### Provider ID Renaming

Renamed backchannel provider IDs for clarity and consistency:

| Old ID | New ID | Reason |
|--------|--------|--------|
| `file` | `file-test-only` | Clarifies this is for testing only, not production |
| `mock-test-only` | `mock-test-only` | No change (already clear) |

**Migration Guide**:

If you're using the file-based backchannel provider, update your configuration:

**Before**:
```bash
--spi-backchannel-provider=file
```

**After**:
```bash
--spi-backchannel-provider=file-test-only
```

**Note**: The old `file` provider ID will no longer work. Update all scripts, documentation, and configurations before upgrading.

## Bug Fixes

### BackchannelProvider SPI Registration

Fixed issue where custom SPIs were not properly registered with Keycloak's provider framework.

**Problem**: `session.getProvider(BackchannelProvider.class)` was returning `null` even when provider JARs were loaded.

**Solution**:
1. Created `BackchannelSpi.java` implementing Keycloak's `Spi` interface
2. Registered SPI via `META-INF/services/org.keycloak.provider.Spi`

**Impact**: CIBA plugin now correctly discovers and uses backchannel providers.

See: [auth41-ciba-spi/src/main/resources/META-INF/services/](../lib/auth41-ciba-spi/src/main/resources/META-INF/services/)

## Documentation Updates

### CIBA Configuration Documentation

Updated documentation to cover:
1. **Per-realm configuration** in `docs/plugins/ciba.md`:
   - Admin Console instructions
   - Realm import JSON examples
   - Multi-environment setup examples
   - Logging behavior

2. **General configuration** in `docs/configuration.md`:
   - Added CIBA configuration section under Realm Configuration
   - Provider comparison table
   - Default behavior explanation

3. **Provider ID updates** across all documentation:
   - Updated `ciba.md` to use `file-test-only`
   - Updated `ciba-quickstart.md` examples
   - Added version notes about naming changes

## Integration Tests

### Federation Broker Tests

Added comprehensive integration test suite (`05-federation-broker.test.ts`) covering:
- Plugin loading verification (6 tests)
- Trust network configuration (2 tests)
- Federation components and architecture (8 tests)
- Documentation of current limitations

**Test Results**: 38/38 integration tests passing

**Coverage**:
- Server startup and health
- OIDC endpoint verification
- Plugin verification
- CIBA authentication flow
- Federation broker integration

See: [tests/integration/05-federation-broker.test.ts](../../auth41/tests/integration/)

## Configuration Files

### Trust Network Configuration

Updated `trust-network.json` with complete OIDC metadata for all providers:
- `authorization_endpoint`
- `token_endpoint`
- `jwks_uri`
- `userinfo_endpoint`
- `backchannel_authentication_endpoint`

**Note**: Current `TrustNetworkConfigLoader` implementation does not yet parse metadata/attributes from JSON. These fields are documented for future implementation.

### Test Realm Configuration

Updated `test-realm-import.json` with:
- CIBA backchannel provider configuration
- Example realm attribute: `"ciba.backchannel.provider": "mock-test-only"`

## Docker Images

### Development Image Updates

Updated `Dockerfile.dev` to:
- Properly copy realm import file to `/opt/keycloak/data/import/`
- Set correct file ownership for import directory
- Support `--import-realm` flag

**Realm Import**: Test realm now automatically imports on container startup with proper CIBA configuration.

## Known Limitations

### Trust Network JSON Parsing

The `TrustNetworkConfigLoader` currently only parses basic provider fields:
- ✅ `provider_id`
- ✅ `issuer`
- ✅ `role`
- ❌ `metadata.*` (planned)
- ❌ `attributes.*` (planned)

**Workaround**: Metadata and attributes can be configured programmatically via ProviderNode builders.

**Planned**: Enhanced JSON parser to support full provider metadata and custom attributes.

### Federation Flow Testing

Full end-to-end federation testing requires:
- Mock OAuth2/OIDC provider endpoints
- Browser-like redirect handling
- Authentication flow configuration via Admin API

**Current State**: Integration tests verify plugin loading and configuration. Detailed federation flow testing relies on unit tests in auth41-plugins project.

**Planned**: Testcontainers-based E2E test suite for complete federation flows.

## Contributors

- Per-realm CIBA configuration implementation
- Provider ID renaming for clarity
- BackchannelProvider SPI registration fix
- Comprehensive documentation updates
- Integration test suite expansion
- Docker image improvements

## Upgrade Notes

### For Developers

1. **Update provider references**: Change `file` to `file-test-only` in all configurations
2. **Rebuild auth41-plugins**: `mvn clean install` to get latest changes
3. **Update Docker images**: Rebuild with new Dockerfile.dev for realm import support
4. **Review CIBA docs**: Check new per-realm configuration options

### For Deployers

1. **Update Keycloak startup scripts**: Replace `--spi-backchannel-provider=file` with `--spi-backchannel-provider=file-test-only`
2. **Configure realms**: Add `ciba.backchannel.provider` realm attribute for per-realm control
3. **Test realm imports**: Verify realm import files include CIBA configuration if needed
4. **Check logs**: Look for "Using CIBA backchannel provider for realm X: Y" debug messages

## Next Steps

Potential areas for future releases:

1. **Trust Network Enhancements**:
   - Parse metadata and attributes from trust-network.json
   - Support dynamic provider discovery
   - Implement OIDC Discovery integration

2. **CIBA Token Integration**:
   - Complete OAuth2 token generation in CIBA token endpoint
   - Integrate with Keycloak TokenManager
   - Support all CIBA delivery modes (poll, ping, push)

3. **Federation Flow Testing**:
   - Testcontainers-based E2E tests
   - Mock OIDC provider implementation
   - Complete authentication flow verification

4. **Production Backchannel Provider**:
   - Implement push notification provider
   - Support multiple notification channels
   - Production-ready deployment guide
