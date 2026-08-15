package com.payments.platform.fraudservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Fraud detection thresholds from application.yml.
 * Single source of truth for risk scoring logic.
 */
@ConfigurationProperties(prefix = "fraud.detection")
@Validated
public record FraudDetectionProperties(
    @NotNull(message = "fraud.detection.risk-score-threshold is required")
    @Min(0) @Max(100)
    Integer riskScoreThreshold,

    @NotNull(message = "fraud.detection.high-value-threshold is required")
    @Min(value = 0, message = "high-value-threshold must be >= 0")
    BigDecimal highValueThreshold
) {
    public FraudDetectionProperties {
        // Validation annotations are checked by @Validated
    }
}
