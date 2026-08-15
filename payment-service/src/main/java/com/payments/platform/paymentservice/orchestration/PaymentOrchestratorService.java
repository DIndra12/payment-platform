package com.payments.platform.paymentservice.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.paymentservice.client.AccountClient;
import com.payments.platform.paymentservice.client.FraudClient;
import com.payments.platform.paymentservice.client.dto.DebitRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckResponse;
import com.payments.platform.paymentservice.client.dto.RiskDecision;
import com.payments.platform.paymentservice.api.PaymentRequest;
import com.payments.platform.paymentservice.api.PaymentResponse;
import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.outbox.OutboxEventRepository;
import com.payments.platform.paymentservice.persistence.entity.Payment;
import com.payments.platform.paymentservice.persistence.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestratorService {

    private final PaymentRepository paymentRepository;
    private final FraudClient fraudClient;
    private final AccountClient accountClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Idempotent retry detected for key: {}. Returning existing payment ID: {}",
                    idempotencyKey, existingPayment.get().getId());
            return mapToResponse(existingPayment.get());
        }

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
            FraudCheckResponse fraudResponse = fraudClient.evaluateRisk(
                    FraudCheckRequest.builder()
                            .payerAccountId(request.getPayerAccountId().toString())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .build()
            );

            if (fraudResponse.decision() == RiskDecision.REJECT) {
                String reason = String.join(", ", fraudResponse.reasons());
                log.warn("Payment {} rejected by Fraud Service. Reasons: {}", payment.getId(), reason);
                return updatePaymentState(payment, PaymentStatus.REJECTED_BY_FRAUD, reason);
            }

            accountClient.debitAccount(
                    request.getPayerAccountId().toString(),
                    DebitRequest.builder()
                            .amount(request.getAmount())
                            .referenceId(payment.getId().toString())
                            .build()
            );

            accountClient.creditAccount(
                    request.getPayeeAccountId().toString(),
                    DebitRequest.builder()
                            .amount(request.getAmount())
                            .referenceId(payment.getId().toString())
                            .build()
            );

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

        if (newStatus == PaymentStatus.COMPLETED) {
            createOutboxEvent(payment);
        }

        return mapToResponse(payment);
    }

    private void createOutboxEvent(Payment payment) {
        try {
            Map<String, Object> eventPayload = Map.of(
                    "paymentId", payment.getId(),
                    "payerAccountId", payment.getPayerAccountId(),
                    "payeeAccountId", payment.getPayeeAccountId(),
                    "amount", payment.getAmount(),
                    "currency", payment.getCurrency()
            );

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("payment");
            outboxEvent.setAggregateId(payment.getId().toString());
            outboxEvent.setEventType("payment.completed");
            outboxEvent.setPayload(objectMapper.valueToTree(eventPayload));
            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event created for payment {}", payment.getId());
        } catch (Exception e) {
            log.error("Failed to create outbox event for payment {}", payment.getId(), e);
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .message(payment.getFailureReason())
                .build();
    }
}
