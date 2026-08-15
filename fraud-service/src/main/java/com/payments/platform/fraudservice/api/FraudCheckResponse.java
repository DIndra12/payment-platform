package com.payments.platform.fraudservice.api;

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
