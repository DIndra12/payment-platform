package com.payments.platform.transactionhistoryservice.exception;

/**
 * The event is structurally unusable — most importantly, it has no correlation
 * key, so it can never be attached to a transaction.
 *
 * <p>This is <strong>not retryable</strong>: redelivering the same malformed
 * record will fail identically. The Kafka error handler is configured to route
 * it straight to the dead-letter topic instead of burning retries on it.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
