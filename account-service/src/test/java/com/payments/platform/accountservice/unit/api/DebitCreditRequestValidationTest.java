package com.payments.platform.accountservice.unit.api;

import com.payments.platform.accountservice.api.CreditRequest;
import com.payments.platform.accountservice.api.DebitRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DebitRequest and CreditRequest Validation Tests")
class DebitCreditRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // DebitRequest Tests
    @Test
    @DisplayName("Valid debit request should pass validation")
    void validDebitRequest_shouldPass() {
        DebitRequest request = new DebitRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null debit amount should fail validation")
    void nullDebitAmount_shouldFail() {
        DebitRequest request = new DebitRequest();
        request.setAmount(null);
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Zero debit amount should fail validation")
    void zeroDebitAmount_shouldFail() {
        DebitRequest request = new DebitRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative debit amount should fail validation")
    void negativeDebitAmount_shouldFail() {
        DebitRequest request = new DebitRequest();
        request.setAmount(BigDecimal.valueOf(-50.00));
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Null debit reference ID should fail validation")
    void nullDebitReferenceId_shouldFail() {
        DebitRequest request = new DebitRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setReferenceId(null);

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("referenceId"));
    }

    // CreditRequest Tests
    @Test
    @DisplayName("Valid credit request should pass validation")
    void validCreditRequest_shouldPass() {
        CreditRequest request = new CreditRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<CreditRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null credit amount should fail validation")
    void nullCreditAmount_shouldFail() {
        CreditRequest request = new CreditRequest();
        request.setAmount(null);
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<CreditRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Zero credit amount should fail validation")
    void zeroCreditAmount_shouldFail() {
        CreditRequest request = new CreditRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<CreditRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative credit amount should fail validation")
    void negativeCreditAmount_shouldFail() {
        CreditRequest request = new CreditRequest();
        request.setAmount(BigDecimal.valueOf(-75.00));
        request.setReferenceId(UUID.randomUUID());

        Set<ConstraintViolation<CreditRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Null credit reference ID should fail validation")
    void nullCreditReferenceId_shouldFail() {
        CreditRequest request = new CreditRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setReferenceId(null);

        Set<ConstraintViolation<CreditRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("referenceId"));
    }
}
