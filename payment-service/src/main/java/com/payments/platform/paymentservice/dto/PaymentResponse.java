package com.payments.platform.paymentservice.dto;

import com.payments.platform.paymentservice.entity.PaymentStatus;
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