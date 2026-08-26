package com.payments.platform.notificationservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private UUID eventId;
    private UUID paymentId;
    private UUID payerAccountId;
    private UUID payeeAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
    private LocalDateTime occurredAt;
    private String traceId;
}
