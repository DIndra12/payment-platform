# Transaction History Service — Design (As Built)

> Status: **implemented and verified**. Delivered as the `transaction-history-service` module plus the producer-side changes in `payment-service` and `account-service` that feed it. Phase 2 item in [README](README.md#next-phases), originally specified at a high level in [payments-microservices-design.md](payments-microservices-design.md) §1.3, §2.4, §2.6, §3.6.
>
> This document describes what was actually built: the service, how it integrates with the rest of the platform, its internals, data model, API, the producer changes made to close the contract gaps, and how it was tested. Where the original high-level spec and the real code disagreed, this reflects the code.

---

## 0. What changed in this delivery

A quick orientation before the detail. This work added one module and modified two others.

| Module | Change | Why |
|---|---|---|
| **transaction-history-service** (new) | Full CQRS read model: 4 Kafka consumers, order-independent projector, query API, DLT handling, metrics | The feature itself |
| **payment-service** (modified) | `@EnableScheduling`; publish `payment.failed`; enrich event payload (`eventId`, `status`, `occurredAt`, `traceId`); Kafka message key = aggregate id; `StringSerializer` fix | Close producer gaps 2–5 (see §3) so events actually flow, in order, with a dedupe key |
| **account-service** (modified) | New outbox (`account.debited` / `account.credited`), `@EnableScheduling`, spring-kafka dependency | Close producer gap 1 — those two topics had no producer at all |
| **infrastructure / README / root pom** | `notification_db` + `transaction_history_db` in init.sql; README Phase 2 + API docs; jacoco coverage includes | Wiring |

Everything below is the design as it now stands in the codebase. The full reactor builds green (`mvnw clean verify -DskipITs`) and the new module has 39 passing tests (25 unit, 8 integration, 6 acceptance).

---

## 1. Purpose

Transaction History Service is the **read side of CQRS** for this platform. The transactional services (payment, account) own normalized, write-optimized schemas in their own databases. Answering "show me everything that happened on account X" from those schemas would mean cross-service joins, which the platform explicitly forbids — no service reads another's tables.

So instead: consume the events those services already emit, and maintain a **denormalized row per transaction** that can answer account-scoped history queries with a single indexed read.

| Property | Value |
|---|---|
| Module | `transaction-history-service` |
| Package root | `com.payments.platform.transactionhistoryservice` |
| Port | **8085** (8081 account, 8082 fraud, 8083 payment, 8084 notification) |
| Database | `transaction_history_db` |
| Consumes (Kafka) | `payment.completed`, `payment.failed`, `account.debited`, `account.credited` |
| Publishes (Kafka) | nothing except dead-letter topics (`<topic>.DLT`) |
| Sync calls out | none — no Feign, no dependency on any other service being up |
| Exposes (HTTP) | `GET /api/v1/transactions/...` query API (port 8085) |
| Owns | `transaction_history` (denormalized), `processed_event` (dedupe ledger) |

**What it demonstrates:** CQRS read models, stitching a coherent view from multiple independent event streams, and handling out-of-order / partial / duplicate event arrival. That last part is the real lesson — because the producer partitions round-robin per topic and the debit/credit legs are keyed by different accounts, `account.debited` can easily land after `payment.completed`.

---

## 2. Context — where it sits in the platform

```
                         ┌─────────────────────┐
                         │   Client / UI / curl │
                         └───────┬─────────┬────┘
             write path (sync)   │         │   read path (history queries)
                                 ▼         │
      ┌────────────────────────────────┐  │
      │      PAYMENT SERVICE  :8083     │  │
      │      saga orchestrator          │  │
      │  ┌───────────────────────────┐  │  │
      │  │ outbox_event  (payment_db)│  │  │
      │  └─────────────┬─────────────┘  │  │
      │   @Scheduled poller (5s)        │  │
      └───┬────┬────────┼───────────────┘  │
   Feign  │    │        │ publishes         │
   (sync) │    │        │ payment.completed │
    ┌─────┘    └───┐    │ payment.failed    │
    ▼              ▼    │  key = paymentId  │
┌──────────┐  ┌────────┴─┐                  │
│ ACCOUNT  │  │  FRAUD   │                  │
│  :8081   │  │  :8082   │                  │
│ ledger + │  └──────────┘                  │
│ outbox   │                                │
└────┬─────┘                                │
     │ @Scheduled poller (5s)               │
     │ publishes account.debited /          │
     │ account.credited (key = accountId)   │
     ▼                                      │
┌───────────────────────────────────────────┼──────────────────────────────┐
│                          KAFKA             │                              │
│   payment.completed ───┬───────────────────┼──────────┐                   │
│   payment.failed   ────┤                   │          │                   │
│   account.debited  ────┤                   │          │                   │
│   account.credited ────┤                   │          │                   │
└────────────────────────┼───────────────────┼──────────┼───────────────────┘
                         │                   │          │
       group:           │                   │          │  group:
       notification-    ▼                   │          ▼  transaction-history-service
       service   ┌──────────────┐           │   ┌────────────────────────────┐
                 │ NOTIFICATION │           │   │  TRANSACTION HISTORY :8085  │
                 │    :8084     │           │   │  ┌──────────────────────┐   │
                 │ consumes     │           │   │  │ PaymentEventConsumer  │   │
                 │ payment.*    │           │   │  │ AccountEventConsumer  │   │
                 │ (own group)  │           │   │  └──────────┬───────────┘   │
                 └──────────────┘           │   │             ▼               │
                                            │   │  ┌──────────────────────┐   │
                                            │   │  │ TransactionProjector  │   │
                                            │   │  │ dedupe→merge→derive   │   │
                                            │   │  └──────────┬───────────┘   │
                                            │   │             ▼               │
                                            │   │  transaction_history_db     │
                                            │   │   • transaction_history     │
                                            │   │   • processed_event         │
                                            │   │             ▲               │
                                            └───┼─────────────┘ read          │
                                       history query API ◄──────────────┐     │
                                            │   │  GET /api/v1/transactions... │
                                            │   └──────────────────────────────┘
                                            └── (client reads history here)
```

Two properties are the whole point of putting this behind Kafka rather than having it call the other services:

- **It is a leaf.** Nothing depends on it, and it makes no synchronous call to anything. If it is down, payments still complete; history just goes stale until it catches up from its committed offsets.
- **It has its own consumer group** (`transaction-history-service`), so it consumes `payment.completed` completely independently of notification-service. Adding it changed nothing about notification-service's offsets or behaviour — that is CQRS fan-out working as intended.

---

## 3. Producer-side changes (gaps that were closed)

The original high-level spec assumed a producer contract that the code did not actually implement. Rather than build the read model against a fiction, the real producer side was read first, five gaps were identified, and **all five were fixed as part of this delivery**. This table is now a record of what was done, not a list of blockers.

| # | Original gap | Fix delivered | Where |
|---|---|---|---|
| 1 | `account.debited` / `account.credited` had **no producer** (account-service had no Kafka code) | Added a full outbox to account-service: `OutboxEvent`, `OutboxEventRepository`, `OutboxPublisher` (@Scheduled), `OutboxSender` + `OutboxSenderKafkaAdapter`, `LedgerEventPublisher`, `JsonNodeConverter`, migration `V2__create_outbox.sql`. `AccountService.debit()/credit()` now write the outbox row in the **same transaction** as the ledger entry. | `account-service/.../outbox/`, `ledger/AccountService.java` |
| 2 | `payment.failed` was **never published** (outbox row only written on COMPLETED) | `updatePaymentState()` now writes an outbox event on `FAILED` and `REJECTED_BY_FRAUD` too, with `eventType = "payment.failed"` and a `failureReason` field. | `PaymentOrchestratorService.java` |
| 3 | Real payload had only 5 fields — no `eventId`, `status`, `occurredAt`, `traceId` | `createOutboxEvent(...)` now builds a `LinkedHashMap` with `eventId` (random UUID), `status`, `occurredAt` (ISO local date-time), and `traceId` (from MDC). Field order is now stable and nulls are allowed. | `PaymentOrchestratorService.java` |
| 4 | **No Kafka message key** → round-robin partitioning → no per-payment ordering | `OutboxSender.send(topic, key, payload)` gained a `key` parameter; both outbox publishers pass `aggregateId` (paymentId for payment events, accountId for account events) as the key. | both `outbox/OutboxSender*.java`, both `OutboxPublisher.java` |
| 5 | Outbox poller **never ran** — `@Scheduled` present but no `@EnableScheduling` | Added `@EnableScheduling` to `PaymentServiceApplication` and `AccountServiceApplication`. | both `*Application.java` |

### Bonus bug fixed: producer double-encoding

While wiring the key change, a latent producer bug surfaced. `payment-service/application.yml` declared a `JsonSerializer` for the producer **value**, but `OutboxSenderKafkaAdapter` hands the template an **already-serialized JSON string** (`payload.toString()`). `JsonSerializer` would encode that string *again*, putting a quoted, escaped string literal on the topic instead of a JSON object — which every consumer then fails to bind. Changed the value serializer to `StringSerializer`. Also corrected `bootstrap-servers` from `9092` (the in-network listener, unreachable from the host) to `9094` (the EXTERNAL listener docker-compose publishes), matching notification-service.

### Consequence for this service's design

Even with keys now set, the design keeps its **order-independent, dedupe-on-`(paymentId, eventType)`** stance rather than assuming ordering:

- The debit and credit legs of one payment are keyed by *different* accounts, so they land on different partitions and can still be consumed out of order relative to each other and to the payment event.
- Keying on `(paymentId, eventType)` rather than `eventId` means the read model still works even against the pre-enrichment payload, and does not depend on `eventId` being globally unique.

---

## 4. The correlation key

All four topics stitch together on **`paymentId`**, and this holds by construction in the code, not by convention:

- `payment.completed` / `payment.failed` carry `paymentId` directly.
- Payment Service calls Account Service with `DebitRequest.referenceId = payment.getId().toString()` (both debit and credit legs, in `PaymentOrchestratorService`).
- Account Service stores that as `ledger_entries.reference_id` (with `UNIQUE(reference_id, entry_type)` — its own idempotency), and its `LedgerEventPublisher` copies it into the event as `referenceId`.

So `account.debited.referenceId == account.credited.referenceId == paymentId`. One row per `paymentId`, four events converging on it.

```
payment.completed  { paymentId: P1, ... }        ──┐
payment.failed     { paymentId: P1, ... }        ──┤
account.debited    { referenceId: P1, ... }      ──┼──►  transaction_history[payment_id = P1]
account.credited   { referenceId: P1, ... }      ──┘
```

---

## 5. Internal architecture (as built)

```
┌───────────────────────────────────────────────────────────────────────────┐
│                  transaction-history-service  :8085                        │
│                                                                            │
│  ── INGEST ───────────────────────────────────────────────────────────────│
│  kafka/                                    projection/                      │
│  ┌────────────────────────────┐           ┌──────────────────────────────┐ │
│  │ KafkaConsumerConfig         │           │ TransactionProjector          │ │
│  │  • one ConsumerFactory +    │           │  @Transactional per event     │ │
│  │    container factory PER     │  calls   │  1. dedupe (processed_event)  │ │
│  │    event type (4 total)      │────────► │  2. load-or-create row        │ │
│  │  • VALUE_DEFAULT_TYPE,       │           │  3. null-safe coalesce merge  │ │
│  │    USE_TYPE_INFO_HEADERS=0   │           │  4. recordEvent + deriveStatus│ │
│  │  • DefaultErrorHandler +     │           │  5. save row + processed_event│ │
│  │    DeadLetterPublishingRecov │           └──────────┬───────────────────┘ │
│  └────────────┬────────────────┘                      │                     │
│               │                                        ▼                     │
│  consumer/    ▼                          projection/EventType,               │
│  ┌────────────────────────────┐          TransactionStatus, ProjectionResult,│
│  │ PaymentEventConsumer         │          EventContext                       │
│  │  @KafkaListener              │                                            │
│  │   payment.completed          │          metrics/ProjectionMetrics          │
│  │   payment.failed             │           txn.history.events.consumed       │
│  │ AccountEventConsumer         │           txn.history.projection.duration    │
│  │  @KafkaListener              │           txn.history.dlt.count              │
│  │   account.debited            │                                            │
│  │   account.credited           │   (ack AFTER commit; exceptions propagate  │
│  │  manual ack                  │    to the error handler → retry → DLT)     │
│  └──────────────────────────────┘                                           │
│                                                                            │
│  ── STORAGE ────────────────────────────────────────────────────────────── │
│  persistence/                                                              │
│  ┌────────────────────────────┐   ┌──────────────────────────────────────┐ │
│  │ transaction_history         │   │ processed_event                       │ │
│  │  1 row / paymentId          │   │  UNIQUE(payment_id, event_type)       │ │
│  │  @Version optimistic lock   │   │  (NOT unique on event_id)             │ │
│  └────────────────────────────┘   └──────────────────────────────────────┘ │
│  TransactionHistoryRepository (JpaSpecificationExecutor) + Specifications   │
│                                                                            │
│  ── QUERY ─────────────────────────────────────────────────────────────────│
│  api/TransactionHistoryController  ──►  query/TransactionHistoryQueryService │
│   GET /api/v1/transactions/account/{accountId}      (paged, filtered)       │
│   GET /api/v1/transactions/{paymentId}              (single, perspective)   │
│   GET /api/v1/transactions/account/{accountId}/summary                      │
│  exception/GlobalExceptionHandler  ({timestamp,status,error,message})       │
└───────────────────────────────────────────────────────────────────────────┘
```

### Actual package layout

```
com.payments.platform.transactionhistoryservice
├── TransactionHistoryServiceApplication      @SpringBootApplication @EnableKafka
├── api/
│   ├── TransactionHistoryController           GET endpoints, @Validated
│   ├── TransactionResponse                    one stitched transaction (account-relative)
│   ├── TransactionPageResponse                paged wrapper
│   ├── TransactionSummaryResponse             counts + totals
│   └── TransactionDirection                   DEBIT | CREDIT (per-request)
├── query/
│   └── TransactionHistoryQueryService         read-only; derives direction/counterparty
├── kafka/
│   ├── KafkaConsumerConfig                    4 container factories + DLT error handler
│   ├── PaymentEventConsumer                   payment.completed, payment.failed
│   └── AccountEventConsumer                   account.debited, account.credited
├── projection/
│   ├── TransactionProjector                   the core; @Transactional, order-independent
│   ├── EventType                              4 wire names (= topic names)
│   ├── TransactionStatus                      IN_PROGRESS | COMPLETED | FAILED
│   ├── ProjectionResult                       APPLIED | SKIPPED_DUPLICATE
│   └── EventContext                           topic/partition/offset record
├── dto/
│   ├── PaymentCompletedEvent, PaymentFailedEvent
│   └── AccountDebitedEvent, AccountCreditedEvent   (all @JsonIgnoreProperties(ignoreUnknown))
├── persistence/
│   ├── entity/TransactionHistory, entity/ProcessedEvent
│   ├── TransactionHistoryRepository           JpaRepository + JpaSpecificationExecutor
│   ├── TransactionHistorySpecifications       null-returning criteria for absent filters
│   └── ProcessedEventRepository
├── metrics/ProjectionMetrics                  Micrometer counters/timer
└── exception/
    ├── TransactionNotFoundException           → 404
    └── InvalidEventException                   → non-retryable, straight to DLT
```

### Why one container factory per event type

payment-service's `OutboxSenderKafkaAdapter` publishes bare JSON with **no `__TypeId__` header**. A single shared `JsonDeserializer` can't pick a target class, and a shared default type throws `MessageConversionException` on the other topics. So `KafkaConsumerConfig` builds one `ConsumerFactory` per type with `JsonDeserializer.VALUE_DEFAULT_TYPE` pinned and `USE_TYPE_INFO_HEADERS=false`, wrapped in `ErrorHandlingDeserializer`, manual ack. This service extends notification-service's two-factory pattern to four and adds the error handler the platform previously lacked.

---

## 6. Data model

### `transaction_history` — the read model (`V1__init_transaction_history_schema.sql`)

```sql
CREATE TABLE transaction_history (
    id                  UUID PRIMARY KEY,
    payment_id          UUID NOT NULL,
    payer_account_id    UUID,              -- nullable: may arrive from a later event
    payee_account_id    UUID,
    amount              NUMERIC(19,4),
    currency            VARCHAR(3),
    status              VARCHAR(20) NOT NULL,   -- derived, see §7
    failure_reason      TEXT,
    debited_at          TIMESTAMP,              -- from account.debited
    credited_at         TIMESTAMP,              -- from account.credited
    completed_at        TIMESTAMP,              -- from payment.completed / failed
    debit_ledger_id     UUID,
    credit_ledger_id    UUID,
    trace_id            VARCHAR(64),
    events_seen         VARCHAR(255) NOT NULL DEFAULT '',  -- CSV audit of event types applied
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,          -- @Version optimistic lock
    CONSTRAINT uk_transaction_history_payment_id UNIQUE (payment_id)
);

CREATE INDEX idx_txn_history_payer      ON transaction_history(payer_account_id, created_at DESC);
CREATE INDEX idx_txn_history_payee      ON transaction_history(payee_account_id, created_at DESC);
CREATE INDEX idx_txn_history_status     ON transaction_history(status);
CREATE INDEX idx_txn_history_created_at ON transaction_history(created_at DESC);
```

Everything sourced from an event is **nullable except `payment_id` and `status`** — a row can be created by whichever event lands first and filled in as the rest arrive. The two separate payer/payee indexes make the account query a single indexed scan; the `@Version` column lets two events for the same payment be processed concurrently from different partitions without silently clobbering each other.

### `processed_event` — idempotency ledger

```sql
CREATE TABLE processed_event (
    id           UUID PRIMARY KEY,
    event_id     UUID,                     -- recorded, nullable, NOT the dedupe key
    payment_id   UUID NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    partition_id INTEGER,
    offset_value BIGINT,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_payment_event UNIQUE (payment_id, event_type)
);
```

Dedupe is keyed on `(payment_id, event_type)`, mirroring account-service's proven `UNIQUE(reference_id, entry_type)`. `event_id` is now populated by the producer (gap #3) and stored, so the key *could* move to it later, but keeping `(paymentId, eventType)` means the read model remains correct even against events that predate the enrichment.

### Derived status

| Events seen for a paymentId | Derived status |
|---|---|
| `account.debited` and/or `account.credited` only | `IN_PROGRESS` |
| `payment.completed` (any combination) | `COMPLETED` |
| `payment.failed` (and no `payment.completed`) | `FAILED` |
| any `account.*` arriving after a terminal event | status unchanged; timestamps/ledger ids backfilled |

`COMPLETED` wins over `FAILED`, and a terminal state is never downgraded — because status is recomputed from the full `events_seen` set every time, not transitioned from the previous value.

---

## 7. Idempotency & out-of-order handling (the core behaviour)

`TransactionProjector` runs one `@Transactional` method per event:

```
onEvent(event, topic, partition, offset):
  0. correlation key present?  no → throw InvalidEventException (non-retryable → DLT)
  1. DEDUPE   processedEventRepo.existsByPaymentIdAndEventType(pid, type)? → SKIPPED_DUPLICATE
  2. LOAD     row = findByPaymentId(pid).orElseGet(newRow)      // partial row is valid
  3. MERGE    row.field = coalesce(row.field, event.field)      // never overwrite known with null
              row.recordEvent(type)                             // add to events_seen
  4. DERIVE   row.status = deriveStatus(row)                     // recompute from full set
  5. SAVE     save(row) + save(processedEvent)                   // one transaction
  → consumer acks AFTER this returns (i.e. after commit)
```

Three guarantees fall out:

- **Duplicate delivery** → step 1 short-circuits.
- **Out-of-order delivery** → step 3 merges into whatever exists and step 4 recomputes, so the end state is identical for any arrival order (proven by a parameterised unit test over all six orderings of the three events).
- **Partial arrival** → the row exists as `IN_PROGRESS` with nullable gaps; the API reports it honestly rather than hiding it.

Two deliberate choices, both of which notification-service gets wrong:

1. **Ack after commit, not before** — the consumer calls `acknowledgment.acknowledge()` only after the projector's transactional method returns.
2. **Let exceptions propagate** — the consumer rethrows so the container's error handler sees the failure. Concurrent same-payment writes surface as `OptimisticLockException`, which the retry path resolves by re-reading the committed row and merging into it.

### Error handling / DLT (the platform's first)

```
listener throws
   │
   ▼
DefaultErrorHandler( FixedBackOff(1s, 2 retries) )
   ├─ transient (DB blip, optimistic-lock contention) → succeeds on retry
   ├─ exhausted ─────────────► DeadLetterPublishingRecoverer ─► <topic>.DLT  (+ metric)
   └─ non-retryable (DeserializationException, InvalidEventException,
      IllegalArgumentException) ─────────────────────────────► <topic>.DLT immediately
```

A malformed record is turned into a null value + `DeserializationException` by `ErrorHandlingDeserializer` and dead-lettered without blocking the partition (verified: a poison pill is DLT'd while a good record queued behind it still projects).

---

## 8. Query API (port 8085)

Read-only, paged, no side effects.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/transactions/account/{accountId}` | Paged history, newest first. Filters: `status`, `direction` (DEBIT/CREDIT), `from`, `to` (ISO date-time), `page`, `size` (max 100). |
| `GET` | `/api/v1/transactions/{paymentId}` | One stitched transaction; optional `?accountId=` sets the perspective; `404` if not yet projected. |
| `GET` | `/api/v1/transactions/account/{accountId}/summary` | `totalTransactions`, `totalOutgoing`/`totalIncoming` (COMPLETED), `countByStatus`. |
| `GET` | `/actuator/health` `/prometheus` | Platform-standard actuator surface. |

`direction` and `counterpartyAccountId` are derived **per request** by comparing the queried `accountId` against the row's payer/payee — the stored row is account-neutral, so the same transaction is a DEBIT to the payer and a CREDIT to the payee. On a self-payment DEBIT wins (arbitrary but deterministic); if the account matches neither leg, both stay null rather than being guessed.

```json
GET /api/v1/transactions/account/1111...1111?size=20
{
  "accountId": "1111...1111", "page": 0, "size": 20, "totalElements": 2, "totalPages": 1,
  "transactions": [
    { "paymentId": "a1b2...", "direction": "DEBIT",
      "counterpartyAccountId": "2222...2222", "amount": 500.5000, "currency": "INR",
      "status": "COMPLETED", "debitedAt": "...", "creditedAt": "...", "completedAt": "...",
      "eventsSeen": "payment.completed,account.debited,account.credited" },
    { "paymentId": "e5f6...", "direction": "DEBIT", "counterpartyAccountId": null,
      "amount": 200.0000, "status": "IN_PROGRESS", "creditedAt": null,
      "eventsSeen": "account.debited" }
  ]
}
```

> **Security (unchanged, still open):** these endpoints have no authorization — any caller can read any account's history. The intended control is JWT at the API Gateway with the token subject checked against the requested `accountId` (design doc §2.10); the Gateway does not exist yet, so this service must be treated as internal-only until it does. Kafka ACLs protect ingest; they do nothing for the query side.

> **Consistency:** the read model is eventually consistent — a payment that just returned `202` may take a few seconds to appear (outbox poll interval + consumer lag). Clients needing read-your-writes should hit `GET /api/v1/payments/{id}` on payment-service.

---

## 9. End-to-end sequence (happy path, as built)

```
Client      Payment Svc        Account Svc         Kafka           Txn History Svc      txn_history_db
  │              │                  │                │                    │                  │
  ├─POST payment─►                  │                │                    │                  │
  │              ├─fraud check──────┤                │                    │                  │
  │              ├─debit(ref=P1)───►│                │                    │                  │
  │              │             [ledger row + outbox row, same tx]         │                  │
  │              ├─credit(ref=P1)──►│                │                    │                  │
  │              │             [ledger row + outbox row, same tx]         │                  │
  │              ├─status=COMPLETED + outbox row (payment.completed)      │                  │
  │◄──202────────┤                  │                │                    │                  │
  │         [poller 5s, key=paymentId]───────────────►│ payment.completed  │                  │
  │              │             [poller 5s, key=accountId]──► account.debited/credited          │
  │              │                  │                ├──(fan-out)────────►│                  │
  │              │                  │                │                    ├─dedupe miss       │
  │              │                  │                │                    ├─merge + derive────►
  │              │                  │                │◄──ack (post-commit)┤                  │
  │              │                  │                │   ...×N events      │                  │
  ├─GET /api/v1/transactions/account/{payer}─────────┼────────────────────►│ single indexed  │
  │◄──────────────── paged history ──────────────────┼─────────────────────┤   read           │
```

---

## 10. Configuration & platform wiring

`transaction-history-service/src/main/resources/application.yml` highlights:

```yaml
server: { port: 8085 }
spring:
  datasource: { url: .../transaction_history_db (${ENV:default} placeholders), hikari 10/2/20000 }
  jpa: { hibernate.ddl-auto: validate }        # Flyway owns the schema
  flyway: { enabled: true, baseline-on-migrate: true }
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9094}   # EXTERNAL listener
    consumer: { group-id: transaction-history-service, auto-offset-reset: earliest,
                enable-auto-commit: false }
    listener: { ack-mode: manual }
management.endpoints.web.exposure.include: health,info,metrics,prometheus,loggers
```

`auto-offset-reset: earliest` + idempotent upserts means the read model is **rebuildable**: reset the consumer group and replay (subject to Kafka retention). Structured JSON logs via `logback-spring.xml` carry `traceId`/`spanId`/`paymentId` in MDC.

| File | Change |
|---|---|
| root `pom.xml` | added `transaction-history-service` module; added `...transactionhistoryservice.projection/.query/.api` to the `coverage` jacoco includes |
| `infrastructure/postgres/init.sql` | added `notification_db` **and** `transaction_history_db` (both were missing) |
| `README.md` | Phase 2 checkbox ticked; port 8085 run block; full API section; DB tier + architecture diagrams updated |
| `docker-compose.yml` | Kafka EXTERNAL listener on 9094 is what both this service and the producers target |

---

## 11. Observability

- **Structured logs** (`logstash-logback-encoder`) with `traceId`/`spanId`/`paymentId` MDC keys.
- **Custom metrics** (`ProjectionMetrics`): `txn.history.events.consumed{topic,result}`, `txn.history.projection.duration{topic}`, `txn.history.dlt.count{topic}`. Kafka consumer lag comes free from the client metrics.
- **Key SLI**: consumer lag + the outbox poll interval = "how stale is transaction history".
- **Trace continuation**: `traceId` is now carried in the event payload; full `traceparent` header propagation across Kafka is still a future (OpenTelemetry) item.

---

## 12. Test strategy & results

Layered to match the platform's `unit/ integration/ acceptance/` convention (the root pom profiles select on directory).

| Layer | Location | Count | What it covers |
|---|---|---|---|
| Unit | `unit/projection/TransactionProjectorTest` | 18 | Every ordering permutation of the four events, duplicate delivery, partial arrival, status derivation, null-safe coalescing, missing correlation key, payment isolation |
| Unit | `unit/query/TransactionHistoryQueryServiceTest` | 7 | Direction/counterparty derivation per perspective, partial-state reporting, 404, summary shape |
| Integration | `integration/ProjectionIntegrationTest` | 5 | Testcontainers Kafka+Postgres; publishes the exact raw JSON the producer emits; four-topic stitching, legacy 5-field payload, failure path, redelivery |
| Integration | `integration/DeadLetterIntegrationTest` | 2 | Poison pill + no-correlation-key event routed to `.DLT` without blocking the partition |
| Integration | `integration/TransactionHistoryServiceApplicationTests` | 1 | Context loads, schema validates (`ddl-auto: validate`), all 4 listeners register |
| Acceptance | `acceptance/TransactionHistoryAcceptanceTest` | 6 | Scrambled events → HTTP query returns one stitched transaction, viewed from both parties; direction filter; summary; 404; oversized page rejected |

**Result:** all 39 pass. Full reactor `mvnw clean verify -DskipITs` → BUILD SUCCESS across all six modules.

Test-infra note: `KafkaPostgresTestBase` uses the **singleton-container** pattern (static init + `.start()`, not `@Container`). JUnit stops `@Container` statics after each class, but Spring caches the context across classes — the mismatch produced "This connection has been closed" on the second class. The singleton keeps container and context lifetimes aligned. Fixtures publish with a plain `StringSerializer` and no type headers, exactly like the real producer, so the tests exercise the real wire contract rather than a friendlier one.

**Known pre-existing item (not introduced here):** notification-service's failsafe `PaymentNotificationAcceptanceTest` can stall at context startup under a plain `clean verify` (likely Docker memory pressure — it starts its own Kafka+Postgres). `git status` confirms notification-service has no modified files, so this predates this work; `-DskipITs` or running the new module's profiles directly avoids it.

---

## 13. Open / future items

| Item | Status |
|---|---|
| Authorization on the query API | Open — blocked on the API Gateway (design §2.10); internal-only until then |
| `traceparent` header propagation across Kafka | Partial — `traceId` in payload; full OpenTelemetry propagation is future |
| Read model rebuild tooling | Supported in principle (`earliest` + idempotent upsert); no operator command yet |
| Multi-currency summary | `totalOutgoing`/`totalIncoming` sum across currencies; only meaningful for single-currency accounts today |
| Shared event-contract module | Each service redeclares its own event DTOs; a shared module would prevent drift |

---

## 14. Summary

Transaction History Service is a pure consumer plus a query API: four Kafka topics in, one denormalized `transaction_history` row per `paymentId` out, stitched on the `paymentId` that flows through `ledger_entries.reference_id`. It has no synchronous dependencies, its own database, its own consumer group and offsets, so it can be down, replayed, or rebuilt without touching the payment path.

Delivering it required closing five real producer gaps — three small changes in payment-service, a new outbox in account-service, and two missing `@EnableScheduling` annotations — plus fixing a producer serializer bug that would have double-encoded every event. The interesting engineering is not the CRUD: it is that the events arrive unordered (legs keyed by different accounts), possibly duplicated, and the projector stays idempotent on `(paymentId, eventType)` and order-independent by merging partial state and recomputing status from the full event set. That property is what let the four-topic integration test pass without changing the projector written against a single topic — and it is proven by a unit test that runs all six arrival orders and asserts an identical final row.
