package com.payments.platform.paymentservice.controller;

import com.payments.platform.paymentservice.dto.PaymentRequest;
import com.payments.platform.paymentservice.dto.PaymentResponse;
import com.payments.platform.paymentservice.service.PaymentOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrchestratorService paymentOrchestratorService;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentOrchestratorService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}