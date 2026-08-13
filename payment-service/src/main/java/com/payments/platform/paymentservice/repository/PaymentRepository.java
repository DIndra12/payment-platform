package com.payments.platform.paymentservice.repository;

import com.payments.platform.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}