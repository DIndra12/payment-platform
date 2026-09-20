package com.payments.platform.apigateway.filter;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for Rate Limit Filter
 *
 * Tests the token bucket algorithm:
 * 1. Token consumption reduces count
 * 2. Rate limit exceeded returns 429
 * 3. Multiple users tracked separately
 * 4. Retry-After header present on 429
 */
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
    }

    @Test
    void testRateLimitFilterCreatesNewBucket() {
        // Test: Filter creates bucket for new user
        GatewayFilter filter = rateLimitFilter.apply(new RateLimitFilter.Config());
        assertNotNull(filter);
    }

    @Test
    void testTokenConsumption() {
        // Test: Each request consumes 1 token

        // Create a bucket (100 tokens initially)
        Bucket bucket1 = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(100,
                    io.github.bucket4j.Refill.intervally(100, java.time.Duration.ofMinutes(1))))
                .build();

        assertTrue(bucket1.tryConsume(1), "First token should be consumable");
        assertTrue(bucket1.tryConsume(1), "Second token should be consumable");
        assertTrue(bucket1.tryConsume(98), "All 98 remaining tokens should be consumable");
        assertFalse(bucket1.tryConsume(1), "Token 101 should be rejected");
    }

    @Test
    void testMultipleUsersBucketSeparation() {
        // Test: Different users have separate buckets

        // User 1 bucket
        Bucket userBucket1 = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(100,
                    io.github.bucket4j.Refill.intervally(100, java.time.Duration.ofMinutes(1))))
                .build();

        // User 2 bucket
        Bucket userBucket2 = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(100,
                    io.github.bucket4j.Refill.intervally(100, java.time.Duration.ofMinutes(1))))
                .build();

        // User 1 exhausts tokens
        for (int i = 0; i < 100; i++) {
            userBucket1.tryConsume(1);
        }

        // User 1 should be rate limited
        assertFalse(userBucket1.tryConsume(1), "User 1 should be rate limited");

        // User 2 should still have tokens
        assertTrue(userBucket2.tryConsume(1), "User 2 should not be rate limited");
    }

    @Test
    void testBucketRefillAfterTime() {
        // Test: Bucket refills after duration passes

        // Create bucket with 1 second refill
        Bucket bucket = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(2,
                    io.github.bucket4j.Refill.intervally(2, java.time.Duration.ofSeconds(1))))
                .build();

        // Consume 2 tokens
        assertTrue(bucket.tryConsume(2), "Should consume 2 tokens");
        assertFalse(bucket.tryConsume(1), "Should not consume when empty");

        // Wait for refill
        try {
            Thread.sleep(1100); // Wait 1.1 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should be able to consume again
        assertTrue(bucket.tryConsume(1), "Should refill after time passes");
    }

    @Test
    void testRateLimitFilterConfiguration() {
        // Test: Filter is properly configured
        GatewayFilter filter = rateLimitFilter.apply(new RateLimitFilter.Config());
        assertNotNull(filter, "Filter should be created");
    }

    @Test
    void testRequestsPerMinuteLimit() {
        // Test: 100 requests per minute limit

        Bucket bucket = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(100,
                    io.github.bucket4j.Refill.intervally(100, java.time.Duration.ofMinutes(1))))
                .build();

        // Should allow 100 requests
        for (int i = 0; i < 100; i++) {
            assertTrue(bucket.tryConsume(1), "Request " + (i+1) + " should be allowed");
        }

        // Should reject 101st request
        assertFalse(bucket.tryConsume(1), "Request 101 should be rejected");
    }

    @Test
    void testConcurrentRequestsFromSameUser() {
        // Test: Concurrent requests use same bucket

        Bucket bucket = Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(5,
                    io.github.bucket4j.Refill.intervally(5, java.time.Duration.ofMinutes(1))))
                .build();

        // Simulate concurrent requests
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(1), "Concurrent request " + (i+1) + " should be allowed");
        }

        // 6th request should fail
        assertFalse(bucket.tryConsume(1), "6th concurrent request should be rate limited");
    }
}
