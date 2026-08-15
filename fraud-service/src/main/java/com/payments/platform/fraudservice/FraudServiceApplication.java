package com.payments.platform.fraudservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.payments.platform.fraudservice.config.FraudDetectionProperties;

@SpringBootApplication
@EnableConfigurationProperties(FraudDetectionProperties.class)
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }

}
