package com.payments.platform.paymentservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client configuration.
 * Enables FAIL_ON_UNKNOWN_PROPERTIES so contract drift is detected immediately
 * instead of silently using default values.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public ObjectMapper feignObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Fail fast if the response contains unexpected fields — indicates contract drift
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        return mapper;
    }
}
