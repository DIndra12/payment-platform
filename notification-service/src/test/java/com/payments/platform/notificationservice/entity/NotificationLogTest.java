package com.payments.platform.notificationservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLogTest {

    @Test
    void onCreate_ShouldSetCreatedAtAndUpdatedAt() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .build();

        LocalDateTime beforeCreate = LocalDateTime.now().minusSeconds(1);

        // Act
        notificationLog.onCreate();

        // Assert
        LocalDateTime afterCreate = LocalDateTime.now().plusSeconds(1);
        assertThat(notificationLog.getCreatedAt()).isNotNull();
        assertThat(notificationLog.getUpdatedAt()).isNotNull();
        assertThat(notificationLog.getCreatedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(notificationLog.getUpdatedAt()).isBetween(beforeCreate, afterCreate);
        // createdAt and updatedAt should be the same (or within nanoseconds due to execution)
        // We just verify both are set and in the same time range
        assertThat(notificationLog.getCreatedAt()).isEqualToIgnoringNanos(notificationLog.getUpdatedAt());
    }

    @Test
    void onCreate_ShouldSetRetryCountToZero_WhenNull() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_FAILED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .retryCount(null) // Explicitly set to null
                .build();

        // Act
        notificationLog.onCreate();

        // Assert
        assertThat(notificationLog.getRetryCount()).isEqualTo(0);
    }

    @Test
    void onCreate_ShouldNotOverrideRetryCount_WhenNotNull() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .retryCount(5)
                .build();

        // Act
        notificationLog.onCreate();

        // Assert
        assertThat(notificationLog.getRetryCount()).isEqualTo(5);
    }

    @Test
    void onUpdate_ShouldUpdateUpdatedAt() throws InterruptedException {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .build();

        notificationLog.onCreate();
        LocalDateTime originalCreatedAt = notificationLog.getCreatedAt();
        LocalDateTime originalUpdatedAt = notificationLog.getUpdatedAt();

        // Wait a bit to ensure time difference
        Thread.sleep(10);

        // Act
        notificationLog.onUpdate();

        // Assert
        assertThat(notificationLog.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(notificationLog.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void onUpdate_ShouldNotAffectCreatedAt() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_FAILED)
                .recipient("test@example.com")
                .subject("Test Subject")
                .message("Test Message")
                .status(NotificationStatus.PENDING)
                .build();

        notificationLog.onCreate();
        LocalDateTime originalCreatedAt = notificationLog.getCreatedAt();

        // Act
        notificationLog.onUpdate();
        notificationLog.onUpdate();
        notificationLog.onUpdate();

        // Assert
        assertThat(notificationLog.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void builder_ShouldCreateNotificationLogWithAllFields() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        LocalDateTime sentAt = LocalDateTime.now();
        LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
        LocalDateTime updatedAt = LocalDateTime.now();

        // Act
        NotificationLog notificationLog = NotificationLog.builder()
                .id(id)
                .eventId(eventId)
                .paymentId(paymentId)
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("recipient@example.com")
                .subject("Payment Completed")
                .message("Your payment has been completed")
                .status(NotificationStatus.SENT)
                .sentAt(sentAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .errorMessage(null)
                .retryCount(3)
                .build();

        // Assert
        assertThat(notificationLog.getId()).isEqualTo(id);
        assertThat(notificationLog.getEventId()).isEqualTo(eventId);
        assertThat(notificationLog.getPaymentId()).isEqualTo(paymentId);
        assertThat(notificationLog.getNotificationType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
        assertThat(notificationLog.getRecipient()).isEqualTo("recipient@example.com");
        assertThat(notificationLog.getSubject()).isEqualTo("Payment Completed");
        assertThat(notificationLog.getMessage()).isEqualTo("Your payment has been completed");
        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationLog.getSentAt()).isEqualTo(sentAt);
        assertThat(notificationLog.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notificationLog.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(notificationLog.getErrorMessage()).isNull();
        assertThat(notificationLog.getRetryCount()).isEqualTo(3);
    }

    @Test
    void builder_ShouldCreateNotificationLogWithErrorMessage() {
        // Arrange & Act
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_FAILED)
                .recipient("test@example.com")
                .subject("Payment Failed")
                .message("Payment could not be processed")
                .status(NotificationStatus.FAILED)
                .errorMessage("Database connection timeout")
                .retryCount(2)
                .build();

        // Assert
        assertThat(notificationLog.getErrorMessage()).isEqualTo("Database connection timeout");
        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void setters_ShouldUpdateFields() {
        // Arrange
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("original@example.com")
                .subject("Original Subject")
                .message("Original Message")
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();

        LocalDateTime newSentAt = LocalDateTime.now();

        // Act
        notificationLog.setStatus(NotificationStatus.SENT);
        notificationLog.setSentAt(newSentAt);
        notificationLog.setRetryCount(1);
        notificationLog.setErrorMessage("Some error");

        // Assert
        assertThat(notificationLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationLog.getSentAt()).isEqualTo(newSentAt);
        assertThat(notificationLog.getRetryCount()).isEqualTo(1);
        assertThat(notificationLog.getErrorMessage()).isEqualTo("Some error");
    }

    @Test
    void builder_ShouldSetDefaultRetryCountToZero_WhenUsingDefault() {
        // Arrange & Act
        NotificationLog notificationLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test message")
                .status(NotificationStatus.PENDING)
                .build();

        // Assert - builder default should be 0
        assertThat(notificationLog.getRetryCount()).isEqualTo(0);
    }

    @Test
    void onCreate_ShouldHandleMultipleNotificationTypes() {
        // Arrange
        NotificationLog completedLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test1@example.com")
                .subject("Test")
                .message("Test")
                .status(NotificationStatus.PENDING)
                .build();

        NotificationLog failedLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_FAILED)
                .recipient("test2@example.com")
                .subject("Test")
                .message("Test")
                .status(NotificationStatus.PENDING)
                .build();

        // Act
        completedLog.onCreate();
        failedLog.onCreate();

        // Assert
        assertThat(completedLog.getCreatedAt()).isNotNull();
        assertThat(failedLog.getCreatedAt()).isNotNull();
        assertThat(completedLog.getNotificationType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
        assertThat(failedLog.getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
    }

    @Test
    void onCreate_ShouldHandleMultipleStatuses() {
        // Arrange
        NotificationLog pendingLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test")
                .status(NotificationStatus.PENDING)
                .build();

        NotificationLog sentLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test")
                .status(NotificationStatus.SENT)
                .build();

        NotificationLog failedLog = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_COMPLETED)
                .recipient("test@example.com")
                .subject("Test")
                .message("Test")
                .status(NotificationStatus.FAILED)
                .build();

        // Act
        pendingLog.onCreate();
        sentLog.onCreate();
        failedLog.onCreate();

        // Assert
        assertThat(pendingLog.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(sentLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(failedLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(pendingLog.getCreatedAt()).isNotNull();
        assertThat(sentLog.getCreatedAt()).isNotNull();
        assertThat(failedLog.getCreatedAt()).isNotNull();
    }
}
