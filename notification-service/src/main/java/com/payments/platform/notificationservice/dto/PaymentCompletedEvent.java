package com.payments.platform.notificationservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
    UUID eventId,
    UUID paymentId,
    UUID payerAccountId,
    UUID payeeAccountId,
    BigDecimal amount,
    String currency,
    Instant completedAt
) {}
