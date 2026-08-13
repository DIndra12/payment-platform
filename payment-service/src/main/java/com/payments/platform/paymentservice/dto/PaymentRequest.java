package com.payments.platform.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {
    @NotNull
    private UUID payerAccountId;
    @NotNull
    private UUID payeeAccountId;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    @NotBlank
    private String currency;
}