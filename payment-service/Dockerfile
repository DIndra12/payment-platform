# Multi-stage build: Reduces final image size by ~70%
# Stage 1: Builder - compiles code in a full JDK environment
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and POM
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Cache Maven dependencies (layer reuse across builds)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build application (test disabled to speed up Docker build)
RUN ./mvnw clean package -DskipTests -q

# Stage 2: Runtime - minimal image with only runtime dependencies
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /build/target/payment-service*.jar app.jar

# Health check endpoint (Spring Boot actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Non-root user (security best practice)
RUN addgroup -g 1000 payment && adduser -D -u 1000 -G payment payment
USER payment

# Expose port
EXPOSE 8083

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
