# Implementation Plan: API Gateway → Resilience4j → E2E Testing → Infrastructure

**Goal:** Complete coding phase with production-ready patterns, then move to infrastructure (Docker, K8s, AWS)

**Timeline:** 4-5 weeks hands-on learning

---

## 📋 Phase Overview

### Phase 1: API Gateway (Week 1-2)
Learn how to:
- Protect internal services
- Implement authentication/authorization
- Route requests to microservices
- Add rate limiting

### Phase 2: Resilience4j (Week 2)
Learn how to:
- Add circuit breakers to prevent cascading failures
- Implement retry logic with backoff
- Add timeouts to service calls
- Degrade gracefully when services fail

### Phase 3: Update & Test (Week 3)
Learn how to:
- Update Postman collection with authentication
- Test end-to-end flows
- Verify security measures work
- Test failure scenarios

### Phase 4: Infrastructure (Week 4-5+)
Learn how to:
- Build Docker images for services
- Write Kubernetes manifests
- Deploy to local K8s
- Understand container orchestration

---

## 🏗️ PHASE 1: API GATEWAY (Week 1-2)

### What is an API Gateway?

An API Gateway is a **central entry point** for all client requests. Think of it as a security checkpoint at an airport:

```
┌─────────────────────────────────────────────────────────┐
│ CLIENT (Browser, Mobile, External Service)              │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP Request
                     ▼
┌──────────────────────────────────────────────────────────┐
│ API GATEWAY (Spring Cloud Gateway) - Port 8080           │
│  ✓ Check JWT token (Authentication)                     │
│  ✓ Check user permissions (Authorization)               │
│  ✓ Apply rate limiting (Don't abuse)                    │
│  ✓ Route to correct service                             │
│  ✓ Log all requests (Audit trail)                       │
└──────────────────────────────────────────────────────────┘
                     │
       ┌─────────────┼─────────────┐
       ▼             ▼             ▼
   [Account]   [Payment]     [Fraud]
   Service      Service      Service
   (8081)       (8083)       (8082)
```

### Why Do We Need It?

1. **Security:** Don't expose all services directly to internet
2. **Authentication:** Verify who the user is (JWT tokens)
3. **Authorization:** Check what the user can do (roles/permissions)
4. **Rate Limiting:** Prevent abuse (max X requests per minute)
5. **Single Entry Point:** Clients talk to one URL, not multiple
6. **Monitoring:** See all traffic in one place

### Implementation Steps:

#### Step 1: Create Gateway Service
```
Create new Spring Boot service: api-gateway-service
├── Add Spring Cloud Gateway dependency
├── Configure routes to all 5 microservices
└── Add JWT validation filter
```

**What we'll learn:**
- How to route requests based on URL paths
- How to add filters (intercept requests/responses)
- How to validate JWT tokens
- How to handle authentication failures

#### Step 2: Add JWT Support
```
Integrate OAuth2/JWT:
├── Configure JWT secret key
├── Add token validation
├── Extract user info from token
└── Attach user context to requests
```

**What we'll learn:**
- What JWT tokens are and how they work
- How to validate token signatures
- How to extract claims from tokens
- How to propagate user context to services

#### Step 3: Add Rate Limiting
```
Limit requests per user/IP:
├── Configure rate limiter
├── Set limits (e.g., 100 req/min)
├── Return 429 Too Many Requests when exceeded
└── Include retry-after header
```

**What we'll learn:**
- How rate limiting prevents abuse
- How to implement token bucket algorithm
- How to persist rate limit state
- How to return proper HTTP responses

---

## 🛡️ PHASE 2: RESILIENCE4j (Week 2)

### What is Resilience4j?

Resilience4j is a **failure-handling library** that makes your system **fault-tolerant**. Think of it as airbags in a car:

```
Normal Flow:
Payment Service → Account Service (success)
                ✓ Money debited
                ✓ Response returned

Failure Without Resilience:
Payment Service → Account Service (DOWN!)
                ✗ Timeout / Error
                ✗ User sees failure
                ✗ Payment hangs

Failure WITH Resilience:
Payment Service → Account Service (DOWN!)
                ▼ Circuit opens (stop calling)
                ▼ Return cached response or default
                ▼ Retry after delay
                ✓ User gets graceful response
```

### Key Patterns We'll Implement:

#### 1. Circuit Breaker
```
Normal State:
Payment → Account Service ✓ Success
Circuit status: CLOSED (allow calls)

After 5 failures:
Payment → Account Service ✗ Fail
Payment → Account Service ✗ Fail
Payment → Account Service ✗ Fail
Circuit status: OPEN (block calls)
Return error immediately (fail fast)

After 30 seconds:
Circuit status: HALF_OPEN (try one call)
If success: CLOSED (resume normal)
If failure: OPEN (block again)
```

**Why?** Prevents cascading failures. If Account Service is down, don't keep hammering it.

#### 2. Retry with Backoff
```
Request fails:
Attempt 1: Wait 100ms → Retry
Attempt 2: Wait 200ms → Retry
Attempt 3: Wait 400ms → Retry
Success! ✓

Why exponential backoff?
- First attempts: Quick retries (transient issues)
- Later attempts: Longer waits (give service time to recover)
```

#### 3. Timeout
```
Sync call to Account Service:
- Wait max 2 seconds
- If no response in 2s: TIMEOUT
- Return error (don't hang forever)
```

#### 4. Bulkhead Pattern
```
Thread pool isolation:
Payment Service calls Account:
├── Thread pool 1 (max 10 threads)
└── If all threads busy: Reject new calls

Benefits:
- Account Service slowness doesn't affect Fraud Service
- Prevents thread starvation
```

### Implementation Steps:

#### Step 1: Add Resilience4j to Payment Service
```
Add dependencies:
├── resilience4j-spring-boot3
├── resilience4j-feign
└── resilience4j-circuitbreaker

Configuration:
├── resilience4j.yml with policies
├── Define thresholds (e.g., 50% failure rate)
└── Set timeouts (e.g., 2 seconds)
```

#### Step 2: Annotate Feign Clients
```java
@CircuitBreaker(name="account-service")
@Retry(name="account-service")
@TimeLimiter(name="account-service")
public AccountResponse debitAccount() { ... }
```

**What we'll learn:**
- How to apply resilience patterns to HTTP calls
- How to configure thresholds
- How to handle circuit breaker state changes

#### Step 3: Add Fallback Methods
```java
// When Account Service fails, use this:
public AccountResponse debitAccountFallback() {
    log.error("Account service failed, using fallback");
    // Return cached response or default
    return AccountResponse.fallback();
}
```

**What we'll learn:**
- How to gracefully degrade when services fail
- How to use cached data
- How to alert operations team

---

## 🧪 PHASE 3: POSTMAN & E2E TESTING (Week 3)

### Update Postman Collection

Add authentication to all requests:
```
Before: 
POST /api/v1/payments
(No auth, anyone can call)

After:
POST /api/v1/payments
Header: Authorization: Bearer <JWT_TOKEN>
(Only authenticated users can call)
```

### E2E Test Scenarios

#### 1. Happy Path (Everything Works)
```
1. Get JWT token from gateway
2. Call Create Payment with token
3. Verify payment created
4. Check Account balance decreased
5. Verify notification sent
```

#### 2. Authentication Test
```
1. Try without JWT token → 401 Unauthorized ✓
2. Try with invalid token → 401 Unauthorized ✓
3. Try with expired token → 401 Unauthorized ✓
4. Try with valid token → 200 OK ✓
```

#### 3. Rate Limiting Test
```
1. Send 100 requests quickly
2. First 100 succeed
3. Request 101 → 429 Too Many Requests ✓
4. Wait 60 seconds
5. Request 102 → 200 OK ✓
```

#### 4. Resilience Test
```
1. Stop Account Service
2. Try to create payment
3. Circuit breaker opens (fail fast, don't wait)
4. Restart Account Service
5. Wait for circuit to close
6. Requests succeed again
```

---

## 🐳 PHASE 4: INFRASTRUCTURE (Week 4-5+)

### What We'll Learn

#### Docker (Week 4)
```
Why Docker?
- Same environment: dev = staging = prod
- Lightweight: Each service is a container
- Easy to scale: Run 5 copies of Payment Service

What we'll do:
├── Create Dockerfile for each service
├── Build images locally
├── Test locally (docker run)
└── Push to Docker Registry (ECR/Docker Hub)
```

#### Kubernetes (Week 5)
```
Why Kubernetes?
- Orchestrate containers
- Auto-restart failed services
- Auto-scale based on load
- Rolling updates (zero downtime deploys)
- Resource limits (prevent runaway services)

What we'll do:
├── Write Kubernetes manifests (YAML)
├── Deploy to local K8s (Docker Desktop)
├── Test scaling & self-healing
└── Practice rolling updates
```

#### AWS Deployment (Week 5+)
```
Move to cloud:
├── Push images to AWS ECR
├── Deploy to AWS EKS (Elastic Kubernetes Service)
├── Setup RDS for PostgreSQL
├── Setup MSK for Kafka
├── Configure load balancer
└── Test in production-like environment
```

---

## 📊 Success Criteria

### After Phase 1 (API Gateway)
- ✅ Gateway running on port 8080
- ✅ All requests must have valid JWT
- ✅ Rate limiting working
- ✅ Can route requests to all 5 services
- ✅ Updated Postman collection with auth

### After Phase 2 (Resilience4j)
- ✅ Circuit breaker prevents cascading failures
- ✅ Retries work with exponential backoff
- ✅ Timeouts prevent hanging requests
- ✅ Graceful degradation when services fail
- ✅ All tests passing

### After Phase 3 (E2E Testing)
- ✅ Complete payment flow works with authentication
- ✅ All failure scenarios handled gracefully
- ✅ Rate limiting works as expected
- ✅ System resilient to service failures
- ✅ Production-ready code

### After Phase 4 (Infrastructure)
- ✅ All services containerized
- ✅ Deployed to local Kubernetes
- ✅ Auto-scaling works
- ✅ Self-healing verified
- ✅ Ready for AWS deployment

---

## 🎯 Learning Objectives

By the end, you'll understand:

### Architecture
- Why systems need API Gateways
- How microservices communicate safely
- Resilience patterns in distributed systems
- Container orchestration principles

### Security
- JWT authentication/authorization
- Rate limiting and abuse prevention
- Token-based security

### Reliability
- Circuit breaker pattern
- Graceful degradation
- Retry strategies
- Timeout handling

### DevOps
- Docker containerization
- Kubernetes orchestration
- Cloud deployment (AWS)
- Infrastructure as Code

---

## 📅 Week-by-Week Schedule

**Week 1:**
- Days 1-2: Design and plan API Gateway
- Days 3-4: Implement API Gateway
- Days 5: JWT integration

**Week 2:**
- Days 1-3: Implement Resilience4j
- Days 4-5: Testing and refinement

**Week 3:**
- Days 1-2: Update Postman collection
- Days 3-5: E2E testing all scenarios

**Week 4:**
- Days 1-3: Create Dockerfiles
- Days 4-5: Docker setup and testing

**Week 5+:**
- Days 1-3: Kubernetes manifests
- Days 4-5: Deploy locally
- Days 6+: AWS deployment

---

## 🚀 Next Step

**Start with API Gateway implementation:**

We'll build this step-by-step:
1. Explain what Spring Cloud Gateway is
2. Create the new service
3. Configure routes
4. Add JWT validation
5. Test with Postman

Ready to start? I'll explain everything as we go! 🎓

---

**Remember:** The goal is not just to build it, but to UNDERSTAND why we're building it this way. Each step has a reason!
