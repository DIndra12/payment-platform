package com.payments.platform.notificationservice.repository;

import com.payments.platform.notificationservice.entity.NotificationLog;
import com.payments.platform.notificationservice.entity.NotificationStatus;
import com.payments.platform.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationLogRepositoryIntegrationTest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
    }

    @Test
    void save_ShouldPersistNotificationLog() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Payment Completed")
                .message("Your payment has been completed successfully")
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();

        // Act
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEventId_ShouldReturnNotificationLog_WhenExists() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = createNotificationLog(eventId, UUID.randomUUID());
        notificationLogRepository.save(notificationLog);

        // Act
        Optional<NotificationLog> found = notificationLogRepository.findByEventId(eventId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo(eventId);
        assertThat(found.get().getRecipient()).isEqualTo("test@example.com");
    }

    @Test
    void findByEventId_ShouldReturnEmpty_WhenNotExists() {
        // Arrange
        UUID nonExistentEventId = UUID.randomUUID();

        // Act
        Optional<NotificationLog> found = notificationLogRepository.findByEventId(nonExistentEventId);

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEventId_ShouldReturnTrue_WhenExists() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = createNotificationLog(eventId, UUID.randomUUID());
        notificationLogRepository.save(notificationLog);

        // Act
        boolean exists = notificationLogRepository.existsByEventId(eventId);

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEventId_ShouldReturnFalse_WhenNotExists() {
        // Arrange
        UUID nonExistentEventId = UUID.randomUUID();

        // Act
        boolean exists = notificationLogRepository.existsByEventId(nonExistentEventId);

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    void save_ShouldEnforceUniqueEventId() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID paymentId1 = UUID.randomUUID();
        UUID paymentId2 = UUID.randomUUID();

        NotificationLog firstLog = createNotificationLog(eventId, paymentId1);
        NotificationLog secondLog = createNotificationLog(eventId, paymentId2);

        notificationLogRepository.save(firstLog);

        // Act & Assert
        assertThatThrownBy(() -> notificationLogRepository.saveAndFlush(secondLog))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAll_ShouldReturnMultipleNotificationLogs() {
        // Arrange
        NotificationLog log1 = createNotificationLog(UUID.randomUUID(), UUID.randomUUID());
        NotificationLog log2 = createNotificationLog(UUID.randomUUID(), UUID.randomUUID());
        NotificationLog log3 = createNotificationLog(UUID.randomUUID(), UUID.randomUUID());

        notificationLogRepository.save(log1);
        notificationLogRepository.save(log2);
        notificationLogRepository.save(log3);

        // Act
        List<NotificationLog> allLogs = notificationLogRepository.findAll();

        // Assert
        assertThat(allLogs).hasSize(3);
    }

    @Test
    void update_ShouldUpdateNotificationStatus() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = createNotificationLog(eventId, UUID.randomUUID());
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Act
        saved.setStatus(NotificationStatus.SENT);
        saved.setSentAt(LocalDateTime.now());
        NotificationLog updated = notificationLogRepository.save(saved);

        // Assert
        NotificationLog found = notificationLogRepository.findByEventId(eventId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.getSentAt()).isNotNull();
    }

    @Test
    void save_ShouldHandleFailedStatus() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_FAILED)
                .recipient("test@example.com")
                .subject("Payment Failed")
                .message("Payment processing failed")
                .status(NotificationStatus.FAILED)
                .errorMessage("Database connection timeout")
                .retryCount(3)
                .build();

        // Act
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Assert
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("Database connection timeout");
        assertThat(saved.getRetryCount()).isEqualTo(3);
    }

    @Test
    void save_ShouldHandleLongMessages() {
        // Arrange
        String longMessage = "This is a very long message. ".repeat(100);
        UUID eventId = UUID.randomUUID();

        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message(longMessage)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();

        // Act
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Assert
        NotificationLog found = notificationLogRepository.findByEventId(eventId).orElseThrow();
        assertThat(found.getMessage()).isEqualTo(longMessage);
        assertThat(found.getMessage().length()).isGreaterThan(1000);
    }

    @Test
    void findByEventId_ShouldHandleMultipleNotificationsForSamePayment() {
        // Arrange
        UUID paymentId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        NotificationLog log1 = createNotificationLog(eventId1, paymentId);
        NotificationLog log2 = createNotificationLog(eventId2, paymentId);

        notificationLogRepository.save(log1);
        notificationLogRepository.save(log2);

        // Act
        Optional<NotificationLog> found1 = notificationLogRepository.findByEventId(eventId1);
        Optional<NotificationLog> found2 = notificationLogRepository.findByEventId(eventId2);

        // Assert
        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getPaymentId()).isEqualTo(paymentId);
        assertThat(found2.get().getPaymentId()).isEqualTo(paymentId);
        assertThat(found1.get().getId()).isNotEqualTo(found2.get().getId());
    }

    @Test
    void deleteById_ShouldRemoveNotificationLog() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = createNotificationLog(eventId, UUID.randomUUID());
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Act
        notificationLogRepository.deleteById(saved.getId());

        // Assert
        Optional<NotificationLog> found = notificationLogRepository.findById(saved.getId());
        assertThat(found).isEmpty();
        assertThat(notificationLogRepository.existsByEventId(eventId)).isFalse();
    }

    @Test
    void save_ShouldHandleDifferentNotificationTypes() {
        // Arrange
        NotificationLog completedLog = createNotificationLog(UUID.randomUUID(), UUID.randomUUID());
        completedLog.setNotificationType(NotificationType.PAYMENT_COMPLETED);

        NotificationLog failedLog = createNotificationLog(UUID.randomUUID(), UUID.randomUUID());
        failedLog.setNotificationType(NotificationType.PAYMENT_FAILED);

        // Act
        NotificationLog savedCompleted = notificationLogRepository.save(completedLog);
        NotificationLog savedFailed = notificationLogRepository.save(failedLog);

        // Assert
        assertThat(savedCompleted.getNotificationType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
        assertThat(savedFailed.getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
    }

    @Test
    void save_ShouldHandleNullErrorMessage() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test message")
                .status(NotificationStatus.SENT)
                .errorMessage(null)
                .retryCount(0)
                .build();

        // Act
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Assert
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void save_ShouldHandleNullSentAt() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test message")
                .status(NotificationStatus.PENDING)
                .sentAt(null)
                .retryCount(0)
                .build();

        // Act
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Assert
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void update_ShouldIncrementRetryCount() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        NotificationLog notificationLog = createNotificationLog(eventId, UUID.randomUUID());
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        // Act
        saved.setRetryCount(saved.getRetryCount() + 1);
        saved.setStatus(NotificationStatus.FAILED);
        saved.setErrorMessage("First retry failed");
        notificationLogRepository.save(saved);

        saved.setRetryCount(saved.getRetryCount() + 1);
        saved.setErrorMessage("Second retry failed");
        NotificationLog updated = notificationLogRepository.save(saved);

        // Assert
        NotificationLog found = notificationLogRepository.findByEventId(eventId).orElseThrow();
        assertThat(found.getRetryCount()).isEqualTo(2);
        assertThat(found.getErrorMessage()).isEqualTo("Second retry failed");
    }

    private NotificationLog createNotificationLog(UUID eventId, UUID paymentId) {
        return NotificationLog.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
