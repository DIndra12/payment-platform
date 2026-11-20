# Technology Stack Overview

**Last Updated:** 2026-11-20

This document provides an overview of all technologies used in the payment platform, their purpose, usage patterns, and improvement opportunities.

## Core Technologies

| Tech | Version | Purpose | Why Chosen | Learn More |
| --- | --- | --- | --- | --- |
| Java | 21 LTS | Programming language | Modern JVM, GraalVM native support, cloud-first features | [java/](tech-stack/01-JAVA.md) |
| Spring Boot | 3.x | Application framework | Cloud-native, excellent ecosystem, battle-tested | [spring-boot/](tech-stack/02-SPRING-BOOT.md) |
| Maven | 3.9+ | Build tool | Multi-module projects, dependency management | [maven/](tech-stack/03-MAVEN.md) |
| PostgreSQL | 16 | Relational database | ACID compliance, distributed, production-grade | [postgresql/](tech-stack/04-POSTGRESQL.md) |
| Kafka | 3.x | Event streaming | Event replay, audit trails, CQRS enabler | [kafka/](tech-stack/05-KAFKA.md) |

## Infrastructure & Deployment

| Tech | Version | Purpose | Why Chosen | Learn More |
| --- | --- | --- | --- | --- |
| Docker | 20.x | Containerization | Consistent dev/prod, easy local testing | [docker/](tech-stack/06-DOCKER.md) |
| Kubernetes | 1.28+ | Orchestration | Auto-scaling, self-healing, cloud-native | [kubernetes/](tech-stack/07-KUBERNETES.md) |
| Keycloak | 22.x | Identity provider | OAuth2/OIDC, JWT tokens, multi-tenancy | [keycloak/](tech-stack/08-KEYCLOAK.md) |

## Observability & Quality

| Tech | Version | Purpose | Why Chosen | Learn More |
| --- | --- | --- | --- | --- |
| JaCoCo | 0.8.x | Code coverage | Enforces test quality, tracks regressions | [jacoco/](tech-stack/09-JACOCO.md) |
| Testcontainers | 1.19+ | Integration testing | Real DB/Kafka in tests, confidence in CI | [testcontainers/](tech-stack/10-TESTCONTAINERS.md) |
| GitHub Actions | - | CI/CD | Native GitHub integration, free for open source | [github-actions/](tech-stack/11-GITHUB-ACTIONS.md) |

## Communication & Testing

| Tech | Version | Purpose | Why Chosen | Learn More |
| --- | --- | --- | --- | --- |
| Spring Cloud Gateway | 4.x | API routing | Simple, cloud-native, works with Feign | [gateway/](tech-stack/12-SPRING-CLOUD-GATEWAY.md) |
| Feign | 13.x | HTTP client | Declarative, works with Resilience4j | [feign/](tech-stack/13-FEIGN.md) |
| Postman | - | API testing | Collections, environments, workflows | [postman/](tech-stack/14-POSTMAN.md) |

## Planned Technologies (Phase 2+)

| Tech | Phase | Purpose | Integration |
| --- | --- | --- | --- |
| Resilience4j | 1 | Circuit breakers, retries, timeouts | Wrap Feign calls |
| OpenTelemetry | 2 | Distributed tracing instrumentation | All services |
| Jaeger | 2 | Trace visualization, storage | Query traces end-to-end |
| Grafana | 2 | Dashboards and alerting | Visualize metrics |
| Helm | 3 | Kubernetes templating | Package deployments |

## Learning Path

1. **Foundation (Week 1):** Java 21, Maven multi-module projects
2. **Framework (Week 2):** Spring Boot 3.x, autoconfiguration, profiles
3. **Data (Week 3):** PostgreSQL, Flyway migrations, connection pooling
4. **Events (Week 4):** Kafka basics, topics, partitions, consumer groups
5. **Testing (Week 5):** JUnit 5, Mockito, Testcontainers, integration tests
6. **Deployment (Week 6):** Docker, Docker Compose, local stack
7. **API (Week 7):** Spring Cloud Gateway, JWT, rate limiting
8. **CI/CD (Week 8):** GitHub Actions workflows, automated testing
9. **Quality (Week 9):** JaCoCo enforcement, coverage targets
10. **Advanced (Week 10+):** Resilience4j, distributed tracing, Kubernetes

## Quick Reference

**Local Development:**
```bash
# Start local infrastructure
./start-all.bat

# Run all tests
./mvnw clean test

# Run with coverage
./mvnw -P coverage test

# View coverage report
start target/site/jacoco/index.html
```

**Common Commands by Tech:**
- **Maven:** `mvn clean install`, `mvn test`, `mvn -P coverage verify`
- **Docker:** `docker ps`, `docker logs <container>`, `docker-compose up -d`
- **Kafka:** Topic list, consumer group info (via CLI in Kafka container)
- **PostgreSQL:** Connect via psql, run Flyway migrations
- **GitHub Actions:** View in GitHub → Actions tab

---

See individual tech documentation files for deep dives, troubleshooting, and improvement opportunities.
