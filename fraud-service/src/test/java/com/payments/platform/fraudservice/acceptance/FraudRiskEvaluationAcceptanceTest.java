package com.payments.platform.fraudservice.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FraudRiskEvaluationAcceptanceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldApproveLowValueTransaction() {
        // Given
        var url = "/api/v1/risk/evaluate";
        var request = Map.of(
                "payerAccountId", UUID.randomUUID().toString(),
                "amount", 100.00,
                "currency", "USD"
        );

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision")).isEqualTo("APPROVE");
    }

    @Test
    void shouldRejectHighValueTransaction() {
        // Given
        var url = "/api/v1/risk/evaluate";
        var request = Map.of(
                "payerAccountId", UUID.randomUUID().toString(),
                "amount", 20000.00,
                "currency", "USD"
        );

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision")).isEqualTo("REJECT");
    }
}
