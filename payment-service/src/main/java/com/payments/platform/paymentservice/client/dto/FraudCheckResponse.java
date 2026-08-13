package com.payments.platform.paymentservice.client.dto;

import lombok.Data;

@Data
public class FraudCheckResponse {
    private boolean isFraudulent;
    private String riskReason;
}