package com.payments.platform.notificationservice.consumers;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentCompletedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCompletedConsumer.class);
    private final NotificationSender notificationSender;

    public PaymentCompletedConsumer(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @KafkaListener(topics = "payment.completed")
    public void consume(PaymentCompletedEvent event) {
        logger.info("Received payment completed event: {}", event);
        notificationSender.sendNotification(event);
    }
}
