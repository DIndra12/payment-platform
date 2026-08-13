# Payments Platform — Microservices Design Document

**Purpose:** Learning project demonstrating sync + async inter-service communication, containerization, Kubernetes orchestration, and AWS deployment in the payments domain.

---

# PART 1 — HIGH LEVEL DESIGN (HLD)

## 1.1 Goals

- Demonstrate synchronous (Feign/REST) and asynchronous (Kafka) service-to-service communication
- Demonstrate correct microservice boundaries with independent data ownership
- Demonstrate containerization → local K8s → AWS EKS deployment path
- Demonstrate observability: distributed tracing, metrics, structured logs
- Demonstrate payments-domain concerns: idempotency, consistency across services, failure handling

## 1.2 System Context

A simplified payment processing platform where a client initiates a payment, the system validates the payer's account and risk profile synchronously, commits the transaction, then asynchronously notifies the user and updates read-optimized transaction history.

## 1.3 Services

| Service | Responsibility | Owns Data |
|---|---|---|
| **API Gateway** | Single entry point, routing, auth (JWT), rate limiting | none |
| **Payment Service** | Orchestrates payment lifecycle (the "saga" coordinator) | payments table |
| **Account Service** | Account balances, debit/credit ledger operations | accounts, ledger_entries |
| **Fraud Service** | Real-time risk scoring before payment commit | risk_rules, risk_history |
| **Notification Service** | Sends email/SMS on payment events (consumer only) | notification_log |
| **Transaction History Service** | Builds a queryable read model of completed transactions (CQRS-style, consumer only) | transaction_history (denormalized) |

Each service = own Aurora schema/database. No service reaches into another's tables directly. This is the core microservices rule this project is meant to teach.

## 1.4 Communication Design

**Synchronous — Feign (blocking, request/response needed before proceeding):**
- Payment Service → Account Service: check & reserve balance
- Payment Service → Fraud Service: get risk score

Rule of thumb used here: if the caller *cannot proceed* without the answer, it's sync.

**Asynchronous — Kafka (fire-and-forget, eventual consistency acceptable):**
- Payment Service publishes `payment.completed` / `payment.failed` events
- Notification Service and Transaction History Service consume independently
- Account Service publishes `account.debited` / `account.credited` events (audit trail / future consumers)

Rule of thumb: if the result doesn't block the caller's response to *its* caller, it's async.

## 1.5 High-Level Flow (initiate payment)

```
Client → API Gateway → Payment Service
                            │
                 (sync, Feign) ├──► Fraud Service      (risk check)
                            │
                 (sync, Feign) ├──► Account Service     (reserve + debit)
                            │
                        [commit payment record]
                            │
                 (async, Kafka) ──► topic: payment.completed
                                        ├──► Notification Service (consumer)
                                        └──► Transaction History Service (consumer)
```

## 1.6 Technology Stack

| Concern | Choice |
|---|---|
| Language/Framework | Java 21, Spring Boot 3.x |
| Sync inter-service calls | Spring Cloud OpenFeign + Resilience4j (circuit breaker, retry, timeout) |
| Async messaging | Apache Kafka, Spring Kafka, Avro or JSON schema |
| Service discovery | Kubernetes DNS (no Eureka needed once on K8s) |
| Database | Aurora PostgreSQL, one schema per service, Flyway for migrations |
| API Gateway | Spring Cloud Gateway |
| Tracing | OpenTelemetry SDK → AWS X-Ray (or Jaeger locally) |
| Metrics | Micrometer → Prometheus + Grafana |
| Logging | Structured JSON (Logback + logstash-encoder) → CloudWatch Logs / EFK |
| Containerization | Docker, multi-stage builds |
| Orchestration | Kubernetes — Kind/Minikube locally, EKS on AWS |
| CI/CD | GitHub Actions → ECR → EKS |
| Secrets | AWS Secrets Manager + K8s External Secrets Operator |
| Auth (service-to-service) | OAuth2 Client Credentials + JWT, Keycloak (or AWS Cognito) as issuer |

## 1.7 Deployment Topology (AWS target state)

- **EKS** cluster running all microservices as separate Deployments + Services
- **MSK** (Managed Streaming for Kafka) instead of self-hosted Kafka
- **Aurora PostgreSQL** — single cluster, separate schema per service (or separate databases if you want harder isolation)
- **ALB Ingress Controller** for external routing into API Gateway
- **AWS X-Ray** for distributed tracing, **CloudWatch** for logs/metrics (or self-hosted Prometheus/Grafana on EKS)
- **Secrets Manager** for DB credentials, rotated and mounted via External Secrets Operator
- **ECR** for container images

## 1.8 Non-Functional Considerations to Design For

- **Idempotency**: payment initiation must be safe to retry (idempotency key from client)
- **Consistency**: no distributed transactions across services — use the Saga pattern (orchestrated by Payment Service) with compensating actions
- **Resilience**: circuit breakers on Feign calls, dead-letter topics for Kafka consumers
- **Traceability**: a single trace ID must be visible across gateway → payment → account/fraud → kafka → notification/history

---

# PART 2 — LOW LEVEL DESIGN (LLD)

## 2.1 Payment Service

### REST API (exposed via Gateway)
```
POST /api/v1/payments
  Headers: Idempotency-Key: <uuid>
  Body: { payerAccountId, payeeAccountId, amount, currency }
  Response: 202 Accepted { paymentId, status: "PROCESSING" }

GET /api/v1/payments/{paymentId}
  Response: { paymentId, status, amount, createdAt, updatedAt }
```

### DB Schema — `payment_db`
```sql
payments (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR UNIQUE NOT NULL,
  payer_account_id UUID NOT NULL,
  payee_account_id UUID NOT NULL,
  amount NUMERIC(19,4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL, -- INITIATED, RISK_CHECKED, DEBITED, COMPLETED, FAILED, COMPENSATED
  failure_reason VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### Orchestration logic (Saga, orchestrated — not choreographed, for learning clarity)
1. Persist payment row, status = `INITIATED` (idempotency key check first — if exists, return existing result)
2. Call Fraud Service (sync, Feign) → if rejected, status = `FAILED`, publish `payment.failed`, return
3. Call Account Service (sync, Feign) to debit payer / credit payee → on failure, status = `FAILED`, publish `payment.failed`
4. status = `COMPLETED`, publish `payment.completed` to Kafka
5. If step 4's publish fails, use an **outbox table** (see 2.5) instead of publishing directly — do not let Kafka availability block the sync response

### Feign Clients
```java
@FeignClient(name = "account-service", fallbackFactory = AccountClientFallback.class)
public interface AccountServiceClient {
    @PostMapping("/api/v1/accounts/{accountId}/debit")
    DebitResponse debit(@PathVariable UUID accountId, @RequestBody DebitRequest request);
}

@FeignClient(name = "fraud-service")
public interface FraudServiceClient {
    @PostMapping("/api/v1/risk/evaluate")
    RiskResponse evaluate(@RequestBody RiskRequest request);
}
```
- Resilience4j: `circuitBreaker`, `retry` (max 2, only on 5xx/timeout, NOT on business 4xx), `timeLimiter` (e.g. 2s)
- Feign requests propagate `Idempotency-Key` and trace headers downstream

## 2.2 Account Service

### REST API
```
POST /api/v1/accounts/{accountId}/debit   { amount, referenceId }
POST /api/v1/accounts/{accountId}/credit  { amount, referenceId }
GET  /api/v1/accounts/{accountId}/balance
```

### DB Schema — `account_db`
```sql
accounts (
  id UUID PRIMARY KEY,
  owner_name VARCHAR,
  balance NUMERIC(19,4) NOT NULL,
  version BIGINT NOT NULL -- optimistic locking
)

ledger_entries (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL,
  amount NUMERIC(19,4) NOT NULL,
  entry_type VARCHAR(10), -- DEBIT, CREDIT
  reference_id UUID NOT NULL, -- payment id, used for idempotency
  created_at TIMESTAMP,
  UNIQUE(reference_id, entry_type)
)
```
- `UNIQUE(reference_id, entry_type)` makes debit/credit idempotent — a retried Feign call from Payment Service won't double-debit
- Optimistic locking (`version`) prevents lost updates under concurrent debits
- Publishes `account.debited` / `account.credited` to Kafka after commit (via outbox)

## 2.3 Fraud Service

### REST API
```
POST /api/v1/risk/evaluate  { payerAccountId, amount, currency }
  Response: { riskScore, decision: "APPROVE" | "REJECT", reasons: [] }
```
Stateless-ish scoring against simple rules stored in `risk_rules` (e.g. amount thresholds, velocity checks against `risk_history`). No downstream dependencies — good candidate to teach fast-fail/timeout behavior.

## 2.4 Kafka Design

| Topic | Producer | Consumers | Key | Partitions (local) |
|---|---|---|---|---|
| `payment.completed` | Payment Service | Notification, Transaction History | `paymentId` | 3 |
| `payment.failed` | Payment Service | Notification | `paymentId` | 3 |
| `account.debited` | Account Service | Transaction History | `accountId` | 3 |
| `account.credited` | Account Service | Transaction History | `accountId` | 3 |

**Event schema example (`payment.completed`):**
```json
{
  "eventId": "uuid",
  "paymentId": "uuid",
  "payerAccountId": "uuid",
  "payeeAccountId": "uuid",
  "amount": 1500.00,
  "currency": "INR",
  "status": "COMPLETED",
  "occurredAt": "2026-07-01T10:00:00Z",
  "traceId": "w3c-trace-id"
}
```
- Use Avro + Schema Registry if you want schema evolution practice, otherwise JSON is fine for a learning project
- Each consumer service implements a **dead-letter topic** (`payment.completed.DLT`) for messages that fail after N retries (Spring Kafka's `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`)
- Consumers are idempotent: dedupe on `eventId` before processing (store processed IDs, or rely on upsert semantics in the read model)

## 2.5 Outbox Pattern (important — ties sync commit to async publish)

To avoid the classic "DB commit succeeded but Kafka publish failed" problem:
1. Within the same DB transaction as the payment status update, insert a row into an `outbox_events` table
2. A separate poller (or Debezium CDC in a more advanced version) reads unpublished outbox rows and publishes them to Kafka, then marks them published
3. This guarantees at-least-once delivery without distributed transactions

```sql
outbox_events (
  id UUID PRIMARY KEY,
  aggregate_id UUID,
  event_type VARCHAR,
  payload JSONB,
  published BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP
)
```

## 2.6 Notification Service & Transaction History Service

Both are pure Kafka consumers, no inbound REST needed for the core flow (though you can add a query API on Transaction History for a UI later).

```java
@KafkaListener(topics = "payment.completed", groupId = "notification-service")
public void onPaymentCompleted(PaymentCompletedEvent event) {
    // dedupe check → send notification → log
}
```

## 2.7 Observability Implementation

- **Trace propagation:** OpenTelemetry auto-instrumentation for Spring Boot picks up incoming HTTP, propagates `traceparent` header through Feign automatically, and through Kafka via header injection (`traceId` in event payload + Kafka headers) so consumers can continue the trace
- **Correlation ID / MDC:** Logback pattern includes `traceId` and `spanId` in every log line (JSON format) so logs, traces, and metrics can be cross-referenced in one query
- **Metrics:** Micrometer exposes `/actuator/prometheus` on every service; key custom metrics: `payment.processing.duration`, `payment.status.count{status=}`, Kafka consumer lag
- **Dashboards:** Grafana — one dashboard for "payment funnel" (initiated → risk-checked → debited → completed/failed counts), one for infra (pod CPU/mem, Kafka lag)

## 2.8 Sequence Diagram — Happy Path

```
Client → Gateway → Payment Svc → Fraud Svc      (sync, ~50ms)
                        │
                        └──► Account Svc         (sync, ~80ms, debit+credit)
                        │
                   [commit + outbox row]
                        │
                   202 response ──────────────────► Client
                        │
              [outbox poller publishes]
                        │
                   Kafka: payment.completed
                    ├──► Notification Svc (async)
                    └──► Transaction History Svc (async)
```

## 2.9 Suggested Build Order (mapped to this design)

1. Account Service + Fraud Service (standalone, own DBs, unit tested)
2. Payment Service with Feign clients to both — get the sync saga working end-to-end locally
3. Add outbox table + poller in Payment Service, wire Kafka producer
4. Notification + Transaction History as Kafka consumers, with DLT handling
5. Add OpenTelemetry + Micrometer to all services, verify one trace spans the whole flow
6. Dockerize everything, docker-compose up (Kafka, Postgres substitute, all services)
7. K8s manifests locally (Kind), then Helm chart per service
8. AWS: Aurora, MSK, EKS, X-Ray, ECR, CI/CD pipeline

## 2.10 Service-to-Service Authentication (JWT)

Two separate trust boundaries to secure: **Client → Gateway → Payment Service** (end-user identity) and **Service → Service** (machine identity, e.g. Payment → Account, Payment → Fraud). Don't conflate them — a service call should be authenticated as *that service*, not just by forwarding the user's token unchanged.

### Identity Provider
Run **Keycloak** (self-hosted on K8s, easiest for a learning setup — no AWS account needed to start) as the OAuth2/OIDC issuer. Swap to **AWS Cognito** later if you want the AWS-native path.

Each microservice is registered as a Keycloak **client** with `client_credentials` grant enabled:
```
client_id: payment-service        client_id: account-service
client_secret: <stored in Secrets Manager / K8s Secret>
```

### Token Flow

**End-user → Gateway:** Client authenticates normally (password/OIDC login), gets a user JWT, sends it as `Authorization: Bearer <user-jwt>` to the Gateway. Gateway validates signature via Keycloak's JWKS endpoint and forwards the request.

**Gateway → Payment Service:** Gateway passes the validated user JWT through (Payment Service also validates it — never trust a downstream hop blindly, validate at every service boundary this is meant to teach that).

**Payment Service → Account/Fraud Service (the actual s2s hop):** Payment Service does **not** forward the user's JWT as-is. Instead it obtains its **own** service JWT via the client_credentials grant and attaches that. This way Account Service knows the call came from "payment-service" as a trusted client, with its own scope, independent of the end user's session lifetime.

```java
// Feign RequestInterceptor — attaches a service JWT to every outbound call
@Component
public class ServiceJwtInterceptor implements RequestInterceptor {

    private final OAuth2AuthorizedClientManager clientManager;

    @Override
    public void apply(RequestTemplate template) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
            .withClientRegistrationId("payment-service-client")
            .principal("payment-service")
            .build();
        OAuth2AuthorizedClient client = clientManager.authorize(request);
        template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
    }
}
```
Spring Security's `spring-security-oauth2-client` handles token caching and refresh automatically — the interceptor doesn't need to manage expiry itself.

### Resource Server Side (Account, Fraud services)
Each service is a Spring Security **OAuth2 Resource Server**, validating incoming JWTs against Keycloak's JWKS:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/payments
```
Add a scope/role check so Account Service only accepts calls carrying a `service` scope claim issued to known clients (e.g. `payment-service`), not just any valid token:
```java
.requestMatchers("/api/v1/accounts/**")
    .hasAuthority("SCOPE_account:write")
```

### Async (Kafka) side
JWTs don't map cleanly onto pub/sub since there's no request/response handshake. Two complementary controls instead:
- **Transport/broker-level:** Kafka SASL_SSL with per-service credentials (or mTLS) + Kafka ACLs — e.g. `payment-service` principal is only granted `WRITE` on `payment.*` topics, `notification-service` only `READ`. This is the actual enforcement layer for Kafka.
- **Event-level (optional, more advanced):** carry the originating service's identity as a header on each Kafka message and have consumers check it against an allow-list before processing — useful mainly for audit/traceability, not as the primary security control.

### Local Development Note
Running Keycloak adds real setup overhead. For the earliest build steps (Account + Fraud + Payment sync flow), it's fine to stub this out with a shared static test JWT or skip auth entirely, then wire in Keycloak once the core saga works end-to-end — don't let auth setup block getting the actual service communication working first.

---

# PART 3 — SERVICE-WISE LLD SUMMARY

A consistent breakdown per service: what it does, what it talks to, and — since this is a learning project — what concept each service exists to teach you.

## 3.1 API Gateway

**What it teaches:** single entry point, JWT validation, routing, cross-cutting concerns kept out of business services.

| | |
|---|---|
| Exposes | Public HTTPS endpoints (`/api/v1/**`), routes to internal services |
| Calls (sync) | None directly — pure reverse proxy/router |
| Publishes (Kafka) | None |
| Consumes (Kafka) | None |
| Data owned | None (stateless) |
| Auth role | Validates end-user JWT (signature + expiry via Keycloak JWKS); rejects unauthenticated requests before they reach any service |

## 3.2 Payment Service

**What it teaches:** saga orchestration, combining sync + async in one flow, the outbox pattern, idempotency.

| | |
|---|---|
| Exposes | `POST /api/v1/payments`, `GET /api/v1/payments/{id}` |
| Calls (sync, Feign) | Fraud Service (`/risk/evaluate`), Account Service (`/accounts/{id}/debit`, `/credit`) |
| Publishes (Kafka) | `payment.completed`, `payment.failed` (via outbox poller, not directly) |
| Consumes (Kafka) | None — it's the flow's origin, not a listener |
| Data owned | `payments`, `outbox_events` |
| Auth role | Validates incoming user JWT; obtains its own service JWT (client_credentials) to call Account/Fraud |
| Key thing to get right | The saga: what happens if Account debit fails after Fraud passed? (→ status `FAILED`, no compensating action needed here since nothing committed yet). What if debit succeeds but the outbox publish is delayed? (→ fine, that's the point of the outbox — eventual, not lost) |

## 3.3 Account Service

**What it teaches:** idempotent writes, optimistic locking, being a "dumb" resource server that trusts nothing blindly.

| | |
|---|---|
| Exposes | `POST /accounts/{id}/debit`, `POST /accounts/{id}/credit`, `GET /accounts/{id}/balance` |
| Calls (sync, Feign) | None — leaf service |
| Publishes (Kafka) | `account.debited`, `account.credited` (via its own outbox) |
| Consumes (Kafka) | None |
| Data owned | `accounts`, `ledger_entries` |
| Auth role | Resource server — validates the service JWT from Payment Service and checks `SCOPE_account:write` |
| Key thing to get right | `UNIQUE(reference_id, entry_type)` on `ledger_entries` — this is what makes a retried Feign call safe (Payment Service may retry on timeout even if the first debit actually succeeded) |

## 3.4 Fraud Service

**What it teaches:** a fast, stateless(ish) sync dependency, and how the caller should behave when a dependency times out (fail closed vs fail open — a genuine design decision to make here).

| | |
|---|---|
| Exposes | `POST /risk/evaluate` |
| Calls (sync, Feign) | None — leaf service |
| Publishes (Kafka) | None (could add `risk.flagged` later as a stretch goal) |
| Consumes (Kafka) | None |
| Data owned | `risk_rules`, `risk_history` |
| Auth role | Resource server — validates service JWT, `SCOPE_risk:read` |
| Key thing to get right | Decide and document: if Fraud Service times out, does Payment Service reject the payment (fail closed — safer) or proceed anyway (fail open — better UX, riskier)? This is a real payments-industry tradeoff worth reasoning through, not just defaulting to one. |

## 3.5 Notification Service

**What it teaches:** pure async consumer, at-least-once delivery handling, dead-letter topics.

| | |
|---|---|
| Exposes | None required for core flow (optional query API later) |
| Calls (sync, Feign) | None |
| Publishes (Kafka) | None (could add `notification.sent` for its own audit trail) |
| Consumes (Kafka) | `payment.completed`, `payment.failed` |
| Data owned | `notification_log` |
| Auth role | None inbound (Kafka ACLs are the control here, not JWT) |
| Key thing to get right | Dedupe on `eventId` before sending — Kafka is at-least-once, so the same event can arrive twice. If you don't dedupe, a user gets two SMS for one payment. |

## 3.6 Transaction History Service

**What it teaches:** CQRS-style read model, building a denormalized view from multiple event sources.

| | |
|---|---|
| Exposes | `GET /transactions/{accountId}` (read model query API — this is the one service worth adding a real query endpoint to) |
| Calls (sync, Feign) | None |
| Publishes (Kafka) | None |
| Consumes (Kafka) | `payment.completed`, `payment.failed`, `account.debited`, `account.credited` |
| Data owned | `transaction_history` (denormalized, built by combining multiple events per transaction) |
| Auth role | Same as Notification Service — Kafka ACLs control ingress, JWT controls its own query API if exposed via Gateway |
| Key thing to get right | This service listens to *four* topics and has to stitch events together into one coherent row per transaction — good exercise in handling out-of-order or partial event arrival (e.g. `account.debited` might arrive before `payment.completed`) |

## 3.7 What This Project Demonstrates, End to End

| Concept | Where you see it |
|---|---|
| Sync service calls | Payment → Account, Payment → Fraud (Feign) |
| Async service calls | Payment → Notification/Transaction History (Kafka) |
| Saga pattern | Payment Service orchestration logic |
| Idempotency | `Idempotency-Key` header, `UNIQUE(reference_id, entry_type)` in ledger |
| Reliable async publish | Outbox pattern in Payment + Account Service |
| At-least-once handling | Dedupe-on-consume in Notification + Transaction History |
| CQRS read model | Transaction History Service |
| Service identity auth | Client credentials JWT flow (2.10) |
| Distributed tracing | Trace ID propagated across every hop above, sync and async |
| Deployment progression | Docker Compose → local K8s → EKS + Aurora + MSK |

---

# PART 4 — KUBERNETES / HELM CHART STRUCTURE

## 4.1 Approach: Umbrella chart + per-service subcharts

Six independent services with near-identical shape (Deployment, Service, ConfigMap, HPA, probes) is exactly the case Helm's **library/umbrella chart pattern** is for: one shared template library so you're not copy-pasting boilerplate six times, plus a parent chart that deploys everything together for local dev and lets you deploy services individually in real environments.

```
payments-platform/
├── charts/
│   ├── common/                      # library chart — shared templates, no resources of its own
│   │   ├── Chart.yaml                (type: library)
│   │   └── templates/
│   │       ├── _deployment.tpl
│   │       ├── _service.tpl
│   │       ├── _hpa.tpl
│   │       ├── _configmap.tpl
│   │       ├── _serviceaccount.tpl
│   │       └── _helpers.tpl          # labels, names, fullname helpers
│   │
│   ├── payment-service/
│   │   ├── Chart.yaml                 (depends on: common)
│   │   ├── values.yaml
│   │   ├── values-local.yaml
│   │   ├── values-aws.yaml
│   │   └── templates/
│   │       ├── deployment.yaml        # calls common._deployment
│   │       ├── service.yaml
│   │       ├── hpa.yaml
│   │       ├── configmap.yaml
│   │       ├── secret-external.yaml   # ExternalSecret CR, not raw secret
│   │       └── servicemonitor.yaml    # Prometheus scrape config
│   │
│   ├── account-service/               (same structure)
│   ├── fraud-service/                 (same structure)
│   ├── notification-service/          (same structure)
│   ├── transaction-history-service/   (same structure)
│   ├── api-gateway/
│   │   └── templates/
│   │       └── ingress.yaml           # only this chart needs an Ingress
│   │
│   └── keycloak/                      # or use Bitnami's chart as a dependency
│
├── Chart.yaml                         # umbrella chart, lists all above as dependencies
├── values.yaml                        # global values (image registry, namespace, env)
├── values-local.yaml                  # Kind/Minikube overrides
├── values-aws.yaml                    # EKS overrides (Aurora endpoint, MSK brokers, IRSA)
└── environments/
    ├── local/
    │   └── kafka-postgres.yaml         # docker-compose or K8s manifests for local Kafka/PG
    ├── dev/
    └── prod/
```

## 4.2 Why a library chart, not just copy-pasted YAML

Each service's `deployment.yaml` template ends up as just:
```yaml
{{- include "common.deployment" . }}
```
with all the actual Deployment spec (probes, resource limits, env-from-configmap, security context) defined **once** in `charts/common/templates/_deployment.tpl` using `{{ .Values.X }}` placeholders. Six services stay consistent by construction instead of by discipline — if you fix a probe path bug, you fix it in one file, not six.

## 4.3 Per-service `values.yaml` shape

```yaml
image:
  repository: <ecr-repo>/payment-service
  tag: latest
replicaCount: 2

resources:
  requests: { cpu: 250m, memory: 256Mi }
  limits:   { cpu: 500m, memory: 512Mi }

probes:
  liveness:  { path: /actuator/health/liveness,  port: 8080 }
  readiness: { path: /actuator/health/readiness, port: 8080 }

env:
  SPRING_PROFILES_ACTIVE: "k8s"
  KAFKA_BOOTSTRAP_SERVERS: "{{ .Values.global.kafka.brokers }}"

configFrom:
  configMapRef: payment-service-config

secretsFrom:
  externalSecretRef: payment-service-db-credentials   # pulled from AWS Secrets Manager

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 6
  targetCPUUtilization: 70

serviceMonitor:
  enabled: true    # Prometheus Operator scrape config
```

## 4.4 Environment overlay strategy

Rather than separate charts per environment, one chart + layered values files:
```bash
# local (Kind)
helm install payments ./payments-platform -f values.yaml -f values-local.yaml

# AWS (EKS)
helm install payments ./payments-platform -f values.yaml -f values-aws.yaml
```
`values-local.yaml` points at in-cluster Kafka/Postgres (or docker-compose, run outside K8s); `values-aws.yaml` points `KAFKA_BOOTSTRAP_SERVERS` at MSK and DB host at the Aurora endpoint, and turns on IRSA (IAM Roles for Service Accounts) annotations on the ServiceAccount instead of static AWS credentials.

## 4.5 Namespace layout

```
payments-dev        # all 6 services + gateway, dev workloads
payments-prod        # same set, prod config
observability        # Prometheus, Grafana, Jaeger/OTel Collector (shared, not per-env)
keycloak              # shared auth, one instance serving both envs via realm separation
```

## 4.6 Templates every service chart needs (via the common library)

| Template | Purpose |
|---|---|
| `deployment.yaml` | pod spec, probes, resource limits, env from ConfigMap + ExternalSecret |
| `service.yaml` | ClusterIP, used for both Feign DNS resolution and Prometheus scraping |
| `hpa.yaml` | scale on CPU (and later, custom metric — Kafka consumer lag for the consumer services) |
| `configmap.yaml` | non-secret config (topic names, feature flags) |
| `secret-external.yaml` | `ExternalSecret` CR — pulls DB creds / OAuth client secret from AWS Secrets Manager at runtime, never stored in the chart itself |
| `serviceaccount.yaml` | one per service, annotated with IRSA role ARN on AWS for least-privilege AWS API access (e.g. only Transaction History's SA gets read access if it ever queries S3) |
| `servicemonitor.yaml` | tells Prometheus Operator to scrape `/actuator/prometheus` |
| `networkpolicy.yaml` | *(recommended addition)* restrict ingress to each service — e.g. Account Service should only accept traffic from Payment Service's pod label, not from Notification Service |

Only **api-gateway**'s chart needs an `ingress.yaml` (ALB Ingress Controller annotations on AWS) — the internal services stay `ClusterIP`-only and unreachable from outside the cluster, which is itself a useful thing to enforce and verify.

## 4.7 Local dev shortcut

For fast inner-loop development, pair this with **Skaffold** or **Tilt**: `skaffold dev` watches source, rebuilds the changed service's image, and re-runs `helm upgrade` for just that subchart — much faster than rebuilding and redeploying the whole umbrella chart on every code change.

## 4.8 Suggested build order for this part

1. Write the `common` library chart first against **one** service (Payment) end to end — deployment, service, probes working in Kind
2. Extract that into the library templates, apply to the remaining five services
3. Add ConfigMap/ExternalSecret wiring once Keycloak + Secrets Manager are in play
4. Add HPA + ServiceMonitor once Prometheus is deployed
5. Add NetworkPolicy last, once you can see actual traffic patterns to lock down

---

*Next steps once you're ready to build: scaffold Account + Fraud services first, or design the K8s/Helm chart structure — whichever you'd like to start with.*
