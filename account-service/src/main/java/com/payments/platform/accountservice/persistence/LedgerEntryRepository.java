package com.payments.platform.accountservice.persistence;

import com.payments.platform.accountservice.ledger.EntryType;
import com.payments.platform.accountservice.ledger.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Optional<LedgerEntry> findByReferenceIdAndEntryType(UUID referenceId, EntryType entryType);
}
