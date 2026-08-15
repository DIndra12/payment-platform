package com.payments.platform.paymentservice.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.paymentservice.outbox.OutboxEventRepository;
import com.payments.platform.paymentservice.persistence.PaymentRepository;
import com.payments.platform.paymentservice.persistence.entity.Payment;
import com.payments.platform.paymentservice.orchestration.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
class PaymentOrchestrationAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSuccessfullyOrchestratePaymentAndCreateOutboxEvent() {
        // Given
        var payerAccountId = UUID.randomUUID();
        var payeeAccountId = UUID.randomUUID();
        var idempotencyKey = UUID.randomUUID().toString();

        // Stub Fraud Service
        stubFor(post(urlEqualTo("/api/v1/risk/evaluate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"decision\": \"APPROVE\"}")));

        // Stub Account Service
        stubFor(post(urlMatching("/api/v1/accounts/.*/debit"))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
        stubFor(post(urlMatching("/api/v1/accounts/.*/credit"))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));

        // When
        var requestBody = Map.of(
                "payerAccountId", payerAccountId,
                "payeeAccountId", payeeAccountId,
                "amount", 100.00,
                "currency", "USD"
        );
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        var requestEntity = new HttpEntity<>(requestBody, headers);

        var response = restTemplate.postForEntity("/api/v1/payments", requestEntity, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var paymentId = UUID.fromString(response.getBody().get("paymentId").toString());

        // Verify payment status becomes COMPLETED by polling the database directly
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });

        // Verify that an outbox event was created for the completed payment
        var outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        var outboxEvent = outboxEvents.get(0);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("payment");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(paymentId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo("payment.completed");
    }
}
