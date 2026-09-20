# 08 - Postman Collection Guide

## 📦 Collection File

**Location:** `postman-collection-gateway.json`  
**Requests:** 50+  
**Folders:** 10  
**Coverage:** All gateway features + all microservices

---

## 🚀 Quick Start with Postman

### 1. Import Collection
```
File → Import → Select: postman-collection-gateway.json
```

### 2. Generate JWT Token
```
Folder: "Setup & Variables"
Request: "Generate JWT Token"
Click: Send
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIiwicm9sZXMiOlsiVVNFUiJdLCJleHAiOjE2MzI0NTY3ODB9.xxx",
  "userId": "user123",
  "expiresIn": 3600
}
```

### 3. Use Token in Requests
Token automatically saved in Postman variable `{{authToken}}`

---

## 📂 Folder Breakdown

### Folder 1: Setup & Variables
**Purpose:** Generate and manage JWT tokens

| Request | What It Does |
|---------|-------------|
| Generate JWT Token | Creates token for user123 (role: USER) |
| Generate Admin Token | Creates token for admin1 (role: ADMIN) |
| Generate Expired Token | Creates expired token (tests expiration) |

**Output:** Token saved to `{{authToken}}` variable for other requests

---

### Folder 2: Authentication Tests
**Purpose:** Verify JWT validation works correctly

| Request | Expected Response |
|---------|-------------------|
| Valid Token - Should Pass | 404 (no backend, but auth OK) |
| No Token - Should Fail | 401 Unauthorized |
| Invalid Token - Should Fail | 401 Unauthorized |
| Malformed Header - Should Fail | 401 Unauthorized |
| Expired Token - Should Fail | 401 Unauthorized |

---

### Folder 3: Rate Limiting Tests
**Purpose:** Verify rate limiting works (100 req/min per user)

| Request | What It Does |
|---------|-------------|
| Make 1 Request | Should: 200/404 |
| Make 100 Requests (Loop) | All should: 200/404 |
| Make 101st Request | Should: 429 Too Many Requests |
| Reset and Try Again | Should: 200/404 (bucket refilled) |
| Different User - Not Limited | Should: 200/404 (separate bucket) |

---

### Folder 4: Account Service Tests
**Purpose:** Test routing to Account Service (port 8081)

| Request | Endpoint | Expected |
|---------|----------|----------|
| Get Account Balance | GET /api/v1/accounts/{id} | 200 or 404 |
| Debit Account | POST /api/v1/accounts/{id}/debit | 200 or 404 |
| Credit Account | POST /api/v1/accounts/{id}/credit | 200 or 404 |

---

### Folder 5: Payment Service Tests
**Purpose:** Test routing to Payment Service (port 8083)

| Request | Endpoint | Expected |
|---------|----------|----------|
| Create Payment | POST /api/v1/payments | 200 or 404 |
| Get Payment Status | GET /api/v1/payments/{id} | 200 or 404 |
| List Payments | GET /api/v1/payments | 200 or 404 |

---

### Folder 6: Fraud Service Tests
**Purpose:** Test routing to Fraud Service (port 8082)

| Request | Endpoint | Expected |
|---------|----------|----------|
| Assess Risk | POST /api/v1/fraud/assess | 200 or 404 |

---

### Folder 7: Circuit Breaker Tests
**Purpose:** Verify Resilience4j behavior

| Request | What It Tests |
|---------|--------------|
| Normal Request | Circuit CLOSED (normal) |
| Simulate Failures | Circuit OPEN (stops requests) |
| Wait 30 Seconds | Circuit HALF-OPEN (testing recovery) |
| Successful Request | Circuit CLOSED (recovery complete) |

---

### Folder 8: Gateway Health Checks
**Purpose:** Verify gateway is running

| Request | Expected |
|---------|----------|
| Health Check | {"status":"UP"} |
| Metrics | Various metrics |

---

### Folder 9: Error Scenarios
**Purpose:** Test error handling

| Request | Expected Status |
|---------|-----------------|
| Invalid Path | 404 Not Found |
| Missing Required Header | 401 Unauthorized |
| Service Timeout | 504 Gateway Timeout |
| Service Down | 503 Service Unavailable |

---

### Folder 10: End-to-End Flows
**Purpose:** Complete user journeys

| Request | Scenario |
|---------|----------|
| Complete Payment Flow | User1: Generate token → Make payment → Check status |
| Multiple Users Independent | User1 + User2: Verify separate rate limits |
| Concurrent Requests | 10 users × 10 requests each |

---

## 🔧 Environment Variables

### Available Variables

```
{{baseUrl}}          → http://localhost:8080
{{authToken}}        → JWT token (auto-set by "Generate JWT Token")
{{userId}}           → user123 (auto-set)
{{accountId}}        → random ID for testing
{{paymentId}}        → payment ID from create response
```

### Use in Requests

```
GET {{baseUrl}}/api/v1/accounts/{{accountId}}
Authorization: Bearer {{authToken}}
```

---

## 🚀 Common Testing Workflows

### Workflow 1: Test Authentication
```
1. Setup & Variables → Generate JWT Token → Send
2. Authentication Tests → Valid Token - Should Pass → Send
3. Authentication Tests → No Token - Should Fail → Send
4. See: First passes (404), second fails (401)
```

### Workflow 2: Test Rate Limiting
```
1. Setup & Variables → Generate JWT Token → Send
2. Rate Limiting Tests → Make 100 Requests (Loop) → Send
3. Rate Limiting Tests → Make 101st Request → Send
4. See: 100 requests pass, 101st returns 429
5. Wait 60 seconds
6. Rate Limiting Tests → Make 1 Request → Send
7. See: Passes (bucket refilled)
```

### Workflow 3: Test All Services
```
1. Setup & Variables → Generate JWT Token
2. Account Service Tests → Get Account Balance
3. Payment Service Tests → Create Payment
4. Fraud Service Tests → Assess Risk
5. Verify all get routed correctly (404 = routing OK, backend not running)
```

### Workflow 4: Test Circuit Breaker
```
1. Circuit Breaker Tests → Normal Request → Send (passes)
2. Circuit Breaker Tests → Simulate Failures × 5 → Send
3. Circuit Breaker Tests → Normal Request → Send (fails - circuit OPEN)
4. Wait 30 seconds
5. Circuit Breaker Tests → Successful Request → Send (passes - circuit CLOSED)
```

---

## 📊 Sample Request Structure

### Authentication Header
```
Key:   Authorization
Value: Bearer {{authToken}}
```

### Example: Get Account Balance
```
GET {{baseUrl}}/api/v1/accounts/123
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

Response (200):
{
  "accountId": "123",
  "balance": 1000.00,
  "currency": "USD"
}

Or (404 if backend not running):
{
  "error": "Not found"
}
```

---

## ✅ Pre-Request Scripts

Some requests have pre-request scripts that:
- Generate random account IDs
- Create timestamps
- Set headers

These run automatically before sending.

---

## 📈 Monitoring Responses

### Status Codes to Expect

| Code | Meaning | Common Cause |
|------|---------|-------------|
| 200 | OK | Backend responded successfully |
| 401 | Unauthorized | Invalid/missing JWT |
| 404 | Not Found | Backend not running (OK for testing gateway) |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Backend crashed |
| 503 | Service Unavailable | Backend down |
| 504 | Gateway Timeout | Backend took >2 seconds |

---

## 🐛 Debugging

### Token Expired?
```
1. Setup & Variables → Generate JWT Token → Send
2. Copy new token
3. Use in requests
```

### Rate Limit Still Active?
```
1. Wait 60 seconds (bucket refills)
2. Or restart gateway
```

### Backend Returning Wrong Error?
```
1. Check service is running: curl http://localhost:8081/actuator/health
2. Check service logs (in terminal window)
3. Check request body matches service expectations
```

---

## 📖 Next Steps

- **Want to run tests?** → Go to **03-TESTING-GUIDE.md**
- **Want to setup?** → Go to **04-SETUP-AND-RUN.md**
- **Want to understand architecture?** → Go to **05-ARCHITECTURE.md**

