package com.payments.platform.transactionhistoryservice.unit.projection;

import com.payments.platform.transactionhistoryservice.dto.AccountCreditedEvent;
import com.payments.platform.transactionhistoryservice.dto.AccountDebitedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentCompletedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentFailedEvent;
import com.payments.platform.transactionhistoryservice.exception.InvalidEventException;
import com.payments.platform.transactionhistoryservice.persistence.ProcessedEventRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.persistence.entity.ProcessedEvent;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.EventContext;
import com.payments.platform.transactionhistoryservice.projection.ProjectionResult;
import com.payments.platform.transactionhistoryservice.projection.TransactionProjector;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The projector is the one component whose correctness the whole service depends
 * on, and it needs no infrastructure to test — so this is where the ordering,
 * duplication and partial-arrival guarantees are actually pinned down.
 *
 * <p>The repositories are backed by in-memory maps rather than strict mocks
 * because the behaviour under test is stateful: "the second event must merge into
 * the row the first event created" is meaningless against a mock that always
 * returns empty.
 */
class TransactionProjectorTest {

    private static final UUID PAYMENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DEBIT_LEDGER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID CREDIT_LEDGER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final BigDecimal AMOUNT = new BigDecimal("500.5000");
    private static final String CURRENCY = "INR";

    private Map<UUID, TransactionHistory> rows;
    private Set<String> processedKeys;
    private TransactionProjector projector;

    @BeforeEach
    void setUp() {
        rows = new HashMap<>();
        processedKeys = new HashSet<>();

        TransactionHistoryRepository transactionRepository = mock(TransactionHistoryRepository.class);
        ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);

        when(transactionRepository.findByPaymentId(any()))
                .thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0, UUID.class))));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            TransactionHistory row = inv.getArgument(0);
            rows.put(row.getPaymentId(), row);
            return row;
        });

        when(processedEventRepository.existsByPaymentIdAndEventType(any(), any()))
                .thenAnswer(inv -> processedKeys.contains(
                        key(inv.getArgument(0, UUID.class), inv.getArgument(1, String.class))));
        when(processedEventRepository.save(any())).thenAnswer(inv -> {
            ProcessedEvent event = inv.getArgument(0);
            processedKeys.add(key(event.getPaymentId(), event.getEventType()));
            return event;
        });

        projector = new TransactionProjector(transactionRepository, processedEventRepository);
    }

    private static String key(UUID paymentId, String eventType) {
        return paymentId + "|" + eventType;
    }

    // ------------------------------------------------------------------
    // Ordering independence — the headline guarantee
    // ------------------------------------------------------------------

    /**
     * Every arrival order of the three events must produce an identical final row.
     * Because the producer sets no Kafka message key, records round-robin across
     * partitions and any of these orders can happen in practice.
     */
    @ParameterizedTest(name = "arrival order {0} still yields a fully stitched COMPLETED row")
    @MethodSource("allArrivalOrders")
    @DisplayName("final row is identical regardless of event arrival order")
    void shouldProduceSameRowForEveryArrivalOrder(List<String> order) {
        order.forEach(this::applyEvent);

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(row.getPayerAccountId()).isEqualTo(PAYER);
        assertThat(row.getPayeeAccountId()).isEqualTo(PAYEE);
        assertThat(row.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(row.getCurrency()).isEqualTo(CURRENCY);
        assertThat(row.getDebitLedgerId()).isEqualTo(DEBIT_LEDGER_ID);
        assertThat(row.getCreditLedgerId()).isEqualTo(CREDIT_LEDGER_ID);
        assertThat(row.getDebitedAt()).isNotNull();
        assertThat(row.getCreditedAt()).isNotNull();
        assertThat(row.getCompletedAt()).isNotNull();
        assertThat(row.eventsSeenAsSet())
                .containsExactlyInAnyOrder("payment.completed", "account.debited", "account.credited");
    }

    private static Stream<List<String>> allArrivalOrders() {
        return permutations(List.of("completed", "debited", "credited")).stream();
    }

    private static <T> List<List<T>> permutations(List<T> input) {
        if (input.size() <= 1) {
            return new ArrayList<>(List.of(new ArrayList<>(input)));
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            List<T> remaining = new ArrayList<>(input);
            T head = remaining.remove(i);
            for (List<T> tail : permutations(remaining)) {
                List<T> permutation = new ArrayList<>();
                permutation.add(head);
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("redelivery of the same event is skipped, not applied twice")
    void shouldSkipDuplicateEvent() {
        assertThat(projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 0L)))
                .isEqualTo(ProjectionResult.APPLIED);

        assertThat(projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 1L)))
                .isEqualTo(ProjectionResult.SKIPPED_DUPLICATE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(PAYMENT_ID).eventsSeenAsSet()).containsExactly("payment.completed");
    }

    @Test
    @DisplayName("dedupe works even though the real payload carries no eventId")
    void shouldDedupeWithoutEventId() {
        PaymentCompletedEvent withoutEventId = paymentCompleted();
        withoutEventId.setEventId(null);

        projector.onPaymentCompleted(withoutEventId, context("payment.completed", 0L));
        ProjectionResult second = projector.onPaymentCompleted(withoutEventId, context("payment.completed", 1L));

        assertThat(second).isEqualTo(ProjectionResult.SKIPPED_DUPLICATE);
    }

    // ------------------------------------------------------------------
    // Status derivation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("account events alone leave the transaction IN_PROGRESS")
    void shouldBeInProgressWithOnlyAccountEvents() {
        projector.onAccountDebited(accountDebited(), context("account.debited", 0L));

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.IN_PROGRESS);
        assertThat(row.getCompletedAt()).isNull();
        // The debited account is the payer, so that leg is now known.
        assertThat(row.getPayerAccountId()).isEqualTo(PAYER);
        assertThat(row.getPayeeAccountId()).isNull();
    }

    @Test
    @DisplayName("a late account event never downgrades a terminal status")
    void shouldNotDowngradeTerminalStatus() {
        projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 0L));
        assertThat(rows.get(PAYMENT_ID).getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        projector.onAccountDebited(accountDebited(), context("account.debited", 1L));

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        // ...but the late event still contributes the data only it carries.
        assertThat(row.getDebitLedgerId()).isEqualTo(DEBIT_LEDGER_ID);
    }

    @Test
    @DisplayName("payment.failed records FAILED with its reason")
    void shouldRecordFailure() {
        projector.onPaymentFailed(paymentFailed("Insufficient funds"), context("payment.failed", 0L));

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(row.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("COMPLETED wins over FAILED regardless of which arrives first")
    void shouldPreferCompletedOverFailed() {
        projector.onPaymentFailed(paymentFailed("transient error"), context("payment.failed", 0L));
        projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 1L));

        assertThat(rows.get(PAYMENT_ID).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    // ------------------------------------------------------------------
    // Null-safe merging
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a later event never overwrites a known value with null")
    void shouldNotOverwriteKnownValuesWithNulls() {
        projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 0L));

        // account.debited carries no currency at all (account-service has none to send)
        AccountDebitedEvent debited = accountDebited();
        debited.setCurrency(null);
        debited.setAmount(null);
        projector.onAccountDebited(debited, context("account.debited", 1L));

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row.getCurrency()).isEqualTo(CURRENCY);
        assertThat(row.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("the five-field payload the producer actually sends projects cleanly")
    void shouldProjectMinimalRealWorldPayload() {
        // Exactly what is on the wire today: no eventId, status, occurredAt or traceId.
        PaymentCompletedEvent minimal = PaymentCompletedEvent.builder()
                .paymentId(PAYMENT_ID)
                .payerAccountId(PAYER)
                .payeeAccountId(PAYEE)
                .amount(AMOUNT)
                .currency(CURRENCY)
                .build();

        assertThat(projector.onPaymentCompleted(minimal, context("payment.completed", 0L)))
                .isEqualTo(ProjectionResult.APPLIED);

        TransactionHistory row = rows.get(PAYMENT_ID);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(row.getTraceId()).isNull();
        // occurredAt was absent, so completedAt falls back to now()
        assertThat(row.getCompletedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Unusable events
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an event with no correlation key is rejected as non-retryable")
    void shouldRejectEventWithoutCorrelationKey() {
        PaymentCompletedEvent noPaymentId = paymentCompleted();
        noPaymentId.setPaymentId(null);

        assertThatThrownBy(() -> projector.onPaymentCompleted(noPaymentId, context("payment.completed", 0L)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("no correlation key");

        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("account events correlate on referenceId, which is the paymentId")
    void shouldRejectAccountEventWithoutReferenceId() {
        AccountDebitedEvent noReference = accountDebited();
        noReference.setReferenceId(null);

        assertThatThrownBy(() -> projector.onAccountDebited(noReference, context("account.debited", 0L)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    @DisplayName("a null payload is rejected rather than NPE-ing")
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> projector.onPaymentCompleted(null, context("payment.completed", 0L)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("null payload");
    }

    // ------------------------------------------------------------------
    // Independence between payments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("events for different payments build separate rows")
    void shouldKeepPaymentsIsolated() {
        UUID otherPayment = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

        projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 0L));
        PaymentCompletedEvent other = paymentCompleted();
        other.setPaymentId(otherPayment);
        projector.onPaymentCompleted(other, context("payment.completed", 1L));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(PAYMENT_ID).getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(rows.get(otherPayment).getPaymentId()).isEqualTo(otherPayment);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void applyEvent(String which) {
        switch (which) {
            case "completed" -> projector.onPaymentCompleted(paymentCompleted(), context("payment.completed", 0L));
            case "debited" -> projector.onAccountDebited(accountDebited(), context("account.debited", 0L));
            case "credited" -> projector.onAccountCredited(accountCredited(), context("account.credited", 0L));
            default -> throw new IllegalArgumentException("Unknown event: " + which);
        }
    }

    private static EventContext context(String topic, Long offset) {
        return EventContext.of(topic, 0, offset);
    }

    private static PaymentCompletedEvent paymentCompleted() {
        return PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(PAYMENT_ID)
                .payerAccountId(PAYER)
                .payeeAccountId(PAYEE)
                .amount(AMOUNT)
                .currency(CURRENCY)
                .status("COMPLETED")
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static PaymentFailedEvent paymentFailed(String reason) {
        return PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(PAYMENT_ID)
                .payerAccountId(PAYER)
                .payeeAccountId(PAYEE)
                .amount(AMOUNT)
                .currency(CURRENCY)
                .status("FAILED")
                .failureReason(reason)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static AccountDebitedEvent accountDebited() {
        return AccountDebitedEvent.builder()
                .eventId(UUID.randomUUID())
                .referenceId(PAYMENT_ID)
                .accountId(PAYER)
                .ledgerEntryId(DEBIT_LEDGER_ID)
                .amount(AMOUNT)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static AccountCreditedEvent accountCredited() {
        return AccountCreditedEvent.builder()
                .eventId(UUID.randomUUID())
                .referenceId(PAYMENT_ID)
                .accountId(PAYEE)
                .ledgerEntryId(CREDIT_LEDGER_ID)
                .amount(AMOUNT)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
