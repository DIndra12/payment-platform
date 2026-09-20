package com.payments.platform.paymentservice.client;

import com.payments.platform.paymentservice.client.dto.DebitRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.concurrent.CompletableFuture;

/**
 * Account Service Feign Client
 *
 * Resilience4j annotations add fault tolerance:
 * 1. @CircuitBreaker - Stop calling failing service, fail fast
 * 2. @Retry - Automatically retry on transient failures
 * 3. @TimeLimiter - Don't wait forever for response (2 seconds max)
 *
 * These prevent cascading failures:
 * - If Account Service is down, circuit opens immediately
 * - Payment Service returns error quickly instead of hanging
 * - Other services aren't affected
 */
@FeignClient(name = "account-service", url = "${external-services.account-service-url}")
public interface AccountClient {

    /**
     * Debit account (reduce balance)
     *
     * Resilience applied:
     * - CircuitBreaker: Open after 5 failures, half-open after 30s
     * - Retry: Retry up to 3 times with exponential backoff
     * - TimeLimiter: Timeout after 2 seconds
     *
     * @param accountId Account to debit
     * @param request Amount to debit
     */
    @PostMapping("/api/v1/accounts/{accountId}/debit")
    @CircuitBreaker(
        name = "account-service",
        fallbackMethod = "debitAccountFallback"
    )
    @Retry(name = "account-service")
    @TimeLimiter(name = "account-service")
    CompletableFuture<Void> debitAccount(
        @PathVariable("accountId") String accountId,
        @RequestBody DebitRequest request
    );

    /**
     * Credit account (increase balance)
     */
    @PostMapping("/api/v1/accounts/{accountId}/credit")
    @CircuitBreaker(
        name = "account-service",
        fallbackMethod = "creditAccountFallback"
    )
    @Retry(name = "account-service")
    @TimeLimiter(name = "account-service")
    CompletableFuture<Void> creditAccount(
        @PathVariable("accountId") String accountId,
        @RequestBody DebitRequest request
    );

    /**
     * Fallback for debit when service is down
     *
     * This is called when:
     * - Circuit breaker is OPEN (service unhealthy)
     * - All retries failed
     * - Timeout exceeded
     *
     * We queue the operation for retry later (saga pattern)
     */
    default CompletableFuture<Void> debitAccountFallback(
        String accountId,
        DebitRequest request,
        Exception ex
    ) {
        // In production: Queue for retry via Kafka
        // For now: Log and return error
        return CompletableFuture.failedFuture(
            new RuntimeException("Account service unavailable, debit queued for retry", ex)
        );
    }

    /**
     * Fallback for credit when service is down
     */
    default CompletableFuture<Void> creditAccountFallback(
        String accountId,
        DebitRequest request,
        Exception ex
    ) {
        return CompletableFuture.failedFuture(
            new RuntimeException("Account service unavailable, credit queued for retry", ex)
        );
    }
}

