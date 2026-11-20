# Spring Boot 3.x

**Current Version:** 3.1+  
**Used In:** All 5 services, API Gateway

## What It Is

Spring Boot is a framework for building production-grade Spring applications with minimal configuration. Version 3.x requires Java 17+ and brings cloud-native features, GraalVM support, and improved performance.

## Where & Why We Use It

**Why Spring Boot 3.x?**
- Autoconfiguration eliminates boilerplate
- Cloud-native (12-factor app ready)
- Native image support (GraalVM)
- Excellent ecosystem (Data, Cloud, Security)
- Embedded Tomcat/Netty (no deployment WAR)
- Actuator for health checks, metrics
- Spring profiles for multi-environment config

**Our Usage:**
- **Payment Service:** Saga orchestrator, main application entry point
- **Account Service:** JPA/Hibernate, transactional operations
- **Fraud Service:** REST API, decision logic
- **Notification Service:** Kafka consumer, async processing
- **Transaction History Service:** CQRS read model, query optimization
- **API Gateway:** Spring Cloud Gateway, routing + filtering

## Architecture Pattern

```
Application Layers (per service):
├── Controller (REST endpoints)
├── Service (business logic)
├── Repository (data access via Spring Data JPA)
├── Entity (JPA annotated POJO)
└── Configuration (Spring @Configuration classes)

Spring Boot handles:
├── Database connection pooling
├── Transaction management
├── Dependency injection
├── Embedded web server
└── Health checks (/actuator/health)
```

## Configuration Strategy

**Three levels of configuration (lowest to highest priority):**
1. `application.yml` (default, all environments)
2. `application-{profile}.yml` (profile-specific: dev, staging, prod)
3. Environment variables (CI/CD secrets, runtime overrides)

**Example:**
```yaml
# application.yml (default)
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  profiles:
    active: dev

# application-dev.yml (local development)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment
  jpa:
    show-sql: true

# application-prod.yml (production)
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/payment
    hikari:
      maximum-pool-size: 20
```

## Current Implementation

**Services use:**
- Spring Data JPA for database access
- Spring Web for REST controllers
- Spring Cloud Gateway for routing
- Spring Kafka for event streaming
- Spring Cloud Circuit Breaker (via Feign + Hystrix, upgrading to Resilience4j)
- Spring Boot Actuator for health/metrics

**Dependency management:**
- Parent POM: `spring-boot-starter-parent:3.1.x`
- Common starters: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-kafka`

## How to Improve & Learn

**Short Term (1-2 weeks):**
1. Master Spring profiles
   - Create environment-specific configs
   - Use `@Profile` annotation for conditional beans
   - Test with `@SpringBootTest(properties=...)`

2. Optimize startup time
   - Measure startup: `mvn spring-boot:run`
   - Lazy initialization: `spring.main.lazy-initialization=true`
   - Track with Actuator: `/actuator/startup`

3. Add custom health checks
   - Implement `HealthIndicator` interface
   - Report Kafka/Database health at startup

**Medium Term (1-2 months):**
1. Implement actuator-based monitoring
   - Enable all endpoints: `/actuator/metrics`, `/actuator/prometheus`
   - Integrate with Grafana for dashboards
   - Set up alerting on critical metrics

2. Upgrade to native image
   - Add GraalVM hints for reflection (Kafka, Hibernate)
   - Build via Maven: `mvn native:compile`
   - Test startup time improvement

3. Add custom metrics
   ```java
   @Component
   public class PaymentMetrics {
       private final MeterRegistry meterRegistry;
       
       public void recordPaymentProcessed(long duration) {
           meterRegistry.timer("payment.processed").record(duration, TimeUnit.MILLISECONDS);
       }
   }
   ```

**Long Term (3+ months):**
1. Implement Spring Cloud Config
   - Centralized config server for all services
   - Dynamic config refresh without restart
   - Audit config changes

2. Add Spring Boot Admin
   - Web UI to view all services health
   - See application metrics and logs
   - Trigger endpoint actions

3. Optimize for cloud
   - Resource limits in K8s match Spring Boot config
   - Graceful shutdown: `server.shutdown=graceful`
   - Circuit breakers around all external calls

## Common Issues & Solutions

| Issue | Cause | Fix |
| --- | --- | --- |
| "No bean of type X found" | Missing @EnableAutoConfiguration or @Configuration | Check classpath, add spring-boot-starter dependency |
| Slow startup in Docker | Classpath scanning is slow | Add `-Dspring.jmx.enabled=false` to reduce startup |
| Cannot find DataSource | No database URL configured | Add `spring.datasource.url` to `application.yml` |
| Actuator endpoints return 404 | Actuator not included | Add `spring-boot-starter-actuator` dependency |

## Configuration Examples

**Minimal application.yml:**
```yaml
spring:
  application:
    name: payment-service
  datasource:
    url: jdbc:postgresql://localhost:5432/payment
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: payment-service
server:
  port: 8083
```

## Learning Resources

- **Official:** https://spring.io/projects/spring-boot
- **Spring Guides:** https://spring.io/guides
- **Spring Boot Docs:** https://docs.spring.io/spring-boot/docs/3.1.x/reference/html/
- **Actuator Guide:** https://spring.io/blog/2020/03/25/spring-boot-actuator
- **Book:** "Spring Boot in Action" (Craig Walls)

## Next Steps

- [ ] Add custom health indicators for all external services
- [ ] Implement Spring Boot Admin server
- [ ] Optimize startup time to <3 seconds
- [ ] Enable Spring Boot native image builds in CI
- [ ] Document all @Configuration classes and their purpose
