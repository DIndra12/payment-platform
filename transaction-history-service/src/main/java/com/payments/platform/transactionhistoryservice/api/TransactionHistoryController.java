package com.payments.platform.transactionhistoryservice.api;

import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import com.payments.platform.transactionhistoryservice.query.TransactionHistoryQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Query API over the CQRS read model.
 *
 * <p><strong>Security:</strong> these endpoints currently have no authorization —
 * any caller can read any account's full transaction history. That is a
 * deliberate, documented gap, not an oversight: the platform's intended control
 * (design doc 2.10) is JWT validation at the API Gateway with the token subject
 * checked against the requested {@code accountId}, and the Gateway does not exist
 * yet. Until it does, this service must be treated as internal-only and must not
 * be exposed publicly. Kafka ACLs protect the ingest side; they do nothing here.
 *
 * <p><strong>Consistency:</strong> the read model is eventually consistent. A
 * payment that just returned {@code 202} from payment-service may not appear here
 * for a few seconds (outbox poll interval plus consumer lag). Clients that need
 * read-your-writes should call {@code GET /api/v1/payments/{id}} on
 * payment-service instead.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionHistoryQueryService queryService;

    public TransactionHistoryController(TransactionHistoryQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Paged history for an account, newest first.
     *
     * @param direction narrows to one leg: DEBIT = account is payer, CREDIT = account is payee
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<TransactionPageResponse> getAccountHistory(
            @PathVariable UUID accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        log.debug("Account history query: account={} status={} direction={} from={} to={} page={} size={}",
                accountId, status, direction, from, to, page, size);

        return ResponseEntity.ok(
                queryService.findAccountHistory(accountId, status, direction, from, to, page, size));
    }

    /**
     * A single stitched transaction.
     *
     * @param accountId optional perspective account; when supplied, the response's
     *                  {@code direction} and {@code counterpartyAccountId} are
     *                  resolved relative to it
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID paymentId,
            @RequestParam(required = false) UUID accountId) {

        return ResponseEntity.ok(queryService.findByPaymentId(paymentId, accountId));
    }

    /** Aggregates for an account: counts per status and total value in/out. */
    @GetMapping("/account/{accountId}/summary")
    public ResponseEntity<TransactionSummaryResponse> getAccountSummary(@PathVariable UUID accountId) {
        return ResponseEntity.ok(queryService.summarize(accountId));
    }
}
