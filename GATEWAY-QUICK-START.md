# API Gateway - Quick Start & Testing Guide

## ⚡ 30-Second Summary

The API Gateway:
- Listens on **port 8080**
- **Validates JWT tokens** for every request
- **Routes requests** to the correct microservice
- **Limits requests** to prevent abuse (100 req/min per user)

---

## 🚀 Quick Start

### 1. Build the Gateway

```bash
cd C:\coding\payment-platform\api-gateway-service
mvnw.cmd clean package -DskipTests
```

Expected output: `BUILD SUCCESS`

### 2. Start All Services

```bash
cd C:\coding\payment-platform
start-all.bat
```

Wait for all 5 services to start (should take ~60 seconds).

### 3. Verify Gateway is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected output:
```json
{
  "status": "UP"
}
```

---

## 🧪 Testing the Gateway

### Test 1: Request WITHOUT JWT Token (Should Fail)

**Command:**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance
```

**Expected:**
```
HTTP 401 Unauthorized
```

**Why:** Gateway requires JWT token for all requests to protected endpoints.

---

### Test 2: Create a JWT Token (For Testing)

Use Postman or create a script. Let me show you how to generate one:

**Java Code (quick test):**
```java
import io.jsonwebtoken.*;
import java.util.Date;

// Secret must match gateway's secret in application.yml
String secret = "your-super-secret-key-that-is-at-least-32-characters-long-for-hs256";

String token = Jwts.builder()
    .setSubject("test-user")
    .claim("roles", Arrays.asList("USER", "ADMIN"))
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + 3600000))  // 1 hour
    .signWith(SignatureAlgorithm.HS256, secret.getBytes())
    .compact();

System.out.println("Token: " + token);
```

---

### Test 3: Request WITH JWT Token (Should Succeed)

**Command:**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

Replace `<YOUR_TOKEN>` with the token from Test 2.

**Expected:**
```
HTTP 200 OK
```

(Account service will return account data)

---

### Test 4: Rate Limiting (Make 101+ Requests)

**Command (in PowerShell):**
```powershell
$token = "<YOUR_TOKEN>"
for ($i = 1; $i -le 105; $i++) {
    $response = Invoke-WebRequest `
        -Uri "http://localhost:8080/api/v1/accounts/123/balance" `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -eq 429) {
        Write-Host "Request $i: Rate Limited (429)"
    } else {
        Write-Host "Request $i: OK"
    }
}
```

**Expected:**
```
Request 1: OK
Request 2: OK
...
Request 100: OK
Request 101: Rate Limited (429)
Request 102: Rate Limited (429)
...
```

---

### Test 5: Invalid Token (Should Fail)

**Command:**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance \
  -H "Authorization: Bearer INVALID_TOKEN_123"
```

**Expected:**
```
HTTP 401 Unauthorized
```

**Why:** Token signature doesn't match (was forged or corrupted).

---

### Test 6: Expired Token (Should Fail)

Create a token that expires immediately:

```java
String expiredToken = Jwts.builder()
    .setSubject("test-user")
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() - 1000))  // Expired 1 sec ago
    .signWith(SignatureAlgorithm.HS256, secret.getBytes())
    .compact();
```

**Command:**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance \
  -H "Authorization: Bearer <EXPIRED_TOKEN>"
```

**Expected:**
```
HTTP 401 Unauthorized
```

---

### Test 7: Malformed Authorization Header (Should Fail)

**Command 1: Missing "Bearer " prefix**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance \
  -H "Authorization: <TOKEN>"
```

**Expected:**
```
HTTP 401 Unauthorized
```

**Command 2: Missing entire Authorization header**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123/balance
```

**Expected:**
```
HTTP 401 Unauthorized
```

---

### Test 8: Route to Different Services

**Test Account Service:**
```bash
curl -X GET http://localhost:8080/api/v1/accounts/123 \
  -H "Authorization: Bearer <TOKEN>"
```

**Test Payment Service:**
```bash
curl -X GET http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer <TOKEN>"
```

**Test Fraud Service:**
```bash
curl -X GET http://localhost:8080/api/v1/risk \
  -H "Authorization: Bearer <TOKEN>"
```

All should work if backend services are running.

---

## 📊 Test Summary Table

| Test | Request | Token | Expected | Reason |
|------|---------|-------|----------|--------|
| 1 | GET /accounts/123 | None | 401 | No auth header |
| 2 | GET /accounts/123 | Valid | 200 | Valid token |
| 3 | GET /accounts/123 | Invalid | 401 | Forged signature |
| 4 | GET /accounts/123 | Expired | 401 | Token expired |
| 5 | (x101 in 60s) | Valid | 429 | Rate limit |
| 6 | GET /actuator/health | None | 200 | No auth required |

---

## 🔍 Debugging: View Logs

### Check Gateway Logs

Look in the terminal window running the gateway service:

```
[DEBUG] JWT token validated for user: test-user
[DEBUG] Rate limit OK for user test-user: remaining requests: 99
```

### Check Request Headers (With curl verbose)

```bash
curl -v -X GET http://localhost:8080/api/v1/accounts/123 \
  -H "Authorization: Bearer <TOKEN>"
```

Look for:
```
> Authorization: Bearer eyJhbGci...
< HTTP/1.1 200 OK
< X-User-Id: test-user
```

---

## 🧩 Postman Collection Setup

### 1. Create Test Token Request

**Method:** POST  
**URL:** `http://localhost:8080/test/token` (if endpoint exists)

Or use the pre-request script to generate one.

### 2. Create General Request

**Method:** GET  
**URL:** `http://localhost:8080/api/v1/accounts/123`

**Headers:**
```
Authorization: Bearer {{jwt_token}}
```

### 3. Pre-request Script

```javascript
// This runs before the request
var secret = "your-super-secret-key...";
var token = jwt.sign({
    sub: "test-user",
    roles: ["USER", "ADMIN"]
}, secret, {
    algorithm: "HS256",
    expiresIn: "1h"
});

pm.environment.set("jwt_token", token);
```

---

## 📋 Troubleshooting

### Problem: "Connection refused on port 8080"

**Solution:**
1. Make sure gateway is running: `mvnw.cmd spring-boot:run`
2. Check it's on port 8080: `netstat -an | grep 8080`
3. Wait 10 seconds for startup

### Problem: "401 Unauthorized" on valid token

**Possible causes:**
1. Secret key doesn't match gateway's secret
2. Token was generated with different secret
3. Token has expired

**Solution:**
- Check `application.yml` for JWT secret
- Regenerate token with matching secret

### Problem: "Rate limited too quickly"

**Possible causes:**
1. Rate limit is 100 per minute per user
2. Tokens from previous tests still active
3. Multiple curl commands running

**Solution:**
- Wait 60 seconds for bucket to refill
- Use fresh token

### Problem: "404 Not Found" even with valid token

**Possible causes:**
1. Backend service not running
2. URL path is wrong
3. Route not configured in GatewayConfig

**Solution:**
- Check all 5 services are running: `start-all.bat`
- Verify path matches route pattern
- Check GatewayConfig.java routes

---

## ✅ Success Checklist

After testing, you should have:

- ✅ Gateway starts on port 8080
- ✅ Valid JWT tokens are accepted
- ✅ Invalid tokens return 401
- ✅ Rate limiting works (429 after 100 requests)
- ✅ Requests route to correct services
- ✅ User info is extracted from token
- ✅ All logs are clear (no errors)

---

## 🎓 Understanding the Response

When you make a request, here's what happens:

```
You send:
  GET /api/v1/accounts/123
  Authorization: Bearer <token>
           ↓
Gateway receives
           ↓
JwtAuthenticationFilter:
  ├─ Validates token ✓
  ├─ Extracts user: "user123"
  └─ Adds header: X-User-Id: user123
           ↓
RateLimitFilter:
  ├─ Gets user bucket ✓
  ├─ Consumes 1 token
  └─ 99 tokens remaining
           ↓
Routes to account-service:8081
           ↓
Account service:
  ├─ Receives request
  ├─ Sees header: X-User-Id: user123
  ├─ Queries database
  └─ Returns: { "accountId": "123", "balance": 1000.00 }
           ↓
Gateway returns to you:
  HTTP 200 OK
  { "accountId": "123", "balance": 1000.00 }
```

---

## 📞 Common Questions

**Q: Can I disable JWT for some routes?**

A: Yes! Edit `GatewayConfig.java` and remove the JWT filter for specific routes. But don't do this for production endpoints.

**Q: How do I increase the rate limit?**

A: Edit `RateLimitFilter.java`:
```java
private static final int REQUESTS_PER_MINUTE = 100;  // Change this
```

**Q: How do I change the JWT secret?**

A: Edit `application.yml`:
```yaml
jwt:
  secret: "your-new-secret-key-here"
```

**Q: Can I see all the requests that passed through?**

A: Yes! Check logs in the gateway terminal window. They show every validated request.

---

## 🚀 Next Steps

1. **Verify gateway works** - Run tests above
2. **Update Postman collection** - Add gateway URL to all requests
3. **Commit to git** - Save your progress
4. **Plan Phase 2** - Add Resilience4j (circuit breakers, retries)

