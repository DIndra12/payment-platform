package com.payments.platform.paymentservice.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Polls unpublished outbox rows and pushes them to Kafka.
 *
 * <p>Requires {@code @EnableScheduling} on the application class to run at all.
 */
@Service
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final OutboxSender outboxSender;

    public OutboxPublisher(OutboxEventRepository repository, OutboxSender outboxSender) {
        this.repository = repository;
        this.outboxSender = outboxSender;
    }

    @Scheduled(fixedDelay = 5000) // every 5 seconds
    public void publishEvents() {
        List<OutboxEvent> events = repository.findByPublishedFalse();
        for (OutboxEvent event : events) {
            // eventType doubles as the topic name; aggregateId becomes the message
            // key so all events for one payment share a partition and stay ordered.
            outboxSender.send(event.getEventType(), event.getAggregateId(), event.getPayload().toString());
            event.setPublished(true);
            repository.save(event);
        }
    }
}
