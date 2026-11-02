package com.payments.platform.accountservice.api;

import com.payments.platform.accountservice.ledger.Account;
import com.payments.platform.accountservice.ledger.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}")
    public ResponseEntity<AccountResponse> createAccount(@PathVariable UUID accountId) {
        // Idempotent: an existing account is returned unchanged with 200, a new one with 201.
        return accountService.findAccount(accountId)
                .map(existing -> ResponseEntity.ok(AccountResponse.from(existing)))
                .orElseGet(() -> ResponseEntity.status(201)
                        .body(AccountResponse.from(accountService.createAccount(accountId))));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<LedgerResponse> debit(
            @PathVariable UUID accountId,
            @Valid @RequestBody DebitRequest request) {
        return ResponseEntity.ok(accountService.debit(accountId, request));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<LedgerResponse> credit(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreditRequest request) {
        return ResponseEntity.ok(accountService.credit(accountId, request));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountResponse> getBalance(@PathVariable UUID accountId) {
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}
