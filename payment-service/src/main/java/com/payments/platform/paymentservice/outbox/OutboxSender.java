package com.payments.platform.paymentservice.outbox;

/**
 * Transport for outbox rows.
 *
 * <p>The {@code key} parameter matters more than it looks: Kafka partitions by
 * key, so passing the aggregate id (the payment id) is what guarantees all
 * events for one payment land on the same partition and are therefore delivered
 * in order. With a null key records round-robin across partitions and ordering
 * is lost, which forces every consumer to be order-independent.
 */
public interface OutboxSender {

    void send(String topic, String key, String payload);
}
