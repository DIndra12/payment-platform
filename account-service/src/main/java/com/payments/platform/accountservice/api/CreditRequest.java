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
    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private UUID referenceId;
}
