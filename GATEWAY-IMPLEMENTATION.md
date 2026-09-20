# Phase 1: API Gateway Implementation - Step-by-Step

## 🎯 What We're Building

An **API Gateway** that sits between clients and microservices:

```
Client (Browser/App)
        ↓ HTTP Request
API Gateway (Port 8080)
   ├─ Check JWT Token (Are you who you say you are?)
   ├─ Check Permissions (What are you allowed to do?)
   ├─ Rate Limit (Don't make too many requests)
   └─ Route to correct service
        ↓
   [Account] [Payment] [Fraud] services
```

---

## 📚 Understanding Spring Cloud Gateway

### What is Spring Cloud Gateway?

Spring Cloud Gateway is a **routing library** that:
1. **Listens for HTTP requests** on port 8080
2. **Routes them** to the correct microservice
3. **Adds security** through filters
4. **Logs everything** for audit trails

### Key Concepts:

**Routes:** Rules that say "if path is X, send to service Y"
```
Pattern: /api/v1/accounts/** → account-service:8081
Pattern: /api/v1/payments/** → payment-service:8083
Pattern: /api/v1/risk/**     → fraud-service:8082
```

**Filters:** Intercept requests/responses to add logic
```
- Authentication Filter: Check JWT token
- Rate Limit Filter: Limit requests per user
- Logging Filter: Log all requests
- CORS Filter: Allow cross-origin requests
```

---

## 🔧 Step 1: Create API Gateway Service

### What We're Creating

A new Spring Boot service that will act as the central entry point.

### File Structure

```
api-gateway-service/
├── pom.xml                           # Dependencies
├── src/
│   ├── main/
│   │   ├── java/com/payments/platform/apigateway/
│   │   │   ├── ApiGatewayApplication.java    # Entry point
│   │   │   ├── config/
│   │   │   │   └── GatewayConfig.java        # Route configuration
│   │   │   ├── filter/
│   │   │   │   └── JwtAuthenticationFilter.java  # JWT validation
│   │   │   └── exception/
│   │   │       └── UnauthorizedAccessException.java
│   │   └── resources/
│   │       ├── application.yml        # Configuration
│   │       └── db/migration/          # Flyway migrations (empty for gateway)
│   └── test/
│       ├── unit/                      # Unit tests
│       ├── integration/               # Integration tests
│       └── acceptance/                # E2E tests
└── .mvn/wrapper/                      # Maven wrapper
```

### Why This Structure?

- **Separation of concerns:** Each package has a single responsibility
- **Easy to test:** Tests organized by layer
- **Production ready:** Follows enterprise patterns
- **Scalable:** Easy to add new routes/filters

---

## 📦 Step 2: Dependencies (pom.xml)

### What Dependencies We Need

```xml
<!-- Spring Cloud Gateway (the routing library) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>

<!-- Spring Security + JWT (authentication) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT library (encode/decode tokens) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
</dependency>

<!-- Spring Boot Web (REST support) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Why Each One?

| Dependency | Purpose | What It Does |
|-----------|---------|-------------|
| `spring-cloud-gateway` | Routing | Routes requests to microservices |
| `spring-security` | Authentication | Validates JWT tokens |
| `jjwt` | JWT Handling | Encodes/decodes JWT tokens |
| `webflux` | Async HTTP | Handles thousands of concurrent requests |

**Why webflux instead of web?**
- Gateway handles lots of concurrent requests
- webflux is async (non-blocking)
- Can handle 10,000+ requests at once
- web is synchronous (blocking) - each request needs a thread

---

## 🔐 Step 3: JWT Authentication Explained

### What is JWT?

JWT = JSON Web Token. It's like a **signed ID card**:

```
Header: {
  "typ": "JWT",
  "alg": "HS256"      ← Algorithm used to sign
}

Payload: {
  "sub": "user123",   ← Subject (who)
  "exp": 1234567890,  ← Expiration time
  "iat": 1234567890,  ← Issued at time
  "roles": ["user"]   ← Permissions
}

Signature: HMACSHA256(header.payload, SECRET_KEY)
           ← Can't be forged without SECRET_KEY
```

### How It Works

```
1. User logs in with username/password
2. Server verifies credentials
3. Server creates JWT with user info
4. Server sends JWT to client

5. Client sends JWT with each request:
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

6. Gateway receives request
7. Gateway validates JWT signature
8. Gateway checks expiration time
9. Gateway extracts user info
10. Gateway adds user to request context
11. Gateway routes to microservice
```

### Why Is This Secure?

- **Can't be forged:** Signature requires SECRET_KEY
- **Can't be modified:** If you change the payload, signature becomes invalid
- **Time-limited:** Tokens expire after N minutes
- **No server storage needed:** Just validate the signature

---

## 🛣️ Step 4: Route Configuration

### What Routes We Need

```
External URL → Internal Service

GET    /api/v1/accounts/**      → account-service:8081
GET    /api/v1/payments/**      → payment-service:8083
POST   /api/v1/payments         → payment-service:8083
GET    /api/v1/risk/**          → fraud-service:8082
GET    /api/v1/transactions/**  → transaction-history-service:8085
```

### How Gateway Routes Requests

```
Request comes in: GET /api/v1/accounts/123/balance
        ↓
Gateway checks routes:
   - Does it match /api/v1/accounts/**? YES ✓
   - Which service? account-service:8081
   - Add JWT validation filter? YES
   - Rate limit? YES (100 req/min)
        ↓
Forward to: http://account-service:8081/api/v1/accounts/123/balance
        ↓
Response comes back
        ↓
Return to client
```

---

## 🧪 Step 5: Testing the Gateway

### Unit Tests (Fast, Mocked)

Test individual components:
- JWT token validation logic
- Route matching logic
- Filter execution

```java
@Test
void testValidJwtTokenAccepted() {
    String token = createValidJwt();
    assertTrue(jwtValidator.isValid(token));
}

@Test
void testExpiredJwtTokenRejected() {
    String token = createExpiredJwt();
    assertFalse(jwtValidator.isValid(token));
}
```

### Integration Tests (Medium, With Real Gateway)

Test gateway behavior with real HTTP:
- Requests with valid JWT pass through
- Requests without JWT return 401
- Requests are routed to correct service
- Rate limiting works

```java
@Test
void testRequestWithoutJwtReturns401() {
    ResponseEntity response = restTemplate.getForEntity(
        "http://localhost:8080/api/v1/accounts/123",
        String.class
    );
    assertEquals(401, response.getStatusCode());
}

@Test
void testRequestWithJwtRoutesToService() {
    String token = createValidJwt();
    ResponseEntity response = restTemplate.exchange(
        "http://localhost:8080/api/v1/accounts/123",
        HttpMethod.GET,
        headers(token),
        String.class
    );
    assertEquals(200, response.getStatusCode());
}
```

### Acceptance Tests (Slow, Full E2E)

Test complete flows:
- Create payment with JWT
- Verify payment appears in history
- Check notifications sent

---

## 🚀 Implementation Stages

We'll build this in stages:

### Stage 1: Basic Gateway (Days 1-2)
✅ Create service  
✅ Add routes to all 5 services  
✅ Test requests pass through  
❌ No authentication yet

### Stage 2: Add JWT (Days 3-4)
✅ Add JWT validation  
✅ Reject requests without JWT  
✅ Extract user info from token  
❌ No rate limiting yet

### Stage 3: Add Rate Limiting (Day 5)
✅ Implement rate limiter  
✅ Return 429 when limit exceeded  
✅ Include retry-after header

### Stage 4: Complete Testing (Week 2)
✅ Update Postman collection  
✅ Test all scenarios  
✅ Verify security works

---

## 📝 Configuration (application.yml)

```yaml
server:
  port: 8080  # Gateway listens on this port

spring:
  cloud:
    gateway:
      routes:
        # Route to Account Service
        - id: account-service
          uri: http://account-service:8081
          predicates:
            - Path=/api/v1/accounts/**
          filters:
            - AuthenticationFilter
            - RateLimitFilter

        # Route to Payment Service
        - id: payment-service
          uri: http://payment-service:8083
          predicates:
            - Path=/api/v1/payments/**
          filters:
            - AuthenticationFilter
            - RateLimitFilter

        # etc for other services

jwt:
  secret: your-secret-key-here-min-32-chars
  expiration: 3600000  # 1 hour in milliseconds
```

---

## 🔑 Key Learnings from This Phase

After building the gateway, you'll understand:

1. **How requests flow through a distributed system**
   - Client → Gateway → Microservice

2. **How authentication works**
   - JWT tokens carry user info
   - Signature prevents tampering
   - Tokens expire for security

3. **How to control access**
   - Rate limiting prevents abuse
   - JWT claims grant permissions
   - Filters intercept all requests

4. **Why gateways are essential**
   - Single entry point
   - Centralized security
   - Easy to add features (logging, metrics)
   - Services don't handle auth directly

---

## ✅ Success Criteria

Gateway is complete when:
- ✅ All requests route to correct services
- ✅ Requests without JWT return 401
- ✅ Valid JWT allows request to proceed
- ✅ Rate limiting works (tested with Postman)
- ✅ All tests pass (unit + integration + acceptance)
- ✅ Can call all 5 services through gateway

---

## 🎯 Next: Let's Build It!

Ready to start coding? I'll:

1. **Create the service structure**
2. **Explain each file as we write it**
3. **Show you how each piece works**
4. **Help you understand the complete flow**

Let's begin! 🚀
