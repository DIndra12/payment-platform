package com.payments.platform.accountservice.dto;

import com.payments.platform.accountservice.entity.LedgerEntry;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class LedgerResponse {
    private UUID id;
    private UUID accountId;
    private BigDecimal amount;
    private String entryType;
    private UUID referenceId;
    private LocalDateTime createdAt;

    public static LedgerResponse from(LedgerEntry entry) {
        return LedgerResponse.builder()
                .id(entry.getId())
                .accountId(entry.getAccountId())
                .amount(entry.getAmount())
                .entryType(entry.getEntryType().name())
                .referenceId(entry.getReferenceId())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}