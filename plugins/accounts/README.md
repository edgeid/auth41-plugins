# Auth41 Accounts Plugin

User account storage and provider association tracking for Auth41 federation.

## Overview

The `auth41-accounts` plugin provides persistent storage for user account records and tracks which provider has custody of each user's account. This is the foundation for the discovery service to route authentication requests to the correct home provider.

## Features

- ✅ **User Account Storage** - CRUD operations for user records
- ✅ **Provider Association Tracking** - Maps users to their home providers
- ✅ **JPA-based Persistence** - Uses Keycloak's database
- ✅ **Email and Identifier Lookups** - Find users by email or user identifier
- ✅ **Immutable Data Model** - Thread-safe with builder pattern
- ✅ **Comprehensive Tests** - 28 tests, 90%+ coverage

## Data Model

### UserAccount

Immutable user account record with the following fields:

```java
public class UserAccount {
    String userIdentifier;          // Unique identifier (email or DID)
    String email;                   // User's email address (optional)
    String name;                    // Display name (optional)
    String homeProviderId;          // Provider that has this user's account
    Map<String, Object> attributes; // Custom profile attributes
    Instant createdAt;              // Creation timestamp
    Instant updatedAt;              // Last update timestamp
}
```

### Database Schema

Table: `auth41_user_accounts`

| Column | Type | Description |
|--------|------|-------------|
| `user_identifier` | VARCHAR(255) | Primary key, unique user identifier |
| `email` | VARCHAR(255) | User's email address (indexed) |
| `name` | VARCHAR(255) | User's display name |
| `home_provider_id` | VARCHAR(255) | Provider ID (indexed) |
| `attributes` | TEXT | JSON-encoded custom attributes |
| `created_at` | TIMESTAMP | Record creation time |
| `updated_at` | TIMESTAMP | Last update time |

**Indexes:**
- `idx_email` on `email` - Fast lookup by email
- `idx_home_provider_id` on `home_provider_id` - Fast provider queries

## API

### AccountStorageProvider SPI

```java
public interface AccountStorageProvider extends Provider {
    // CRUD operations
    void createAccount(UserAccount account);
    UserAccount getAccount(String userIdentifier);
    UserAccount getAccountByEmail(String email);
    void updateAccount(UserAccount account);
    boolean deleteAccount(String userIdentifier);

    // Provider association queries
    List<UserAccount> findAccountsByProvider(String providerId);
    Set<String> findProvidersByEmail(String userEmail);
    Set<String> findProvidersByUser(String userIdentifier);

    // Utility methods
    boolean accountExists(String userIdentifier);
    long getAccountCount();
    long getAccountCountByProvider(String providerId);
}
```

## Usage Examples

### Creating a User Account

```java
AccountStorageProvider storage = session.getProvider(AccountStorageProvider.class);

UserAccount account = UserAccount.builder()
    .userIdentifier("alice@example.com")
    .email("alice@example.com")
    .name("Alice Smith")
    .homeProviderId("provider-b")
    .attribute("department", "engineering")
    .attribute("role", "developer")
    .build();

storage.createAccount(account);
```

### Finding a User's Home Provider

```java
Set<String> providers = storage.findProvidersByEmail("alice@example.com");
// Returns: ["provider-b"]
```

### Finding All Users at a Provider

```java
List<UserAccount> accounts = storage.findAccountsByProvider("provider-b");
// Returns all user accounts at provider-b
```

### Updating an Account

```java
UserAccount account = storage.getAccount("alice@example.com");

UserAccount updated = UserAccount.builder()
    .userIdentifier(account.getUserIdentifier())
    .email(account.getEmail())
    .name("Alice Johnson")  // Updated name
    .homeProviderId("provider-c")  // Migrated to new provider
    .attributes(account.getAttributes())
    .build();

storage.updateAccount(updated);
```

## Integration with Other Plugins

### auth41-discovery

The discovery plugin uses `AccountStorageProvider` to find which provider has a user's account:

```java
// In DiscoveryProvider implementation
AccountStorageProvider storage = session.getProvider(AccountStorageProvider.class);
Set<String> providers = storage.findProvidersByEmail(userEmail);
```

### auth41-handoff

The handoff plugin updates home provider after user migration:

```java
// After successful migration
UserAccount account = storage.getAccount(userIdentifier);
UserAccount migrated = UserAccount.builder()
    .userIdentifier(account.getUserIdentifier())
    .email(account.getEmail())
    .name(account.getName())
    .homeProviderId(newProviderId)  // Update to new provider
    .attributes(account.getAttributes())
    .build();

storage.updateAccount(migrated);
```

## Building

```bash
# Build this plugin
mvn clean install -pl plugins/auth41-accounts

# Run tests
mvn test -pl plugins/auth41-accounts

# With coverage report
mvn clean verify -pl plugins/auth41-accounts
```

## Deployment

1. **Build the plugin:**
   ```bash
   mvn clean install -pl plugins/auth41-accounts
   ```

2. **Copy JAR to Keycloak:**
   ```bash
   cp target/auth41-accounts-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
   ```

3. **Rebuild Keycloak:**
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```

4. **Start Keycloak:**
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh start-dev
   ```

5. **Verify deployment:**
   - Database table `auth41_user_accounts` should be created automatically via Liquibase
   - Check Keycloak logs for: `Initializing Auth41 Accounts Storage Provider`

## Configuration

The plugin uses Keycloak's existing JPA connection - no additional configuration needed.

### Database Migration

The plugin automatically creates the database schema on first startup using Liquibase. The changelog is located at:
```
META-INF/auth41-accounts-changelog.xml
```

## Testing

### Unit Tests (28 tests)

- **UserAccountTest** (9 tests)
  - Builder pattern with required/optional fields
  - Immutability and defensive copying
  - Equals/hashCode/toString

- **JpaAccountStorageProviderTest** (19 tests)
  - CRUD operations (create, read, update, delete)
  - Lookups by email and identifier
  - Provider association queries
  - Error handling (null inputs, not found, duplicates)
  - Count operations

### Running Tests

```bash
# All tests
mvn test -pl plugins/auth41-accounts

# Specific test class
mvn test -pl plugins/auth41-accounts -Dtest=UserAccountTest

# With coverage
mvn clean verify -pl plugins/auth41-accounts
# Coverage report: target/site/jacoco/index.html
```

## Dependencies

- **Keycloak 23.0.4** - Provides JPA connection and entity management
- **Hibernate 6.2.13** - JPA implementation (provided by Keycloak)
- **Jakarta Persistence API 3.1.0** - JPA annotations
- **Jackson 2.16.1** - JSON serialization for attributes field

## Architecture

```
┌─────────────────────────────────────┐
│   AccountStorageProvider (SPI)      │
│   - Interface definition            │
└──────────────┬──────────────────────┘
               │ implements
               ▼
┌─────────────────────────────────────┐
│  JpaAccountStorageProvider          │
│  - Uses Keycloak's EntityManager    │
│  - CRUD operations                  │
│  - Provider association queries     │
└──────────────┬──────────────────────┘
               │ uses
               ▼
┌─────────────────────────────────────┐
│      UserAccountEntity (JPA)        │
│  - Maps to auth41_user_accounts     │
│  - Liquibase schema management      │
└─────────────────────────────────────┘
               │ converts to/from
               ▼
┌─────────────────────────────────────┐
│     UserAccount (Domain Model)      │
│  - Immutable with builder pattern   │
│  - Defensive copying                │
└─────────────────────────────────────┘
```

## Performance Considerations

- **Indexed Lookups** - Email and provider ID columns are indexed for fast queries
- **Connection Pooling** - Uses Keycloak's existing connection pool
- **Batch Operations** - Use JPA batch operations for bulk inserts
- **Caching** - Consider implementing cache layer for frequently accessed accounts

## Future Enhancements

- [ ] **Audit Trail** - Track account modifications
- [ ] **Soft Delete** - Mark accounts as deleted instead of hard delete
- [ ] **Account History** - Track provider migrations
- [ ] **Bulk Operations** - Batch create/update/delete APIs
- [ ] **Search** - Full-text search on name and attributes
- [ ] **Pagination** - Support for large result sets

## License

Apache License 2.0
