package com.payments.platform.paymentservice.client.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class DebitRequest {
    private BigDecimal amount;
    private String referenceId; // Used for idempotency in Account Service
}