package com.payments.platform.accountservice.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreditRequest {
    @NotNull(message = "Credit amount cannot be null")
    @Positive(message = "Credit amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Reference ID cannot be null")
    private UUID referenceId;
}
