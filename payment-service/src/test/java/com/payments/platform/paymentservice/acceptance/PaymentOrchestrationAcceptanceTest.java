package com.payments.platform.paymentservice.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.*;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class PaymentOrchestrationAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.2"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private org.apache.kafka.clients.consumer.Consumer<String, String> consumer;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("clients.account-service.url", () -> "http://localhost:${wiremock.server.port}");
        registry.add("clients.fraud-service.url", () -> "http://localhost:${wiremock.server.port}");
    }

    @BeforeEach
    void setUp(@Autowired ConsumerFactory<String, String> consumerFactory) {
        consumer = consumerFactory.createConsumer("test-group", "test");
        consumer.subscribe(java.util.Collections.singletonList("payment.completed"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void shouldSuccessfullyOrchestratePayment() {
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
        stubFor(post(urlEqualTo("/api/v1/accounts/debit"))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
        stubFor(post(urlEqualTo("/api/v1/accounts/credit"))
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
        var paymentId = response.getBody().get("paymentId").toString();

        // Verify payment status becomes COMPLETED
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var statusResponse = restTemplate.getForEntity("/api/v1/payments/" + paymentId, Map.class);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statusResponse.getBody().get("status")).isEqualTo("COMPLETED");
        });

        // Verify Kafka event is published
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000));
            assertThat(records.count()).isEqualTo(1);
            var event = objectMapper.readValue(records.iterator().next().value(), Map.class);
            assertThat(event.get("paymentId")).isEqualTo(paymentId);
        });
    }
}
