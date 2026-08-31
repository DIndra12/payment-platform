package com.payments.platform.transactionhistoryservice.projection;

import com.payments.platform.transactionhistoryservice.dto.AccountCreditedEvent;
import com.payments.platform.transactionhistoryservice.dto.AccountDebitedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentCompletedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentFailedEvent;
import com.payments.platform.transactionhistoryservice.exception.InvalidEventException;
import com.payments.platform.transactionhistoryservice.persistence.ProcessedEventRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.persistence.entity.ProcessedEvent;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the {@code transaction_history} read model from event streams.
 *
 * <p>This class is where the correctness of the whole service lives. Its inputs
 * are hostile in three specific ways, and every design choice here is a response
 * to one of them:
 *
 * <ol>
 *   <li><strong>At-least-once delivery.</strong> The same event can arrive twice.
 *       Handled by the {@code processed_event} dedupe guard in step 1.</li>
 *   <li><strong>No ordering guarantee.</strong> payment-service publishes with no
 *       Kafka message key, so records round-robin across partitions and
 *       {@code account.debited} can easily land after {@code payment.completed}.
 *       Handled by merging into whatever row exists (step 3) and recomputing
 *       status from the complete event set (step 4) rather than transitioning a
 *       state machine.</li>
 *   <li><strong>Partial payloads.</strong> The real {@code payment.completed}
 *       carries five fields; {@code eventId}, {@code status}, {@code occurredAt}
 *       and {@code traceId} are absent. Handled by keying dedupe on
 *       {@code (paymentId, eventType)} and by never overwriting a known value
 *       with a null one.</li>
 * </ol>
 *
 * <p>The consequence worth stating plainly: <em>the final row is identical
 * regardless of the order the events arrive in.</em>
 */
@Slf4j
@Service
public class TransactionProjector {

    private final TransactionHistoryRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;

    public TransactionProjector(TransactionHistoryRepository transactionRepository,
                                ProcessedEventRepository processedEventRepository) {
        this.transactionRepository = transactionRepository;
        this.processedEventRepository = processedEventRepository;
    }

    // ------------------------------------------------------------------
    // payment.completed
    // ------------------------------------------------------------------

    @Transactional
    public ProjectionResult onPaymentCompleted(PaymentCompletedEvent event, EventContext context) {
        requireEvent(event, "payment.completed");
        return project(event.getPaymentId(), EventType.PAYMENT_COMPLETED, event.getEventId(), context,
                row -> {
                    row.setPayerAccountId(coalesce(row.getPayerAccountId(), event.getPayerAccountId()));
                    row.setPayeeAccountId(coalesce(row.getPayeeAccountId(), event.getPayeeAccountId()));
                    row.setAmount(coalesce(row.getAmount(), event.getAmount()));
                    row.setCurrency(coalesce(row.getCurrency(), event.getCurrency()));
                    row.setTraceId(coalesce(row.getTraceId(), event.getTraceId()));
                    row.setCompletedAt(coalesce(row.getCompletedAt(), timestampOf(event.getOccurredAt())));
                });
    }

    // ------------------------------------------------------------------
    // payment.failed
    // ------------------------------------------------------------------

    @Transactional
    public ProjectionResult onPaymentFailed(PaymentFailedEvent event, EventContext context) {
        requireEvent(event, "payment.failed");
        return project(event.getPaymentId(), EventType.PAYMENT_FAILED, event.getEventId(), context,
                row -> {
                    row.setPayerAccountId(coalesce(row.getPayerAccountId(), event.getPayerAccountId()));
                    row.setPayeeAccountId(coalesce(row.getPayeeAccountId(), event.getPayeeAccountId()));
                    row.setAmount(coalesce(row.getAmount(), event.getAmount()));
                    row.setCurrency(coalesce(row.getCurrency(), event.getCurrency()));
                    row.setTraceId(coalesce(row.getTraceId(), event.getTraceId()));
                    row.setFailureReason(coalesce(row.getFailureReason(), event.getFailureReason()));
                    row.setCompletedAt(coalesce(row.getCompletedAt(), timestampOf(event.getOccurredAt())));
                });
    }

    // ------------------------------------------------------------------
    // account.debited — the debited account is, by definition, the payer
    // ------------------------------------------------------------------

    @Transactional
    public ProjectionResult onAccountDebited(AccountDebitedEvent event, EventContext context) {
        requireEvent(event, "account.debited");
        return project(event.getReferenceId(), EventType.ACCOUNT_DEBITED, event.getEventId(), context,
                row -> {
                    row.setPayerAccountId(coalesce(row.getPayerAccountId(), event.getAccountId()));
                    row.setAmount(coalesce(row.getAmount(), event.getAmount()));
                    row.setCurrency(coalesce(row.getCurrency(), event.getCurrency()));
                    row.setTraceId(coalesce(row.getTraceId(), event.getTraceId()));
                    row.setDebitLedgerId(coalesce(row.getDebitLedgerId(), event.getLedgerEntryId()));
                    row.setDebitedAt(coalesce(row.getDebitedAt(), timestampOf(event.getOccurredAt())));
                });
    }

    // ------------------------------------------------------------------
    // account.credited — the credited account is, by definition, the payee
    // ------------------------------------------------------------------

    @Transactional
    public ProjectionResult onAccountCredited(AccountCreditedEvent event, EventContext context) {
        requireEvent(event, "account.credited");
        return project(event.getReferenceId(), EventType.ACCOUNT_CREDITED, event.getEventId(), context,
                row -> {
                    row.setPayeeAccountId(coalesce(row.getPayeeAccountId(), event.getAccountId()));
                    row.setAmount(coalesce(row.getAmount(), event.getAmount()));
                    row.setCurrency(coalesce(row.getCurrency(), event.getCurrency()));
                    row.setTraceId(coalesce(row.getTraceId(), event.getTraceId()));
                    row.setCreditLedgerId(coalesce(row.getCreditLedgerId(), event.getLedgerEntryId()));
                    row.setCreditedAt(coalesce(row.getCreditedAt(), timestampOf(event.getOccurredAt())));
                });
    }

    // ------------------------------------------------------------------
    // The single projection path all four event types share
    // ------------------------------------------------------------------

    private ProjectionResult project(UUID paymentId,
                                     EventType eventType,
                                     UUID eventId,
                                     EventContext context,
                                     Consumer<TransactionHistory> merge) {

        // The correlation key is the one field we genuinely cannot work without.
        if (paymentId == null) {
            throw new InvalidEventException(
                    "Event of type " + eventType.wireName() + " has no correlation key "
                            + "(paymentId/referenceId); it can never be attached to a transaction");
        }

        // 1. DEDUPE — Kafka is at-least-once.
        if (processedEventRepository.existsByPaymentIdAndEventType(paymentId, eventType.wireName())) {
            log.info("Event {} for payment {} already projected, skipping",
                    eventType.wireName(), paymentId);
            return ProjectionResult.SKIPPED_DUPLICATE;
        }

        // 2. LOAD OR CREATE — a partial row is a valid row.
        TransactionHistory row = transactionRepository.findByPaymentId(paymentId)
                .orElseGet(() -> newRow(paymentId));

        // 3. MERGE — null-safe, never overwrites a known value with an unknown one.
        merge.accept(row);
        row.recordEvent(eventType);

        // 4. RECOMPUTE STATUS from the full event set, so ordering is irrelevant.
        row.setStatus(deriveStatus(row));

        // 5. Read model row + idempotency marker commit together. If either fails
        //    the whole thing rolls back and Kafka redelivers, which is safe
        //    precisely because of step 1.
        transactionRepository.save(row);
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .eventType(eventType.wireName())
                .topic(context.topic())
                .partitionId(context.partition())
                .offsetValue(context.offset())
                .build());

        log.info("Projected {} for payment {} -> status {} (events seen: {})",
                eventType.wireName(), paymentId, row.getStatus(), row.getEventsSeen());

        return ProjectionResult.APPLIED;
    }

    /**
     * Status is a pure function of which events have been seen. Because it is
     * recomputed from the set rather than transitioned from the previous value,
     * a terminal state can never be downgraded by a late-arriving
     * {@code account.*} event, and a late-arriving terminal event still wins.
     */
    TransactionStatus deriveStatus(TransactionHistory row) {
        if (row.hasSeen(EventType.PAYMENT_COMPLETED)) {
            return TransactionStatus.COMPLETED;
        }
        if (row.hasSeen(EventType.PAYMENT_FAILED)) {
            return TransactionStatus.FAILED;
        }
        // Only account.* events so far: money has moved, outcome not yet known.
        return TransactionStatus.IN_PROGRESS;
    }

    private TransactionHistory newRow(UUID paymentId) {
        log.debug("Creating new read model row for payment {}", paymentId);
        return TransactionHistory.builder()
                .paymentId(paymentId)
                .status(TransactionStatus.IN_PROGRESS)
                .eventsSeen("")
                .build();
    }

    /**
     * Falls back to now() when the producer omits {@code occurredAt}, which it
     * does today.
     *
     * <p>Caveat worth knowing: this makes timestamps non-deterministic across a
     * replay, so a rebuilt read model will show different {@code *_at} values
     * than the original. Phase B (adding {@code occurredAt} to the payload)
     * removes that.
     */
    private LocalDateTime timestampOf(LocalDateTime occurredAt) {
        return occurredAt != null ? occurredAt : LocalDateTime.now();
    }

    private static <T> T coalesce(T existing, T incoming) {
        return existing != null ? existing : incoming;
    }

    private static void requireEvent(Object event, String eventType) {
        if (event == null) {
            throw new InvalidEventException("Received null payload for " + eventType);
        }
    }
}
