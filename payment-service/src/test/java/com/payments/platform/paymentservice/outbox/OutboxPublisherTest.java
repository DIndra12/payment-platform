package com.payments.platform.paymentservice.outbox;

import com.payments.platform.paymentservice.repository.OutboxEventRepository;
import com.payments.platform.paymentservice.service.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(com.payments.platform.paymentservice.TestContainersConfig.class)
class OutboxPublisherTest {

    @Autowired
    private OutboxEventRepository repository;


    @Autowired
    private OutboxPublisher publisher;

    @Test
    void testEventIsPublished() throws Exception {
        // Arrange
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId("123");
        event.setAggregateType("Payment");
        event.setEventType("payment.initiated");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode payload = mapper.readTree("{\"amount\":100}");
        event.setPayload(payload);
        repository.save(event);

        // Act
        publisher.publishEvents();

        // Assert
        OutboxEvent saved = repository.findById(event.getId()).get();
        assertTrue(saved.isPublished());
    }
}
