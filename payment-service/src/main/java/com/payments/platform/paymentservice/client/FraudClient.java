package com.payments.platform.paymentservice.client;

import com.payments.platform.paymentservice.client.dto.FraudCheckRequest;
import com.payments.platform.paymentservice.client.dto.FraudCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "fraud-service", url = "${external-services.fraud-service.url}")
public interface FraudClient {
    @PostMapping("/api/v1/risk/evaluate")
    FraudCheckResponse evaluateRisk(@RequestBody FraudCheckRequest request);
}