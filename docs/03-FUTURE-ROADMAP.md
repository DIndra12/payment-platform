# Future Roadmap

**Date:** 2026-11-20

## 15-Week Production Readiness Plan

**Phase 1: Resilience (Weeks 1-3)**
- Add Resilience4j: circuit breakers, retry logic, timeouts on Feign calls
- Kafka DLQ routing: consumers don't crash on bad messages
- Secrets management: Move DB passwords to K8s Secrets or Vault
- Dockerfiles: Containerize all 5 services + gateway
- Backup/restore procedure: PostgreSQL backups, disaster recovery docs
- **Exit Criteria:** No cascading failures; services survive brief outages

**Phase 2: Observability (Weeks 4-6)**
- OpenTelemetry + Jaeger: Distributed tracing across all services
- Log aggregation: EFK stack or CloudWatch Logs
- Grafana dashboards: Payment latency, error rates, throughput
- Alerting rules: PagerDuty integration for critical failures
- Audit logging: Who did what, when (compliance)
- **Exit Criteria:** Can trace a payment request end-to-end; see where it slows

**Phase 3: Deployment (Weeks 7-9)**
- Kubernetes manifests: Deployment, Service, ConfigMap, StatefulSet for Kafka/DB
- Helm charts: Templated multi-environment deploys (staging, prod)
- CI/CD pipeline: GitHub Actions → staging auto-deploy, manual prod approval
- Blue-green deployment: Zero-downtime releases
- **Exit Criteria:** Can deploy a new service version without downtime

**Phase 4: Scale (Weeks 10-12)**
- Bulkhead pattern: Thread isolation per service endpoint
- Concurrency tests: Race conditions, concurrent idempotency keys
- Horizontal Pod Autoscaling (HPA): Scale services by CPU/memory
- Load testing: JMeter → 100 req/sec, measure p95 latency
- Database read replicas: Offload transaction-history queries
- **Exit Criteria:** Sustain 100 req/sec with <500ms p95 latency

**Phase 5: Security (Weeks 13-14)**
- API Gateway enhancements: Service-to-service OAuth2, mTLS
- Rate limiting per customer (not just per user)
- Input validation & sanitization: XSS, SQL injection prevention
- RBAC: Different roles (customer, merchant, admin) with scoped permissions
- **Exit Criteria:** No OWASP Top 10 vulnerabilities; pen-test passes

**Phase 6: Documentation (Week 15)**
- Troubleshooting runbooks: Common failures and how to fix them
- Deployment procedures: How to roll out a new version
- Disaster recovery plan: What to do if Kafka/DB goes down
- OpenAPI/Swagger: Auto-generated API docs from annotations
- **Exit Criteria:** New oncall engineer can triage an incident

**Timeline & Resources**
- **Total effort:** ~71 person-weeks
- **Recommended team:** 3-4 FTE over 15 weeks
- **Critical path:** Resilience → Dockerfiles → K8s → CD → Load test
- **Parallelization:** Observability and security can start in Phase 1

**Success Metrics (at Phase 3 end)**
- 99.5% uptime (measured over 1 month)
- Payment latency p95 < 500ms
- Error rate < 0.1%
- Zero downtime blue-green deploys
- All requests traceable end-to-end
- <2 minute MTTR (mean time to recovery) for common failures

## AWS Patterns Coverage

**Current Score: 6/10 (60% coverage)**

**Mostly Covered (60-70%)**
1. **Three-tier** (ALB + EC2 + RDS) — Logical separation exists, needs AWS deployment
2. **Event-driven** (EventBridge + Lambda) — Kafka covers concept, needs EventBridge + Lambda
3. **Container app** (ECS/EKS + ALB) — Dockerfiles ready, needs ECS/EKS deployment

**Partially Covered (20-50%)**
4. **Queue worker** (SQS + Lambda) — Outbox→Kafka pattern; could switch to SQS
5. **Serverless API** (API Gateway + Lambda) — 20% concept only; all Spring Boot (not Lambda)
6. **Backup + DR** — 40% local scripts exist; needs AWS Backup + S3

**Not Covered (0-10%)**
7. **Static website** (S3 + CloudFront) — No static hosting
8. **Data lake** (S3 + Glue + Athena) — No analytics pipeline
9. **Streaming** (Kinesis + Lambda) — 30% Kafka instead of Kinesis
10. **Multi-AZ** (ALB + ASG + RDS) — Single-zone local deployment

**3-Phase Implementation Plan**

**Phase 1 (Weeks 1-4): Foundation** → Patterns 1, 6, 9, 10
- ECS + ALB (replace Docker Compose)
- RDS Multi-AZ (replace local PostgreSQL)
- AWS Backup + S3 (backup/restore)
- Autoscaling Group (handle load)

**Phase 2 (Weeks 5-8): Event & Compute** → Patterns 3, 4, 5
- Lambda functions for async processing
- EventBridge for saga orchestration
- SQS queues for resilience (replace Kafka)
- API Gateway + Lambda (serverless option)

**Phase 3 (Weeks 9-12): Analytics & Distribution** → Patterns 2, 7, 8
- CloudFront + S3 (static docs, frontend)
- Kinesis (stream processing alternative to Kafka)
- Glue + Athena (data lake for analytics)

**Why We're Ready for AWS**
- Microservices architecture is sound (saga, outbox, CQRS work as-is)
- All services are Dockerized
- Event-driven design maps cleanly to EventBridge
- Local infrastructure setup shows ops discipline
