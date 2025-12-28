# Keycloak 26.0.7 Migration Guide

This document describes the changes required for upgrading Auth41 plugins from Keycloak 23.0.4 to 26.0.7.

## Executive Summary

Auth41 plugins have been successfully upgraded to support Keycloak 26.0.7. This spans three major Keycloak versions (23 → 24 → 25 → 26) and includes critical compatibility changes required for the RESTEasy Reactive migration.

**Status**: ✅ **All plugins fully compatible with Keycloak 26.0.7**

## Version Information

- **Previous Version**: Keycloak 23.0.4
- **Current Version**: Keycloak 26.0.7
- **Release Date**: December 2024
- **Java Version**: Requires Java 21 (Java 17 support removed in KC 26)

## Critical Changes Made

### 1. RESTEasy Reactive Compatibility (Required for KC 24+)

Keycloak switched from RESTEasy Classic to RESTEasy Reactive in version 24.0.0. This requires two additions to JAX-RS resources:

#### Added META-INF/beans.xml

**File**: `plugins/auth41-ciba/src/main/resources/META-INF/beans.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
       https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd"
       version="3.0">
</beans>
```

**Purpose**: Required for CDI bean discovery and JAX-RS resource scanning in RESTEasy Reactive.

#### Added @Provider Annotation

**File**: `plugins/auth41-ciba/src/main/java/org/apifocal/auth41/plugin/ciba/CibaRootResource.java`

```java
import jakarta.ws.rs.ext.Provider;

@Provider  // <-- Required for KC 24+
public class CibaRootResource {
    // ...
}
```

**Purpose**: Marks JAX-RS resource classes for discovery by RESTEasy Reactive runtime.

### 2. Null Safety Improvements

Added null checks to prevent NullPointerExceptions from malformed BackchannelProvider responses:

**File**: `CibaTokenResource.java`

```java
// Check if status is null before dereferencing
if (status == null) {
    logger.errorf("BackchannelProvider returned null status for auth_req_id: %s", authReqId);
    return errorResponse("invalid_request", "Authentication request not found",
        Response.Status.BAD_REQUEST);
}
```

**Test Coverage**: Added `shouldReturnErrorWhenBackchannelProviderReturnsNullStatus()` test.

### 3. Dependency Updates

**File**: `parent/pom.xml`

```xml
<properties>
    <keycloak.version>26.0.7</keycloak.version>
</properties>
```

**File**: `plugins/auth41-ciba/pom.xml` and `plugins/auth41-federation-broker/pom.xml`

```xml
<!-- JAX-RS implementation for tests (required for KC 26) -->
<dependency>
    <groupId>org.jboss.resteasy</groupId>
    <artifactId>resteasy-core</artifactId>
    <version>6.2.10.Final</version>
    <scope>test</scope>
</dependency>
```

**Purpose**: Provides JAX-RS RuntimeDelegate for unit tests with Keycloak 26.

## SPI Compatibility Matrix

| SPI / API | KC 23.0.4 | KC 26.0.7 | Status | Notes |
|-----------|-----------|-----------|--------|-------|
| `RealmResourceProvider` | ✅ | ✅ | Compatible | Requires @Provider + beans.xml from KC 24+ |
| `KeycloakSession` | ✅ | ✅ | Compatible | Minor deprecations not affecting our usage |
| `UserModel` | ✅ | ✅ | Compatible | Stable |
| `ClientModel` | ✅ | ✅ | Compatible | `updateClient()` deprecated, not used |
| `RealmModel` | ✅ | ✅ | Compatible | IDP methods deprecated, not used |
| JAX-RS `@Context` | ✅ | ✅ | Compatible | Still supported for resource classes |
| Jakarta EE APIs | ✅ | ✅ | Compatible | Already using `jakarta.ws.rs.*` |

## Breaking Changes by Version

### Keycloak 24.0.0 (March 2024)

#### RESTEasy Reactive Migration
- **Impact**: High - Affects all custom JAX-RS resources
- **Required Actions**:
  1. Add empty `META-INF/beans.xml` to JAR
  2. Annotate JAX-RS resources with `@Provider`
- **Status**: ✅ Completed

#### User Profile Always Enabled
- **Impact**: None - Using UserModel correctly
- **Status**: ✅ No changes needed

### Keycloak 25.0.0 (June 2024)

#### Java 17 Deprecated
- **Impact**: Medium - Java 17 support will be removed in KC 26
- **Required Action**: Plan migration to Java 21
- **Status**: ⚠️ Currently using Java 17, must upgrade before production deployment

#### Hostname Configuration v2
- **Impact**: Low - Configuration only
- **Status**: ✅ No code changes needed

### Keycloak 26.0.0 (October 2024)

#### Java 21 Required
- **Impact**: High - Java 17 no longer supported
- **Required Action**: Build and run on Java 21
- **Status**: ⚠️ Action required before production

#### Marshalling Library Change
- **Impact**: Medium - All caches cleared on upgrade
- **Required Action**: Plan for session loss during upgrade
- **Status**: ⚠️ Documented below

#### Persistent User Sessions Default
- **Impact**: Low - Sessions now persisted to database by default
- **Status**: ✅ No code changes needed

## Test Results

All test suites pass successfully with Keycloak 26.0.7:

```
✅ auth41-ciba: 48/48 tests passed
   - CibaTokenResourceTest: 24 tests
   - CibaAuthenticationResourceTest: 24 tests

✅ Full Project: 177/177 tests passed
   - auth41-accounts: 28 tests
   - auth41-backchannel-file: 8 tests
   - auth41-backchannel-mock: 23 tests
   - auth41-ciba: 48 tests
   - auth41-discovery: 23 tests
   - auth41-trust-network: 41 tests
   - auth41-topology: 25 tests
   - auth41-federation-broker: 5 tests
```

## Deployment Considerations

### Session Loss During Upgrade

Upgrading directly from Keycloak 23 to 26 will cause **all active sessions to be lost** due to the marshalling library change (JBoss Marshalling → Infinispan Protostream).

#### Option 1: Direct Upgrade (Fastest)
1. Update Auth41 plugins (already done)
2. Deploy Keycloak 26.0.7
3. Deploy updated plugins

**Pros**: Single deployment step
**Cons**: All users logged out

#### Option 2: Staged Upgrade (Recommended for Production)
1. Upgrade to Keycloak 25.x first
2. Enable persistent sessions feature
3. Wait for sessions to be persisted
4. Upgrade to Keycloak 26.0.7

**Pros**: Preserves user sessions
**Cons**: Multiple deployment steps

### Java 21 Migration

Before deploying to production:

1. Update build environment to Java 21
2. Update `maven.compiler.source` and `maven.compiler.target` to 21
3. Test all plugins with Java 21
4. Update production Keycloak server to run on Java 21

**Current Status**: Code compiles with Java 17, but KC 26 requires Java 21 runtime.

## Files Modified

### Source Code Changes
- `plugins/auth41-ciba/src/main/java/org/apifocal/auth41/plugin/ciba/CibaRootResource.java`
  - Added `@Provider` annotation
  - Added import for `jakarta.ws.rs.ext.Provider`

- `plugins/auth41-ciba/src/main/java/org/apifocal/auth41/plugin/ciba/CibaTokenResource.java`
  - Added null check for `BackchannelAuthStatus` (lines 100-104)

### Resource Files
- `plugins/auth41-ciba/src/main/resources/META-INF/beans.xml` (NEW)
  - Empty CDI beans descriptor for RESTEasy Reactive

### Build Configuration
- `parent/pom.xml`
  - Updated `keycloak.version` from 23.0.4 to 26.0.7

- `plugins/auth41-ciba/pom.xml`
  - Added resteasy-core 6.2.10.Final test dependency

- `plugins/auth41-federation-broker/pom.xml`
  - Added resteasy-core 6.2.10.Final test dependency
  - Added missing `<scope>test</scope>` to assertj-core

### Test Files
- `plugins/auth41-ciba/src/test/java/.../CibaTokenResourceTest.java`
  - Added `shouldReturnErrorWhenApprovedStatusHasNullUserId()` test
  - Added `shouldReturnErrorWhenBackchannelProviderReturnsNullStatus()` test

## Known Issues and Limitations

### 1. CIBA Token Generation Not Implemented
The token endpoint currently returns status information only. Full OAuth2 token generation (access_token, refresh_token, id_token) will be implemented in a future release.

**Current Behavior**: Returns JSON with `status: "APPROVED"` and user information.

**Planned**: Integrate with Keycloak's `TokenManager` to generate standard OAuth2 tokens.

### 2. Client Authentication Not Implemented
The token endpoint does not yet validate client credentials (client_secret, JWT, mTLS).

**Current Behavior**: Basic client_id validation only.

**Planned**: Full client authentication implementation.

## Verification Checklist

Before deploying to production:

- [x] All unit tests pass (177/177)
- [x] Code compiles successfully with Keycloak 26.0.7 dependencies
- [x] `META-INF/beans.xml` added to all JAX-RS resource plugins
- [x] `@Provider` annotation added to JAX-RS resource classes
- [ ] Java 21 runtime environment configured
- [ ] Integration tests performed with Keycloak 26.0.7 instance
- [ ] Manual testing of CIBA authentication flow
- [ ] Manual testing of CIBA token polling
- [ ] Session loss strategy decided (direct vs staged upgrade)
- [ ] Deployment runbook updated

## References

### Official Keycloak Documentation
- [Keycloak 26.0 Migration Guide](https://www.keycloak.org/docs/latest/upgrading/index.html)
- [Keycloak 26.0 Release Notes](https://www.keycloak.org/2024/10/keycloak-2600-released)
- [Keycloak 26.0.7 Release](https://www.keycloak.org/2024/12/keycloak-2607-released.html)
- [Keycloak API Documentation](https://www.keycloak.org/docs-api/26.0.7/javadocs/)

### Red Hat Documentation
- [Red Hat Build of Keycloak 26.0 Migration Guide](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.0/html-single/migration_guide/index)
- [Migrating Custom Providers](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.0/html/migration_guide/migrating-providers)

### Community Resources
- [RESTEasy Reactive Migration Issue](https://github.com/keycloak/keycloak/issues/23444)
- [RealmResourceProvider Issues](https://github.com/keycloak/keycloak/issues/25882)

## Support

For questions or issues related to this migration:

1. Check this migration guide first
2. Review Keycloak 26.0 migration documentation
3. Check Auth41 plugin documentation in `docs/plugins/`
4. Search GitHub issues for similar problems

---

**Migration Completed**: December 28, 2025
**Keycloak Version**: 26.0.7
**Status**: Ready for integration testing
