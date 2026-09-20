package com.payments.platform.apigateway.config;

import com.payments.platform.apigateway.filter.JwtAuthenticationFilter;
import com.payments.platform.apigateway.filter.RateLimitFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Configuration
 *
 * This defines:
 * 1. Routes: Which URL patterns go to which services
 * 2. Filters: What security/rate-limiting to apply
 *
 * Request Flow for each route:
 *
 *   1. Request arrives: GET /api/v1/accounts/123
 *   2. Gateway checks routes (does it match any pattern?)
 *   3. Matching route: /api/v1/accounts/** → account-service:8081
 *   4. Apply filters in order:
 *      a. JwtAuthenticationFilter: Validate JWT token
 *      b. RateLimitFilter: Check rate limit
 *   5. Forward request to: http://account-service:8081/api/v1/accounts/123
 *   6. Get response from service
 *   7. Return response to client
 *
 * Why order matters?
 *
 * JwtAuthenticationFilter first:
 *   └─ Adds X-User-Id header (needed by rate limit filter)
 *
 * RateLimitFilter second:
 *   └─ Uses X-User-Id header to track rate limits per user
 *
 * If we reversed them, rate limit filter wouldn't have user ID!
 */
@Configuration
@Slf4j
public class GatewayConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    /**
     * Define all gateway routes
     *
     * Routes map URL patterns to backend services.
     *
     * Pattern: /api/v1/accounts/** means:
     * - /api/v1/accounts/123 ✓ matches
     * - /api/v1/accounts/123/balance ✓ matches
     * - /api/v1/payments/456 ✗ doesn't match
     *
     * @param builder Spring Cloud Gateway route builder
     * @return RouteLocator with all routes defined
     */
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        log.info("Initializing gateway routes...");

        return builder.routes()

                // Route 1: Account Service
                .route("account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                                .filter(rateLimitFilter.apply(new RateLimitFilter.Config()))
                        )
                        .uri("http://account-service:8081")
                )

                // Route 2: Payment Service
                .route("payment-service", r -> r
                        .path("/api/v1/payments/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                                .filter(rateLimitFilter.apply(new RateLimitFilter.Config()))
                        )
                        .uri("http://payment-service:8083")
                )

                // Route 3: Fraud Service
                .route("fraud-service", r -> r
                        .path("/api/v1/risk/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                                .filter(rateLimitFilter.apply(new RateLimitFilter.Config()))
                        )
                        .uri("http://fraud-service:8082")
                )

                // Route 4: Notification Service
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                                .filter(rateLimitFilter.apply(new RateLimitFilter.Config()))
                        )
                        .uri("http://notification-service:8084")
                )

                // Route 5: Transaction History Service
                .route("transaction-history-service", r -> r
                        .path("/api/v1/transactions/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config()))
                                .filter(rateLimitFilter.apply(new RateLimitFilter.Config()))
                        )
                        .uri("http://transaction-history-service:8085")
                )

                // Route 6: Health/Actuator endpoints (no auth required)
                .route("health-check", r -> r
                        .path("/actuator/**")
                        .uri("http://api-gateway-service:8080")
                )

                .build();
    }
}
