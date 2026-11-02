# Performance Optimization Implementation Guide

Complete implementation guide for building fast, scalable payment systems with caching, database optimization, and load testing.

**Phase**: 2c (Performance)  
**Duration**: 2-3 weeks  
**Difficulty**: Medium  
**Best For**: Backend engineers, DBAs, DevOps engineers, platform engineers

---

## Table of Contents

1. [Overview](#overview)
2. [Phase 2d: Redis Caching](#phase-2d-redis-caching)
3. [Phase 2e: Database Optimization](#phase-2e-database-optimization)
4. [Phase 2f: Load Testing](#phase-2f-load-testing)
5. [Success Metrics](#success-metrics)
6. [Learning Outcomes](#learning-outcomes)

---

## Overview

### Goal

Make the payment system fast and scalable—process 1000+ payments/second with sub-100ms latency.

### What You'll Learn

- Caching strategies (Redis)
- Database optimization (indexing, query analysis)
- Connection pooling tuning
- Load testing and profiling
- Batch processing
- Horizontal scaling considerations

### Outcome

When complete, you can:
- Process 1000+ payments/second
- p95 latency < 100ms (with caching)
- p99 latency < 500ms
- Database queries optimized (all < 100ms)
- Connection pool sized correctly
- No connection pool exhaustion under load

### Current State ✅

- Spring Boot configured
- PostgreSQL with Flyway migrations
- Actuator endpoints enabled
- Micrometer metrics ready

### What's New

This guide adds:
- Redis caching layer
- Query optimization with strategic indexes
- Connection pool tuning
- Load testing framework
- Performance profiling

---

## Phase 2d: Redis Caching

### Concept: In-Memory Cache

**Traditional Approach** (Database queries):
```
Request → Database query (10-50ms) → Response (10-50ms)
Request → Database query (10-50ms) → Response (10-50ms)
Request → Database query (10-50ms) → Response (10-50ms)
```

**Caching Approach** (Memory cache):
```
Request → Cache hit (1ms) → Response (1ms) ✓ Fast!
Request → Cache hit (1ms) → Response (1ms) ✓ Fast!
Request → Database (10-50ms) → Cache update → Response
```

### Step 1: Add Redis Dependencies

**File**: `pom.xml` (each service)

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

---

### Step 2: Add Redis to docker-compose.yml

**Verify existing or add:**

```yaml
redis:
  image: redis:7-alpine
  container_name: payments-redis
  ports:
    - "6379:6379"
  command: redis-server --appendonly yes
  volumes:
    - redis-data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 3
  depends_on:
    - postgres

volumes:
  redis-data:
```

---

### Step 3: Configure Redis in Spring

**File**: `src/main/resources/application.yml`

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    connect-timeout: 2000ms
    database: 0
    jedis:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: -1ms

cache:
  type: redis
  redis:
    time-to-live: 3600000  # 1 hour default TTL
```

---

### Step 4: Cache Account Balance

**File**: `account-service/src/main/java/com/payments/platform/account/service/AccountService.java`

```java
@Service
public class AccountService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private RedisTemplate<String, Account> redisTemplate;
    
    private static final String ACCOUNT_CACHE_KEY = "account:";
    private static final long CACHE_TTL = 300; // 5 minutes
    
    /**
     * Get account with caching
     */
    public Account getAccount(UUID accountId) {
        String cacheKey = ACCOUNT_CACHE_KEY + accountId;
        
        // Try cache first
        Account cached = (Account) redisTemplate
            .opsForValue()
            .get(cacheKey);
        
        if (cached != null) {
            log.debug("Cache hit for account: {}", accountId);
            return cached;
        }
        
        // Cache miss: fetch from database
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Store in cache
        redisTemplate.opsForValue().set(cacheKey, account, 
            Duration.ofSeconds(CACHE_TTL));
        
        log.debug("Cached account: {}", accountId);
        return account;
    }
    
    /**
     * Update account and invalidate cache
     */
    public Account updateAccount(UUID accountId, Account updated) {
        Account account = accountRepository.save(updated);
        
        // Invalidate cache
        String cacheKey = ACCOUNT_CACHE_KEY + accountId;
        redisTemplate.delete(cacheKey);
        
        log.debug("Invalidated cache for account: {}", accountId);
        return account;
    }
    
    /**
     * Clear all account cache
     */
    public void clearAccountCache() {
        Set<String> keys = redisTemplate.keys(ACCOUNT_CACHE_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} account cache entries", keys.size());
        }
    }
}
```

---

### Step 5: Cache Fraud Rules

**File**: `fraud-service/src/main/java/com/payments/platform/fraud/service/FraudService.java`

```java
@Service
public class FraudService {
    
    @Autowired
    private FraudRuleRepository fraudRuleRepository;
    
    @Autowired
    private RedisTemplate<String, List<FraudRule>> redisTemplate;
    
    private static final String FRAUD_RULES_CACHE_KEY = "fraud:rules";
    private static final long RULES_CACHE_TTL = 3600; // 1 hour
    
    /**
     * Load fraud rules with caching
     */
    @Cacheable(value = "fraudRules", key = "'all'")
    public List<FraudRule> loadRules() {
        log.debug("Loading fraud rules from database");
        return fraudRuleRepository.findAll();
    }
    
    /**
     * Evaluate payment against cached rules
     */
    public FraudCheckResponse evaluateRisk(PaymentRequest request) {
        List<FraudRule> rules = loadRules(); // Uses cache
        
        for (FraudRule rule : rules) {
            if (rule.matches(request)) {
                log.warn("Fraud rule matched: {}", rule.getName());
                return FraudCheckResponse.rejected(rule.getName());
            }
        }
        
        return FraudCheckResponse.approved();
    }
    
    /**
     * Reload rules and clear cache
     */
    @CacheEvict(value = "fraudRules", allEntries = true)
    public void reloadRules() {
        log.info("Reloading fraud rules and clearing cache");
        fraudRuleRepository.refresh();
    }
}
```

---

### Step 6: Cache Configuration Class

**File**: `src/main/java/com/payments/platform/config/CacheConfig.java`

```java
package com.payments.platform.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(60))
            .disableCachingNullValues();
        
        return RedisCacheManager.create(connectionFactory);
    }
}
```

---

### Step 7: Monitor Cache Performance

**File**: Create cache metrics**

```java
@Service
public class CacheMetricsService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void recordCacheMetrics() {
        try {
            String info = redisTemplate.getConnectionFactory()
                .getConnection()
                .info()
                .get("stats");
            
            // Parse Redis stats and record metrics
            // ...
            
        } catch (Exception e) {
            log.error("Failed to record cache metrics", e);
        }
    }
}
```

---

## Phase 2e: Database Optimization

### Step 1: Analyze Slow Queries

**Common Payment Queries:**

```sql
-- Query 1: Get payments for account
SELECT p.* FROM payments p
WHERE p.payer_account_id = '123'
ORDER BY p.created_at DESC
LIMIT 10;

-- Query 2: Get payments by status
SELECT p.* FROM payments p
WHERE p.status = 'COMPLETED'
  AND p.created_at > now() - interval '24 hours'
ORDER BY p.created_at DESC
LIMIT 100;

-- Query 3: Get account balance
SELECT a.balance FROM accounts a
WHERE a.id = '123'
FOR UPDATE;
```

---

### Step 2: Add Strategic Indexes

**File**: `account-service/src/main/resources/db/migration/V006__performance_indexes.sql`

```sql
-- Index for account balance queries
CREATE INDEX idx_accounts_id_balance
ON accounts(id, balance);

-- Index for payment queries by payer
CREATE INDEX idx_payments_payer_account_id_created_at
ON payments(payer_account_id, created_at DESC);

-- Index for payment queries by status
CREATE INDEX idx_payments_status_created_at
ON payments(status, created_at DESC);

-- Index for fraud risk queries
CREATE INDEX idx_fraud_events_account_id_created_at
ON fraud_events(account_id, created_at DESC);

-- Index for transaction history
CREATE INDEX idx_transaction_history_account_id_created_at
ON transaction_history(account_id, created_at DESC);

-- Partial index for active payments only
CREATE INDEX idx_payments_active
ON payments(id)
WHERE status IN ('PENDING', 'PROCESSING');

-- GIN index for JSONB payload searching
CREATE INDEX idx_payments_payload_gin
ON payments USING gin(payload);
```

---

### Step 3: Verify Index Usage

```sql
-- Analyze query execution
EXPLAIN ANALYZE
SELECT p.* FROM payments p
WHERE p.payer_account_id = '123'
ORDER BY p.created_at DESC
LIMIT 10;

-- Expected output should show:
-- Seq Scan ... (slow, uses no index)
-- OR Index Scan ... (fast, uses index)

-- Disable sequential scan to force index usage
SET enable_seqscan = OFF;

-- Re-run query
EXPLAIN ANALYZE
SELECT p.* FROM payments p
WHERE p.payer_account_id = '123'
ORDER BY p.created_at DESC
LIMIT 10;
```

---

### Step 4: Connection Pool Tuning

**File**: `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/payments
    username: postgres
    password: postgres
    
    hikari:
      # Pool size calculation:
      # Formula: (core_count * 2) + effective_spindle_count
      # Example: 4 cores, 1 disk = (4*2) + 1 = 9 connections
      maximum-pool-size: 20
      minimum-idle: 5
      
      # Connection timeout
      connection-timeout: 20000
      
      # Idle timeout (5 minutes)
      idle-timeout: 300000
      
      # Max connection lifetime (20 minutes)
      max-lifetime: 1200000
      
      # Connection validation
      connection-test-query: "SELECT 1"
      
      # Leak detection
      leak-detection-threshold: 60000
```

**Tuning Guidelines:**

```
minimum-idle:
  - Low traffic: 2-3
  - Medium traffic: 5-10
  - High traffic: 10-15

maximum-pool-size:
  - Formula: (CPU_count * 2) + disk_count
  - 4 core CPU + 1 disk = 9
  - 8 core CPU + 2 disks = 18
  - Never exceed 30 (diminishing returns)

idle-timeout:
  - 5 minutes (300000ms) - typical
  - Shorter if limited connections
  - Longer if connection creation expensive

max-lifetime:
  - 30 minutes minimum
  - Should be less than database max_connections
```

---

### Step 5: Connection Pool Monitoring

**File**: `src/main/java/com/payments/platform/config/DataSourceMetrics.java`

```java
@Configuration
public class DataSourceMetrics {
    
    @Bean
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password,
        MeterRegistry meterRegistry) {
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        // Register metrics
        meterRegistry.gauge("hikari.connections.active",
            dataSource::getActiveConnections);
        
        meterRegistry.gauge("hikari.connections.idle",
            dataSource::getIdleConnections);
        
        meterRegistry.gauge("hikari.connections.pending",
            dataSource::getPendingThreads);
        
        return dataSource;
    }
}
```

---

### Step 6: Query Performance Monitoring

**File**: `src/main/java/com/payments/platform/aspect/QueryPerformanceAspect.java`

```java
@Aspect
@Component
public class QueryPerformanceAspect {
    
    private static final Logger log = LoggerFactory.getLogger(
        QueryPerformanceAspect.class);
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object monitorQueryPerformance(ProceedingJoinPoint pjp) 
        throws Throwable {
        
        String methodName = pjp.getSignature().getName();
        String className = pjp.getTarget().getClass().getSimpleName();
        long startTime = System.currentTimeMillis();
        
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Record metric
            meterRegistry.timer("query.duration",
                "method", methodName,
                "class", className)
                .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            // Log slow queries
            if (duration > 100) {
                log.warn("SLOW QUERY: {}.{} took {}ms",
                    className, methodName, duration);
            }
        }
    }
}
```

---

## Phase 2f: Load Testing

### Step 1: Install JMeter

```bash
# Download JMeter
# https://jmeter.apache.org/download_jmeter.cgi

# Extract and run
./apache-jmeter-5.6.1/bin/jmeter
```

---

### Step 2: Create Load Test Plan

**Via JMeter GUI:**

```
1. File → New
2. Test Plan (name: "Payment Platform Load Test")
3. Right-click → Add → Threads → Thread Group
   - Number of Threads (users): 100
   - Ramp-up Period: 10s
   - Loop Count: 1

4. Add → Sampler → HTTP Request
   - Name: POST /api/payments
   - Server: localhost:8080
   - Port: 8080
   - Method: POST
   - Path: /api/payments
   - Body: 
     {
       "payerAccountId": "ACC001",
       "payeeAccountId": "ACC002",
       "amount": 100.00,
       "currency": "USD"
     }

5. Add → Listener → View Results Tree
6. Add → Listener → Aggregate Report
7. Run test (Ctrl+R)
```

---

### Step 3: CLI Load Testing

**Using Apache JMeter CLI:**

```bash
# Run load test
jmeter -n -t performance-tests/payment-load-test.jmx \
  -l results.jtl \
  -j jmeter.log \
  -o results/

# View results
cat results/index.html
```

---

### Step 4: Load Test Scenarios

**Scenario 1: Normal Load**
```
- 100 users
- Ramp-up: 10 seconds (gradual increase)
- Duration: 5 minutes
- Expected: p95 < 500ms, error rate < 0.1%
```

**Scenario 2: Spike**
```
- 100 users
- Ramp-up: 1 second (sudden spike)
- Duration: 2 minutes
- Expected: System recovers, no cascading failures
```

**Scenario 3: Stress Test**
```
- Start: 100 users
- Increment: +50 users every 2 minutes
- Stop: When system breaks or reaches target
- Goal: Find breaking point and max throughput
```

---

### Step 5: Interpret Results

**Key Metrics:**

| Metric | Target | Interpretation |
|--------|--------|-----------------|
| Throughput (req/sec) | 100+ | Requests processed per second |
| Avg Response Time | < 100ms | Average latency |
| Min Response Time | 10-50ms | Best case (cache hit) |
| Max Response Time | < 2000ms | Worst case (timeout) |
| p50 (median) | < 50ms | Half of requests faster than this |
| p95 | < 500ms | 95% of requests faster than this |
| p99 | < 1000ms | 99% of requests faster than this |
| Error Rate | < 0.1% | Failed requests percentage |

**Example Results:**

```
Sampler name      Samples | Avg  | Min  | Max   | Error % | Throughput
POST /api/payments 3000   | 145  | 12   | 1234  | 0.2%    | 100.0/sec
```

---

### Step 6: Profiling with JProfiler

**Using JProfiler for CPU/Memory analysis:**

```bash
# Run service with JProfiler agent
java -agentlib:jprofi=nowait,port=8849 \
  -jar payment-service.jar

# Connect JProfiler GUI:
# 1. File → New Session
# 2. Connect to: localhost:8849
# 3. View:
#    - CPU Usage by method
#    - Memory allocation
#    - GC pauses
#    - Thread states
```

---

## Success Metrics

### When Complete ✅

- [ ] Redis cache configured and monitored
- [ ] Cache hit rate > 80% for account queries
- [ ] Database indexes created and verified
- [ ] Connection pool tuned (20-30 connections)
- [ ] Query performance < 100ms (99th percentile)
- [ ] Load test sustains 1000 req/sec
- [ ] Load test p95 latency < 500ms
- [ ] Load test p99 latency < 1000ms
- [ ] No connection pool exhaustion
- [ ] All tests passing

### Performance Baselines

| Metric | Target | Actual |
|--------|--------|--------|
| Throughput (without cache) | 100 req/sec | TBD |
| Throughput (with cache) | 500+ req/sec | TBD |
| p95 latency (without cache) | 500ms | TBD |
| p95 latency (with cache) | 100ms | TBD |
| Database query time | < 100ms | TBD |
| Cache hit rate | > 80% | TBD |
| Connection pool usage | < 15/20 | TBD |

---

## Learning Outcomes

### You will understand:

- Cache invalidation strategies
- Redis data structures and operations
- Database query optimization techniques
- Index creation and usage
- Connection pool sizing and tuning
- Load testing methodologies
- Performance profiling techniques
- Horizontal scaling considerations
- Bottleneck identification

### Skills gained:

- Performance optimization
- Scalability analysis
- Load testing
- Database tuning
- Caching strategies
- Profiling and debugging
- Capacity planning

---

## Reference: Common Tuning Parameters

### Redis Configuration

```yaml
redis:
  max-memory: 256mb
  max-memory-policy: allkeys-lru  # Evict oldest keys when full
  appendonly: yes                  # Persist to disk
  appendfsync: everysec           # Sync every 1 second
```

### PostgreSQL Tuning

```sql
-- Connection limits
max_connections = 200
superuser_reserved_connections = 3

-- Memory
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB

-- WAL (Write-Ahead Log)
wal_buffers = 16MB
checkpoint_completion_target = 0.9
wal_keep_size = 1GB

-- Autovacuum
autovacuum_naptime = 10s
autovacuum_vacuum_threshold = 50
autovacuum_analyze_threshold = 50
```

### JVM Tuning

```bash
java -Xms2G -Xmx2G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -jar payment-service.jar
```

---

**Last Updated**: 2026-09-21  
**Status**: Ready for Phase 2c Implementation  
**Next**: Production deployment and monitoring
