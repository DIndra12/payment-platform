package com.payments.platform.notificationservice.repository;

import com.payments.platform.notificationservice.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    Optional<NotificationLog> findByEventId(UUID eventId);
    boolean existsByEventId(UUID eventId);
}
