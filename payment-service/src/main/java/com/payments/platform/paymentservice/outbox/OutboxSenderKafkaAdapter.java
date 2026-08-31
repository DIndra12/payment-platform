package com.payments.platform.paymentservice.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxSenderKafkaAdapter implements OutboxSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxSenderKafkaAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes with the aggregate id as the message key so that per-payment
     * ordering holds across partitions.
     */
    @Override
    public void send(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload);
    }
}
