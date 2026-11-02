# Resilience Engineering Implementation Guide

Complete implementation guide for building fault-tolerant, self-healing systems with event sourcing and chaos engineering.

**Phase**: 2b (Resilience)  
**Duration**: 3-4 weeks  
**Difficulty**: Hard  
**Best For**: Platform engineers, architects, senior backend engineers

---

## Table of Contents

1. [Overview](#overview)
2. [Phase 2b: Event Sourcing](#phase-2b-event-sourcing)
3. [Phase 2c: Bulkhead Pattern](#phase-2c-bulkhead-pattern)
4. [Phase 2d: Chaos Engineering](#phase-2d-chaos-engineering)
5. [Phase 2e: Compensating Transactions](#phase-2e-compensating-transactions)
6. [Success Metrics](#success-metrics)
7. [Learning Outcomes](#learning-outcomes)

---

## Overview

### Goal

Build a system that survives partial failures, recovers automatically, and learns from chaos.

### What You'll Learn

- Event sourcing patterns (immutable event logs)
- State reconstruction from events
- Bulkhead thread pool isolation
- Chaos engineering testing
- Compensating transactions (saga rollback)
- State machine patterns

### Outcome

When complete, you can:
- Rebuild any account's balance at any point in time
- View complete audit trail of every transaction
- Isolate service failures to prevent cascading
- Automatically recover from chaos scenarios
- Rollback failed payments via compensation

### Current State ✅

- Resilience4j configured (circuit breaker, retry, timeout)
- Kafka error handling with DLQ routing
- Saga pattern implemented for distributed transactions
- Outbox pattern for consistency

### What's New

This guide adds:
- Event sourcing for audit trail
- Bulkhead pattern for thread isolation
- Chaos engineering for failure testing
- Compensating transaction workflows

---

## Phase 2b: Event Sourcing

### Concept: Events as Source of Truth

**Traditional Approach** (State-based):
```
Accounts table:
  account_id | balance | updated_at
  123        | 900     | 2026-09-21 14:30:00  ← Only current state
```

**Event Sourcing Approach** (Event-based):
```
Account events table:
  event_id | account_id | event_type      | payload              | created_at
  1        | 123        | ACCOUNT_CREATED | {"initial": 1000}    | 2026-01-01 10:00:00
  2        | 123        | DEBIT           | {"amount": 100}      | 2026-09-21 14:00:00
  3        | 123        | CREDIT          | {"amount": 50}       | 2026-09-21 14:15:00
  4        | 123        | DEBIT           | {"amount": 50}       | 2026-09-21 14:30:00
  
Replay events: 1000 - 100 + 50 - 50 = 900 ← Reconstruct state
```

### Benefits

- **Audit Trail**: Complete history of every change
- **Temporal Queries**: "What was balance on Sept 1?"
- **Replay**: Rebuild state at any point in time
- **Debugging**: Understand exact sequence of events
- **Legal Compliance**: Immutable transaction log

### Step 1: Create Event Schema

**File**: `account-service/src/main/resources/db/migration/V003__create_events.sql`

```sql
CREATE TABLE account_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version INT NOT NULL,
    correlation_id VARCHAR(36),
    created_by VARCHAR(100),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    UNIQUE(account_id, version)
);

CREATE INDEX idx_account_events_account_id 
ON account_events(account_id);

CREATE INDEX idx_account_events_created_at 
ON account_events(created_at DESC);

CREATE INDEX idx_account_events_type 
ON account_events(event_type);

CREATE INDEX idx_account_events_correlation 
ON account_events(correlation_id);
```

---

### Step 2: Create Event Classes

**File**: `account-service/src/main/java/com/payments/platform/account/events/DomainEvent.java`

```java
package com.payments.platform.account.events;

import java.util.UUID;
import java.time.LocalDateTime;

public abstract class DomainEvent {
    private final UUID eventId;
    private final LocalDateTime createdAt;
    private final String correlationId;
    
    protected DomainEvent(String correlationId) {
        this.eventId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.correlationId = correlationId;
    }
    
    public abstract String getEventType();
    public abstract UUID getAggregateId();
    public abstract String getPayloadJson();
    
    public UUID getEventId() { return eventId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCorrelationId() { return correlationId; }
}
```

**File**: `account-service/src/main/java/com/payments/platform/account/events/AccountDebitedEvent.java`

```java
package com.payments.platform.account.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;

public class AccountDebitedEvent extends DomainEvent {
    private final UUID accountId;
    private final BigDecimal amount;
    private final String reference;
    private final String reason;
    
    public AccountDebitedEvent(
        UUID accountId,
        BigDecimal amount,
        String reference,
        String reason,
        String correlationId) {
        super(correlationId);
        this.accountId = accountId;
        this.amount = amount;
        this.reference = reference;
        this.reason = reason;
    }
    
    @Override
    public String getEventType() { return "ACCOUNT_DEBITED"; }
    
    @Override
    public UUID getAggregateId() { return accountId; }
    
    @Override
    public String getPayloadJson() {
        try {
            return new ObjectMapper().writeValueAsString(
                Map.of(
                    "amount", amount,
                    "reference", reference,
                    "reason", reason
                )
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
}
```

**File**: `account-service/src/main/java/com/payments/platform/account/events/AccountCreditedEvent.java`

```java
package com.payments.platform.account.events;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountCreditedEvent extends DomainEvent {
    private final UUID accountId;
    private final BigDecimal amount;
    private final String reference;
    private final String reason;
    
    public AccountCreditedEvent(
        UUID accountId,
        BigDecimal amount,
        String reference,
        String reason,
        String correlationId) {
        super(correlationId);
        this.accountId = accountId;
        this.amount = amount;
        this.reference = reference;
        this.reason = reason;
    }
    
    @Override
    public String getEventType() { return "ACCOUNT_CREDITED"; }
    
    @Override
    public UUID getAggregateId() { return accountId; }
    
    @Override
    public String getPayloadJson() {
        // Similar to AccountDebitedEvent
        return "{}";
    }
    
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
}
```

---

### Step 3: Event Store Repository

**File**: `account-service/src/main/java/com/payments/platform/account/repository/EventStoreRepository.java`

```java
package com.payments.platform.account.repository;

import com.payments.platform.account.entity.EventStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventStoreRepository extends JpaRepository<EventStore, UUID> {
    
    List<EventStore> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
    
    List<EventStore> findByAggregateIdAndCreatedAtBefore(
        UUID aggregateId,
        LocalDateTime createdAt);
    
    List<EventStore> findByEventType(String eventType);
    
    List<EventStore> findByCorrelationId(String correlationId);
}
```

**File**: `account-service/src/main/java/com/payments/platform/account/entity/EventStore.java`

```java
package com.payments.platform.account.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_events")
public class EventStore {
    
    @Id
    private UUID eventId;
    
    @Column(name = "account_id", nullable = false)
    private UUID aggregateId;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private int version;
    
    @Column
    private String correlationId;
    
    @Column
    private String createdBy;
    
    // Constructors, getters, setters...
    
    public EventStore() {}
    
    public EventStore(UUID eventId, UUID aggregateId, String eventType,
                     String payload, LocalDateTime createdAt, int version) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.version = version;
    }
}
```

---

### Step 4: Event Sourcing Service

**File**: `account-service/src/main/java/com/payments/platform/account/service/EventSourcingService.java`

```java
package com.payments.platform.account.service;

import com.payments.platform.account.entity.Account;
import com.payments.platform.account.entity.EventStore;
import com.payments.platform.account.events.*;
import com.payments.platform.account.repository.EventStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EventSourcingService {
    
    private static final Logger log = LoggerFactory.getLogger(EventSourcingService.class);
    
    @Autowired
    private EventStoreRepository eventStore;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Record a domain event in the event store
     */
    public void recordEvent(DomainEvent event) {
        EventStore es = new EventStore();
        es.setEventId(event.getEventId());
        es.setAggregateId(event.getAggregateId());
        es.setEventType(event.getEventType());
        es.setPayload(event.getPayloadJson());
        es.setCreatedAt(event.getCreatedAt());
        es.setCorrelationId(event.getCorrelationId());
        es.setVersion(getNextVersion(event.getAggregateId()));
        
        eventStore.save(es);
        log.info("Event recorded: {} for aggregate: {}", 
            event.getEventType(), event.getAggregateId());
    }
    
    /**
     * Rebuild account state from all events
     */
    public Account rebuildAccountFromEvents(UUID accountId) {
        List<EventStore> events = eventStore
            .findByAggregateIdOrderByCreatedAt(accountId);
        
        if (events.isEmpty()) {
            throw new RuntimeException("No events found for account: " + accountId);
        }
        
        Account account = new Account(accountId);
        
        for (EventStore eventRecord : events) {
            applyEvent(account, eventRecord);
        }
        
        log.info("Rebuilt account {} to version {}", 
            accountId, events.size());
        return account;
    }
    
    /**
     * Get account balance at specific point in time
     */
    public java.math.BigDecimal getBalanceAt(UUID accountId, java.time.LocalDateTime asOf) {
        List<EventStore> events = eventStore
            .findByAggregateIdAndCreatedAtBefore(accountId, asOf);
        
        Account account = new Account(accountId);
        
        for (EventStore eventRecord : events) {
            applyEvent(account, eventRecord);
        }
        
        return account.getBalance();
    }
    
    /**
     * Get all events for an account
     */
    public List<EventStore> getAccountHistory(UUID accountId) {
        return eventStore.findByAggregateIdOrderByCreatedAt(accountId);
    }
    
    /**
     * Get all events for a payment request
     */
    public List<EventStore> getEventsForPayment(String correlationId) {
        return eventStore.findByCorrelationId(correlationId);
    }
    
    private void applyEvent(Account account, EventStore eventRecord) {
        switch (eventRecord.getEventType()) {
            case "ACCOUNT_DEBITED":
                handleDebit(account, eventRecord);
                break;
            case "ACCOUNT_CREDITED":
                handleCredit(account, eventRecord);
                break;
            default:
                log.warn("Unknown event type: {}", eventRecord.getEventType());
        }
    }
    
    private void handleDebit(Account account, EventStore eventRecord) {
        try {
            AccountDebitedEvent event = objectMapper.readValue(
                eventRecord.getPayload(), 
                AccountDebitedEvent.class);
            account.debit(event.getAmount());
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize debit event", e);
        }
    }
    
    private void handleCredit(Account account, EventStore eventRecord) {
        try {
            AccountCreditedEvent event = objectMapper.readValue(
                eventRecord.getPayload(), 
                AccountCreditedEvent.class);
            account.credit(event.getAmount());
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize credit event", e);
        }
    }
    
    private int getNextVersion(UUID accountId) {
        List<EventStore> events = eventStore
            .findByAggregateIdOrderByCreatedAt(accountId);
        return events.size() + 1;
    }
}
```

---

### Step 5: Update Account Service to Use Event Sourcing

**File**: `account-service/src/main/java/com/payments/platform/account/service/AccountService.java`

```java
@Service
public class AccountService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private EventSourcingService eventSourcingService;
    
    public void debitAccount(UUID accountId, BigDecimal amount, String correlationId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Update state
        account.debit(amount);
        accountRepository.save(account);
        
        // Record event
        DomainEvent event = new AccountDebitedEvent(
            accountId,
            amount,
            UUID.randomUUID().toString(),
            "PAYMENT_DEBIT",
            correlationId
        );
        eventSourcingService.recordEvent(event);
    }
    
    public void creditAccount(UUID accountId, BigDecimal amount, String correlationId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // Update state
        account.credit(amount);
        accountRepository.save(account);
        
        // Record event
        DomainEvent event = new AccountCreditedEvent(
            accountId,
            amount,
            UUID.randomUUID().toString(),
            "PAYMENT_CREDIT",
            correlationId
        );
        eventSourcingService.recordEvent(event);
    }
}
```

---

### Step 6: Create Audit Query Endpoints

**File**: `account-service/src/main/java/com/payments/platform/account/controller/AuditController.java`

```java
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    
    @Autowired
    private EventSourcingService eventSourcingService;
    
    /**
     * Get complete account history
     */
    @GetMapping("/accounts/{accountId}/history")
    public ResponseEntity<List<EventDto>> getAccountHistory(
        @PathVariable UUID accountId) {
        
        List<EventStore> events = eventSourcingService.getAccountHistory(accountId);
        List<EventDto> dtos = events.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Get account balance at specific time
     */
    @GetMapping("/accounts/{accountId}/balance-at")
    public ResponseEntity<BalanceAtDto> getBalanceAt(
        @PathVariable UUID accountId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime asOf) {
        
        BigDecimal balance = eventSourcingService.getBalanceAt(accountId, asOf);
        return ResponseEntity.ok(new BalanceAtDto(balance, asOf));
    }
    
    /**
     * Get all events for a payment
     */
    @GetMapping("/payments/{correlationId}/events")
    public ResponseEntity<List<EventDto>> getPaymentEvents(
        @PathVariable String correlationId) {
        
        List<EventStore> events = eventSourcingService.getEventsForPayment(correlationId);
        List<EventDto> dtos = events.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    private EventDto toDto(EventStore event) {
        return new EventDto(
            event.getEventId(),
            event.getEventType(),
            event.getPayload(),
            event.getCreatedAt(),
            event.getVersion()
        );
    }
}
```

---

## Phase 2c: Bulkhead Pattern

### Concept: Thread Pool Isolation

**Problem**: One slow service affects all callers
```
Caller Thread Pool (100 threads)
  ├─ Request 1-50: → account-service (all threads blocked!)
  ├─ Request 51-100: → fraud-service (no threads left, timeout)
  └─ Result: Cascading failure
```

**Solution**: Separate thread pools per service
```
Caller Thread Pool (100 threads)
  ├─ Account Bulkhead (10 threads) → account-service (isolated)
  ├─ Fraud Bulkhead (5 threads) → fraud-service (isolated)
  └─ Payment Bulkhead (20 threads) → payment-service (isolated)
  
Result: One slow service doesn't affect others
```

### Step 1: Configure Resilience4j Bulkhead

**File**: `payment-service/src/main/resources/application.yml`

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
        core-thread-pool-size: 3
        max-thread-pool-size: 5
        queue-capacity: 50
```

### Step 2: Apply Bulkhead to Feign Clients

**File**: `payment-service/src/main/java/com/payments/platform/payment/client/AccountClient.java`

```java
@FeignClient(name = "account-service")
public interface AccountClient {
    
    @PostMapping("/api/v1/accounts/{id}/debit")
    @CircuitBreaker(name = "account-service")
    @Bulkhead(name = "account-service")
    @Retry(name = "account-service")
    AccountResponse debitAccount(
        @PathVariable String id,
        @RequestBody DebitRequest request,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
```

### Step 3: Test Bulkhead Isolation

**File**: `payment-service/src/test/java/com/payments/platform/payment/BulkheadIsolationTest.java`

```java
@SpringBootTest
public class BulkheadIsolationTest {
    
    @Autowired
    private AccountClient accountClient;
    
    @Test
    void testBulkheadPreventsThreadExhaustion() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(15);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Submit 15 tasks to bulkhead with max 10 concurrent calls
        for (int i = 0; i < 15; i++) {
            executor.submit(() -> {
                try {
                    accountClient.debitAccount(
                        "ACC123",
                        new DebitRequest(BigDecimal.TEN),
                        UUID.randomUUID().toString()
                    );
                    successCount.incrementAndGet();
                } catch (BulkheadFullException e) {
                    // Expected for threads 11-15
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Verify bulkhead prevented more than 10 concurrent calls
        assertThat(successCount.get()).isLessThanOrEqualTo(10);
        assertThat(failureCount.get()).isGreaterThan(0);
    }
}
```

---

## Phase 2d: Chaos Engineering

### Concept: Test Failure Recovery

**Goal**: Confirm system recovers automatically from failures

### Step 1: Add Chaos Monkey Dependency

**File**: `payment-service/pom.xml`

```xml
<dependency>
    <groupId>de.codecentric</groupId>
    <artifactId>chaos-monkey-spring-boot</artifactId>
    <version>2.7.4</version>
    <scope>test</scope>
</dependency>
```

### Step 2: Configure Chaos Scenarios

**File**: `payment-service/src/test/resources/application-chaos.yml`

```yaml
chaos:
  monkey:
    enabled: true
    
    # Randomly throw exceptions
    exception:
      enabled: true
      level: 3  # 0-5: higher = more failures
      type: java.lang.RuntimeException
      
    # Randomly add latency
    latency:
      enabled: true
      level: 3
      duration: 1000  # ms
      
    # Watch methods for chaos
    watcher:
      controller: true
      service: true
      repository: false
```

### Step 3: Create Chaos Test

**File**: `payment-service/src/test/java/com/payments/platform/payment/ChaosEngineeringTest.java`

```java
@SpringBootTest
@ActiveProfiles("chaos")
public class ChaosEngineeringTest {
    
    @Autowired
    private PaymentOrchestrator orchestrator;
    
    @Test
    void testPaymentRecoveryFromChaos() {
        PaymentRequest request = new PaymentRequest(
            "ACC001",
            "ACC002",
            BigDecimal.valueOf(100),
            "USD"
        );
        
        // With chaos enabled, requests will randomly fail
        // Test that payment eventually succeeds after retries
        
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Payment payment = orchestrator.processPayment(request);
                assertThat(payment.getStatus())
                    .isEqualTo(PaymentStatus.COMPLETED);
                return; // Success!
            } catch (Exception e) {
                if (attempt == 9) {
                    throw e;
                }
                try {
                    Thread.sleep(1000); // Wait before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }
    
    @Test
    void testSystemStabilityUnderChaos() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        // Submit 100 payment requests under chaos
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    PaymentRequest request = new PaymentRequest(
                        "ACC" + (i % 10),
                        "ACC" + ((i + 1) % 10),
                        BigDecimal.TEN,
                        "USD"
                    );
                    orchestrator.processPayment(request);
                    completed.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify system remains stable: high success rate
        double successRate = (double) completed.get() / 100;
        assertThat(successRate).isGreaterThan(0.8); // 80%+ success
    }
}
```

---

## Phase 2e: Compensating Transactions

### Concept: Saga Rollback

When a payment fails, automatically reverse all operations:
```
Payment Flow:
  1. Debit account ✓
  2. Check fraud ✗ (REJECTED)
  3. Compensation: Credit account ✓
  Result: Account back to original balance
```

### Step 1: Create Compensation Log

**File**: `payment-service/src/main/resources/db/migration/V005__compensations.sql`

```sql
CREATE TABLE compensation_log (
    compensation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    compensation_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_compensation_payment_id 
ON compensation_log(payment_id);

CREATE INDEX idx_compensation_status 
ON compensation_log(status);
```

### Step 2: Compensation Logic

**File**: `payment-service/src/main/java/com/payments/platform/payment/service/PaymentOrchestrator.java`

```java
@Service
public class PaymentOrchestrator {
    
    @Autowired
    private AccountClient accountClient;
    
    @Autowired
    private FraudClient fraudClient;
    
    @Autowired
    private NotificationClient notificationClient;
    
    @Autowired
    private CompensationService compensationService;
    
    @Transactional
    public Payment processPayment(PaymentRequest request, String correlationId) {
        Payment payment = new Payment(request);
        payment.setStatus(PaymentStatus.INITIATED);
        payment = paymentRepository.save(payment);
        
        try {
            // Step 1: Debit source account
            log.info("Debiting account: {}", request.getPayerAccountId());
            accountClient.debitAccount(
                request.getPayerAccountId(),
                new DebitRequest(request.getAmount()),
                correlationId
            );
            recordCompensation(payment, "ACCOUNT_DEBIT");
            
            // Step 2: Assess fraud risk
            log.info("Checking fraud: {}", request);
            FraudCheckResponse fraudCheck = fraudClient.evaluateRisk(
                request,
                correlationId
            );
            
            if (fraudCheck.decision() == FraudDecision.REJECT) {
                log.warn("Fraud detected, compensating");
                // Compensation: Refund account
                compensateAccountDebit(payment, request.getPayerAccountId(), 
                    request.getAmount(), correlationId);
                
                payment.setStatus(PaymentStatus.REJECTED);
                payment.setReason("Fraud detected");
                paymentRepository.save(payment);
                
                return payment;
            }
            
            // Step 3: Credit destination account
            log.info("Crediting account: {}", request.getPayeeAccountId());
            accountClient.creditAccount(
                request.getPayeeAccountId(),
                new CreditRequest(request.getAmount()),
                correlationId
            );
            
            // Step 4: Send notification
            notificationClient.sendPaymentConfirmation(payment, correlationId);
            
            payment.setStatus(PaymentStatus.COMPLETED);
            return paymentRepository.save(payment);
            
        } catch (Exception e) {
            log.error("Payment processing failed, compensating", e);
            compensateAccountDebit(payment, request.getPayerAccountId(),
                request.getAmount(), correlationId);
            
            payment.setStatus(PaymentStatus.FAILED);
            payment.setReason(e.getMessage());
            paymentRepository.save(payment);
            
            throw e;
        }
    }
    
    private void compensateAccountDebit(
        Payment payment,
        String accountId,
        BigDecimal amount,
        String correlationId) {
        
        try {
            log.info("Compensating: crediting account {} amount {}", 
                accountId, amount);
            
            accountClient.creditAccount(
                accountId,
                new CreditRequest(amount),
                correlationId
            );
            
            compensationService.recordCompensation(
                payment.getId(),
                "ACCOUNT_DEBIT_REVERSAL",
                "SUCCESS"
            );
        } catch (Exception e) {
            log.error("Compensation failed: could not credit account {}", 
                accountId, e);
            
            compensationService.recordCompensation(
                payment.getId(),
                "ACCOUNT_DEBIT_REVERSAL",
                "FAILED"
            );
            
            // Alert: manual intervention needed
            notifyCompensationFailure(payment, accountId, amount);
            throw e;
        }
    }
    
    private void recordCompensation(Payment payment, String type) {
        CompensationLog log = new CompensationLog();
        log.setCompensationId(UUID.randomUUID());
        log.setPaymentId(payment.getId());
        log.setCompensationType(type);
        log.setStatus("RECORDED");
        log.setCreatedAt(LocalDateTime.now());
        compensationLogRepository.save(log);
    }
}
```

**File**: `payment-service/src/main/java/com/payments/platform/payment/service/CompensationService.java`

```java
@Service
public class CompensationService {
    
    @Autowired
    private CompensationLogRepository compensationLogRepository;
    
    public void recordCompensation(
        UUID paymentId,
        String compensationType,
        String status) {
        
        CompensationLog log = new CompensationLog();
        log.setCompensationId(UUID.randomUUID());
        log.setPaymentId(paymentId);
        log.setCompensationType(compensationType);
        log.setStatus(status);
        log.setCreatedAt(LocalDateTime.now());
        
        if ("SUCCESS".equals(status)) {
            log.setExecutedAt(LocalDateTime.now());
        }
        
        compensationLogRepository.save(log);
    }
    
    public List<CompensationLog> getPaymentCompensations(UUID paymentId) {
        return compensationLogRepository.findByPaymentId(paymentId);
    }
}
```

---

## Success Metrics

### When Complete ✅

- [ ] Event sourcing running for Account service
- [ ] Can rebuild account balance at any point in time
- [ ] Kibana shows complete payment event chain
- [ ] Bulkhead prevents cascading failures (verified in tests)
- [ ] Chaos tests confirm automatic recovery (80%+ success)
- [ ] Failed payments automatically compensated
- [ ] Compensation audit trail complete
- [ ] All tests passing

### Verification Checklist

```bash
# 1. Event Sourcing
curl http://localhost:8081/api/v1/audit/accounts/ACC001/history

# 2. Balance at time
curl "http://localhost:8081/api/v1/audit/accounts/ACC001/balance-at?asOf=2026-09-21T14:00:00"

# 3. Payment events
curl "http://localhost:8083/api/v1/audit/payments/correlation-id-123/events"

# 4. Compensation log
curl http://localhost:8083/api/v1/audit/payments/payment-id-456/compensations
```

---

## Learning Outcomes

### You will understand:

- Event sourcing patterns and temporal queries
- Immutable event logs for audit trails
- State reconstruction from events
- Bulkhead thread pool isolation
- Chaos engineering testing methodologies
- Compensating transactions (saga rollback)
- Failure recovery patterns
- Audit trail design

### Skills gained:

- Fault-tolerant system design
- Distributed transaction coordination
- Chaos testing and verification
- Event-driven architecture
- Audit compliance and forensics
- State machine patterns

---

**Last Updated**: 2026-09-21  
**Status**: Ready for Phase 2b Implementation  
**Next**: Phase 2c (Performance Optimization)
