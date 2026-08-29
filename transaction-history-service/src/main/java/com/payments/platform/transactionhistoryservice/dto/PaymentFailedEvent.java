package com.payments.platform.transactionhistoryservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code payment.failed}.
 *
 * <p>Same optional-field caveat as {@link PaymentCompletedEvent}, plus
 * {@code failureReason}, which is the field that makes "why did my payment not
 * go through" answerable from the read model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentFailedEvent {

    private UUID paymentId;
    private UUID payerAccountId;
    private UUID payeeAccountId;
    private BigDecimal amount;
    private String currency;
    private String failureReason;

    // Optional / forward-compatible — may be null on the wire today.
    private UUID eventId;
    private String status;
    private LocalDateTime occurredAt;
    private String traceId;
}
