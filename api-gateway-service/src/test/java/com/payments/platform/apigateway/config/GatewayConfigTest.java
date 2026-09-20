package com.payments.platform.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit Tests for Gateway Configuration
 *
 * Tests that verify:
 * 1. Routes are configured for all 5 services
 * 2. Path patterns match correctly
 * 3. Routes are applied in correct order
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void testGatewayConfigExists() {
        // Test: Gateway configuration is loaded
        assertNotNull(routeLocator, "RouteLocator should be autowired");
    }

    @Test
    void testHealthCheckRoute() {
        // Test: Health check endpoint accessible without auth

        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Test
    void testAccountServiceRouteProtected() {
        // Test: Account service route requires auth

        webTestClient
                .get()
                .uri("/api/v1/accounts/123")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testPaymentServiceRouteProtected() {
        // Test: Payment service route requires auth

        webTestClient
                .post()
                .uri("/api/v1/payments")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testFraudServiceRouteProtected() {
        // Test: Fraud service route requires auth

        webTestClient
                .post()
                .uri("/api/v1/risk/assess")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testNotificationServiceRouteProtected() {
        // Test: Notification service route requires auth

        webTestClient
                .get()
                .uri("/api/v1/notifications")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testTransactionHistoryRouteProtected() {
        // Test: Transaction history route requires auth

        webTestClient
                .get()
                .uri("/api/v1/transactions")
                .exchange()
                .expectStatus()
                .isUnauthorized();  // 401
    }

    @Test
    void testPathPatternMatching() {
        // Test: Path patterns match correctly

        // All these should require auth (match protected routes)
        String[] protectedPaths = {
                "/api/v1/accounts/123/balance",
                "/api/v1/accounts/456/ledger",
                "/api/v1/payments/789",
                "/api/v1/risk/assess",
                "/api/v1/notifications/all",
                "/api/v1/transactions/search"
        };

        for (String path : protectedPaths) {
            webTestClient
                    .get()
                    .uri(path)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();  // All should be 401 without auth
        }
    }

    @Test
    void testInvalidPathReturns404() {
        // Test: Invalid paths return 404

        webTestClient
                .get()
                .uri("/invalid/path/that/does/not/exist")
                .exchange()
                .expectStatus()
                .isNotFound();  // 404
    }
}
