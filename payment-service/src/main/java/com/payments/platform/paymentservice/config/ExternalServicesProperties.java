package com.payments.platform.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * External service URLs for Payment Service to call.
 * Provides validation at startup: if a required URL is missing or misconfigured,
 * the application fails fast with a clear error instead of silently using a malformed placeholder.
 */
@ConfigurationProperties(prefix = "external-services")
@Validated
public record ExternalServicesProperties(
    @NotBlank(message = "external-services.account-service.url is required")
    String accountServiceUrl,

    @NotBlank(message = "external-services.fraud-service.url is required")
    String fraudServiceUrl
) {
    public ExternalServicesProperties {
        // Validation annotations are checked by @Validated
    }

    public String getAccountServiceUrl() {
        return accountServiceUrl;
    }

    public String getFraudServiceUrl() {
        return fraudServiceUrl;
    }
}
