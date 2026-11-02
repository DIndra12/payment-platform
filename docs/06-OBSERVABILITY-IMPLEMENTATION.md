# Observability Deep Dive Implementation Guide

Complete implementation guide for distributed tracing, logging, and metrics across the Payment Platform.

**Phase**: 2a (Observability)  
**Duration**: 2-3 weeks  
**Difficulty**: Medium  
**Best For**: DevOps engineers, on-call engineers, platform engineers

---

## Table of Contents

1. [Overview](#overview)
2. [Phase 2a: Distributed Tracing with Jaeger](#phase-2a-distributed-tracing-with-jaeger)
3. [Phase 2b: Centralized Logging](#phase-2b-centralized-logging)
4. [Phase 2c: Metrics and Dashboards](#phase-2c-metrics-and-dashboards)
5. [Success Metrics](#success-metrics)
6. [Learning Outcomes](#learning-outcomes)

---

## Overview

### Goal

Understand and debug payment flows across all microservices in real-time with complete visibility.

### What You'll Learn

- Distributed tracing (Jaeger, OpenTelemetry)
- Centralized logging (EFK stack or CloudWatch)
- Metrics and dashboards (Prometheus, Grafana)
- Correlation IDs and trace contexts
- Production debugging techniques

### Outcome

When complete, you can:
- Follow a single payment through all services in Jaeger
- See all logs centralized and searchable by correlation ID
- View real-time metrics in Grafana
- Diagnose performance issues in < 5 minutes
- Find the exact step where payment failed

### Current State ✅

- Phase 2 Observability partially complete:
  - OpenTelemetry 1.32.0 configured
  - Micrometer Tracing bridge implemented
  - ELK Stack integrated (Elasticsearch, Kibana, Fluent Bit)
  - Prometheus + Grafana dashboards ready
  - Structured JSON logging in place

### What's Left

This guide completes:
- Distributed tracing visualization in Jaeger
- Correlation ID propagation
- Custom business metrics
- Dashboard interpretation and debugging workflows

---

## Phase 2a: Distributed Tracing with Jaeger

### Step 1: Verify OpenTelemetry Dependencies

**Current status**: Already configured in root `pom.xml`

**Verify in root pom.xml:**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bom</artifactId>
            <version>1.4.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Verify in each service pom.xml:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry.exporter</groupId>
    <artifactId>opentelemetry-exporter-jaeger-thrift</artifactId>
</dependency>
```

✅ **Status**: Already configured in Phase 2

---

### Step 2: Configure OpenTelemetry in application.yml

**File to verify/update**: Each service's `src/main/resources/application.yml`

```yaml
# API Gateway Service Example
otel:
  sdk:
    disabled: false
  exporter:
    otlp:
      protocol: grpc
      endpoint: http://jaeger:4317
  traces:
    exporter: jaeger
  metrics:
    exporter: prometheus
  resource:
    attributes:
      service.name: api-gateway-service
      service.version: 1.0.0

management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% in dev, 10% in prod
```

**Repeat for each service**, changing `service.name` to:
- `payment-service`
- `account-service`
- `fraud-service`
- `notification-service`
- `transaction-history-service`

---

### Step 3: Jaeger Container in docker-compose.yml

**Verify Jaeger service exists:**

```yaml
jaeger:
  image: jaegertracing/all-in-one:latest
  container_name: payments-jaeger
  ports:
    - "16686:16686"  # Jaeger UI
    - "4317:4317"    # gRPC receiver (OTLP)
    - "4318:4318"    # HTTP receiver (OTLP)
  environment:
    COLLECTOR_OTLP_ENABLED: "true"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:14269/"]
    interval: 10s
    timeout: 5s
    retries: 3
  depends_on:
    - elasticsearch
```

✅ **Status**: Already in docker-compose.yml

---

### Step 4: Create Correlation ID Interceptor

**File**: `api-gateway-service/src/main/java/com/payments/platform/apigateway/filter/CorrelationIdFilter.java`

```java
package com.payments.platform.apigateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Configuration
public class CorrelationIdFilter implements GlobalFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC = "correlationId";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Generate or extract correlation ID
        String correlationId = exchange.getRequest()
            .getHeaders()
            .getFirst(CORRELATION_ID_HEADER);
        
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        final String finalCorrelationId = correlationId;
        
        // Add to response headers
        exchange.getResponse()
            .getHeaders()
            .add(CORRELATION_ID_HEADER, finalCorrelationId);
        
        // Log the correlation ID
        org.slf4j.MDC.put(CORRELATION_ID_MDC, finalCorrelationId);
        
        return chain.filter(exchange);
    }
}
```

---

### Step 5: Propagate Correlation ID to Downstream Services

**File**: Update all Feign clients

**Example - AccountClient in payment-service**:

```java
@FeignClient(name = "account-service")
public interface AccountClient {
    
    @PostMapping("/api/v1/accounts/{id}/debit")
    @CircuitBreaker(name = "account-service")
    AccountResponse debitAccount(
        @PathVariable String id,
        @RequestBody DebitRequest request,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
    
    @PostMapping("/api/v1/accounts/{id}/credit")
    @CircuitBreaker(name = "account-service")
    AccountResponse creditAccount(
        @PathVariable String id,
        @RequestBody CreditRequest request,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
```

**Update payment orchestrator to propagate:**

```java
@Service
public class PaymentOrchestrator {
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private FraudClient fraudClient;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        String correlationId = MDC.get("correlationId");
        
        try {
            // Debit source account
            accountClient.debitAccount(
                request.getPayerAccountId(),
                new DebitRequest(request.getAmount()),
                correlationId  // Propagate
            );
            
            // Check fraud
            FraudCheckResponse fraudCheck = fraudClient.evaluateRisk(
                request,
                correlationId  // Propagate
            );
            
            // Continue processing...
        } catch (Exception e) {
            log.error("Payment failed [{}]", correlationId, e);
            throw e;
        }
    }
}
```

---

### Step 6: Add Custom Span Annotations

**Optional**: Add business-level spans to track important operations

```java
import io.micrometer.tracing.Tracer;

@Service
public class PaymentService {
    @Autowired
    private Tracer tracer;
    
    public Payment createPayment(PaymentRequest request) {
        // Create custom span
        try (Tracer.SpanInScope scope = tracer.nextSpan()
            .name("payment.create")
            .tag("payer.id", request.getPayerAccountId())
            .tag("amount", request.getAmount().toString())
            .start()
            .openScope()) {
            
            Payment payment = new Payment(request);
            return paymentRepository.save(payment);
        }
    }
}
```

---

### Step 7: Test Distributed Tracing

**Start the system:**

```bash
# 1. Start all services
docker-compose up -d

# 2. Wait for Jaeger to be ready
docker-compose logs jaeger | grep -i listening

# 3. Verify health
curl http://localhost:8080/health/ready
```

**Make a payment request:**

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-api" | jq -r '.access_token')

# Make payment
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "payerAccountId": "ACC001",
    "payeeAccountId": "ACC002",
    "amount": 100.00,
    "currency": "USD"
  }'
```

**View in Jaeger UI:**

```
1. Open: http://localhost:16686
2. Service: Select "api-gateway-service"
3. Operation: Select "POST /api/payments"
4. Find Traces: Click latest trace
5. View Timeline: See all 5 services
   - api-gateway-service (entry point)
   - payment-service (orchestration)
   - account-service (debit)
   - fraud-service (check)
   - notification-service (send email)
```

**Expected output:**

```
Trace ID: a1b2c3d4e5f6g7h8
Duration: 245ms
Services: 5

Spans:
├─ POST /api/payments (api-gateway-service) 245ms
  ├─ PaymentOrchestrator.processPayment (payment-service) 240ms
    ├─ POST /api/accounts/ACC001/debit (account-service) 45ms
    ├─ POST /api/fraud/evaluate (fraud-service) 50ms
    ├─ POST /api/accounts/ACC002/credit (account-service) 40ms
    └─ POST /api/notifications/email (notification-service) 100ms
```

---

## Phase 2b: Centralized Logging

### Current Status ✅

- Elasticsearch running (port 9200)
- Kibana running (port 5601)
- Fluent Bit configured to forward logs
- Structured JSON logging in place

### Step 1: Verify Logback Configuration

**File**: `payment-service/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console appender for development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{ISO8601} [%thread] %-5level %logger{36} - %msg correlationId=%X{correlationId}%n
            </pattern>
        </encoder>
    </appender>

    <!-- File appender (for Fluent Bit to read) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${spring.application.name}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/%d{yyyy-MM-dd}/${spring.application.name}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>
                %d{ISO8601} [%thread] %-5level %logger{36} - %msg correlationId=%X{correlationId}%n
            </pattern>
        </encoder>
    </appender>

    <!-- JSON appender (for structured logging) -->
    <appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${spring.application.name}-json.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${spring.application.name}"}</customFields>
        </encoder>
    </appender>

    <!-- Profile-specific configuration -->
    <springProfile name="production">
        <root level="INFO">
            <appender-ref ref="FILE"/>
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <springProfile name="development">
        <root level="DEBUG">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

---

### Step 2: Verify Fluent Bit Configuration

**File**: `infrastructure/fluent-bit/fluent-bit.conf`

```ini
[SERVICE]
    Flush        5
    Daemon       off
    Log_Level    info
    Parsers_File parsers.conf

[INPUT]
    Name              tail
    Path              ./logs/*/*.log
    Parser            json
    Tag               docker.*
    Refresh_Interval  5
    Mem_Buf_Limit     50MB

[OUTPUT]
    Name            es
    Match           *
    Host            elasticsearch
    Port            9200
    HTTP_User       elastic
    HTTP_Passwd     changeme
    Index           logs-%Y.%m.%d
    Type            _doc
    Retry_Limit     false
```

---

### Step 3: Access Kibana Dashboard

**URL**: http://localhost:5601

**Setup:**

1. **Create Index Pattern**:
   - Click "Stack Management" → "Index Patterns"
   - Create pattern: `logs-*`
   - Time field: `@timestamp`

2. **View Logs**:
   - Click "Discover"
   - Search by correlation ID: `correlationId: "a1b2c3d4"`
   - Filter by service: `service: "payment-service"`
   - Sort by timestamp

3. **Sample Query**:
   ```
   correlationId: "a1b2c3d4" AND level: "ERROR"
   ```

---

### Step 4: Create Custom Log Patterns

**Enhanced logging in services:**

```java
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    
    public void processPayment(Payment payment, String correlationId) {
        try {
            log.info("Starting payment processing", 
                "correlationId", correlationId,
                "paymentId", payment.getId(),
                "amount", payment.getAmount(),
                "status", "INITIATED");
            
            // Process...
            
            log.info("Payment processing complete",
                "correlationId", correlationId,
                "paymentId", payment.getId(),
                "status", "COMPLETED",
                "duration", duration);
                
        } catch (Exception e) {
            log.error("Payment processing failed",
                "correlationId", correlationId,
                "paymentId", payment.getId(),
                "error", e.getMessage(),
                "status", "FAILED");
            throw e;
        }
    }
}
```

---

## Phase 2c: Metrics and Dashboards

### Current Status ✅

- Prometheus running (port 9090)
- Grafana running (port 3000)
- Micrometer collecting metrics
- Spring Boot Actuator enabled

### Step 1: Verify Prometheus Configuration

**File**: `infrastructure/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'api-gateway-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway-service:8080']

  - job_name: 'payment-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['payment-service:8083']

  - job_name: 'account-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['account-service:8081']

  - job_name: 'fraud-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['fraud-service:8082']

  - job_name: 'notification-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['notification-service:8084']

  - job_name: 'transaction-history-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['transaction-history-service:8085']
```

---

### Step 2: Create Custom Business Metrics

**File**: `payment-service/src/main/java/com/payments/platform/payment/metrics/PaymentMetrics.java`

```java
package com.payments.platform.payment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PaymentMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordPaymentSuccess(String currency, BigDecimal amount) {
        meterRegistry.counter("payments.processed",
            "status", "success",
            "currency", currency)
            .increment();
        
        meterRegistry.gauge("payments.amount.total",
            amount.doubleValue());
    }
    
    public void recordPaymentFailure(String reason) {
        meterRegistry.counter("payments.processed",
            "status", "failed",
            "reason", reason)
            .increment();
    }
    
    public void recordPaymentDuration(long durationMs, String status) {
        Timer.builder("payment.duration")
            .tag("status", status)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordFraudCheckResult(boolean isFraudulent) {
        meterRegistry.counter("fraud.checks",
            "result", isFraudulent ? "rejected" : "approved")
            .increment();
    }
}
```

**Use in service:**

```java
@Service
public class PaymentService {
    @Autowired
    private PaymentMetrics metrics;
    
    public Payment processPayment(PaymentRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Process payment...
            Payment payment = createPayment(request);
            
            metrics.recordPaymentSuccess(
                request.getCurrency(),
                request.getAmount()
            );
            
            return payment;
        } catch (Exception e) {
            metrics.recordPaymentFailure(e.getClass().getSimpleName());
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordPaymentDuration(duration, "completed");
        }
    }
}
```

---

### Step 3: Access Grafana Dashboards

**URL**: http://localhost:3000  
**Login**: admin / admin

**Import Payment Platform Dashboard:**

1. Create new dashboard
2. Add panels:
   - **Payment Success Rate**: `rate(payments_processed_total{status="success"}[5m])`
   - **Payment Latency p95**: `histogram_quantile(0.95, payment_duration_seconds_bucket)`
   - **Error Rate**: `rate(payments_processed_total{status="failed"}[5m])`
   - **Throughput**: `rate(payments_processed_total[1m])`

---

### Step 4: Create Alert Rules

**File**: `infrastructure/prometheus/alerts.yml`

```yaml
groups:
  - name: payment_alerts
    interval: 30s
    rules:
      - alert: HighPaymentErrorRate
        expr: rate(payments_processed_total{status="failed"}[5m]) > 0.05
        for: 2m
        annotations:
          summary: "Payment error rate > 5%"
          
      - alert: SlowPaymentProcessing
        expr: histogram_quantile(0.95, payment_duration_seconds_bucket) > 2
        for: 5m
        annotations:
          summary: "P95 payment latency > 2s"
          
      - alert: ServiceDown
        expr: up{job=~".*-service"} == 0
        for: 1m
        annotations:
          summary: "{{ $labels.job }} is down"
```

---

## Success Metrics

### When Complete ✅

- [ ] Jaeger UI shows payment trace through 5+ services
- [ ] Can search Kibana logs by correlation ID
- [ ] Find payment in Kibana in < 30 seconds
- [ ] Grafana dashboards display real-time metrics
- [ ] Can diagnose issue in < 5 minutes from logs
- [ ] Alert rules firing correctly
- [ ] All tests passing

### Performance Baselines

| Metric | Target | Actual |
|--------|--------|--------|
| Payment latency p50 | < 100ms | TBD |
| Payment latency p95 | < 500ms | TBD |
| Payment latency p99 | < 1000ms | TBD |
| Error rate | < 0.1% | TBD |
| Log ingestion latency | < 5s | TBD |
| Trace sampling overhead | < 5% | TBD |

---

## Learning Outcomes

### You will understand:

- How to trace requests through distributed systems
- Correlation IDs and context propagation
- OpenTelemetry instrumentation and exporters
- Jaeger UI navigation and trace analysis
- ELK stack log aggregation and querying
- Prometheus metrics collection and scraping
- Grafana dashboard creation and alerting
- Production debugging workflows

### Skills gained:

- Production troubleshooting
- Performance analysis and profiling
- Incident investigation and root cause analysis
- Observability architecture design
- Dashboard and alert creation
- Real-time monitoring

---

**Last Updated**: 2026-09-21  
**Status**: Ready for Phase 2a Implementation  
**Next**: Phase 2b (Resilience Engineering)
