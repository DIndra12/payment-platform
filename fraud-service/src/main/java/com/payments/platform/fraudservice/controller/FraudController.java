package com.payments.platform.fraudservice.controller;

import com.payments.platform.fraudservice.dto.FraudCheckRequest;
import com.payments.platform.fraudservice.dto.FraudCheckResponse;
import com.payments.platform.fraudservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    @PostMapping("/evaluate")
    public FraudCheckResponse evaluateRisk(@RequestBody FraudCheckRequest request) {
        return fraudDetectionService.evaluate(request);
    }
}