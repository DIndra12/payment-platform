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
import java.time.Instant;
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
        var event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.TEN,
                "USD",
                Instant.now()
        );

        // When
        paymentCompletedConsumer.consume(event);

        // Then
        verify(notificationSender).sendNotification(event);
    }
}
