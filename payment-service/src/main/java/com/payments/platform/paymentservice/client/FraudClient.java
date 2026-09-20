package com.payments.platform.paymentservice.client;

import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckResponse;
import com.payments.platform.paymentservice.client.dto.RiskDecision;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Fraud Service Feign Client
 *
 * Resilience4j annotations add fault tolerance:
 * 1. @CircuitBreaker - Stop calling failing service, fail fast
 * 2. @Retry - Automatically retry on transient failures
 * 3. @TimeLimiter - Don't wait forever for response (2 seconds max)
 *
 * These prevent cascading failures when fraud service is slow/down
 */
@FeignClient(name = "fraud-service", url = "${external-services.fraud-service-url}")
public interface FraudClient {

    /**
     * Evaluate payment risk
     *
     * Resilience applied:
     * - CircuitBreaker: Open after 50% failure rate, half-open after 30s
     * - Retry: Retry up to 3 times with exponential backoff
     *
     * Note: @TimeLimiter requires CompletableFuture; for sync methods,
     * timeout is handled at the Feign/HTTP client level
     *
     * @param request Payment details to assess
     * @return Risk assessment
     */
    @PostMapping("/api/v1/risk/evaluate")
    @CircuitBreaker(
        name = "fraud-service",
        fallbackMethod = "evaluateRiskFallback"
    )
    @Retry(name = "fraud-service")
    FraudCheckResponse evaluateRisk(@RequestBody FraudCheckRequest request);

    /**
     * Fallback for fraud evaluation when service is down
     *
     * This is called when:
     * - Circuit breaker is OPEN (service unhealthy)
     * - All retries failed
     * - Timeout exceeded
     *
     * In production: Default to APPROVE to allow payments through
     * (fraud service is advisory, not blocking)
     */
    default FraudCheckResponse evaluateRiskFallback(
        FraudCheckRequest request,
        Exception ex
    ) {
        // When fraud service is unavailable, approve payment with low risk score
        // This prevents fraud service outages from blocking all payments
        return new FraudCheckResponse(
            0,  // riskScore = 0 (lowest risk)
            RiskDecision.APPROVE,
            List.of("Fraud service unavailable, defaulting to APPROVE")
        );
    }
}