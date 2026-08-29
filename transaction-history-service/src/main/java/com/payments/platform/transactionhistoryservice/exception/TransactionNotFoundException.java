package com.payments.platform.transactionhistoryservice.exception;

import java.util.UUID;

/**
 * No read model row exists for this payment.
 *
 * <p>Worth reading as "not projected yet" rather than "does not exist": this
 * service is eventually consistent, so a payment that was just accepted may not
 * have reached the read model. Clients needing read-your-writes should query
 * payment-service directly.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID paymentId) {
        super("No transaction history found for payment " + paymentId
                + " (it may not have been projected yet)");
    }
}
