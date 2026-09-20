package com.payments.platform.paymentservice.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class DebitRequest {
    @NotNull(message = "Debit amount cannot be null")
    @Positive(message = "Debit amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Reference ID cannot be null")
    private String referenceId;
}