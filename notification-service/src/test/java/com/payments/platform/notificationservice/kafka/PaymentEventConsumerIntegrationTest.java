package com.payments.platform.notificationservice.kafka;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.entity.NotificationStatus;
import com.payments.platform.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"payment.completed", "payment.failed"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
class PaymentEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9093");
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> "localhost:9093");
        registry.add("spring.kafka.producer.bootstrap-servers", () -> "localhost:9093");
        registry.add("spring.kafka.consumer.group-id", () -> "notification-service-test");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
        registry.add("spring.kafka.producer.properties.spring.json.trusted.packages", () -> "*");
    }

    @Autowired
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplateCompleted;

    @Autowired
    private KafkaTemplate<String, PaymentFailedEvent> kafkaTemplateFailed;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
    }

    @Test
    void consumePaymentCompleted_ShouldProcessEventAndSaveToDatabase() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // Act
        kafkaTemplateCompleted.send("payment.completed", event);

        // Assert
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var notificationLog = notificationLogRepository.findByEventId(eventId);
                    assertThat(notificationLog).isPresent();
                    assertThat(notificationLog.get().getPaymentId()).isEqualTo(paymentId);
                    assertThat(notificationLog.get().getStatus()).isIn(NotificationStatus.SENT, NotificationStatus.PENDING);
                });
    }

    @Test
    void consumePaymentFailed_ShouldProcessEventAndSaveToDatabase() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("750.00"))
                .currency("EUR")
                .status("FAILED")
                .failureReason("Insufficient funds")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // Act
        kafkaTemplateFailed.send("payment.failed", event);

        // Assert
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var notificationLog = notificationLogRepository.findByEventId(eventId);
                    assertThat(notificationLog).isPresent();
                    assertThat(notificationLog.get().getPaymentId()).isEqualTo(paymentId);
                    assertThat(notificationLog.get().getMessage()).contains("failed");
                    assertThat(notificationLog.get().getMessage()).contains("Insufficient funds");
                });
    }

    @Test
    void consumePaymentCompleted_ShouldHandleDuplicateEvents() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("300.00"))
                .currency("GBP")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // Act - Send the same event twice
        kafkaTemplateCompleted.send("payment.completed", event);
        kafkaTemplateCompleted.send("payment.completed", event);

        // Assert - Should only have one notification log
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var allLogs = notificationLogRepository.findAll();
                    var logsForEvent = allLogs.stream()
                            .filter(log -> log.getEventId().equals(eventId))
                            .toList();
                    assertThat(logsForEvent).hasSize(1);
                });
    }

    @Test
    void consumePaymentFailed_ShouldHandleDuplicateEvents() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status("FAILED")
                .failureReason("Card expired")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // Act - Send the same event twice
        kafkaTemplateFailed.send("payment.failed", event);
        kafkaTemplateFailed.send("payment.failed", event);

        // Assert - Should only have one notification log
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var allLogs = notificationLogRepository.findAll();
                    var logsForEvent = allLogs.stream()
                            .filter(log -> log.getEventId().equals(eventId))
                            .toList();
                    assertThat(logsForEvent).hasSize(1);
                });
    }

    @Test
    void consumePaymentCompleted_ShouldProcessMultipleEventsInSequence() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        UUID eventId3 = UUID.randomUUID();
        
        PaymentCompletedEvent event1 = createPaymentCompletedEvent(eventId1, new BigDecimal("100.00"));
        PaymentCompletedEvent event2 = createPaymentCompletedEvent(eventId2, new BigDecimal("200.00"));
        PaymentCompletedEvent event3 = createPaymentCompletedEvent(eventId3, new BigDecimal("300.00"));

        // Act
        kafkaTemplateCompleted.send("payment.completed", event1);
        kafkaTemplateCompleted.send("payment.completed", event2);
        kafkaTemplateCompleted.send("payment.completed", event3);

        // Assert
        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var allLogs = notificationLogRepository.findAll();
                    assertThat(allLogs).hasSizeGreaterThanOrEqualTo(3);
                    
                    assertThat(notificationLogRepository.existsByEventId(eventId1)).isTrue();
                    assertThat(notificationLogRepository.existsByEventId(eventId2)).isTrue();
                    assertThat(notificationLogRepository.existsByEventId(eventId3)).isTrue();
                });
    }

    @Test
    void consumePaymentFailed_ShouldProcessMultipleEventsInSequence() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        
        PaymentFailedEvent event1 = createPaymentFailedEvent(eventId1, "Card declined");
        PaymentFailedEvent event2 = createPaymentFailedEvent(eventId2, "Invalid CVV");

        // Act
        kafkaTemplateFailed.send("payment.failed", event1);
        kafkaTemplateFailed.send("payment.failed", event2);

        // Assert
        await()
                .atMost(Duration.ofSeconds(15))
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
    void consumePaymentCompleted_ShouldHandleLargeAmounts() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        
        PaymentCompletedEvent event = createPaymentCompletedEvent(eventId, new BigDecimal("999999999.99"));

        // Act
        kafkaTemplateCompleted.send("payment.completed", event);

        // Assert
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var notificationLog = notificationLogRepository.findByEventId(eventId);
                    assertThat(notificationLog).isPresent();
                    assertThat(notificationLog.get().getMessage()).contains("999999999.99");
                });
    }

    @Test
    void consumePaymentFailed_ShouldHandleLongFailureReasons() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        String longReason = "A".repeat(500);
        
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status("FAILED")
                .failureReason(longReason)
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // Act
        kafkaTemplateFailed.send("payment.failed", event);

        // Assert
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var notificationLog = notificationLogRepository.findByEventId(eventId);
                    assertThat(notificationLog).isPresent();
                    assertThat(notificationLog.get().getMessage()).contains(longReason);
                });
    }

    @Test
    void consumePaymentCompleted_ShouldSetCorrectNotificationDetails() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerAccountId = UUID.randomUUID();
        
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(payerAccountId)
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("1500.50"))
                .currency("EUR")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId("trace-" + UUID.randomUUID())
                .build();

        // Act
        kafkaTemplateCompleted.send("payment.completed", event);

        // Assert
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var notificationLog = notificationLogRepository.findByEventId(eventId);
                    assertThat(notificationLog).isPresent();
                    
                    var log = notificationLog.get();
                    assertThat(log.getEventId()).isEqualTo(eventId);
                    assertThat(log.getPaymentId()).isEqualTo(paymentId);
                    assertThat(log.getRecipient()).contains(payerAccountId.toString());
                    assertThat(log.getRecipient()).contains("@example.com");
                    assertThat(log.getSubject()).isEqualTo("Payment Completed Successfully");
                    assertThat(log.getMessage()).contains("1500.50");
                    assertThat(log.getMessage()).contains("EUR");
                    assertThat(log.getRetryCount()).isEqualTo(0);
                    assertThat(log.getCreatedAt()).isNotNull();
                    assertThat(log.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    void consumeMixedEvents_ShouldProcessBothCompletedAndFailedEvents() {
        // Arrange
        UUID completedEventId = UUID.randomUUID();
        UUID failedEventId = UUID.randomUUID();
        
        PaymentCompletedEvent completedEvent = createPaymentCompletedEvent(completedEventId, new BigDecimal("400.00"));
        PaymentFailedEvent failedEvent = createPaymentFailedEvent(failedEventId, "Network timeout");

        // Act
        kafkaTemplateCompleted.send("payment.completed", completedEvent);
        kafkaTemplateFailed.send("payment.failed", failedEvent);

        // Assert
        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    assertThat(notificationLogRepository.existsByEventId(completedEventId)).isTrue();
                    assertThat(notificationLogRepository.existsByEventId(failedEventId)).isTrue();
                    
                    var completedLog = notificationLogRepository.findByEventId(completedEventId).orElseThrow();
                    var failedLog = notificationLogRepository.findByEventId(failedEventId).orElseThrow();
                    
                    assertThat(completedLog.getMessage()).contains("successfully completed");
                    assertThat(failedLog.getMessage()).contains("failed");
                    assertThat(failedLog.getMessage()).contains("Network timeout");
                });
    }

    private PaymentCompletedEvent createPaymentCompletedEvent(UUID eventId, BigDecimal amount) {
        return PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(amount)
                .currency("USD")
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
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status("FAILED")
                .failureReason(failureReason)
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
