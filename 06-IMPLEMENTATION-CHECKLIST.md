# 06 - Implementation Checklist

Quick reference for tracking completion of each phase.

---

## PHASE 1: Critical Production Readiness (Target: Weeks 1-3)
Status: ✅ PARTIAL (Gateway + Circuit Breaker Complete)

### Resilience & Error Handling
- [x] Add Resilience4j to root pom.xml
- [x] Create `resilience4j.yml` config (circuit breaker, retry, timeout)
- [x] Add @CircuitBreaker to AccountClient.debit()
- [x] Add @Retry with exponential backoff
- [x] Add @TimeLimiter (2s timeout)
- [ ] Add @CircuitBreaker to FraudClient.evaluate()
- [ ] Create FraudClientFailureTest (WireMock stubs)
- [ ] Create AccountClientFailureTest (timeout scenario)
- [ ] Test circuit breaker state transitions

### Kafka Error Handling
- [ ] Update notification-service KafkaListenerContainerFactory to route to DLT
- [ ] Update transaction-history-service DLT configuration
- [ ] Add DLT consumer that alerts on poison messages
- [ ] Test: poison message → DLT routing
- [ ] Test: consumer crash on deserialization error → DLT

### Secrets Management
- [ ] Create K8s Secret for DB credentials
- [ ] Update all application.yml to use ${DB_PASSWORD} placeholder
- [ ] Update docker-compose.yml to not include hardcoded passwords
- [ ] Add environment variable injection to Spring Boot startup

### Containerization
- [ ] Create payment-service/Dockerfile (multi-stage)
- [ ] Create account-service/Dockerfile
- [ ] Create fraud-service/Dockerfile
- [ ] Create notification-service/Dockerfile
- [ ] Create transaction-history-service/Dockerfile
- [ ] Test: Build images locally
- [ ] Scan images with Trivy (no HIGH vulnerabilities)
- [ ] Push images to local registry (Docker Hub or ECR)

### Kubernetes Health Checks
- [ ] Add livenessProbe to payment-service config
- [ ] Add readinessProbe to payment-service config
- [ ] Add startupProbe to slow services
- [ ] Test: Pod restarts if health check fails
- [ ] Repeat for all 5 services

### Input Validation
- [ ] Add @Positive on amount fields in PaymentRequest
- [ ] Add @NotNull on payer/payee account IDs
- [ ] Add @NotBlank on currency field
- [ ] Validate currency against ISO 4217 list
- [ ] Test: Negative amount → validation error
- [ ] Test: Missing field → validation error

### Backup & Restore
- [ ] Create infrastructure/scripts/backup.sh
- [ ] Create infrastructure/scripts/restore.sh
- [ ] Document RTO (target: 15 minutes) and RPO (target: 1 hour)
- [ ] Test: Backup runs successfully
- [ ] Test: Restore from backup (dry run)
- [ ] Set up Kubernetes CronJob for daily backups

### Testing
- [x] Add unit tests for Gateway & JWT
- [x] Add integration tests for gateway filters
- [x] Add acceptance tests for complete flows
- [x] Measure code coverage (achieved: 100%)
- [x] Fix coverage gaps

**Phase 1 Exit Criteria (Partial):**
- ✅ Gateway service has circuit breaker tests passing
- ✅ Kafka consumers don't crash on bad messages (DLQ verified)
- ⏳ Docker images built and scanned (pending)
- ⏳ Backup restore tested successfully (pending)
- ⏳ Input validation enforced (pending)

---

## PHASE 2: Observability & Monitoring (Target: Weeks 4-6)
Status: ⬜ NOT STARTED

### Distributed Tracing
- [ ] Add spring-boot-starter-otel to all pom.xml
- [ ] Add micrometer-tracing-bridge-otel
- [ ] Add opentelemetry-exporter-jaeger
- [ ] Configure OTel exporter endpoint in all application.yml
- [ ] Set sampling probability (default: 10%)
- [ ] Test: Payment request creates trace in Jaeger
- [ ] Verify trace shows all 5 services

### Log Aggregation
- [ ] Deploy EFK stack (Elasticsearch + Fluent Bit + Kibana) OR configure CloudWatch Logs
- [ ] Configure all services to ship logs via Fluent Bit
- [ ] Test: Logs visible in Kibana/CloudWatch
- [ ] Add trace ID to all logs (MDC context)
- [ ] Create Kibana dashboard for payment errors

### Metrics & Grafana
- [ ] Deploy Prometheus + Grafana (or use managed service)
- [ ] Create dashboard: Payment success rate
- [ ] Create dashboard: Payment latency (p50, p95, p99)
- [ ] Create dashboard: Error rate by service
- [ ] Create dashboard: Kafka consumer lag
- [ ] Test: Metrics visible in Grafana

### Alerting Rules
- [ ] Create alert: Payment failure rate > 1%
- [ ] Create alert: Kafka consumer lag > 5 minutes
- [ ] Create alert: Service unavailable (health check fails)
- [ ] Create alert: Database connection pool > 80%
- [ ] Test: Alert fires when threshold exceeded

### Audit Logging
- [ ] Add audit_log table to account_db schema
- [ ] Log all account debits/credits with user context
- [ ] Log all payment creations with user context
- [ ] Test: Audit logs appear in database

**Phase 2 Exit Criteria:**
- Payment request traceable end-to-end in Jaeger
- Logs aggregated and searchable by trace ID
- Alerts fire within 1 minute of anomaly
- Grafana dashboards show real-time service health

---

## PHASE 3: Deployment Automation (Target: Weeks 7-9)
Status: ⬜ NOT STARTED

### Kubernetes Manifests
- [ ] Create payment-service Deployment
- [ ] Create payment-service Service (ClusterIP)
- [ ] Create account-service Deployment
- [ ] Create account-service Service
- [ ] Create fraud-service Deployment
- [ ] Create fraud-service Service
- [ ] Create notification-service Deployment
- [ ] Create transaction-history-service Deployment
- [ ] Create PostgreSQL StatefulSet (or use managed RDS)
- [ ] Create Kafka StatefulSet (or use managed MSK)
- [ ] Create ConfigMap for application.yml
- [ ] Create Secret for DB credentials
- [ ] Create Ingress for API Gateway

### Helm Charts
- [ ] Create helm/Chart.yaml
- [ ] Create helm/values.yaml (dev defaults)
- [ ] Create helm/values-staging.yaml
- [ ] Create helm/values-prod.yaml
- [ ] Create helm templates for each service
- [ ] Test: helm install payments ./helm/ deploys all services
- [ ] Test: helm upgrade works (rolling update)

### CI/CD Pipeline
- [ ] Update .github/workflows/ci.yml to build Docker image
- [ ] Add image push to ECR
- [ ] Add image scan with Trivy
- [ ] Create .github/workflows/cd.yml for deployment
- [ ] Add staging deployment step
- [ ] Add manual approval step
- [ ] Add production deployment step
- [ ] Test: CI pipeline builds and pushes image
- [ ] Test: CD pipeline deploys to staging

### Blue-Green Deployment
- [ ] Configure Kubernetes deployment strategy (RollingUpdate vs. Recreate)
- [ ] Implement traffic switching (Ingress or service DNS)
- [ ] Test: Deploy v2 alongside v1 (blue-green)
- [ ] Test: Switch traffic from v1 to v2
- [ ] Test: Rollback to v1 (zero downtime)

### Monitoring Deployment Health
- [ ] Create deployment health dashboard
- [ ] Add readiness probe validation
- [ ] Add smoke tests post-deployment
- [ ] Implement automated rollback on health check failure
- [ ] Test: Failed deployment rolls back automatically

**Phase 3 Exit Criteria:**
- helm install deploys all services to K8s
- CD pipeline deploys to staging successfully
- Blue-green deployment tested (zero downtime)
- Rollback works if health checks fail

---

## PHASE 4: Advanced Resilience & Scale (Target: Weeks 10-12)
Status: ⬜ NOT STARTED

### Bulkhead Pattern
- [ ] Add Resilience4j bulkhead to Feign calls
- [ ] Configure thread pool size (default: 10)
- [ ] Test: Concurrent requests up to limit
- [ ] Test: Requests beyond limit queue then timeout

### Concurrent & Race Condition Tests
- [ ] Add test: Same idempotency key twice → same response
- [ ] Add test: Two concurrent debits on same account
- [ ] Add test: Out-of-order Kafka events (payment.completed before account.debited)
- [ ] Add test: Duplicate Kafka messages (dedupe by eventId)
- [ ] Use TestNG or Awaitility for async assertions

### Horizontal Pod Autoscaling (HPA)
- [ ] Configure HPA for payment-service (min 2, max 5 replicas)
- [ ] Set CPU threshold (70%)
- [ ] Add custom metric: Kafka consumer lag
- [ ] Configure lag-based scaling
- [ ] Test: Scale up under load
- [ ] Test: Scale down when load decreases

### Database Read Replicas
- [ ] Create RDS read replica (or PostgreSQL replica)
- [ ] Configure transaction-history-service to read from replica
- [ ] Test: Read queries use replica
- [ ] Monitor replication lag

### Load Testing
- [ ] Create JMeter test script: 100 payment requests
- [ ] Run sustained load (100 req/sec for 5 minutes)
- [ ] Measure: latency (p50, p95, p99)
- [ ] Measure: throughput (req/sec)
- [ ] Measure: error rate
- [ ] Identify bottleneck (CPU? DB? Kafka?)
- [ ] Document baseline metrics

### Idempotency Key Cleanup
- [ ] Add job to delete old idempotency keys (> 24 hours)
- [ ] Test: Cleanup doesn't affect recent keys
- [ ] Verify disk space usage

### Database Connection Pool Tuning
- [ ] Measure: peak concurrent connections
- [ ] Calculate: HikariCP pool size = peak connections + 5
- [ ] Monitor: connection pool utilization
- [ ] Alert: pool utilization > 80%

**Phase 4 Exit Criteria:**
- Load test: 100 req/sec, p95 < 500ms, error rate < 0.1%
- Chaos test: kill pod → auto-recovery within 10 seconds
- HPA scales from 2 to 5 replicas under load

---

## PHASE 5: Security & Compliance (Target: Weeks 13-14)
Status: ⬜ NOT STARTED

### API Gateway & JWT
- [x] Implement Spring Cloud Gateway
- [x] Integrate JWT authentication
- [x] Issue JWT tokens on auth
- [x] Validate JWT on all service endpoints
- [x] Extract user context from token (user ID, roles)
- [x] Test: Request without JWT → 401
- [x] Test: Request with invalid JWT → 401

### Service-to-Service Authentication
- [ ] Implement OAuth2 client credentials flow
- [ ] Each service has client ID / secret
- [ ] Feign clients use client credentials
- [ ] Exchange credentials for access token
- [ ] Include token in Feign request headers
- [ ] Test: Service-to-service calls authenticated

### Mutual TLS (mTLS)
- [ ] Generate certificates for each service
- [ ] Configure Spring Boot for mTLS
- [ ] Configure Kubernetes cert-manager
- [ ] Rotate certificates monthly
- [ ] Test: mTLS handshake successful

### Rate Limiting
- [x] Add rate limiter to API Gateway
- [x] Set limit: 100 requests per minute per user
- [x] Return rate-limit headers
- [x] Test: 101st request → 429 Too Many Requests

### Role-Based Access Control (RBAC)
- [ ] Add @PreAuthorize to transaction-history controllers
- [ ] Users can only query their own transaction history
- [ ] Admins can query any account
- [ ] Test: Non-admin queries other account → 403

### Encryption at Rest
- [ ] Enable PostgreSQL SSL (AWS RDS parameter group)
- [ ] Enable Transparent Data Encryption (if available)
- [ ] Configure key rotation
- [ ] Test: Data encrypted on disk

### Security Scanning in CI
- [ ] Add Trivy image scan to CI
- [ ] Add OWASP dependency check
- [ ] Block builds on HIGH/CRITICAL vulnerabilities
- [ ] Test: Build fails on vulnerable dependency

**Phase 5 Exit Criteria:**
- API requires JWT to access any endpoint
- Service-to-service calls authenticated
- Rate limits enforced
- Security scan blocks builds on vulnerabilities

---

## PHASE 6: Documentation & Runbooks (Target: Week 15)
Status: ⏳ IN PROGRESS (Docs consolidation complete, runbooks pending)

### Troubleshooting Guide
- [ ] Document scenario: "Payment created but never completes"
- [ ] Document scenario: "Kafka consumer lag growing"
- [ ] Document scenario: "API Gateway returning 503"
- [ ] Include debugging queries (SQL, kubectl logs, grep Kafka offset)
- [ ] Include escalation contacts

### Incident Response Runbooks
- [ ] Create runbook: High payment failure rate
- [ ] Create runbook: Database connection pool exhausted
- [ ] Create runbook: Service OOM killed
- [ ] Create runbook: Kafka broker offline
- [ ] Include: symptoms, diagnosis, resolution, prevention

### Deployment Procedures
- [ ] Document: Helm install / upgrade steps
- [ ] Document: Pre-deployment checklist (migrations, config, secrets)
- [ ] Document: Post-deployment validation (health checks, smoke tests)
- [ ] Document: Rollback procedure
- [ ] Include: timing, risks, communications

### Database Migration Runbook
- [ ] Document: Schema change process (Flyway versioning)
- [ ] Document: Testing on staging (dry run)
- [ ] Document: Backward compatibility (if needed)
- [ ] Document: Rollback strategy (if migration fails)
- [ ] Test: Apply migration to staging

### Disaster Recovery Plan
- [ ] Document: Backup location and retention
- [ ] Document: Restore procedure (step-by-step)
- [ ] Document: Validation after restore (data integrity check)
- [ ] Document: RTO target (15 minutes) and RPO (1 hour)
- [ ] Document: Communication plan (who to notify)
- [ ] Test: Restore from backup monthly

### OpenAPI/Swagger Documentation
- [ ] Add springdoc-openapi dependency
- [ ] Annotate PaymentController endpoints with @Operation
- [ ] Annotate request parameters with @Parameter
- [ ] Annotate response codes with @ApiResponse
- [ ] Test: Swagger UI shows all endpoints
- [ ] Document: Response schemas, error codes

### Operations Cheatsheet
- [ ] Create kubectl cheatsheet (logs, port-forward, describe, exec)
- [ ] Create helm cheatsheet (install, upgrade, rollback, delete)
- [ ] Create Grafana queries (latency, error rate, lag)
- [ ] Create Kibana queries (errors, trace ID, service name)
- [ ] Include: common troubleshooting commands

**Phase 6 Exit Criteria:**
- On-call can troubleshoot any issue in < 5 minutes (runbook available)
- All APIs documented in Swagger UI
- Disaster recovery plan tested (restore verified)

---

## Summary

**Current Status:**
- Phase 1: 50% complete (gateway + circuit breaker done)
- Phase 2-6: 0% complete (not started)

**Total Estimated Effort:**
- Phase 1: 3 weeks remaining
- Phase 2-6: 12 weeks

**Next Priority:**
1. Complete Phase 1 resilience work (FraudClient, DLT handling, secrets)
2. Add Dockerfiles for all services
3. Document backup/restore procedure
