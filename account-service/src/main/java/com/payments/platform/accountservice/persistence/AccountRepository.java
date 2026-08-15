package com.payments.platform.accountservice.persistence;

import com.payments.platform.accountservice.ledger.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {}
