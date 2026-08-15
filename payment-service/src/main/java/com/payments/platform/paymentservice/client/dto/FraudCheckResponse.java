package com.payments.platform.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from Fraud Service /risk/evaluate endpoint.
 * Maps to the Fraud Service's actual response contract, not an invented schema.
 */
public record FraudCheckResponse(
    @JsonProperty("riskScore")
    int riskScore,

    @JsonProperty("decision")
    RiskDecision decision,

    @JsonProperty("reasons")
    List<String> reasons
) {}
