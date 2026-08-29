package com.payments.platform.transactionhistoryservice.kafka;

import com.payments.platform.transactionhistoryservice.dto.AccountCreditedEvent;
import com.payments.platform.transactionhistoryservice.dto.AccountDebitedEvent;
import com.payments.platform.transactionhistoryservice.metrics.ProjectionMetrics;
import com.payments.platform.transactionhistoryservice.projection.EventContext;
import com.payments.platform.transactionhistoryservice.projection.ProjectionResult;
import com.payments.platform.transactionhistoryservice.projection.TransactionProjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Consumes the ledger movement topics published by account-service's outbox.
 *
 * <p>These events carry {@code referenceId}, which is the {@code paymentId} —
 * account-service stores the value payment-service passed as
 * {@code DebitRequest.referenceId}. That is what lets a ledger movement find its
 * transaction row, even when it arrives before the payment event that would
 * otherwise have created it.
 */
@Slf4j
@Component
public class AccountEventConsumer {

    private final TransactionProjector projector;
    private final ProjectionMetrics metrics;

    public AccountEventConsumer(TransactionProjector projector, ProjectionMetrics metrics) {
        this.projector = projector;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "account.debited",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountDebitedListenerContainerFactory")
    public void onAccountDebited(@Payload AccountDebitedEvent event,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                @Header(KafkaHeaders.OFFSET) Long offset,
                                Acknowledgment acknowledgment) {
        consume(topic, partition, offset, acknowledgment,
                context -> projector.onAccountDebited(event, context));
    }

    @KafkaListener(
            topics = "account.credited",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "accountCreditedListenerContainerFactory")
    public void onAccountCredited(@Payload AccountCreditedEvent event,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                 @Header(KafkaHeaders.OFFSET) Long offset,
                                 Acknowledgment acknowledgment) {
        consume(topic, partition, offset, acknowledgment,
                context -> projector.onAccountCredited(event, context));
    }

    private void consume(String topic, Integer partition, Long offset,
                         Acknowledgment acknowledgment,
                         Function<EventContext, ProjectionResult> projection) {
        long startedAt = System.nanoTime();
        EventContext context = EventContext.of(topic, partition, offset);
        try {
            ProjectionResult result = projection.apply(context);
            metrics.recordConsumed(topic, result);
            acknowledgment.acknowledge();
        } catch (RuntimeException e) {
            metrics.recordFailure(topic);
            log.error("Failed to project record from {}-{} offset {}; leaving unacknowledged "
                    + "for the error handler to retry or dead-letter", topic, partition, offset, e);
            throw e;
        } finally {
            metrics.recordDuration(topic, System.nanoTime() - startedAt);
        }
    }
}
