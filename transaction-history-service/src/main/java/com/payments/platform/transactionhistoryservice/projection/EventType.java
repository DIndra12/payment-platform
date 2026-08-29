package com.payments.platform.transactionhistoryservice.projection;

import java.util.Arrays;

/**
 * The four event types this service projects into the read model.
 *
 * <p>The wire value doubles as the Kafka topic name: payment-service's
 * {@code OutboxPublisher} sends to {@code event.getEventType()} directly, so
 * topic name and event type are the same string by construction.
 */
public enum EventType {

    PAYMENT_COMPLETED("payment.completed"),
    PAYMENT_FAILED("payment.failed"),
    ACCOUNT_DEBITED("account.debited"),
    ACCOUNT_CREDITED("account.credited");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    /** The topic name / {@code event_type} column value. */
    public String wireName() {
        return wireName;
    }

    public static EventType fromWireName(String wireName) {
        return Arrays.stream(values())
                .filter(t -> t.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + wireName));
    }
}
