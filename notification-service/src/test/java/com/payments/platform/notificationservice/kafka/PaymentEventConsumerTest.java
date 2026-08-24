package com.payments.platform.notificationservice.kafka;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

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
    void consumePaymentCompleted_ShouldProcessEventAndAcknowledge_WhenSuccessful() {
        // Arrange
        String topic = "payment.completed";
        int partition = 0;
        long offset = 100L;

        // Act
        paymentEventConsumer.consumePaymentCompleted(
                paymentCompletedEvent, topic, partition, offset, acknowledgment
        );

        // Assert
        verify(notificationService).handlePaymentCompleted(paymentCompletedEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumePaymentCompleted_ShouldNotAcknowledge_WhenProcessingFails() {
        // Arrange
        String topic = "payment.completed";
        int partition = 0;
        long offset = 100L;
        RuntimeException expectedException = new RuntimeException("Processing failed");

        doThrow(expectedException).when(notificationService).handlePaymentCompleted(paymentCompletedEvent);

        // Act & Assert
        assertThatThrownBy(() -> paymentEventConsumer.consumePaymentCompleted(
                paymentCompletedEvent, topic, partition, offset, acknowledgment
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Processing failed");

        verify(notificationService).handlePaymentCompleted(paymentCompletedEvent);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentCompleted_ShouldProcessDifferentPartitionsAndOffsets() {
        // Arrange
        String topic = "payment.completed";
        int partition = 5;
        long offset = 999L;

        // Act
        paymentEventConsumer.consumePaymentCompleted(
                paymentCompletedEvent, topic, partition, offset, acknowledgment
        );

        // Assert
        verify(notificationService).handlePaymentCompleted(paymentCompletedEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumePaymentFailed_ShouldProcessEventAndAcknowledge_WhenSuccessful() {
        // Arrange
        String topic = "payment.failed";
        int partition = 0;
        long offset = 200L;

        // Act
        paymentEventConsumer.consumePaymentFailed(
                paymentFailedEvent, topic, partition, offset, acknowledgment
        );

        // Assert
        verify(notificationService).handlePaymentFailed(paymentFailedEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumePaymentFailed_ShouldNotAcknowledge_WhenProcessingFails() {
        // Arrange
        String topic = "payment.failed";
        int partition = 0;
        long offset = 200L;
        RuntimeException expectedException = new RuntimeException("Database error");

        doThrow(expectedException).when(notificationService).handlePaymentFailed(paymentFailedEvent);

        // Act & Assert
        assertThatThrownBy(() -> paymentEventConsumer.consumePaymentFailed(
                paymentFailedEvent, topic, partition, offset, acknowledgment
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        verify(notificationService).handlePaymentFailed(paymentFailedEvent);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentFailed_ShouldHandleNullPointerException() {
        // Arrange
        String topic = "payment.failed";
        int partition = 0;
        long offset = 300L;
        NullPointerException expectedException = new NullPointerException("Null event data");

        doThrow(expectedException).when(notificationService).handlePaymentFailed(paymentFailedEvent);

        // Act & Assert
        assertThatThrownBy(() -> paymentEventConsumer.consumePaymentFailed(
                paymentFailedEvent, topic, partition, offset, acknowledgment
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null event data");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentCompleted_ShouldHandleServiceException() {
        // Arrange
        String topic = "payment.completed";
        int partition = 0;
        long offset = 400L;
        IllegalStateException expectedException = new IllegalStateException("Invalid state");

        doThrow(expectedException).when(notificationService).handlePaymentCompleted(paymentCompletedEvent);

        // Act & Assert
        assertThatThrownBy(() -> paymentEventConsumer.consumePaymentCompleted(
                paymentCompletedEvent, topic, partition, offset, acknowledgment
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid state");

        verify(acknowledgment, never()).acknowledge();
    }
}
