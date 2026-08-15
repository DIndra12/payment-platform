package com.payments.platform.notificationservice.service;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;

public interface NotificationSender {
    void sendNotification(PaymentCompletedEvent event);
}
