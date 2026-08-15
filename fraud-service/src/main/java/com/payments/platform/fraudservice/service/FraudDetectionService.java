package com.payments.platform.fraudservice.service;

import com.payments.platform.fraudservice.config.FraudDetectionProperties;
import com.payments.platform.fraudservice.dto.FraudCheckRequest;
import com.payments.platform.fraudservice.dto.FraudCheckResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FraudDetectionService {

    private final FraudDetectionProperties properties;

    public FraudDetectionService(FraudDetectionProperties properties) {
        this.properties = properties;
    }

    public FraudCheckResponse evaluate(FraudCheckRequest request) {
        List<String> reasons = new ArrayList<>();
        int score = 10; // base low risk score

        if (request.getAmount().compareTo(properties.highValueThreshold()) > 0) {
            score += 80;
            reasons.add("Transaction amount exceeds high-value threshold: " + properties.highValueThreshold());
        }

        String decision = (score >= properties.riskScoreThreshold()) ? "REJECT" : "APPROVE";

        return FraudCheckResponse.builder()
                .riskScore(score)
                .decision(decision)
                .reasons(reasons)
                .build();
    }
}