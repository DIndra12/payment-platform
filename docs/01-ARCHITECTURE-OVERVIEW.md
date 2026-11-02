# Payment Platform Architecture & End-to-End Flow

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          EXTERNAL CLIENT                                    │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │ HTTPS
                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY SERVICE (8080)                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ • Spring Cloud Gateway (async/WebFlux)                              │   │
│  │ • OAuth2 integration with Keycloak                                  │   │
│  │ • Rate limiting (Bucket4j token bucket algorithm)                  │   │
│  │ • JWT validation                                                    │   │
│  │ • Request routing to backend services                              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└────┬────────────────────────────────────────────────────────────────────────┘
     │ Routes HTTP requests + Trace Context Headers
     │
     ├──────────────┬──────────────┬──────────────┬─────────────┬──────────────┐
     │              │              │              │             │              │
     ▼              ▼              ▼              ▼             ▼              ▼
┌─────────┐  ┌─────────┐  ┌──────────┐  ┌────────────┐  ┌──────────────┐ ┌──────────────┐
│ ACCOUNT │  │ FRAUD   │  │ PAYMENT  │  │ NOTIF.     │  │ TRANSACTION  │ │   (Future)   │
│ SERVICE │  │ SERVICE │  │ SERVICE  │  │ SERVICE    │  │ HISTORY SVC  │ │  Search/     │
│ (8081)  │  │ (8082)  │  │ (8083)   │  │ (8084)     │  │ (8085)       │ │  Analytics   │
└─────────┘  └─────────┘  └──────────┘  └────────────┘  └──────────────┘ └──────────────┘
     │              │              │              │             │
     ▼              ▼              ▼              ▼             ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                    SYNCHRONOUS COMMUNICATION (Feign)                         │
│                  (Saga pattern with circuit breakers)                        │
└──────────────────────────────────────────────────────────────────────────────┘
     │
     └──────────────────────────────────────────────────────────────────┐
                                                                        │
                       ┌─────────────────────────────────────────────┐  │
                       │                                             │  │
                       ▼                                             │  │
          ┌───────────────────────┐                                  │  │
          │   PostgreSQL (5432)   │                                  │  │
          │  ┌─────────────────┐  │                                  │  │
          │  │ account_db      │  │ ◄─────────────────────┐          │  │
          │  │ payment_db      │  │                       │          │  │
          │  │ fraud_db        │  │  (per-service DBs)    │          │  │
          │  │ notification_db │  │                       │          │  │
          │  │ transaction_db  │  │                       │          │  │
          │  └─────────────────┘  │                       │          │  │
          └───────────────────────┘                       │          │  │
                                                          │          │  │
                                ┌──────────────────────┐ │  ┌─────────┘  │
                                │                      │ │  │            │
                                ▼                      │ │  │            │
                    ┌──────────────────────┐           │ │  │            │
                    │   Kafka (9094)       │           │ │  │            │
                    │  ┌────────────────┐  │           │ │  │            │
                    │  │ payment.      │  │           │ │  │            │
                    │  │ completed     │  │ ◄─────────┘ │  │ Outbox     │
                    │  │               │  │             │  │ Pattern    │
                    │  │ payment.      │  │             │  │            │
                    │  │ failed        │  │             │  │            │
                    │  │               │  │             │  │            │
                    │  │ account.      │  │             │  │            │
                    │  │ debited       │  │             │  │            │
                    │  │               │  │             │  │            │
                    │  │ account.      │  │             │  │            │
                    │  │ credited      │  │             │  │            │
                    │  └────────────────┘  │             │  │            │
                    └──────────────────────┘             │  │            │
                           ▲                             │  │            │
                           └─────────────────────────────┘  │            │
                                                            │            │
                              ┌─────────────────────────────┘            │
                              │                                          │
     ┌────────────────────────┘                                          │
     │                                                                    │
     ▼                                                      ┌─────────────┘
  EVENT CONSUMER
  (Async: Updates read models, sends notifications, etc.)
```

## Service Responsibilities

### API Gateway Service
- **Role:** Entry point for all client requests
- **Technology:** Spring Cloud Gateway (async/WebFlux for high concurrency)
- **Responsibilities:**
  - OAuth2/JWT authentication via Keycloak
  - Rate limiting per user/API key
  - Request routing to backend services
  - Adding correlation IDs to requests
  - Propagating trace context headers
  
### Payment Service
- **Role:** Orchestrator for payment processing (Saga pattern)
- **Technology:** Spring Boot Web with Feign clients
- **Responsibilities:**
  - Initiate payment workflows
  - Call account-service (debit account) and fraud-service (validate risk)
  - Persist payment events to outbox
  - Publish payment.completed/payment.failed events to Kafka
  - Implement circuit breaker/retry on external calls

### Account Service
- **Role:** Manage account balances and ledger entries
- **Technology:** Spring Boot JPA with PostgreSQL
- **Responsibilities:**
  - Handle account debits and credits
  - Maintain ledger of all transactions
  - Publish account.debited/account.credited events (outbox pattern)
  - Prevent overdrafts via transaction isolation

### Fraud Service
- **Role:** Risk assessment for payments
- **Technology:** Spring Boot REST API
- **Responsibilities:**
  - Evaluate payment risk based on rules
  - Return risk score and decision
  - No persistence (stateless)
  - Lightweight, fast response time (<500ms target)

### Notification Service
- **Role:** Send notifications for payment events
- **Technology:** Spring Boot + Kafka consumer
- **Responsibilities:**
  - Consume payment.completed and payment.failed events
  - Send email/SMS notifications
  - Handle retry/DLQ for failures
  - Audit notification delivery

### Transaction History Service
- **Role:** Read model for transactions (CQRS)
- **Technology:** Spring Boot + Kafka consumer + PostgreSQL
- **Responsibilities:**
  - Build eventually-consistent read model
  - Consume all payment events from Kafka
  - Support fast queries for transaction history
  - Idempotent projection (replay-safe)

## Data Flow: Payment Processing End-to-End

### Happy Path (Successful Payment)

```
1. CLIENT REQUEST
   POST /api/payments
   { "accountId": "ACC123", "amount": 50.00, "recipientId": "RCPT456" }
                          │
                          ▼
2. API GATEWAY
   ├─ Validate JWT token (Keycloak)
   ├─ Check rate limit (Bucket4j)
   ├─ Generate correlationId, traceId, spanId
   ├─ Add headers: X-Trace-Id, X-Span-Id, X-Correlation-Id
   └─ Route to /api/payments on payment-service:8083
                          │
                          ▼
3. PAYMENT SERVICE (START SAGA)
   ├─ Create Payment entity (status=PENDING)
   ├─ Create Outbox event
   ├─ BEGIN TRANSACTION (all-or-nothing)
   │
   ├─ CALL: account-service (Feign + CircuitBreaker)
   │  POST /api/accounts/ACC123/debit
   │  { "amount": 50.00, "transactionId": "TXN789" }
   │        │
   │        ▼
   │  ACCOUNT SERVICE
   │  ├─ Lock account row (SERIALIZABLE isolation)
   │  ├─ Check balance >= 50.00
   │  ├─ Debit account
   │  ├─ Create ledger entry
   │  ├─ Create Outbox: account.debited event
   │  ├─ COMMIT (durability guaranteed)
   │  └─ Return: { "newBalance": 950.00, "success": true }
   │
   └─ CALL: fraud-service (Feign + CircuitBreaker)
      POST /api/fraud/check
      { "amount": 50.00, "accountId": "ACC123", "recipientId": "RCPT456" }
           │
           ▼
      FRAUD SERVICE
      ├─ Run fraud rules (no DB call)
      │  • Typical velocity checks
      │  • Amount thresholds
      │  • Account history
      ├─ Calculate risk_score (0-100)
      ├─ Decision: APPROVED (risk_score < 75)
      └─ Return: { "riskScore": 30, "decision": "APPROVED" }
                          │
                          ▼
4. PAYMENT SERVICE (SAGA COMPLETES)
   ├─ Both calls succeeded → status=COMPLETED
   ├─ Update Payment entity
   ├─ Update Outbox: payment.completed event
   ├─ COMMIT transaction (Durable write)
   └─ Return: { "paymentId": "PAY999", "status": "COMPLETED" }
                          │
                          ▼
5. ASYNC EVENT PROCESSING (Via Kafka)
   ┌─────────────────────────────────────────────────────┐
   │ OutboxPublisher polls Outbox table (background job) │
   │ ├─ SELECT * FROM outbox WHERE published=false       │
   │ ├─ Publish to Kafka topics                          │
   │ └─ Mark published=true                              │
   └──────────────┬──────────────────────────────────────┘
                  │
    ┌─────────────┴──────────────┬─────────────┐
    │                            │             │
    ▼                            ▼             ▼
NOTIFICATION SERVICE      TRANSACTION HISTORY  (FUTURE: Analytics)
├─ Consume:              ├─ Consume:
│  payment.completed     │  payment.completed
├─ Send email to user    │  account.debited
├─ Log event            ├─ Build materialized view
└─ Persist audit        │  on transaction_history table
                        └─ Support fast queries

6. OBSERVABILITY (CONTINUOUS)
   ├─ TRACING: Trace spans created at each hop
   │   ├─ api-gateway span
   │   ├─ payment-service span
   │   │   ├─ account-service (child span)
   │   │   └─ fraud-service (child span)
   │   └─ Full tree visible in Jaeger UI
   │
   ├─ LOGGING: JSON logs with traceId
   │   ├─ Each log includes: traceId, spanId, correlationId, service
   │   ├─ Fluent Bit ships logs to Elasticsearch
   │   └─ Search correlation across services in Kibana
   │
   └─ METRICS: Prometheus scrapes /actuator/prometheus
       ├─ http_server_requests_seconds (histogram)
       ├─ jvm_memory_used_bytes (gauge)
       ├─ tomcat_jdbc_connections_active (gauge)
       ├─ kafka_producer_record_send_errors_total (counter)
       └─ Grafana dashboards show in real-time
```

### Sad Path (Fraud Rejection)

```
1-3. Same as happy path until fraud-service
     
FRAUD SERVICE returns:
{ "riskScore": 85, "decision": "REJECTED" }
                          │
                          ▼
PAYMENT SERVICE (SAGA COMPENSATION)
├─ Fraud rejected → status=FAILED
├─ Call account-service to CREDIT back the debit
│  POST /api/accounts/ACC123/credit
│  { "amount": 50.00, "transactionId": "TXN789" }
│       │
│       ▼
│  ACCOUNT SERVICE reverses debit
│  └─ Creates Outbox: account.credited event
│
├─ Update Payment entity: status=FAILED, reason=FRAUD_REJECTED
├─ Update Outbox: payment.failed event
├─ COMMIT
└─ Return: { "paymentId": "PAY999", "status": "FAILED", "reason": "FRAUD_REJECTED" }
                          │
                          ▼
ASYNC EVENT PROCESSING
├─ NOTIFICATION SERVICE sends failure notification
└─ TRANSACTION HISTORY records the failed attempt
```

## Outbox Pattern (Ensuring Consistency)

The outbox pattern solves the distributed transaction problem:

```
PROBLEM: Payment service saves payment + publishes event
If publish fails, event is lost. If service crashes between save & publish, 
external consumers miss the event.

SOLUTION: Outbox Table
1. Payment service writes payment + outbox row in SAME transaction
2. Outbox row marked published=false
3. OutboxPublisher (background job) polls:
   SELECT * FROM outbox WHERE published=false
4. Sends to Kafka
5. Marks published=true
6. On crash, retry logic ensures delivery

GUARANTEE: Every payment generates exactly one event (idempotent consumers required).
```

## Resilience & Fault Tolerance

### Circuit Breaker (Resilience4j)
```
CLOSED state (normal):
  account-service requests pass through, latency tracked

THRESHOLD HIT:
  - 50% failure rate in 10-request sliding window
  - P95 latency > 3 seconds
  - Circuit OPENS

OPEN state (fail-fast):
  - New requests fail immediately without calling service
  - Prevents cascading failures
  - Wait 30 seconds

HALF-OPEN state:
  - Try 2 test requests to remote service
  - If both succeed → back to CLOSED
  - If either fails → back to OPEN
```

### Retry Strategy (Exponential Backoff)
```
Max 3 attempts per request:
  Attempt 1: immediate
  Attempt 2: wait 100ms
  Attempt 3: wait 200ms (2x)
  
Idempotent key in request header ensures retries are safe.
```

### Database Isolation
```
Account Service uses SERIALIZABLE isolation:
- No race conditions on account balance
- Prevents double-spend
- Sequential consistency for balance checks
```

## Observability Integration

### Tracing (OpenTelemetry + Micrometer)
- **Automatic:** Spring creates spans for HTTP requests, Feign calls, database calls
- **Propagation:** Trace headers passed downstream via Feign clients
- **Sampling:** 100% in dev (management.tracing.sampling.probability=1.0)
- **Export:** Spans sent to Jaeger for visualization
- **Correlation:** traceId groups all spans in a request flow

### Logging (Logstash + ELK)
- **Structured:** All logs output as JSON
- **Correlation:** traceId, spanId, correlationId in every log
- **Aggregation:** Fluent Bit ships logs to Elasticsearch
- **Search:** Query by traceId in Kibana to see complete flow
- **Example:** `{"timestamp":"2026-09-21T13:15:00Z","level":"INFO","logger":"com.payments.platform.PaymentService","message":"Processing payment","traceId":"abc123","spanId":"def456","correlationId":"pay999","service":"payment-service"}`

### Metrics (Prometheus + Grafana)
- **Collection:** Prometheus scrapes /actuator/prometheus every 10s
- **Metrics:**
  - `http_server_requests_seconds` (histogram of latencies)
  - `http_server_requests_seconds_count` (request count)
  - `jvm_memory_used_bytes` (JVM heap usage)
  - `tomcat_jdbc_connections_active` (connection pool)
  - `kafka_producer_record_send_errors_total` (Kafka failures)
- **Alerts:** Prometheus rule engine detects anomalies
- **Dashboards:** Grafana visualizes metrics in real-time

## Technology Stack Summary

| Layer | Technology | Purpose |
|-------|------------|---------|
| **API Gateway** | Spring Cloud Gateway, Keycloak, JWT | Entry point, auth, routing |
| **Services** | Spring Boot 3.3, Java 21 | Business logic microservices |
| **Sync Communication** | Spring Cloud Feign, Resilience4j | Cross-service calls with resilience |
| **Async Communication** | Apache Kafka 3.7 | Event streaming for saga pattern |
| **Databases** | PostgreSQL 16 | Per-service relational storage |
| **Migrations** | Flyway | Database schema versioning |
| **Tracing** | OpenTelemetry, Micrometer, Jaeger | Distributed request tracing |
| **Logging** | Logstash Encoder, Fluent Bit, Elasticsearch | Centralized log aggregation |
| **Metrics** | Prometheus, Grafana | Time-series metrics & alerts |
| **Deployment** | Docker Compose (dev), K8s (roadmap) | Container orchestration |

## Network Topology (Docker Compose)

```
services:
  postgres         → 5432 (all services connect)
  kafka            → 9094 (services publish/consume events)
  keycloak         → 8080 (auth for API gateway)
  elasticsearch    → 9200 (receives logs from Fluent Bit)
  fluent-bit       → 24224 (ingests logs from services)
  kibana           → 5601 (log dashboard)
  prometheus       → 9090 (scrapes metrics)
  grafana          → 3000 (metrics dashboard)
  
  api-gateway-service      → 8080 (client entry point)
  account-service          → 8081
  fraud-service            → 8082
  payment-service          → 8083
  notification-service     → 8084
  transaction-history-service → 8085
```

## Phase Roadmap

- **Phase 1: Core Services** ✅ (Completed)
  - Microservices, databases, Kafka, saga pattern
  
- **Phase 2: Observability** ✅ (Just completed)
  - Phase 2a: Distributed Tracing (OpenTelemetry/Jaeger)
  - Phase 2b: Centralized Logging (ELK Stack)
  - Phase 2c: Metrics & Dashboards (Prometheus/Grafana)
  
- **Phase 3: Advanced Features** (Next)
  - API documentation (OpenAPI/Swagger)
  - Load testing & performance tuning
  - Multi-region deployment simulation
  
- **Phase 4: Production Hardening** (Future)
  - Kubernetes deployment (service discovery, load balancing)
  - Istio service mesh (traffic management, circuit breaker)
  - Vault for secrets management
  - TLS/mTLS encryption
  
- **Phase 5: Analytics & Reporting** (Future)
  - Data warehouse (dbt + BigQuery/Snowflake)
  - Business intelligence dashboards
  - Fraud analytics ML models
