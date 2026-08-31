# Payments Platform — End-to-End Application Flow

> A single-page map of how the whole platform behaves at runtime: what each service does, how a payment travels through them synchronously, how events fan out asynchronously, and where the data lives. Everything here reflects the code as it actually is, not just the aspirational design — where the two differ, the difference is called out.

Related docs: [README](README.md) · [payments-microservices-design.md](payments-microservices-design.md) · [transaction-history-service-design.md](transaction-history-service-design.md)

---

## 1. The services at a glance

| Service | Port | Role | Owns (DB) | Sync API | Kafka produces | Kafka consumes |
|---|---|---|---|---|---|---|
| **payment-service** | 8083 | Saga orchestrator — the only writer of a payment | `payment_db`: `payments`, `outbox_event` | `POST /api/v1/payments` | `payment.completed`, `payment.failed` | — |
| **account-service** | 8081 | Accounts + double-entry ledger | `account_db`: `accounts`, `ledger_entries`, `outbox_event` | `POST /api/v1/accounts/{id}/debit`, `.../credit`, `GET /{id}/balance` | `account.debited`, `account.credited` | — |
| **fraud-service** | 8082 | Stateless risk scoring | none (rules in config) | `POST /api/v1/risk/evaluate` | — | — |
| **notification-service** | 8084 | Sends user notifications (mock) | `notification_db`: `notification_log` | — | — | `payment.completed`, `payment.failed` |
| **transaction-history-service** | 8085 | CQRS read model of transactions | `transaction_history_db`: `transaction_history`, `processed_event` | `GET /api/v1/transactions/...` | `*.DLT` only | all four topics above |
| *Kafka* | 9094 (host) | Event backbone (KRaft, single node) | — | — | — | — |
| *PostgreSQL* | 5432 | One database per service | — | — | — | — |
| *Keycloak* | 8080 | OAuth2/OIDC (provisioned, not yet wired) | — | — | — | — |

Two interaction styles run side by side:

- **Synchronous (Feign/HTTP):** used when the caller cannot proceed without an answer — payment → fraud, payment → account.
- **Asynchronous (Kafka via the outbox pattern):** used when eventual consistency is fine — payment/account → notification + transaction-history.

---

## 2. Master flow — a single payment, end to end

```
 ┌────────┐
 │ CLIENT │  curl / UI
 └───┬────┘
     │  POST /api/v1/payments
     │  Header: Idempotency-Key: <key>
     │  Body:   { payerAccountId, payeeAccountId, amount, currency }
     ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  PAYMENT SERVICE  :8083   PaymentController → PaymentOrchestratorService        ║
║                                                                                ║
║  (0) Idempotency: findByIdempotencyKey(key)                                    ║
║        └─ hit? ─────────────► return existing PaymentResponse (no side effects) ║
║                                                                                ║
║  (1) INSERT payment row  status=INITIATED           ── payment_db.payments     ║
║                                                                                ║
║  (2) Fraud check  ──Feign POST /api/v1/risk/evaluate──►┌─────────────────────┐ ║
║      { payerAccountId, amount, currency }              │  FRAUD SVC  :8082    │ ║
║      ◄──── { riskScore, decision, reasons } ───────────│  score amount vs.    │ ║
║                                                        │  threshold → APPROVE │ ║
║      decision == REJECT?                               │  / REJECT (stateless)│ ║
║        └─ yes ─► status=REJECTED_BY_FRAUD              └─────────────────────┘ ║
║                  write outbox_event(payment.failed) ─────────────┐             ║
║                  return 202 {REJECTED_BY_FRAUD}                   │             ║
║        └─ no ─► continue                                          │             ║
║                                                                  │             ║
║  (3) Debit payer ──Feign POST /accounts/{payer}/debit──►┌────────┴──────────┐  ║
║      { amount, referenceId = paymentId }                │ ACCOUNT SVC :8081 │  ║
║                                                         │  @Transactional:  │  ║
║  (4) Credit payee ─Feign POST /accounts/{payee}/credit─►│  • idempotency    │  ║
║      { amount, referenceId = paymentId }                │    (referenceId,  │  ║
║                                                         │     entry_type)   │  ║
║      any Feign call throws?                             │  • update balance │  ║
║        └─ yes ─► status=FAILED                          │  • INSERT ledger  │  ║
║                  write outbox_event(payment.failed)     │  • INSERT outbox  │  ║
║                  return 202 {FAILED}                    │    (same tx!)     │  ║
║        └─ no ─► continue                                └────────┬──────────┘  ║
║                                                                 │             ║
║  (5) status=COMPLETED                                           │             ║
║      write outbox_event(payment.completed)  ── payment_db       │             ║
║  (6) return 202 {COMPLETED, paymentId}                          │             ║
╚═════════════════════════════════╪══════════════════════════════╪═════════════╝
        202 Accepted ◄─────────────┘                              │
                                                                  │
   ── ASYNC from here: both services' @Scheduled outbox pollers (every 5s) ──
                                                                  │
   payment_db.outbox_event                     account_db.outbox_event
        │ poller, key = paymentId                    │ poller, key = accountId
        ▼                                            ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║                                 KAFKA  :9094                                    ║
║   payment.completed   payment.failed   account.debited   account.credited      ║
╚════════╪═══════════════════╪═══════════════════╪═══════════════════╪═══════════╝
         │                   │                   │                   │
   group=notification-service│             group=transaction-history-service
         ▼                   ▼                   ▼                   ▼
╔═════════════════════════════╗     ╔══════════════════════════════════════════╗
║ NOTIFICATION SVC  :8084     ║     ║ TRANSACTION HISTORY SVC  :8085           ║
║  dedupe on eventId          ║     ║  Payment/AccountEventConsumer            ║
║  build message              ║     ║   → TransactionProjector (@Transactional)║
║  send (mock)                ║     ║     1 dedupe (payment_id,event_type)     ║
║  INSERT notification_log    ║     ║     2 load-or-create row                 ║
╚═════════════════════════════╝     ║     3 null-safe merge                    ║
                                    ║     4 recompute status                   ║
                                    ║     5 save row + processed_event         ║
                                    ║  transaction_history_db (denormalized)   ║
                                    ╚══════════════════════════════════════════╝
                                                    ▲
   CLIENT ── GET /api/v1/transactions/account/{id} ─┘  (read side, port 8085)
```

**Key runtime facts baked into that picture:**

- The client gets its `202 Accepted` as soon as the synchronous saga finishes (steps 0–6). Notifications and history are built *afterwards*, asynchronously — so they are eventually consistent, typically within a few seconds (outbox poll interval + consumer lag).
- The debit and credit both use `referenceId = paymentId`. Account-service enforces `UNIQUE(reference_id, entry_type)`, which is what makes a retried debit/credit a no-op instead of double-charging.
- Each outbox row is written in the **same database transaction** as the state change that caused it. A separate poller moves rows to Kafka. That is the outbox pattern: the DB commit and the intent-to-publish can never disagree, even if Kafka is momentarily down.

---

## 3. Payment saga state machine (as coded)

The orchestrator (`PaymentOrchestratorService.processPayment`) is a synchronous saga. The `PaymentStatus` enum documents a richer set of intermediate states, but the code today sets only the ones marked ✅ below.

```
                    ┌───────────┐
                    │ INITIATED │  ✅ (row inserted)
                    └─────┬─────┘
                          │ fraud check
              REJECT ◄────┤────► APPROVE
                 │        │
                 ▼        │ debit payer  → credit payee   (Feign, sync)
      ┌────────────────┐  │        │
      │REJECTED_BY_FRAUD│ │        │  any call throws
      └────────┬────────┘ │        ▼
               │          │   ┌────────┐
               │          │   │ FAILED │  ✅
               │          │   └───┬────┘
               │          ▼       │
               │    ┌───────────┐ │
               │    │ COMPLETED │✅│
               │    └─────┬─────┘ │
               │          │       │
     publishes │  publishes       │ publishes
  payment.failed│ payment.completed│ payment.failed
               ▼          ▼        ▼
           ── Kafka outbox_event ──

  Documented-but-not-yet-set intermediate states (enum only):
  RISK_CHECKED, DEBITED, CREDITED, COMPENSATED
```

> **Honest gap:** there is **no compensation** yet. If the debit succeeds but the credit throws, the payment is marked `FAILED` and a `payment.failed` event is emitted, but the debit is **not** automatically reversed. The `COMPENSATED` state exists in the enum for the intended saga but is not wired. In practice the credit rarely fails independently in this learning setup, but it is the first thing to harden for real use.

---

## 4. The outbox → Kafka mechanism (shared by payment & account services)

Both producing services use the identical pattern:

```
 business @Transactional method (e.g. AccountService.debit / Payment updatePaymentState)
   │
   ├─ write business rows (ledger entry / payment status)
   └─ write outbox_event row  { aggregateType, aggregateId, eventType, payload(JSONB), published=false }
        └─ COMMIT  (both or neither)

 OutboxPublisher  @Scheduled(fixedDelay = 5s)   ← requires @EnableScheduling on the app class
   │  findByPublishedFalse()
   ▼
 OutboxSender.send(topic = eventType, key = aggregateId, payload)
   │  (OutboxSenderKafkaAdapter → KafkaTemplate<String,String>, StringSerializer)
   ▼
 Kafka topic  (topic name == eventType; message key == aggregateId)
   │
   └─ mark row published=true
```

Notes that matter for correctness:

- **`eventType` doubles as the topic name.** `payment.completed`, `account.debited`, etc. are both the DB column value and the Kafka topic.
- **The message key is the aggregate id** — `paymentId` for payment events, `accountId` for account events. This keeps all events for one aggregate on one partition and therefore ordered. Because the debit and credit legs are keyed by *different* accounts, they can still arrive at a consumer out of order relative to each other — which is why the read model is built to be order-independent (§6).
- **Value is a raw JSON string, not a typed record.** The payload is serialized to JSON once when the outbox row is written, then sent with a `StringSerializer`. Consumers therefore deserialize with a per-topic default type and `USE_TYPE_INFO_HEADERS=false` (no `__TypeId__` header is sent).
- **At-least-once.** Marking `published=true` is not transactional with the Kafka send, so a crash between send and mark can re-deliver. Consumers dedupe, so this is safe.

### Canonical event payloads (as emitted today)

```jsonc
// payment.completed            // payment.failed (adds failureReason)
{                               {
  "eventId":   "<uuid>",          "eventId":   "<uuid>",
  "paymentId": "<uuid>",          "paymentId": "<uuid>",
  "payerAccountId": "<uuid>",     "payerAccountId": "<uuid>",
  "payeeAccountId": "<uuid>",     "payeeAccountId": "<uuid>",
  "amount": 500.5000,             "amount": 500.5000,
  "currency": "INR",             "currency": "INR",
  "status": "COMPLETED",          "status": "FAILED",
  "occurredAt": "ISO-local",      "failureReason": "…",
  "traceId": "<mdc|null>"         "occurredAt": "ISO-local",
}                                 "traceId": "<mdc|null>"
                                }

// account.debited / account.credited
{ "eventId":"<uuid>", "referenceId":"<paymentId>", "accountId":"<uuid>",
  "ledgerEntryId":"<uuid>", "amount":500.5000, "entryType":"DEBIT|CREDIT",
  "occurredAt":"ISO-local", "traceId":"<mdc|null>" }
```

`referenceId` on the account events **is** the `paymentId` — that is the join key across all four topics.

---

## 5. Consumer flow — notification-service

```
 payment.completed / payment.failed
   │  @KafkaListener (group = notification-service, manual ack, one factory per type)
   ▼
 NotificationService.handlePaymentCompleted / handlePaymentFailed  @Transactional
   │
   ├─ existsByEventId(eventId)?  ── yes ─► skip (dedupe), ack
   │
   ├─ build subject + message + recipient (mock: payerAccountId@example.com)
   ├─ INSERT notification_log (status=PENDING)
   ├─ sendNotification(...)          ← mock: logs + 100ms sleep (swap for email/SMS)
   ├─ UPDATE status=SENT, sentAt
   └─ on exception → INSERT a FAILED notification_log row
```

Its purpose is a pure async consumer with at-least-once dedupe. Two known quirks (documented, pre-existing): it dedupes on `eventId` (now that producers send one, this works; before enrichment it would have been null), and it swallows exceptions into a `FAILED` log row rather than letting them reach a dead-letter path.

---

## 6. Consumer flow — transaction-history-service (CQRS read model)

This service consumes **all four topics** and stitches them into one denormalized row per payment.

```
 payment.completed │ payment.failed │ account.debited │ account.credited
   │ PaymentEventConsumer                    │ AccountEventConsumer
   │  (group = transaction-history-service, manual ack, one factory per type)
   ▼                                         ▼
 TransactionProjector.onXxx(event, ctx)   @Transactional
   │
   0. correlation key (paymentId / referenceId) present?  no → InvalidEventException → DLT
   1. DEDUPE   existsByPaymentIdAndEventType(pid, type)?   yes → SKIPPED_DUPLICATE, ack
   2. LOAD     findByPaymentId(pid) or create a new partial row
   3. MERGE    field = coalesce(existing, incoming)   ← never overwrite known with null
               recordEvent(type)                       ← append to events_seen (CSV)
   4. DERIVE   status = f(events_seen)                 ← recomputed, not transitioned
                 payment.completed seen → COMPLETED (wins)
                 payment.failed seen    → FAILED
                 only account.* so far  → IN_PROGRESS
   5. SAVE     transaction_history row  +  processed_event marker   (one tx)
   → consumer acks AFTER commit
   error → DefaultErrorHandler: retry 2× @1s → DeadLetterPublishingRecoverer → <topic>.DLT
```

Because status is recomputed from the full `events_seen` set every time and merges are null-safe, **the final row is identical regardless of the order the four events arrive in** — the headline property, proven by a unit test over all six orderings. A row may legitimately exist as `IN_PROGRESS` with gaps until the remaining events land; the query API surfaces that honestly.

Read side (port 8085):

```
 GET /api/v1/transactions/account/{accountId}          paged, filters: status, direction, from, to
 GET /api/v1/transactions/{paymentId}?accountId=…       single, 404 if not projected yet
 GET /api/v1/transactions/account/{accountId}/summary   counts + totalIn/out
```

`direction` (DEBIT/CREDIT) and `counterpartyAccountId` are derived **per request** from the queried account vs. the row's payer/payee — the stored row is account-neutral.

---

## 7. Data ownership map

```
┌──────────────────────┬──────────────────────┬────────────────┬──────────────────┬───────────────────────┐
│     payment_db        │     account_db        │   fraud_db*    │  notification_db │ transaction_history_db │
│  (payment-service)    │  (account-service)    │ (fraud-service)│(notification-svc)│ (transaction-hist-svc)│
├──────────────────────┼──────────────────────┼────────────────┼──────────────────┼───────────────────────┤
│ payments              │ accounts              │  — (rules in   │ notification_log │ transaction_history   │
│ outbox_event          │ ledger_entries        │    config;     │                  │ processed_event       │
│                       │ outbox_event          │    fraud_db    │                  │                       │
│                       │                       │    created but │                  │                       │
│                       │                       │    unused)     │                  │                       │
└──────────────────────┴──────────────────────┴────────────────┴──────────────────┴───────────────────────┘
   No service reads another service's tables. The only cross-service data sharing is via
   synchronous API calls (payment→account, payment→fraud) and asynchronous Kafka events.
```

\* `fraud_db` is created by `infrastructure/postgres/init.sql` but fraud-service is stateless and does not use it today.

---

## 8. Failure & resilience behaviour

| Failure | What happens | Recovery |
|---|---|---|
| Fraud REJECT | `status=REJECTED_BY_FRAUD`, `payment.failed` emitted, `202` returned | Terminal; user notified |
| Debit/credit Feign call throws | `status=FAILED`, `payment.failed` emitted, `202` returned | **No auto-compensation yet** (see §3) |
| Duplicate `POST /payments` (same Idempotency-Key) | Existing payment returned, no new side effects | Safe to retry |
| Duplicate debit/credit (same referenceId) | Existing ledger entry returned (unique constraint) | Safe to retry |
| Kafka down when poller runs | Outbox row stays `published=false`; retried next poll (5s) | At-least-once delivery |
| Duplicate event delivered | notification: `existsByEventId` skip; history: `(paymentId,eventType)` skip | Idempotent consumers |
| Out-of-order events (history) | Merge + recompute → same final row | Order-independent by design |
| Malformed / unusable event (history) | Retry 2×, then routed to `<topic>.DLT` without blocking the partition | Dead-letter topic |
| transaction-history-service down | Payments unaffected; history goes stale | Catches up from committed offsets (replayable, `auto-offset-reset: earliest`) |

---

## 9. Observability & cross-cutting

- **Structured JSON logs** on every service via `logstash-logback-encoder`, with `traceId` / `spanId` / `paymentId` in MDC for correlation.
- **Metrics** on `/actuator/prometheus` (Micrometer). transaction-history-service adds custom counters/timer: `txn.history.events.consumed{topic,result}`, `txn.history.projection.duration`, `txn.history.dlt.count`.
- **Health / readiness** on `/actuator/health` (Kubernetes probes enabled).
- **Trace propagation** across Kafka is partial: `traceId` is carried in the event payload; full `traceparent` header propagation (OpenTelemetry) is a future item.

---

## 10. Deployment / infrastructure

```
docker-compose.yml
  ├─ postgres :5432      one instance, five logical databases (init.sql creates all five)
  ├─ kafka    :9094      KRaft single node; EXTERNAL listener 9094 is what all services target
  └─ keycloak :8080      OAuth2/OIDC issuer — provisioned, not yet wired into the services

Each service is a Spring Boot app (Java 21, Boot 3.3.0), Flyway-migrated schema,
run locally with `mvn spring-boot:run` on its own port (8081–8085).
```

---

## 11. What is real vs. aspirational (so the diagram isn't misread)

| Area | Real today | Aspirational / documented-only |
|---|---|---|
| Sync saga (payment→fraud→account) | ✅ Working end to end | — |
| Outbox + Kafka events (all 4 topics) | ✅ Working, keyed, enriched payloads | — |
| Notification & history consumers | ✅ Working, idempotent | — |
| History query API | ✅ Working (no auth) | JWT auth at a Gateway |
| Dead-letter handling | ✅ In transaction-history-service | notification-service still swallows errors |
| Saga compensation | ❌ | `COMPENSATED` state + debit reversal |
| API Gateway, Keycloak auth | ❌ (Keycloak container only) | Gateway routing, JWT validation, client-credentials service-to-service |
| Distributed tracing | Partial (`traceId` in payload/MDC) | Full OpenTelemetry across HTTP + Kafka |
| Intermediate saga states | ❌ (`RISK_CHECKED`/`DEBITED`/`CREDITED` unused) | Persist each step for finer recovery |

---

## 12. One-paragraph summary

A client posts a payment to **payment-service**, which orchestrates a synchronous saga: it records the payment, calls **fraud-service** to score risk, then calls **account-service** to debit the payer and credit the payee (idempotent on `referenceId = paymentId`), and finally marks the payment `COMPLETED` (or `REJECTED_BY_FRAUD` / `FAILED`). Every state change also writes an **outbox** row in the same transaction; a 5-second poller publishes those rows to **Kafka**, keyed by aggregate id. Two independent consumer groups react asynchronously: **notification-service** sends the user a message, and **transaction-history-service** projects the four event streams into a denormalized, queryable read model that is idempotent and order-independent. The synchronous path gives the client an immediate answer; the asynchronous path builds the read-optimized and user-facing side effects without ever blocking a payment or letting one service reach into another's database.
