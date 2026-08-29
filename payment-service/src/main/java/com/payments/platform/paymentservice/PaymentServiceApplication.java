package com.payments.platform.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.payments.platform.paymentservice.config.ExternalServicesProperties;

/**
 * Payment Service — the saga orchestrator.
 *
 * <p>{@code @EnableScheduling} is required for
 * {@link com.payments.platform.paymentservice.outbox.OutboxPublisher} to run. Its
 * {@code @Scheduled} poller is what actually moves outbox rows onto Kafka, so
 * without this annotation the service commits outbox rows and never publishes
 * them — every downstream consumer stays silent while the sync path looks
 * perfectly healthy.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties(ExternalServicesProperties.class)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
