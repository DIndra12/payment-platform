package com.payments.platform.paymentservice.unit.api;

import com.payments.platform.paymentservice.api.PaymentRequest;
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

@DisplayName("PaymentRequest Validation Tests")
class PaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid payment request should pass validation")
    void validPaymentRequest_shouldPass() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Null payer account ID should fail validation")
    void nullPayerAccountId_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(null);
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("payerAccountId"));
    }

    @Test
    @DisplayName("Null payee account ID should fail validation")
    void nullPayeeAccountId_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(null);
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("payeeAccountId"));
    }

    @Test
    @DisplayName("Null amount should fail validation")
    void nullAmount_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(null);
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Zero amount should fail validation")
    void zeroAmount_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Negative amount should fail validation")
    void negativeAmount_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(-100.00));
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Amount less than 0.01 should fail validation")
    void amountLessThanMinimum_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(0.001));
        request.setCurrency("USD");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    @DisplayName("Blank currency should fail validation")
    void blankCurrency_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("");

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);  // @NotBlank and @Pattern both trigger
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Null currency should fail validation")
    void nullCurrency_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency(null);

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Invalid currency code (not 3 uppercase letters) should fail validation")
    void invalidCurrencyFormat_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("US");  // Only 2 letters

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Lowercase currency code should fail validation")
    void lowercaseCurrency_shouldFail() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(UUID.randomUUID());
        request.setPayeeAccountId(UUID.randomUUID());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("usd");  // Lowercase

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    @DisplayName("Valid currencies should pass validation")
    void validCurrencies_shouldPass() {
        String[] validCurrencies = {"USD", "EUR", "INR", "GBP", "JPY", "CAD", "AUD"};

        for (String currency : validCurrencies) {
            PaymentRequest request = new PaymentRequest();
            request.setPayerAccountId(UUID.randomUUID());
            request.setPayeeAccountId(UUID.randomUUID());
            request.setAmount(BigDecimal.valueOf(100.00));
            request.setCurrency(currency);

            Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
            assertThat(violations).as("Currency %s should be valid", currency).isEmpty();
        }
    }

    @Test
    @DisplayName("Multiple validation errors should all be reported")
    void multipleValidationErrors_shouldReportAll() {
        PaymentRequest request = new PaymentRequest();
        request.setPayerAccountId(null);
        request.setPayeeAccountId(null);
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("US");  // Invalid: only 2 letters

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(4);
    }
}
