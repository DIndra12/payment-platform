package com.payments.platform.transactionhistoryservice.support;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Real Postgres + real Kafka, started once per JVM.
 *
 * <p><strong>Singleton containers, deliberately not {@code @Container}.</strong>
 * JUnit's Testcontainers extension stops {@code @Container} statics when a test
 * class finishes, but Spring caches the {@code ApplicationContext} across test
 * classes with identical configuration. The second class would then reuse a
 * connection pool pointing at a container that no longer exists — the symptom is
 * {@code "This connection has been closed"} on the first query. Starting the
 * containers in a static initialiser and letting Ryuk clean them up on JVM exit
 * keeps container lifetime and context lifetime aligned.
 *
 * <p>Publishing deliberately uses a plain {@link StringSerializer} with no type
 * headers, because that is exactly what payment-service's
 * {@code OutboxSenderKafkaAdapter} does. Testing against a nicely-typed
 * {@code JsonSerializer} producer would pass while the real thing fails.
 *
 * <p>Not named {@code *Test} so the surefire include patterns do not try to run it.
 */
public abstract class KafkaPostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final KafkaContainer KAFKA;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                .withStartupTimeout(Duration.ofMinutes(3));
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Publishes a raw JSON string, mirroring the real producer: value is a
     * pre-serialized JSON string, key is the aggregate id, no type headers.
     */
    protected static void publishRaw(String topic, String key, String json) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, json));
            producer.flush();
        }
    }

    protected static Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", groupId);
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }
}
