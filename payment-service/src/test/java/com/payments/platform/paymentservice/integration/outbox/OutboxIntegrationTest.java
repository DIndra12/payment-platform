package com.payments.platform.paymentservice.integration.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void outboxPayloadIsJsonb() throws Exception {
        try (Connection c = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {
            String sql = "SELECT table_schema, table_name, column_name, data_type, udt_name FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name='outbox_event' AND column_name='payload'";
            PreparedStatement ps = c.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            int rows = 0;
            String dataType = null;
            while (rs.next()) {
                rows++;
                dataType = rs.getString("data_type");
            }
            assertThat(rows).isGreaterThan(0);
            assertThat(dataType).isEqualToIgnoringCase("jsonb");
        }
    }
}
