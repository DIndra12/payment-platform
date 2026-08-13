package com.payments.platform.paymentservice.client.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class FraudCheckRequest {
    private String payerAccountId;
    private BigDecimal amount;
    private String currency;
}