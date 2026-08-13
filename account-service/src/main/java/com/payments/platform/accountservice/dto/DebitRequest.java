package com.payments.platform.accountservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class DebitRequest {
    @NotNull @Positive private BigDecimal amount;
    @NotNull private UUID referenceId;
}