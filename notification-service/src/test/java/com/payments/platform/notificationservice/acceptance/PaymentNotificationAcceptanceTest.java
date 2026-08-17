package com.payments.platform.notificationservice.acceptance;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.entity.NotificationType;
import com.payments.platform.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
class PaymentNotificationAcceptanceTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.2"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "notification-service");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @Autowired
    private KafkaTemplate<String, PaymentFailedEvent> kafkaTemplateForFailedEvents;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
    }

    @Test
    void shouldReceivePaymentCompletedEventAndTriggerNotification() {
        // Given
        var topic = "payment.completed";
        var eventId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var event = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(250.75))
                .currency("GBP")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When
        kafkaTemplate.send(topic, event);

        // Then - verify notification was processed and logged
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();
                assertThat(notificationLog.get().getPaymentId()).isEqualTo(paymentId);
                assertThat(notificationLog.get().getStatus().name()).isIn("SENT", "PENDING");
            });
    }

    @Test
    void shouldReceivePaymentFailedEventAndTriggerNotification() {
        // Given
        var topic = "payment.failed";
        var eventId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(150.50))
                .currency("USD")
                .status("FAILED")
                .failureReason("Insufficient funds")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When
        kafkaTemplateForFailedEvents.send(topic, event);

        // Then - verify notification was processed and logged
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();
                assertThat(notificationLog.get().getPaymentId()).isEqualTo(paymentId);
                assertThat(notificationLog.get().getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
                assertThat(notificationLog.get().getMessage()).contains("failed");
                assertThat(notificationLog.get().getMessage()).contains("Insufficient funds");
            });
    }

    @Test
    void shouldHandleDuplicatePaymentCompletedEvents() {
        // Given
        var topic = "payment.completed";
        var eventId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var event = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(500.00))
                .currency("EUR")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When - send the same event twice
        kafkaTemplate.send(topic, event);
        kafkaTemplate.send(topic, event);

        // Then - should only create one notification log
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var allLogs = notificationLogRepository.findAll();
                var logsForEvent = allLogs.stream()
                        .filter(log -> log.getEventId().equals(eventId))
                        .toList();
                assertThat(logsForEvent).hasSize(1);
                assertThat(logsForEvent.get(0).getPaymentId()).isEqualTo(paymentId);
            });
    }

    @Test
    void shouldHandleDuplicatePaymentFailedEvents() {
        // Given
        var topic = "payment.failed";
        var eventId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(300.00))
                .currency("GBP")
                .status("FAILED")
                .failureReason("Card expired")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When - send the same event twice
        kafkaTemplateForFailedEvents.send(topic, event);
        kafkaTemplateForFailedEvents.send(topic, event);

        // Then - should only create one notification log
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var allLogs = notificationLogRepository.findAll();
                var logsForEvent = allLogs.stream()
                        .filter(log -> log.getEventId().equals(eventId))
                        .toList();
                assertThat(logsForEvent).hasSize(1);
            });
    }

    @Test
    void shouldProcessMultiplePaymentCompletedEvents() {
        // Given
        var topic = "payment.completed";
        var eventId1 = UUID.randomUUID();
        var eventId2 = UUID.randomUUID();
        var eventId3 = UUID.randomUUID();

        var event1 = createPaymentCompletedEvent(eventId1, BigDecimal.valueOf(100.00), "USD");
        var event2 = createPaymentCompletedEvent(eventId2, BigDecimal.valueOf(200.00), "EUR");
        var event3 = createPaymentCompletedEvent(eventId3, BigDecimal.valueOf(300.00), "GBP");

        // When
        kafkaTemplate.send(topic, event1);
        kafkaTemplate.send(topic, event2);
        kafkaTemplate.send(topic, event3);

        // Then
        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                assertThat(notificationLogRepository.existsByEventId(eventId1)).isTrue();
                assertThat(notificationLogRepository.existsByEventId(eventId2)).isTrue();
                assertThat(notificationLogRepository.existsByEventId(eventId3)).isTrue();

                var allLogs = notificationLogRepository.findAll();
                assertThat(allLogs).hasSizeGreaterThanOrEqualTo(3);
            });
    }

    @Test
    void shouldProcessMultiplePaymentFailedEvents() {
        // Given
        var topic = "payment.failed";
        var eventId1 = UUID.randomUUID();
        var eventId2 = UUID.randomUUID();

        var event1 = createPaymentFailedEvent(eventId1, "Card declined");
        var event2 = createPaymentFailedEvent(eventId2, "Invalid CVV");

        // When
        kafkaTemplateForFailedEvents.send(topic, event1);
        kafkaTemplateForFailedEvents.send(topic, event2);

        // Then
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                assertThat(notificationLogRepository.existsByEventId(eventId1)).isTrue();
                assertThat(notificationLogRepository.existsByEventId(eventId2)).isTrue();

                var log1 = notificationLogRepository.findByEventId(eventId1).orElseThrow();
                var log2 = notificationLogRepository.findByEventId(eventId2).orElseThrow();

                assertThat(log1.getMessage()).contains("Card declined");
                assertThat(log2.getMessage()).contains("Invalid CVV");
            });
    }

    @Test
    void shouldProcessMixedPaymentEventsInCorrectOrder() {
        // Given
        var completedEventId = UUID.randomUUID();
        var failedEventId = UUID.randomUUID();

        var completedEvent = createPaymentCompletedEvent(completedEventId, BigDecimal.valueOf(750.00), "USD");
        var failedEvent = createPaymentFailedEvent(failedEventId, "Network timeout");

        // When
        kafkaTemplate.send("payment.completed", completedEvent);
        kafkaTemplateForFailedEvents.send("payment.failed", failedEvent);

        // Then
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var completedLog = notificationLogRepository.findByEventId(completedEventId);
                var failedLog = notificationLogRepository.findByEventId(failedEventId);

                assertThat(completedLog).isPresent();
                assertThat(failedLog).isPresent();

                assertThat(completedLog.get().getNotificationType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
                assertThat(failedLog.get().getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);

                assertThat(completedLog.get().getMessage()).contains("successfully completed");
                assertThat(failedLog.get().getMessage()).contains("failed");
                assertThat(failedLog.get().getMessage()).contains("Network timeout");
            });
    }

    @Test
    void shouldHandleLargeAmountPayments() {
        // Given
        var eventId = UUID.randomUUID();
        var event = createPaymentCompletedEvent(eventId, new BigDecimal("999999999.99"), "EUR");

        // When
        kafkaTemplate.send("payment.completed", event);

        // Then
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();
                assertThat(notificationLog.get().getMessage()).contains("999999999.99");
                assertThat(notificationLog.get().getMessage()).contains("EUR");
            });
    }

    @Test
    void shouldHandlePaymentFailedEventWithLongFailureReason() {
        // Given
        var eventId = UUID.randomUUID();
        String longReason = "Transaction failed due to multiple security checks: " +
                "unusual transaction pattern detected, geographical mismatch, " +
                "velocity check exceeded, insufficient authentication";

        var event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(1000.00))
                .currency("USD")
                .status("FAILED")
                .failureReason(longReason)
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When
        kafkaTemplateForFailedEvents.send("payment.failed", event);

        // Then
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();
                assertThat(notificationLog.get().getMessage()).contains(longReason);
            });
    }

    @Test
    void shouldSetCorrectTimestampsOnNotificationLogs() {
        // Given
        var eventId = UUID.randomUUID();
        var event = createPaymentCompletedEvent(eventId, BigDecimal.valueOf(100.00), "USD");
        var beforeSend = LocalDateTime.now().minusSeconds(1);

        // When
        kafkaTemplate.send("payment.completed", event);

        // Then
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();

                var log = notificationLog.get();
                assertThat(log.getCreatedAt()).isNotNull();
                assertThat(log.getUpdatedAt()).isNotNull();
                assertThat(log.getCreatedAt()).isAfter(beforeSend);
                assertThat(log.getUpdatedAt()).isAfter(beforeSend);
            });
    }

    private PaymentCompletedEvent createPaymentCompletedEvent(UUID eventId, BigDecimal amount, String currency) {
        return PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(amount)
                .currency(currency)
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    private PaymentFailedEvent createPaymentFailedEvent(UUID eventId, String failureReason) {
        return PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(200.00))
                .currency("USD")
                .status("FAILED")
                .failureReason(failureReason)
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
