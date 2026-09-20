# Postman Collection Guide - With API Gateway & Circuit Breaker

## 📋 Overview

This collection tests the **complete payment platform** through the **API Gateway** with:
- ✅ JWT authentication
- ✅ Rate limiting (100 req/min per user)
- ✅ Circuit breaker on backend calls
- ✅ Routing to all 5 microservices
- ✅ Failure scenarios

---

## 🚀 Getting Started

### 1. Import Collection

1. Open Postman
2. File → Import
3. Select: `postman-collection-gateway.json`
4. Click Import

### 2. Set Base URL

All requests use variable `{{gateway_url}}` which defaults to:
```
http://localhost:8080
```

To change:
- Click on collection name
- Variables tab
- Change `gateway_url` value

### 3. Verify Prerequisites

Before testing, ensure:
- ✅ Gateway running on port 8080
- ✅ All 5 services running (8081-8085)
- ✅ Docker containers running (PostgreSQL, Kafka)

```bash
# Verify
curl http://localhost:8080/actuator/health
```

---

## 📊 Collection Structure

### Folder 1: Setup & JWT Token
- **Generate JWT Token** - Create test token
- **Gateway Health Check** - Verify gateway is running

### Folder 2: Authentication Tests
- **Request WITHOUT JWT** - Should fail (401)
- **Request WITH Invalid JWT** - Should fail (401)
- **Request WITH Valid JWT** - Should succeed (200)

### Folder 3: Account Service
- Get Balance
- Get Ledger
- Debit Account
- Credit Account

### Folder 4: Payment Service
- Create Payment (Happy Path)
- Create Payment (Invalid - Should fail)
- Get Payment Status

### Folder 5: Fraud Service
- Assess Risk

### Folder 6: Rate Limiting
- Rapid Fire Requests
- Check Retry-After Header

### Folder 7: Circuit Breaker
- Normal Payment (Circuit CLOSED)
- Payment When Account Service Down (Circuit OPEN)
- Circuit Breaker State Check

### Folder 8: Transaction History
- Get Transaction History
- Get Transactions for Account

### Folder 9: Notifications
- Get Notifications

### Folder 10: End-to-End Payment Flow
- Check Initial Balances
- Create Payment
- Check Payment Status
- Check Final Balances
- View Transaction History

---

## 🔐 JWT Authentication Setup

### Using Pre-Generated Token

For testing, we use a pre-generated test token. To set it up:

1. Go to **Folder 01: Setup & JWT Token**
2. Run **Generate JWT Token**
3. Token is stored in `{{jwt_token}}` variable
4. All other requests use: `Authorization: Bearer {{jwt_token}}`

### Generate Your Own Token

If you want to generate tokens with your own logic:

**Option 1: Using JWT.io**
1. Go to https://jwt.io
2. Create token with payload:
```json
{
  "sub": "test-user",
  "roles": ["USER", "ADMIN"],
  "exp": 9999999999
}
```
3. Secret: `your-super-secret-key-that-is-at-least-32-characters-long-for-hs256`
4. Copy token
5. Paste in `jwt_token` variable

**Option 2: Using Node.js**
```javascript
const jwt = require('jsonwebtoken');

const token = jwt.sign(
  { sub: 'test-user', roles: ['USER', 'ADMIN'] },
  'your-super-secret-key-that-is-at-least-32-characters-long-for-hs256',
  { expiresIn: '1h' }
);

console.log(token);
```

---

## 🧪 Testing Workflow

### Quick Test (5 minutes)

1. **Setup**
   - Ensure gateway running
   - Generate JWT token

2. **Test Authentication**
   - Run: "Request WITHOUT JWT" → Should get 401
   - Run: "Request WITH Valid JWT" → Should get 200

3. **Test Routing**
   - Run: "Get Account Balance" → Routes to account-service
   - Run: "Create Payment" → Routes to payment-service

**Expected:** All pass ✅

### Full Test (30 minutes)

1. **Setup** (Folder 01)
   - Generate JWT Token

2. **Authentication** (Folder 02)
   - Test without JWT
   - Test with invalid JWT
   - Test with valid JWT

3. **Account Operations** (Folder 03)
   - Get Balance
   - Get Ledger
   - Credit Account
   - Debit Account

4. **Payment Flow** (Folder 04 + Folder 10)
   - Create Payment
   - Check Status
   - View in Transaction History

5. **Rate Limiting** (Folder 06)
   - Send request rapidly 100+ times
   - See 429 Too Many Requests
   - Check Retry-After header

6. **Circuit Breaker** (Folder 07)
   - Normal payment (works)
   - Payment with service down (fails fast)

---

## 🔍 Testing by Feature

### JWT Authentication

**Test 1: No JWT Token**
```
GET /api/v1/accounts/123/balance
(No Authorization header)

Expected: 401 Unauthorized
```

**Test 2: Invalid JWT Token**
```
GET /api/v1/accounts/123/balance
Authorization: Bearer INVALID_TOKEN

Expected: 401 Unauthorized
```

**Test 3: Valid JWT Token**
```
GET /api/v1/accounts/123/balance
Authorization: Bearer {{jwt_token}}

Expected: 200 OK
```

---

### Routing

**All routes tested through gateway:**

```
/api/v1/accounts/**      → account-service:8081
/api/v1/payments/**      → payment-service:8083
/api/v1/risk/**          → fraud-service:8082
/api/v1/notifications/** → notification-service:8084
/api/v1/transactions/**  → transaction-history-service:8085
```

**Test:**
1. Run "Get Account Balance"
2. Run "Create Payment"
3. Run "Assess Risk"
4. All should route correctly

---

### Rate Limiting

**Setup:**
1. Go to **Folder 06: Rate Limiting**
2. Run "Rapid Fire Requests" → Copies request to clipboard
3. In Postman, click Send repeatedly (or use runner)

**Expected:**
- Requests 1-100: 200 OK
- Request 101+: 429 Too Many Requests
- After 60 seconds: Back to 200 OK

**Check Retry-After:**
```
HTTP 429 Too Many Requests
Header: Retry-After: 60

Means: Try again after 60 seconds
```

---

### Circuit Breaker

**Scenario 1: Service is Healthy (Circuit CLOSED)**

1. Ensure account-service running
2. Run "Normal Payment" (Folder 07)
3. Expected: 200 OK or 201 Created

**Scenario 2: Service is Down (Circuit OPEN)**

1. Stop account-service:
   ```bash
   # Close the account-service window in start-all.bat
   # Or: taskkill /FI "WINDOWTITLE eq account-service*"
   ```

2. Run "Payment When Account Service Down"

3. Expected: Behavior
   - First 5 requests: Will fail (trying to call down service)
   - Request 6+: 503 Service Unavailable (circuit opens, fail fast)
   - Response time: ~100ms (fast fail instead of 2s timeout)

**Why Circuit Breaker Matters:**

```
Without Circuit Breaker:
  Request 1 → Account Service DOWN → Wait 2s timeout → Fail
  Request 2 → Account Service DOWN → Wait 2s timeout → Fail
  Request 3 → Account Service DOWN → Wait 2s timeout → Fail
  ...
  Total time: 2s × 3 = 6 seconds per request

With Circuit Breaker:
  Request 1 → Account Service DOWN → Retry → Wait 2s → Fail
  Request 2 → Account Service DOWN → Retry → Wait 2s → Fail
  Request 3 → Account Service DOWN → Retry → Wait 2s → Fail
  Request 4 → Account Service DOWN → Retry → Wait 2s → Fail
  Request 5 → Account Service DOWN → Retry → Wait 2s → Fail
  Request 6 → Circuit OPEN → Fail fast → 100ms ✓
  Request 7 → Circuit OPEN → Fail fast → 100ms ✓
  Request 8 → Circuit OPEN → Fail fast → 100ms ✓

Result: Requests 6+ are 20x faster!
```

---

## 📈 End-to-End Payment Flow

**Complete flow testing:**

1. **Check Initial Balance**
   - Account 1 balance: $1000

2. **Create Payment**
   - From Account 1 → Account 2
   - Amount: $25

3. **Check Payment Status**
   - Payment ID: abc123
   - Status: PENDING or COMPLETED

4. **Check Final Balance**
   - Account 1 balance: $975 (1000 - 25)

5. **View Transaction History**
   - See the payment transaction

---

## 🎓 Understanding Test Results

### Status Codes

| Code | Meaning | Scenario |
|------|---------|----------|
| 200 | OK | Request succeeded |
| 201 | Created | Resource created |
| 400 | Bad Request | Invalid data (e.g., negative amount) |
| 401 | Unauthorized | No/invalid JWT token |
| 404 | Not Found | Resource doesn't exist |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Backend error |
| 503 | Service Unavailable | Circuit breaker open |

### Response Headers

| Header | Meaning |
|--------|---------|
| `Authorization` | JWT token |
| `X-User-Id` | User ID (added by gateway) |
| `X-User-Roles` | User roles (added by gateway) |
| `Retry-After` | When to retry (on 429) |

---

## 🐛 Troubleshooting

### Problem: "Connection refused" on port 8080

**Solution:**
1. Verify gateway is running: `curl http://localhost:8080/actuator/health`
2. Start gateway: `cd api-gateway-service && mvnw.cmd spring-boot:run`
3. Wait 10 seconds for startup

### Problem: "401 Unauthorized" on all requests

**Solution:**
1. Check `{{jwt_token}}` is set: Click Variables tab
2. Token should be long string starting with `eyJ`
3. Regenerate token if empty

### Problem: "404 Not Found" even with valid JWT

**Solution:**
1. Check backend service is running
2. Verify route pattern in GatewayConfig.java
3. Check URL path matches pattern (e.g., `/api/v1/accounts/**`)

### Problem: Rate limit not triggering

**Solution:**
1. Rate limit is 100 requests **per minute per user**
2. Run "Rapid Fire Requests" using Postman Runner:
   - Select request
   - Click "..." → Run
   - Set iterations to 105
   - Send quickly
3. Requests 101-105 should get 429

### Problem: Circuit breaker not opening

**Solution:**
1. Ensure account-service is stopped
2. Run payment request 5+ times
3. After 5 failures, circuit opens
4. Next request should fail quickly (503)

---

## 💡 Pro Tips

### Using Postman Environment

Instead of collection variables, use Postman Environments:

1. New Environment: `Gateway-Prod`
2. Add variables:
   - `gateway_url`: `http://localhost:8080`
   - `jwt_token`: `your-token-here`
3. Select environment at top-right
4. All requests use environment variables

### Batch Testing with Postman Runner

1. Select folder (e.g., "Authentication Tests")
2. Click "..." → Run
3. Set iterations and delays
4. View results

### Monitoring Circuit Breaker

Check circuit breaker state:

```bash
curl http://localhost:8083/actuator/health
```

Look for `circuitbreakers` section in response.

### Load Testing

Use Postman Runner to stress test:

1. Select "Rapid Fire Requests"
2. Runner: 1000 iterations
3. Delay: 0ms
4. Watch rate limiter in action

---

## 🔄 Variables Reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `gateway_url` | `http://localhost:8080` | Gateway base URL |
| `jwt_token` | `empty` | JWT token for auth |
| `jwt_secret` | `your-secret-key...` | Secret for token generation |
| `account_id_1` | UUID | First test account |
| `account_id_2` | UUID | Second test account |
| `idempotency_key` | Random GUID | Prevents duplicate payments |

---

## 📚 Request Details

### Create Payment Request

```
POST /api/v1/payments
Authorization: Bearer {{jwt_token}}
Content-Type: application/json
Idempotency-Key: {{idempotency_key}}

{
  "payerAccountId": "{{account_id_1}}",
  "payeeAccountId": "{{account_id_2}}",
  "amount": 50.00,
  "currency": "USD",
  "description": "Payment for services"
}
```

**Why Idempotency-Key?**
- Prevents duplicate payments if request sent twice
- Gateway deduplicates by this key
- Safe to retry

### Rate Limit Request

```
GET /api/v1/accounts/123/balance
Authorization: Bearer {{jwt_token}}
```

**Rate Limit Rules:**
- 100 requests per minute per user
- User ID: Extracted from JWT token (sub claim)
- Returns 429 if exceeded
- Includes `Retry-After: 60` header

---

## 🎯 Next Steps

1. **Test basic flow** - Run "End-to-End Payment Flow"
2. **Test rate limiting** - Rapid fire 105 requests
3. **Test circuit breaker** - Stop account-service and create payment
4. **Test authentication** - Try without JWT
5. **Monitor logs** - Check payment-service logs for circuit breaker state changes

---

## 📖 Related Documentation

- **GATEWAY-QUICK-START.md** - Testing the gateway directly
- **GATEWAY-WALKTHROUGH.md** - Understanding gateway internals
- **GATEWAY-IMPLEMENTATION.md** - Concepts and patterns
- **POSTMAN-GUIDE.md** - Old collection (without gateway)

---

**Last Updated:** 2026-09-21  
**Collection Version:** 2.0 (With Gateway & Circuit Breaker)

