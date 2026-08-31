package com.payments.platform.transactionhistoryservice.projection;

/**
 * Kafka coordinates of the record being projected. Recorded on the
 * {@code processed_event} row so a projected transaction can be traced back to
 * the exact records that built it.
 */
public record EventContext(String topic, Integer partition, Long offset) {

    public static EventContext of(String topic, Integer partition, Long offset) {
        return new EventContext(topic, partition, offset);
    }
}
