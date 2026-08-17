package com.payments.platform.notificationservice.kafka;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service")
    public void consumePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received payment completed event from topic: {}, partition: {}, offset: {}, paymentId: {}",
                    topic, partition, offset, event.getPaymentId());
            
            notificationService.handlePaymentCompleted(event);
            
            // Manual acknowledgment after successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed and acknowledged payment completed event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error processing payment completed event: {}", event.getEventId(), e);
            // Don't acknowledge - message will be redelivered or sent to DLT
            throw e;
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service")
    public void consumePaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received payment failed event from topic: {}, partition: {}, offset: {}, paymentId: {}",
                    topic, partition, offset, event.getPaymentId());
            
            notificationService.handlePaymentFailed(event);
            
            // Manual acknowledgment after successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed and acknowledged payment failed event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error processing payment failed event: {}", event.getEventId(), e);
            // Don't acknowledge - message will be redelivered or sent to DLT
            throw e;
        }
    }
}
