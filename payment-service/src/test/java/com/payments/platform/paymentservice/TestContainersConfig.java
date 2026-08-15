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
        // Start the container at class load so DynamicPropertySource can expose its URL/credentials.
        POSTGRES_CONTAINER.start();
    }


    private static final java.util.concurrent.atomic.AtomicBoolean MIGRATED = new java.util.concurrent.atomic.AtomicBoolean(false);

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
        // Run Flyway programmatically against the container so migrations in src/main/resources/db/migration are applied for tests.
        // Do this in beforeAll (not in static init) to avoid class-initializer failures during test class loading.
        // Run migrations once per JVM to avoid re-applying scripts for multiple test classes that reuse the same container.
        if (MIGRATED.compareAndSet(false, true)) {
            try {
                String jdbcUrl = POSTGRES_CONTAINER.getJdbcUrl();
                // strip query params if present (some JDBC URLs include loggerLevel), Flyway needs plain URL for detection
                int q = jdbcUrl.indexOf('?');
                if (q > 0) jdbcUrl = jdbcUrl.substring(0, q);
                System.out.println("[TestContainersConfig] Using JDBC URL for migrations: " + jdbcUrl);
                // Ensure driver is registered
                try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException ignore) { }
                // Apply SQL migration files directly using Spring's ScriptUtils.
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword())) {
                    org.springframework.core.io.ClassPathResource r1 = new org.springframework.core.io.ClassPathResource("db/migration/V1__init_payment_schema.sql");
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn, r1);
                    org.springframework.core.io.ClassPathResource r2 = new org.springframework.core.io.ClassPathResource("db/migration/V2__create_outbox.sql");
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn, r2);
                    org.springframework.core.io.ClassPathResource r3 = new org.springframework.core.io.ClassPathResource("db/migration/V3__migrate_payload_to_jsonb.sql");
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn, r3);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to run Flyway migrations against Testcontainers Postgres", e);
            }
        } else {
            System.out.println("[TestContainersConfig] Migrations already applied in this JVM; skipping.");
        }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Flyway migrations against Testcontainers Postgres", e);
        }
    }
}
