# Troubleshooting Guide

This guide helps diagnose and resolve common issues with Auth41 plugins.

## General Troubleshooting Steps

### 1. Check Keycloak Logs

Enable detailed logging:

```bash
# Edit standalone.xml or standalone-ha.xml
<logger category="org.apifocal.auth41">
    <level name="DEBUG"/>
</logger>
```

Or via environment variable:

```bash
export QUARKUS_LOG_CATEGORY_ORG_APIFOCAL_AUTH41_LEVEL=DEBUG
```

View logs:

```bash
tail -f $KEYCLOAK_HOME/data/log/keycloak.log
```

### 2. Verify Plugin Loading

Check logs for plugin loading messages:

```bash
grep "Loaded SPI" $KEYCLOAK_HOME/data/log/keycloak.log | grep auth41
```

Expected output:
```
INFO  [org.keycloak.provider] Loaded SPI trust-network (provider = auth41-trust-network)
INFO  [org.keycloak.provider] Loaded SPI topology (provider = auth41-topology)
INFO  [org.keycloak.provider] Loaded SPI discovery (provider = auth41-discovery)
INFO  [org.keycloak.provider] Loaded SPI accounts (provider = auth41-accounts)
INFO  [org.keycloak.provider] Loaded SPI authenticator (provider = auth41-federation-broker)
INFO  [org.keycloak.provider] Loaded SPI theme-selector (provider = auth41-theme-selector)
```

### 3. Validate Configuration

Verify trust network file syntax:

```bash
# Check JSON syntax
cat trust-network.json | jq .

# Validate required fields
jq '.network_id, .topology_type, .providers, .trust_relationships' trust-network.json
```

### 4. Test Network Connectivity

Verify Keycloak can reach other providers:

```bash
# Test OIDC discovery endpoint
curl https://provider.example.com/realms/main/.well-known/openid-configuration

# Test JWKS endpoint
curl https://provider.example.com/realms/main/protocol/openid-connect/certs
```

## Common Issues

### Plugins Not Loading

**Symptom**: No Auth41 entries in Keycloak logs, federation broker not available

**Possible Causes**:

1. **JARs not in providers directory**

   **Solution**:
   ```bash
   # Check JARs exist
   ls -l $KEYCLOAK_HOME/providers/auth41-*.jar

   # Copy if missing
   cp plugins/*/target/*.jar $KEYCLOAK_HOME/providers/
   ```

2. **Keycloak not rebuilt after adding JARs**

   **Solution**:
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```

3. **Incorrect file permissions**

   **Solution**:
   ```bash
   chmod 644 $KEYCLOAK_HOME/providers/auth41-*.jar
   chown keycloak:keycloak $KEYCLOAK_HOME/providers/auth41-*.jar
   ```

4. **Version incompatibility**

   **Solution**:
   - Verify Keycloak version is 23.x or later
   - Check plugin was built for correct Keycloak version
   - Rebuild plugins: `mvn clean install`

### Trust Network Not Loading

**Symptom**: Error in logs: "Failed to load trust network" or "Trust network file not found"

**Possible Causes**:

1. **File path not set**

   **Solution**:
   ```bash
   export AUTH41_TRUST_NETWORK_PATH=/path/to/trust-network.json
   $KEYCLOAK_HOME/bin/kc.sh start
   ```

2. **File not accessible**

   **Solution**:
   ```bash
   # Check file exists
   ls -l /path/to/trust-network.json

   # Fix permissions
   chmod 644 /path/to/trust-network.json
   ```

3. **Invalid JSON syntax**

   **Solution**:
   ```bash
   # Validate JSON
   cat trust-network.json | jq .

   # Common errors:
   # - Missing commas
   # - Trailing commas (not allowed in JSON)
   # - Unquoted keys or values
   # - Mismatched brackets
   ```

4. **Invalid configuration**

   **Solution**:
   Check logs for specific validation errors:
   ```bash
   grep "Trust network validation" $KEYCLOAK_HOME/data/log/keycloak.log
   ```

   Common validation errors:
   - Missing required fields: `provider_id`, `issuer`, `role`
   - Invalid topology_type
   - Trust relationship references non-existent provider
   - Duplicate provider_id

### Federation Broker Not Working

**Symptom**: Users not redirected to home provider, authentication fails

**Possible Causes**:

1. **Federation broker not in authentication flow**

   **Solution**:
   - Admin Console → Authentication → Flows
   - Edit browser flow
   - Add "Auth41 Federation Broker" execution
   - Set to REQUIRED or ALTERNATIVE
   - Bind flow to realm

2. **Discovery not finding provider**

   **Solution**:
   Enable debug logging and check:
   ```bash
   grep "Discovery" $KEYCLOAK_HOME/data/log/keycloak.log
   ```

   Verify:
   - Email domain configured in trust network
   - User email matches domain exactly
   - WebFinger endpoint responds (if enabled)

3. **No trust path between providers**

   **Solution**:
   Check topology logs:
   ```bash
   grep "Trust path" $KEYCLOAK_HOME/data/log/keycloak.log
   ```

   Verify:
   - Trust relationships defined in both directions
   - Trust level is EXPLICIT or TRANSITIVE (not NONE)
   - Providers exist in trust network

4. **Identity provider not configured**

   **Solution**:
   - Admin Console → Identity Providers
   - Create OIDC provider with alias matching provider_id
   - Configure client ID and secret
   - Set issuer to match trust network

### Discovery Issues

**Symptom**: Error: "Could not discover home provider for user"

**Possible Causes**:

1. **Email domain not configured**

   **Solution**:
   Add email domain to provider configuration:
   ```json
   {
     "provider_id": "acme-corp",
     "discovery": {
       "email_domains": ["acme.com", "acme.org"]
     }
   }
   ```

2. **WebFinger endpoint not responding**

   **Solution**:
   Test WebFinger manually:
   ```bash
   curl "https://acme.com/.well-known/webfinger?resource=acct:user@acme.com"
   ```

   If fails:
   - Disable WebFinger discovery
   - Use email domain discovery instead

3. **Discovery cache stale**

   **Solution**:
   ```bash
   # Clear discovery cache (requires restart)
   # Or reduce cache TTL
   export AUTH41_DISCOVERY_CACHE_TTL=60
   ```

### Shadow Account Issues

**Symptom**: Users can't login, duplicate accounts created, attributes not synced

**Possible Causes**:

1. **Account linking disabled**

   **Solution**:
   - Identity Provider → Settings → Account Linking
   - Set "First Login Flow" to enable linking
   - Enable "Trust Email" to auto-link by email

2. **Duplicate accounts**

   **Solution**:
   - Admin Console → Users
   - Find duplicate users
   - Delete shadow account
   - Ensure account linking enabled before next login

3. **Attributes not syncing**

   **Solution**:
   - Identity Provider → Mappers
   - Create attribute mappers for required claims
   - Verify claim names match ID token
   - Enable "Sync Mode Override" to force sync

4. **Username conflicts**

   **Solution**:
   Change username template:
   ```bash
   -Dauth41.accounts.username-template='${preferred_username}@${provider_id}'
   ```

### Theme Issues

**Symptom**: Auth41 themes not appearing, wrong theme displayed

**Possible Causes**:

1. **Themes not loaded**

   **Solution**:
   Check logs:
   ```bash
   grep "theme-selector" $KEYCLOAK_HOME/data/log/keycloak.log
   ```

   Verify auth41-themes JAR deployed and built

2. **Theme not selected in realm**

   **Solution**:
   - Admin Console → Realm Settings → Themes
   - Select auth41-corporate, auth41-modern, or auth41-minimal
   - Save and clear browser cache

3. **Theme cache**

   **Solution**:
   ```bash
   # Disable theme caching (development only)
   $KEYCLOAK_HOME/bin/kc.sh start-dev --spi-theme-static-max-age=-1

   # Clear browser cache
   # Hard refresh: Ctrl+Shift+R (Windows/Linux) or Cmd+Shift+R (Mac)
   ```

### OIDC Token Validation Failures

**Symptom**: Error: "Invalid token signature" or "Token issuer mismatch"

**Possible Causes**:

1. **Issuer mismatch**

   **Solution**:
   Verify issuer in trust network exactly matches token:
   ```bash
   # Decode ID token (use jwt.io or similar)
   # Check "iss" claim matches trust network issuer
   ```

   Common issues:
   - Trailing slash: `https://provider.com/realms/main/` vs `https://provider.com/realms/main`
   - HTTP vs HTTPS
   - Subdomain mismatch

2. **JWKS not accessible**

   **Solution**:
   Test JWKS endpoint:
   ```bash
   curl https://provider.example.com/realms/main/protocol/openid-connect/certs
   ```

   If fails:
   - Check network connectivity
   - Verify SSL certificates valid
   - Check firewall rules

3. **Clock skew**

   **Solution**:
   Synchronize server clocks:
   ```bash
   # Install and enable NTP
   sudo apt-get install ntp
   sudo systemctl enable ntp
   sudo systemctl start ntp
   ```

   Allow clock skew tolerance:
   ```bash
   -Dauth41.token.clock-skew=120  # 120 seconds
   ```

### Performance Issues

**Symptom**: Slow authentication, timeouts

**Possible Causes**:

1. **Discovery cache disabled**

   **Solution**:
   Enable and tune discovery cache:
   ```bash
   export AUTH41_DISCOVERY_CACHE_TTL=300  # 5 minutes
   ```

2. **Network latency to home provider**

   **Solution**:
   - Check network latency: `ping provider.example.com`
   - Consider geographic proximity
   - Increase timeouts:
     ```bash
     -Dauth41.http.connect-timeout=10000
     -Dauth41.http.read-timeout=30000
     ```

3. **Database performance**

   **Solution**:
   - Index federated_identity table
   - Optimize database queries
   - Scale database resources

4. **Too many providers in trust network**

   **Solution**:
   - Limit discovery to relevant providers
   - Use email domain hints to reduce search space
   - Consider network segmentation

## Debugging Techniques

### Enable Debug Logging for Specific Components

```bash
# Trust network
-Dquarkus.log.category."org.apifocal.auth41.trust".level=DEBUG

# Discovery
-Dquarkus.log.category."org.apifocal.auth41.discovery".level=DEBUG

# Topology
-Dquarkus.log.category."org.apifocal.auth41.topology".level=DEBUG

# Accounts
-Dquarkus.log.category."org.apifocal.auth41.accounts".level=DEBUG

# Federation broker
-Dquarkus.log.category."org.apifocal.auth41.broker".level=DEBUG
```

### Trace Authentication Flow

Add to authentication flow:

1. Admin Console → Authentication → Flows
2. Add "Script Authenticator" before Auth41 Federation Broker
3. Use script to log context:
   ```javascript
   AuthenticationFlowError = Java.type("org.keycloak.authentication.AuthenticationFlowError");

   function authenticate(context) {
       var username = context.getUser() ? context.getUser().getUsername() : "unknown";
       print("Auth41 Debug: User=" + username + ", Realm=" + context.getRealm().getName());
       context.success();
   }
   ```

### Inspect HTTP Traffic

Use proxy to inspect OIDC traffic:

```bash
# Set up mitmproxy
mitmproxy -p 8888

# Configure Keycloak to use proxy
export JAVA_OPTS="-Dhttp.proxyHost=localhost -Dhttp.proxyPort=8888 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=8888"
```

### Decode Tokens

Inspect ID tokens and access tokens:

```bash
# Use jwt.io or decode with jq
TOKEN="eyJhbGciOiJSUzI1NiIsIn..."
echo $TOKEN | cut -d. -f2 | base64 -d | jq .
```

## Error Messages Reference

### "Trust network not found"

**Cause**: Trust network configuration not loaded

**Solution**: Set `AUTH41_TRUST_NETWORK_PATH` and restart Keycloak

### "Provider not found in trust network: {provider_id}"

**Cause**: Referenced provider doesn't exist in trust network

**Solution**: Add provider to trust network configuration or fix provider_id reference

### "No trust path from {provider_a} to {provider_b}"

**Cause**: No trust relationship exists between providers

**Solution**: Add trust relationships to trust network configuration

### "Discovery failed for user: {email}"

**Cause**: Could not determine home provider from email

**Solution**: Add email domain to provider discovery configuration

### "Failed to create shadow account"

**Cause**: Error during federated account creation

**Solution**: Check logs for specific error, verify database connectivity, check username conflicts

### "Token validation failed: signature invalid"

**Cause**: Token signature doesn't match JWKS

**Solution**: Verify issuer matches, check JWKS endpoint accessible, verify clock synchronization

## Getting Help

If you encounter an issue not covered in this guide:

1. **Check Logs**: Enable DEBUG logging and collect relevant log entries
2. **Gather Information**:
   - Keycloak version
   - Auth41 plugin version
   - Trust network configuration (sanitized)
   - Error messages and stack traces
   - Steps to reproduce
3. **Search Issues**: Check [GitHub Issues](https://github.com/apifocal/auth41-plugins/issues)
4. **Report Bug**: Create new issue with collected information
5. **Ask Community**: Post in [GitHub Discussions](https://github.com/apifocal/auth41-plugins/discussions)

## Next Steps

- [Configuration Guide](configuration.md) - Review configuration settings
- [Architecture Documentation](architecture.md) - Understand how components work
- [Plugin Documentation](plugins/trust-network.md) - Learn about individual plugins
