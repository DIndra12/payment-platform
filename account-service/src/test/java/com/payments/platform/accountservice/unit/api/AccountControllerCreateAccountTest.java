package com.payments.platform.accountservice.unit.api;

import com.payments.platform.accountservice.api.AccountController;
import com.payments.platform.accountservice.api.AccountResponse;
import com.payments.platform.accountservice.ledger.Account;
import com.payments.platform.accountservice.ledger.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountControllerCreateAccountTest {

    private AccountService accountService;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        controller = new AccountController(accountService);
    }

    @Test
    void createsNewAccountWith201() {
        UUID id = UUID.randomUUID();
        when(accountService.findAccount(id)).thenReturn(Optional.empty());
        when(accountService.createAccount(id)).thenReturn(
                Account.builder().id(id).ownerName("Account-" + id).balance(BigDecimal.ZERO).build());

        ResponseEntity<AccountResponse> response = controller.createAccount(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(accountService).createAccount(id);
    }

    @Test
    void returnsExistingAccountWith200AndDoesNotRecreateIt() {
        UUID id = UUID.randomUUID();
        when(accountService.findAccount(id)).thenReturn(Optional.of(
                Account.builder().id(id).ownerName("John Doe").balance(new BigDecimal("10000.00")).build()));

        ResponseEntity<AccountResponse> response = controller.createAccount(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountService, never()).createAccount(any());
    }
}
