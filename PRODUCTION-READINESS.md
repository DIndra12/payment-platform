# Production-Readiness Implementation Plan

**Project:** Payment Platform Microservices  
**Status:** Phase 0 (Foundation In Place)  
**Last Updated:** 2026-09-20  
**Target:** Production-ready by end of Phase 3

---

## Executive Summary

The payment platform has a **solid architectural foundation** with 5 well-designed microservices, proper saga orchestration, outbox pattern implementation, and organized test structure. However, it requires **significant work** across resilience, observability, security, and deployment to be truly production-ready.

### Current State
✅ Architecture & design solidified  
✅ Core services implemented (payment, account, fraud, notification, transaction-history)  
✅ Outbox pattern prevents data loss  
✅ Idempotency keys prevent duplicates  
✅ Multi-level test organization (unit, integration, acceptance)  
✅ Database migrations with Flyway  
✅ GitHub Actions CI/CD pipeline  

### Critical Gaps (Must Fix for Production)
❌ No circuit breakers, retries, timeouts on service calls  
❌ Kafka consumers crash on bad messages (no DLQ routing)  
❌ No distributed tracing (can't debug across services)  
❌ No Dockerfiles or Kubernetes manifests  
❌ No secrets management (hardcoded DB passwords)  
❌ No API authentication/authorization  
❌ No backup/restore procedure documented  

---

## Implementation Roadmap (6 Phases)

### Phase 1: Critical Production Readiness (Weeks 1-3)
**Goal:** Make system resilient enough to handle production traffic  
**Effort:** 12 weeks | **Team:** 2 backend + 1 ops

Priority tasks:
1. ✓ Add Resilience4j (circuit breakers, retries, timeouts) to service calls
2. ✓ Implement Kafka DLQ handling (no more consumer crashes)
3. ✓ Externalize secrets (DB passwords → K8s Secrets / AWS Secrets Manager)
4. ✓ Create Dockerfiles for all 5 services
5. ✓ Add Kubernetes health checks (liveness, readiness probes)
6. ✓ Implement backup/restore procedure
7. ✓ Harden input validation

### Phase 2: Observability & Monitoring (Weeks 4-6)
**Goal:** Understand production behavior; respond to incidents  
**Effort:** 11 weeks | **Team:** 1 backend + 1 platform + 1 ops

Priority tasks:
1. ✓ Wire OpenTelemetry + Jaeger distributed tracing
2. ✓ Set up EFK stack or CloudWatch Logs
3. ✓ Create Grafana dashboards & alerting rules
4. ✓ Add audit logging to sensitive operations

### Phase 3: Deployment Automation (Weeks 7-9)
**Goal:** Repeatable, safe deployments  
**Effort:** 16 weeks | **Team:** 2 backend + 2 ops

Priority tasks:
1. ✓ Write Kubernetes manifests for all services
2. ✓ Create Helm charts (templated deployments)
3. ✓ Set up ECR + CI image push
4. ✓ Implement CD pipeline (staging → prod)
5. ✓ Blue-green or canary deployment strategy

### Phase 4: Advanced Resilience & Scale (Weeks 10-12)
**Goal:** Handle edge cases, scale to production load  
**Effort:** 14 weeks | **Team:** 1 backend + 2 QA + 1 ops

Priority tasks:
1. ✓ Bulkhead pattern (thread pool isolation)
2. ✓ Comprehensive concurrent/race condition tests
3. ✓ Horizontal Pod Autoscaling (HPA)
4. ✓ Database read replicas
5. ✓ Load testing (JMeter 100 req/sec)

### Phase 5: Security & Compliance (Weeks 13-14)
**Goal:** Secure production deployment  
**Effort:** 13 weeks | **Team:** 1 backend + 1 security + 1 ops

Priority tasks:
1. ✓ API Gateway + JWT authentication
2. ✓ Service-to-service OAuth2
3. ✓ mTLS for inter-service communication
4. ✓ Rate limiting
5. ✓ Role-based access control

### Phase 6: Documentation & Runbooks (Week 15)
**Goal:** Empower on-call engineers  
**Effort:** 5 weeks | **Team:** 1 backend + 1 ops

Priority tasks:
1. ✓ Troubleshooting guide & incident runbooks
2. ✓ Deployment procedures
3. ✓ Disaster recovery checklist
4. ✓ OpenAPI/Swagger specs

---

## Critical Issues by Category

### 1. RESILIENCE & ERROR HANDLING (P0 - Blocker)

**Issue:** Service calls have no timeout, retry, or circuit breaker  
**Impact:** One slow service brings down entire payment system  
**Solution:** Add Resilience4j  
- Circuit breaker: stop calling failed services after 50% failure rate
- Retry: exponential backoff (max 3 attempts, 1s initial)
- Timeout: 2s for Fraud/Account calls; 5s for Kafka

**Estimated Effort:** Medium (1-2 weeks)

**File Changes Required:**
- `pom.xml`: Add `resilience4j-spring-boot3`, `resilience4j-feign`
- `payment-service/src/main/resources/application.yml`: Add Resilience4j config
- `payment-service/src/main/java/.../client/AccountClient.java`: Add `@CircuitBreaker`, `@Retry` annotations
- `payment-service/src/test/java/.../*Test.java`: Add failure scenario tests (WireMock stubs)

---

### 2. OBSERVABILITY (P0 - Blocker)

**Issue:** Cannot trace payment through all services; no distributed tracing  
**Impact:** Production incident means 2+ hours debugging across 5 services  
**Solution:** OpenTelemetry + Jaeger + EFK stack  
- Trace request end-to-end (payment → account, fraud → Kafka → notification, history)
- Centralize logs by trace ID
- Export metrics to Prometheus

**Estimated Effort:** Large (2-3 weeks)

**File Changes Required:**
- All pom.xml: Add `spring-boot-starter-otel`, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-jaeger`
- All application.yml: Add OTel config (exporter endpoint, sampling rate)
- All controllers/services: Propagate trace context in Feign + Kafka headers
- New: EFK Helm chart or CloudWatch Logs configuration

---

### 3. CONTAINERIZATION & KUBERNETES (P0 - Blocker)

**Issue:** No Dockerfiles; no K8s manifests; no deployment automation  
**Impact:** Cannot deploy to production  
**Solution:** Multi-stage Dockerfiles + K8s manifests + Helm charts  

**Estimated Effort:** Large (2-3 weeks)

**Files to Create:**
- `payment-service/Dockerfile` (multi-stage: builder → runtime)
- `account-service/Dockerfile`
- `fraud-service/Dockerfile`
- `notification-service/Dockerfile`
- `transaction-history-service/Dockerfile`
- `infrastructure/k8s/payment-service-deployment.yaml`
- `infrastructure/k8s/account-service-deployment.yaml`
- ... (and services, configmaps, secrets)
- `infrastructure/helm/Chart.yaml` + templates for all services

---

### 4. SECRETS MANAGEMENT (P0 - Blocker)

**Issue:** DB passwords hardcoded in `docker-compose.yml` and `application.yml`  
**Impact:** Source code contains production secrets  
**Solution:** Externalize secrets to K8s Secrets / AWS Secrets Manager  

**Estimated Effort:** Small (1 week)

**File Changes Required:**
- All `application.yml`: Replace hardcoded passwords with `${DB_PASSWORD}` placeholder
- K8s manifests: Create Secret objects
- CI/CD: Inject secrets at deployment time

---

### 5. TESTING (P1 - High Priority)

**Issue:** Test coverage incomplete; no failure scenario tests  
**Gap Summary:**
- No Feign client failure tests (timeout, 500 error, circuit breaker)
- No Kafka consumer error handling tests
- No integration tests between real services
- No concurrent/race condition tests
- No controller input validation tests

**Estimated Effort:** Medium (2-3 weeks)

**Tests to Add:**
- `PaymentOrchestrationFailureTest`: Account service timeout → payment marked FAILED
- `FraudDetectionFallbackTest`: Fraud service returns 500 → payment approved (or rejected)
- `KafkaConsumerErrorHandlingTest`: Poison message → routed to DLT
- `IdempotencyTest`: Same idempotency key twice → same response, no duplicate
- `ConcurrencyTest`: Two debits on same account simultaneously

---

### 6. DATABASE & BACKUPS (P0 - Blocker)

**Issue:** No backup procedure; data loss risk is critical  
**Impact:** 1 failed migration = data loss + downtime  
**Solution:** Automated backups + restore procedure + regular dry-runs  

**Estimated Effort:** Small (1 week)

**File Changes Required:**
- Create `infrastructure/scripts/backup.sh`: Daily pg_dump + upload to S3
- Create `infrastructure/scripts/restore.sh`: Restore from backup + Flyway baseline
- Document: RTO (15 min), RPO (1 hour)
- K8s CronJob to run backup script daily

---

### 7. SECURITY (P1 - High Priority)

**Issues:**
- No authentication on API endpoints (anyone can query transaction history)
- No HTTPS (Feign calls use `http://localhost:PORT`)
- No input validation on amounts (negative amounts accepted?)
- No rate limiting

**Solutions:**
1. **API Gateway + JWT:** Implement Spring Cloud Gateway; issue JWT tokens (Keycloak)
2. **Input Validation:** Add `@Positive` on amount fields; validate UUIDs, currency codes
3. **HTTPS:** Enable in K8s (cert-manager + Let's Encrypt)
4. **Rate Limiting:** 100 req/min per IP on `/payments` endpoint

**Estimated Effort:** Large (2-3 weeks)

---

### 8. MONITORING & ALERTING (P1 - High Priority)

**Issue:** No alerting rules; can't detect payment failures in production  
**Impact:** Payments silently fail; customers discover hours later  
**Solution:** Prometheus + Grafana + alert rules  

**Alert Rules to Create:**
- Payment failure rate > 1% → page oncall
- Kafka consumer lag > 5 minutes → alert
- Service response time p99 > 1s → investigate
- Outbox event age > 10 minutes → data stuck

**Estimated Effort:** Medium (1-2 weeks)

---

## Test Coverage Current State vs. Production

| Service | Current Tests | Unit | Integration | Acceptance | Gap |
|---------|---|---|---|---|---|
| **payment-service** | 3 | 0 | 2 | 1 | ❌ Feign failures, full orchestration, concurrency |
| **account-service** | 1 | 0 | 0 | 1 | ❌ Ledger edge cases, concurrent debits, validation |
| **fraud-service** | 1 | 0 | 0 | 1 | ❌ Detection logic, rule evaluation, edge cases |
| **notification-service** | 10 | 4 | 5 | 1 | ❌ Error handling, DLQ routing, dedupe failure |
| **transaction-history-service** | 8 | 3 | 4 | 1 | ❌ Out-of-order events, projection completeness |

**Target State (Post-Phase 1):**
- All services: Unit tests for business logic (>80% coverage)
- All services: Integration tests with real DB/Kafka (critical paths)
- All services: Acceptance tests (happy path + error scenarios)
- **Total:** 40-50 tests per service; 90% code coverage

---

## Success Metrics

### By End of Phase 3 (Week 9)
1. ✅ Zero downtime deploys via blue-green (K8s + Helm)
2. ✅ 99.5% availability (< 3.5 hours downtime/month)
3. ✅ Payment latency p95 < 500ms
4. ✅ Error rate < 0.1% (fewer than 1 failed payment per 1000)
5. ✅ All requests traceable end-to-end (Jaeger)
6. ✅ Alerts fire within 1 minute of anomaly

### By End of Phase 6 (Week 15)
7. ✅ Backup restore tested monthly (RTO < 15 min)
8. ✅ On-call can troubleshoot any issue in < 5 minutes (runbook available)
9. ✅ All APIs documented (OpenAPI/Swagger)
10. ✅ Security audit passed (no hardcoded secrets, input validation on all endpoints)

---

## Next Steps (Week 1 Priority)

### Immediate Actions (This Week)
1. **Code Review:** Review current Feign client configuration (no resilience)
2. **Test Gap Analysis:** Run `mvn test` report; measure coverage by service
3. **Team Kickoff:** Assign phase responsibilities (1-2 backend devs for Phase 1)
4. **Setup:** Create branching strategy (feature branches, PR reviews)

### Phase 1 Kickoff (Week 2)
1. Add Resilience4j dependencies to root `pom.xml`
2. Create `FraudClientResilienceTest` (WireMock stubs for failures)
3. Start Dockerfile work (payment-service first)
4. Document backup/restore procedure

### Resources Needed
- Resilience4j Spring Boot starter docs
- Kubernetes documentation (deployment, service, configmap)
- Helm documentation (chart structure, templates)
- Jaeger / EFK setup guide

---

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Production Checklist](https://12factor.net/)

---

## Document History

| Date | Status | Changes |
|------|--------|---------|
| 2026-09-20 | Draft | Initial production-readiness analysis |

