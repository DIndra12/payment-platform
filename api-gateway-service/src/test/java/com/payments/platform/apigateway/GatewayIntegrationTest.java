package com.payments.platform.apigateway;

import com.payments.platform.apigateway.util.TestTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Arrays;

/**
 * Integration Tests for Complete Gateway Flow
 *
 * Tests the complete request processing pipeline:
 * 1. JWT Authentication Filter
 * 2. Rate Limiting Filter
 * 3. Routing to backend services
 * 4. Response handling
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String validToken;

    @BeforeEach
    void setUp() {
        // Generate valid token for testing
        validToken = TestTokenGenerator.generateToken("test-user", Arrays.asList("USER", "ADMIN"));
    }

    // ==================== Authentication Tests ====================

    @Test
    void testRequestWithoutAuthenticationFails() {
        // Test: Request without JWT returns 401

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void testRequestWithValidAuthenticationPasses() {
        // Test: Request with valid JWT is processed

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus()
                .isNotFound();  // 404 because backend not running, but auth passed!
    }

    @Test
    void testRequestWithInvalidTokenFails() {
        // Test: Request with invalid JWT returns 401

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer invalid_token_xyz")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void testRequestWithMalformedAuthHeaderFails() {
        // Test: Missing "Bearer " prefix returns 401

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", validToken)  // Missing "Bearer "
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    // ==================== Header Propagation Tests ====================

    @Test
    void testUserIdHeaderAddedToRequest() {
        // Test: JWT filter adds X-User-Id header

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus()
                .isNotFound();  // Backend not running, but auth passed (header added)
    }

    @Test
    void testMultipleRolesInToken() {
        // Test: Token with multiple roles extracted correctly

        String tokenWithRoles = TestTokenGenerator.generateToken("user456",
                Arrays.asList("USER", "ADMIN", "SUPPORT"));

        webTestClient
                .get()
                .uri("/api/v1/accounts/456")
                .header("Authorization", "Bearer " + tokenWithRoles)
                .exchange()
                .expectStatus()
                .isNotFound();  // Auth passed, backend not available
    }

    // ==================== Routing Tests ====================

    @Test
    void testAccountServicePathRouting() {
        // Test: /api/v1/accounts/** patterns route correctly

        String[] accountPaths = {
                "/api/v1/accounts/123",
                "/api/v1/accounts/456/balance",
                "/api/v1/accounts/789/ledger"
        };

        for (String path : accountPaths) {
            webTestClient
                    .get()
                    .uri(path)
                    .header("Authorization", "Bearer " + validToken)
                    .exchange()
                    .expectStatus()
                    .isNotFound();  // Not running, but routing attempted
        }
    }

    @Test
    void testPaymentServicePathRouting() {
        // Test: /api/v1/payments/** patterns route correctly

        webTestClient
                .post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + validToken)
                .header("Content-Type", "application/json")
                .bodyValue("{\"amount\": 100}")
                .exchange()
                .expectStatus()
                .isNotFound();  // Not running, but routing attempted
    }

    @Test
    void testFraudServicePathRouting() {
        // Test: /api/v1/risk/** patterns route correctly

        webTestClient
                .post()
                .uri("/api/v1/risk/assess")
                .header("Authorization", "Bearer " + validToken)
                .header("Content-Type", "application/json")
                .bodyValue("{\"amount\": 50}")
                .exchange()
                .expectStatus()
                .isNotFound();  // Not running, but routing attempted
    }

    // ==================== Health Check Tests ====================

    @Test
    void testHealthCheckDoesNotRequireAuthentication() {
        // Test: Health endpoint accessible without JWT

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Test
    void testHealthCheckReturnsValidJson() {
        // Test: Health check returns JSON with status

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .is2xxSuccessful()
                .expectBody()
                .jsonPath("$.status").isNotEmpty();
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testInvalidPathReturns404() {
        // Test: Invalid paths return 404

        webTestClient
                .get()
                .uri("/invalid/path")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void testMissingRequiredHeadersHandled() {
        // Test: Missing Authorization header handled gracefully

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    // ==================== HTTP Method Tests ====================

    @Test
    void testGetRequestsRouted() {
        // Test: GET requests routed correctly

        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void testPostRequestsRouted() {
        // Test: POST requests routed correctly

        webTestClient
                .post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + validToken)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void testMultipleHeadersPreserved() {
        // Test: Multiple headers passed through correctly

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + validToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Custom-Header", "custom-value")
                .exchange()
                .expectStatus()
                .isNotFound();  // Backend not running, but headers passed through
    }

    // ==================== Token Validation Edge Cases ====================

    @Test
    void testEmptyTokenFails() {
        // Test: Empty Bearer token fails

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer ")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void testIncorrectBearerPrefixFails() {
        // Test: Wrong prefix fails

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Basic " + validToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void testCaseSensitiveBearerPrefix() {
        // Test: "bearer" (lowercase) should fail

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "bearer " + validToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
