# Keycloak Setup & Configuration Guide

Complete guide for setting up Keycloak authentication for the Payment Platform.

**Status**: Production-Ready | Phase 2 Complete | OAuth2/OpenID Connect Enabled  
**Last Updated**: 2026-09-21 | Keycloak 24.0

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Quick Start](#quick-start)
4. [Understanding the Setup](#understanding-the-setup)
5. [Realm Configuration](#realm-configuration)
6. [Client Configuration](#client-configuration)
7. [User Management](#user-management)
8. [Role-Based Access Control](#role-based-access-control)
9. [API Gateway Integration](#api-gateway-integration)
10. [Token Validation](#token-validation)
11. [Testing Authentication](#testing-authentication)
12. [Troubleshooting](#troubleshooting)
13. [Security Best Practices](#security-best-practices)
14. [Future Enhancements](#future-enhancements)

---

## Overview

### What is Keycloak?

**Keycloak** is an open-source identity and access management (IAM) solution that provides:
- OAuth2/OpenID Connect server
- User federation and management
- Role-based access control (RBAC)
- Multi-factor authentication (MFA)
- Social login integration

**Why we use it:**
- Centralized authentication for all microservices
- Standard OAuth2 protocol (not tied to Keycloak)
- Easy user and role management
- Testable locally (no cloud dependency)
- Production-grade security

### Current Architecture

```
Client Application
  ↓ OAuth2 Authorization Code Flow
Keycloak (Port 8090)
  ├─ Realm: payment-platform
  ├─ Client: payment-api
  ├─ Roles: USER, ADMIN, SUPPORT
  └─ Users: test-user, admin-user
  ↑ JWT Token
API Gateway (Port 8080)
  ├─ Validates JWT signature
  ├─ Checks token expiration
  ├─ Extracts user claims
  └─ Routes authenticated requests to microservices
```

---

## Architecture

### OAuth2 Flow (Client Credentials)

Used for **service-to-service authentication** and **testing**:

```
1. Client sends credentials to Keycloak
   POST /realms/payment-platform/protocol/openid-connect/token
   └─ client_id: payment-api
   └─ grant_type: client_credentials

2. Keycloak returns JWT token
   {
     "access_token": "eyJhbGci...",
     "expires_in": 300,
     "token_type": "Bearer"
   }

3. Client sends token to API Gateway
   GET /api/payments
   └─ Authorization: Bearer eyJhbGci...

4. API Gateway validates token
   ├─ Fetches Keycloak's public key
   ├─ Verifies token signature
   ├─ Checks expiration
   └─ Extracts user claims

5. If valid, route to microservices
   Microservices receive JWT in request context
```

### JWT Token Structure

```
Header:
{
  "alg": "RS256",       // Algorithm (RSA 256-bit)
  "typ": "JWT",         // Type
  "kid": "abc123"       // Key ID (for public key lookup)
}

Payload:
{
  "sub": "user-id",                    // Subject (user identifier)
  "iss": "http://keycloak.../...",    // Issuer
  "exp": 1726944600,                   // Expiration time
  "iat": 1726944300,                   // Issued at
  "email": "user@example.com",
  "realm_access": {
    "roles": ["USER", "default-roles-payment-platform"]
  },
  "resource_access": {
    "payment-api": {
      "roles": ["USER"]
    }
  }
}

Signature:
[Base64-encoded RSA signature]
```

---

## Quick Start

### 1. Start Keycloak (Already in Docker Compose)

```bash
# Start Keycloak with docker-compose
docker-compose up -d keycloak

# Wait for startup (shows "Listening on" message)
docker-compose logs keycloak | grep -i listening
```

**Expected output**:
```
keycloak | Listening on: http://0.0.0.0:8080
keycloak | (Initial admin username: admin, password: admin)
```

### 2. Initialize Realm, Client, and Users

**Automatic initialization** (on Docker container start):
```bash
# Runs automatically via init-keycloak.sh
# Creates:
#   ├─ Realm: payment-platform
#   ├─ Client: payment-api (public client)
#   ├─ Roles: USER, ADMIN, SUPPORT
#   ├─ User: test-user (role: USER, password: password123)
#   └─ User: admin-user (role: ADMIN, password: password123)
```

**Manual initialization** (if needed):
```bash
# Run initialization script manually
bash infrastructure/keycloak/init-keycloak.sh

# Or with custom settings
KEYCLOAK_URL=http://keycloak:8080 bash infrastructure/keycloak/init-keycloak.sh
```

### 3. Access Keycloak Admin Console

```
URL: http://localhost:8090
Username: admin
Password: admin

Navigate to:
  Realms → payment-platform
  Clients → payment-api
  Users → test-user, admin-user
```

### 4. Get JWT Token for Testing

```bash
# Option 1: Client Credentials Flow (for testing)
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" \
  -d "client_secret=your-client-secret" | jq -r '.access_token')

echo $TOKEN

# Option 2: Resource Owner Password Flow (for user testing)
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=test-user" \
  -d "password=password123" \
  -d "client_id=payment-api" | jq -r '.access_token')

echo $TOKEN
```

### 5. Use Token to Call API

```bash
# Make request with JWT token
curl -X GET http://localhost:8080/api/accounts/ACC123/balance \
  -H "Authorization: Bearer $TOKEN"

# Expected: 200 OK with account balance
# Or: 401 Unauthorized if token invalid/expired
```

---

## Understanding the Setup

### What's Already Configured

#### 1. Docker Compose Service

**File**: `docker-compose.yml`

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0
  container_name: payments-keycloak
  environment:
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
  command: start-dev
  ports:
    - "8090:8080"
  depends_on:
    - postgres
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**What this does**:
- Pulls Keycloak 24.0 image
- Sets admin credentials (admin/admin)
- Exposes port 8090 externally (8080 internally)
- Runs in development mode (no HTTPS required locally)
- Depends on PostgreSQL for data persistence

#### 2. Initialization Script

**File**: `infrastructure/keycloak/init-keycloak.sh`

**What it does**:
```bash
1. Waits for Keycloak to be ready
2. Gets admin authentication token
3. Creates realm: "payment-platform"
4. Creates client: "payment-api" (public client)
5. Creates roles: USER, ADMIN, SUPPORT
6. Creates test users: test-user, admin-user
```

**Run time**: ~10-15 seconds after Keycloak starts

#### 3. API Gateway Integration

**File**: `api-gateway-service/src/main/java/.../KeycloakTokenValidator.java`

**What it does**:
```java
1. Intercepts all /api/* requests
2. Extracts JWT from Authorization header
3. Fetches Keycloak's public key (JWKS endpoint)
4. Validates token signature using RSA-256
5. Checks token expiration
6. Extracts user claims (roles, permissions)
7. Caches public keys (1-hour TTL)
8. Falls back to local secret if Keycloak unavailable
```

#### 4. Configuration Files

**File**: `api-gateway-service/src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: "http://keycloak:8080/realms/payment-platform"
          jwk-set-uri: "http://keycloak:8080/realms/payment-platform/protocol/openid-connect/certs"

jwt:
  secret: "your-super-secret-key-that-is-at-least-32-characters-long-for-hs256"
```

**What this configures**:
- Issuer URI: Where to find the realm
- JWKS URI: Where to fetch public keys
- Fallback secret: For testing without Keycloak

---

## Realm Configuration

### What is a Realm?

A **realm** is a namespace that:
- Contains users, roles, and clients
- Has its own security policies
- Issues its own tokens
- Manages its own configuration

### Create/Manage Realm

#### Via Admin Console

```
1. Open: http://localhost:8090
2. Login: admin / admin
3. Top-left dropdown: "Master" → "Create realm"
4. Realm name: payment-platform
5. Create

OR (if already exists):
1. Top-left dropdown: Select "payment-platform"
```

#### Via REST API

```bash
# Create realm
curl -X POST http://keycloak:8080/admin/realms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "realm": "payment-platform",
    "enabled": true,
    "displayName": "Payment Platform"
  }'

# Get realm details
curl -X GET http://keycloak:8080/admin/realms/payment-platform \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Realm Settings

**Access via console**: Realms → payment-platform → Settings

| Setting | Value | Purpose |
|---------|-------|---------|
| Realm Name | payment-platform | Unique identifier |
| Display Name | Payment Platform | Human-readable name |
| Enabled | true | Realm is active |
| Token exp | 300 seconds | JWT expiration |
| Access code lifetime | 60 seconds | Auth code validity |
| Session timeout | 1800 seconds | User session timeout |

---

## Client Configuration

### What is a Client?

A **client** is an application that:
- Requests tokens from Keycloak
- Uses tokens to access APIs
- Can have roles and permissions
- Can be public (browser app) or confidential (server app)

### Current Client: "payment-api"

**Type**: Public Client (no client secret required)

#### Via Admin Console

```
1. Realms → payment-platform → Clients
2. Click "payment-api"
3. Review settings
```

#### Via REST API

```bash
# Get client details
curl -X GET http://keycloak:8080/admin/realms/payment-platform/clients?clientId=payment-api \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Update client settings
curl -X PUT http://keycloak:8080/admin/realms/payment-platform/clients/{CLIENT_ID} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "clientId": "payment-api",
    "enabled": true,
    "publicClient": true,
    "directAccessGrantsEnabled": true,
    "standardFlowEnabled": true,
    "redirectUris": ["http://localhost:*/*"],
    "webOrigins": ["http://localhost:*"]
  }'
```

### Client Settings Reference

| Setting | Value | Purpose |
|---------|-------|---------|
| Client ID | payment-api | Identifier in token requests |
| Client Secret | (none) | Public client (no secret) |
| Enabled | true | Client is active |
| Public Client | true | No secret required |
| Direct Access Grants | true | Allows password flow |
| Standard Flow | true | Allows auth code flow |
| Redirect URIs | http://localhost:*/* | Where to redirect after auth |
| Web Origins | http://localhost:* | CORS configuration |
| Protocol | openid-connect | OAuth2/OIDC protocol |

---

## User Management

### Test Users

Two users are created automatically:

```
User 1:
  Username: test-user
  Password: password123
  Roles: USER
  Email: test@example.com

User 2:
  admin-user
  Password: password123
  Roles: ADMIN
  Email: admin@example.com
```

### Create New User

#### Via Admin Console

```
1. Realms → payment-platform → Users
2. Click "Create new user"
3. Fill in:
   - Username: new-user
   - Email: new-user@example.com
   - First Name: New
   - Last Name: User
   - Email verified: toggle
   - Enabled: toggle
4. Create
5. Credentials tab: Set password
6. Realm Roles: Assign roles
```

#### Via REST API

```bash
# Create user
curl -X POST http://keycloak:8080/admin/realms/payment-platform/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "username": "new-user",
    "email": "new-user@example.com",
    "firstName": "New",
    "lastName": "User",
    "enabled": true,
    "credentials": [{
      "type": "password",
      "value": "password123",
      "temporary": false
    }]
  }'

# Get user ID
curl -X GET http://keycloak:8080/admin/realms/payment-platform/users?username=new-user \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[0].id'

# Reset password
curl -X PUT http://keycloak:8080/admin/realms/payment-platform/users/{USER_ID}/reset-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "type": "password",
    "value": "newpassword123",
    "temporary": false
  }'
```

### List Users

```bash
# Get all users
curl -X GET http://keycloak:8080/admin/realms/payment-platform/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Search user
curl -X GET "http://keycloak:8080/admin/realms/payment-platform/users?search=test-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Get specific user details
USER_ID=$(curl -s -X GET "http://keycloak:8080/admin/realms/payment-platform/users?username=test-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

curl -X GET http://keycloak:8080/admin/realms/payment-platform/users/$USER_ID \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

## Role-Based Access Control

### Roles

Three roles are available:

```
USER:
  └─ Can access basic payment APIs
  └─ Can view own transaction history

ADMIN:
  └─ Can access all payment APIs
  └─ Can view all transactions
  └─ Can manage users

SUPPORT:
  └─ Can view transactions (support access)
  └─ Can access support APIs
  └─ Cannot modify payments
```

### Assign Roles to User

#### Via Admin Console

```
1. Realms → payment-platform → Users
2. Click user (e.g., "test-user")
3. Role Mappings tab
4. Available Roles: Select roles
5. Add Selected
```

#### Via REST API

```bash
# Get user ID
USER_ID=$(curl -s -X GET "http://keycloak:8080/admin/realms/payment-platform/users?username=test-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

# Get realm roles
curl -X GET http://keycloak:8080/admin/realms/payment-platform/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Assign role to user
ROLE_ID=$(curl -s -X GET http://keycloak:8080/admin/realms/payment-platform/roles/ADMIN \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.id')

curl -X POST http://keycloak:8080/admin/realms/payment-platform/users/$USER_ID/role-mappings/realm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "[{\"id\":\"$ROLE_ID\",\"name\":\"ADMIN\"}]"

# List user roles
curl -X GET http://keycloak:8080/admin/realms/payment-platform/users/$USER_ID/role-mappings/realm \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

## API Gateway Integration

### How Token Validation Works

**File**: `api-gateway-service/src/main/java/.../KeycloakTokenValidator.java`

#### Flow Diagram

```
Request arrives at API Gateway
        ↓
Extract JWT from Authorization header
        ↓
Decode JWT header to get Key ID (kid)
        ↓
Is public key cached (and fresh)?
  ├─ YES: Use cached key
  └─ NO: Fetch from Keycloak JWKS endpoint
        ↓
Parse RSA public key from JWKS
        ↓
Validate token signature with public key
        ↓
Check token expiration
        ↓
Extract claims (user ID, roles, permissions)
        ↓
Cache public key for 1 hour
        ↓
Allow request to proceed to microservices
        ↓
Microservices receive JWT in request context
```

### Implementation Details

**Public Key Caching**:
- Keys fetched from: `/realms/payment-platform/protocol/openid-connect/certs`
- Cache TTL: 1 hour (3600000 ms)
- Fallback: Local JWT secret (for testing)

**Validation Flow**:
1. Try Keycloak validation (RSA-256 signature)
2. If fails, fallback to local secret
3. If both fail, return 401 Unauthorized

**Fallback Secret**:
```
jwt.secret: "your-super-secret-key-that-is-at-least-32-characters-long-for-hs256"
```
(Configured in application.yml, useful when Keycloak is unavailable)

---

## Testing Authentication

### Get Token (Postman)

**Setup in Postman**:

1. Create new request
2. Auth tab → Type: OAuth 2.0
3. Configure new token:
   - Grant Type: Client Credentials
   - Access Token URL: http://localhost:8090/realms/payment-platform/protocol/openid-connect/token
   - Client ID: payment-api
   - Client Secret: (leave empty - public client)
4. Get New Access Token
5. Use Token

### Get Token (Curl)

```bash
# 1. Get token
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq -r '.access_token')

# 2. Verify token
echo $TOKEN

# 3. Make request with token
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC123",
    "amount": 50.00,
    "recipientId": "RCPT456"
  }'

# Expected response: 200 OK with payment created
```

### Test with Postman Collection

**File**: `postman-collection-gateway.json`

1. Import in Postman
2. Setup → Generate JWT Token (runs curl internally)
3. Use token in any request
4. All requests include: `Authorization: Bearer {{token}}`

### Decode Token (for debugging)

```bash
# Decode JWT (no verification)
curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq -r '.access_token' | jq -R 'split(".") | .[1] | @base64d | fromjson'

# Output shows:
{
  "exp": 1726944600,
  "iat": 1726944300,
  "jti": "abc123",
  "iss": "http://keycloak:8080/realms/payment-platform",
  "aud": "account",
  "sub": "service-account-payment-api",
  "typ": "Bearer",
  "azp": "payment-api",
  "realm_access": {
    "roles": ["default-roles-payment-platform"]
  },
  "resource_access": {
    "payment-api": {
      "roles": []
    }
  }
}
```

---

## Troubleshooting

### Issue: "401 Unauthorized"

**Cause**: Invalid or missing JWT token

**Solution**:
```bash
# 1. Verify token exists
echo $TOKEN

# 2. Verify token is valid
curl -s "http://keycloak:8090/realms/payment-platform/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq

# 3. Verify token hasn't expired
# Decode token and check "exp" field

# 4. Verify correct format in header
# Should be: Authorization: Bearer <token>
# NOT: Authorization: <token>
```

### Issue: "Token has expired"

**Cause**: JWT token reached expiration time (300 seconds default)

**Solution**:
```bash
# Get new token
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq -r '.access_token')

# Verify new token
curl -X GET http://localhost:8080/api/... \
  -H "Authorization: Bearer $TOKEN"
```

### Issue: "Key not found"

**Cause**: Keycloak public key cache doesn't have the key

**Solution**:
```bash
# 1. Verify Keycloak is running
curl http://localhost:8090/health/ready

# 2. Manually refresh public keys
curl http://keycloak:8080/realms/payment-platform/protocol/openid-connect/certs

# 3. Check API Gateway logs
docker-compose logs api-gateway-service | grep -i "key\|keycloak"

# 4. Restart API Gateway to clear cache
docker-compose restart api-gateway-service
```

### Issue: Keycloak won't start

**Cause**: PostgreSQL not ready, port conflict, or insufficient memory

**Solution**:
```bash
# 1. Check PostgreSQL is running
docker-compose logs postgres | tail -20

# 2. Check port 8090 is free
lsof -i :8090

# 3. Increase Docker memory
Settings → Resources → Memory: 4GB+

# 4. Restart services
docker-compose down -v
docker-compose up -d keycloak
```

### Issue: Users not created

**Cause**: init-keycloak.sh didn't run or failed

**Solution**:
```bash
# 1. Check if script ran
docker-compose logs keycloak | grep -i "initialization"

# 2. Run script manually
bash infrastructure/keycloak/init-keycloak.sh

# 3. Check users exist
curl -X GET http://keycloak:8080/admin/realms/payment-platform/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 4. Create users manually if missing
bash infrastructure/keycloak/init-keycloak.sh
```

---

## Security Best Practices

### For Development

✅ **DO**:
- Use `localhost` URLs in development
- Use public client for testing (no secret needed)
- Rotate test passwords weekly
- Log all authentication events
- Use HTTPS in any non-localhost environment

❌ **DON'T**:
- Commit client secrets to git
- Use default passwords in production
- Disable HTTPS for production realms
- Share Keycloak admin password
- Store tokens in browser localStorage

### For Production

1. **Enable HTTPS**: Use TLS certificates
2. **Change Admin Password**: Not `admin/admin`
3. **Use Confidential Clients**: With strong secrets
4. **Setup Email Verification**: Prevent fake accounts
5. **Enable MFA**: For admin users
6. **Configure LDAP/SAML**: For enterprise integration
7. **Setup Backup**: Persistent database backup
8. **Monitor Access**: Log all admin actions
9. **Rate Limit**: Prevent brute force attacks
10. **Audit Trail**: Track token issuance

### For APIs

1. **Validate Issuer**: Confirm `iss` claim
2. **Check Signature**: RSA-256 validation
3. **Verify Expiration**: `exp` claim
4. **Validate Audience**: Optional `aud` claim
5. **Cache Public Keys**: Refresh hourly
6. **Fallback Strategy**: Handle Keycloak downtime
7. **Rate Limiting**: Prevent token exhaustion
8. **Scope Validation**: Only grant needed permissions

---

## Future Enhancements

### Phase 3 Features

| Feature | Benefit | Effort |
|---------|---------|--------|
| **MFA (TOTP)** | Stronger security for users | Medium |
| **Social Login** | Google/GitHub integration | Medium |
| **LDAP/SAML** | Enterprise user federation | High |
| **Custom Theme** | Branded login pages | Low |
| **User Impersonation** | Support team access | Low |
| **Scope Management** | Fine-grained permissions | High |

### Phase 4 Features

| Feature | Benefit | Effort |
|---------|---------|--------|
| **Kubernetes Integration** | Auto-scaling Keycloak | High |
| **Database Replication** | High availability | High |
| **Infinispan Caching** | Performance optimization | Medium |
| **Event Storage** | Audit compliance | Medium |
| **Custom Protocol Mappers** | Token customization | High |
| **Decentralized Identity** | Self-sovereign identity | Very High |

### Recommended Timeline

**Immediate (Phase 2)**:
- ✅ Basic OAuth2 setup (done)
- ✅ User and role management (done)
- ✅ Token validation in API Gateway (done)

**Phase 3 (Q4 2026)**:
- MFA for admin users
- Custom login theme
- User impersonation for support
- Email verification

**Phase 4 (Q1 2027)**:
- LDAP integration
- SAML support
- Kubernetes HA setup
- Advanced auditing

---

## Quick Reference

### URLs

```
Keycloak Admin Console: http://localhost:8090
Realm Dashboard:        http://localhost:8090/admin/realms/payment-platform
Users:                  http://localhost:8090/admin/realms/payment-platform/users
Clients:                http://localhost:8090/admin/realms/payment-platform/clients
Token Endpoint:         http://localhost:8090/realms/payment-platform/protocol/openid-connect/token
JWKS Endpoint:          http://localhost:8090/realms/payment-platform/protocol/openid-connect/certs
Health Check:           http://localhost:8090/health/ready
```

### Common Commands

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8090/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" | jq -r '.access_token')

# Get user token (client credentials)
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq -r '.access_token')

# Get user token (resource owner password)
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=test-user" \
  -d "password=password123" \
  -d "client_id=payment-api" | jq -r '.access_token')

# Decode token
echo $TOKEN | jq -R 'split(".") | .[1] | @base64d | fromjson'

# List all realms
curl -X GET http://keycloak:8080/admin/realms \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# List all users
curl -X GET http://keycloak:8080/admin/realms/payment-platform/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Health check
curl http://localhost:8090/health/ready
```

### Test Users

```
test-user:
  Email: test@example.com
  Password: password123
  Role: USER

admin-user:
  Email: admin@example.com
  Password: password123
  Role: ADMIN
```

---

## Summary

**Keycloak provides**:
- Centralized authentication for all services
- OAuth2/OpenID Connect compliance
- User and role management
- Token-based authorization
- Integration with existing infrastructure

**Currently configured**:
- Realm: payment-platform
- Client: payment-api (public)
- Users: test-user, admin-user
- Roles: USER, ADMIN, SUPPORT
- Token validation in API Gateway

**Next steps**:
1. Use Postman collection to test APIs with JWT tokens
2. Create custom users and roles as needed
3. Plan MFA and social login for Phase 3
4. Monitor Keycloak performance as user base grows

---

**Last Updated**: 2026-09-21  
**Status**: Production-Ready  
**Next Phase**: Phase 3 (Q4 2026) - MFA & Social Login
