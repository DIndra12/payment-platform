package com.payments.platform.paymentservice.api;

import com.payments.platform.paymentservice.orchestration.PaymentOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    /**
     * Get the current status of a payment.
     *
     * @param paymentId payment ID returned by POST /api/v1/payments
     * @return 200 with the payment, or 404 if it does not exist
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return paymentOrchestratorService.getPayment(paymentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
