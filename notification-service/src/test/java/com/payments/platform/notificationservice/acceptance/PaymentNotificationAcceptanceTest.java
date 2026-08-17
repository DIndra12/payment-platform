package com.payments.platform.notificationservice.acceptance;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.repository.NotificationLogRepository;
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
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

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
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                var notificationLog = notificationLogRepository.findByEventId(eventId);
                assertThat(notificationLog).isPresent();
                assertThat(notificationLog.get().getPaymentId()).isEqualTo(paymentId);
                assertThat(notificationLog.get().getStatus().name()).isIn("SENT", "PENDING");
            });
    }
}
