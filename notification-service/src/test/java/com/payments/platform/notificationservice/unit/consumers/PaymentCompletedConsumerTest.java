package com.payments.platform.notificationservice.unit.consumers;

import com.payments.platform.notificationservice.consumers.PaymentCompletedConsumer;
import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.service.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedConsumerTest {

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private PaymentCompletedConsumer paymentCompletedConsumer;

    @Test
    void shouldConsumePaymentCompletedEventAndSendNotification() {
        // Given
        var event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When
        paymentCompletedConsumer.consume(event);

        // Then
        verify(notificationSender).sendNotification(event);
    }
}
