# API Gateway - Complete Walkthrough

## 📚 Part 1: Understanding the Pieces

Let me walk you through each component of the gateway and explain **why** it's built this way.

---

## 🧩 Component 1: ApiGatewayApplication.java

### What is it?

The **entry point** of the entire gateway service. This is the main class that starts everything.

### Code:
```java
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### How it works:

1. When you run `mvnw.cmd spring-boot:run`, Java executes the `main()` method
2. `SpringApplication.run()` starts the Spring Boot application
3. Spring automatically:
   - Scans for `@Component` classes (like JwtUtil)
   - Scans for `@Configuration` classes (like GatewayConfig)
   - Loads `application.yml` configuration
   - Starts the embedded web server on port 8080

### Why this pattern?

- **Single responsibility:** Entry point is simple
- **Spring manages everything:** Dependency injection, configuration
- **Easy to test:** Can be tested with @SpringBootTest

### What to remember:

> One `@SpringBootApplication` class is all you need to start a Spring Boot service.

---

## 🛣️ Component 2: GatewayConfig.java

### What is it?

The **routing configuration**. This defines where requests go.

### How it works:

When a request arrives, Spring Cloud Gateway checks each route:

```
Request: GET /api/v1/accounts/123

Check Route 1: /api/v1/accounts/** matches /api/v1/accounts/123? ✓ YES!
└─ Forward to: http://account-service:8081/api/v1/accounts/123

Check Route 2: /api/v1/payments/** matches /api/v1/accounts/123? ✗ NO
Check Route 3: ...
```

### The Routes We Defined:

```yaml
/api/v1/accounts/**      → account-service:8081       (Account operations)
/api/v1/payments/**      → payment-service:8083       (Payments)
/api/v1/risk/**          → fraud-service:8082         (Fraud detection)
/api/v1/notifications/** → notification-service:8084  (Notifications)
/api/v1/transactions/**  → transaction-history:8085   (Read model)
```

### Filters Applied to Each Route:

```
For each route above:
  ├─ JwtAuthenticationFilter (validate token)
  └─ RateLimitFilter (check rate limit)

For /actuator/** (health check):
  └─ No filters (allow unauthenticated access)
```

### Why This Order?

**JwtAuthenticationFilter** MUST run first because:
1. Validates JWT token
2. Adds `X-User-Id` header to the request
3. RateLimitFilter needs this header!

### Code Example:

```java
@Bean
public RouteLocator routeLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("account-service", r -> r
            .path("/api/v1/accounts/**")
            .filters(f -> f
                .filter(jwtAuthenticationFilter.apply(...))  // 1st: Auth
                .filter(rateLimitFilter.apply(...))          // 2nd: Rate limit
            )
            .uri("http://account-service:8081")
        )
        // ... more routes ...
        .build();
}
```

### What to remember:

> Routes map URL patterns to backend services. Filters execute in order, so put dependency first.

---

## 🔐 Component 3: JwtAuthenticationFilter.java

### What is it?

A **filter that validates JWT tokens** for every request.

### How it works:

```
Request arrives: GET /api/v1/accounts/123
Header: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
        │
        ├─ Step 1: Extract Authorization header ✓ Found
        │
        ├─ Step 2: Check format ✓ Starts with "Bearer "
        │
        ├─ Step 3: Extract token: "eyJhbGciOi..."
        │
        ├─ Step 4: Validate signature
        │          │
        │          └─ Is signature valid? (not forged?)
        │             ├─ YES: Continue
        │             └─ NO: Return 401 Unauthorized
        │
        ├─ Step 5: Check expiration
        │          │
        │          └─ Has token expired?
        │             ├─ NO: Continue
        │             └─ YES: Return 401 Unauthorized
        │
        ├─ Step 6: Extract user info
        │          └─ User ID: "user123"
        │          └─ Roles: ["USER", "ADMIN"]
        │
        ├─ Step 7: Add to headers for downstream services
        │          ├─ X-User-Id: user123
        │          └─ X-User-Roles: USER,ADMIN
        │
        └─ Step 8: Forward request to backend service
           └─ GET http://account-service:8081/api/v1/accounts/123
              Headers include X-User-Id, X-User-Roles, etc.
```

### JWT Token Structure:

A JWT token has 3 parts, separated by dots:

```
Header.Payload.Signature

eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiZXhwIjoxNjI1MDAw MDAw…Signature
│                                │                                                        │
└─────────────────────────────┬─────────────────────────────────────────────────────────┘
                              │
                        THREE PARTS (base64 encoded)

Decoded:

Header: {
  "typ": "JWT",
  "alg": "HS256"      ← Algorithm used for signature
}

Payload: {
  "sub": "user123",   ← Subject (who owns this token)
  "exp": 1625000000,  ← Expiration time (Unix timestamp)
  "iat": 1624996400,  ← Issued at (when token was created)
  "roles": ["USER"]   ← Custom claim (user permissions)
}

Signature: HMACSHA256(header.payload, SECRET_KEY)
           ← This prevents tampering
```

### Why the Signature Matters:

```
Scenario 1: Token is valid
  Original Token:  header.payload.correctSignature
  Gateway checks:  Recalculate HMACSHA256(header.payload, SECRET)
  Result:          Signatures match ✓ ALLOW
  
Scenario 2: Token is forged
  Forged Token:    header.payload.wrongSignature
  Attacker tried to modify payload: "sub": "hacker"
  Gateway checks:  Recalculate HMACSHA256(header.payload, SECRET)
  Result:          Signatures don't match ✗ REJECT (401)
  
Scenario 3: Token is valid but expired
  Expired Token:   header.payload.correctSignature
  BUT:             exp: 1600000000 (past date)
  Gateway checks:  Is exp > now? NO
  Result:          ✗ REJECT (401)
```

### Code Flow:

```java
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        try {
            // 1. Get Authorization header
            String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null) {
                return 401 Unauthorized;
            }

            // 2. Extract token from "Bearer xyz" format
            String token = authHeader.substring(7);  // Remove "Bearer "

            // 3. Validate token (JwtUtil does this)
            Claims claims = jwtUtil.validateToken(token);
            // If invalid, validateToken() throws JwtValidationException
            // Exception is caught below and returns 401

            // 4. Extract user info
            String userId = jwtUtil.getUserId(claims);

            // 5. Add to request headers for downstream services
            exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .build();

            // 6. Continue to next filter
            return chain.filter(exchange);

        } catch (JwtValidationException e) {
            return 401 Unauthorized;
        }
    };
}
```

### What to remember:

> Filters intercept EVERY request. JWT filter validates tokens and stops invalid requests at the gateway (fail fast).

---

## ⚡ Component 4: RateLimitFilter.java

### What is it?

A **filter that prevents abuse** by limiting requests per user.

### Why do we need it?

```
Scenario 1: No rate limiting
  Attacker sends 1,000,000 requests/second
  └─ Your backend gets overwhelmed
  └─ Service crashes
  └─ Legitimate users can't access

Scenario 2: With rate limiting (100 requests/minute)
  Attacker sends 1,000 requests/second
  └─ First 100 are allowed
  └─ Request 101 returns 429 Too Many Requests
  └─ Attacker is throttled
  └─ Service stays healthy
  └─ Legitimate users still work
```

### Token Bucket Algorithm (Simplified):

Imagine each user has a bucket that:
- Starts with 100 tokens
- Each request consumes 1 token
- Tokens leak out and refill over time

```
Time 0:   Bucket has 100 tokens ❶❷❸...❶❶❶ (100 tokens)
          Request 1 arrives → consume 1 token → 99 left
          Request 2 arrives → consume 1 token → 98 left
          Request 3 arrives → consume 1 token → 97 left
          ...
          Request 100 arrives → consume 1 token → 0 left

Time 0+:  Bucket is empty (no tokens)
          Request 101 arrives → NO TOKENS → Return 429 Too Many Requests

Time 60s: Bucket refills (1 minute passed)
          Bucket has 100 tokens again
          Request 102 arrives → consume 1 token → 99 left
```

### How we store buckets:

```java
Map<String, Bucket> buckets = new ConcurrentHashMap<>();
// "user123" → Bucket(99 tokens remaining)
// "user456" → Bucket(50 tokens remaining)
// "user789" → Bucket(0 tokens remaining)
```

### Code Flow:

```java
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        // 1. Get user ID from request header (added by JwtAuthenticationFilter)
        String userId = exchange.getRequest()
            .getHeaders()
            .getFirst("X-User-Id");

        // 2. Get bucket for this user
        Bucket bucket = resolveBucket(userId);  // Creates if doesn't exist

        // 3. Try to consume 1 token
        if (bucket.tryConsume(1)) {
            // Token consumed successfully
            return chain.filter(exchange);  // Allow request

        } else {
            // No tokens left
            exchange.getResponse()
                .setStatusCode(HttpStatus.TOO_MANY_REQUESTS);  // 429
            exchange.getResponse()
                .getHeaders()
                .add("Retry-After", "60");  // Retry after 60 seconds
            return exchange.getResponse().setComplete();
        }
    };
}
```

### Important Detail:

> **Why we use X-User-Id from headers:**
> 
> JwtAuthenticationFilter already added this header
> We just read it instead of recalculating it
> This shows filters can communicate via headers

### What to remember:

> Token bucket limits requests per user without rejecting requests unfairly. After time passes, bucket refills.

---

## 📋 Component 5: JwtUtil.java

### What is it?

A **utility class that handles all JWT operations**.

### Three main jobs:

#### Job 1: Validate Token

```java
public Claims validateToken(String token) {
    try {
        // Parse and verify signature
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(secret.getBytes())
            .build()
            .parseClaimsJws(token)
            .getBody();

        // Check expiration
        if (claims.getExpiration().before(new Date())) {
            throw new JwtValidationException("Token has expired");
        }

        return claims;

    } catch (SignatureException e) {
        throw new JwtValidationException("Invalid token signature (token was forged)", e);
    } catch (ExpiredJwtException e) {
        throw new JwtValidationException("Token has expired", e);
    }
    // ... other exceptions ...
}
```

**Why** `setSigningKey(secret.getBytes())`?
- Only the server knows the secret key
- If signature matches after recalculating with secret: Token is valid
- If signature doesn't match: Token was forged

#### Job 2: Extract Claims

```java
public String getUserId(Claims claims) {
    return claims.getSubject();  // The "sub" claim
}

public List<String> getRoles(Claims claims) {
    Object rolesObj = claims.get("roles");  // Custom claim
    if (rolesObj instanceof List) {
        return (List<String>) rolesObj;
    }
    return Collections.emptyList();
}
```

#### Job 3: Generate Token (for testing)

```java
public String generateToken(String userId, List<String> roles) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", roles);

    return Jwts.builder()
        .setClaims(claims)
        .setSubject(userId)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(SignatureAlgorithm.HS256, secret.getBytes())
        .compact();
}
```

### Example: Create a test token

```java
JwtUtil jwtUtil = new JwtUtil();
String token = jwtUtil.generateToken("user123", Arrays.asList("USER", "ADMIN"));
// Output: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWI...

// Later, validate it:
Claims claims = jwtUtil.validateToken(token);
String userId = jwtUtil.getUserId(claims);  // "user123"
List<String> roles = jwtUtil.getRoles(claims);  // ["USER", "ADMIN"]
```

### What to remember:

> JwtUtil encapsulates JWT complexity. Everyone else just calls validateToken().

---

## 📄 Component 6: application.yml

### What is it?

**Configuration file** that sets up the gateway.

```yaml
server:
  port: 8080  # Gateway listens on this port

spring:
  application:
    name: api-gateway-service

jwt:
  secret: "your-super-secret-key-that-is-at-least-32-chars"
  expiration: 3600000  # 1 hour in milliseconds

logging:
  level:
    com.payments.platform: DEBUG  # Show debug logs from our code
```

### Why externalize configuration?

```
Development:  secret: "dev-secret", port: 8080, expiration: 1 hour
Test:         secret: "test-secret", port: 8080, expiration: 10 min
Production:   secret: "prod-secret-${VAULT}", port: 443, expiration: 30 min
```

Different environments need different values!

### What to remember:

> Configuration goes in application.yml, not hardcoded in Java. Different environments override different values.

---

## 📚 Part 2: How Everything Works Together

### Complete Request Flow:

```
1. Client sends HTTP request:
   GET /api/v1/accounts/123/balance
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

2. Gateway receives on port 8080

3. Route matching:
   Does path match any route?
   /api/v1/accounts/123/balance matches /api/v1/accounts/** ✓
   
4. Apply filters in order:

   Filter 1: JwtAuthenticationFilter
   ┌─────────────────────────────────────────────────┐
   │ 1. Extract Authorization: Bearer xyz            │
   │ 2. Extract token: xyz                           │
   │ 3. Validate signature using secret key          │
   │ 4. Check expiration: Is exp > now?              │
   │ 5. Extract claims: sub="user123", roles=[...]   │
   │ 6. Add headers: X-User-Id: user123              │
   │ 7. Pass to next filter                          │
   └─────────────────────────────────────────────────┘
                     ↓
   Filter 2: RateLimitFilter
   ┌─────────────────────────────────────────────────┐
   │ 1. Read X-User-Id: user123                      │
   │ 2. Get bucket for user123                       │
   │ 3. Try to consume 1 token                       │
   │ 4. Success: Have tokens left (99 remaining)     │
   │ 5. Continue to next handler                     │
   └─────────────────────────────────────────────────┘
                     ↓
5. Forward to backend service:
   GET http://account-service:8081/api/v1/accounts/123/balance
   Headers: X-User-Id: user123, X-User-Roles: USER, ...

6. Account service responds:
   HTTP 200 OK
   Body: { "accountId": "123", "balance": 1000.00 }

7. Gateway returns response to client:
   HTTP 200 OK
   Body: { "accountId": "123", "balance": 1000.00 }
```

### What Happens If Something Fails:

#### Scenario 1: No Authorization Header

```
Request: GET /api/v1/accounts/123
(No Authorization header)

Filter 1: JwtAuthenticationFilter
  ├─ Read Authorization header: NULL
  ├─ Return immediately: 401 Unauthorized
  └─ Don't forward to account-service

Client receives: HTTP 401 Unauthorized
```

#### Scenario 2: Invalid Token (forged)

```
Request: GET /api/v1/accounts/123
Authorization: Bearer FAKE_TOKEN

Filter 1: JwtAuthenticationFilter
  ├─ Extract token: FAKE_TOKEN
  ├─ Validate signature
  ├─ Recalculate: HMACSHA256(header.payload, secret)
  ├─ Compare with provided signature: MISMATCH
  ├─ Throw JwtValidationException
  └─ Return: 401 Unauthorized

Client receives: HTTP 401 Unauthorized
```

#### Scenario 3: Rate Limit Exceeded

```
Request: GET /api/v1/accounts/123 (101st request in 1 minute)

Filter 1: JwtAuthenticationFilter ✓ PASSED
Filter 2: RateLimitFilter
  ├─ Read X-User-Id: user123
  ├─ Get bucket: 0 tokens remaining
  ├─ Try to consume 1 token: FAILED
  ├─ Return: 429 Too Many Requests
  └─ Add header: Retry-After: 60

Client receives:
  HTTP 429 Too Many Requests
  Header: Retry-After: 60
  (Try again after 60 seconds)
```

---

## ✅ Part 3: Testing

### What We're Testing:

```
Unit Tests (JwtUtilTest.java):
├─ Valid tokens are accepted
├─ Forged tokens are rejected
├─ Expired tokens are rejected
└─ Claims are extracted correctly

Integration Tests (JwtAuthenticationFilterIntegrationTest.java):
├─ Requests WITH valid JWT pass through
├─ Requests WITHOUT JWT return 401
├─ Requests WITH invalid JWT return 401
└─ Health check doesn't require auth
```

### Running Tests:

```bash
# Run unit tests
mvnw.cmd test

# Run integration tests
mvnw.cmd verify

# Run specific test
mvnw.cmd test -Dtest=JwtUtilTest
```

---

## 🎯 Key Concepts Summary

| Concept | Purpose | Example |
|---------|---------|---------|
| **Route** | Maps URL patterns to services | `/api/v1/accounts/**` → `account-service:8081` |
| **Filter** | Intercepts requests to add logic | JwtAuthenticationFilter validates tokens |
| **JWT** | Signed token carrying user info | `header.payload.signature` |
| **Signature** | Prevents token tampering | `HMACSHA256(header.payload, secret)` |
| **Claims** | Information inside token | `sub: "user123", roles: ["USER"]` |
| **Rate Limiting** | Prevents abuse | 100 requests per user per minute |
| **Token Bucket** | Rate limit algorithm | Bucket starts with 100 tokens, refills every 60s |

---

## 🚀 Next: How to Test It

### Step 1: Build the gateway

```bash
cd api-gateway-service
mvnw.cmd clean package -DskipTests
```

### Step 2: Start the backend services

```bash
# Make sure all 5 services are running
# account-service: 8081
# fraud-service: 8082
# payment-service: 8083
# notification-service: 8084
# transaction-history-service: 8085
```

### Step 3: Start the gateway

```bash
cd api-gateway-service
mvnw.cmd spring-boot:run
```

Gateway starts on: http://localhost:8080

### Step 4: Test with Postman or curl

```bash
# Generate a test token
curl -X POST http://localhost:8080/test/token \
  -d "userId=user123&roles=USER,ADMIN"

# Make a request with the token
curl -X GET http://localhost:8080/api/v1/accounts/123 \
  -H "Authorization: Bearer <token>"

# Try without token (should get 401)
curl -X GET http://localhost:8080/api/v1/accounts/123
```

---

## 📖 Recommended Reading Order

1. **ApiGatewayApplication.java** - The entry point (2 min)
2. **application.yml** - Configuration (2 min)
3. **GatewayConfig.java** - Routes and filters (5 min)
4. **JwtAuthenticationFilter.java** - Authentication (10 min)
5. **JwtUtil.java** - JWT operations (10 min)
6. **RateLimitFilter.java** - Rate limiting (10 min)
7. **Tests** - Verify everything works (15 min)

Total: ~50 minutes to understand the entire system.

