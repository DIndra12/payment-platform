package com.payments.platform.transactionhistoryservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code account.debited}, published by account-service's outbox.
 *
 * <p>{@code referenceId} is the correlation key: payment-service passes
 * {@code payment.getId()} as {@code DebitRequest.referenceId}, account-service
 * stores it as {@code ledger_entries.reference_id}, so
 * {@code referenceId == paymentId}. That is how an account event finds its
 * transaction row.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AccountDebitedEvent {

    /** The payment this ledger movement belongs to. */
    private UUID referenceId;
    private UUID accountId;
    private UUID ledgerEntryId;
    private BigDecimal amount;
    private String currency;

    // Optional / forward-compatible.
    private UUID eventId;
    private LocalDateTime occurredAt;
    private String traceId;
}
