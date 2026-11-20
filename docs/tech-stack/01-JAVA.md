# Java 21 LTS

**Current Version:** 21 LTS  
**Used In:** All 5 services, API Gateway

## What It Is

Java 21 is a Long-Term Support (LTS) release with modern language features, improved performance, and excellent cloud support. It's the runtime that powers Spring Boot 3.x applications.

## Where & Why We Use It

**Why Java 21?**
- LTS (Long-Term Support until 2031)
- Virtual threads (preview feature) for better scalability
- Pattern matching enhancements
- Record classes for DTOs
- Native image support via GraalVM
- Excellent Spring Boot 3.x integration
- Large ecosystem of proven libraries

**Our Usage:**
- All microservices bytecode: Java 21 (major version 70)
- Maven compiler plugin: target 21, source 21
- IDE: Configure project SDK to Java 21

## How It Works

```java
// Java 21 features used in our codebase

// 1. Record classes (immutable DTOs)
record PaymentRequest(String id, BigDecimal amount, String currency) {}

// 2. Pattern matching (in validations)
if (payment instanceof PaymentRequest pr && pr.amount().signum() > 0) {
    // process payment
}

// 3. Text blocks (for SQL, JSON)
String query = """
    SELECT * FROM payments
    WHERE status = ?
    """;
```

## Configuration

**Maven pom.xml:**
```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

**Local Setup:**
```bash
# Download from eclipse.temurin.net
# Set JAVA_HOME to Java 21 installation
# Verify: java -version
```

## Current Issues & Gotchas

| Issue | Cause | Fix |
| --- | --- | --- |
| JaCoCo warnings about bytecode version 70 | Java 21 bytecode newer than JaCoCo expects | Upgrade to latest JaCoCo; warnings are non-blocking |
| IDE doesn't recognize Java 21 syntax | SDK not configured | File → Project Structure → Set SDK to Java 21 |
| Compilation fails: "source release 21 not supported" | Old Maven version | Update to Maven 3.9+ |

## How to Improve & Learn

**Short Term (1-2 weeks):**
1. Study virtual threads (java.lang.Thread.ofVirtual())
   - Lightweight threads for high concurrency
   - Use in async endpoints, Kafka consumers
   
2. Master record classes
   - Replace Lombok @Data with records
   - Immutable by default, cleaner code

3. Pattern matching in validation
   - Replace instanceof + casting with patterns
   - Cleaner null checks, type guards

**Medium Term (1-2 months):**
1. Experiment with GraalVM native images
   - Faster startup, smaller memory footprint
   - Build tool: `mvn native:compile` (via Spring Native plugin)
   
2. Sealed classes for domain models
   - Restrict inheritance (Payment vs Account service models)
   - Better type safety
   
3. Text blocks for SQL/JSON
   - Replace string concatenation with triple-quoted strings
   - Improves readability

**Long Term (3+ months):**
1. Migrate to virtual threads in production
   - Rewrite thread pools with virtual thread executors
   - Expect 5-10x concurrency improvement
   
2. Build container images with native compilation
   - Reduce image size from ~300MB to ~100MB
   - Start time: 2s → 50ms

## Learning Resources

- **Official:** https://docs.oracle.com/en/java/javase/21/
- **Virtual Threads:** https://openjdk.org/jeps/444
- **Record Classes:** https://openjdk.org/jeps/395
- **GraalVM Native Image:** https://www.graalvm.org/native-image/
- **Book:** "Modern Java in Action" (Raoul-Gabriel Urma, Alan Mycroft)

## Next Steps

- [ ] Update all services to use record classes for DTOs
- [ ] Add virtual thread executor to payment service (high-throughput)
- [ ] Test GraalVM native image build in CI
- [ ] Document Java 21 features used in architectural decisions
