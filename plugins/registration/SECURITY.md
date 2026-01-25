# Security Configuration

## Test Endpoints

### `/test/clear` Endpoint

The registration plugin includes a test endpoint at `DELETE /realms/{realm}/registration/test/clear` that allows clearing all invite tokens and registration requests from the database.

**⚠️ SECURITY WARNING:** This endpoint is **disabled by default** and should only be enabled in development/test environments.

#### Security Controls

This endpoint requires **BOTH** of the following conditions to be met:

1. **Configuration Flag**: `enable-test-endpoints=true` must be set (default: `false`)
2. **Admin Authentication**: The user must have realm administrator permissions

#### Enabling the Endpoint (Dev/Test Only)

To enable the test endpoint in a development or test environment, add the following system property:

```bash
-Dspi-realm-resource-registration-enable-test-endpoints=true
```

**Example Keycloak startup:**

```bash
bin/kc.sh start-dev \
  -Dspi-realm-resource-registration-enable-test-endpoints=true
```

#### Production Configuration

**DO NOT** enable test endpoints in production. The configuration defaults to `false` when not specified, which means the endpoint will return `404 Not Found` even if called by an administrator.

#### Usage

When enabled and authenticated as a realm administrator:

```bash
curl -X DELETE \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://keycloak.example.com/realms/myrealm/registration/test/clear
```

Response:
```json
{
  "message": "Test data cleared",
  "invites_deleted": 5,
  "requests_deleted": 3
}
```

#### Security Responses

- **404 Not Found**: Test endpoints are disabled (production mode)
- **403 Forbidden**: User does not have admin permissions
- **200 OK**: Successfully cleared data (admin with test endpoints enabled)
