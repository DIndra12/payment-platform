package com.payments.platform.transactionhistoryservice.api;

import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate view of an account's activity.
 *
 * <p>Caveat: {@code totalOutgoing} and {@code totalIncoming} sum raw amounts
 * across whatever currencies are present. That is only meaningful for a
 * single-currency account, which is all the platform models today. Multi-currency
 * would need these broken out per currency.
 */
@Getter
@Builder
public class TransactionSummaryResponse {

    private final UUID accountId;
    private final long totalTransactions;

    /** Sum of COMPLETED payments where this account was the payer. */
    private final BigDecimal totalOutgoing;

    /** Sum of COMPLETED payments where this account was the payee. */
    private final BigDecimal totalIncoming;

    /** Row counts keyed by derived status. Statuses with no rows are present as 0. */
    private final Map<TransactionStatus, Long> countByStatus;
}
