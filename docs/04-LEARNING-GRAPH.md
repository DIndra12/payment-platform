# Learning Graph

**Date:** 2026-11-20

## Milestone Timeline

**Completed Phases ✅**
- **2026-08-15** — Unit test infrastructure (Phase 1): Maven profiles, Surefire, JaCoCo setup
- **2026-09-20** — API Gateway (Phase 1): JWT auth, rate limiting, 3 filter layers
- **2026-09-21** — Documentation consolidated: 25+ files → 10 guides
- **2026-11-01** — Local E2E testing works end-to-end: Keycloak + gateway + Postman
- **2026-11-02** — AWS patterns analysis: 6/10 coverage, 3-phase roadmap
- **2026-11-20** — Postman + Docker fixes: CRLF issue resolved, collections verified

**Current Focus**
- Fixing test gaps (Feign failures, Kafka errors, concurrency)
- Preparing for resilience patterns (Resilience4j)
- Local infrastructure stable

**Next 6 Months (Expected)**
- **Dec 2026** — Phase 1: Resilience + Dockerfiles complete
- **Jan 2027** — Phase 2: Observability (Jaeger, Grafana)
- **Feb 2027** — Phase 3: K8s + CD pipeline
- **Mar 2027** — Phase 4: Scale testing (100 req/sec)
- **Apr 2027** — Phase 5: Security hardening
- **May 2027** — Phase 6: Documentation + production launch

**Effort Tracking**
- **Total person-weeks so far:** ~20 (architecture design, gateway, test infra)
- **Remaining to production:** ~71 person-weeks
- **Recommended velocity:** 3-4 FTE (71 weeks ÷ 3 FTE = ~6 months)

## Key Architecture Decisions & Trade-offs

**1. Saga Orchestration vs. Choreography**
- **Decision:** Saga orchestration (Payment Service is the orchestrator)
- **Why:** Clearer visibility into flow state; easier to debug and monitor
- **Trade-off:** Payment Service becomes stateful; needs careful error handling
- **Alternative rejected:** Choreography would scatter state across services

**2. Outbox Pattern for Event Delivery**
- **Decision:** Events written to DB in same transaction as domain data, then polled to Kafka
- **Why:** Guarantees exactly-once delivery; no data loss if Kafka is down
- **Trade-off:** Extra polling overhead; slightly delayed event delivery (100-200ms)
- **Alternative rejected:** Direct Kafka publish could lose events on crash

**3. 5 Separate PostgreSQL Databases**
- **Decision:** Each service owns its own database schema
- **Why:** Service isolation; independent scaling; can use different migration strategies
- **Trade-off:** Distributed transactions need saga pattern; querying across services requires eventual consistency
- **Alternative rejected:** Shared database would create tight coupling

**4. API Gateway + Feign for Routing**
- **Decision:** Spring Cloud Gateway + Feign HTTP clients
- **Why:** Simple, cloud-native, works with Spring Boot ecosystem
- **Trade-off:** Feign calls are blocking (need Resilience4j); no service mesh complexity
- **Alternative rejected:** Service mesh (Istio) adds operational overhead

**5. Kafka for Events (not RabbitMQ or SQS)**
- **Decision:** Kafka for event streaming
- **Why:** Event replay capability; audit trail for compliance; good fit for CQRS
- **Trade-off:** More operational overhead than SQS; requires tuning
- **Alternative rejected:** RabbitMQ lacks replay; SQS has shorter retention

**6. Spring Boot 3.x + Java 21**
- **Decision:** Latest stable Spring Boot with Java 21
- **Why:** Native compilation support (GraalVM); better cloud performance; LTS stability
- **Trade-off:** Some libraries haven't migrated yet; requires Java 21 in production
- **Alternative rejected:** Older Spring Boot (2.x) lacks cloud-native features

**7. Testcontainers for Integration Tests**
- **Decision:** Real DB/Kafka containers in tests via Testcontainers
- **Why:** Confidence that tests match production; faster feedback than mocks
- **Trade-off:** Slower test suite (~30s vs ~5s for unit tests)
- **Alternative rejected:** Mocks give false confidence; mismatches slip to production

**8. 95% JaCoCo Coverage Target**
- **Decision:** Enforce 95% line coverage on core packages
- **Why:** Catches untested error paths; forces developers to think about edge cases
- **Trade-off:** Some edge cases (network chaos) hard to test with high coverage
- **Alternative rejected:** No enforcement leads to coverage creep

**9. Idempotency via Idempotency-Key Header**
- **Decision:** Client provides UUID; payment service deduplicates on key
- **Why:** Prevents duplicate charges; simple, works with any client
- **Trade-off:** Need to store keys in DB (small overhead); expiry policy needed
- **Alternative rejected:** Server-generated keys don't prevent user retries

**10. CQRS for Transaction History**
- **Decision:** Transaction-history-service builds denormalized view from Kafka events
- **Why:** Query service is fast (denormalized); separation of concerns; independent scaling
- **Trade-off:** Eventual consistency (not real-time); need to handle events out of order
- **Alternative rejected:** Querying directly from account/payment services would create tight coupling
