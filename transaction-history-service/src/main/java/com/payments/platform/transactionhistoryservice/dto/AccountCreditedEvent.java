package com.payments.platform.transactionhistoryservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code account.credited}, published by account-service's outbox.
 *
 * <p>As with {@link AccountDebitedEvent}, {@code referenceId} is the
 * {@code paymentId} used to correlate into the read model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AccountCreditedEvent {

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
