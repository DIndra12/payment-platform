package com.payments.platform.paymentservice.outbox;

public interface OutboxSender {
    void send(String topic, String payload);
}
