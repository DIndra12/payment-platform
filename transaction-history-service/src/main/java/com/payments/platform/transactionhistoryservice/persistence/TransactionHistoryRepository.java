package com.payments.platform.transactionhistoryservice.persistence;

import com.payments.platform.transactionhistoryservice.persistence.entity.TransactionHistory;
import com.payments.platform.transactionhistoryservice.projection.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model access.
 *
 * <p>Filtered account queries go through {@link JpaSpecificationExecutor} rather
 * than a JPQL query with {@code :param IS NULL} guards — Hibernate cannot always
 * infer the SQL type of a null enum/timestamp parameter, and the Criteria API
 * sidesteps it by only building the predicates actually requested.
 */
@Repository
public interface TransactionHistoryRepository
        extends JpaRepository<TransactionHistory, UUID>, JpaSpecificationExecutor<TransactionHistory> {

    Optional<TransactionHistory> findByPaymentId(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    /**
     * Total value this account sent, for a given status. Only the payer leg
     * counts as money out.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM TransactionHistory t
            WHERE t.payerAccountId = :accountId
              AND t.status = :status
            """)
    BigDecimal sumOutgoing(@Param("accountId") UUID accountId,
                           @Param("status") TransactionStatus status);

    /** Total value this account received, for a given status. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM TransactionHistory t
            WHERE t.payeeAccountId = :accountId
              AND t.status = :status
            """)
    BigDecimal sumIncoming(@Param("accountId") UUID accountId,
                           @Param("status") TransactionStatus status);

    /** Per-status row counts for an account, either leg. */
    @Query("""
            SELECT t.status, COUNT(t)
            FROM TransactionHistory t
            WHERE t.payerAccountId = :accountId OR t.payeeAccountId = :accountId
            GROUP BY t.status
            """)
    List<Object[]> countByStatusForAccount(@Param("accountId") UUID accountId);

    @Query("""
            SELECT COUNT(t)
            FROM TransactionHistory t
            WHERE t.payerAccountId = :accountId OR t.payeeAccountId = :accountId
            """)
    long countForAccount(@Param("accountId") UUID accountId);
}
