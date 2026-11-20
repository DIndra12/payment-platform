# System Design

**Date:** 2026-11-20

## Architecture Overview

**5 Microservices:**
- **Payment Service** (8083) — Saga orchestrator; payment flow state machine
- **Account Service** (8081) — Ledger; debit/credit operations
- **Fraud Service** (8082) — Risk scoring; approve/reject decisions
- **Notification Service** (8084) — Kafka consumer; SMS delivery
- **Transaction History Service** (8085) — CQRS read model; query API

**Key Patterns:**
- **Saga Orchestration:** Payment Service coordinates INITIATED → FRAUD_CHECK → DEBITED → COMPLETED
- **Outbox Pattern:** Events in DB transaction with Kafka publish (no data loss)
- **Idempotency:** Clients send `Idempotency-Key` header; prevents duplicates on retry
- **CQRS:** Transaction-history-service denormalizes from 4 Kafka topics
- **Event-driven:** Notification and history services consume async events

**Communication:**
- Sync: Feign HTTP (payment → account/fraud; blocks, needs resilience)
- Async: Kafka topics (payment.completed, account.debited, account.credited, etc.)

**Tech Stack:**
- Spring Boot 3.x, Java 21, Maven multi-module
- PostgreSQL 16 (5 separate databases)
- Kafka for events
- Testcontainers for integration tests
- Docker local, K8s target

## Current Implementation Status

**What's Built ✅**
- Core architecture: 5 services, database schemas
- Saga orchestration with outbox pattern
- Idempotency support (Idempotency-Key header)
- API Gateway with JWT auth + rate limiting (100 req/min)
- Flyway migrations for schema versioning
- 27 test classes across 5 services (unit + integration + acceptance)
- GitHub Actions CI/CD pipeline
- Spring Boot Actuator health endpoints
- Local Docker Compose infrastructure (Kafka, PostgreSQL, all services)
- Postman collections for testing

**Production Readiness: NOT READY** (critical gaps below)

**Critical Gaps ❌**
1. **Resilience:** No circuit breakers, timeouts, retries → cascading failures
2. **Observability:** No distributed tracing (Jaeger/OpenTelemetry) → debugging impossible
3. **Containerization:** No Dockerfiles or K8s manifests
4. **Secrets Management:** DB passwords hardcoded in code
5. **Error Handling:** Kafka consumers crash on bad messages (no DLQ)
6. **Testing Gaps:** No Feign failure scenarios, no concurrency/race tests
7. **Backup:** No backup/restore procedure
8. **Security:** No input validation, no RBAC beyond gateway JWT
9. **Monitoring:** No alerting rules or dashboards

**What's Blocking Deployment**
- Lack of resilience patterns (Resilience4j needed)
- No proper error handling in Kafka consumers
- Feign HTTP calls have no timeout/retry/circuit-breaker
- Missing end-to-end tests (all 5 services + Kafka together)
- No deployment infrastructure (K8s manifests, Helm charts)
