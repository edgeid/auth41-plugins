# Configuration Guide

This guide covers how to configure Auth41 plugins for federated authentication in Keycloak.

## Configuration Overview

Auth41 configuration happens at three levels:

1. **System Configuration** - Environment variables and system properties
2. **Trust Network Configuration** - JSON file defining providers and trust relationships
3. **Realm Configuration** - Keycloak Admin Console settings per realm

## System Configuration

### Environment Variables

Set these before starting Keycloak:

```bash
# Trust network configuration file path (required)
export AUTH41_TRUST_NETWORK_PATH=/opt/keycloak/data/trust-network.json

# Enable debug logging (optional)
export AUTH41_LOG_LEVEL=DEBUG

# Discovery cache TTL in seconds (optional, default: 300)
export AUTH41_DISCOVERY_CACHE_TTL=300
```

### System Properties

Alternatively, set as Java system properties:

```bash
$KEYCLOAK_HOME/bin/kc.sh start \
  -Dauth41.trust.network.path=/opt/keycloak/data/trust-network.json \
  -Dauth41.log.level=DEBUG \
  -Dauth41.discovery.cache.ttl=300
```

### Docker Configuration

For Docker deployments:

```yaml
# docker-compose.yml
services:
  keycloak:
    image: keycloak-auth41:latest
    environment:
      AUTH41_TRUST_NETWORK_PATH: /opt/keycloak/data/trust-network.json
      AUTH41_LOG_LEVEL: INFO
    volumes:
      - ./trust-network.json:/opt/keycloak/data/trust-network.json
```

## Trust Network Configuration

### Configuration File Structure

Create `trust-network.json`:

```json
{
  "network_id": "my-federation",
  "topology_type": "hub-and-spoke",
  "providers": {
    "provider-id": {
      "provider_id": "provider-id",
      "issuer": "https://provider.example.com/realms/main",
      "role": "hub|spoke|peer",
      "discovery": {
        "email_domains": ["example.com"],
        "webfinger_enabled": true
      },
      "metadata": {
        "name": "Provider Name",
        "description": "Provider description"
      }
    }
  },
  "trust_relationships": [
    {
      "from": "provider-a",
      "to": "provider-b",
      "trust_level": "EXPLICIT",
      "metadata": {
        "established": "2025-01-01",
        "contact": "admin@example.com"
      }
    }
  ],
  "metadata": {
    "version": "1.0",
    "updated": "2025-01-15"
  }
}
```

### Provider Configuration

Each provider requires:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `provider_id` | string | Yes | Unique identifier for the provider |
| `issuer` | string | Yes | OIDC issuer URL (must match tokens) |
| `role` | string | Yes | Provider role: `hub`, `spoke`, or `peer` |
| `discovery` | object | No | Discovery configuration |
| `discovery.email_domains` | array | No | Email domains for this provider |
| `discovery.webfinger_enabled` | boolean | No | Enable WebFinger discovery |
| `metadata` | object | No | Custom metadata |

**Example**:

```json
{
  "provider_id": "acme-corp",
  "issuer": "https://auth.acme.com/realms/employees",
  "role": "spoke",
  "discovery": {
    "email_domains": ["acme.com", "acme.org"],
    "webfinger_enabled": true
  },
  "metadata": {
    "name": "ACME Corporation",
    "contact_email": "admin@acme.com"
  }
}
```

### Trust Relationship Configuration

Each trust relationship requires:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | string | Yes | Source provider ID |
| `to` | string | Yes | Target provider ID |
| `trust_level` | string | Yes | Trust level: `EXPLICIT`, `TRANSITIVE`, or `NONE` |
| `metadata` | object | No | Custom metadata |

**Trust Levels**:

- **EXPLICIT**: Direct trust relationship, users can authenticate
- **TRANSITIVE**: Indirect trust via intermediary (hub), requires topology support
- **NONE**: No trust, authentication blocked

**Example**:

```json
{
  "from": "hub",
  "to": "acme-corp",
  "trust_level": "EXPLICIT",
  "metadata": {
    "established": "2025-01-01",
    "expires": "2026-01-01"
  }
}
```

### Topology Types

#### Hub-and-Spoke

All spokes connect through a central hub:

```json
{
  "topology_type": "hub-and-spoke",
  "providers": {
    "hub": {
      "provider_id": "hub",
      "issuer": "https://hub.example.com/realms/main",
      "role": "hub"
    },
    "spoke-a": {
      "provider_id": "spoke-a",
      "issuer": "https://spoke-a.example.com/realms/main",
      "role": "spoke"
    },
    "spoke-b": {
      "provider_id": "spoke-b",
      "issuer": "https://spoke-b.example.com/realms/main",
      "role": "spoke"
    }
  },
  "trust_relationships": [
    {"from": "hub", "to": "spoke-a", "trust_level": "EXPLICIT"},
    {"from": "spoke-a", "to": "hub", "trust_level": "EXPLICIT"},
    {"from": "hub", "to": "spoke-b", "trust_level": "EXPLICIT"},
    {"from": "spoke-b", "to": "hub", "trust_level": "EXPLICIT"}
  ]
}
```

Users from Spoke A can access services at Spoke B through the hub (transitive trust).

#### Mesh

Direct peer-to-peer trust relationships:

```json
{
  "topology_type": "mesh",
  "providers": {
    "org-a": {
      "provider_id": "org-a",
      "issuer": "https://org-a.example.com/realms/main",
      "role": "peer"
    },
    "org-b": {
      "provider_id": "org-b",
      "issuer": "https://org-b.example.com/realms/main",
      "role": "peer"
    },
    "org-c": {
      "provider_id": "org-c",
      "issuer": "https://org-c.example.com/realms/main",
      "role": "peer"
    }
  },
  "trust_relationships": [
    {"from": "org-a", "to": "org-b", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-a", "trust_level": "EXPLICIT"},
    {"from": "org-b", "to": "org-c", "trust_level": "EXPLICIT"},
    {"from": "org-c", "to": "org-b", "trust_level": "EXPLICIT"}
  ]
}
```

Each organization explicitly defines which peers it trusts.

## Realm Configuration

### Enable Federation Broker

1. Log in to Keycloak Admin Console
2. Select your realm
3. Navigate to **Authentication** → **Flows**
4. Duplicate the "Browser" flow (or create new flow)
5. Click **Add execution**
6. Select **Auth41 Federation Broker**
7. Set requirement to **REQUIRED** or **ALTERNATIVE**
8. Click **Save**

### Bind Flow to Realm

1. Navigate to **Authentication** → **Bindings**
2. Set **Browser Flow** to your new flow
3. Click **Save**

### Configure Identity Provider

For each trusted provider in the network, create an OIDC identity provider:

1. Navigate to **Identity Providers** → **Add provider** → **OpenID Connect v1.0**
2. Configure:
   - **Alias**: Use provider_id from trust network (e.g., `acme-corp`)
   - **Display name**: Provider name (e.g., "ACME Corporation")
   - **Authorization URL**: `{issuer}/protocol/openid-connect/auth`
   - **Token URL**: `{issuer}/protocol/openid-connect/token`
   - **Logout URL**: `{issuer}/protocol/openid-connect/logout`
   - **Client ID**: Your client ID at the provider
   - **Client Secret**: Your client secret
   - **Issuer**: Must match `issuer` in trust network
3. Under **Advanced Settings**:
   - **Validate Signatures**: ON
   - **Use JWKS URL**: ON
   - **JWKS URL**: `{issuer}/protocol/openid-connect/certs`
4. Click **Save**

### Configure Attribute Mapping

Map claims from ID tokens to user attributes:

1. In Identity Provider settings, go to **Mappers** tab
2. Click **Add mapper**
3. Configure mapper:
   - **Name**: Descriptive name (e.g., "Email")
   - **Mapper Type**: **Attribute Importer**
   - **Claim**: Token claim name (e.g., `email`)
   - **User Attribute Name**: Local attribute (e.g., `email`)
4. Click **Save**

**Common mappings**:

| Claim | User Attribute | Description |
|-------|----------------|-------------|
| `email` | `email` | User email address |
| `given_name` | `firstName` | First name |
| `family_name` | `lastName` | Last name |
| `preferred_username` | `username` | Preferred username |
| `phone_number` | `phoneNumber` | Phone number |

### Configure Account Linking

Control how federated accounts are linked:

1. Navigate to **Identity Providers** → [Provider] → **Settings**
2. Under **Account Linking**:
   - **First Login Flow**: Select flow for first-time users
   - **Post Login Flow**: Select flow for returning users
   - **Link Only**: Require existing account to link (ON/OFF)

**Options**:

- **Automatic Linking**: Automatically link to existing user with same email
- **Prompt User**: Ask user to link or create new account
- **Create Only**: Always create new account (no linking)

### Theme Configuration

Configure Auth41 themes:

1. Navigate to **Realm Settings** → **Themes**
2. Select themes:
   - **Login Theme**: `auth41-corporate`, `auth41-modern`, or `auth41-minimal`
   - **Account Theme**: `auth41-corporate`, `auth41-modern`, or `auth41-minimal`
   - **Email Theme**: `auth41-corporate`, `auth41-modern`, or `auth41-minimal`
3. Click **Save**

#### Dynamic Theme Selection

Use realm attributes for dynamic selection:

1. Navigate to **Realm Settings** → **General** → **Realm Attributes**
2. Add attribute:
   - **Key**: `auth41.theme.default`
   - **Value**: `auth41-modern`
3. For client-specific themes:
   - **Key**: `auth41.theme.client.{client-id}`
   - **Value**: `auth41-corporate`

#### User-Specific Themes

Set theme in user attributes:

1. Navigate to **Users** → [User] → **Attributes**
2. Add attribute:
   - **Key**: `theme`
   - **Value**: `auth41-modern`

**Priority**: User attribute > Client mapping > Realm default

## Discovery Configuration

### Email Domain Discovery

Configure email domains in trust network:

```json
{
  "provider_id": "acme-corp",
  "issuer": "https://auth.acme.com/realms/employees",
  "discovery": {
    "email_domains": ["acme.com", "acme.org", "acme.net"]
  }
}
```

When user enters `user@acme.com`, discovery returns `acme-corp` provider.

### WebFinger Discovery

Enable WebFinger for dynamic discovery:

```json
{
  "provider_id": "acme-corp",
  "issuer": "https://auth.acme.com/realms/employees",
  "discovery": {
    "webfinger_enabled": true
  }
}
```

Discovery will query:
```
https://acme.com/.well-known/webfinger?resource=acct:user@acme.com
```

Expected response:
```json
{
  "subject": "acct:user@acme.com",
  "links": [
    {
      "rel": "http://openid.net/specs/connect/1.0/issuer",
      "href": "https://auth.acme.com/realms/employees"
    }
  ]
}
```

### Discovery Priority

Discovery methods are tried in order:

1. **User Attribute**: Check `home_provider` attribute on existing user
2. **Email Domain**: Match email domain to provider
3. **WebFinger**: Query WebFinger endpoint
4. **Fallback**: Use default provider (if configured)

## Accounts Configuration

### Shadow Account Creation

Configure account creation behavior:

**System Properties**:
```bash
# Username template (default: ${email})
-Dauth41.accounts.username-template='${email}'

# Auto-create accounts (default: true)
-Dauth41.accounts.auto-create=true

# Auto-link by email (default: false)
-Dauth41.accounts.auto-link-email=false
```

**Username Templates**:
- `${email}` - Use full email address
- `${preferred_username}` - Use preferred username claim
- `${sub}` - Use subject claim (unique ID)
- `${preferred_username}@${provider_id}` - Scoped username

### Attribute Synchronization

Configure which attributes to sync from ID token:

**System Properties**:
```bash
# Sync attributes on every login (default: true)
-Dauth41.accounts.sync-on-login=true

# Attributes to sync (comma-separated)
-Dauth41.accounts.sync-attributes=email,firstName,lastName,phoneNumber
```

### Account Lifecycle

**First Login** (no existing account):
1. ID token received from home provider
2. Check if shadow account exists (by federated identity link)
3. If not, create new user
4. Sync attributes from token
5. Link federated identity
6. Create local session

**Subsequent Login** (existing account):
1. Find shadow account by federated identity link
2. Update last login timestamp
3. Sync changed attributes (if enabled)
4. Create local session

**Account Removal**:
- Deleting shadow account in Keycloak removes federated link
- User must re-authenticate to recreate account
- Original account at home provider unaffected

## Complete Configuration Example

### Scenario: University Consortium

Three universities (A, B, C) in a hub-and-spoke federation:

**trust-network.json**:
```json
{
  "network_id": "university-consortium",
  "topology_type": "hub-and-spoke",
  "providers": {
    "consortium-hub": {
      "provider_id": "consortium-hub",
      "issuer": "https://auth.consortium.edu/realms/federation",
      "role": "hub",
      "discovery": {
        "email_domains": ["consortium.edu"]
      },
      "metadata": {
        "name": "University Consortium Hub"
      }
    },
    "university-a": {
      "provider_id": "university-a",
      "issuer": "https://sso.university-a.edu/realms/students",
      "role": "spoke",
      "discovery": {
        "email_domains": ["university-a.edu", "univ-a.edu"],
        "webfinger_enabled": true
      },
      "metadata": {
        "name": "University A"
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
        "name": "University B"
      }
    }
  },
  "trust_relationships": [
    {
      "from": "consortium-hub",
      "to": "university-a",
      "trust_level": "EXPLICIT",
      "metadata": {"established": "2025-01-01"}
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
      "metadata": {"established": "2025-01-01"}
    },
    {
      "from": "university-b",
      "to": "consortium-hub",
      "trust_level": "EXPLICIT"
    }
  ],
  "metadata": {
    "version": "1.0",
    "description": "University Consortium Federation Network"
  }
}
```

**Environment**:
```bash
export AUTH41_TRUST_NETWORK_PATH=/opt/keycloak/data/trust-network.json
export AUTH41_LOG_LEVEL=INFO
```

**Keycloak Configuration** (at consortium hub):

1. Create identity providers for University A and B
2. Configure authentication flow with Federation Broker
3. Set up attribute mappers for email, firstName, lastName
4. Enable automatic account linking by email
5. Select `auth41-corporate` theme

**User Flow**:
1. Student from University A (`student@university-a.edu`) accesses resource at consortium hub
2. Federation Broker discovers home provider: `university-a`
3. Topology validates trust path: `consortium-hub` → `university-a` (EXPLICIT)
4. Student redirected to University A SSO
5. After authentication, token returned to consortium hub
6. Shadow account created/updated with synced attributes
7. Student granted access to resource

## Testing Configuration

### Verify Trust Network Loading

Check Keycloak logs:

```bash
grep "Auth41" $KEYCLOAK_HOME/data/log/keycloak.log
```

Expected output:
```
INFO  [org.apifocal.auth41.trust] Loaded trust network: university-consortium
INFO  [org.apifocal.auth41.trust] Registered 3 providers
INFO  [org.apifocal.auth41.trust] Registered 4 trust relationships
```

### Test Discovery

Use Keycloak admin CLI:

```bash
# Test email domain discovery
curl -X GET "http://localhost:8080/realms/main/auth41/discover?email=user@university-a.edu"
```

Expected response:
```json
{
  "provider_id": "university-a",
  "issuer": "https://sso.university-a.edu/realms/students"
}
```

### Test Authentication Flow

1. Access protected application
2. Enter email: `student@university-a.edu`
3. Verify redirect to University A login page
4. Authenticate at University A
5. Verify redirect back to application
6. Check shadow account created in Keycloak

### Troubleshooting Configuration

See [Troubleshooting Guide](troubleshooting.md) for common configuration issues.

## Next Steps

- [Plugin Documentation](plugins/trust-network.md) - Learn about individual plugins
- [Examples](examples/hub-spoke.md) - Detailed configuration examples
- [Troubleshooting](troubleshooting.md) - Common issues and solutions
