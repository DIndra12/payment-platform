package com.payments.platform.notificationservice.service;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.entity.NotificationLog;
import com.payments.platform.notificationservice.entity.NotificationStatus;
import com.payments.platform.notificationservice.entity.NotificationType;
import com.payments.platform.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private NotificationService notificationService;

    private PaymentCompletedEvent paymentCompletedEvent;
    private PaymentFailedEvent paymentFailedEvent;

    @BeforeEach
    void setUp() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID payerAccountId = UUID.randomUUID();
        UUID payeeAccountId = UUID.randomUUID();

        paymentCompletedEvent = PaymentCompletedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .payerAccountId(payerAccountId)
                .payeeAccountId(payeeAccountId)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId("trace-123")
                .build();

        paymentFailedEvent = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(paymentId)
                .payerAccountId(payerAccountId)
                .payeeAccountId(payeeAccountId)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .status("FAILED")
                .failureReason("Insufficient funds")
                .occurredAt(LocalDateTime.now())
                .traceId("trace-456")
                .build();
    }

    @Test
    void handlePaymentCompleted_ShouldSaveNotification_WhenEventIsNew() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog firstSave = captor.getAllValues().get(0);
        assertThat(firstSave.getEventId()).isEqualTo(paymentCompletedEvent.getEventId());
        assertThat(firstSave.getPaymentId()).isEqualTo(paymentCompletedEvent.getPaymentId());
        assertThat(firstSave.getNotificationType()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
        assertThat(firstSave.getStatus()).isEqualTo(NotificationStatus.PENDING);

        NotificationLog secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(secondSave.getSentAt()).isNotNull();
    }

    @Test
    void handlePaymentCompleted_ShouldSkipProcessing_WhenEventAlreadyExists() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(true);

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void handlePaymentFailed_ShouldSaveNotification_WhenEventIsNew() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentFailedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentFailed(paymentFailedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog firstSave = captor.getAllValues().get(0);
        assertThat(firstSave.getEventId()).isEqualTo(paymentFailedEvent.getEventId());
        assertThat(firstSave.getPaymentId()).isEqualTo(paymentFailedEvent.getPaymentId());
        assertThat(firstSave.getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(firstSave.getMessage()).contains("failed");
        assertThat(firstSave.getMessage()).contains("Insufficient funds");
    }

    @Test
    void handlePaymentFailed_ShouldSkipProcessing_WhenEventAlreadyExists() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentFailedEvent.getEventId())).thenReturn(true);

        // Act
        notificationService.handlePaymentFailed(paymentFailedEvent);

        // Assert
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void handlePaymentCompleted_ShouldSaveFailedNotification_WhenFirstSaveThrowsException() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert - Should still try to save (throws on first save, then saves failed notification)
        verify(notificationLogRepository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    void handlePaymentCompleted_ShouldSaveFailedNotification_WhenExceptionOccursDuringProcessing() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(i -> i.getArgument(0)) // First save succeeds
                .thenThrow(new RuntimeException("Failed to update status")) // Second save fails
                .thenAnswer(i -> i.getArgument(0)); // Failed notification save succeeds

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(3)).save(captor.capture());

        // Verify failed notification was saved
        NotificationLog failedLog = captor.getAllValues().get(2);
        assertThat(failedLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedLog.getErrorMessage()).contains("Failed to update status");
    }

    @Test
    void handlePaymentFailed_ShouldSaveFailedNotification_WhenExceptionOccurs() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentFailedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenThrow(new RuntimeException("Database error"))
                .thenAnswer(i -> i.getArgument(0)); // Failed notification save succeeds

        // Act
        notificationService.handlePaymentFailed(paymentFailedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog failedLog = captor.getAllValues().get(1);
        assertThat(failedLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedLog.getNotificationType()).isEqualTo(NotificationType.PAYMENT_FAILED);
        assertThat(failedLog.getErrorMessage()).contains("Database error");
        assertThat(failedLog.getRecipient()).isEqualTo("unknown");
        assertThat(failedLog.getSubject()).isEqualTo("Failed Notification");
    }

    @Test
    void handlePaymentCompleted_ShouldContainCorrectMessageFormat() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getMessage()).contains(paymentCompletedEvent.getAmount().toString());
        assertThat(savedLog.getMessage()).contains(paymentCompletedEvent.getCurrency());
        assertThat(savedLog.getMessage()).contains(paymentCompletedEvent.getPaymentId().toString());
        assertThat(savedLog.getMessage()).contains("successfully completed");
        assertThat(savedLog.getSubject()).isEqualTo("Payment Completed Successfully");
        assertThat(savedLog.getRecipient()).contains(paymentCompletedEvent.getPayerAccountId().toString());
        assertThat(savedLog.getRecipient()).contains("@example.com");
    }

    @Test
    void handlePaymentFailed_ShouldContainCorrectMessageFormat() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentFailedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentFailed(paymentFailedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getMessage()).contains(paymentFailedEvent.getAmount().toString());
        assertThat(savedLog.getMessage()).contains(paymentFailedEvent.getCurrency());
        assertThat(savedLog.getMessage()).contains(paymentFailedEvent.getPaymentId().toString());
        assertThat(savedLog.getMessage()).contains("has failed");
        assertThat(savedLog.getMessage()).contains(paymentFailedEvent.getFailureReason());
        assertThat(savedLog.getSubject()).isEqualTo("Payment Failed");
        assertThat(savedLog.getRecipient()).contains(paymentFailedEvent.getPayerAccountId().toString());
    }

    @Test
    void handlePaymentCompleted_ShouldSetRetryCountToZero() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentCompletedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentCompleted(paymentCompletedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getRetryCount()).isEqualTo(0);
    }

    @Test
    void handlePaymentFailed_ShouldSetRetryCountToZero() {
        // Arrange
        when(notificationLogRepository.existsByEventId(paymentFailedEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentFailed(paymentFailedEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getRetryCount()).isEqualTo(0);
    }

    @Test
    void handlePaymentCompleted_ShouldHandleLargeAmounts() {
        // Arrange
        PaymentCompletedEvent largeAmountEvent = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("999999999.99"))
                .currency("EUR")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId("trace-large")
                .build();

        when(notificationLogRepository.existsByEventId(largeAmountEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentCompleted(largeAmountEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getMessage()).contains("999999999.99");
        assertThat(savedLog.getMessage()).contains("EUR");
    }

    @Test
    void handlePaymentFailed_ShouldHandleLongFailureReasons() {
        // Arrange
        String longReason = "Transaction declined due to multiple reasons including: " +
                "insufficient funds, invalid account status, exceeded daily limit, " +
                "suspicious activity detected, and pending verification requirements";

        PaymentFailedEvent eventWithLongReason = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("GBP")
                .status("FAILED")
                .failureReason(longReason)
                .occurredAt(LocalDateTime.now())
                .traceId("trace-long")
                .build();

        when(notificationLogRepository.existsByEventId(eventWithLongReason.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentFailed(eventWithLongReason);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getMessage()).contains(longReason);
    }

    @Test
    void handlePaymentCompleted_ShouldHandleDifferentCurrencies() {
        // Arrange
        PaymentCompletedEvent jpyEvent = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(new BigDecimal("10000"))
                .currency("JPY")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId("trace-jpy")
                .build();

        when(notificationLogRepository.existsByEventId(jpyEvent.getEventId())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        notificationService.handlePaymentCompleted(jpyEvent);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());

        NotificationLog savedLog = captor.getAllValues().get(0);
        assertThat(savedLog.getMessage()).contains("JPY");
        assertThat(savedLog.getMessage()).contains("10000");
    }
}
