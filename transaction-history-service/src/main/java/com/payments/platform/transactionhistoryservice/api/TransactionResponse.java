package com.payments.platform.transactionhistoryservice.api;

import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One stitched transaction, presented from the perspective of the account that
 * was queried.
 *
 * <p>Nulls are meaningful here rather than accidental: a null {@code creditedAt}
 * with a non-null {@code debitedAt} means the credit event has not been
 * projected yet. The API surfaces partial state honestly instead of hiding it.
 */
@Getter
@Builder
public class TransactionResponse {

    private final UUID paymentId;

    /** Relative to the queried account. Null if neither leg is known yet. */
    private final TransactionDirection direction;

    /** The other side of the transaction. Null if not yet known. */
    private final UUID counterpartyAccountId;

    private final UUID payerAccountId;
    private final UUID payeeAccountId;

    private final BigDecimal amount;
    private final String currency;
    private final TransactionStatus status;

    /** Populated only for FAILED transactions. */
    private final String failureReason;

    private final LocalDateTime debitedAt;
    private final LocalDateTime creditedAt;
    private final LocalDateTime completedAt;

    private final UUID debitLedgerId;
    private final UUID creditLedgerId;

    private final String traceId;

    /**
     * Which event types have been projected into this row. Exposed because for a
     * read model built from four independent streams, "why does this row look
     * like this" is a question worth being able to answer directly.
     */
    private final String eventsSeen;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
