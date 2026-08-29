package com.payments.platform.accountservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Polls unpublished outbox rows and pushes them to Kafka.
 *
 * <p>Requires {@code @EnableScheduling} on {@code AccountServiceApplication}.
 */
@Slf4j
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
            try {
                outboxSender.send(event.getEventType(), event.getAggregateId(),
                        event.getPayload().toString());
                event.setPublished(true);
                repository.save(event);
                log.debug("Published outbox event {} for aggregate {}",
                        event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                // Leave published=false so the next poll retries. At-least-once by
                // design: a duplicate is cheap because consumers dedupe, whereas a
                // dropped event silently corrupts every downstream read model.
                log.error("Failed to publish outbox event {} (id {}); will retry on next poll",
                        event.getEventType(), event.getId(), e);
            }
        }
    }
}
