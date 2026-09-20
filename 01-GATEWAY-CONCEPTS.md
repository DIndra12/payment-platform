# 01 - API Gateway & Resilience Concepts

## 📚 What You'll Learn

This guide explains the **why** behind API Gateways, JWT authentication, rate limiting, and circuit breakers. Read this to understand the architectural patterns before diving into code.

---

## 🌍 API Gateway Pattern

### What is an API Gateway?

An **API Gateway** is a **central entry point** for all client requests:

```
Clients (Web, Mobile, External)
        ↓ HTTP Request
    API Gateway (Port 8080)
        ├─ Check: Are you authenticated? (JWT)
        ├─ Check: Are you rate limited? (Token bucket)
        ├─ Check: Which service should handle this?
        └─ Forward to correct backend service
            ↓
    Microservices (Account, Payment, Fraud, etc)
```

### Why Do We Need It?

| Problem | Solution |
|---------|----------|
| Clients need to know all service URLs | Gateway provides single entry point |
| No authentication layer | Gateway validates JWT tokens |
| Services get hammered with requests | Gateway rate limits clients |
| Services are exposed to internet | Gateway acts as firewall |
| No audit trail | Gateway logs all requests |

### Real-World Example

```
Without Gateway:
  User → [Need Account Service URL]
      → User → [Need Payment Service URL]
      → User → [Need Fraud Service URL]
  (Complex, insecure, no limits)

With Gateway:
  User → http://localhost:8080 (only one URL)
      → Gateway figures out which service
      → Gateway enforces security
      → Gateway enforces rate limits
```

---

## 🔐 JWT Authentication

### What is JWT?

**JWT = JSON Web Token**. It's a **signed ID card** that proves who you are.

Structure:
```
Header . Payload . Signature
```

Example decoded:
```json
Header: {
  "typ": "JWT",
  "alg": "HS256"
}

Payload: {
  "sub": "user123",
  "roles": ["USER", "ADMIN"],
  "exp": 1625000000,
  "iat": 1624996400
}

Signature: HMACSHA256(header.payload, SECRET_KEY)
```

### How JWT Works

```
1. User logs in with username/password
   POST /auth/login
   {username: "alice", password: "secret"}

2. Server verifies credentials (check database)

3. Server creates JWT with user info
   JWT = sign({sub: "alice", roles: ["USER"]}, SECRET)

4. Server sends JWT to client
   {token: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}

5. Client stores JWT (browser localStorage, app storage)

6. Client sends JWT with every request
   GET /api/accounts/123
   Authorization: Bearer eyJhbGci...

7. Gateway receives request

8. Gateway validates JWT:
   a. Extract token from Authorization header
   b. Recalculate signature: HMACSHA256(header.payload, SECRET)
   c. Compare with provided signature
   d. If match → Token is valid (not forged)
   e. If no match → Token is invalid (forged, corrupted, etc)
   f. Check expiration: is exp > now?
   g. Extract claims: sub (user), roles, etc

9. Gateway adds user info to request headers
   X-User-Id: alice
   X-User-Roles: USER

10. Gateway forwards to backend service

11. Backend service reads X-User-Id header
    and knows who the user is (no database lookup!)

12. Response sent back to client
```

### Why is JWT Secure?

**The signature prevents tampering:**

```
Scenario 1: Valid token
  Token:  header.payload.correctSignature
  Check:  HMACSHA256(header.payload, SECRET) = correctSignature ✓
  Result: VALID - Allow request

Scenario 2: Forged token (attacker changed payload)
  Forged: header.payload_HACKED.correctSignature
  Check:  HMACSHA256(header.payload_HACKED, SECRET) ≠ correctSignature ✗
  Result: INVALID - Reject with 401

Scenario 3: Expired token (time passed)
  Token:  header.payload.validSignature
  Check:  Signature is valid ✓
          BUT: exp = 1600000000, now = 1625000000 ✗
  Result: INVALID (expired) - Reject with 401
```

**Key insight:** Only the server knows the SECRET. So only the server can create valid signatures.

### Token Claims

Claims are fields inside the JWT:

```json
{
  "sub": "user123",           // Subject (who) - required
  "roles": ["USER", "ADMIN"], // Permissions - custom
  "exp": 1625000000,          // Expiration time
  "iat": 1624996400,          // Issued at time
  "iss": "payment-platform"   // Issuer
}
```

---

## 🚦 Rate Limiting

### Why Rate Limiting?

```
Scenario 1: Without rate limiting
  Attacker: curl -L http://localhost:8080/api/... (1000 times/sec)
  Result:   Server gets overwhelmed
            Legitimate users can't access
            Denial of Service (DoS) attack!

Scenario 2: With rate limiting (100 requests/minute)
  Attacker: curl -L http://localhost:8080/api/... (1000 times/sec)
  Result:   First 100 requests succeed
            Request 101+: 429 Too Many Requests
            Attacker rate limited
            Legitimate users still OK
```

### Token Bucket Algorithm

**Mental model:** Each user has a bucket of tokens.

```
Time 0:
┌─────────────────────────────┐
│ Bucket: 100 tokens ✓✓✓✓✓... │
└─────────────────────────────┘

Request 1 arrives:
├─ Does bucket have token? YES
├─ Remove 1 token
└─ Allow request
   Bucket: 99 tokens left

Request 2 arrives:
├─ Does bucket have token? YES
├─ Remove 1 token
└─ Allow request
   Bucket: 98 tokens left

...repeat 98 times...

Request 100 arrives:
├─ Does bucket have token? YES
├─ Remove 1 token
└─ Allow request
   Bucket: 0 tokens left

Request 101 arrives:
├─ Does bucket have token? NO ✗
└─ REJECTED: 429 Too Many Requests
   "Retry-After: 60"

Time 60 seconds pass:
┌─────────────────────────────┐
│ Bucket: 100 tokens ✓✓✓✓✓... │ (Refilled!)
└─────────────────────────────┘

Request 102 arrives:
├─ Does bucket have token? YES
├─ Remove 1 token
└─ Allow request
```

### Per-User Limiting

**Each user has their own bucket:**

```
User A:
├─ Bucket: 100 tokens
├─ Used: 95 tokens
└─ Remaining: 5 tokens

User B:
├─ Bucket: 100 tokens
├─ Used: 10 tokens
└─ Remaining: 90 tokens

User A makes request 96: REJECTED (0 tokens)
User B makes request 11: ALLOWED (89 tokens left)
```

**Key insight:** Rate limit is per user, not global. One power user can't affect other users.

---

## 🔌 Circuit Breaker Pattern

### The Problem: Cascading Failures

```
Account Service is having issues (slow responses)

Payment Service (trying to call Account Service):
├─ Request 1: Wait 2 seconds... Timeout ✗
├─ Request 2: Wait 2 seconds... Timeout ✗
├─ Request 3: Wait 2 seconds... Timeout ✗
├─ Request 4: Wait 2 seconds... Timeout ✗
├─ Request 5: Wait 2 seconds... Timeout ✗

After 5 failures:
├─ Thread pool exhausted (threads all waiting)
├─ Payment Service becomes slow
├─ Other services affected too (cascading failure!)
```

### The Solution: Circuit Breaker

**A circuit breaker** has **3 states:**

#### State 1: CLOSED (Normal)
```
Payment → Account Service: Working ✓
Circuit status: CLOSED (allow all calls)
Money debited successfully
```

#### State 2: OPEN (Service Down)
```
Payment → Account Service: Failing ✗
  Attempt 1: Timeout
  Attempt 2: Timeout
  Attempt 3: Timeout
  Attempt 4: Timeout
  Attempt 5: Timeout
Circuit opens (stop calling!)

Payment → Account Service: BLOCKED ✗
Circuit status: OPEN
Response: Fail immediately (don't wait)
Time to respond: 100ms instead of 2000ms
```

#### State 3: HALF-OPEN (Testing Recovery)
```
After 30 seconds of OPEN state:
Circuit enters HALF-OPEN (test if service recovered)

Payment → Account Service: TRY ONE CALL ✓
  Result: SUCCESS!
  
Circuit changes to CLOSED (service is back)
```

### Flow Diagram

```
     Request
        ↓
    ┌───────┐
    │CLOSED │  (Allow all calls)
    └───┬───┘
        ↓
   Call succeeds? YES → Continue
        ↓NO (failure)
   Failure count++
        ↓
   Failure count >= 5?
        NO → Continue as CLOSED
        YES ↓
    ┌───────┐
    │ OPEN  │  (Block all calls - fail fast!)
    └───┬───┘
        ↓
   Wait 30 seconds
        ↓
    ┌──────────┐
    │HALF-OPEN│  (Try one test call)
    └───┬──────┘
        ↓
   Test call succeeds? 
        YES ↓
    ┌───────┐
    │CLOSED │  (Reopen - service recovered)
    └───────┘
        NO ↓
    ┌───────┐
    │ OPEN  │  (Close again - still failing)
    └───────┘
```

### Real-World Timing

```
Without Circuit Breaker:
  Request 1: 2000ms (timeout)
  Request 2: 2000ms (timeout)
  Request 3: 2000ms (timeout)
  Request 4: 2000ms (timeout)
  Request 5: 2000ms (timeout)
  Request 6: 2000ms (timeout) ← Thread pool exhausted!
  Request 7: Can't respond (no threads available)
  Request 8: Can't respond (no threads available)
  Total damage: System becomes unresponsive

With Circuit Breaker:
  Request 1: 2000ms (timeout) - Failure #1
  Request 2: 2000ms (timeout) - Failure #2
  Request 3: 2000ms (timeout) - Failure #3
  Request 4: 2000ms (timeout) - Failure #4
  Request 5: 2000ms (timeout) - Failure #5
  Circuit OPENS ↓
  Request 6: 100ms ✓ (fail fast!)
  Request 7: 100ms ✓ (fail fast!)
  Request 8: 100ms ✓ (fail fast!)
  Total damage: Minimal (fail fast prevents cascading)
```

---

## 🔄 Service Discovery

### Current Approach: Hardcoded with Environment Variables

**Why we hardcode now:**
- Development is local (all services on localhost)
- Ports are fixed (8081, 8082, 8083, etc)
- Works perfectly with environment variables

**How it works:**
```yaml
# application.yml
external-services:
  account-service-url: ${ACCOUNT_SERVICE_URL:http://localhost:8081}
                       ↑ Environment variable with default
```

```bash
# Local development (uses default)
java -jar payment-service.jar
# Connects to: http://localhost:8081

# Docker Compose (override via env var)
docker run -e ACCOUNT_SERVICE_URL=http://account-service:8081 payment-service
# Connects to: http://account-service:8081 (Docker DNS)

# Kubernetes (Phase 4 - future)
docker run -e ACCOUNT_SERVICE_URL=http://account-service:8081 payment-service
# Connects to: http://account-service:8081 (Kubernetes DNS)
```

### Future Approach: Kubernetes Service Discovery (Phase 4)

When we deploy to Kubernetes:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: account-service
spec:
  selector:
    app: account-service
  ports:
    - port: 8081
```

```
Payment Service calls: http://account-service:8081
Kubernetes DNS resolves to:
  ├─ Pod #1 (10.244.1.5:8081) ✓
  ├─ Pod #2 (10.244.1.6:8081) ✓
  └─ Pod #3 (10.244.1.7:8081) ✓
```

Kubernetes automatically:
- Discovers all pods
- Load balances between them
- Removes unhealthy pods
- Adds new pods when scaling

**Same code, different infrastructure!**

---

## 🎯 Key Takeaways

1. **API Gateway** = Single entry point with security & rate limiting
2. **JWT** = Secure, stateless authentication (signed tokens)
3. **Rate Limiting** = Token bucket algorithm to prevent abuse
4. **Circuit Breaker** = Resilience pattern to prevent cascading failures
5. **Service Discovery** = Starts hardcoded, evolves to Kubernetes (Phase 4)

---

## 📖 Next Steps

- **Want to see code?** → Read **02-CODE-WALKTHROUGH.md**
- **Want to test it?** → Read **03-TESTING-GUIDE.md**
- **Want to run it?** → Read **04-SETUP-AND-RUN.md**
- **Want system design?** → Read **05-ARCHITECTURE.md**
