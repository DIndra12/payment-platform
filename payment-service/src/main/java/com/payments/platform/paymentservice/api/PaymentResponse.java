package com.payments.platform.paymentservice.api;

import com.payments.platform.paymentservice.orchestration.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private PaymentStatus status;
    private String message;
}
