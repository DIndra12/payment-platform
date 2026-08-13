package com.payments.platform.accountservice.repository;

import com.payments.platform.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<com.payments.platform.accountservice.entity.Account, UUID> {}