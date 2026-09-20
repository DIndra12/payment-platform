package com.payments.platform.fraudservice.unit.api;

import com.payments.platform.fraudservice.api.FraudCheckRequest;
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

@DisplayName("FraudCheckRequest Validation Tests")
class FraudCheckRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid fraud check request should pass validation")
    void validFraudCheckRequest_shouldPass() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null payer account ID should fail validation")
    void nullPayerAccountId_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(null);
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("payerAccountId"));
    }

    @Test
    @DisplayName("Null amount should fail validation")
    void nullAmount_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(null);
        request.setCurrency("USD");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Zero amount should fail validation")
    void zeroAmount_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("USD");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative amount should fail validation")
    void negativeAmount_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(-100.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Blank currency should fail validation")
    void blankCurrency_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency("");

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);  // @NotBlank and @Pattern both trigger
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Null currency should fail validation")
    void nullCurrency_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency(null);

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Invalid currency format should fail validation")
    void invalidCurrencyFormat_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency("USDA");  // 4 letters instead of 3

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Lowercase currency code should fail validation")
    void lowercaseCurrency_shouldFail() {
        FraudCheckRequest request = new FraudCheckRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(500.00));
        request.setCurrency("eur");  // Lowercase

        Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Valid currency codes should pass validation")
    void validCurrencies_shouldPass() {
        String[] validCurrencies = {"USD", "EUR", "INR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY"};

        for (String currency : validCurrencies) {
            FraudCheckRequest request = new FraudCheckRequest();
            request.setPayerAccountId(UUID.randomUUID());
            request.setAmount(BigDecimal.valueOf(500.00));
            request.setCurrency(currency);

            Set<ConstraintViolation<FraudCheckRequest>> violations = validator.validate(request);
            assertThat(violations).as("Currency %s should be valid", currency).isEmpty();
        }
    }
}
