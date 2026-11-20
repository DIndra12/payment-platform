# Testcontainers 1.19+

**Current Version:** 1.19+  
**Used In:** Integration tests for all services

## What It Is

Testcontainers is a testing library that spins up Docker containers (PostgreSQL, Kafka, etc.) during test execution, providing real dependencies instead of mocks. This ensures tests match production reality.

## Where & Why We Use It

**Why Testcontainers?**
- Real DB/Kafka in tests (not mocks)
- Tests are closer to production behavior
- Automatic container cleanup
- Works with JUnit 5
- Parallel test execution (each test gets its own container)
- CI/CD friendly (just needs Docker)

**Our Usage:**
```
src/test/java/
├── unit/              # Mocked tests (fast, 5s)
├── integration/       # Testcontainers (slow, 30s)
└── acceptance/        # E2E flow tests
```

## Integration Test Example

**Before (mocked):**
```java
@SpringBootTest
class PaymentServiceTest {
    @MockBean
    private PaymentRepository paymentRepository;
    
    @Test
    void testCreatePayment() {
        // Test doesn't hit real database
        // Might not catch SQL errors, constraint violations
        when(paymentRepository.save(any())).thenReturn(new Payment());
    }
}
```

**After (Testcontainers):**
```java
@SpringBootTest
@Testcontainers
class PaymentServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void testCreatePayment() {
        // Test hits REAL database in container
        Payment payment = new Payment("123", BigDecimal.TEN, "USD");
        Payment saved = paymentRepository.save(payment);
        
        // Catches DB errors, constraint violations, etc.
        assertThat(saved.getId()).isNotNull();
    }
}
```

## Multi-Container Tests

**Testing with PostgreSQL + Kafka:**
```java
@SpringBootTest
@Testcontainers
class PaymentServiceE2ETest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    void testPaymentFlow() {
        // Create payment → DB + Kafka
        Payment payment = paymentService.create(new PaymentRequest());
        
        // Verify event in Kafka
        // Check database state
    }
}
```

## Testcontainers Configuration

**Maven dependency:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
```

**Testcontainers properties (optional):**
```properties
# testcontainers.properties
testcontainers.docker.client.strategy=org.testcontainers.dockerclient.strategies.DockerClientStrategyProvider$UnixSocketClientProviderStrategy
```

## How to Improve & Learn

**Short Term (1-2 weeks):**
1. Add more containers to tests
   - WireMock for HTTP mocking
   - Localstack for AWS services
   - Redis for caching tests

2. Optimize test performance
   - Reuse containers across tests
   - Use `@Testcontainers(disabledWithoutDocker = true)` for CI
   - Parallel test execution

3. Test edge cases
   ```java
   @Test
   void testPaymentWithDatabaseDown() {
       // Pause container to simulate downtime
       postgres.stop();
       
       assertThrows(DataAccessException.class, 
           () -> paymentService.create(request));
       
       postgres.start();
   }
   ```

**Medium Term (1-2 months):**
1. Advanced container features
   - Execute commands inside container
   - Copy files to/from container
   - Wait strategies (wait for logs, port availability)

2. Chaos testing
   - Inject network delays
   - Drop connections
   - Simulate resource constraints

3. Performance testing
   - Load test against real database
   - Measure query performance with containers
   - Benchmark concurrent access

**Long Term (3+ months):**
1. Docker Compose module (spin up full stack)
   ```java
   @Container
   static DockerComposeContainer<?> compose =
       new DockerComposeContainer<>(new File("docker-compose.yml"))
           .withExposedService("postgres_1", 5432);
   ```

2. Custom containers
   - Create containers for internal services
   - Test container orchestration

3. CI/CD integration
   - Run all tests in GitHub Actions
   - Fail fast on container startup failures
   - Cache Docker images

## Common Issues

| Issue | Cause | Fix |
| --- | --- | --- |
| "Cannot connect to Docker daemon" | Docker not running | Start Docker Desktop |
| Test hangs | Container not ready | Add wait strategy or increase timeout |
| "Resource limit exceeded" | Too many containers | Reduce parallel tests, reuse containers |
| Slow tests | Container startup overhead | Use `@Testcontainers` to reuse containers |
| Out of disk | Old container images | `docker image prune -a` |

## Test Pyramid Strategy

```
         /\
        /  \  Acceptance Tests (E2E with Testcontainers)
       /    \ Few, slow, full flow
      /------\
     /        \
    /          \ Integration Tests (Testcontainers)
   /            \ Many, medium speed, service tests
  /              \
 /________________\ Unit Tests (Mocked)
 Lots, fast, isolated
```

## Best Practices

1. **Use profiles for different test types**
   ```bash
   # Unit tests (no containers)
   mvn -P unit test
   
   # Integration tests (with containers)
   mvn -P integration test
   ```

2. **Keep containers lightweight**
   - Use alpine base images
   - Disable unnecessary services

3. **Fail fast**
   - Check Docker is running at test start
   - Skip expensive tests in CI if needed

4. **Document container setup**
   - Comment why each container is needed
   - Version pin base images

## Learning Resources

- **Official:** https://www.testcontainers.org/
- **Documentation:** https://www.testcontainers.org/quickstart/
- **Examples:** https://github.com/testcontainers/testcontainers-java/tree/main/examples
- **Blog:** https://www.testcontainers.org/blog/

## Next Steps

- [ ] Add Testcontainers to all integration tests
- [ ] Set up WireMock for HTTP client testing
- [ ] Implement chaos testing (inject failures)
- [ ] Optimize test performance (measure startup time)
- [ ] Document test pyramid strategy
- [ ] Add GitHub Actions integration for CI/CD tests
