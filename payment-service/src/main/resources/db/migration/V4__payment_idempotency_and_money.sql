-- V4: Add idempotency key and align money precision
-- Supports idempotent payment initiation (design §2.1, §3.7)
-- Aligns payment.amount precision with ledger_entries (both NUMERIC(19,4))

-- Idempotency: clients can safely retry payment initiation
ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(64);
UPDATE payments SET idempotency_key = id::text WHERE idempotency_key IS NULL;
ALTER TABLE payments ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX uk_payments_idempotency_key ON payments(idempotency_key);

-- Money precision: DECIMAL(19,2) → NUMERIC(19,4) to match account-service ledger
ALTER TABLE payments ALTER COLUMN amount TYPE NUMERIC(19,4);

-- Status field: widen to accommodate new state names (COMPENSATED, RISK_CHECKED, etc.)
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(30);

-- Failure reason: unlimited text for detailed error messages (removes truncation hack)
ALTER TABLE payments ALTER COLUMN failure_reason TYPE TEXT;
