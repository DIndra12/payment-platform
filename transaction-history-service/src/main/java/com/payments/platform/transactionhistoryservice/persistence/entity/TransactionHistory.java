package com.payments.platform.transactionhistoryservice.persistence.entity;

import com.payments.platform.transactionhistoryservice.projection.EventType;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Denormalized read model: one row per {@code paymentId}, stitched together from
 * up to four Kafka event streams.
 *
 * <p>Everything sourced from an event is nullable except {@code paymentId} and
 * {@code status}. A row can be created by whichever event arrives first and
 * filled in as the rest land — necessary because the producer sets no Kafka
 * message key, so partitioning is round-robin and ordering is not guaranteed.
 *
 * <p>{@code version} gives optimistic locking: two events for the same payment
 * can be processed concurrently from different partitions, and the loser must
 * fail and retry rather than silently clobber the winner's merge.
 */
@Entity
@Table(name = "transaction_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transaction_history_payment_id", columnNames = {"payment_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistory {

    private static final String EVENTS_SEEN_DELIMITER = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Correlation key across all four topics. */
    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "payer_account_id")
    private UUID payerAccountId;

    @Column(name = "payee_account_id")
    private UUID payeeAccountId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "debited_at")
    private LocalDateTime debitedAt;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "debit_ledger_id")
    private UUID debitLedgerId;

    @Column(name = "credit_ledger_id")
    private UUID creditLedgerId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    /**
     * Comma-separated audit of which event types built this row. Drives status
     * derivation and makes a partially-built row self-describing when debugging.
     */
    @Builder.Default
    @Column(name = "events_seen", nullable = false, length = 255)
    private String eventsSeen = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (eventsSeen == null) {
            eventsSeen = "";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    // events_seen helpers — the CSV encoding is a storage concern, so it is
    // owned here rather than leaking into the projector.
    // ------------------------------------------------------------------

    public Set<String> eventsSeenAsSet() {
        if (eventsSeen == null || eventsSeen.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(eventsSeen.split(EVENTS_SEEN_DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasSeen(EventType type) {
        return eventsSeenAsSet().contains(type.wireName());
    }

    /** Idempotent: recording the same event type twice leaves the set unchanged. */
    public void recordEvent(EventType type) {
        Set<String> seen = eventsSeenAsSet();
        if (seen.add(type.wireName())) {
            eventsSeen = String.join(EVENTS_SEEN_DELIMITER, seen);
        }
    }
}
