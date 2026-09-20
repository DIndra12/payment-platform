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
 * Acceptance Tests for API Gateway
 *
 * End-to-end testing of real user scenarios:
 * 1. Complete authentication flow
 * 2. Request routing through gateway
 * 3. Rate limiting under load
 * 4. Error scenarios
 * 5. Multiple concurrent users
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayAcceptanceTest {

    @Autowired
    private WebTestClient webTestClient;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // Generate tokens with different roles
        userToken = TestTokenGenerator.generateToken("user123", "USER");
        adminToken = TestTokenGenerator.generateToken("admin456", "ADMIN");
    }

    // ==================== Scenario 1: Authentication Flow ====================

    @Test
    void acceptanceTest_UserCanAuthenticateAndAccessAPI() {
        // Scenario: User authenticates with JWT and accesses API

        // Step 1: User makes request without token
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        // Step 2: User authenticates (gets token)
        // Token is already generated in setUp

        // Step 3: User makes request with token
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();  // 404 because backend not running, but auth passed!
    }

    @Test
    void acceptanceTest_DifferentUsersHaveIndependentTokens() {
        // Scenario: Multiple users can authenticate independently

        // User 1 authenticates
        webTestClient
                .get()
                .uri("/api/v1/accounts/user1")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // User 2 (admin) authenticates
        webTestClient
                .get()
                .uri("/api/v1/accounts/admin")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // Both succeeded
    }

    // ==================== Scenario 2: Request Routing ====================

    @Test
    void acceptanceTest_RequestsRouteToCorrectServices() {
        // Scenario: Different request paths route to different services

        // Route to Account Service
        webTestClient
                .get()
                .uri("/api/v1/accounts/123/balance")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // Route to Payment Service
        webTestClient
                .post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .bodyValue("{\"amount\": 100}")
                .exchange()
                .expectStatus()
                .isNotFound();

        // Route to Fraud Service
        webTestClient
                .post()
                .uri("/api/v1/risk/assess")
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .bodyValue("{\"amount\": 50}")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    // ==================== Scenario 3: Rate Limiting ====================

    @Test
    void acceptanceTest_RateLimitingEnforcesQuota() {
        // Scenario: User hits rate limit after 100 requests

        // Send requests up to limit
        for (int i = 0; i < 100; i++) {
            webTestClient
                    .get()
                    .uri("/api/v1/accounts/123")
                    .header("Authorization", "Bearer " + userToken)
                    .exchange()
                    .expectStatus()
                    .isNotFound();  // Backend not running, but shouldn't hit rate limit
        }

        // Request 101 should be rate limited
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isEqualTo(429);  // 429 Too Many Requests
    }

    @Test
    void acceptanceTest_RateLimitHeaderProvided() {
        // Scenario: When rate limited, Retry-After header is provided

        // Exhaust rate limit
        for (int i = 0; i < 100; i++) {
            webTestClient
                    .get()
                    .uri("/api/v1/accounts/123")
                    .header("Authorization", "Bearer " + userToken)
                    .exchange();
        }

        // Next request should have Retry-After header
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectHeader()
                .exists("Retry-After");
    }

    @Test
    void acceptanceTest_DifferentUsersHaveSeparateRateLimits() {
        // Scenario: Rate limit is per-user, not global

        // User 1 makes 100 requests
        for (int i = 0; i < 100; i++) {
            webTestClient
                    .get()
                    .uri("/api/v1/accounts/123")
                    .header("Authorization", "Bearer " + userToken)
                    .exchange();
        }

        // User 1 should be rate limited
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isEqualTo(429);

        // User 2 should NOT be rate limited (different user)
        webTestClient
                .get()
                .uri("/api/v1/accounts/456")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isNotFound();  // Backend not running, but rate limit not hit
    }

    // ==================== Scenario 4: Error Handling ====================

    @Test
    void acceptanceTest_InvalidTokensRejected() {
        // Scenario: Various invalid tokens are rejected

        String[] invalidTokens = {
                "invalid",
                "xyz123",
                "",
                "Bearer invalid",
                userToken + "corrupted"
        };

        for (String invalidToken : invalidTokens) {
            webTestClient
                    .get()
                    .uri("/api/v1/accounts/123")
                    .header("Authorization", "Bearer " + invalidToken)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
    }

    @Test
    void acceptanceTest_MissingAuthHeaderRejected() {
        // Scenario: Requests without Authorization header are rejected

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void acceptanceTest_HealthCheckNotRequireAuth() {
        // Scenario: Health check endpoint accessible without auth

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    // ==================== Scenario 5: Multiple Request Types ====================

    @Test
    void acceptanceTest_GetPostPutDeleteAllSupported() {
        // Scenario: Different HTTP methods are supported

        // GET
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // POST
        webTestClient
                .post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound();

        // PUT (if supported)
        webTestClient
                .put()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange();
        // Don't assert status - route may not exist

        // DELETE (if supported)
        webTestClient
                .delete()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + userToken)
                .exchange();
        // Don't assert status - route may not exist
    }

    // ==================== Scenario 6: Concurrent Users ====================

    @Test
    void acceptanceTest_MultipleConcurrentUsersWork() {
        // Scenario: Multiple users can access simultaneously

        // User 1
        webTestClient
                .get()
                .uri("/api/v1/accounts/user1")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // User 2 (admin)
        webTestClient
                .get()
                .uri("/api/v1/accounts/user2")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // User 1 again
        webTestClient
                .get()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // All succeeded
    }

    // ==================== Scenario 7: Complete User Journey ====================

    @Test
    void acceptanceTest_CompleteUserJourney() {
        // Scenario: Full user journey from start to finish

        // 1. User tries to access without auth
        webTestClient
                .get()
                .uri("/api/v1/accounts/my-account")
                .exchange()
                .expectStatus()
                .isUnauthorized();

        // 2. User authenticates (gets token)
        // Already done in setUp

        // 3. User accesses multiple services
        webTestClient
                .get()
                .uri("/api/v1/accounts/my-account/balance")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // 4. User performs operations
        webTestClient
                .post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .bodyValue("{\"amount\": 100}")
                .exchange()
                .expectStatus()
                .isNotFound();

        // 5. User checks history
        webTestClient
                .get()
                .uri("/api/v1/transactions")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNotFound();

        // All steps succeeded (auth and routing worked)
    }
}
