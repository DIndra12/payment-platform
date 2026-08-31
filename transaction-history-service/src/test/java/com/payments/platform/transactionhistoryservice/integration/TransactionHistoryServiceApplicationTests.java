package com.payments.platform.transactionhistoryservice.integration;

import com.payments.platform.transactionhistoryservice.projection.TransactionProjector;
import com.payments.platform.transactionhistoryservice.query.TransactionHistoryQueryService;
import com.payments.platform.transactionhistoryservice.support.KafkaPostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context load test. Cheap but valuable here: it proves the Flyway migration and
 * the JPA entities agree (ddl-auto is {@code validate}), and that all four
 * per-type Kafka container factories wire up without a bean clash.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionHistoryServiceApplicationTests extends KafkaPostgresTestBase {

    @Autowired
    private TransactionProjector projector;

    @Autowired
    private TransactionHistoryQueryService queryService;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Test
    @DisplayName("context loads, schema validates, and all four listeners register")
    void contextLoads() {
        assertThat(projector).isNotNull();
        assertThat(queryService).isNotNull();
        // Two consumers x two topics each.
        assertThat(listenerRegistry.getListenerContainers()).hasSize(4);
    }
}
