package com.payments.platform.paymentservice.service;

import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000) // every 5 seconds
    public void publishEvents() {
        List<OutboxEvent> events = repository.findByPublishedFalse();
        for (OutboxEvent event : events) {
            kafkaTemplate.send(event.getEventType(), event.getPayload().toString());
            event.setPublished(true);
            repository.save(event);
        }
    }
}

