package com.payments.platform.transactionhistoryservice.integration;

import com.payments.platform.transactionhistoryservice.persistence.ProcessedEventRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import com.payments.platform.transactionhistoryservice.support.EventFixtures;
import com.payments.platform.transactionhistoryservice.support.KafkaPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end ingest against real Kafka and real Postgres, publishing the exact
 * JSON the producer puts on the wire.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectionIntegrationTest extends KafkaPostgresTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("500.5000");
    private static final String CURRENCY = "INR";

    @Autowired
    private TransactionHistoryRepository transactionRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("payment.completed becomes a COMPLETED row")
    void shouldProjectPaymentCompleted() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        publishRaw("payment.completed", paymentId.toString(),
                EventFixtures.paymentCompleted(paymentId, payer, payee, AMOUNT, CURRENCY));

        TransactionHistory row = awaitRow(paymentId);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(row.getPayerAccountId()).isEqualTo(payer);
        assertThat(row.getPayeeAccountId()).isEqualTo(payee);
        assertThat(row.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(row.getCurrency()).isEqualTo(CURRENCY);
    }

    @Test
    @DisplayName("the legacy five-field payload still projects")
    void shouldProjectLegacyPayload() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        publishRaw("payment.completed", paymentId.toString(),
                EventFixtures.paymentCompletedLegacyFiveFields(paymentId, payer, payee, AMOUNT, CURRENCY));

        TransactionHistory row = awaitRow(paymentId);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        // occurredAt was absent, so the projector substituted its own timestamp.
        assertThat(row.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("payment.failed becomes a FAILED row carrying the reason")
    void shouldProjectPaymentFailed() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        publishRaw("payment.failed", paymentId.toString(),
                EventFixtures.paymentFailed(paymentId, payer, payee, AMOUNT, CURRENCY, "Insufficient funds"));

        TransactionHistory row = awaitRow(paymentId);
        assertThat(row.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(row.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("all four topics stitch into one row")
    void shouldStitchAllFourTopics() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        UUID debitLedgerId = UUID.randomUUID();
        UUID creditLedgerId = UUID.randomUUID();

        publishRaw("account.debited", payer.toString(),
                EventFixtures.accountDebited(paymentId, payer, debitLedgerId, AMOUNT));
        publishRaw("account.credited", payee.toString(),
                EventFixtures.accountCredited(paymentId, payee, creditLedgerId, AMOUNT));
        publishRaw("payment.completed", paymentId.toString(),
                EventFixtures.paymentCompleted(paymentId, payer, payee, AMOUNT, CURRENCY));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            TransactionHistory row = transactionRepository.findByPaymentId(paymentId).orElse(null);
            assertThat(row).isNotNull();
            assertThat(row.eventsSeenAsSet()).containsExactlyInAnyOrder(
                    "payment.completed", "account.debited", "account.credited");
            assertThat(row.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(row.getDebitLedgerId()).isEqualTo(debitLedgerId);
            assertThat(row.getCreditLedgerId()).isEqualTo(creditLedgerId);
        });

        // Exactly one row for the payment, and one idempotency marker per event.
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.findByPaymentId(paymentId)).hasSize(3);
    }

    @Test
    @DisplayName("a redelivered event does not produce a second row or a second marker")
    void shouldIgnoreRedelivery() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        String payload = EventFixtures.paymentCompleted(paymentId, payer, payee, AMOUNT, CURRENCY);

        publishRaw("payment.completed", paymentId.toString(), payload);
        awaitRow(paymentId);

        // Same event again, exactly as Kafka would redeliver it.
        publishRaw("payment.completed", paymentId.toString(), payload);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(processedEventRepository.findByPaymentId(paymentId)).hasSize(1);
        });
    }

    private TransactionHistory awaitRow(UUID paymentId) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(paymentId).isPresent());
        return transactionRepository.findByPaymentId(paymentId).orElseThrow();
    }
}
