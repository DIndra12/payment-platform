package com.payments.platform.paymentservice;

import com.payments.platform.paymentservice.outbox.OutboxSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestOutboxSenderConfig {

    @Bean
    public OutboxSender outboxSender() {
        return (topic, payload) -> {
            // stub: do nothing, assume send succeeds
        };
    }
}
