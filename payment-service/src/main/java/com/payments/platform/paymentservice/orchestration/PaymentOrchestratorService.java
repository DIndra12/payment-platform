package com.payments.platform.paymentservice.orchestration;

import com.payments.platform.paymentservice.client.AccountClient;
import com.payments.platform.paymentservice.client.FraudClient;
import com.payments.platform.paymentservice.client.dto.DebitRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckResponse;
import com.payments.platform.paymentservice.client.dto.RiskDecision;
import com.payments.platform.paymentservice.api.PaymentRequest;
import com.payments.platform.paymentservice.api.PaymentResponse;
import com.payments.platform.paymentservice.persistence.entity.Payment;
import com.payments.platform.paymentservice.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestratorService {

    private final PaymentRepository paymentRepository;
    private final FraudClient fraudClient;
    private final AccountClient accountClient;

    /**
     * Process a payment request idempotently.
     *
     * @param request payment details
     * @param idempotencyKey unique identifier; if a payment with this key exists, return its result
     * @return payment response (202 Accepted on success, with payment ID and status)
     */
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // 1. Idempotency check: if we've seen this key before, return the existing result
        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Idempotent retry detected for key: {}. Returning existing payment ID: {}",
                    idempotencyKey, existingPayment.get().getId());
            return mapToResponse(existingPayment.get());
        }

        // 2. Initialize Payment in Database
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .payerAccountId(request.getPayerAccountId())
                .payeeAccountId(request.getPayeeAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.INITIATED)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment INITIATED with ID: {} (idempotency key: {})", payment.getId(), idempotencyKey);

        try {
            // 2. Fraud Check Step
            FraudCheckResponse fraudResponse = fraudClient.evaluateRisk(
                    FraudCheckRequest.builder()
                            .payerAccountId(request.getPayerAccountId().toString())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .build()
            );

            if (fraudResponse.decision() == RiskDecision.REJECT) {
                String reason = String.join(", ", fraudResponse.reasons());
                log.warn("Payment {} rejected by Fraud Service (risk score: {}). Reasons: {}",
                        payment.getId(), fraudResponse.riskScore(), reason);
                return updatePaymentState(payment, PaymentStatus.REJECTED_BY_FRAUD, reason);
            }

            // 3. Account Debit Step
            accountClient.debitAccount(
                    request.getPayerAccountId().toString(),
                    DebitRequest.builder()
                            .amount(request.getAmount())
                            .referenceId(payment.getId().toString()) // Pass Payment ID for idempotency!
                            .build()
            );

            // 4. Account Credit Step
            accountClient.creditAccount(
                    request.getPayeeAccountId().toString(),
                    DebitRequest.builder()
                            .amount(request.getAmount())
                            .referenceId(payment.getId().toString()) // Pass Payment ID for idempotency!
                            .build()
            );

            // 5. Complete Payment
            log.info("Payment {} COMPLETED successfully.", payment.getId());
            return updatePaymentState(payment, PaymentStatus.COMPLETED, "Payment successful");

        } catch (Exception e) {
            log.error("Payment {} FAILED during orchestration: {}", payment.getId(), e.getMessage());
            return updatePaymentState(payment, PaymentStatus.FAILED, e.getMessage());
        }
    }

    private PaymentResponse updatePaymentState(Payment payment, PaymentStatus newStatus, String reason) {
        payment.setStatus(newStatus);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .message(payment.getFailureReason())
                .build();
    }
}
