package com.payments.platform.paymentservice.service;

import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaOperations<String, String> kafkaOperations;

    public OutboxPublisher(OutboxEventRepository repository, KafkaOperations<String, String> kafkaOperations) {
        this.repository = repository;
        this.kafkaOperations = kafkaOperations;
    }

    @Scheduled(fixedDelay = 5000) // every 5 seconds
    public void publishEvents() {
        List<OutboxEvent> events = repository.findByPublishedFalse();
        for (OutboxEvent event : events) {
            kafkaOperations.send(event.getEventType(), event.getPayload().toString());
            event.setPublished(true);
            repository.save(event);
        }
    }
}

