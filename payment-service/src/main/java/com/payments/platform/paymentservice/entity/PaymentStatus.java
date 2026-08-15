package com.payments.platform.paymentservice.entity;

/**
 * Payment lifecycle states (Saga orchestration).
 *
 * Transitions:
 * INITIATED → RISK_CHECKED → DEBITED → CREDITED → COMPLETED
 *          ↘ REJECTED_BY_FRAUD    ↘ FAILED  ↘ COMPENSATED
 *
 * Design §2.1, §3.2
 */
public enum PaymentStatus {
    /** Payment row created, awaiting fraud check */
    INITIATED,

    /** Fraud check passed; payer balance reserved */
    RISK_CHECKED,

    /** Payer debited; awaiting payee credit */
    DEBITED,

    /** Payee credited; payment complete */
    CREDITED,

    /** Final state: payment succeeded */
    COMPLETED,

    /** Fraud Service rejected the payment */
    REJECTED_BY_FRAUD,

    /** Payment failed (infrastructure or business error) */
    FAILED,

    /** Debit succeeded but credit failed; compensating credit issued */
    COMPENSATED
}