-- Transaction History Service — CQRS read model schema
--
-- See transaction-history-service-design.md section 6.
--
-- Two tables:
--   transaction_history : denormalized, one row per paymentId, built by merging
--                         events from up to four topics
--   processed_event     : idempotency ledger, guards against Kafka's
--                         at-least-once redelivery

-- ---------------------------------------------------------------------------
-- transaction_history — the read model
--
-- Every column sourced from an event is NULLABLE except payment_id and status.
-- This is deliberate: a row may be created by whichever event arrives first
-- (events are unkeyed today, so partitioning is round-robin and ordering is
-- not guaranteed), then filled in as the remaining events land.
-- ---------------------------------------------------------------------------
CREATE TABLE transaction_history (
    id                  UUID PRIMARY KEY,
    payment_id          UUID NOT NULL,
    payer_account_id    UUID,
    payee_account_id    UUID,
    amount              NUMERIC(19,4),
    currency            VARCHAR(3),
    status              VARCHAR(20) NOT NULL,
    failure_reason      TEXT,
    debited_at          TIMESTAMP,
    credited_at         TIMESTAMP,
    completed_at        TIMESTAMP,
    debit_ledger_id     UUID,
    credit_ledger_id    UUID,
    trace_id            VARCHAR(64),
    events_seen         VARCHAR(255) NOT NULL DEFAULT '',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transaction_history_payment_id UNIQUE (payment_id)
);

-- Separate payer/payee indexes are what make the account history query a
-- single indexed scan. An account can appear on either side of a payment.
CREATE INDEX idx_txn_history_payer ON transaction_history(payer_account_id, created_at DESC);
CREATE INDEX idx_txn_history_payee ON transaction_history(payee_account_id, created_at DESC);

-- Create index for filtering by derived status
CREATE INDEX idx_txn_history_status ON transaction_history(status);

-- Create index for the default newest-first ordering
CREATE INDEX idx_txn_history_created_at ON transaction_history(created_at DESC);

-- ---------------------------------------------------------------------------
-- processed_event — idempotency ledger
--
-- The uniqueness key is (payment_id, event_type), NOT event_id.
--
-- Reason: the real payment.completed payload on the wire today carries only
-- paymentId, payerAccountId, payeeAccountId, amount and currency — there is no
-- eventId (see design doc section 3, gap #3). A UNIQUE constraint on event_id
-- would therefore be violated by the second real event, which is the latent
-- bug notification_log currently carries.
--
-- (payment_id, event_type) mirrors account-service's proven
-- UNIQUE(reference_id, entry_type) idempotency and works with the fields that
-- actually arrive. event_id is still recorded so it can become the key once
-- producers start sending it.
-- ---------------------------------------------------------------------------
CREATE TABLE processed_event (
    id           UUID PRIMARY KEY,
    event_id     UUID,
    payment_id   UUID NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    partition_id INTEGER,
    offset_value BIGINT,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_payment_event UNIQUE (payment_id, event_type)
);

-- Create index for querying by event_id (not unique — see note above)
CREATE INDEX idx_processed_event_event_id ON processed_event(event_id);

-- Create index for looking up every event that built a given transaction
CREATE INDEX idx_processed_event_payment_id ON processed_event(payment_id);
