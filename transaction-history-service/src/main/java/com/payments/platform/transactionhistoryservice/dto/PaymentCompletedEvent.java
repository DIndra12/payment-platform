package com.payments.platform.transactionhistoryservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code payment.completed}.
 *
 * <p><strong>Only the first five fields are guaranteed.</strong> The payload
 * payment-service actually publishes today is:
 * <pre>
 * {"paymentId":"..","payerAccountId":"..","payeeAccountId":"..","amount":1500.0000,"currency":"INR"}
 * </pre>
 * {@code eventId}, {@code status}, {@code occurredAt} and {@code traceId} are
 * declared because the design doc specifies them and Phase B adds them, but the
 * projector must treat them as optional — Jackson leaves them null otherwise.
 * This is why dedupe is keyed on {@code (paymentId, eventType)} rather than
 * {@code eventId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentCompletedEvent {

    private UUID paymentId;
    private UUID payerAccountId;
    private UUID payeeAccountId;
    private BigDecimal amount;
    private String currency;

    // Optional / forward-compatible — may be null on the wire today.
    private UUID eventId;
    private String status;
    private LocalDateTime occurredAt;
    private String traceId;
}
