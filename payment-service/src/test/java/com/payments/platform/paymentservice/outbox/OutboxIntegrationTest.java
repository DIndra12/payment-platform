package com.payments.platform.paymentservice.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(com.payments.platform.paymentservice.TestContainersConfig.class)
class OutboxIntegrationTest {

    // Use the TestContainersConfig container directly to avoid the test profile's jdbc:tc driver creating a separate container
    // DataSource is left available for other tests, but this integration test uses the container URL directly.


    @Test
    void outboxPayloadIsJsonb() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(
                com.payments.platform.paymentservice.TestContainersConfig.POSTGRES_CONTAINER.getJdbcUrl(),
                com.payments.platform.paymentservice.TestContainersConfig.POSTGRES_CONTAINER.getUsername(),
                com.payments.platform.paymentservice.TestContainersConfig.POSTGRES_CONTAINER.getPassword())) {
            String sql = "SELECT table_schema, table_name, column_name, data_type, udt_name FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name='outbox_event' AND column_name='payload'";
            PreparedStatement ps = c.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            System.out.println("[OutboxIntegrationTest] Running: " + sql);
            int rows = 0;
            java.sql.ResultSetMetaData md = rs.getMetaData();
            String dataType = null;
            String udtName = null;
            while (rs.next()) {
                rows++;
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    System.out.println("[OutboxIntegrationTest] col=" + md.getColumnName(i) + " val='" + rs.getString(i) + "'");
                }
                dataType = rs.getString("data_type");
                udtName = rs.getString("udt_name");
            }
            System.out.println("[OutboxIntegrationTest] rows=" + rows);
            assertThat(rows).isGreaterThan(0);
            System.out.println("[OutboxIntegrationTest] payload.data_type = '" + dataType + "' udt='" + udtName + "'");
            assertThat(dataType).isEqualToIgnoringCase("jsonb");
        }
    }
}
