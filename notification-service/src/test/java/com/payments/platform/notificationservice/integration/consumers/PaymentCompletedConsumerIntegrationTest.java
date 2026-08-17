package com.payments.platform.notificationservice.integration.consumers;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.service.NotificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PaymentCompletedConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @MockBean
    private NotificationSender notificationSender;

    @Test
    void shouldConsumePaymentCompletedEventFromKafka() {
        // Given
        var topic = "payment.completed";
        var event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .payerAccountId(UUID.randomUUID())
                .payeeAccountId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.50))
                .currency("EUR")
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();

        // When
        kafkaTemplate.send(topic, event);

        // Then
        verify(notificationSender, timeout(5000)).sendNotification(event);
    }
}
