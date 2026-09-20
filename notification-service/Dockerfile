# Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/notification-service*.jar app.jar

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

RUN addgroup -g 1000 notif && adduser -D -u 1000 -G notif notif
USER notif

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
