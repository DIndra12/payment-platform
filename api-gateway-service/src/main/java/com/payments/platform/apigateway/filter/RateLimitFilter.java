package com.payments.platform.apigateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter
 *
 * Limits requests per user to prevent abuse.
 *
 * What is rate limiting?
 *
 * Imagine a restaurant that limits each customer to 100 meals per hour.
 * If someone tries to order meal #101, they're told to wait.
 *
 * This filter does the same for API requests:
 * - Each user gets 100 requests per minute
 * - After 100 requests, return 429 Too Many Requests
 * - After 1 minute, bucket refills and they can make more requests
 *
 * Why rate limiting?
 *
 * 1. **Prevent abuse:** Stop someone from hammering your API
 * 2. **Prevent DoS:** Limit damage from denial-of-service attacks
 * 3. **Fair usage:** Everyone gets equal access to resources
 * 4. **Predictability:** Service doesn't get overloaded
 *
 * How does it work?
 *
 * Token Bucket Algorithm (simple version):
 *
 *   Bucket (100 tokens) ────────────────────┐
 *                                           │
 *   Request comes in                       │
 *   ├─ Does bucket have token? YES          │
 *   ├─ Remove token                        │
 *   └─ Allow request (99 tokens left)
 *                                           ▼
 *   Another request comes in              Tokens
 *   ├─ Does bucket have token? YES       (leak out 1
 *   ├─ Remove token                      token per
 *   └─ Allow request (98 tokens left)    ~600ms)
 *                                           │
 *   100th request                           │
 *   ├─ Does bucket have token? YES         │
 *   ├─ Remove token                        │
 *   └─ Allow request (0 tokens left)       │
 *                                           │
 *   101st request                           │
 *   ├─ Does bucket have token? NO          │
 *   └─ Return 429 Too Many Requests    ────┘
 *
 *   After 1 minute:
 *   ├─ Bucket refilled to 100 tokens
 *   └─ User can make requests again
 */
@Component
@Slf4j
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    // Store buckets per user (userId → Bucket)
    // In production, this would be in Redis for distributed rate limiting
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Configuration
    private static final int REQUESTS_PER_MINUTE = 100;

    public RateLimitFilter() {
        super(Config.class);
    }

    /**
     * Create rate limit bucket for user
     *
     * Allows REQUESTS_PER_MINUTE requests per minute
     *
     * @return New bucket with 100 capacity, refilled every 60 seconds
     */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(REQUESTS_PER_MINUTE, Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get or create bucket for user
     *
     * @param userId User ID (from X-User-Id header)
     * @return Bucket for this user
     */
    private Bucket resolveBucket(String userId) {
        return buckets.computeIfAbsent(userId, k -> createNewBucket());
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            try {
                // 1. Get user ID from request header (added by JwtAuthenticationFilter)
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

                // If no user ID, allow the request (shouldn't happen if JWT filter ran)
                if (userId == null || userId.isBlank()) {
                    log.warn("No user ID in request headers, skipping rate limiting");
                    return chain.filter(exchange);
                }

                // 2. Get user's bucket
                Bucket bucket = resolveBucket(userId);

                // 3. Check if bucket has tokens
                if (bucket.tryConsume(1)) {
                    // Token available, allow request
                    log.debug("Rate limit OK for user {}: remaining requests", userId);
                    return chain.filter(exchange);

                } else {
                    // No tokens, rate limit exceeded
                    log.warn("Rate limit exceeded for user {}", userId);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add("Retry-After", "60");
                    return exchange.getResponse().setComplete();
                }

            } catch (Exception e) {
                log.error("Error in rate limiting filter", e);
                // On error, allow the request (fail open, not fail closed)
                return chain.filter(exchange);
            }
        };
    }

    // Configuration class
    public static class Config {
    }
}
