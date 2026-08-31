package com.payments.platform.transactionhistoryservice.kafka;

import com.payments.platform.transactionhistoryservice.dto.PaymentCompletedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentFailedEvent;
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

/**
 * Consumes the payment lifecycle topics.
 *
 * <p>Two rules this class follows deliberately, both of which notification-service
 * gets wrong:
 *
 * <ol>
 *   <li><strong>Acknowledge only after the projection transaction commits.</strong>
 *       {@code TransactionProjector} is {@code @Transactional}, so the commit
 *       happens when the call returns through the proxy. Acking before that would
 *       risk losing an event if the commit failed.</li>
 *   <li><strong>Let exceptions propagate.</strong> Swallowing them into a log row
 *       means the container never sees a failure, which silently disables retries
 *       and the dead-letter route. Failing loudly is what makes the error handler
 *       in {@link KafkaConsumerConfig} actually do its job.</li>
 * </ol>
 */
@Slf4j
@Component
public class PaymentEventConsumer {

    private final TransactionProjector projector;
    private final ProjectionMetrics metrics;

    public PaymentEventConsumer(TransactionProjector projector, ProjectionMetrics metrics) {
        this.projector = projector;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "payment.completed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentCompletedListenerContainerFactory")
    public void onPaymentCompleted(@Payload PaymentCompletedEvent event,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                   @Header(KafkaHeaders.OFFSET) Long offset,
                                   Acknowledgment acknowledgment) {
        consume(topic, partition, offset, acknowledgment,
                context -> projector.onPaymentCompleted(event, context));
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentFailedListenerContainerFactory")
    public void onPaymentFailed(@Payload PaymentFailedEvent event,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                               @Header(KafkaHeaders.OFFSET) Long offset,
                               Acknowledgment acknowledgment) {
        consume(topic, partition, offset, acknowledgment,
                context -> projector.onPaymentFailed(event, context));
    }

    private void consume(String topic, Integer partition, Long offset,
                         Acknowledgment acknowledgment,
                         java.util.function.Function<EventContext, ProjectionResult> projection) {
        long startedAt = System.nanoTime();
        EventContext context = EventContext.of(topic, partition, offset);
        try {
            ProjectionResult result = projection.apply(context);
            metrics.recordConsumed(topic, result);
            // Commit has already happened at this point.
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
