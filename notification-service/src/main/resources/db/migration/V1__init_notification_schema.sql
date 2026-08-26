-- Create notification_log table
CREATE TABLE notification_log (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    payment_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0
);

-- Create index for querying by payment_id
CREATE INDEX idx_notification_log_payment_id ON notification_log(payment_id);

-- Create index for querying by event_id (deduplication)
CREATE INDEX idx_notification_log_event_id ON notification_log(event_id);

-- Create index for querying by status
CREATE INDEX idx_notification_log_status ON notification_log(status);

-- Create index for querying by created_at
CREATE INDEX idx_notification_log_created_at ON notification_log(created_at DESC);
