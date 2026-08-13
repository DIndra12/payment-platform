package com.payments.platform.accountservice.repository;


import com.payments.platform.accountservice.entity.EntryType;
import com.payments.platform.accountservice.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Optional<LedgerEntry> findByReferenceIdAndEntryType(UUID referenceId, EntryType entryType);
}