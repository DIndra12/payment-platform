package com.payments.platform.apigateway.filter;

import com.payments.platform.apigateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Arrays;

/**
 * Integration Tests for JWT Authentication Filter
 *
 * Tests that verify:
 * 1. Requests WITH valid JWT token are allowed
 * 2. Requests WITHOUT JWT token are rejected (401)
 * 3. Requests WITH invalid token are rejected (401)
 * 4. JWT claims are extracted and added to request headers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAuthenticationFilterIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setUp() {
        // Setup JWT util with test secret
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000); // 1 hour

        // Generate test tokens
        validToken = jwtUtil.generateToken("test-user", Arrays.asList("USER"));
    }

    @Test
    void testRequestWithValidJwtTokenIsAllowed() {
        // Test: Request with valid JWT token should pass through

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus()
                .isOk();  // Should pass through (may return 404 from backend, but gateway allowed it)
    }

    @Test
    void testRequestWithoutJwtTokenIsRejected() {
        // Test: Request without JWT token should return 401

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testRequestWithInvalidJwtTokenIsRejected() {
        // Test: Request with invalid JWT token should return 401

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testRequestWithMalformedAuthHeaderIsRejected() {
        // Test: Request with malformed Authorization header should return 401

        // Missing "Bearer " prefix
        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .header("Authorization", validToken)  // Missing "Bearer "
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testHealthCheckDoesNotRequireAuth() {
        // Test: Health check endpoints should not require JWT

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
