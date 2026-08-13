package com.payments.platform.accountservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"reference_id", "entry_type"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}