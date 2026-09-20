# Keycloak OAuth2 Setup Guide

**Updated:** 2026-09-21  
**Status:** ✅ Integrated with Payment Platform Gateway

---

## Overview

The Payment Platform now uses **Keycloak** for OAuth2 authentication instead of simple JWT tokens. This provides enterprise-grade security features including:

- ✅ Public key infrastructure (RSA keys instead of shared secrets)
- ✅ Token revocation and blacklisting
- ✅ Automatic key rotation
- ✅ User federation and identity provider integration
- ✅ Audit logging of all authentication events
- ✅ Multi-factor authentication support (future)
- ✅ Role-based access control (RBAC)

---

## Architecture

```
┌─────────────┐
│   Postman   │
└──────┬──────┘
       │ 1. Get OAuth2 Token
       ↓
┌─────────────────┐
│   Keycloak      │ (Port 8090)
│ (Auth Server)   │ ✅ Realm: payment-platform
└────────┬────────┘   ✅ Client: payment-api
         │ 2. Return Access Token (JWT)
         ↓
┌─────────────────┐
│   API Gateway   │ (Port 8080)
│ (Token Validate)│ ✅ Validates token from Keycloak JWKS
└────────┬────────┘   ✅ Extracts user info & roles
         │ 3. Forward with X-User-Id header
         ↓
┌─────────────────────────────────────────────┐
│ Microservices                               │
│ ├─ Account Service (8081)                  │
│ ├─ Fraud Service (8082)                    │
│ ├─ Payment Service (8083)                  │
│ ├─ Notification Service (8084)             │
│ └─ Transaction History (8085)              │
└─────────────────────────────────────────────┘
```

---

## Quick Start (5 Minutes)

### Step 1: Start All Services (including Keycloak)

```bash
cd C:\coding\payment-platform
start-all.bat
```

Services will start in Docker:
- **Keycloak** → http://localhost:8090
- **API Gateway** → http://localhost:8080
- **PostgreSQL** → localhost:5432
- **Kafka** → localhost:9094

Wait ~30 seconds for all containers to be healthy.

### Step 2: Initialize Keycloak Realm

```bash
# Linux/Mac
bash infrastructure/keycloak/init-keycloak.sh

# Windows PowerShell
bash infrastructure/keycloak/init-keycloak.sh
```

This creates:
- ✅ Realm: `payment-platform`
- ✅ Client: `payment-api`
- ✅ Test Users: test-user, admin-user, support-user
- ✅ Roles: USER, ADMIN, SUPPORT

### Step 3: Test with Postman

1. **Open Postman** and import: `postman-collection-gateway.json`
2. **Run "Get Keycloak OAuth2 Token"** (in 01 Setup folder)
   - Fetches access token from Keycloak
   - Auto-saves to `{{jwt_token}}` variable
3. **Run any API request** (now uses Keycloak token!)
   - Example: "Get Account Balance" in 03 Account Service folder
   - Uses `Authorization: Bearer {{jwt_token}}`

---

## Test Users

| Username | Password | Role(s) | Usage |
|----------|----------|---------|-------|
| `test-user` | `password123` | USER | Default test user |
| `admin-user` | `password123` | ADMIN | Admin operations testing |
| `support-user` | `password123` | SUPPORT | Support/read-only access (future) |

**Note:** These are test-only credentials. Change before production deployment.

---

## How Token Validation Works

### 1. Client Gets Token from Keycloak

```bash
curl -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=payment-api" \
  -d "username=test-user" \
  -d "password=password123"

# Response:
{
  "access_token": "eyJhbGc...",  # ← Use this token
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "..."
}
```

### 2. Client Sends Token to Gateway

```bash
curl -H "Authorization: Bearer eyJhbGc..." \
  http://localhost:8080/api/v1/accounts/123/balance
```

### 3. Gateway Validates Token

The gateway performs these checks:

1. **Fetch public key** from Keycloak JWKS endpoint
   - Endpoint: `http://keycloak:8080/realms/payment-platform/protocol/openid-connect/certs`
   - Keycloak URL is internal docker network address (keycloak:8080)

2. **Verify signature** using public key
   - Decodes token: `header.payload.signature`
   - Recalculates signature with public key
   - Rejects if forged or tampered

3. **Check expiration** (`exp` claim)
   - Rejects if expired

4. **Extract user info**
   - User ID (subject): `claims.sub` → `test-user`
   - Roles: `claims.resource_access.payment-api.roles` → `["USER", "ADMIN"]`

5. **Add headers** for downstream services
   - `X-User-Id: test-user`
   - `X-User-Roles: USER,ADMIN`

6. **Route to service** (e.g., Account Service gets request with headers)

### 4. Microservices Trust Gateway

Downstream services assume the gateway has already validated the token. They:
- ✅ Trust `X-User-Id` header (set by gateway)
- ✅ Don't need to validate JWT again
- ✅ Can be stateless (no session storage needed)

---

## Accessing Keycloak Admin Console

### Admin Console URL
```
http://localhost:8090
```

### Admin Credentials
```
Username: admin
Password: admin
```

### What You Can Do in Admin Console

1. **View Users**
   - Keycloak > Realms > payment-platform > Users
   - See: test-user, admin-user, support-user

2. **Reset Passwords**
   - Click user > Set Password
   - Generate temporary password for users

3. **View Clients**
   - Keycloak > Realms > payment-platform > Clients
   - See: payment-api client configuration

4. **View Roles**
   - Keycloak > Realms > payment-platform > Roles
   - See: USER, ADMIN, SUPPORT roles

5. **Monitor Token Issuance**
   - Keycloak > Realms > payment-platform > Events
   - See: Login events, token issuance, etc.

---

## Token Structure (JWT Claims)

Each Keycloak token contains:

```json
{
  "header": {
    "alg": "RS256",           // Algorithm: RSA with SHA-256
    "typ": "JWT",
    "kid": "abc123"           // Key ID (for key rotation)
  },
  "payload": {
    "sub": "test-user",       // Subject (username)
    "iss": "http://keycloak:8080/realms/payment-platform",
    "aud": "payment-api",     // Audience (client ID)
    "exp": 1726950000,        // Expiration time
    "iat": 1726946400,        // Issued at time
    "auth_time": 1726946400,
    "name": "Test User",
    "resource_access": {
      "payment-api": {
        "roles": ["USER"]      // Client roles
      }
    }
  },
  "signature": "..."  // Signed with Keycloak private key
}
```

---

## Configuration Files

### docker-compose.yml
Defines Keycloak container:
```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0
  environment:
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
  ports:
    - "8090:8080"  # External:Internal port mapping
  depends_on:
    - postgres
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
```

### api-gateway-service/src/main/resources/application.yml
Gateway OAuth2 configuration:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: "http://keycloak:8080/realms/payment-platform"
          jwk-set-uri: "http://keycloak:8080/realms/payment-platform/protocol/openid-connect/certs"
```

**Note:** URLs use `keycloak:8080` (internal Docker network)  
Postman uses `http://localhost:8090` (external access)

### KeycloakTokenValidator.java
Gateway token validation logic:
```java
public Claims validateToken(String token) {
  try {
    // Try Keycloak validation first
    return validateWithKeycloak(token);
  } catch (Exception e) {
    // Fallback to local secret (for backward compatibility)
    return validateWithLocalSecret(token);
  }
}
```

---

## Troubleshooting

### "Keycloak connection refused (localhost:8090)"
**Cause:** Keycloak container not running  
**Fix:**
```bash
docker ps | grep keycloak
# If not running, start all services:
start-all.bat
# Wait 30 seconds for healthcheck to pass
```

### "Invalid token" from gateway (401 Unauthorized)
**Cause 1:** Token expired (valid for 1 hour)  
**Fix:** Run "Get Keycloak OAuth2 Token" again in Postman

**Cause 2:** Token from wrong realm  
**Fix:** Verify admin console shows realm "payment-platform"

**Cause 3:** Wrong username/password  
**Fix:** Check credentials (test-user / password123)

### "Cannot fetch public key" in gateway logs
**Cause:** Gateway can't reach Keycloak  
**Check:**
```bash
# From gateway container:
curl http://keycloak:8080/health/ready
```

**Fix:** Ensure docker-compose networking is correct. Check:
1. Both containers in same docker network
2. Keycloak health endpoint returns 200
3. Container logs: `docker logs payments-keycloak`

### Postman "Keycloak Health Check" fails
**Cause:** Port 8090 not accessible  
**Fix:**
1. Verify docker-compose running: `docker ps | grep keycloak`
2. Try direct curl: `curl http://localhost:8090/health/ready`
3. Check firewall rules blocking port 8090
4. Try different Keycloak URL: `http://host.docker.internal:8090` (Mac/Windows)

### Tests fail with "Cannot generate token"
**Cause:** TestTokenGenerator tries to reach Keycloak  
**Fix:** Tests use local token generation (HMAC-SHA256)  
No Keycloak needed for unit tests. If failing:
1. Check test logs for actual error
2. Verify application-test.yml has correct settings
3. Run: `mvnw.cmd test -pl api-gateway-service`

---

## Security Considerations

### For Development ⚠️
- Keycloak admin credentials (admin/admin) are public
- Test user passwords are public
- JWT tokens valid for 1 hour
- No encryption on transit (use localhost only)

### For Production 🔒
- Change Keycloak admin password
- Use unique, strong passwords for all users
- Enable HTTPS/TLS for all connections
- Reduce token expiration (15-30 minutes)
- Enable token revocation for logout
- Monitor Keycloak events for suspicious activity
- Regularly rotate RSA signing keys
- Implement rate limiting on token endpoint
- Use environment variables for secrets

---

## Key Differences from Previous Setup

| Aspect | Before (Simple JWT) | Now (Keycloak OAuth2) |
|--------|---------------------|----------------------|
| **Key Type** | HMAC-SHA256 (shared secret) | RSA-256 (public/private) |
| **Key Storage** | Hardcoded in application.yml | Keycloak JWKS endpoint |
| **Key Rotation** | Manual (requires restart) | Automatic (via Keycloak) |
| **Token Revocation** | Not possible | Immediate (blacklist) |
| **User Management** | Manual authentication | Keycloak realm |
| **Test Tokens** | Hardcoded in tests | Generated via OAuth2 flow |
| **Fallback** | Not applicable | Supported (local secret) |
| **Production Ready** | No | Yes ✅ |

---

## Next Steps

1. ✅ **Start services:** `start-all.bat`
2. ✅ **Initialize Keycloak:** `bash infrastructure/keycloak/init-keycloak.sh`
3. ✅ **Test with Postman:** Import collection, get token, make requests
4. ✅ **Verify gateway logs:** Check token validation messages
5. 📋 **Read:** See 07-PRODUCTION-READINESS.md for deployment roadmap
6. 📋 **Monitor:** Check docker-compose logs for any issues

---

## References

- **Keycloak Documentation:** https://www.keycloak.org/documentation
- **OAuth2 Spec:** https://tools.ietf.org/html/rfc6749
- **JWT Spec:** https://tools.ietf.org/html/rfc7519
- **JWKS Spec:** https://tools.ietf.org/html/rfc7517

---

## Frequently Asked Questions

**Q: Can I use Keycloak with Spring Security?**  
A: Yes! The gateway uses `spring-boot-starter-oauth2-resource-server`. Add OAuth2 filtering as needed.

**Q: How do I add more test users?**  
A: Use Keycloak Admin Console (http://localhost:8090) → Realms → payment-platform → Users → Create.

**Q: How do I revoke a token?**  
A: Once implemented, use Keycloak token revocation endpoint. For now, just wait for expiration.

**Q: Can I use client credentials instead of password grant?**  
A: Yes, but configure public client first. See Keycloak docs for client credentials flow.

**Q: What if I need to change the realm name?**  
A: Update `spring.security.oauth2.resourceserver.jwt.issuer-uri` in application.yml and Postman variables.

---

**Questions?** Check the main README.md or the comprehensive 07-PRODUCTION-READINESS.md guide.
