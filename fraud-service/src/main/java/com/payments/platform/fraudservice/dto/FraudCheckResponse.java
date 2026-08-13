package com.payments.platform.fraudservice.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class FraudCheckResponse {
    private int riskScore;
    private String decision;
    private List<String> reasons;
}