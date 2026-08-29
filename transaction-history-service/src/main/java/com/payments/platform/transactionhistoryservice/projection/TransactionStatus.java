package com.payments.platform.transactionhistoryservice.projection;

/**
 * Derived status of a transaction in the read model.
 *
 * <p>This is never taken verbatim from a single event — the real
 * {@code payment.completed} payload does not even carry a {@code status} field.
 * It is recomputed from the full set of events seen for a payment, so that
 * arrival order cannot affect the outcome. See
 * {@link TransactionProjector#deriveStatus}.
 */
public enum TransactionStatus {

    /**
     * Money has started moving (an {@code account.*} event arrived) but no
     * terminal payment event has been seen yet.
     */
    IN_PROGRESS,

    /** {@code payment.completed} was seen. Terminal. */
    COMPLETED,

    /** {@code payment.failed} was seen and {@code payment.completed} was not. Terminal. */
    FAILED;

    /**
     * Terminal states are never downgraded by a later-arriving {@code account.*}
     * event. This is what makes the projection order-independent.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
