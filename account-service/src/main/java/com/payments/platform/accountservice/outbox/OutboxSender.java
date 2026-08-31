package com.payments.platform.accountservice.outbox;

/**
 * Transport for outbox rows. The {@code key} is what Kafka partitions on, so
 * passing the accountId keeps all events for one account ordered.
 */
public interface OutboxSender {

    void send(String topic, String key, String payload);
}
