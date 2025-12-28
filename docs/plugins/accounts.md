# Accounts Plugin

The Accounts plugin manages federated user accounts (shadow accounts) created when users authenticate through Auth41 federation.

## Overview

**Plugin Name**: `auth41-accounts`
**SPI**: Custom `FederatedAccountProvider`, Keycloak `UserStorageProvider`
**Purpose**: Create and manage shadow accounts for federated users

When a user authenticates at their home provider and accesses a service via Auth41 federation, a shadow account is created at the service provider. This plugin manages the lifecycle of these shadow accounts.

## Key Concepts

### Shadow Account

A local user account representing a federated identity:

- **Local User**: Keycloak `UserModel` at service provider
- **Federated Identity Link**: Connection to home provider account
- **Synchronized Attributes**: User attributes from ID token
- **No Local Password**: Cannot authenticate directly

### Account Lifecycle

1. **First Login**: Create shadow account, link federated identity, sync attributes
2. **Subsequent Logins**: Update last login, optionally sync changed attributes
3. **Account Removal**: Delete shadow account (doesn't affect home provider)

## Account Creation Flow

```
1. User authenticates at home provider
2. ID token returned to service provider
3. Accounts plugin checks for existing shadow account
4. If not found:
   a. Extract claims from ID token
   b. Create new UserModel
   c. Set username (email or unique ID)
   d. Sync attributes from token
   e. Link to federated identity
   f. Set account flags (enabled, email verified)
5. If found:
   a. Update last login timestamp
   b. Optionally sync changed attributes
6. Return UserModel for session creation
```

## Configuration

### Username Template

Configure how shadow account usernames are generated:

```bash
# Use full email address (default)
-Dauth41.accounts.username-template='${email}'

# Use preferred username from token
-Dauth41.accounts.username-template='${preferred_username}'

# Use subject claim (unique ID)
-Dauth41.accounts.username-template='${sub}'

# Scoped username (prevents conflicts)
-Dauth41.accounts.username-template='${preferred_username}@${provider_id}'
```

**Template Variables**:
- `${email}` - Email claim from ID token
- `${preferred_username}` - Preferred username claim
- `${sub}` - Subject claim (unique identifier)
- `${provider_id}` - Home provider ID
- `${given_name}` - First name
- `${family_name}` - Last name

### Attribute Synchronization

Configure which attributes to sync:

```bash
# Sync attributes on every login (default: true)
-Dauth41.accounts.sync-on-login=true

# Attributes to sync (comma-separated)
-Dauth41.accounts.sync-attributes=email,firstName,lastName,phoneNumber

# Sync mode: UPDATE_ALWAYS, UPDATE_MISSING, NEVER
-Dauth41.accounts.sync-mode=UPDATE_ALWAYS
```

### Account Linking

Configure automatic account linking:

```bash
# Auto-link by email (default: false)
-Dauth41.accounts.auto-link-email=false

# Auto-link by username (default: false)
-Dauth41.accounts.auto-link-username=false

# Require manual linking (default: true)
-Dauth41.accounts.require-manual-link=true
```

## API Reference

### FederatedAccountProvider SPI

```java
public interface FederatedAccountProvider extends Provider {
    /**
     * Create or update shadow account for federated user
     *
     * @param realm Keycloak realm
     * @param providerId Home provider ID
     * @param claims Claims from ID token
     * @return Shadow account UserModel
     */
    UserModel createOrUpdateAccount(
        RealmModel realm,
        String providerId,
        Map<String, Object> claims
    );

    /**
     * Find shadow account by federated identity
     *
     * @param realm Keycloak realm
     * @param providerId Home provider ID
     * @param externalId User ID at home provider (sub claim)
     * @return Shadow account if exists, null otherwise
     */
    UserModel findAccountByFederatedIdentity(
        RealmModel realm,
        String providerId,
        String externalId
    );

    /**
     * Link federated identity to existing user
     *
     * @param realm Keycloak realm
     * @param user Existing user
     * @param providerId Home provider ID
     * @param externalId User ID at home provider
     */
    void linkFederatedIdentity(
        RealmModel realm,
        UserModel user,
        String providerId,
        String externalId
    );

    /**
     * Sync attributes from ID token to user
     *
     * @param user Shadow account
     * @param claims Claims from ID token
     */
    void syncAttributes(UserModel user, Map<String, Object> claims);
}
```

### Usage Example

```java
public class FederationBroker implements Authenticator {

    @Override
    public void action(AuthenticationFlowContext context) {
        // Extract ID token claims
        Map<String, Object> claims = extractClaims(idToken);
        String providerId = "university-a";

        // Get accounts provider
        FederatedAccountProvider accounts =
            context.getSession().getProvider(FederatedAccountProvider.class);

        // Create or update shadow account
        UserModel user = accounts.createOrUpdateAccount(
            context.getRealm(),
            providerId,
            claims
        );

        // Set user in context
        context.setUser(user);
        context.success();
    }
}
```

## Attribute Mapping

### Standard OIDC Claims

Default attribute mappings:

| ID Token Claim | User Attribute | Description |
|----------------|----------------|-------------|
| `sub` | Federated ID | Unique identifier at home provider |
| `email` | `email` | Email address |
| `email_verified` | `emailVerified` | Email verification status |
| `given_name` | `firstName` | First name |
| `family_name` | `lastName` | Last name |
| `preferred_username` | `username` | Preferred username (if template uses it) |
| `phone_number` | `phoneNumber` | Phone number |
| `picture` | `picture` | Profile picture URL |

### Custom Attribute Mappers

Configure via Identity Provider mappers in Keycloak Admin Console:

1. Navigate to **Identity Providers** → [Provider] → **Mappers**
2. Click **Add mapper**
3. Select **Attribute Importer**
4. Configure:
   - **Claim**: Token claim name (e.g., `department`)
   - **User Attribute Name**: Local attribute (e.g., `department`)
   - **Claim Type**: String, Integer, Boolean, JSON

### Sync Modes

- **UPDATE_ALWAYS**: Always overwrite local attributes
- **UPDATE_MISSING**: Only set if local attribute empty
- **NEVER**: Never sync (one-time at creation only)

## Account Linking

### Automatic Linking by Email

When enabled, link to existing user with matching email:

```java
if (autoLinkByEmail) {
    UserModel existingUser = session.users().getUserByEmail(realm, email);
    if (existingUser != null) {
        linkFederatedIdentity(realm, existingUser, providerId, externalId);
        return existingUser;
    }
}
```

**Security Consideration**: Only enable if email verification is enforced at home provider.

### Manual Linking Flow

Present user with linking options:

1. **Link to Existing Account**: User logs in to existing account, confirms link
2. **Create New Account**: Create separate shadow account
3. **Cancel**: Abort authentication

Configured via **First Login Flow** in Identity Provider settings.

## Data Model

### FederatedIdentity

Keycloak's built-in model linking shadow account to federated identity:

```java
public class FederatedIdentityModel {
    private String identityProvider;  // provider_id
    private String userId;             // sub claim from ID token
    private String userName;           // username at home provider
    private String token;              // optional: last ID token
}
```

Stored in `federated_identity` table:
```sql
SELECT * FROM federated_identity WHERE user_id = 'shadow-user-uuid';
```

### Shadow Account Attributes

Special attributes set on shadow accounts:

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `home_provider` | Provider ID | Discovery hint for future logins |
| `federated_at` | Timestamp | When account was created |
| `last_federated_login` | Timestamp | Last federated authentication |
| `federation_mode` | `shadow` | Marks as federated account |

## Performance

### Account Lookup

- **By Federated Identity**: O(1) database index lookup
- **By Email**: O(1) database index lookup
- **Attribute Sync**: O(n) where n = number of mapped attributes

### Optimization Strategies

1. **Index federated_identity table** on (identity_provider, user_id)
2. **Cache user lookups** in Keycloak's user cache
3. **Lazy attribute sync**: Only sync changed attributes
4. **Batch operations**: Sync multiple attributes in single transaction

## Security Considerations

### Password Protection

Shadow accounts have no local password:
- `credentials` table has no entries for shadow users
- Direct authentication blocked
- Must authenticate via federation

### Email Verification

Trust email verification from home provider:

```java
if (claims.containsKey("email_verified") &&
    (Boolean) claims.get("email_verified")) {
    user.setEmailVerified(true);
}
```

### Account Takeover Prevention

- Don't auto-link by email without verification
- Require explicit user consent for linking
- Log all account linking events
- Support account unlinking

### Attribute Trust

- Only sync attributes from trusted ID token
- Validate attribute values (e.g., email format)
- Sanitize string attributes (prevent XSS)
- Don't sync sensitive attributes (password, SSN, etc.)

## Troubleshooting

### Duplicate Shadow Accounts

**Symptom**: Multiple accounts for same user

**Causes**:
- Auto-linking disabled
- Different usernames generated each login
- Email changed at home provider

**Solutions**:
1. Enable auto-linking by email
2. Use stable username template (`${sub}`)
3. Merge duplicate accounts manually

### Attributes Not Syncing

**Symptom**: User attributes not updated after login

**Check**:
1. Verify sync-on-login enabled
2. Check attribute mappers configured
3. Verify claim present in ID token
4. Check sync mode (UPDATE_ALWAYS vs UPDATE_MISSING)

### Account Linking Fails

**Symptom**: Error when trying to link accounts

**Check**:
1. Existing account email must match token email
2. Email must be verified (if required)
3. User must have permission to link accounts
4. Check for existing federated identity link

## Testing

### Unit Tests

Located in: `src/test/java/org/apifocal/auth41/plugin/accounts/`

**Test Coverage**:
- Shadow account creation
- Attribute synchronization
- Account linking (auto and manual)
- Username template rendering
- Duplicate prevention
- Security validations

**Example Test**:
```java
@Test
void testCreateShadowAccount() {
    Map<String, Object> claims = Map.of(
        "sub", "user-123",
        "email", "alice@university-a.edu",
        "given_name", "Alice",
        "family_name", "Smith"
    );

    UserModel user = accountsProvider.createOrUpdateAccount(
        realm,
        "university-a",
        claims
    );

    assertNotNull(user);
    assertEquals("alice@university-a.edu", user.getEmail());
    assertEquals("Alice", user.getFirstName());
    assertEquals("Smith", user.getLastName());
}
```

**Run Tests**:
```bash
mvn test -pl plugins/auth41-accounts
```

## Integration with Other Plugins

### Federation Broker

Federation broker creates/updates shadow accounts after authentication:

```java
UserModel user = accountsProvider.createOrUpdateAccount(
    realm,
    homeProviderId,
    idTokenClaims
);
context.setUser(user);
```

### Discovery Plugin

Accounts plugin sets `home_provider` attribute for discovery:

```java
user.setSingleAttribute("home_provider", providerId);
// Future logins use this for fast discovery
```

## Next Steps

- [Federation Broker](federation-broker.md) - Uses accounts for shadow user creation
- [Discovery Plugin](discovery.md) - Uses home_provider attribute
- [Configuration Guide](../configuration.md) - Configure account linking and sync
- [Troubleshooting](../troubleshooting.md) - Account troubleshooting
