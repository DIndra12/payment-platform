package com.payments.platform.transactionhistoryservice.acceptance;

import com.payments.platform.transactionhistoryservice.persistence.ProcessedEventRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.support.EventFixtures;
import com.payments.platform.transactionhistoryservice.support.KafkaPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole point of the service, end to end: publish the four events in a
 * scrambled order over real Kafka, then ask the HTTP API for the account's
 * history and expect one correctly stitched transaction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransactionHistoryAcceptanceTest extends KafkaPostgresTestBase {

    private static final BigDecimal AMOUNT = new BigDecimal("500.5000");
    private static final String CURRENCY = "INR";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
    @DisplayName("scrambled events produce one stitched transaction, queryable from both sides")
    void shouldServeStitchedHistoryToBothParties() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        // Deliberately the "wrong" order: the terminal event first, ledger movements after.
        publishRaw("payment.completed", paymentId.toString(),
                EventFixtures.paymentCompleted(paymentId, payer, payee, AMOUNT, CURRENCY));
        publishRaw("account.credited", payee.toString(),
                EventFixtures.accountCredited(paymentId, payee, UUID.randomUUID(), AMOUNT));
        publishRaw("account.debited", payer.toString(),
                EventFixtures.accountDebited(paymentId, payer, UUID.randomUUID(), AMOUNT));

        awaitAllEventsProjected(paymentId);

        // The payer sees money going out.
        ResponseEntity<String> payerView = get("/api/v1/transactions/account/" + payer);
        assertThat(payerView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payerView.getBody())
                .contains("\"totalElements\":1")
                .contains("\"direction\":\"DEBIT\"")
                .contains("\"counterpartyAccountId\":\"" + payee + "\"")
                .contains("\"status\":\"COMPLETED\"");

        // The payee sees the same transaction as money coming in.
        ResponseEntity<String> payeeView = get("/api/v1/transactions/account/" + payee);
        assertThat(payeeView.getBody())
                .contains("\"direction\":\"CREDIT\"")
                .contains("\"counterpartyAccountId\":\"" + payer + "\"");

        // And it is retrievable directly by payment id.
        ResponseEntity<String> byId = get("/api/v1/transactions/" + paymentId + "?accountId=" + payer);
        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byId.getBody()).contains("\"direction\":\"DEBIT\"");
    }

    @Test
    @DisplayName("a partially projected transaction is reported as IN_PROGRESS")
    void shouldReportPartialTransactionAsInProgress() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();

        // Only the debit leg arrives; the payment outcome is still unknown.
        publishRaw("account.debited", payer.toString(),
                EventFixtures.accountDebited(paymentId, payer, UUID.randomUUID(), AMOUNT));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(paymentId).isPresent());

        ResponseEntity<String> response = get("/api/v1/transactions/account/" + payer);
        assertThat(response.getBody())
                .contains("\"status\":\"IN_PROGRESS\"")
                .contains("\"direction\":\"DEBIT\"");
    }

    @Test
    @DisplayName("direction filter narrows to one leg")
    void shouldFilterByDirection() {
        UUID paymentId = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();

        publishRaw("payment.completed", paymentId.toString(),
                EventFixtures.paymentCompleted(paymentId, payer, payee, AMOUNT, CURRENCY));
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(paymentId).isPresent());

        // The payer has an outgoing transaction...
        assertThat(get("/api/v1/transactions/account/" + payer + "?direction=DEBIT").getBody())
                .contains("\"totalElements\":1");
        // ...and no incoming one.
        assertThat(get("/api/v1/transactions/account/" + payer + "?direction=CREDIT").getBody())
                .contains("\"totalElements\":0");
    }

    @Test
    @DisplayName("summary aggregates completed value for an account")
    void shouldSummarizeAccount() {
        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        publishRaw("payment.completed", first.toString(),
                EventFixtures.paymentCompleted(first, payer, payee, new BigDecimal("100.0000"), CURRENCY));
        publishRaw("payment.completed", second.toString(),
                EventFixtures.paymentCompleted(second, payer, payee, new BigDecimal("250.0000"), CURRENCY));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> transactionRepository.findByPaymentId(first).isPresent()
                        && transactionRepository.findByPaymentId(second).isPresent());

        ResponseEntity<String> summary = get("/api/v1/transactions/account/" + payer + "/summary");
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody())
                .contains("\"totalTransactions\":2")
                .contains("\"COMPLETED\":2");
    }

    @Test
    @DisplayName("an unprojected payment returns 404 with a message explaining why")
    void shouldReturn404ForUnknownPayment() {
        ResponseEntity<String> response = get("/api/v1/transactions/" + UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("may not have been projected yet");
    }

    @Test
    @DisplayName("an oversized page size is rejected")
    void shouldRejectInvalidPageSize() {
        ResponseEntity<String> response =
                get("/api/v1/transactions/account/" + UUID.randomUUID() + "?size=5000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void awaitAllEventsProjected(UUID paymentId) {
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(processedEventRepository.findByPaymentId(paymentId))
                        .hasSize(3));
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }
}
