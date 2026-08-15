package com.payments.platform.paymentservice.controller;

import com.payments.platform.paymentservice.dto.PaymentRequest;
import com.payments.platform.paymentservice.dto.PaymentResponse;
import com.payments.platform.paymentservice.service.PaymentOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrchestratorService paymentOrchestratorService;

    /**
     * Initiate a payment.
     *
     * @param request payment details (payer, payee, amount, currency)
     * @param idempotencyKey unique identifier for this request (for idempotent retries)
     * @return 202 Accepted with payment ID and status
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentOrchestratorService.processPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}