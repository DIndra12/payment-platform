package com.payments.platform.transactionhistoryservice.api;

/**
 * Direction of a transaction <em>relative to the account being queried</em>.
 *
 * <p>The stored read model row is account-neutral — it records a payer and a
 * payee. Direction is derived per request, so the same row is a DEBIT to the
 * payer and a CREDIT to the payee.
 */
public enum TransactionDirection {

    /** The queried account is the payer: money out. */
    DEBIT,

    /** The queried account is the payee: money in. */
    CREDIT
}
