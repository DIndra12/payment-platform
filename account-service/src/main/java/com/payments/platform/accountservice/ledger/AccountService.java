package com.payments.platform.accountservice.ledger;

import com.payments.platform.accountservice.api.CreditRequest;
import com.payments.platform.accountservice.api.DebitRequest;
import com.payments.platform.accountservice.api.LedgerResponse;
import com.payments.platform.accountservice.persistence.AccountRepository;
import com.payments.platform.accountservice.persistence.LedgerEntryRepository;
import com.payments.platform.accountservice.exception.AccountNotFoundException;
import com.payments.platform.accountservice.exception.InsufficientBalanceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerResponse debit(UUID accountId, DebitRequest request) {
        // Idempotency Check: return existing ledger entry if already processed
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReferenceIdAndEntryType(
                request.getReferenceId(), EntryType.DEBIT);
        if (existing.isPresent()) {
            return LedgerResponse.from(existing.get());
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient funds for account " + accountId);
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(request.getAmount())
                .entryType(EntryType.DEBIT)
                .referenceId(request.getReferenceId())
                .createdAt(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(entry);
        return LedgerResponse.from(entry);
    }

    @Transactional
    public LedgerResponse credit(UUID accountId, CreditRequest request) {
        // Idempotency Check
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReferenceIdAndEntryType(
                request.getReferenceId(), EntryType.CREDIT);
        if (existing.isPresent()) {
            return LedgerResponse.from(existing.get());
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(request.getAmount())
                .entryType(EntryType.CREDIT)
                .referenceId(request.getReferenceId())
                .createdAt(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(entry);
        return LedgerResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }
}
