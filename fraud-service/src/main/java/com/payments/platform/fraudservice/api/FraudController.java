package com.payments.platform.fraudservice.api;

import com.payments.platform.fraudservice.detection.FraudDetectionService;
import jakarta.validation.Valid;
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
    public FraudCheckResponse evaluateRisk(@Valid @RequestBody FraudCheckRequest request) {
        return fraudDetectionService.evaluate(request);
    }
}
