CREATE TABLE outbox_event (
                              id BIGSERIAL PRIMARY KEY,
                              aggregate_id VARCHAR(255) NOT NULL,
                              aggregate_type VARCHAR(255) NOT NULL,
                              event_type VARCHAR(255) NOT NULL,
                              payload JSONB NOT NULL,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              published BOOLEAN DEFAULT FALSE
);
