package com.payments.platform.transactionhistoryservice.unit.query;

import com.payments.platform.transactionhistoryservice.api.TransactionDirection;
import com.payments.platform.transactionhistoryservice.api.TransactionResponse;
import com.payments.platform.transactionhistoryservice.api.TransactionSummaryResponse;
import com.payments.platform.transactionhistoryservice.exception.TransactionNotFoundException;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import com.payments.platform.transactionhistoryservice.query.TransactionHistoryQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the part of the read side with actual logic in it: turning an
 * account-neutral stored row into an account-relative response.
 */
class TransactionHistoryQueryServiceTest {

    private static final UUID PAYMENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STRANGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private TransactionHistoryRepository repository;
    private TransactionHistoryQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionHistoryRepository.class);
        service = new TransactionHistoryQueryService(repository);
    }

    @Test
    @DisplayName("the payer sees the transaction as a DEBIT to the payee")
    void shouldPresentPayerPerspectiveAsDebit() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(completedRow()));

        TransactionResponse response = service.findByPaymentId(PAYMENT_ID, PAYER);

        assertThat(response.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(response.getCounterpartyAccountId()).isEqualTo(PAYEE);
    }

    @Test
    @DisplayName("the payee sees the same transaction as a CREDIT from the payer")
    void shouldPresentPayeePerspectiveAsCredit() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(completedRow()));

        TransactionResponse response = service.findByPaymentId(PAYMENT_ID, PAYEE);

        assertThat(response.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(response.getCounterpartyAccountId()).isEqualTo(PAYER);
    }

    @Test
    @DisplayName("direction is null when the perspective account is not a party to the payment")
    void shouldLeaveDirectionNullForUnrelatedAccount() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(completedRow()));

        TransactionResponse response = service.findByPaymentId(PAYMENT_ID, STRANGER);

        assertThat(response.getDirection()).isNull();
        assertThat(response.getCounterpartyAccountId()).isNull();
        // The underlying legs are still reported.
        assertThat(response.getPayerAccountId()).isEqualTo(PAYER);
    }

    @Test
    @DisplayName("direction is null when no perspective account is supplied")
    void shouldLeaveDirectionNullWithoutPerspective() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(completedRow()));

        assertThat(service.findByPaymentId(PAYMENT_ID, null).getDirection()).isNull();
    }

    @Test
    @DisplayName("a partially projected row is reported honestly rather than hidden")
    void shouldExposePartialState() {
        TransactionHistory partial = TransactionHistory.builder()
                .paymentId(PAYMENT_ID)
                .payerAccountId(PAYER)
                .amount(new BigDecimal("200.0000"))
                .status(TransactionStatus.IN_PROGRESS)
                .eventsSeen("account.debited")
                .build();
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(partial));

        TransactionResponse response = service.findByPaymentId(PAYMENT_ID, PAYER);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.IN_PROGRESS);
        assertThat(response.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        // Payee leg has not arrived, so there is no counterparty to report yet.
        assertThat(response.getCounterpartyAccountId()).isNull();
        assertThat(response.getCreditedAt()).isNull();
        assertThat(response.getEventsSeen()).isEqualTo("account.debited");
    }

    @Test
    @DisplayName("an unprojected payment is a 404, not an empty result")
    void shouldThrowWhenNotProjected() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPaymentId(PAYMENT_ID, PAYER))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("not have been projected yet");
    }

    @Test
    @DisplayName("summary reports every status, including those with no rows")
    void shouldSeedAllStatusesInSummary() {
        when(repository.countForAccount(PAYER)).thenReturn(3L);
        when(repository.sumOutgoing(any(), any())).thenReturn(new BigDecimal("700.5000"));
        when(repository.sumIncoming(any(), any())).thenReturn(null); // no rows -> null SUM
        when(repository.countByStatusForAccount(PAYER)).thenReturn(List.of(
                new Object[]{TransactionStatus.COMPLETED, 2L},
                new Object[]{TransactionStatus.FAILED, 1L}
        ));

        TransactionSummaryResponse summary = service.summarize(PAYER);

        assertThat(summary.getTotalTransactions()).isEqualTo(3L);
        assertThat(summary.getTotalOutgoing()).isEqualByComparingTo("700.5000");
        // A null SUM must surface as zero, not as a null in the JSON.
        assertThat(summary.getTotalIncoming()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getCountByStatus())
                .containsEntry(TransactionStatus.COMPLETED, 2L)
                .containsEntry(TransactionStatus.FAILED, 1L)
                .containsEntry(TransactionStatus.IN_PROGRESS, 0L);
    }

    private static TransactionHistory completedRow() {
        return TransactionHistory.builder()
                .paymentId(PAYMENT_ID)
                .payerAccountId(PAYER)
                .payeeAccountId(PAYEE)
                .amount(new BigDecimal("500.5000"))
                .currency("INR")
                .status(TransactionStatus.COMPLETED)
                .eventsSeen("payment.completed,account.debited,account.credited")
                .build();
    }
}
