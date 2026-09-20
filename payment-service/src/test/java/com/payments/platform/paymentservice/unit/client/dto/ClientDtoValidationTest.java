package com.payments.platform.paymentservice.unit.client.dto;

import com.payments.platform.paymentservice.client.dto.DebitRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Client DTO Validation Tests")
class ClientDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // DebitRequest (Client DTO) Tests
    @Test
    @DisplayName("Valid client debit request should pass validation")
    void validClientDebitRequest_shouldPass() {
        DebitRequest request = DebitRequest.builder()
            .amount(BigDecimal.valueOf(100.00))
            .referenceId("payment-12345")
            .build();

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null amount in client debit request should fail validation")
    void nullAmountInClientDebitRequest_shouldFail() {
        DebitRequest request = DebitRequest.builder()
            .amount(null)
            .referenceId("payment-12345")
            .build();

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative amount in client debit request should fail validation")
    void negativeAmountInClientDebitRequest_shouldFail() {
        DebitRequest request = DebitRequest.builder()
            .amount(BigDecimal.valueOf(-50.00))
            .referenceId("payment-12345")
            .build();

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Null reference ID in client debit request should fail validation")
    void nullReferenceIdInClientDebitRequest_shouldFail() {
        DebitRequest request = DebitRequest.builder()
            .amount(BigDecimal.valueOf(100.00))
            .referenceId(null)
            .build();

        Set<ConstraintViolation<DebitRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("referenceId"));
    }

    // FraudCheckRequest (Client DTO) Tests
    @Test
    @DisplayName("Valid client fraud check request should pass validation")
    void validClientFraudCheckRequest_shouldPass() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("550e8400-e29b-41d4-a716-446655440000")
            .amount(BigDecimal.valueOf(500.00))
            .currency("USD")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank payer account ID in client fraud check request should fail validation")
    void blankPayerAccountIdInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("")
            .amount(BigDecimal.valueOf(500.00))
            .currency("USD")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals("payerAccountId"));
    }

    @Test
    @DisplayName("Null payer account ID in client fraud check request should fail validation")
    void nullPayerAccountIdInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId(null)
            .amount(BigDecimal.valueOf(500.00))
            .currency("USD")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("payerAccountId"));
    }

    @Test
    @DisplayName("Null amount in client fraud check request should fail validation")
    void nullAmountInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("550e8400-e29b-41d4-a716-446655440000")
            .amount(null)
            .currency("USD")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative amount in client fraud check request should fail validation")
    void negativeAmountInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("550e8400-e29b-41d4-a716-446655440000")
            .amount(BigDecimal.valueOf(-100.00))
            .currency("USD")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Blank currency in client fraud check request should fail validation")
    void blankCurrencyInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("550e8400-e29b-41d4-a716-446655440000")
            .amount(BigDecimal.valueOf(500.00))
            .currency("")
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);  // @NotBlank and @Pattern both trigger
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Invalid currency format in client fraud check request should fail validation")
    void invalidCurrencyInClientFraudCheckRequest_shouldFail() {
        FraudCheckRequest request = FraudCheckRequest.builder()
            .payerAccountId("550e8400-e29b-41d4-a716-446655440000")
            .amount(BigDecimal.valueOf(500.00))
            .currency("us")  // Lowercase and too short
            .build();

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }
}
