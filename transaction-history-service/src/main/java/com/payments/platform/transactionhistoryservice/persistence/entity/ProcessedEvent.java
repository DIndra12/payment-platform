package com.payments.platform.transactionhistoryservice.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency ledger. One row per {@code (paymentId, eventType)} that has been
 * successfully projected.
 *
 * <p>Kafka is at-least-once, so the same event can arrive more than once. The
 * projector checks this table before doing any work.
 *
 * <p>Note the uniqueness key is {@code (paymentId, eventType)} and <em>not</em>
 * {@code eventId}: the real payload on the wire carries no {@code eventId}
 * today, so a unique constraint on it would reject the second real event.
 * {@code eventId} is still recorded for forward compatibility.
 */
@Entity
@Table(name = "processed_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_payment_event",
                columnNames = {"payment_id", "event_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nullable — producers do not send it yet. */
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_id")
    private Integer partitionId;

    @Column(name = "offset_value")
    private Long offsetValue;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}
