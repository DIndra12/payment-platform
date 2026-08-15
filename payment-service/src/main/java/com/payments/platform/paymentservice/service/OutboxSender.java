package com.payments.platform.paymentservice.service;

public interface OutboxSender {
    void send(String topic, String payload);
}
