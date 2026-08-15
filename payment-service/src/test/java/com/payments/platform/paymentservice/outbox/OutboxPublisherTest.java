package com.payments.platform.paymentservice.outbox;

import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.outbox.OutboxEventRepository;
import com.payments.platform.paymentservice.outbox.OutboxPublisher;
import com.payments.platform.paymentservice.outbox.OutboxSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OutboxPublisherTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisher publisher;

    @MockBean
    private OutboxSender outboxSender;

    @Test
    void testEventIsPublished() throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId("123");
        event.setAggregateType("Payment");
        event.setEventType("payment.initiated");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode payload = mapper.readTree("{\"amount\":100}");
        event.setPayload(payload);
        repository.save(event);

        publisher.publishEvents();

        OutboxEvent saved = repository.findById(event.getId()).get();
        assertTrue(saved.isPublished());
    }
}
