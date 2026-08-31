package com.payments.platform.transactionhistoryservice.projection;

/**
 * Outcome of projecting one event. Returned rather than thrown so consumers can
 * distinguish "did nothing on purpose" from "failed", and tag metrics accordingly.
 */
public enum ProjectionResult {

    /** The event was merged into the read model. */
    APPLIED,

    /**
     * This {@code (paymentId, eventType)} was already projected. Kafka redelivered
     * it; doing nothing is the correct behaviour.
     */
    SKIPPED_DUPLICATE
}
