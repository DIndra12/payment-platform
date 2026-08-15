package com.payments.platform.notificationservice.service;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockNotificationSender implements NotificationSender {

    private static final Logger logger = LoggerFactory.getLogger(MockNotificationSender.class);

    @Override
    public void sendNotification(PaymentCompletedEvent event) {
        // Simulate sending a notification
        logger.info("Sending notification for payment {}: Payer {}, Payee {}, Amount {} {}",
                event.paymentId(),
                event.payerAccountId(),
                event.payeeAccountId(),
                event.amount(),
                event.currency());
    }
}
