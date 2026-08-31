package com.payments.platform.transactionhistoryservice.query;

import com.payments.platform.transactionhistoryservice.api.TransactionDirection;
import com.payments.platform.transactionhistoryservice.api.TransactionPageResponse;
import com.payments.platform.transactionhistoryservice.api.TransactionResponse;
import com.payments.platform.transactionhistoryservice.api.TransactionSummaryResponse;
import com.payments.platform.transactionhistoryservice.exception.TransactionNotFoundException;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistoryRepository;
import com.payments.platform.transactionhistoryservice.persistence.TransactionHistorySpecifications;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The read side. Strictly read-only — nothing here mutates the projection.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class TransactionHistoryQueryService {

    private final TransactionHistoryRepository repository;

    public TransactionHistoryQueryService(TransactionHistoryRepository repository) {
        this.repository = repository;
    }

    public TransactionPageResponse findAccountHistory(UUID accountId,
                                                     TransactionStatus status,
                                                     TransactionDirection direction,
                                                     LocalDateTime from,
                                                     LocalDateTime to,
                                                     int page,
                                                     int size) {

        Specification<TransactionHistory> spec =
                Specification.where(TransactionHistorySpecifications.involvesAccount(accountId))
                        .and(TransactionHistorySpecifications.hasDirection(accountId, direction))
                        .and(TransactionHistorySpecifications.hasStatus(status))
                        .and(TransactionHistorySpecifications.createdFrom(from))
                        .and(TransactionHistorySpecifications.createdTo(to));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionHistory> results = repository.findAll(spec, pageable);

        List<TransactionResponse> transactions = results.getContent().stream()
                .map(row -> toResponse(row, accountId))
                .toList();

        return TransactionPageResponse.builder()
                .accountId(accountId)
                .page(results.getNumber())
                .size(results.getSize())
                .totalElements(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .transactions(transactions)
                .build();
    }

    /**
     * @param perspectiveAccountId optional; when provided, {@code direction} and
     *                             {@code counterpartyAccountId} are resolved
     *                             relative to it
     */
    public TransactionResponse findByPaymentId(UUID paymentId, UUID perspectiveAccountId) {
        return repository.findByPaymentId(paymentId)
                .map(row -> toResponse(row, perspectiveAccountId))
                .orElseThrow(() -> new TransactionNotFoundException(paymentId));
    }

    public TransactionSummaryResponse summarize(UUID accountId) {
        Map<TransactionStatus, Long> countByStatus = new EnumMap<>(TransactionStatus.class);
        // Seed every status so consumers get a stable shape rather than absent keys.
        for (TransactionStatus status : TransactionStatus.values()) {
            countByStatus.put(status, 0L);
        }
        for (Object[] statusCount : repository.countByStatusForAccount(accountId)) {
            countByStatus.put((TransactionStatus) statusCount[0], (Long) statusCount[1]);
        }

        return TransactionSummaryResponse.builder()
                .accountId(accountId)
                .totalTransactions(repository.countForAccount(accountId))
                .totalOutgoing(orZero(repository.sumOutgoing(accountId, TransactionStatus.COMPLETED)))
                .totalIncoming(orZero(repository.sumIncoming(accountId, TransactionStatus.COMPLETED)))
                .countByStatus(countByStatus)
                .build();
    }

    /**
     * Resolves the account-relative view.
     *
     * <p>If the queried account is both payer and payee (a self-payment), DEBIT
     * wins — an arbitrary but deterministic choice. If neither leg matches, or
     * the perspective account is absent, direction and counterparty stay null
     * rather than being guessed.
     */
    TransactionResponse toResponse(TransactionHistory row, UUID perspectiveAccountId) {
        TransactionDirection direction = null;
        UUID counterparty = null;

        if (perspectiveAccountId != null) {
            if (Objects.equals(row.getPayerAccountId(), perspectiveAccountId)) {
                direction = TransactionDirection.DEBIT;
                counterparty = row.getPayeeAccountId();
            } else if (Objects.equals(row.getPayeeAccountId(), perspectiveAccountId)) {
                direction = TransactionDirection.CREDIT;
                counterparty = row.getPayerAccountId();
            }
        }

        return TransactionResponse.builder()
                .paymentId(row.getPaymentId())
                .direction(direction)
                .counterpartyAccountId(counterparty)
                .payerAccountId(row.getPayerAccountId())
                .payeeAccountId(row.getPayeeAccountId())
                .amount(row.getAmount())
                .currency(row.getCurrency())
                .status(row.getStatus())
                .failureReason(row.getFailureReason())
                .debitedAt(row.getDebitedAt())
                .creditedAt(row.getCreditedAt())
                .completedAt(row.getCompletedAt())
                .debitLedgerId(row.getDebitLedgerId())
                .creditLedgerId(row.getCreditLedgerId())
                .traceId(row.getTraceId())
                .eventsSeen(row.getEventsSeen())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
