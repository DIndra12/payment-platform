package com.payments.platform.fraudservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FraudCheckRequest {
    @NotNull(message = "Payer account ID cannot be null")
    private UUID payerAccountId;

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
