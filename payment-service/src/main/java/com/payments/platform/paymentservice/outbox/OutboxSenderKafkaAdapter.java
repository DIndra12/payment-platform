package com.payments.platform.paymentservice.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxSenderKafkaAdapter implements OutboxSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxSenderKafkaAdapter(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String payload) {
        kafkaTemplate.send(topic, payload);
    }
}
