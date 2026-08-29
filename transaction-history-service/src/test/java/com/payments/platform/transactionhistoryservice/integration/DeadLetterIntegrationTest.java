package com.payments.platform.transactionhistoryservice.integration;

import com.payments.platform.transactionhistoryservice.persistence.ProcessedEventRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.support.EventFixtures;
import com.payments.platform.transactionhistoryservice.support.KafkaPostgresTestBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the dead-letter path — the platform's first, since nothing else wires
 * a {@code DefaultErrorHandler}.
 *
 * <p>The important behaviour is not just "bad record ends up on the DLT" but
 * "bad record does not wedge the partition": a poison pill must not stop the
 * events behind it from being projected.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeadLetterIntegrationTest extends KafkaPostgresTestBase {

    private static final String TOPIC = "payment.completed";
    private static final String DLT_TOPIC = "payment.completed.DLT";

    @Autowired
    private TransactionHistoryRepository transactionRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        processedEventRepository.deleteAll();

        dltConsumer = new KafkaConsumer<>(consumerProps("dlt-assertions-" + UUID.randomUUID()));
        dltConsumer.subscribe(List.of(DLT_TOPIC));
        dltConsumer.poll(Duration.ofSeconds(1)); // force assignment
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    @DisplayName("malformed JSON is dead-lettered without blocking later records")
    void shouldDeadLetterPoisonPillAndKeepConsuming() {
        UUID goodPayment = UUID.randomUUID();

        // A record that cannot possibly deserialize into the event type.
        publishRaw(TOPIC, "poison", "{ this is not valid json ");

        // A perfectly good record queued behind it.
        publishRaw(TOPIC, goodPayment.toString(), EventFixtures.paymentCompleted(
                goodPayment, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("120.0000"), "INR"));

        // The partition kept moving: the good record was projected.
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(goodPayment).isPresent());

        // And the poison pill was routed to the dead-letter topic.
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofSeconds(2));
            assertThat(records.count()).isPositive();
        });
    }

    @Test
    @DisplayName("an event with no correlation key is dead-lettered rather than retried forever")
    void shouldDeadLetterEventWithoutCorrelationKey() {
        // Structurally valid JSON, but no paymentId: it can never be attached to a
        // transaction, so retrying is pointless. InvalidEventException is registered
        // as non-retryable for exactly this case.
        publishRaw(TOPIC, "no-key",
                "{\"payerAccountId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":10.0000}");

        UUID goodPayment = UUID.randomUUID();
        publishRaw(TOPIC, goodPayment.toString(), EventFixtures.paymentCompleted(
                goodPayment, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("80.0000"), "INR"));

        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(goodPayment).isPresent());

        // No row was created for the unusable event.
        assertThat(transactionRepository.count()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofSeconds(2));
            assertThat(records.count()).isPositive();
        });
    }
}
