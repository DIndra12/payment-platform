package com.payments.platform.transactionhistoryservice.persistence;

import com.payments.platform.transactionhistoryservice.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency ledger access.
 *
 * <p>The dedupe check is on {@code (paymentId, eventType)}, not {@code eventId},
 * because the producer omits {@code eventId} today.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByPaymentIdAndEventType(UUID paymentId, String eventType);

    Optional<ProcessedEvent> findByPaymentIdAndEventType(UUID paymentId, String eventType);

    List<ProcessedEvent> findByPaymentId(UUID paymentId);

    /**
     * Available for when producers start sending {@code eventId} and the dedupe
     * key can move to it.
     */
    boolean existsByEventId(UUID eventId);
}
