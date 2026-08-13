package com.payments.platform.paymentservice.service;

import com.payments.platform.paymentservice.client.AccountClient;
import com.payments.platform.paymentservice.client.FraudClient;
import com.payments.platform.paymentservice.client.dto.DebitRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckResponse;
import com.payments.platform.paymentservice.dto.PaymentRequest;
import com.payments.platform.paymentservice.dto.PaymentResponse;
import com.payments.platform.paymentservice.entity.Payment;
import com.payments.platform.paymentservice.entity.PaymentStatus;
import com.payments.platform.paymentservice.repository.PaymentRepository;
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

    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Initialize Payment in Database
        Payment payment = Payment.builder()
                .payerAccountId(request.getPayerAccountId())
                .payeeAccountId(request.getPayeeAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.INITIATED)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment INITIATED with ID: {}", payment.getId());

        try {
            // 2. Fraud Check Step
            FraudCheckResponse fraudResponse = fraudClient.evaluateRisk(
                    FraudCheckRequest.builder()
                            .payerAccountId(request.getPayerAccountId().toString())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .build()
            );

            if (fraudResponse.isFraudulent()) {
                log.warn("Payment {} rejected by Fraud Service. Reason: {}", payment.getId(), fraudResponse.getRiskReason());
                return updatePaymentState(payment, PaymentStatus.REJECTED_BY_FRAUD, fraudResponse.getRiskReason());
            }

            // 3. Account Debit Step
            accountClient.debitAccount(
                    request.getPayerAccountId().toString(),
                    DebitRequest.builder()
                            .amount(request.getAmount())
                            .referenceId(payment.getId().toString()) // Pass Payment ID for idempotency!
                            .build()
            );

            // 4. Complete Payment
            log.info("Payment {} COMPLETED successfully.", payment.getId());
            return updatePaymentState(payment, PaymentStatus.COMPLETED, "Payment successful");

        } catch (Exception e) {
            log.error("Payment {} FAILED during orchestration: {}", payment.getId(), e.getMessage());
            return updatePaymentState(payment, PaymentStatus.FAILED, e.getMessage());
        }
    }

    private PaymentResponse updatePaymentState(Payment payment, PaymentStatus newStatus, String reason) {
        payment.setStatus(newStatus);

        // Safely truncate the reason to 255 characters to satisfy the database constraint
        String safeReason = reason;
        if (reason != null && reason.length() > 255) {
            safeReason = reason.substring(0, 252) + "...";
        }

        payment.setFailureReason(safeReason);
        paymentRepository.save(payment);

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(newStatus)
                .message(safeReason)
                .build();
    }
}