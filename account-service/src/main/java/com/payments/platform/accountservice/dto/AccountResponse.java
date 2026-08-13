package com.payments.platform.accountservice.dto;

import com.payments.platform.accountservice.entity.Account;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
public class AccountResponse {
    private UUID id;
    private String ownerName;
    private BigDecimal balance;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .build();
    }
}