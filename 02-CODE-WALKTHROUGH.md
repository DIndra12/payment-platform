# 02 - Code Walkthrough & Configuration

## 📖 Navigate the Codebase

This guide walks you through the code structure and explains how each piece works.

---

## 🏗️ Project Structure

```
payment-platform/ (Root)
├── pom.xml (Parent - all 7 services)
│   └── <module>api-gateway-service</module>
│   └── <module>account-service</module>
│   └── ... (5 microservices)
│
├── api-gateway-service/ (NEW - Port 8080)
│   ├── pom.xml
│   ├── src/main/java/com/payments/platform/apigateway/
│   │   ├── ApiGatewayApplication.java (Entry point)
│   │   ├── config/
│   │   │   └── GatewayConfig.java (Routes + filters)
│   │   ├── filter/
│   │   │   ├── JwtAuthenticationFilter.java (JWT validation)
│   │   │   └── RateLimitFilter.java (Rate limiting)
│   │   ├── util/
│   │   │   └── JwtUtil.java (JWT operations)
│   │   └── exception/
│   │       └── JwtValidationException.java
│   ├── src/main/resources/
│   │   └── application.yml (Configuration)
│   └── src/test/java/ (62 tests)
│
├── payment-service/ (Circuit breaker added)
│   ├── src/main/java/.../client/
│   │   └── AccountClient.java (@CircuitBreaker added)
│   └── src/main/resources/
│       └── application.yml (Resilience4j config)
│
└── (5 other microservices...)
```

---

## 🔧 API Gateway Components

### 1. ApiGatewayApplication.java

**Location:** `api-gateway-service/src/main/java/.../ApiGatewayApplication.java`

```java
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**What it does:**
- Entry point for the application
- Spring Boot starts the embedded web server (Tomcat)
- Loads configuration from application.yml
- Creates beans for all components

**When to modify:** Almost never - this is boilerplate.

---

### 2. GatewayConfig.java (Routes)

**Location:** `api-gateway-service/src/main/java/.../config/GatewayConfig.java`

**What it does:**
- Defines all routes (URL → Service mapping)
- Applies filters (JWT, Rate Limit)

**Key part:**
```java
@Bean
public RouteLocator routeLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("account-service", r -> r
            .path("/api/v1/accounts/**")
            .filters(f -> f
                .filter(jwtAuthenticationFilter.apply(...))   // 1. JWT first
                .filter(rateLimitFilter.apply(...))           // 2. Rate limit
            )
            .uri("http://account-service:8081")              // Where to send
        )
        // ... more routes ...
        .build();
}
```

**How to add a new route:**
```java
.route("new-service", r -> r
    .path("/api/v1/new-service/**")
    .filters(f -> f
        .filter(jwtAuthenticationFilter.apply(...))
        .filter(rateLimitFilter.apply(...))
    )
    .uri("http://new-service:8086")
)
```

---

### 3. JwtAuthenticationFilter.java

**Location:** `api-gateway-service/src/main/java/.../filter/JwtAuthenticationFilter.java`

**What it does:**
1. Extracts JWT token from Authorization header
2. Validates token signature and expiration
3. Adds user info to request headers
4. Returns 401 if invalid

**Key method:**
```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        // 1. Get Authorization header
        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);
        
        // 2. Check if present
        if (authHeader == null) {
            return 401 Unauthorized;
        }
        
        // 3. Extract token (remove "Bearer " prefix)
        String token = authHeader.substring(7);
        
        // 4. Validate token
        Claims claims = jwtUtil.validateToken(token);
        
        // 5. Add user info to headers
        exchange.getRequest().mutate()
            .header("X-User-Id", claims.getSubject())
            .build();
        
        // 6. Continue to next filter
        return chain.filter(exchange);
    };
}
```

**When to modify:**
- Add new headers (e.g., X-User-Roles)
- Change authentication logic
- Add role-based access control

---

### 4. RateLimitFilter.java

**Location:** `api-gateway-service/src/main/java/.../filter/RateLimitFilter.java`

**What it does:**
- Tracks tokens per user (token bucket)
- Allows 100 requests per minute
- Returns 429 if exceeded

**Key concept:**
```
Each user has a bucket:
  Bucket: 100 tokens
  Each request uses: 1 token
  Refill: Every 60 seconds
```

**When to modify:**
- Change rate limit (currently 100 req/min)
- Use Redis for distributed rate limiting (production)
- Add different limits for different user types

---

### 5. JwtUtil.java

**Location:** `api-gateway-service/src/main/java/.../util/JwtUtil.java`

**What it does:**
- Validates JWT signatures
- Extracts claims (user info)
- Generates tokens (for testing)

**Key methods:**
```java
// Validate token
public Claims validateToken(String token) {
    // Check signature (can't be forged)
    // Check expiration (not too old)
    // Return claims
}

// Extract user ID
public String getUserId(Claims claims) {
    return claims.getSubject();
}

// Extract roles
public List<String> getRoles(Claims claims) {
    return claims.get("roles", List.class);
}

// Generate token (for testing)
public String generateToken(String userId, List<String> roles) {
    return Jwts.builder()
        .setSubject(userId)
        .claim("roles", roles)
        .setExpiration(...)
        .signWith(SignatureAlgorithm.HS256, secret)
        .compact();
}
```

**When to modify:**
- Add new claims (permissions, departments, etc)
- Change token expiration time
- Add token refresh logic

---

## ⚙️ Configuration

### Gateway Configuration (application.yml)

**Location:** `api-gateway-service/src/main/resources/application.yml`

```yaml
server:
  port: 8080              # Port gateway listens on

jwt:
  secret: "your-secret"   # Secret for signing tokens
  expiration: 3600000     # 1 hour in milliseconds

logging:
  level:
    com.payments.platform: DEBUG
```

### Payment Service Configuration (application.yml)

**Location:** `payment-service/src/main/resources/application.yml`

```yaml
external-services:
  account-service-url: ${ACCOUNT_SERVICE_URL:http://localhost:8081}
  fraud-service-url: ${FRAUD_SERVICE_URL:http://localhost:8082}

resilience4j:
  circuitbreaker:
    instances:
      account-service:
        failure-rate-threshold: 50          # Open if 50% fail
        wait-duration-in-open-state: 30000  # Wait 30s
        sliding-window-size: 10             # Track last 10 calls
        minimum-number-of-calls: 5          # Need 5+ calls

  retry:
    instances:
      account-service:
        max-attempts: 3                     # Try 3 times
        wait-duration: 100                  # 100ms wait
        interval-function: exponential      # 100ms, 200ms, 400ms

  timelimiter:
    instances:
      account-service:
        timeout-duration: 2s                # Don't wait >2s
```

---

## 🔌 Circuit Breaker Implementation

### AccountClient.java (Feign Client)

**Location:** `payment-service/src/main/java/.../client/AccountClient.java`

```java
@FeignClient(
    name = "account-service",
    url = "${external-services.account-service-url}"
)
public interface AccountClient {
    
    @PostMapping("/api/v1/accounts/{accountId}/debit")
    @CircuitBreaker(name = "account-service", 
                    fallbackMethod = "debitAccountFallback")
    @Retry(name = "account-service")
    @TimeLimiter(name = "account-service")
    CompletableFuture<Void> debitAccount(
        @PathVariable("accountId") String accountId,
        @RequestBody DebitRequest request
    );
    
    // Fallback when service is down
    default CompletableFuture<Void> debitAccountFallback(
        String accountId,
        DebitRequest request,
        Exception ex
    ) {
        log.error("Account service unavailable");
        return CompletableFuture.failedFuture(ex);
    }
}
```

**Annotations explain:**
- `@CircuitBreaker` - Opens after failures, closes after recovery
- `@Retry` - Retries with exponential backoff
- `@TimeLimiter` - Timeouts after 2 seconds
- `fallbackMethod` - Called when all fails

---

## 🔐 Hardcoding Locations & How to Override

### Where Hardcoding Happens

#### 1. Gateway Routes (GatewayConfig.java)
```java
.uri("http://account-service:8081")  // ← HARDCODED
```

**Override:** Use environment variables
```bash
export ACCOUNT_SERVICE_URL=http://account-service:9081
java -jar api-gateway.jar
```

#### 2. Payment Service Config (application.yml)
```yaml
account-service-url: ${ACCOUNT_SERVICE_URL:http://localhost:8081}
                     ↑ Default if not set
```

**Override:**
```bash
export ACCOUNT_SERVICE_URL=http://account-service:8081
java -jar payment-service.jar
```

#### 3. Server Ports
```yaml
server:
  port: 8080  # ← HARDCODED
```

**Override:**
```bash
java -jar api-gateway.jar --server.port=9080
```

---

## 📊 Request Flow (Code Path)

When request arrives at gateway:

```
1. Request: GET /api/v1/accounts/123
   Header: Authorization: Bearer <token>
        ↓
2. GatewayConfig.routeLocator()
   └─ Find matching route: /api/v1/accounts/123 matches /api/v1/accounts/**
        ↓
3. Apply Filters (in order):
   
   a) JwtAuthenticationFilter.apply()
      └─ JwtUtil.validateToken(token)
         ├─ Check signature (HMACSHA256)
         ├─ Check expiration
         ├─ Extract claims
         └─ Add X-User-Id header
        ↓
   
   b) RateLimitFilter.apply()
      └─ Check bucket for user123
         ├─ Does it have token? YES
         ├─ Consume 1 token
         └─ Continue
        ↓
4. Forward to backend:
   GET http://account-service:8081/api/v1/accounts/123
   Header: X-User-Id: user123
        ↓
5. Response comes back
        ↓
6. Return to client
```

---

## 🧪 Testing Points in Code

### Where to Test Each Component

```
JwtUtilTest.java
└─ Test JWT operations in isolation
   ├─ Token generation
   ├─ Token validation
   ├─ Signature verification
   └─ Claims extraction

RateLimitFilterTest.java
└─ Test rate limiting logic
   ├─ Token consumption
   ├─ Bucket refill
   ├─ Per-user tracking
   └─ Concurrency

GatewayIntegrationTest.java
└─ Test complete flows
   ├─ JWT + Rate limit together
   ├─ Routing to services
   ├─ Error responses
   └─ Header propagation

GatewayAcceptanceTest.java
└─ Test real user scenarios
   ├─ Complete user journey
   ├─ Multiple concurrent users
   ├─ Rate limiting under load
   └─ Circuit breaker behavior
```

---

## 🚀 Common Modifications

### Add a New Route

**File:** `api-gateway-service/src/main/java/.../config/GatewayConfig.java`

```java
.route("new-service", r -> r
    .path("/api/v1/new-service/**")
    .filters(f -> f
        .filter(jwtAuthenticationFilter.apply(...))
        .filter(rateLimitFilter.apply(...))
    )
    .uri("http://new-service:8086")
)
```

### Change Rate Limit

**File:** `api-gateway-service/src/main/java/.../filter/RateLimitFilter.java`

```java
private static final int REQUESTS_PER_MINUTE = 200;  // Change from 100
```

### Change JWT Expiration

**File:** `api-gateway-service/src/main/resources/application.yml`

```yaml
jwt:
  expiration: 1800000  # Change from 3600000 (30 min instead of 1 hour)
```

### Change Circuit Breaker Threshold

**File:** `payment-service/src/main/resources/application.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      account-service:
        failure-rate-threshold: 30  # Open after 30% failures (was 50%)
```

---

## 📖 Next Steps

- **Want to test?** → Read **03-TESTING-GUIDE.md**
- **Want to run?** → Read **04-SETUP-AND-RUN.md**
- **Want architecture?** → Read **05-ARCHITECTURE.md**
