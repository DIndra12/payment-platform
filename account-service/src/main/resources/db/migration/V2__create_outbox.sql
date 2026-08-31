-- Outbox for account-service, mirroring payment-service's outbox_event.
--
-- Why an outbox rather than publishing straight to Kafka from AccountService:
-- the ledger write and the event publish must not be able to disagree. Inserting
-- the event row in the same transaction as the ledger entry means either both
-- happen or neither does; a separate poller then moves rows to Kafka.

CREATE TABLE outbox_event (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published      BOOLEAN DEFAULT FALSE
);

-- Create index for the poller's hot query
CREATE INDEX idx_outbox_event_published ON outbox_event(published) WHERE published = FALSE;
