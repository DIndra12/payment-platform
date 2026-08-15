package com.payments.platform.paymentservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.springframework.util.concurrent.ListenableFuture;

@TestConfiguration
public class TestKafkaConfig {

    @Bean
    public KafkaOperations<String, String> kafkaOperations() {
        return new KafkaOperations<>() {
            @Override
            public ListenableFuture<SendResult<String, String>> send(String topic, String data) {
                SettableListenableFuture<SendResult<String, String>> f = new SettableListenableFuture<>();
                f.set(null);
                return f;
            }

            // Other methods are not used in tests — throw UnsupportedOperationException for clarity.
            @Override public ListenableFuture<SendResult<String, String>> send(org.apache.kafka.clients.producer.ProducerRecord<String, String> record) { throw new UnsupportedOperationException(); }
            @Override public ListenableFuture<SendResult<String, String>> send(String topic, Integer partition, String key, String data) { throw new UnsupportedOperationException(); }
            @Override public ListenableFuture<SendResult<String, String>> send(String topic, String key, String data) { throw new UnsupportedOperationException(); }
            @Override public ListenableFuture<SendResult<String, String>> sendOffset(org.apache.kafka.clients.producer.ProducerRecord<String, String> record, long offset) { throw new UnsupportedOperationException(); }
            @Override public void flush() { }
            @Override public void destroy() { }
            @Override public Object execute(org.springframework.messaging.MessageChannelCallback callback) { throw new UnsupportedOperationException(); }
        };
    }
}
