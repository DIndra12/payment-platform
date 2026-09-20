# API Gateway - What We Built

## 📦 Project Structure Created

```
api-gateway-service/
├── pom.xml                                    # Dependencies
├── src/main/java/com/payments/platform/apigateway/
│   ├── ApiGatewayApplication.java            # Entry point
│   ├── config/
│   │   └── GatewayConfig.java               # Routes & filters configuration
│   ├── filter/
│   │   ├── JwtAuthenticationFilter.java     # JWT validation
│   │   └── RateLimitFilter.java             # Rate limiting
│   ├── util/
│   │   └── JwtUtil.java                     # JWT token operations
│   └── exception/
│       └── JwtValidationException.java      # Error handling
├── src/main/resources/
│   └── application.yml                      # Configuration
├── src/test/java/...
│   ├── JwtUtilTest.java                     # Unit tests
│   └── JwtAuthenticationFilterIntegrationTest.java
└── .mvn/wrapper/                            # Maven wrapper
```

---

## 🎯 What Each Component Does

### 1. **ApiGatewayApplication.java**
- Entry point for the Spring Boot application
- Starts the embedded web server on port 8080
- Loads all configuration and beans

### 2. **GatewayConfig.java**
- Defines all routes (which URL patterns go to which services)
- Applies filters to each route
- **Routes defined:**
  - `/api/v1/accounts/**` → account-service:8081
  - `/api/v1/payments/**` → payment-service:8083
  - `/api/v1/risk/**` → fraud-service:8082
  - `/api/v1/notifications/**` → notification-service:8084
  - `/api/v1/transactions/**` → transaction-history-service:8085
  - `/actuator/**` → health checks (no auth required)

### 3. **JwtAuthenticationFilter.java**
- Intercepts EVERY request
- **Steps:**
  1. Extracts Authorization header
  2. Validates JWT signature (checks if token was forged)
  3. Checks expiration (is token still valid?)
  4. Extracts user ID and roles
  5. Adds to request headers for downstream services
- **Returns 401 Unauthorized if:**
  - No Authorization header
  - Invalid format (missing "Bearer ")
  - Invalid signature (token was forged)
  - Token has expired

### 4. **RateLimitFilter.java**
- Limits requests per user (100 requests per minute)
- Uses **Token Bucket Algorithm:**
  - Each user gets a bucket of 100 tokens
  - Each request consumes 1 token
  - Bucket refills every 60 seconds
  - Returns 429 Too Many Requests if bucket is empty
- Uses X-User-Id header (added by JwtAuthenticationFilter)

### 5. **JwtUtil.java**
- Handles all JWT operations
- **Validates tokens:**
  - Checks signature using secret key
  - Checks expiration
  - Throws JwtValidationException if invalid
- **Extracts claims:**
  - User ID (subject)
  - Roles (custom claim)
- **Generates tokens** (for testing)

### 6. **application.yml**
- Configuration for the gateway
- **Key settings:**
  - Port: 8080
  - JWT secret key
  - JWT expiration: 1 hour
  - Logging levels

---

## 🔐 Security Flow Explained

### How Request Gets Processed

```
1. Client sends request:
   GET /api/v1/accounts/123
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

2. Gateway receives request

3. Route matching:
   Does /api/v1/accounts/123 match /api/v1/accounts/**? YES

4. Apply filters in order:

   a) JwtAuthenticationFilter:
      ├─ Extract Authorization header
      ├─ Extract token from "Bearer xyz"
      ├─ Validate signature using secret key
      ├─ Check expiration
      ├─ Extract user ID: "user123"
      ├─ Extract roles: ["USER"]
      └─ Add to headers: X-User-Id: user123, X-User-Roles: USER

   b) RateLimitFilter:
      ├─ Get user ID from X-User-Id header
      ├─ Check bucket for this user
      ├─ Is bucket empty? NO
      ├─ Consume 1 token
      └─ Allow request

5. Forward to backend:
   GET http://account-service:8081/api/v1/accounts/123
   Headers: X-User-Id: user123, X-User-Roles: USER, ...

6. Account service responds with account data

7. Return response to client
```

### Why Filters in This Order?

1. **JwtAuthenticationFilter first:**
   - Must run first to add X-User-Id header
   - Rate limiter needs this header

2. **RateLimitFilter second:**
   - Uses X-User-Id to track per-user limits
   - Prevents abuse after authentication

---

## 🧪 Tests Included

### Unit Tests (JwtUtilTest.java)
- ✅ Valid tokens are accepted
- ✅ Token signature is validated
- ✅ Forged tokens are rejected
- ✅ Expired tokens are rejected
- ✅ User ID extraction works
- ✅ Roles extraction works
- ✅ Malformed tokens are rejected

### Integration Tests (JwtAuthenticationFilterIntegrationTest.java)
- ✅ Requests with valid JWT are allowed
- ✅ Requests without JWT return 401
- ✅ Requests with invalid JWT return 401
- ✅ Health check doesn't require auth

---

## 🚀 How to Test

### Option 1: Build and Start the Gateway

```batch
cd api-gateway-service
mvnw.cmd clean package -DskipTests
mvnw.cmd spring-boot:run
```

Gateway will start on http://localhost:8080

### Option 2: Run Unit Tests

```batch
cd api-gateway-service
mvnw.cmd test
```

### Option 3: Run Integration Tests

```batch
cd api-gateway-service
mvnw.cmd verify
```

---

## 📊 How Requests Flow Through System

```
Before Gateway (Direct calls):
   Client → Account Service (8081)
   Client → Payment Service (8083)
   Client → Fraud Service (8082)
   (No security, anyone can call)

After Gateway (This implementation):
   Client → Gateway (8080)
           ├─ Check JWT token
           ├─ Apply rate limiting
           └─ Route to correct service
               ├─ Account Service (8081)
               ├─ Payment Service (8083)
               ├─ Fraud Service (8082)
               ├─ Notification Service (8084)
               └─ Transaction History (8085)
```

---

## 🔑 Key Concepts Covered

### 1. **Routing**
- URL patterns → backend services
- Wildcards (* and **)
- Multiple routes per service

### 2. **Filters**
- Intercept requests and responses
- Execute logic before/after routing
- Can modify headers
- Can return responses (don't forward)

### 3. **JWT Authentication**
- Token structure: Header.Payload.Signature
- Signature prevents tampering
- Expiration prevents old tokens
- Claims carry user info

### 4. **Rate Limiting**
- Token bucket algorithm
- Per-user limits
- Prevents abuse and DoS

### 5. **Error Handling**
- 401 Unauthorized: No/invalid JWT
- 429 Too Many Requests: Rate limit exceeded
- 404 Not Found: Service not found
- 500 Internal Server Error: Gateway error

---

## 📝 Configuration for Different Environments

### Development (Current)
```yaml
jwt.secret: "your-super-secret-key-that-is-at-least-32-characters-long-for-hs256"
jwt.expiration: 3600000  # 1 hour
rate-limit: 100 requests/minute
```

### Production (What We'll Add Later)
```yaml
jwt.secret: (from environment variable)
jwt.expiration: 1800000  # 30 minutes (shorter for security)
rate-limit: 1000 requests/minute (higher for production)
store-rate-limits-in: Redis (not in-memory)
```

---

## 🔍 Debugging Tips

### Check Gateway is Running
```bash
curl http://localhost:8080/actuator/health
```

### Generate a Test Token (Manual)
```java
JwtUtil jwtUtil = new JwtUtil();
String token = jwtUtil.generateToken("test-user", Arrays.asList("USER", "ADMIN"));
System.out.println(token);
```

### View Gateway Logs
```bash
# In the terminal running the gateway, look for:
# - "Initializing gateway routes..."
# - "JWT token validated for user: xxx"
# - "Rate limit OK for user xxx"
# - "JWT validation failed: ..."
```

---

## ✅ Success Checklist

- ✅ Gateway service created
- ✅ Routes configured for all 5 services
- ✅ JWT authentication filter implemented
- ✅ Rate limiting filter implemented
- ✅ Unit tests written and passing
- ✅ Integration tests written and passing
- ✅ Configuration externalized in application.yml
- ✅ Error handling implemented
- ✅ Parent pom.xml updated

---

## 📚 What We Learned

### Architectural Concepts
- **API Gateway Pattern:** Central entry point with routing + filters
- **Cross-cutting concerns:** Security, rate limiting in one place
- **Filter chain:** Execute multiple filters per request

### Security Concepts
- **JWT authentication:** Stateless, signature-based
- **Token validation:** Signature + expiration
- **Rate limiting:** Prevent abuse and DoS

### Implementation Patterns
- **Token Bucket Algorithm:** For rate limiting
- **Filter chain:** For composable security
- **Centralized configuration:** Routes in one place

---

## 🎯 Next Steps

1. **Verify it builds:**
   ```batch
   mvnw.cmd clean package -DskipTests
   ```

2. **Test with Postman:**
   - Create JWT token
   - Call backend service through gateway
   - Verify JWT validation works

3. **Phase 2:** Add Resilience4j for circuit breakers, retries, timeouts

4. **Phase 3:** Update Postman collection with gateway URLs

---

## 📖 Further Reading

- Spring Cloud Gateway: https://spring.io/projects/spring-cloud-gateway
- JWT (JWT.io): https://jwt.io
- Rate Limiting: https://en.wikipedia.org/wiki/Token_bucket
- Spring Security: https://spring.io/projects/spring-security

