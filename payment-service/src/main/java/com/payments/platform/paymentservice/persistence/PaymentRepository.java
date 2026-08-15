package com.payments.platform.paymentservice.persistence;

import com.payments.platform.paymentservice.persistence.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    /**
     * Find an existing payment by idempotency key.
     * Used to detect and safely retry payment initiation.
     *
     * @param idempotencyKey unique identifier from request header
     * @return existing payment if found
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
