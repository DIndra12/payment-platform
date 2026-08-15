package com.payments.platform.paymentservice;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
public class TestContainersConfig implements BeforeAllCallback {

    public static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:15.3-alpine")
            .withDatabaseName("paymentdb")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES_CONTAINER.start();
        // Apply SQL migrations directly for tests to avoid Flyway auto-detection issues in this environment
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword())) {
            org.springframework.core.io.ClassPathResource r1 = new org.springframework.core.io.ClassPathResource("db/migration/V1__init_payment_schema.sql");
            org.springframework.core.io.ClassPathResource r2 = new org.springframework.core.io.ClassPathResource("db/migration/V2__create_outbox.sql");
            org.springframework.core.io.ClassPathResource r3 = new org.springframework.core.io.ClassPathResource("db/migration/V3__migrate_payload_to_jsonb.sql");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c, r1);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c, r2);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c, r3);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply migration scripts to Testcontainers Postgres", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.flyway.password", POSTGRES_CONTAINER::getPassword);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // no-op; ensures JUnit extension is loaded when referenced
    }
}
