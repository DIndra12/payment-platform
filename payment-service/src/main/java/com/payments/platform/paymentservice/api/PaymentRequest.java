package com.payments.platform.paymentservice.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {
    @NotNull(message = "Payer account ID cannot be null")
    private UUID payerAccountId;

    @NotNull(message = "Payee account ID cannot be null")
    private UUID payeeAccountId;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @Pattern(
        regexp = "^[A-Z]{3}$",
        message = "Currency must be a valid ISO 4217 code (3 uppercase letters, e.g., USD, INR, EUR)"
    )
    private String currency;
}
