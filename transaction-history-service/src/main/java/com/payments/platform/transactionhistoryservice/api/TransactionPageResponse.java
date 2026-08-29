package com.payments.platform.transactionhistoryservice.api;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** A page of account history. */
@Getter
@Builder
public class TransactionPageResponse {

    private final UUID accountId;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final List<TransactionResponse> transactions;
}
