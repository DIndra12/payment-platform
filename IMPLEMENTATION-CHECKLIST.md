# Production-Ready Implementation Checklist

Quick reference for tracking completion of each phase.

---

## PHASE 1: Critical Production Readiness (Target: Weeks 1-3)
Status: ⬜ NOT STARTED

### Resilience & Error Handling
- [ ] Add Resilience4j to root pom.xml
- [ ] Create `resilience4j.yml` config (circuit breaker, retry, timeout)
- [ ] Add @CircuitBreaker to AccountClient.debit()
- [ ] Add @CircuitBreaker to FraudClient.evaluate()
- [ ] Add @Retry with exponential backoff
- [ ] Add @TimeLimiter (2s timeout)
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
- [ ] Add unit tests for PaymentOrchestratorService failures
- [ ] Add integration tests with Testcontainers
- [ ] Add acceptance tests for full payment flow
- [ ] Measure code coverage (target: 80%+)
- [ ] Fix coverage gaps

**Phase 1 Exit Criteria:**
- ✅ All services have circuit breaker tests passing
- ✅ Kafka consumers don't crash on bad messages (DLQ verified)
- ✅ Docker images built and scanned
- ✅ Backup restore tested successfully
- ✅ Input validation enforced

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
- ✅ Payment request traceable end-to-end in Jaeger
- ✅ Logs aggregated and searchable by trace ID
- ✅ Alerts fire within 1 minute of anomaly
- ✅ Grafana dashboards show real-time service health

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
- ✅ helm install deploys all services to K8s
- ✅ CD pipeline deploys to staging successfully
- ✅ Blue-green deployment tested (zero downtime)
- ✅ Rollback works if health checks fail

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
- ✅ Load test: 100 req/sec, p95 < 500ms, error rate < 0.1%
- ✅ Chaos test: kill pod → auto-recovery within 10 seconds
- ✅ HPA scales from 2 to 5 replicas under load

---

## PHASE 5: Security & Compliance (Target: Weeks 13-14)
Status: ⬜ NOT STARTED

### API Gateway & JWT
- [ ] Implement Spring Cloud Gateway (or AWS ALB)
- [ ] Integrate with Keycloak / Auth0 / AWS Cognito
- [ ] Issue JWT tokens on login
- [ ] Validate JWT on all service endpoints
- [ ] Extract user context from token (user ID, roles)
- [ ] Test: Request without JWT → 401
- [ ] Test: Request with invalid JWT → 401

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
- [ ] Add rate limiter to API Gateway
- [ ] Set limit: 100 requests per minute per IP
- [ ] Return rate-limit headers
- [ ] Test: 101st request → 429 Too Many Requests

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
- ✅ API requires JWT to access any endpoint
- ✅ Service-to-service calls authenticated
- ✅ Rate limits enforced
- ✅ Security scan blocks builds on vulnerabilities

---

## PHASE 6: Documentation & Runbooks (Target: Week 15)
Status: ⬜ NOT STARTED

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
- ✅ On-call can troubleshoot any issue in < 5 minutes (runbook available)
- ✅ All APIs documented in Swagger UI
- ✅ Disaster recovery plan tested (restore verified)

---

## Cross-Phase Dependencies

```
Phase 1 (Resilience)
    ├─> Phase 2 (Observability) 
    │   └─> Phase 3 (Deployment)
    │       ├─> Phase 4 (Scale)
    │       └─> Phase 5 (Security)
    └─> Phase 6 (Docs)
```

**Critical Path:** Resilience → Dockerfiles → K8s → CD → Load Testing

---

## Team Assignments

### Phase 1 (Weeks 1-3)
- **Backend Dev 1:** Resilience4j + Kafka error handling
- **Backend Dev 2:** Dockerfiles + input validation
- **DevOps:** Secrets management + backup/restore
- **QA:** Testing + test gap analysis

### Phase 2 (Weeks 4-6)
- **Backend Dev 1:** OpenTelemetry + Jaeger setup
- **Platform Eng:** EFK stack or CloudWatch setup
- **DevOps:** Prometheus + Grafana + alerting
- **QA:** Load testing preparation

### Phase 3 (Weeks 7-9)
- **Backend Dev 1:** Kubernetes manifests
- **Backend Dev 2:** Helm charts
- **DevOps:** CI/CD pipeline + ECR setup
- **DevOps:** Blue-green deployment strategy

### Phase 4 (Weeks 10-12)
- **Backend Dev 1:** Bulkhead + concurrency tests
- **QA:** Load testing + chaos engineering
- **DevOps:** HPA + DB read replicas
- **Backend Dev 2:** Database tuning

### Phase 5 (Weeks 13-14)
- **Backend Dev 1:** API Gateway + JWT
- **Security Eng:** Secrets rotation + mTLS
- **DevOps:** RBAC + rate limiting
- **QA:** Security testing

### Phase 6 (Week 15)
- **Backend Dev 1:** Troubleshooting guide + runbooks
- **DevOps:** Operations cheatsheet
- **Tech Writer:** OpenAPI/Swagger + deployment docs

---

## Weekly Check-Ins

Every week, update this checklist:

- [ ] Week 1: Resilience4j basics working
- [ ] Week 2: Dockerfiles building, Kafka DLQ routing
- [ ] Week 3: Backup/restore tested
- [ ] Week 4: Jaeger tracing working
- [ ] Week 5: Grafana dashboards live
- [ ] Week 6: Alerts firing correctly
- [ ] Week 7: K8s manifests deployed to staging
- [ ] Week 8: Helm charts working
- [ ] Week 9: CD pipeline live
- [ ] Week 10: HPA scaling
- [ ] Week 11: Load test passing
- [ ] Week 12: Blue-green deployment ready
- [ ] Week 13: JWT authentication working
- [ ] Week 14: mTLS verified
- [ ] Week 15: Runbooks and docs complete

---

## Success Criteria (Final Validation)

**End of Phase 3 (Production-Ready for Beta):**
- ✅ 99.5% uptime (< 3.5 hours downtime/month)
- ✅ Payment latency p95 < 500ms
- ✅ Error rate < 0.1%
- ✅ Zero downtime deploys (blue-green verified)
- ✅ All requests traceable (Jaeger)

**End of Phase 6 (Production-Ready for GA):**
- ✅ RTO < 15 minutes (backup restore tested)
- ✅ On-call incident resolution < 5 minutes (runbooks available)
- ✅ All endpoints documented (Swagger)
- ✅ All security requirements met

---

## Notes

- Phases can run in parallel (e.g., Phase 1 & 2 overlap in weeks 4-6)
- Each phase should have code review before merging
- Each phase should have PR description with rationale
- Update this checklist weekly during standup
- After Phase 3, system is safe for production beta
- After Phase 6, system is safe for production GA

---

