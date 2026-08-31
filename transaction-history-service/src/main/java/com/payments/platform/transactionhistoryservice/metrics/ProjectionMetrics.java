package com.payments.platform.transactionhistoryservice.metrics;

import com.payments.platform.transactionhistoryservice.projection.ProjectionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom metrics for the read model, exposed on {@code /actuator/prometheus}.
 *
 * <p>Kafka consumer lag comes free from the client metrics Micrometer already
 * binds. These add the projection-specific view: <em>how stale is transaction
 * history, and is anything failing to project?</em>
 */
@Component
public class ProjectionMetrics {

    private static final String CONSUMED = "txn.history.events.consumed";
    private static final String DURATION = "txn.history.projection.duration";
    private static final String DLT = "txn.history.dlt.count";

    private final MeterRegistry registry;

    public ProjectionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordConsumed(String topic, ProjectionResult result) {
        Counter.builder(CONSUMED)
                .description("Events consumed and projected into the read model")
                .tag("topic", topic)
                .tag("result", result.name().toLowerCase())
                .register(registry)
                .increment();
    }

    public void recordFailure(String topic) {
        Counter.builder(CONSUMED)
                .description("Events consumed and projected into the read model")
                .tag("topic", topic)
                .tag("result", "failed")
                .register(registry)
                .increment();
    }

    public void recordDuration(String topic, long nanos) {
        Timer.builder(DURATION)
                .description("Time spent projecting a single event")
                .tag("topic", topic)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /** Incremented by the dead-letter recoverer. Should alert if non-zero. */
    public void recordDeadLettered(String topic) {
        Counter.builder(DLT)
                .description("Events routed to a dead-letter topic after exhausting retries")
                .tag("topic", topic)
                .register(registry)
                .increment();
    }
}
