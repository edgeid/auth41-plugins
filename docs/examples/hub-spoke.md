# Hub-and-Spoke Federation Example

This example demonstrates setting up a complete hub-and-spoke federation with Auth41, where multiple universities share authentication through a central consortium hub.

## Scenario

**University Consortium** with three members:

- **Consortium Hub** - Central authentication broker
- **University A** - Member institution
- **University B** - Member institution

**Use Case**: Students from University A or B can access shared resources (library, research portals, etc.) by authenticating at their home university.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│         Shared Resource (Library Portal)            │
│         client_id from Consortium Hub               │
└────────────────┬────────────────────────────────────┘
                 │ OIDC Auth Request
                 ▼
┌─────────────────────────────────────────────────────┐
│              Consortium Hub                         │
│  • Trust network configuration                      │
│  • Auth41 plugins installed                         │
│  • Federation broker in auth flow                   │
│  • Shadow accounts for all users                    │
└──────────┬──────────────────────┬───────────────────┘
           │                      │
           │ Trusts               │ Trusts
           ▼                      ▼
┌────────────────────┐  ┌────────────────────┐
│  University A      │  │  University B      │
│  • Students        │  │  • Students        │
│  • Faculty         │  │  • Faculty         │
│  • OIDC Provider   │  │  • OIDC Provider   │
└────────────────────┘  └────────────────────┘
```

## Prerequisites

- 3 Keycloak instances (or realms) representing Hub, Univ A, Univ B
- Auth41 plugins built: `mvn clean install`
- Network connectivity between instances

## Step 1: Deploy Auth41 Plugins

On the **Consortium Hub** Keycloak instance:

```bash
# Copy all plugin JARs
cp plugins/*/target/*.jar $HUB_KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$HUB_KEYCLOAK_HOME/bin/kc.sh build

# Start Keycloak
$HUB_KEYCLOAK_HOME/bin/kc.sh start --hostname=hub.consortium.edu
```

Universities A and B can use standard Keycloak (no Auth41 plugins needed).

## Step 2: Configure Trust Network

Create `/opt/keycloak/data/trust-network.json` on Hub:

```json
{
  "network_id": "university-consortium",
  "topology_type": "hub-and-spoke",
  "providers": {
    "consortium-hub": {
      "provider_id": "consortium-hub",
      "issuer": "https://hub.consortium.edu/realms/federation",
      "role": "hub",
      "discovery": {
        "email_domains": ["consortium.edu"]
      },
      "metadata": {
        "name": "University Consortium Hub",
        "contact": "admin@consortium.edu"
      }
    },
    "university-a": {
      "provider_id": "university-a",
      "issuer": "https://sso.university-a.edu/realms/students",
      "role": "spoke",
      "discovery": {
        "email_domains": ["university-a.edu", "univ-a.edu"],
        "webfinger_enabled": false
      },
      "metadata": {
        "name": "University A",
        "contact": "it@university-a.edu"
      }
    },
    "university-b": {
      "provider_id": "university-b",
      "issuer": "https://idp.university-b.edu/realms/main",
      "role": "spoke",
      "discovery": {
        "email_domains": ["university-b.edu"],
        "webfinger_enabled": false
      },
      "metadata": {
        "name": "University B",
        "contact": "support@university-b.edu"
      }
    }
  },
  "trust_relationships": [
    {
      "from": "consortium-hub",
      "to": "university-a",
      "trust_level": "EXPLICIT",
      "metadata": {
        "established": "2025-01-01",
        "agreement": "Consortium MOU 2025"
      }
    },
    {
      "from": "university-a",
      "to": "consortium-hub",
      "trust_level": "EXPLICIT"
    },
    {
      "from": "consortium-hub",
      "to": "university-b",
      "trust_level": "EXPLICIT",
      "metadata": {
        "established": "2025-01-01",
        "agreement": "Consortium MOU 2025"
      }
    },
    {
      "from": "university-b",
      "to": "consortium-hub",
      "trust_level": "EXPLICIT"
    }
  ],
  "metadata": {
    "version": "1.0",
    "description": "University Consortium Federation Network",
    "updated": "2025-01-15"
  }
}
```

Set environment variable:

```bash
export AUTH41_TRUST_NETWORK_PATH=/opt/keycloak/data/trust-network.json
```

## Step 3: Configure Hub Identity Providers

On **Consortium Hub**, create OIDC identity providers for each university:

### University A Identity Provider

1. **Admin Console** → **Identity Providers** → **Add provider** → **OpenID Connect v1.0**

2. **Basic Settings**:
   - Alias: `university-a`
   - Display name: `University A`
   - Enabled: ON

3. **OpenID Connect Settings**:
   - Authorization URL: `https://sso.university-a.edu/realms/students/protocol/openid-connect/auth`
   - Token URL: `https://sso.university-a.edu/realms/students/protocol/openid-connect/token`
   - Logout URL: `https://sso.university-a.edu/realms/students/protocol/openid-connect/logout`
   - Client ID: `consortium-hub`
   - Client Secret: `{secret-from-university-a}`
   - Issuer: `https://sso.university-a.edu/realms/students`

4. **Advanced Settings**:
   - Validate Signatures: ON
   - Use JWKS URL: ON
   - JWKS URL: `https://sso.university-a.edu/realms/students/protocol/openid-connect/certs`
   - Default Scopes: `openid profile email`

5. **Mappers** (create for each):
   - Email: `email` → `email`
   - First Name: `given_name` → `firstName`
   - Last Name: `family_name` → `lastName`
   - Username: `preferred_username` → `username`

6. Click **Save**

### University B Identity Provider

Repeat above for University B with URLs:
- Authorization: `https://idp.university-b.edu/realms/main/protocol/openid-connect/auth`
- Token: `https://idp.university-b.edu/realms/main/protocol/openid-connect/token`
- Etc.

## Step 4: Register Hub at Universities

On **University A** Keycloak:

1. **Admin Console** → **Clients** → **Create client**

2. **General Settings**:
   - Client type: OpenID Connect
   - Client ID: `consortium-hub`

3. **Capability config**:
   - Client authentication: ON
   - Authorization: OFF
   - Standard flow: ON
   - Direct access grants: OFF

4. **Login settings**:
   - Valid redirect URIs: `https://hub.consortium.edu/realms/federation/broker/university-a/endpoint*`
   - Valid post logout redirect URIs: `https://hub.consortium.edu/*`
   - Web origins: `https://hub.consortium.edu`

5. **Credentials** tab:
   - Copy Client Secret
   - Provide to Hub admin

Repeat for **University B** with:
- Client ID: `consortium-hub`
- Redirect URI: `https://hub.consortium.edu/realms/federation/broker/university-b/endpoint*`

## Step 5: Configure Authentication Flow

On **Consortium Hub**:

1. **Admin Console** → **Authentication** → **Flows**

2. Click **Duplicate** on "Browser" flow
   - Name: `Federation Browser`

3. Click **Add execution** to the flow
   - Provider: `Auth41 Federation Broker`
   - Click **Add**

4. Set requirement to **ALTERNATIVE** (allows local admin login as fallback)

5. **Authentication** → **Bindings**
   - Browser Flow: `Federation Browser`
   - Click **Save**

## Step 6: Configure Client Application

Register the Library Portal at **Consortium Hub**:

1. **Clients** → **Create client**

2. **General Settings**:
   - Client ID: `library-portal`
   - Name: `Consortium Library Portal`

3. **Capability config**:
   - Client authentication: ON
   - Standard flow: ON

4. **Login settings**:
   - Valid redirect URIs: `https://library.consortium.edu/*`
   - Web origins: `https://library.consortium.edu`

5. **Client Scopes**:
   - Assigned: openid, profile, email

## Step 7: Test Federation

### Test User Journey: University A Student

1. **Create test user at University A**:
   - Username: `alice`
   - Email: `alice@university-a.edu`
   - Password: `password`
   - Email verified: ON

2. **Access Library Portal**:
   ```
   https://library.consortium.edu/
   ```

3. **Redirected to Consortium Hub login**:
   ```
   https://hub.consortium.edu/realms/federation/protocol/openid-connect/auth
   ```

4. **Enter email**: `alice@university-a.edu`
   - Federation Broker discovers home provider: University A
   - Validates trust path: Hub → University A (EXPLICIT ✓)

5. **Redirected to University A login**:
   ```
   https://sso.university-a.edu/realms/students/protocol/openid-connect/auth
   ```

6. **Authenticate at University A**:
   - Enter credentials
   - MFA if enabled
   - Consent to share attributes

7. **Redirected back to Hub**:
   - Hub receives ID token
   - Validates token signature
   - Creates shadow account for alice@university-a.edu
   - Links federated identity

8. **Redirected to Library Portal**:
   ```
   https://library.consortium.edu/
   ```
   - User logged in!

9. **Verify shadow account at Hub**:
   - Admin Console → Users
   - Search: `alice@university-a.edu`
   - Check Federated Identities tab
   - Linked to: university-a, user ID: {sub from token}

### Test User Journey: University B Student

Repeat with:
- User: `bob@university-b.edu` at University B
- Should redirect to University B for authentication
- Shadow account created at Hub

## Verification

### Check Trust Network Loaded

```bash
grep "Auth41" $HUB_KEYCLOAK_HOME/data/log/keycloak.log
```

Expected:
```
INFO  [org.apifocal.auth41.trust] Loaded trust network: university-consortium
INFO  [org.apifocal.auth41.trust] Registered 3 providers
INFO  [org.apifocal.auth41.trust] Registered 4 trust relationships
```

### Check Discovery

```bash
# Test email domain discovery
curl -v "http://localhost:8080/realms/federation/auth41/discover?email=alice@university-a.edu"
```

Expected response:
```json
{
  "provider_id": "university-a",
  "issuer": "https://sso.university-a.edu/realms/students"
}
```

### Check Shadow Accounts

On Hub, verify shadow accounts exist for federated users:

```bash
# Admin Console → Users
# Filter by "Federated Users" or search by email
```

## Troubleshooting

### User Not Redirected to Home Provider

**Check**:
1. Discovery finding provider: Check email domain in trust network
2. Trust path valid: Check trust relationships configured
3. Identity provider created: Alias must match provider_id
4. Federation broker in auth flow

### Token Validation Fails

**Check**:
1. Issuer matches exactly: `https://sso.university-a.edu/realms/students`
2. JWKS URL accessible from Hub
3. Client ID and secret correct
4. Clock synchronization (NTP)

### Shadow Account Not Created

**Check**:
1. Auth41-accounts plugin installed
2. Email claim present in ID token
3. Attribute mappers configured
4. Check Keycloak logs for errors

## Advanced Configuration

### Multiple Hubs

For larger federations, use multiple hubs:

```json
{
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub-us": {"role": "hub", ...},
    "hub-eu": {"role": "hub", ...},
    "university-a": {"role": "spoke", ...}
  },
  "trust_relationships": [
    {"from": "hub-us", "to": "hub-eu", "trust_level": "EXPLICIT"},
    {"from": "hub-eu", "to": "hub-us", "trust_level": "EXPLICIT"},
    {"from": "hub-us", "to": "university-a", "trust_level": "EXPLICIT"}
  ]
}
```

### Custom Themes

Apply consortium branding:

```bash
# On Hub
-Dspi-theme-selector-auth41-theme-selector-default-theme=auth41-classic
```

Or per client:
```json
{
  "auth41.theme.client.library-portal": "auth41-classic"
}
```

### Account Linking

Allow users to link existing Hub accounts:

1. **Identity Provider** → university-a → **Account Linking**
2. First Login Flow: `First broker login`
3. Enable: **Trust Email**

## Next Steps

- [Mesh Topology Example](mesh.md) - Peer-to-peer trust network
- [Configuration Guide](../configuration.md) - Advanced configuration
- [Troubleshooting](../troubleshooting.md) - Common issues
- [Architecture](../architecture.md) - How it works
