package com.payments.platform.notificationservice.service;

import com.payments.platform.notificationservice.dto.PaymentCompletedEvent;
import com.payments.platform.notificationservice.dto.PaymentFailedEvent;
import com.payments.platform.notificationservice.entity.NotificationLog;
import com.payments.platform.notificationservice.entity.NotificationStatus;
import com.payments.platform.notificationservice.entity.NotificationType;
import com.payments.platform.notificationservice.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // Dedupe check - if event already processed, skip
        if (notificationLogRepository.existsByEventId(event.getEventId())) {
            log.info("Event {} already processed, skipping", event.getEventId());
            return;
        }

        log.info("Processing payment completed event for payment: {}", event.getPaymentId());

        try {
            // Build notification message
            String message = String.format(
                "Your payment of %s %s has been successfully completed. Payment ID: %s",
                event.getAmount(), event.getCurrency(), event.getPaymentId()
            );

            String subject = "Payment Completed Successfully";
            String recipient = event.getPayerAccountId().toString() + "@example.com"; // Mock recipient

            // Save notification log
            NotificationLog notificationLog = NotificationLog.builder()
                    .eventId(event.getEventId())
                    .paymentId(event.getPaymentId())
                    .notificationType(NotificationType.PAYMENT_COMPLETED)
                    .recipient(recipient)
                    .subject(subject)
                    .message(message)
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .build();

            notificationLogRepository.save(notificationLog);

            // Send notification (mock implementation)
            sendNotification(notificationLog);

            // Update status to SENT
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(LocalDateTime.now());
            notificationLogRepository.save(notificationLog);

            log.info("Successfully sent notification for payment: {}", event.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to process payment completed event for payment: {}", event.getPaymentId(), e);
            // Save failed notification log
            saveFailedNotification(event.getEventId(), event.getPaymentId(), 
                    NotificationType.PAYMENT_COMPLETED, e.getMessage());
        }
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // Dedupe check
        if (notificationLogRepository.existsByEventId(event.getEventId())) {
            log.info("Event {} already processed, skipping", event.getEventId());
            return;
        }

        log.info("Processing payment failed event for payment: {}", event.getPaymentId());

        try {
            // Build notification message
            String message = String.format(
                "Your payment of %s %s has failed. Reason: %s. Payment ID: %s",
                event.getAmount(), event.getCurrency(), 
                event.getFailureReason(), event.getPaymentId()
            );

            String subject = "Payment Failed";
            String recipient = event.getPayerAccountId().toString() + "@example.com"; // Mock recipient

            // Save notification log
            NotificationLog notificationLog = NotificationLog.builder()
                    .eventId(event.getEventId())
                    .paymentId(event.getPaymentId())
                    .notificationType(NotificationType.PAYMENT_FAILED)
                    .recipient(recipient)
                    .subject(subject)
                    .message(message)
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .build();

            notificationLogRepository.save(notificationLog);

            // Send notification (mock implementation)
            sendNotification(notificationLog);

            // Update status to SENT
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(LocalDateTime.now());
            notificationLogRepository.save(notificationLog);

            log.info("Successfully sent notification for failed payment: {}", event.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to process payment failed event for payment: {}", event.getPaymentId(), e);
            // Save failed notification log
            saveFailedNotification(event.getEventId(), event.getPaymentId(), 
                    NotificationType.PAYMENT_FAILED, e.getMessage());
        }
    }

    private void sendNotification(NotificationLog notificationLog) {
        // Mock implementation - in production, integrate with email/SMS service
        log.info("Sending notification to: {}", notificationLog.getRecipient());
        log.info("Subject: {}", notificationLog.getSubject());
        log.info("Message: {}", notificationLog.getMessage());
        
        // Simulate network call
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveFailedNotification(UUID eventId, UUID paymentId, 
                                       NotificationType type, String errorMessage) {
        NotificationLog failedLog = NotificationLog.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .notificationType(type)
                .recipient("unknown")
                .subject("Failed Notification")
                .message("Failed to send notification")
                .status(NotificationStatus.FAILED)
                .errorMessage(errorMessage)
                .retryCount(0)
                .build();
        
        notificationLogRepository.save(failedLog);
    }
}
