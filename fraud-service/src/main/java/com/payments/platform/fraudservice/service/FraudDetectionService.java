package com.payments.platform.fraudservice.service;

import com.payments.platform.fraudservice.dto.FraudCheckRequest;
import com.payments.platform.fraudservice.dto.FraudCheckResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FraudDetectionService {

    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("50000.00");

    public FraudCheckResponse evaluate(FraudCheckRequest request) {
        List<String> reasons = new ArrayList<>();
        int score = 10; // base low risk score

        if (request.getAmount().compareTo(HIGH_RISK_THRESHOLD) > 0) {
            score += 80;
            reasons.add("Transaction amount exceeds high risk limit: " + HIGH_RISK_THRESHOLD);
        }

        String decision = (score >= 70) ? "REJECT" : "APPROVE";

        return FraudCheckResponse.builder()
                .riskScore(score)
                .decision(decision)
                .reasons(reasons)
                .build();
    }
}