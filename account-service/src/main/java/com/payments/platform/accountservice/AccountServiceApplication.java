package com.payments.platform.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Account Service — accounts, ledger, and the outbox that publishes ledger
 * movements as {@code account.debited} / {@code account.credited}.
 *
 * <p>The explicit {@code @EnableJpaRepositories} / {@code @EntityScan} packages
 * must list the outbox package too; without it the outbox entity and repository
 * are invisible to Spring Data even though they sit under the same root package.
 *
 * <p>{@code @EnableScheduling} is what actually runs
 * {@link com.payments.platform.accountservice.outbox.OutboxPublisher}.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories({
        "com.payments.platform.accountservice.persistence",
        "com.payments.platform.accountservice.outbox"
})
@EntityScan({
        "com.payments.platform.accountservice.ledger",
        "com.payments.platform.accountservice.outbox"
})
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

}
