# Payment Platform — Complete System Flow

**Date:** 2026-11-20  
**Purpose:** Visual guide showing how data flows through the entire payment system, with plain English explanations

---

## 1. High-Level Architecture Overview

This is what the entire system looks like from 10,000 feet:

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT APPLICATION                        │
│                      (Mobile app, Web UI)                        │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                    POST /api/v1/payments/create
                    (with Idempotency-Key header)
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────┐
        │          API GATEWAY (Port 8080)                 │
        │  - Validates JWT authentication                  │
        │  - Rate limiting (100 req/min per user)         │
        │  - Routes to backend services                   │
        └──────────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
        ┌──────────────────────┐  ┌──────────────────────┐
        │  PAYMENT SERVICE     │  │  OTHER SERVICES      │
        │  (Saga Orchestrator) │  │  (Account, Fraud,    │
        │  (Port 8083)         │  │   Notification,      │
        │                      │  │   Transaction Hist)  │
        │  - Receives payment  │  │                      │
        │  - Orchestrates flow │  │  (Ports 8081-8085)   │
        │  - Manages saga      │  │                      │
        └──────────────────────┘  └──────────────────────┘
                    │
                    │ (Sync HTTP calls via Feign)
                    │ (Async events via Kafka)
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
   ┌─────────────┐         ┌──────────────┐
   │ PostgreSQL  │         │ Apache Kafka │
   │ 5 databases │         │ Event Stream │
   │ (each       │         │ (Topics for  │
   │  service    │         │  each event) │
   │  owns its   │         │              │
   │  own DB)    │         │              │
   └─────────────┘         └──────────────┘
```

**Key Points:**
- Client sends payment request to **API Gateway** (single entry point)
- Gateway validates authentication + rate limiting, then routes to **Payment Service**
- Payment Service orchestrates the entire flow (Saga pattern)
- Services communicate via:
  - **Sync:** Feign HTTP calls (immediate responses)
  - **Async:** Kafka events (eventual consistency)
- Each service has its own **PostgreSQL database** (data isolation)

---

## 2. The Payment Flow (Happy Path)

This is what happens when a customer successfully makes a payment:

```
STEP 1: CLIENT INITIATES PAYMENT
╔════════════════════════════════════════════════════════════════╗
║ curl -X POST http://localhost:8080/api/v1/payments/create      ║
║ Headers: Authorization: Bearer <JWT_TOKEN>                     ║
║          Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000 ║
║ Body: {                                                         ║
║   "amount": 100.00,                                             ║
║   "currency": "USD",                                            ║
║   "account_id": "user-123"                                      ║
║ }                                                               ║
╚════════════════════════════════════════════════════════════════╝
              │
              │ Request reaches API Gateway
              ▼
    ┌─────────────────────────────┐
    │ API Gateway Processing      │
    │                             │
    │ 1. Extract JWT token        │
    │ 2. Validate signature       │
    │ 3. Check if expired         │
    │ 4. Extract user ID + roles  │
    │ 5. Rate limit check         │
    │    (100 req/min per user)   │
    │ 6. Route to Payment Service │
    └─────────────────────────────┘
              │
              │ ✓ All checks pass
              ▼
    ┌─────────────────────────────┐
    │ Payment Service             │
    │ Receives request            │
    │                             │
    │ Status: INITIATED           │
    │ ID: 550e8400...             │
    │ Amount: $100.00             │
    │ User: user-123              │
    └─────────────────────────────┘
              │
              │ Save to database + create Kafka event
              ▼
    ┌─────────────────────────────────────────────────┐
    │ Outbox Pattern (ATOMIC TRANSACTION)             │
    │                                                 │
    │ 1. INSERT payment into payments table           │
    │    Status: INITIATED                            │
    │                                                 │
    │ 2. INSERT event into outbox table               │
    │    Event: "payment.initiated"                   │
    │    Payload: {id, amount, account_id, ...}      │
    │                                                 │
    │ COMMIT (all or nothing)                         │
    └─────────────────────────────────────────────────┘
              │
              │ Outbox Poller (runs every 1 second)
              │ Finds unpublished events
              ▼
    ┌─────────────────────────────┐
    │ Publish to Kafka Topic      │
    │ "payment.initiated"         │
    │                             │
    │ Other services subscribe:   │
    │ ✓ Fraud Service             │
    │ ✓ Notification Service      │
    │ ✓ Transaction History Svc   │
    └─────────────────────────────┘

════════════════════════════════════════════════════════════════════

STEP 2: FRAUD SERVICE CHECKS PAYMENT
╔════════════════════════════════════════════════════════════════╗
║ Fraud Service subscribes to "payment.initiated" topic          ║
║ Receives event in real-time                                    ║
╚════════════════════════════════════════════════════════════════╝
              │
              ▼
    ┌─────────────────────────────┐
    │ Fraud Service Processing    │
    │                             │
    │ 1. Receive payment event    │
    │ 2. Load customer history    │
    │ 3. Check amount vs average  │
    │ 4. Check location/device    │
    │ 5. Run ML risk model        │
    │ 6. Calculate risk score     │
    │                             │
    │ Risk Score: 25% (LOW)       │
    │ Decision: APPROVE           │
    └─────────────────────────────┘
              │
              │ Save to fraud DB + publish event
              ▼
    ┌─────────────────────────────────────────────────┐
    │ Outbox Pattern (Fraud Service DB)               │
    │                                                 │
    │ 1. INSERT risk_score into decisions table       │
    │    Score: 25%                                   │
    │    Decision: APPROVE                            │
    │                                                 │
    │ 2. INSERT event into outbox table               │
    │    Event: "payment.fraud_checked"               │
    │    Payload: {payment_id, risk_score, ...}      │
    │                                                 │
    │ COMMIT                                          │
    └─────────────────────────────────────────────────┘
              │
              │ Publish to Kafka
              ▼
    ┌─────────────────────────────┐
    │ Kafka Topic                 │
    │ "payment.fraud_checked"     │
    │                             │
    │ Payment Service listens     │
    │ (subscribes to this topic)  │
    └─────────────────────────────┘

════════════════════════════════════════════════════════════════════

STEP 3: PAYMENT SERVICE CALLS ACCOUNT SERVICE (SYNC FEIGN)
╔════════════════════════════════════════════════════════════════╗
║ Payment Service (Saga Orchestrator) receives fraud approval    ║
║ Now needs to debit the account                                 ║
╚════════════════════════════════════════════════════════════════╝
              │
              ▼
    ┌─────────────────────────────┐
    │ Payment Service             │
    │ Saga State Update:          │
    │                             │
    │ Status: FRAUD_CHECKED       │
    │ Save to DB                  │
    └─────────────────────────────┘
              │
              │ Now call Account Service (HTTP Feign call)
              │ ► Blocking call (waits for response)
              ▼
    ╔═════════════════════════════════════════════════════╗
    ║ HTTP REQUEST (Feign)                                ║
    ║ POST http://account-service:8081/debit              ║
    ║ Body: {                                             ║
    ║   "account_id": "user-123",                         ║
    ║   "amount": 100.00,                                 ║
    ║   "reference_id": "payment-550e8400...",            ║
    ║   "reference_type": "PAYMENT"                       ║
    ║ }                                                   ║
    ╚═════════════════════════════════════════════════════╝
              │
              │ Sent over network to Account Service
              ▼
    ┌─────────────────────────────┐
    │ Account Service             │
    │ Receives debit request      │
    │                             │
    │ 1. Load account from DB     │
    │ 2. Check balance > $100     │
    │ 3. Create ledger entry      │
    │ 4. Update account balance   │
    │ 5. Publish event to Kafka   │
    │                             │
    │ ✓ Success: Balance: $900    │
    └─────────────────────────────┘
              │
              │ Return response (HTTP 200)
              ▼
    ┌─────────────────────────────┐
    │ Payment Service receives:   │
    │ {                           │
    │   "account_id": "user-123", │
    │   "new_balance": 900.00,    │
    │   "status": "SUCCESS"       │
    │ }                           │
    │                             │
    │ Update saga state:          │
    │ Status: DEBITED             │
    │ Save to DB                  │
    └─────────────────────────────┘

════════════════════════════════════════════════════════════════════

STEP 4: PUBLISH PAYMENT COMPLETED EVENT
╔════════════════════════════════════════════════════════════════╗
║ Payment Service publishes final event                          ║
║ All subscribers get notified                                   ║
╚════════════════════════════════════════════════════════════════╝
              │
              ▼
    ┌─────────────────────────────────────────────────┐
    │ Outbox Pattern (Payment Service DB)             │
    │                                                 │
    │ 1. INSERT into payments table                   │
    │    Status: COMPLETED                            │
    │    Final Amount: $100.00                        │
    │                                                 │
    │ 2. INSERT event into outbox                     │
    │    Event: "payment.completed"                   │
    │    Payload: {id, amount, account_id, ...}      │
    │                                                 │
    │ COMMIT                                          │
    └─────────────────────────────────────────────────┘
              │
              │ Multiple services listen to this event
              ▼
    ┌────────────────────────────────────────────────────┐
    │ Who listens to "payment.completed"?                │
    │                                                    │
    │ 1. Notification Service                            │
    │    → Send SMS: "Payment of $100 successful"        │
    │    → Send email receipt                            │
    │                                                    │
    │ 2. Transaction History Service (CQRS)              │
    │    → Add to transaction_view                       │
    │    → Denormalized for fast queries                 │
    │                                                    │
    │ 3. Analytics Service (future)                      │
    │    → Track payment patterns                        │
    │    → Calculate daily totals                        │
    └────────────────────────────────────────────────────┘

════════════════════════════════════════════════════════════════════

STEP 5: CLIENT QUERIES RESULT
╔════════════════════════════════════════════════════════════════╗
║ Client polls for payment status                                ║
║ GET /api/v1/payments/550e8400-e29b-41d4-a716-446655440000      ║
╚════════════════════════════════════════════════════════════════╝
              │
              │ Routed via API Gateway
              ▼
    ┌─────────────────────────────┐
    │ Payment Service API         │
    │ /payments/:id endpoint      │
    │                             │
    │ Query payment from DB       │
    │ Status: COMPLETED           │
    │ Amount: $100.00             │
    │ Created: 2026-11-20 14:30   │
    └─────────────────────────────┘
              │
              │ Return response (HTTP 200)
              ▼
    ╔═════════════════════════════════════════════════════╗
    ║ HTTP RESPONSE                                       ║
    ║ {                                                   ║
    ║   "id": "550e8400-e29b-41d4-a716-446655440000",     ║
    ║   "account_id": "user-123",                         ║
    ║   "amount": 100.00,                                 ║
    ║   "currency": "USD",                                ║
    ║   "status": "COMPLETED",                            ║
    ║   "created_at": "2026-11-20T14:30:00Z",             ║
    ║   "completed_at": "2026-11-20T14:30:02Z"            ║
    ║ }                                                   ║
    ╚═════════════════════════════════════════════════════╝
```

---

## 3. Data Flow Across Services

**How data moves through the system:**

```
TIME: 2026-11-20 14:30:00

┌─────────────────────────────────────────────────────────────────┐
│ T+0s: Payment Initiated                                          │
├─────────────────────────────────────────────────────────────────┤
│ payment_service.payments TABLE:                                  │
│ ┌─────────────┬──────────┬─────────────┐                         │
│ │ id          │ status   │ created_at  │                         │
│ ├─────────────┼──────────┼─────────────┤                         │
│ │ 550e8400... │ INITIATED│ 2026-11-20  │  ◄── Written by        │
│ │             │          │ 14:30:00    │      Payment Service   │
│ └─────────────┴──────────┴─────────────┘                         │
│                                                                   │
│ payment_service.outbox TABLE:                                    │
│ ┌──────────────┬─────────────────────────┐                       │
│ │ event_id     │ event_type              │ published │           │
│ ├──────────────┼─────────────────────────┤───────────┤           │
│ │ 1            │ payment.initiated       │ false     │  ◄── New  │
│ └──────────────┴─────────────────────────┴───────────┘           │
└─────────────────────────────────────────────────────────────────┘

TIME: 2026-11-20 14:30:01

┌─────────────────────────────────────────────────────────────────┐
│ T+1s: Outbox Poller Publishes Event to Kafka                    │
├─────────────────────────────────────────────────────────────────┤
│ Kafka Topic: payment.initiated                                   │
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │ partition_0: [                                              │  │
│ │   {                                                          │  │
│ │     "payment_id": "550e8400-e29b-41d4-a716-446655440000",   │  │
│ │     "account_id": "user-123",                               │  │
│ │     "amount": 100.00,                                       │  │
│ │     "currency": "USD"                                       │  │
│ │   } ◄── Now visible to all subscribers                       │  │
│ │ ]                                                            │  │
│ └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│ payment_service.outbox TABLE:                                    │
│ ┌──────────────┬─────────────────────────┬──────────┐            │
│ │ event_id     │ event_type              │published │            │
│ ├──────────────┼─────────────────────────┼──────────┤            │
│ │ 1            │ payment.initiated       │ true     │  ◄── Mark  │
│ │              │                         │          │      as pub │
│ └──────────────┴─────────────────────────┴──────────┘            │
└─────────────────────────────────────────────────────────────────┘

TIME: 2026-11-20 14:30:02

┌─────────────────────────────────────────────────────────────────┐
│ T+2s: Fraud Service Consumes Event & Scores Payment             │
├─────────────────────────────────────────────────────────────────┤
│ fraud_service.risk_scores TABLE:                                 │
│ ┌──────────────┬────────────┬──────────┐                         │
│ │ payment_id   │ risk_score │ decision │                         │
│ ├──────────────┼────────────┼──────────┤                         │
│ │ 550e8400...  │ 0.25       │ APPROVE  │  ◄── Written by        │
│ └──────────────┴────────────┴──────────┘      Fraud Service     │
│                                                                   │
│ fraud_service.outbox TABLE:                                      │
│ ┌──────────────┬──────────────────────────┐                      │
│ │ event_id     │ event_type               │published │           │
│ ├──────────────┼──────────────────────────┤──────────┤           │
│ │ 1            │ payment.fraud_checked    │ false    │  ◄── New  │
│ └──────────────┴──────────────────────────┴──────────┘           │
└─────────────────────────────────────────────────────────────────┘

TIME: 2026-11-20 14:30:03

┌─────────────────────────────────────────────────────────────────┐
│ T+3s: Payment Service Receives Fraud Result, Calls Account Svc  │
├─────────────────────────────────────────────────────────────────┤
│ payment_service.payments TABLE:                                  │
│ ┌─────────────┬──────────────┐                                   │
│ │ id          │ status       │                                   │
│ ├─────────────┼──────────────┤                                   │
│ │ 550e8400... │ FRAUD_CHECKED│  ◄── Updated by Payment Service  │
│ └─────────────┴──────────────┘                                   │
│                                                                   │
│ [FEIGN HTTP CALL → Account Service]                              │
│                                                                   │
│ account_service.ledger TABLE:                                    │
│ ┌──────────────┬─────────────┬────────┐                          │
│ │ reference_id │ entry_type  │ amount │                          │
│ ├──────────────┼─────────────┼────────┤                          │
│ │ 550e8400...  │ DEBIT       │ 100.00 │  ◄── Written by Account  │
│ └──────────────┴─────────────┴────────┘      Service (Feign)    │
│                                                                   │
│ account_service.accounts TABLE:                                  │
│ ┌──────────────┬──────────┐                                      │
│ │ id           │ balance  │                                      │
│ ├──────────────┼──────────┤                                      │
│ │ user-123     │ 900.00   │  ◄── Decremented by $100            │
│ └──────────────┴──────────┘      (was 1000.00)                   │
│                                                                   │
│ account_service.outbox TABLE:                                    │
│ ┌──────────────┬──────────────────┐                              │
│ │ event_id     │ event_type       │published │                   │
│ ├──────────────┼──────────────────┼──────────┤                   │
│ │ 1            │ account.debited  │ false    │  ◄── New          │
│ └──────────────┴──────────────────┴──────────┘                   │
└─────────────────────────────────────────────────────────────────┘

TIME: 2026-11-20 14:30:04

┌─────────────────────────────────────────────────────────────────┐
│ T+4s: Payment Completed, Events Published                       │
├─────────────────────────────────────────────────────────────────┤
│ payment_service.payments TABLE:                                  │
│ ┌─────────────┬───────────┐                                      │
│ │ id          │ status    │                                      │
│ ├─────────────┼───────────┤                                      │
│ │ 550e8400... │ COMPLETED │  ◄── Final status                   │
│ └─────────────┴───────────┘                                      │
│                                                                   │
│ Kafka Topics (all published):                                    │
│ ├─ payment.initiated ──────────────────────────────────┐         │
│ ├─ payment.fraud_checked ──────────────────────────────┤         │
│ ├─ account.debited ─────────────────────────────────────┤         │
│ ├─ account.credited ───────────────────────────────────┤         │
│ └─ payment.completed ──────────────────────────────────┘         │
│                                                                   │
│ transaction_history_service.transactions_view TABLE:             │
│ ┌──────────────┬──────────┬────────┐                             │
│ │ payment_id   │ type     │ amount │                             │
│ ├──────────────┼──────────┼────────┤                             │
│ │ 550e8400...  │ PAYMENT  │ 100.00 │  ◄── CQRS Denormalized     │
│ │              │          │        │      Read Model Built       │
│ └──────────────┴──────────┴────────┘      from Kafka Events     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. The Saga Pattern (State Machine)

The Payment Service is a **Saga Orchestrator** that manages state transitions:

```
                    ┌─ INITIATED ─┐
                    │              │
                    │  (Payment    │
                    │   created)   │
                    │              │
                    └──────┬───────┘
                           │
                    (Call Fraud Service)
                           │
                           ▼
                    ┌─ FRAUD_CHECK ─┐
                    │                │
                    │  (Risk scored  │
                    │   & approved)  │
                    │                │
                    └──────┬─────────┘
                           │
                    (Call Account Service)
                           │
                           ▼
                    ┌─ DEBITED ─────┐
                    │                │
                    │  (Account      │
                    │   debited)     │
                    │                │
                    └──────┬─────────┘
                           │
                    (Publish final event)
                           │
                           ▼
                    ┌─ COMPLETED ───┐
                    │                │
                    │  (Payment      │
                    │   successful)  │
                    │                │
                    └────────────────┘


WHAT IF SOMETHING FAILS?

                    ┌─ INITIATED ─┐
                    │              │
                    └──────┬───────┘
                           │
                    (Call Fraud Service)
                           │
                    ┌──────▼───────┐
                    │              │
              Fraud Service DOWN  Fraud rejects
              or timeout          payment
                    │              │
                    ▼              ▼
              Retry with      ┌─ REJECTED ─┐
              Resilience4j    │             │
              (exponential    │ (Payment    │
              backoff)        │  canceled)  │
                              │             │
                              └─────────────┘
```

---

## 5. The Outbox Pattern (Why It Matters)

**Problem:** What if Kafka is down when we try to publish an event?

```
WRONG WAY (Data Loss):
╔════════════════════════════════╗
│ 1. Create payment in DB        │
│    Status: INITIATED           │
├════════════════════════════════┤
│ 2. Try to publish to Kafka     │
│    ⚠️  KAFKA IS DOWN!          │
│    Event LOST (never sent)     │
├════════════════════════════════┤
│ 3. App retries                 │
│    But event already forgotten │
│    Data inconsistency!         │
╚════════════════════════════════╝


RIGHT WAY (Outbox Pattern):
╔════════════════════════════════════════════════════════════════╗
│ ATOMIC TRANSACTION (all or nothing):                           │
│                                                                 │
│ 1. INSERT payment into payments table                          │
│    Status: INITIATED                                           │
│    INSERT payment.id = 550e8400...                             │
│                                                                 │
│ 2. INSERT event into outbox table                              │
│    event_type = "payment.initiated"                            │
│    published = false                                           │
│                                                                 │
│ 3. COMMIT (both succeed or both fail)                          │
│                                                                 │
│ ✓ Payment exists in DB                                         │
│ ✓ Event exists in DB (not published yet)                       │
├════════════════════════════════════════════════════════════════┤
│ SEPARATE PROCESS (Outbox Poller):                              │
│                                                                 │
│ Every 1 second:                                                │
│ 1. Query outbox: SELECT * WHERE published = false              │
│ 2. Publish to Kafka:                                           │
│    - If SUCCESS: UPDATE outbox SET published = true            │
│    - If FAILURE: Retry next cycle (Kafka will come back up)   │
│                                                                 │
│ ✓ Guarantees exactly-once delivery (idempotent)               │
│ ✓ No data loss even if Kafka is temporarily down              │
│ ✓ Events replayed if service crashes                          │
╚════════════════════════════════════════════════════════════════╝
```

---

## 6. Event-Driven Architecture (Kafka Topics)

Each service publishes and subscribes to events:

```
SERVICES & WHAT THEY PUBLISH:

Payment Service publishes:
├─ payment.initiated
│   (New payment received, fraud check needed)
│
├─ payment.fraud_checked
│   (Fraud result received, account debit needed)
│
└─ payment.completed
    (Payment successful, notify customer)


Account Service publishes:
├─ account.debited
│   (Money deducted from account)
│
└─ account.credited
    (Money added to account)


Fraud Service publishes:
└─ payment.fraud_checked
    (Risk score calculated, decision made)


SERVICES & WHAT THEY SUBSCRIBE TO:

Notification Service listens to:
├─ payment.completed
│   → Send SMS: "Payment successful"
│   → Send email receipt
│
└─ payment.rejected
    → Send SMS: "Payment declined"


Transaction History Service listens to:
├─ payment.initiated
├─ payment.fraud_checked
├─ payment.completed
├─ account.debited
└─ account.credited
    → Build denormalized view
    → User can query transaction history
    → Fast queries (pre-aggregated data)


Payment Service (Orchestrator) listens to:
├─ payment.fraud_checked
│   (Triggers account debit)
│
└─ account.debited
    (Triggers completion)


              payment.initiated
                     │
         ┌───────────┼───────────┐
         │           │           │
         ▼           ▼           ▼
    [Fraud]    [Notify]    [History]
    Service    Service     Service
         │
         │ payment.fraud_checked
         │
         ▼
    [Payment]
    Service
         │
    (HTTP Feign)
         │
         ▼
    [Account Service]
    Debits account
         │
         │ account.debited
         │
         ▼
    [Payment Service]
    Marks completed
         │
         │ payment.completed
         │
         ▼
    [Notification]
    Service sends SMS
```

---

## 7. Database Isolation (Why 5 Databases?)

Each service has its own database to ensure independence:

```
BEFORE (Shared Database - TIGHTLY COUPLED):
┌────────────────────────────────────────────┐
│          SHARED PostgreSQL DB              │
│                                            │
│ ├─ payments_table                          │
│ ├─ accounts_table                          │
│ ├─ risk_scores_table                       │
│ ├─ notifications_table                     │
│ └─ transactions_view_table                 │
│                                            │
│ PROBLEMS:                                  │
│ ✗ All services lock same tables            │
│ ✗ Schema changes affect everyone           │
│ ✗ Scaling one service scales all           │
│ ✗ One service down = cascading failures    │
│ ✗ Foreign keys create tight coupling       │
└────────────────────────────────────────────┘


AFTER (5 Separate Databases - DECOUPLED):
┌──────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Server                             │
│                                                                  │
│ ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│ │ payment_db      │  │ account_db       │  │ fraud_db       │  │
│ │                 │  │                  │  │                │  │
│ │ payments table  │  │ accounts table   │  │ risk_scores tbl│  │
│ │ outbox table    │  │ ledger table     │  │ decisions tbl  │  │
│ │                 │  │ outbox table     │  │ outbox table   │  │
│ └─────────────────┘  └──────────────────┘  └────────────────┘  │
│                                                                  │
│ ┌──────────────────────┐    ┌──────────────────────────────┐   │
│ │ notification_db      │    │ transaction_history_db       │   │
│ │                      │    │                              │   │
│ │ notifications table  │    │ transactions_view table      │   │
│ │ retry_queue table    │    │ (denormalized, read-only)    │   │
│ │ outbox table         │    │ (built from Kafka events)    │   │
│ └──────────────────────┘    └──────────────────────────────┘   │
│                                                                  │
│ BENEFITS:                                                       │
│ ✓ Each service scales independently                             │
│ ✓ Schema changes don't affect other services                   │
│ ✓ Failures isolated (payment down ≠ account down)              │
│ ✓ Clear ownership (Account Service owns accounts_db)           │
│ ✓ Easy to migrate individual services                          │
│ ✓ Can use different DB technologies per service (future)       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 8. Idempotency (Preventing Duplicate Payments)

**Problem:** Client retries payment request → duplicate charges

```
CLIENT BEHAVIOR:
┌─────────────────────────────────────────────────────────────┐
│ T=0s:   POST /payments/create                               │
│         ├─ Request sent to Payment Service                  │
│         ├─ Processing... processing...                      │
│         └─ ⚠️  Network timeout (no response received)       │
│                                                             │
│ T=5s:   Network recovers, client assumes failed             │
│         POST /payments/create (retry with same data)        │
│         └─ ✓ Request reaches server                         │
│                                                             │
│ PROBLEM: Was first payment processed or not?               │
│ If yes: Client will be charged TWICE                        │
│ If no: Client will be charged once (correct)                │
└─────────────────────────────────────────────────────────────┘


SOLUTION: Idempotency Key
┌──────────────────────────────────────────────────────────────┐
│ CLIENT SENDS:                                                │
│ POST /payments/create                                        │
│ Headers: Idempotency-Key: 550e8400-e29b-41d4-a716-...      │
│ Body: { amount: 100.00, currency: "USD", ... }             │
├──────────────────────────────────────────────────────────────┤
│ REQUEST 1 (Original):                                        │
│ 1. Payment Service receives idempotency key                  │
│ 2. Query idempotency_keys table:                             │
│    SELECT * FROM idempotency_keys                            │
│    WHERE key = '550e8400...'                                │
│ 3. NOT FOUND (first time)                                   │
│ 4. INSERT into idempotency_keys:                             │
│    id='550e8400...', response='<payment response>', ...     │
│ 5. Create payment, return response                           │
│                                                              │
│ NETWORK TIMEOUT (client thinks failed)                       │
│                                                              │
│ REQUEST 2 (Retry with same Idempotency-Key):               │
│ 1. Payment Service receives idempotency key                  │
│ 2. Query idempotency_keys table:                             │
│    SELECT * FROM idempotency_keys                            │
│    WHERE key = '550e8400...'                                │
│ 3. ✓ FOUND! Previous response cached                         │
│ 4. Return cached response (same payment ID)                  │
│ 5. ✗ NO DUPLICATE PAYMENT CREATED                            │
│                                                              │
│ RESULT: Idempotent operation (safe to retry)                 │
└──────────────────────────────────────────────────────────────┘
```

---

## 9. Rate Limiting (Protection Against Abuse)

API Gateway limits requests per user:

```
REQUEST 1-100 (Per Minute):
┌────────────────────────────────────────┐
│ POST /payments/create                  │
│ User: john@example.com                 │
│ Gateway: ✓ Token bucket has 100 tokens │
│ │ Deduct 1 token                       │
│ │ Tokens left: 99                      │
│ └─ Request proceeds to Payment Service │
├────────────────────────────────────────┤
│ REQUEST 101 (Exceeds limit):           │
│ POST /payments/create                  │
│ User: john@example.com                 │
│ Gateway: ✓ Token bucket has 0 tokens   │
│ │ ✗ DENY request                       │
│ │ Return 429 Too Many Requests         │
│ │ Retry-After: 60 seconds              │
│ └─ Request BLOCKED                     │
├────────────────────────────────────────┤
│ AFTER 1 MINUTE (Auto-refill):          │
│ Token bucket refilled to 100 tokens    │
│ Next request succeeds                  │
└────────────────────────────────────────┘

WHY RATE LIMITING?
├─ Prevents abuse (100 payments/min per user)
├─ Protects backend from overload
├─ Ensures fair resource sharing
└─ Defense against DoS attacks
```

---

## 10. Complete Request/Response Cycle

**From start to finish, what happens:**

```
TIME    ACTOR              ACTION
────────────────────────────────────────────────────────────────────

0ms    CLIENT             POST /api/v1/payments/create
                          Authorization: Bearer <JWT>
                          Idempotency-Key: 550e8400...
                          Body: {amount: 100, currency: USD}

5ms    GATEWAY            ✓ Validate JWT signature
       (API Gateway)      ✓ Check expiration (token valid 1 hour)
                          ✓ Extract user_id = "user-123"
                          ✓ Rate limit check (99 of 100 tokens left)
                          → Route to Payment Service

10ms   PAYMENT SVC        ✓ Receive request
       (Port 8083)        ✓ Validate amount > 0
                          ✓ Check idempotency key (not seen before)
                          
                          TRANSACTION 1 START:
                          └─ INSERT payment (status: INITIATED)
                          └─ INSERT into outbox
                          └─ COMMIT
                          
                          Return 201 Created to client

15ms   OUTBOX POLLER      Runs every 1s
       (Scheduler)        → Find unpublished events
                          → Found 1: "payment.initiated"
                          → Publish to Kafka topic

20ms   FRAUD SERVICE      Subscribes to "payment.initiated"
       (Port 8082)        ✓ Receive event
                          → Load customer history
                          → Check fraud patterns
                          → Risk score: 25% (LOW)
                          → Decision: APPROVE
                          
                          TRANSACTION 2 START:
                          └─ INSERT into risk_scores table
                          └─ INSERT into outbox
                          └─ COMMIT

25ms   OUTBOX POLLER      Found "payment.fraud_checked" event
                          → Publish to Kafka

30ms   PAYMENT SVC        Subscribed to "payment.fraud_checked"
       (Kafka consumer)   ✓ Receive fraud approval
                          → Update saga state
                          → Status: FRAUD_CHECKED
                          
                          → NOW call Account Service (HTTP Feign)

35ms   ACCOUNT SVC        Feign HTTP call received
       (Port 8081)        POST /debit
                          Body: {account_id: user-123, amount: 100}
                          
                          TRANSACTION 3 START:
                          ├─ Query account (balance: 1000)
                          ├─ Verify balance > 100 ✓
                          ├─ INSERT ledger (DEBIT 100)
                          ├─ UPDATE account balance = 900
                          ├─ INSERT into outbox
                          └─ COMMIT
                          
                          Return 200 OK {new_balance: 900}

40ms   PAYMENT SVC        ✓ Received Account response
                          → Update saga state: DEBITED
                          
                          TRANSACTION 4 START:
                          ├─ UPDATE payment status: COMPLETED
                          ├─ INSERT into outbox
                          └─ COMMIT
                          
                          Return 200 OK to client

42ms   CLIENT             ✓ Receive success response
                          {
                            id: 550e8400...,
                            status: COMPLETED,
                            amount: 100.00
                          }

45ms   OUTBOX POLLER      Found "payment.completed" event
       (Scheduler)        → Publish to Kafka

50ms   NOTIFICATION SVC   Subscribed to "payment.completed"
       (Port 8084)        ✓ Receive event
                          → Send SMS: "Payment of $100 successful"
                          → Send email receipt

55ms   TRANSACTION HIST   Subscribed to "payment.completed"
       (CQRS Service)     ✓ Receive event
       (Port 8085)        → INSERT into transactions_view
                          → Denormalized for fast queries
                          
                          User can now query:
                          GET /transactions/550e8400...
                          Response: {id, amount, status, ...}

════════════════════════════════════════════════════════════════════
TOTAL TIME: ~55ms (from client POST to final Kafka publication)
════════════════════════════════════════════════════════════════════
```

---

## 11. Error Scenarios

**What happens when things fail:**

```
SCENARIO 1: Account Service Timeout
┌────────────────────────────────────────┐
│ T=30ms: Payment Service calls Account  │
│         (Feign HTTP)                   │
│                                        │
│ T=5000ms: No response from Account Svc │
│           (Account Service is slow)    │
│                                        │
│ ✗ Timeout occurs                       │
│   (Resilience4j timeout = 2s)         │
├────────────────────────────────────────┤
│ RECOVERY:                              │
│ 1. Retry with exponential backoff      │
│    Attempt 1: wait 100ms, retry        │
│    Attempt 2: wait 200ms, retry        │
│    Attempt 3: wait 400ms, retry        │
│                                        │
│ 2. If all retries fail:                │
│    ✓ Circuit breaker OPENS             │
│    ✗ All future calls fail fast        │
│    (don't waste time waiting)           │
│                                        │
│ 3. Circuit breaker monitoring:         │
│    Wait 30s, test with 1 request       │
│    If succeeds: CLOSE circuit breaker  │
│    If fails: Stay OPEN                 │
└────────────────────────────────────────┘


SCENARIO 2: Kafka Broker Down
┌────────────────────────────────────────┐
│ T=15ms: Outbox Poller tries to publish │
│         event to Kafka                 │
│                                        │
│ ✗ Kafka is DOWN                        │
│   Connection refused                   │
├────────────────────────────────────────┤
│ RECOVERY:                              │
│ 1. Event remains in outbox table:      │
│    ┌──────────────────────────┐        │
│    │ event_id: 1              │        │
│    │ event_type: payment...   │        │
│    │ published: FALSE ◄─ Not  │        │
│    │                   published       │
│    └──────────────────────────┘        │
│                                        │
│ 2. Poller retries next cycle (1s)     │
│    ✓ Kafka comes back online           │
│    → Event published successfully      │
│    → Mark as published                 │
│                                        │
│ ✓ ZERO DATA LOSS                       │
│ ✓ Eventually consistent                │
│   (delayed by outage duration)         │
└────────────────────────────────────────┘


SCENARIO 3: Idempotency Key Saves the Day
┌────────────────────────────────────────┐
│ REQUEST 1 (Original):                  │
│ Client: POST /payments/create          │
│ Idempotency-Key: 550e8400...          │
│                                        │
│ ✓ Payment created                      │
│ ✓ Stored in idempotency_keys table    │
│ Response sent to client...             │
│                                        │
│ ⚠️  NETWORK HICCUP (response lost)     │
│ Client doesn't receive response        │
├────────────────────────────────────────┤
│ REQUEST 2 (Client Retries):            │
│ Client: POST /payments/create          │
│ Idempotency-Key: 550e8400... (same!)  │
│                                        │
│ ✓ Payment Service receives             │
│ ✓ Looks up in idempotency_keys table  │
│ ✓ Found: 550e8400... → cached response │
│ → Return cached response (same ID)     │
│                                        │
│ ✓ DUPLICATE PREVENTED                  │
│ ✓ Client receives response (even if    │
│   it's the cached one from first try)  │
└────────────────────────────────────────┘
```

---

## 12. Summary: Why This Architecture?

```
PATTERN          WHY WE USE IT             BENEFIT
────────────────────────────────────────────────────────────────
Microservices    Independent deployments   Scale Payment Service
                                           without scaling Fraud

Saga             Distributed transactions  Cross-service payments
Orchestration    Coordinated flows         without 2-phase commit

Outbox Pattern   Atomic DB + events        No data loss if Kafka
                                           is temporarily down

Event-Driven     Loose coupling            Services don't need to
(Kafka)          Eventual consistency      know about each other

Separate DBs     Service isolation         Account down doesn't
                                           crash Payment Service

Idempotency      Duplicate prevention      Retries are safe
Keys             Exactly-once semantics    No duplicate charges

Rate Limiting    Abuse prevention          Fair resource sharing
                 DDoS protection           Protects backend

API Gateway      Single entry point        Centralized auth
                 JWT + authentication      Rate limiting

CQRS             Denormalized reads        Fast transaction history
                 Separation of concerns    queries (pre-aggregated)

Resilience4j     Fault tolerance           Timeouts, retries,
                 Graceful degradation      circuit breakers
```

---

## Next Steps

This diagram explains:
- ✓ What each service does
- ✓ How data flows through the system
- ✓ Why each pattern exists
- ✓ What happens on success & failure
- ✓ How events are guaranteed to be delivered
- ✓ Why database isolation matters

**To implement improvements:**
1. Add resilience patterns (Phase 1)
2. Add observability/tracing (Phase 2)
3. Deploy to Kubernetes (Phase 3)
4. Scale to 100 req/sec (Phase 4)
