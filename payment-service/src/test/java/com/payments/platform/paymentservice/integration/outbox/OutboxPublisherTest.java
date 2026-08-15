package com.payments.platform.paymentservice.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.outbox.OutboxEventRepository;
import com.payments.platform.paymentservice.outbox.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OutboxPublisherTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.2"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    private org.apache.kafka.clients.consumer.Consumer<String, String> consumer;

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp(@Autowired ConsumerFactory<String, String> consumerFactory) {
        consumer = consumerFactory.createConsumer("outbox-test-group", "test");
        consumer.subscribe(java.util.Collections.singletonList("payment.initiated"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void shouldPublishEventFromOutboxToKafka() throws Exception {
        // Given
        JsonNode payload = objectMapper.readTree("{\"amount\":100}");
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId("123");
        event.setAggregateType("Payment");
        event.setEventType("payment.initiated");
        event.setPayload(payload);
        repository.save(event);

        // When
        publisher.publishEvents();

        // Then
        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isEqualTo(1);
        var receivedEvent = records.iterator().next().value();
        JsonNode receivedPayload = objectMapper.readTree(receivedEvent);
        assertThat(receivedPayload.get("aggregateId").asText()).isEqualTo("123");
        assertThat(receivedPayload.get("eventType").asText()).isEqualTo("payment.initiated");
        assertThat(receivedPayload.get("payload").get("amount").asInt()).isEqualTo(100);

        // Verify the event is marked as published in the database
        OutboxEvent publishedEvent = repository.findById(event.getId()).get();
        assertThat(publishedEvent.isPublished()).isTrue();
    }
}
