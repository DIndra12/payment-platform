package com.payments.platform.transactionhistoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Transaction History Service — the CQRS read side of the payments platform.
 *
 * <p>Pure Kafka consumer plus a query API. No Feign clients, no synchronous
 * dependency on any other service: if this service is down, payments still
 * complete and history simply goes stale until it catches up from its committed
 * offsets.
 */
@SpringBootApplication
@EnableKafka
public class TransactionHistoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionHistoryServiceApplication.class, args);
    }
}
