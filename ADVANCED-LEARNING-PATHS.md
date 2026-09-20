# 🚀 Advanced Learning Paths - Payment Platform

**Created:** 2026-09-21  
**Status:** Phase 1 Complete, Ready for Phase 2+ Advanced Work  
**Project State:** Production-ready foundation (6 microservices, Dockerfiles, health checks, backups)

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Path A: Observability Deep Dive](#path-a-observability-deep-dive)
3. [Path B: Resilience Engineering](#path-b-resilience-engineering)
4. [Path C: Performance Optimization](#path-c-performance-optimization)
5. [Implementation Guide](#implementation-guide)
6. [Learning Outcomes](#learning-outcomes)

---

## Overview

This document provides three advanced learning paths to enhance the payment platform beyond Phase 1. Each path provides **deep, industry-relevant expertise** while maintaining code quality and test coverage.

### Current Project State
- ✅ Phase 1: Critical resilience (100% complete)
- ✅ 6 microservices containerized
- ✅ 100+ tests passing
- ✅ API Gateway with JWT + rate limiting
- ✅ Circuit breaker, retry, timeout patterns
- ✅ Kafka error handling
- ✅ Backup/restore procedures

### Choosing a Path

| Criterion | Path A (Observability) | Path B (Resilience) | Path C (Performance) |
|-----------|----------------------|-------------------|-------------------|
| **Learning Curve** | Medium | Hard | Medium |
| **Practical Value** | High (production debugging) | Very High (prevents disasters) | High (scales systems) |
| **Implementation Time** | 2-3 weeks | 3-4 weeks | 2-3 weeks |
| **Best For** | DevOps engineers, on-call engineers | Platform engineers, architects | Backend engineers, DBAs |
| **Difficulty** | Medium | Hard | Medium |

---

# PATH A: Observability Deep Dive

## 🔍 Overview

**Goal:** Understand and debug payment flows across all 5 microservices in real-time

**What You'll Learn:**
- Distributed tracing (Jaeger, OpenTelemetry)
- Centralized logging (EFK stack or CloudWatch)
- Metrics and dashboards (Prometheus, Grafana)
- Correlation IDs and trace contexts
- Production debugging techniques

**Outcome:** Can follow a single payment through all services and see exactly what happened at each step

---

## Phase 2a: Distributed Tracing with Jaeger

### Step 1: Add OpenTelemetry Dependencies

**Files to modify:** All `pom.xml` files

```xml
<!-- Root pom.xml -->
<properties>
    <opentelemetry.version>1.40.0</opentelemetry.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Add to each service `pom.xml`:**
```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry.exporter</groupId>
    <artifactId>opentelemetry-exporter-jaeger-thrift</artifactId>
</dependency>
```

### Step 2: Configure OpenTelemetry in application.yml

```yaml
otel:
  sdk:
    disabled: false
  exporter:
    otlp:
      protocol: grpc
      endpoint: http://jaeger:4317  # Jaeger gRPC endpoint
  traces:
    exporter: jaeger
  metrics:
    exporter: prometheus
  resource:
    attributes:
      service.name: ${spring.application.name}
      service.version: 1.0.0
```

### Step 3: Add Jaeger to docker-compose.yml

```yaml
jaeger:
  image: jaegertracing/all-in-one:latest
  ports:
    - "16686:16686"  # Jaeger UI
    - "4317:4317"    # gRPC receiver
  environment:
    COLLECTOR_OTLP_ENABLED: "true"
```

### Step 4: Create Correlation ID Interceptor

**File:** `api-gateway-service/src/main/java/.../CorrelationIdFilter.java`

```java
@Component
public class CorrelationIdFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        
        // Add to response headers
        exchange.getResponse().getHeaders().add("X-Correlation-ID", correlationId);
        
        // Add to MDC for logging
        return chain.filter(exchange)
            .contextWrite(ctx -> ctx.put("correlationId", correlationId));
    }
}
```

### Step 5: Propagate to Downstream Services

**In Feign clients:**
```java
@FeignClient(name = "account-service")
public interface AccountClient {
    @PostMapping("/api/v1/accounts/{id}/debit")
    @CircuitBreaker(name = "account-service")
    void debitAccount(
        @PathVariable String id,
        @RequestBody DebitRequest request,
        @RequestHeader("X-Correlation-ID") String correlationId  // Propagate
    );
}
```

### Step 6: Add Jaeger UI Navigation

**Update documentation** with:
- Access: http://localhost:16686
- Search by service name
- Filter by operation (POST /api/v1/payments)
- View trace timeline (all 5 services)

### Testing Phase 2a
```bash
# 1. Start all services + Jaeger
docker-compose up

# 2. Make a payment request
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"payerAccountId": "...", "payeeAccountId": "...", "amount": 100}'

# 3. Check Jaeger UI
# - Go to http://localhost:16686
# - Select payment-service
# - Search for payment-orchestration-service
# - Click on trace to see all 5 services
```

---

## Phase 2b: Centralized Logging

### Option 1: EFK Stack (Open Source)

**Add to docker-compose.yml:**
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.0.0
  environment:
    - discovery.type=single-node
  ports:
    - "9200:9200"

kibana:
  image: docker.elastic.co/kibana/kibana:8.0.0
  ports:
    - "5601:5601"

fluent-bit:
  image: fluent/fluent-bit:latest
  volumes:
    - ./fluent-bit.conf:/fluent-bit/etc/fluent-bit.conf
```

**fluent-bit.conf:**
```ini
[SERVICE]
    Flush        5
    Daemon       off
    Log_Level    info

[INPUT]
    Name              docker
    Tag               docker.*

[OUTPUT]
    Name              es
    Match             *
    Host              elasticsearch
    Port              9200
    HTTP_User         elastic
    HTTP_Passwd       changeme
    Index             logs-%Y.%m.%d
    Type              _doc
```

### Option 2: CloudWatch (AWS)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cloudwatch-logs</artifactId>
</dependency>
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-logs</artifactId>
</dependency>
```

Configure in `application.yml`:
```yaml
logging:
  config: classpath:logback-aws.xml
```

### Create Logback Configuration

**File:** `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{ISO8601} [%thread] %-5level %logger{36} - %msg%n
                correlationId=%X{correlationId}
            </pattern>
        </encoder>
    </appender>

    <!-- File appender (for Fluent Bit) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${spring.application.name}.log</file>
        <encoder>
            <pattern>
                %d{ISO8601} [%thread] %-5level %logger{36} - %msg correlationId=%X{correlationId}%n
            </pattern>
        </encoder>
    </appender>

    <root level="DEBUG">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

---

## Phase 2c: Metrics and Dashboards

### Step 1: Add Prometheus Dependencies

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Step 2: Configure Prometheus in docker-compose.yml

```yaml
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml

grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

**prometheus.yml:**
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'payment-service'
    static_configs:
      - targets: ['payment-service:8083']
    metrics_path: '/actuator/prometheus'

  - job_name: 'account-service'
    static_configs:
      - targets: ['account-service:8081']
    metrics_path: '/actuator/prometheus'

  # ... repeat for other services
```

### Step 3: Create Custom Business Metrics

**File:** `payment-service/PaymentMetricsService.java`

```java
@Service
public class PaymentMetricsService {
    private final MeterRegistry meterRegistry;

    public void recordPaymentSuccess(String currency, BigDecimal amount) {
        meterRegistry.counter("payments.success", 
            "currency", currency)
            .increment();
        
        meterRegistry.timer("payment.duration", "status", "success")
            .record(() -> { /* ... */ });
    }

    public void recordPaymentFailure(String reason) {
        meterRegistry.counter("payments.failure", "reason", reason)
            .increment();
    }
}
```

### Step 4: Create Grafana Dashboards

**Dashboards to create:**
1. **Payment Success Rate:** (successful payments / total) %
2. **Payment Latency:** p50, p95, p99 latency
3. **Error Rate by Service:** per-service error rates
4. **Kafka Consumer Lag:** queue depth per consumer group
5. **Database Connection Pool:** active/idle connections

---

## Path A: Success Metrics

When complete, you should be able to:
- ✅ Trace a payment through all 5 services in Jaeger
- ✅ See all logs centralized and searchable by correlation ID
- ✅ View real-time metrics in Grafana
- ✅ Diagnose performance issues in < 5 minutes
- ✅ Find the exact step where payment failed

---

---

# PATH B: Resilience Engineering

## 🛡️ Overview

**Goal:** Build a system that survives partial failures and learns from them

**What You'll Learn:**
- Event sourcing (audit trail of every change)
- Bulkhead pattern (thread pool isolation)
- Chaos engineering (test failure recovery)
- Compensating transactions
- State machine patterns

**Outcome:** Payment system recovers from any single service failure automatically

---

## Phase 3a: Event Sourcing

### Concept

Instead of storing just current state:
```
Accounts table:
  account_id | balance
  123        | 900     ← Only current state
```

Store immutable events:
```
Account events:
  account_id | event_type        | amount | balance_after | timestamp
  123        | ACCOUNT_CREATED   | 1000   | 1000          | 2026-01-01
  123        | DEBIT              | 100    | 900           | 2026-09-21
```

### Step 1: Create Event Schema

**File:** `account-service/src/main/resources/db/migration/V003__create_events.sql`

```sql
CREATE TABLE account_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version INT NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_account_events_account_id ON account_events(account_id);
CREATE INDEX idx_account_events_created_at ON account_events(created_at);
```

### Step 2: Create Event Classes

```java
public abstract class DomainEvent {
    public abstract String getEventType();
    public abstract UUID getAggregateId();
}

public class AccountDebitedEvent extends DomainEvent {
    private final UUID accountId;
    private final BigDecimal amount;
    private final String reference;
    
    @Override
    public String getEventType() { return "ACCOUNT_DEBITED"; }
    
    @Override
    public UUID getAggregateId() { return accountId; }
}

public class AccountCreditedEvent extends DomainEvent {
    private final UUID accountId;
    private final BigDecimal amount;
    private final String reference;
    
    @Override
    public String getEventType() { return "ACCOUNT_CREDITED"; }
    
    @Override
    public UUID getAggregateId() { return accountId; }
}
```

### Step 3: Event Store Repository

```java
@Repository
public interface EventStoreRepository extends JpaRepository<EventStore, UUID> {
    List<EventStore> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
}

@Entity
@Table(name = "account_events")
public class EventStore {
    @Id private UUID eventId;
    @Column(name = "account_id") private UUID aggregateId;
    private String eventType;
    @Column(columnDefinition = "jsonb") private String payload;
    private LocalDateTime createdAt;
    private int version;
}
```

### Step 4: Event Sourcing Service

```java
@Service
public class EventSourcingService {
    @Autowired
    private EventStoreRepository eventStore;
    
    public void recordEvent(DomainEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        
        EventStore es = new EventStore();
        es.setAggregateId(event.getAggregateId());
        es.setEventType(event.getEventType());
        es.setPayload(payload);
        es.setCreatedAt(LocalDateTime.now());
        es.setVersion(getNextVersion(event.getAggregateId()));
        
        eventStore.save(es);
    }
    
    // Replay events to rebuild state
    public Account rebuildAccountFromEvents(UUID accountId) {
        List<EventStore> events = eventStore
            .findByAggregateIdOrderByCreatedAt(accountId);
        
        Account account = new Account(accountId);
        
        for (EventStore event : events) {
            DomainEvent domainEvent = deserializeEvent(event);
            account.applyEvent(domainEvent);
        }
        
        return account;
    }
}
```

### Step 5: Audit Queries Enabled

```java
// Query: "What was balance on Sept 1?"
List<AccountDebitedEvent> events = eventStore
    .findByAggregateIdAndCreatedAtBefore(accountId, 
        LocalDateTime.of(2026, 9, 1, 23, 59, 59));

BigDecimal balanceThen = events.stream()
    .map(e -> e.getAmount())
    .reduce(initialBalance, BigDecimal::add);
```

---

## Phase 3b: Bulkhead Pattern

**Goal:** Prevent one slow service from affecting others

### Step 1: Configure Thread Pool Isolation

**File:** `payment-service/application.yml`

```yaml
resilience4j:
  bulkhead:
    instances:
      account-service:
        max-concurrent-calls: 10
        max-wait-duration: 1s
        core-thread-pool-size: 5
        max-thread-pool-size: 10
        queue-capacity: 100
        
      fraud-service:
        max-concurrent-calls: 5
        max-wait-duration: 1s
```

### Step 2: Apply Bulkhead Annotation

```java
@FeignClient(name = "account-service")
public interface AccountClient {
    @PostMapping("/api/v1/accounts/{id}/debit")
    @CircuitBreaker(name = "account-service")
    @Bulkhead(name = "account-service")  // NEW
    @Retry(name = "account-service")
    void debitAccount(@PathVariable String id, @RequestBody DebitRequest req);
}
```

### Step 3: Test Bulkhead Isolation

```java
@Test
void testBulkheadIsolation() {
    // Thread 1-10: Account service calls (should work)
    // Thread 11+: Should queue or fail fast
    
    ExecutorService executor = Executors.newFixedThreadPool(15);
    
    for (int i = 0; i < 15; i++) {
        executor.submit(() -> {
            try {
                accountClient.debitAccount(...);
            } catch (BulkheadFullException e) {
                // Thread 11+ should hit this
            }
        });
    }
}
```

---

## Phase 3c: Chaos Engineering

### Step 1: Add Chaos Monkey for Spring Boot

```xml
<dependency>
    <groupId>de.codecentric</groupId>
    <artifactId>chaos-monkey-spring-boot</artifactId>
    <version>2.7.4</version>
</dependency>
```

### Step 2: Configure Chaos Scenarios

**File:** `application-chaos.yml`

```yaml
chaos:
  monkey:
    enabled: true
    
    # Randomly throw exceptions
    exception:
      enabled: true
      level: 3  # 0-5, higher = more often
      type: java.lang.RuntimeException
      
    # Randomly add latency
    latency:
      enabled: true
      level: 3
      duration: 1000  # ms
      
    # Randomly watch method execution fail
    watcher:
      controller: true
      service: true
      repository: true
```

### Step 3: Create Chaos Tests

```java
@SpringBootTest
@ActiveProfiles("chaos")
public class ChaosEngineeringTest {
    
    @Test
    void testPaymentRecoveryFromChaos() throws Exception {
        // With chaos enabled, services will randomly fail
        // Test that payment eventually succeeds after retries
        
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Payment payment = orchestrator.processPayment(request);
                assertThat(payment.getStatus()).isEqualTo(COMPLETED);
                return; // Success!
            } catch (Exception e) {
                if (attempt == 9) throw e;
                Thread.sleep(1000);
            }
        }
    }
}
```

---

## Phase 3d: Compensating Transactions

### Step 1: Create Compensation Log

**File:** `payment-service/src/main/resources/db/migration/V004__compensations.sql`

```sql
CREATE TABLE compensation_log (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    compensation_type VARCHAR(50),
    status VARCHAR(20),
    created_at TIMESTAMP,
    executed_at TIMESTAMP
);
```

### Step 2: Compensation Logic

```java
public class PaymentOrchestrator {
    public Payment processPayment(PaymentRequest request) {
        Payment payment = paymentRepository.save(new Payment(request));
        
        try {
            // Step 1: Debit account
            accountClient.debitAccount(...);
            
            // Step 2: Assess fraud
            FraudCheckResponse fraudCheck = fraudClient.evaluateRisk(...);
            if (fraudCheck.decision() == REJECT) {
                compensateDebit(payment);  // Refund
                return payment;
            }
            
            return payment;
        } catch (Exception e) {
            compensateDebit(payment);
            throw e;
        }
    }
    
    private void compensateDebit(Payment payment) {
        try {
            accountClient.creditAccount(...);  // Reverse
            recordCompensation(payment, "DEBIT_REFUNDED");
        } catch (Exception e) {
            recordCompensation(payment, "COMPENSATION_FAILED");
            throw e;
        }
    }
}
```

---

## Path B: Success Metrics

When complete, you should be able to:
- ✅ See complete audit trail of every payment (event sourcing)
- ✅ Rebuild any account's balance at any point in time
- ✅ Service isolation prevents cascading failures (bulkhead)
- ✅ Chaos monkey confirms system recovers automatically
- ✅ Failed payments automatically compensated

---

---

# PATH C: Performance Optimization

## ⚡ Overview

**Goal:** Make the payment system fast and scalable

**What You'll Learn:**
- Caching strategies (Redis)
- Database optimization (indexing, query analysis)
- Connection pooling tuning
- Load testing and profiling
- Batch processing

**Outcome:** Process 1000+ payments/second with sub-100ms latency

---

## Phase 4a: Redis Caching

### Step 1: Add Redis Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

### Step 2: Configure Redis

**docker-compose.yml:**
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
```

**application.yml:**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    jedis:
      pool:
        max-active: 20
        max-idle: 10
```

### Step 3: Cache Account Balance

```java
@Service
public class AccountService {
    @Autowired
    private RedisTemplate<String, Account> redisTemplate;
    
    @Cacheable(value = "accounts", key = "#accountId")
    public Account getAccount(UUID accountId) {
        // Cached for 5 minutes
        return accountRepository.findById(accountId)
            .orElseThrow();
    }
    
    @CacheEvict(value = "accounts", key = "#accountId")
    public void updateAccount(UUID accountId, Account updated) {
        accountRepository.save(updated);
    }
}
```

### Step 4: Cache Fraud Rules

```java
@Service
public class FraudService {
    @Cacheable(value = "fraud-rules", ttl = 3600)  // 1 hour
    public List<FraudRule> loadRules() {
        return fraudRuleRepository.findAll();
    }
}
```

---

## Phase 4b: Database Optimization

### Step 1: Query Analysis

```sql
-- Find slow queries
EXPLAIN ANALYZE
SELECT p.* FROM payments p
WHERE p.payer_account_id = '123'
ORDER BY p.created_at DESC
LIMIT 10;
```

### Step 2: Add Strategic Indexes

```sql
-- Index by payer (common query)
CREATE INDEX idx_payments_payer_account_id_created_at 
ON payments(payer_account_id, created_at DESC);

-- Index by status (common filter)
CREATE INDEX idx_payments_status_created_at
ON payments(status, created_at DESC);

-- Index on account (balance queries)
CREATE INDEX idx_accounts_id_balance
ON accounts(id, balance);
```

### Step 3: Connection Pool Tuning

**application.yml:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      
      # Auto-detect optimal pool size
      # Rule: (core_count * 2) + effective_spindle_count
      # Example: 4 cores, 1 disk = (4*2) + 1 = 9 connections
```

### Step 4: Create Performance Monitoring

```java
@Component
public class QueryPerformanceMonitor {
    @Around("execution(* com.payments.platform..*Repository.*(..))")
    public Object monitorQueryPerformance(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 100) {  // Slow query threshold
                log.warn("SLOW QUERY: {} took {}ms", 
                    pjp.getSignature(), duration);
            }
        }
    }
}
```

---

## Phase 4c: Load Testing

### Step 1: Create JMeter Test Plan

**File:** `performance-tests/payment-load-test.jmx`

```xml
<!-- JMeter GUI: 
  1. Create Test Plan
  2. Add Thread Group (100 users, ramp-up 10s, duration 5min)
  3. Add HTTP Request (POST /api/v1/payments)
  4. Add assertions (response time < 500ms)
  5. Add graphing listeners
-->
```

Or use Apache JMeter CLI:
```bash
jmeter -n -t performance-tests/payment-load-test.jmx \
  -l results.jtl -j jmeter.log
```

### Step 2: Load Test Scenarios

**Scenario 1: Normal Load**
- 100 users
- Ramp-up: 10 seconds
- Duration: 5 minutes
- Expected: p95 < 500ms

**Scenario 2: Spike**
- 100 users
- Ramp-up: 1 second (sudden)
- Duration: 2 minutes
- Expected: System recovers after spike

**Scenario 3: Stress**
- Increase to 500 users until failure
- Find breaking point
- Document max throughput

### Step 3: Profiling with JProfiler

```bash
# Run service with profiler
java -agentlib:jprofi=nowait,port=8849 -jar payment-service.jar

# Connect JProfiler GUI and watch:
# - CPU usage by method
# - Memory allocation
# - GC pauses
```

---

## Path C: Success Metrics

When complete, you should be able to:
- ✅ Process 1000+ payments/second
- ✅ p95 latency < 100ms (with caching)
- ✅ p99 latency < 500ms
- ✅ Database queries optimized (all < 100ms)
- ✅ Connection pool sized correctly
- ✅ No connection pool exhaustion under load

---

---

# Implementation Guide

## Getting Started

### Prerequisites
- All Phase 1 work complete ✅
- Project compiles cleanly ✅
- All tests passing ✅

### General Steps for Any Path

1. **Create a new branch**
   ```bash
   git checkout -b phase-2a-observability
   ```

2. **Add dependencies** to relevant `pom.xml` files

3. **Update docker-compose.yml** for new services (Jaeger, Prometheus, Redis, etc.)

4. **Write tests first** before implementing

5. **Verify locally** with `docker-compose up`

6. **Create PR** with detailed description

7. **Add documentation** to relevant .md files

---

## Learning Outcomes by Path

### Path A: Observability
**You will understand:**
- How to trace requests through distributed systems
- Correlation IDs and context propagation
- OpenTelemetry instrumentation
- Jaeger UI navigation
- ELK/CloudWatch logging
- Prometheus metrics collection
- Grafana dashboard creation

**Skills gained:**
- Production debugging
- Performance analysis
- Incident investigation
- Observability architecture

### Path B: Resilience
**You will understand:**
- Event sourcing patterns
- State reconstruction from events
- Bulkhead thread pool isolation
- Chaos engineering testing
- Compensating transactions
- Saga pattern completion

**Skills gained:**
- Failure recovery
- Distributed transaction coordination
- Chaos testing
- Audit trail design

### Path C: Performance
**You will understand:**
- Caching strategies
- Database optimization
- Connection pool tuning
- Load testing methodologies
- Profiling and bottleneck identification
- Horizontal scaling considerations

**Skills gained:**
- Performance optimization
- Scalability analysis
- Load testing
- Database tuning

---

## Success Criteria for Each Path

### Path A Complete When:
- [ ] Jaeger UI shows payment trace through 5 services
- [ ] Kibana/CloudWatch shows all logs with correlation IDs
- [ ] Grafana dashboards display real-time metrics
- [ ] Can diagnose issue in < 5 minutes from logs
- [ ] All tests pass

### Path B Complete When:
- [ ] Event sourcing running for Account service
- [ ] Can rebuild account balance at any point in time
- [ ] Bulkhead prevents cascading failures (test proves it)
- [ ] Chaos tests confirm automatic recovery
- [ ] All tests pass

### Path C Complete When:
- [ ] Redis caching reduces latency by 50%+
- [ ] Database indexes added and queries optimized
- [ ] Load test sustains 1000 req/sec with p95 < 500ms
- [ ] Connection pool sized optimally
- [ ] All tests pass

---

## Next Steps

1. **Choose a path** (A, B, or C) based on your interests
2. **Read the detailed guide** for that path
3. **Create implementation plan** with specific tasks
4. **Start with Step 1** and work sequentially
5. **Test at each step** - don't skip testing
6. **Document** lessons learned

---

## Resources

### Path A: Observability
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [ELK Stack Guide](https://www.elastic.co/guide/en/elastic-stack/current/index.html)

### Path B: Resilience
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Chaos Engineering](https://principlesofchaos.org/)

### Path C: Performance
- [Redis Documentation](https://redis.io/documentation)
- [PostgreSQL Query Optimization](https://www.postgresql.org/docs/current/performance.html)
- [Apache JMeter](https://jmeter.apache.org/)

---

**Last Updated:** 2026-09-21  
**Status:** Ready for Phase 2+ implementation
