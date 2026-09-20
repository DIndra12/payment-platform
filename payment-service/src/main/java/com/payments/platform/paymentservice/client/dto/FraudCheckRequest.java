package com.payments.platform.paymentservice.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class FraudCheckRequest {
    @NotBlank(message = "Payer account ID cannot be blank")
    private String payerAccountId;

    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @Pattern(
        regexp = "^[A-Z]{3}$",
        message = "Currency must be a valid ISO 4217 code (3 uppercase letters, e.g., USD, INR, EUR)"
    )
    private String currency;
}