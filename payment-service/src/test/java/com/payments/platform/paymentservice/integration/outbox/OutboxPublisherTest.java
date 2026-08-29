package com.payments.platform.paymentservice.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.paymentservice.outbox.OutboxEvent;
import com.payments.platform.paymentservice.outbox.OutboxEventRepository;
import com.payments.platform.paymentservice.outbox.OutboxPublisher;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies what the outbox actually puts on the wire.
 *
 * <p>Two properties matter to every consumer downstream:
 * <ul>
 *   <li>The message <strong>value is the raw payload JSON</strong> — not the outbox
 *       row wrapped in an envelope, and not a double-encoded JSON string. The
 *       producer's value serializer must stay {@code StringSerializer}, because
 *       {@code OutboxSenderKafkaAdapter} hands over an already-serialized string;
 *       a {@code JsonSerializer} would re-encode it into a quoted literal that no
 *       consumer can bind.</li>
 *   <li>The message <strong>key is the aggregate id</strong>, which is what keeps
 *       all events for one payment on the same partition and therefore ordered.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OutboxPublisherTest {

    private static final String TOPIC = "payment.initiated";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // A plain String consumer rather than the application's ConsumerFactory: the
        // point of this test is to inspect the exact bytes on the topic, not to
        // re-apply the app's own deserialization assumptions.
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));
        consumer.poll(Duration.ofSeconds(1)); // force partition assignment
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("publishes the raw payload keyed by aggregate id, and marks the row published")
    void shouldPublishEventFromOutboxToKafka() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"amount\":100}");
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId("123");
        event.setAggregateType("Payment");
        event.setEventType(TOPIC);
        event.setPayload(payload);
        repository.save(event);

        publisher.publishEvents();

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20));
        assertThat(records.count()).isEqualTo(1);

        ConsumerRecord<String, String> record = records.iterator().next();

        // Key = aggregate id, so per-payment ordering is preserved.
        assertThat(record.key()).isEqualTo("123");

        // Value = the payload itself, parseable as a JSON object.
        JsonNode receivedPayload = objectMapper.readTree(record.value());
        assertThat(receivedPayload.isObject()).isTrue();
        assertThat(receivedPayload.get("amount").asInt()).isEqualTo(100);

        OutboxEvent publishedEvent = repository.findById(event.getId()).orElseThrow();
        assertThat(publishedEvent.isPublished()).isTrue();
    }
}
