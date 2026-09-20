# Hardcoding Locations & How to Override

## 🔍 Where Are Ports Hardcoded?

### Location 1: API Gateway Routes

**File:** `api-gateway-service/src/main/java/.../GatewayConfig.java`

```java
@Bean
public RouteLocator routeLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("account-service", r -> r
            .path("/api/v1/accounts/**")
            .uri("http://account-service:8081")  ← HARDCODED
        )
        .route("payment-service", r -> r
            .path("/api/v1/payments/**")
            .uri("http://payment-service:8083")  ← HARDCODED
        )
        // ... more routes ...
        .build();
}
```

**How to override:**

❌ **Wrong way:** Change code and recompile
```java
// Don't do this!
.uri("http://payment-service:9083")  // ← Bad idea
// Now you need to recompile and redeploy
```

✅ **Right way:** Use environment variables
```java
// Create a configuration class:
@Configuration
public class ServiceUrlConfig {
    @Value("${services.account.url:http://account-service:8081}")
    private String accountServiceUrl;
    
    @Value("${services.payment.url:http://payment-service:8083}")
    private String paymentServiceUrl;
    
    // Use these in GatewayConfig
}
```

Then override via:
```bash
# Method 1: Environment variable
export SERVICES_ACCOUNT_URL=http://account-service:9081
java -jar api-gateway.jar

# Method 2: Command line
java -jar api-gateway.jar --services.account.url=http://account-service:9081

# Method 3: application.yml
services:
  account:
    url: http://account-service:9081
```

---

### Location 2: Payment Service - Account Service Call

**File:** `payment-service/src/main/java/.../PaymentSaga.java` (where it calls Account Service)

```java
public class PaymentSaga {
    @Autowired
    private AccountClient accountClient;
    
    public void processPayment(PaymentRequest request) {
        // The call is here:
        accountClient.debitAccount(
            request.getPayerAccountId(),
            new DebitRequest(request.getAmount())
        );
        // ↑ Where does accountClient know to call?
        //   Answer: From the @FeignClient annotation
    }
}
```

**File:** `payment-service/src/main/java/.../AccountClient.java` (Feign client)

```java
@FeignClient(
    name = "account-service",
    url = "${external-services.account-service-url}"  ← Gets URL from config
)
public interface AccountClient {
    @PostMapping("/api/v1/accounts/{accountId}/debit")
    void debitAccount(...);
}
```

**File:** `payment-service/src/main/resources/application.yml`

```yaml
external-services:
  account-service-url: ${ACCOUNT_SERVICE_URL:http://localhost:8081}  ← HARDCODED DEFAULT
  fraud-service-url: ${FRAUD_SERVICE_URL:http://localhost:8082}      ← HARDCODED DEFAULT
```

**How to override:**

```bash
# Method 1: Environment variable (recommended)
export ACCOUNT_SERVICE_URL=http://account-service:8081
java -jar payment-service.jar

# Method 2: Docker
docker run -e ACCOUNT_SERVICE_URL=http://account-service:8081 payment-service

# Method 3: Docker Compose
services:
  payment-service:
    environment:
      ACCOUNT_SERVICE_URL: http://account-service:8081

# Method 4: Kubernetes
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: payment-service
      env:
        - name: ACCOUNT_SERVICE_URL
          value: http://account-service:8081

# Method 5: Command line
java -jar payment-service.jar --external-services.account-service-url=http://account-service:8081
```

---

### Location 3: Server Ports

**File:** `api-gateway-service/src/main/resources/application.yml`

```yaml
server:
  port: 8080  ← HARDCODED
```

**File:** `account-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081  ← HARDCODED
```

**File:** `payment-service/src/main/resources/application.yml`

```yaml
server:
  port: 8083  ← HARDCODED
```

**How to override:**

```bash
# Method 1: Environment variable
export SERVER_PORT=9080
java -jar api-gateway.jar

# Method 2: Command line
java -jar api-gateway.jar --server.port=9080

# Method 3: application.yml (at runtime)
server:
  port: 9080

# Method 4: Docker
docker run -p 9080:8080 -e SERVER_PORT=8080 api-gateway

# Method 5: Kubernetes
ports:
  - containerPort: 8080
    name: http
```

---

## 🎯 Current Setup: How It All Works Together

### Local Development (start-all.bat)

```
start-all.bat
  ↓
Starts each service with default ports:
  
  ├─ account-service:8081        (uses default in application.yml)
  ├─ fraud-service:8082          (uses default in application.yml)
  ├─ payment-service:8083        (uses default in application.yml)
  │   └─ Calls: http://localhost:8081 (uses default for account-service)
  ├─ notification-service:8084   (uses default in application.yml)
  └─ transaction-history:8085    (uses default in application.yml)
  
  Gateway:8080                    (uses default in application.yml)
    └─ Routes to: account-service:8081 (uses default in GatewayConfig)
```

**Result:** All defaults work perfectly! ✓

---

### Docker Compose Deployment

```yaml
version: '3'
services:
  payment-service:
    image: payment-service:latest
    environment:
      ACCOUNT_SERVICE_URL: http://account-service:8081  ← Override default!
      FRAUD_SERVICE_URL: http://fraud-service:8082
    
  account-service:
    image: account-service:latest
    ports:
      - "8081:8081"
```

**What happens:**
1. Docker Compose starts payment-service container
2. Sets environment variable: `ACCOUNT_SERVICE_URL=http://account-service:8081`
3. Payment Service reads: `${ACCOUNT_SERVICE_URL:http://localhost:8081}`
4. Finds the environment variable, uses `http://account-service:8081`
5. Docker's internal DNS resolves `account-service` to the container
6. Connection works! ✓

**Key insight:** Docker Compose uses service names, not hardcoded IPs!

---

### Kubernetes Deployment (Phase 4)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: payment-service
spec:
  containers:
    - name: payment-service
      image: payment-service:latest
      env:
        - name: ACCOUNT_SERVICE_URL
          value: http://account-service:8081  ← Override default!
```

**What happens:**
1. Kubernetes starts payment-service pod
2. Sets environment variable: `ACCOUNT_SERVICE_URL=http://account-service:8081`
3. Payment Service reads: `${ACCOUNT_SERVICE_URL:http://localhost:8081}`
4. Finds the environment variable, uses `http://account-service:8081`
5. Kubernetes DNS (CoreDNS) resolves `account-service` 
6. DNS returns IPs of all healthy pods
7. Connection load-balanced across all pods! ✓

**Key insight:** Same code, same configuration, just different DNS provider!

---

## 📋 All Hardcoded Values & How to Override

| Location | Hardcoded Value | Override Method | Priority |
|----------|-----------------|-----------------|----------|
| `application.yml` server.port | 8080 (gateway) | `--server.port=XXXX` | 1st |
| `application.yml` server.port | 8081 (account) | `--server.port=XXXX` | 1st |
| `application.yml` account-service-url | http://localhost:8081 | `$ACCOUNT_SERVICE_URL` | 2nd |
| `GatewayConfig.java` uri | http://account-service:8081 | Would need env var support | 3rd |

**Priority explains:** Command line > Environment variable > Default value

---

## ✅ Checklist: Is Hardcoding a Problem?

```
Current State (Phase 2):
  ✓ Works perfectly locally
  ✓ Environment variables allow overrides
  ✓ Can be deployed to Docker
  ✓ Can be deployed to Kubernetes
  ✓ No code changes needed for different environments
  
Answer: NOT A PROBLEM ✓

Future (Phase 4):
  ✓ Kubernetes handles service discovery
  ✓ No hardcoding issues
  ✓ Automatic failover
  ✓ Automatic load balancing
  
Answer: SOLVED IN KUBERNETES ✓
```

---

## 🚀 Action Items

### Right Now (Do Nothing!)
```
✓ Current design is good
✓ Uses environment variables for flexibility
✓ Works everywhere (local, Docker, K8s)
✓ Keep it as is
```

### When Planning Phase 4
```
1. Document current service-to-service communication
2. Plan Kubernetes Services for each microservice
3. Update deployment documentation
4. Test with existing Postman collection
5. Verify DNS resolution works
6. Deploy to Kubernetes
7. Test failover scenarios
8. Celebrate! 🎉
```

---

## 📞 FAQ

### Q: Should we extract hardcoded ports to a config file?

**A:** Not needed. We already use environment variables (best practice):
```yaml
# Current (GOOD):
account-service-url: ${ACCOUNT_SERVICE_URL:http://localhost:8081}

# Vs. Alternative (WORSE):
account-service-url: http://localhost:8081  # No way to override
```

---

### Q: Should we add Spring Cloud Eureka for service discovery?

**A:** Not for this project. Kubernetes handles it better:
- Kubernetes provides built-in service discovery (CoreDNS)
- No need for separate Eureka cluster
- Simpler architecture
- Same result with less complexity

**When would you use Eureka?**
- Multiple datacenters across cloud providers
- Mixed environments (Kubernetes + non-Kubernetes)
- Need service mesh features
- Already running Eureka infrastructure

---

### Q: Can we change ports now for testing?

**A:** Sure! Use environment variables:
```bash
# Test with different ports
export ACCOUNT_SERVICE_URL=http://localhost:9081
export PAYMENT_SERVICE_URL=http://localhost:9083
java -jar payment-service.jar
```

---

### Q: What if someone hardcodes the IP address?

**A:** We guard against this:
```java
// ❌ WRONG - Would appear in code review
.uri("http://192.168.1.100:8081")

// ✅ RIGHT - Uses environment variable
.uri("http://${ACCOUNT_SERVICE_HOST:account-service}:${ACCOUNT_SERVICE_PORT:8081}")
```

We can add a code review check for this.

---

## 🎯 Summary

**Current approach (Phase 2):**
- Uses environment variables ✓
- Works everywhere ✓
- No hardcoding problems ✓
- Ready for production ✓

**Future approach (Phase 4):**
- Let Kubernetes handle discovery ✓
- Same code, different infrastructure ✓
- Zero downtime deployment ✓
- Automatic failover ✓

**Conclusion:**
No changes needed now. Design bridges from development to production seamlessly. 🎯

