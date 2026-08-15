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

    @Autowired
    DataSource dataSource;

    @Test
    void outboxPayloadIsJsonb() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PreparedStatement ps = c.prepareStatement(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='outbox_event' AND column_name='payload'");
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            String dataType = rs.getString(1);
            assertThat(dataType).isEqualToIgnoringCase("jsonb");
        }
    }
}
