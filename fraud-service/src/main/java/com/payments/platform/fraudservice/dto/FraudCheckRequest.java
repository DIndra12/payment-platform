package com.payments.platform.fraudservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;


import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FraudCheckRequest {
    @NotNull private UUID payerAccountId;
    @NotNull @Positive private BigDecimal amount;
    @NotNull private String currency;
}