package com.payments.platform.transactionhistoryservice.persistence;

import com.payments.platform.transactionhistoryservice.api.TransactionDirection;
import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Criteria predicates for the account history query. Each factory returns null
 * when the filter is absent, which Spring Data treats as "no restriction", so
 * absent filters never reach SQL.
 */
public final class TransactionHistorySpecifications {

    private TransactionHistorySpecifications() {
    }

    /**
     * The account appears on either leg. Combined with {@link #hasDirection} this
     * narrows to one leg.
     */
    public static Specification<TransactionHistory> involvesAccount(UUID accountId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("payerAccountId"), accountId),
                cb.equal(root.get("payeeAccountId"), accountId)
        );
    }

    /**
     * DEBIT means the account is the payer (money out); CREDIT means it is the
     * payee (money in). Direction is therefore relative to the account being
     * queried — the stored row itself is account-neutral.
     */
    public static Specification<TransactionHistory> hasDirection(UUID accountId,
                                                                TransactionDirection direction) {
        if (direction == null) {
            return null;
        }
        return (root, query, cb) -> direction == TransactionDirection.DEBIT
                ? cb.equal(root.get("payerAccountId"), accountId)
                : cb.equal(root.get("payeeAccountId"), accountId);
    }

    public static Specification<TransactionHistory> hasStatus(TransactionStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<TransactionHistory> createdFrom(LocalDateTime from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<TransactionHistory> createdTo(LocalDateTime to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
