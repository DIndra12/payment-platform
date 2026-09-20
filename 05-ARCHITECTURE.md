# 05 - System Architecture

## 🏛️ High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Clients (Web, Mobile, External)                             │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP/REST
                     ▼
┌──────────────────────────────────────────────────────────────┐
│ API GATEWAY (Port 8080)                                     │
│ ├─ JWT Token Validation                                     │
│ ├─ Rate Limiting (100 req/min per user)                    │
│ ├─ Request Routing                                          │
│ └─ User Context Propagation                                 │
└────────────┬──────────────────────────────────────────────┬─┘
             │                                              │
        Routes to:                                      Routes to:
        ┌─────────────────────────────────────────────────────────┐
        ▼                    ▼              ▼              ▼       ▼
    ┌─────────┐      ┌────────────┐   ┌────────┐     ┌──────────┐ ┌────────────┐
    │Account  │      │ Payment    │   │ Fraud  │     │Notif.    │ │Transaction│
    │Service  │      │ Service    │   │Service │     │Service   │ │ History   │
    │(8081)   │      │(8083)      │   │(8082)  │     │(8084)    │ │(8085)     │
    │         │      │            │   │        │     │          │ │           │
    │ Balance │      │ Saga       │   │ Risk   │     │ Event    │ │ CQRS      │
    │ Ledger  │      │ @Circuit   │   │ ML     │     │ Consumer │ │ Read      │
    │ Debit   │      │ @Retry     │   │ Rules  │     │ Processor│ │ Model     │
    │ Credit  │      │ @Timeout   │   │        │     │          │ │           │
    └────┬────┘      └────────┬───┘   └────┬───┘     └─────┬────┘ └────┬──────┘
         │                    │            │              │          │
         └──────┬─────────────┼────────────┤──────────────┤──────────┘
                │             │            │              │
                └─────────────┼────────────┼──────────────┘
                              ▼            ▼
                         ┌──────────────────────────┐
                         │ Kafka Message Broker    │
                         │ (9094)                   │
                         │ ├─ payment.completed    │
                         │ ├─ payment.failed       │
                         │ ├─ account.debited      │
                         │ └─ account.credited     │
                         └──────────┬───────────────┘
                                    │
                              ┌─────┴─────────────┬─────────────┐
                              ▼                   ▼             ▼
                         ┌──────────┐        ┌─────────┐   ┌────────┐
                         │PostgreSQL│        │ Keycloak│   │ Volumes│
                         │ (5432)   │        │(8080)   │   │        │
                         │ 5x DBs   │        │Identity │   │ Data   │
                         └──────────┘        │Provider │   │        │
                                             └─────────┘   └────────┘
```

---

## 📊 Request Flow Example: Create Payment

```
1. User makes request via UI
   POST /api/v1/payments
   Authorization: Bearer eyJhbGci...
   {payerAccountId: 123, payeeAccountId: 456, amount: 100}
        ↓
2. API Gateway receives request
   └─ JwtAuthenticationFilter validates token
   └─ Extracts user info → X-User-Id: user1
   └─ RateLimitFilter checks bucket (user1 has 99 tokens left)
   └─ Route matches: /api/v1/payments → payment-service:8083
        ↓
3. Payment Service receives request
   └─ Receives X-User-Id header (knows who's making request)
   └─ Saga orchestration begins
        ├─ Step 1: Call Account Service to debit
        │  └─ @CircuitBreaker wraps call
        │  └─ @Retry attempts up to 3 times
        │  └─ @TimeLimiter waits max 2 seconds
        │  └─ If fails: Fallback returns graceful error
        │
        ├─ Step 2: Call Fraud Service to assess risk
        │  └─ Similar resilience wraps this call too
        │
        ├─ Step 3: Emit payment.completed event to Kafka
        │  └─ Guarantees delivery via outbox pattern
        │
        └─ Step 4: Return response to client
             ↓ (via gateway)
        ↓
4. API Gateway returns response to client
   └─ 200 OK: {paymentId: 789, status: COMPLETED}
        ↓
5. Event Processing (Async)
   └─ Notification Service consumes payment.completed event
      └─ Sends SMS/Email to both accounts
   └─ Transaction History Service consumes events
      └─ Updates CQRS read model
```

---

## 🔄 Saga Pattern (Distributed Transactions)

```
Payment Saga:

Step 1: Debit Account
  Payment Service → Account Service
  Debit $100 from Payer
  ✓ Success → Continue
  ✗ Fail → Compensate (Rollback)

Step 2: Assess Risk
  Payment Service → Fraud Service
  Check risk for $100 payment
  ✓ Success & Low Risk → Continue
  ✗ Fail or High Risk → Compensate (Refund payer)

Step 3: Complete Payment
  Payment Service emits: payment.completed
  ✓ Notification Service receives → sends notification
  ✓ Transaction History receives → records transaction

If any step fails:
  └─ Compensation logic runs
  └─ Debit reversed
  └─ Payer refunded
  └─ Consistent state maintained
```

---

## 🛡️ Resilience Layers

```
Layer 1: API Gateway
├─ Authentication (JWT)
├─ Rate Limiting
└─ Fast Routing

Layer 2: Circuit Breaker
├─ Blocks calls to failing services
├─ Fails fast (100ms vs 2000ms)
└─ Auto-recovery after 30s

Layer 3: Retry
├─ Automatic retry (up to 3 times)
├─ Exponential backoff (100ms, 200ms, 400ms)
└─ Handles transient failures

Layer 4: Timeout
├─ Max 2 second wait
├─ Prevents hanging requests
└─ Thread safety
```

---

## 📈 Scaling Architecture

```
Current (Phase 2):
  Single instance each:
  ├─ API Gateway (8080)
  ├─ Account Service (8081)
  ├─ Payment Service (8083)
  └─ ... (other services)

Future (Phase 4 - Kubernetes):
  Multiple instances:
  ├─ API Gateway ×3 (load balanced)
  ├─ Account Service ×5 (auto-scale on CPU)
  ├─ Payment Service ×3 (auto-scale on requests)
  └─ ... (auto-scaling per service)
  
  Load Balancer → Service Discovery → Pods
  Auto-healing: Failed pod → replacement pod
```

---

## 🗄️ Data Architecture

```
Each Service Has Own Database:

Account Service DB          Payment Service DB
├─ accounts table           ├─ payments table
├─ ledger entries           ├─ outbox (for saga)
└─ balances                 └─ compensations

Fraud Service DB            Notification Service DB
├─ risk rules               ├─ events processed
├─ ML models                └─ notification log
└─ assessment history

Transaction History DB (Read Model)
├─ transactions (denormalized view)
└─ indexed for fast queries
```

---

## 🔐 Security Architecture

```
External (Internet)
    ↓
Firewall / WAF
    ↓
API Gateway (TLS/SSL in production)
├─ JWT Validation
├─ Rate Limiting
├─ CORS Handling
└─ Request Logging
    ↓
Services (Internal Only)
├─ Each validates X-User-Id header
├─ Circuit breaker for inter-service
└─ Secrets from environment
    ↓
Database
├─ Connection pooling
├─ Encrypted passwords
└─ SQL injection prevention
```

---

## 📡 Message Architecture

```
Services emit events to Kafka:

Account Service emits:
├─ account.debited
└─ account.credited

Payment Service emits:
├─ payment.completed
└─ payment.failed

Consumers subscribe:

Notification Service:
├─ Subscribes to: payment.*, account.*
└─ Sends notifications

Transaction History:
├─ Subscribes to: all events
└─ Updates read model

Outbox Pattern:
├─ Each service writes event to own DB
├─ Background job reads outbox
└─ Publishes to Kafka (guaranteed delivery)
```

---

## 🚀 Deployment Architecture (Phase 4)

```
Developer Workstation
    ↓
Git push
    ↓
CI/CD Pipeline
├─ Build Docker image
├─ Run tests
├─ Scan for vulnerabilities
└─ Push to ECR
    ↓
Kubernetes Cluster (AWS EKS)
├─ Pull image from ECR
├─ Create pods
├─ Service discovery
├─ Auto-scaling
└─ Health checks
    ↓
Load Balancer
├─ Distribute traffic
├─ SSL termination
└─ Health checks
    ↓
Services Running
├─ Auto-scaled
├─ Self-healing
├─ Rolling updates
└─ Zero downtime deploys
```

---

## 🎯 Services Overview

| Service | Port | Purpose | Database | Resilience |
|---------|------|---------|----------|-----------|
| **Gateway** | 8080 | Entry point | — | Rate limit + Auth |
| **Account** | 8081 | Balances | account_db | None (no dependencies) |
| **Fraud** | 8082 | Risk | fraud_db | None (no dependencies) |
| **Payment** | 8083 | Orchestration | payment_db | Circuit breaker + Retry + Timeout |
| **Notification** | 8084 | Events | notif_db | Kafka consumer |
| **Transaction** | 8085 | Read model | transaction_db | Event consumer |

---

## 📖 Next Steps

- Want to understand code? → **02-CODE-WALKTHROUGH.md**
- Want to test it? → **03-TESTING-GUIDE.md**
- Want to run it? → **04-SETUP-AND-RUN.md**
