package com.payments.platform.transactionhistoryservice.kafka;

import com.payments.platform.transactionhistoryservice.dto.AccountCreditedEvent;
import com.payments.platform.transactionhistoryservice.dto.AccountDebitedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentCompletedEvent;
import com.payments.platform.transactionhistoryservice.dto.PaymentFailedEvent;
import com.payments.platform.transactionhistoryservice.exception.InvalidEventException;
import com.payments.platform.transactionhistoryservice.metrics.ProjectionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka consumer configuration: one container factory per event type, plus the
 * retry/dead-letter error handling the platform was missing.
 *
 * <p><strong>Why one factory per type.</strong> payment-service's
 * {@code OutboxSenderKafkaAdapter} publishes the payload as a bare JSON string
 * with no {@code __TypeId__} header. A single shared {@code JsonDeserializer}
 * therefore has no way to pick a target class, and pinning one default type
 * would throw {@code MessageConversionException} on every other topic. So each
 * event type gets its own {@link ConsumerFactory} with
 * {@code VALUE_DEFAULT_TYPE} fixed and type headers explicitly ignored.
 * notification-service established this pattern; this service extends it to four
 * types and adds the error handler.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /** Total attempts = 1 initial + this many retries. */
    private static final long RETRY_ATTEMPTS = 2L;
    private static final long RETRY_INTERVAL_MS = 1000L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private final ProjectionMetrics metrics;

    public KafkaConsumerConfig(ProjectionMetrics metrics) {
        this.metrics = metrics;
    }

    private Map<String, Object> baseConsumerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // earliest: the read model must be rebuildable by replaying from the start
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // manual ack after commit
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Wrapping in ErrorHandlingDeserializer turns a poison-pill payload into a
        // null value + DeserializationException header instead of an endless
        // deserialize-crash-redeliver loop.
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return config;
    }

    private <T> ConsumerFactory<String, T> consumerFactoryFor(Class<T> type) {
        Map<String, Object> config = baseConsumerConfig();
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactoryFor(
            Class<T> type, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactoryFor(type));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ------------------------------------------------------------------
    // Container factories — one per event type
    // ------------------------------------------------------------------

    @Bean(name = "paymentCompletedListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent>
            paymentCompletedListenerContainerFactory(DefaultErrorHandler kafkaErrorHandler) {
        return containerFactoryFor(PaymentCompletedEvent.class, kafkaErrorHandler);
    }

    @Bean(name = "paymentFailedListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent>
            paymentFailedListenerContainerFactory(DefaultErrorHandler kafkaErrorHandler) {
        return containerFactoryFor(PaymentFailedEvent.class, kafkaErrorHandler);
    }

    @Bean(name = "accountDebitedListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AccountDebitedEvent>
            accountDebitedListenerContainerFactory(DefaultErrorHandler kafkaErrorHandler) {
        return containerFactoryFor(AccountDebitedEvent.class, kafkaErrorHandler);
    }

    @Bean(name = "accountCreditedListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AccountCreditedEvent>
            accountCreditedListenerContainerFactory(DefaultErrorHandler kafkaErrorHandler) {
        return containerFactoryFor(AccountCreditedEvent.class, kafkaErrorHandler);
    }

    // ------------------------------------------------------------------
    // Retry + dead-letter handling
    // ------------------------------------------------------------------

    /**
     * Producer used only to publish to dead-letter topics.
     *
     * <p>It needs a type-delegating serializer because the recoverer forwards
     * whatever it has: for a deserialization failure that is the original
     * {@code byte[]}, and for a downstream failure it is the deserialized object.
     * A single fixed serializer cannot handle both.
     */
    @Bean
    public ProducerFactory<Object, Object> dltProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(String.class, new StringSerializer());
        delegates.put(Object.class, new JsonSerializer<>());
        // assignable=true so Object.class acts as the catch-all
        DelegatingByTypeSerializer serializer = new DelegatingByTypeSerializer(delegates, true);

        DefaultKafkaProducerFactory<Object, Object> factory =
                new DefaultKafkaProducerFactory<>(config);
        factory.setKeySerializer(serializer);
        factory.setValueSerializer(serializer);
        return factory;
    }

    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate(ProducerFactory<Object, Object> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    /**
     * Retry transient failures, then dead-letter.
     *
     * <p>Retryable: DB blips and optimistic-lock contention (two events for the
     * same payment landing concurrently from different partitions). The retry
     * re-reads the committed row and merges into it correctly.
     *
     * <p>Not retryable: malformed JSON and events with no correlation key.
     * Redelivering those fails identically, so retrying only delays the
     * inevitable and holds up the partition.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, exception) -> {
                    String dltTopic = record.topic() + ".DLT";
                    log.error("Dead-lettering record from {}-{} offset {} to {}: {}",
                            record.topic(), record.partition(), record.offset(),
                            dltTopic, exception.getMessage());
                    metrics.recordDeadLettered(record.topic());
                    // partition -1: let the broker choose, since the DLT may have a
                    // different partition count than the source topic
                    return new TopicPartition(dltTopic, -1);
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));

        handler.addNotRetryableExceptions(
                DeserializationException.class,
                InvalidEventException.class,
                IllegalArgumentException.class);

        return handler;
    }
}
