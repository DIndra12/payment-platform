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
}
