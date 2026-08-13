package com.payments.platform.paymentservice.client;

import com.payments.platform.paymentservice.client.dto.DebitRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-service", url = "${external-services.account-service.url}")
public interface AccountClient {
    @PostMapping("/api/v1/accounts/{accountId}/debit")
    void debitAccount(@PathVariable("accountId") String accountId, @RequestBody DebitRequest request);
}