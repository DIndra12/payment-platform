# E2E Testing

**Date:** 2026-11-20

## Local Stack Setup (Windows PC)

**How to Start**
1. Ensure Docker Desktop is running (env has DOCKER_HOST=tcp://localhost:2375)
2. Run `start-all.bat` from repo root
   - Spins up: PostgreSQL 16, Kafka, Keycloak, 5 services + API Gateway
   - Services open in separate terminal windows
   - Gateway available at http://localhost:8080
   - Keycloak admin at http://localhost:8090

**Current Infrastructure**
- **Docker Compose:** Defined in `infrastructure/docker-compose.yml`
- **Keycloak:** Realm = payment-platform, JWT signing key configured
- **Kafka:** 1 broker, topics for each service event (payment.completed, etc.)
- **PostgreSQL:** 5 databases (account, payment, fraud, notification, transaction-history)
- **Services:** Ports 8081-8085, Gateway on 8080

**Postman Collections** (verified 2026-11-20)
- `postman-collection-gateway.json` — Gateway + JWT auth tests
- `postman-collection.json` — Direct microservice calls
- Both pass Newman: 0 failures
- Collections stored in My Workspace (auto-synced from repo)

**Known Gotchas & Fixes**

| Issue | Cause | Fix |
| --- | --- | --- |
| "Connection refused" on all requests | Docker Desktop not running | Start Docker Desktop first |  
| Keycloak returns 401 on all requests | CRLF line endings broke init script | `.gitattributes` has `*.sh text eol=lf`; re-run `docker compose up -d --force-recreate keycloak-init` |
| Postman collections are outdated | Collections synced from repo main on 2026-11-20 | Refresh workspace collections when repo JSON changes |
| Payment flow hangs | Kafka broker down or slow | Check Docker containers: `docker ps` should show all 6 running |

**Testing E2E Locally**
- Use Postman collections or curl
- Happy path: Create payment → Check fraud → Debit account → Get transaction history
- Payment takes 2-3 seconds (Kafka events, Feign calls)
- Check logs in service terminal windows for errors

## Test Coverage Gaps

**P0 Blockers** (prevent deployment)
- Feign client failures: No tests for timeout, 500 errors, circuit breaker transitions, exponential backoff
- Kafka consumer errors: No tests for deserialization failures, consumer crash/restart, DLT routing
- Integration: No real end-to-end test (all 5 services + Kafka together)
- Concurrency: No test for same idempotency key submitted twice concurrently, two debits on same account, out-of-order Kafka events

**P1 High Priority**
- Controller input validation: No tests for negative amount, missing currency, invalid UUID
- Database constraints: No tests for unique constraint, foreign key, NOT NULL violations
- Outbox edge cases: No test for Kafka broker offline, scheduler stuck, race between payment creation and outbox poll

**P2 Nice-to-have**
- Performance tests (throughput at scale)
- Chaos tests (service degradation, pod kills)
- Security tests (input sanitization, XSS, SQL injection)

**Current Coverage Status**

| Service | Package | Current | Need |
| --- | --- | --- | --- |
| Account | ledger | 12% | +83% |
| Account | api | 8% | +87% |
| Fraud | detection | 12% | +83% |
| Fraud | api | 0% | +95% |
| Payment | orchestration | 24% | +71% |
| Payment | api | 0% | +95% |

Target: 95% coverage on all core packages via JaCoCo enforcement.

## Recommended Test Additions

**Quick Wins** (1-2 days each; high ROI)
1. Add WireMock stubs for Fraud/Account service failures in PaymentService tests
   - Tests timeout, 500 error, circuit breaker transitions
   - Effort: 1 day | Impact: Prevents cascading failures

2. Add `@Valid` annotation tests for controller DTOs
   - Tests negative amount, missing currency, invalid UUID
   - Effort: 1 day | Impact: Catches bad input before DB

3. Add Kafka consumer DLT routing test
   - Tests deserialization failures, poison messages
   - Effort: 1 day | Impact: Prevents silent failures

**Medium Effort** (3-5 days each; medium ROI)
1. Real end-to-end integration test with Docker Compose
   - All 5 services + Kafka; full payment flow
   - Effort: 4 days | Impact: Confidence in deployment

2. Concurrent payment test with same idempotency key
   - Two requests in parallel; ensure one-time-only execution
   - Effort: 3 days | Impact: Prevents duplicate charges

3. Out-of-order Kafka event test
   - Transaction-history-service handles events arriving out of sequence
   - Effort: 2 days | Impact: Prevents denormalization corruption

**Large Effort** (1-2 weeks each; nice-to-have)
- Full chaos testing suite (Testcontainers + Toxiproxy)
- Performance/load testing with JMeter (100 req/sec)
- Comprehensive concurrency test suite (TestNG + Awaitility)

**Recommended Priority**
1. Quick Wins (get these done first → unblocks P0)
2. Real E2E integration test (then safe to deploy)
3. Concurrency + out-of-order tests (production safety)
4. Large effort items (phase 2+)
