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

    // Do not auto-start the container. Start only when no external DB URL is provided (CI uses a service DB)

    private static final java.util.concurrent.atomic.AtomicBoolean MIGRATED = new java.util.concurrent.atomic.AtomicBoolean(false);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String extUrl = System.getenv("SPRING_DATASOURCE_URL");
        String extUser = System.getenv("SPRING_DATASOURCE_USERNAME");
        String extPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (extUrl != null && !extUrl.isBlank()) {
            // Use externally provided DB (CI service) — don't start Testcontainers
            registry.add("spring.datasource.url", () -> extUrl);
            registry.add("spring.datasource.username", () -> extUser == null ? "test" : extUser);
            registry.add("spring.datasource.password", () -> extPass == null ? "test" : extPass);
            registry.add("spring.flyway.url", () -> extUrl);
            registry.add("spring.flyway.user", () -> extUser == null ? "test" : extUser);
            registry.add("spring.flyway.password", () -> extPass == null ? "test" : extPass);
        } else {
            // No external DB provided — start Testcontainers now so DynamicPropertySource exposes a valid JDBC URL
            POSTGRES_CONTAINER.start();
            // set system properties immediately so Spring can pick them up during context bootstrap
            String url = POSTGRES_CONTAINER.getJdbcUrl();
            String user = POSTGRES_CONTAINER.getUsername();
            String pass = POSTGRES_CONTAINER.getPassword();
            System.setProperty("spring.datasource.url", url);
            System.setProperty("spring.datasource.username", user);
            System.setProperty("spring.datasource.password", pass);
            System.setProperty("spring.flyway.url", url);
            System.setProperty("spring.flyway.user", user);
            System.setProperty("spring.flyway.password", pass);
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> user);
            registry.add("spring.datasource.password", () -> pass);
            registry.add("spring.flyway.url", () -> url);
            registry.add("spring.flyway.user", () -> user);
            registry.add("spring.flyway.password", () -> pass);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Run Flyway programmatically against the container so migrations in src/main/resources/db/migration are applied for tests.
        // Do this in beforeAll (not in static init) to avoid class-initializer failures during test class loading.
        // Run migrations once per JVM to avoid re-applying scripts for multiple test classes that reuse the same container.
        if (MIGRATED.compareAndSet(false, true)) {
            try {
                String extUrl = System.getenv("SPRING_DATASOURCE_URL");
                String jdbcUrl;
                String user;
                String pass;
                if (extUrl != null && !extUrl.isBlank()) {
                    // Use external DB provided by CI (service) — apply migrations against it.
                    jdbcUrl = extUrl;
                    user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "test");
                    pass = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "test");
                    System.out.println("[TestContainersConfig] Using external JDBC URL for migrations: " + jdbcUrl);
                } else {
                    // Start Testcontainers and use its JDBC URL
                    POSTGRES_CONTAINER.start();
                    jdbcUrl = POSTGRES_CONTAINER.getJdbcUrl();
                    user = POSTGRES_CONTAINER.getUsername();
                    pass = POSTGRES_CONTAINER.getPassword();
                    System.out.println("[TestContainersConfig] Started Testcontainers and using JDBC URL for migrations: " + jdbcUrl);
                }

                // strip query params if present (some JDBC URLs include loggerLevel), ScriptUtils doesn't like them for some ops
                int q = jdbcUrl.indexOf('?');
                if (q > 0) jdbcUrl = jdbcUrl.substring(0, q);
                // Ensure driver is registered
                try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException ignore) { }
                // Apply SQL migration files directly using Spring's ScriptUtils.
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, user, pass)) {
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
    }
}
