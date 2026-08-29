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
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestratorService {

    /** Event type doubles as the Kafka topic name in {@code OutboxPublisher}. */
    private static final String EVENT_PAYMENT_COMPLETED = "payment.completed";
    private static final String EVENT_PAYMENT_FAILED = "payment.failed";

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

        // Every terminal state produces an event. Publishing only the happy path
        // left failed payments invisible to every downstream consumer, which made
        // "why did my payment not go through" unanswerable from the read model.
        if (newStatus == PaymentStatus.COMPLETED) {
            createOutboxEvent(payment, EVENT_PAYMENT_COMPLETED);
        } else if (newStatus == PaymentStatus.FAILED || newStatus == PaymentStatus.REJECTED_BY_FRAUD) {
            createOutboxEvent(payment, EVENT_PAYMENT_FAILED);
        }

        return mapToResponse(payment);
    }

    private void createOutboxEvent(Payment payment, String eventType) {
        try {
            // LinkedHashMap rather than Map.of: field order is then stable, and
            // null values (failureReason on a completed payment) are permitted.
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            // eventId gives consumers a stable dedupe key. Without it they are
            // forced to dedupe on (aggregateId, eventType).
            eventPayload.put("eventId", UUID.randomUUID().toString());
            eventPayload.put("paymentId", payment.getId());
            eventPayload.put("payerAccountId", payment.getPayerAccountId());
            eventPayload.put("payeeAccountId", payment.getPayeeAccountId());
            eventPayload.put("amount", payment.getAmount());
            eventPayload.put("currency", payment.getCurrency());
            eventPayload.put("status", payment.getStatus().name());
            // ISO_LOCAL_DATE_TIME (no zone suffix) so consumers can bind straight to
            // LocalDateTime, which is what every entity on this platform uses. The
            // design doc's example shows a trailing 'Z'; that would not deserialize
            // into LocalDateTime without extra consumer configuration.
            eventPayload.put("occurredAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            // Populated once OpenTelemetry is wired up; null until then.
            eventPayload.put("traceId", MDC.get("traceId"));

            if (EVENT_PAYMENT_FAILED.equals(eventType)) {
                eventPayload.put("failureReason", payment.getFailureReason());
            }

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("payment");
            outboxEvent.setAggregateId(payment.getId().toString());
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(objectMapper.valueToTree(eventPayload));
            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event {} created for payment {}", eventType, payment.getId());
        } catch (Exception e) {
            // Swallowed deliberately: failing here would roll back a payment whose
            // money has already moved. The cost is that a lost outbox row means a
            // silently missing event, so this log line is worth alerting on.
            log.error("Failed to create outbox event {} for payment {}", eventType, payment.getId(), e);
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
