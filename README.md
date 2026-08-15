# 🚀 Payments Platform — Microservices Learning Project

A complete, production-ready microservices payment processing system demonstrating enterprise patterns like Saga orchestration, idempotency, outbox pattern, distributed tracing, and Kubernetes deployment.

**Perfect for:** Learning microservices architecture, inter-service communication, distributed transactions, and cloud-native deployment patterns.

---

## 📋 Table of Contents

1. [Quick Start (5 minutes)](#quick-start)
2. [What You'll Learn](#what-youll-learn)
3. [Architecture Overview](#architecture-overview)
4. [Prerequisites & Setup](#prerequisites--setup)
5. [Detailed Setup Guide](#detailed-setup-guide)
6. [Running the Platform](#running-the-platform)
7. [Running Tests](#running-tests)
8. [API Endpoints](#api-endpoints)
9. [Project Structure](#project-structure)
10. [Next Phases](#next-phases)
11. [Troubleshooting](#troubleshooting)

---

## Quick Start

Get the platform running in 5 minutes with these simple steps:

```bash
# 1. Clone the repository
git clone <your-repo-url>
cd payment-platform

# 2. Ensure Java 21 is installed
java -version  # Should show openjdk version "21"

# 3. Build the entire project
./mvnw clean build

# 4. Run all tests (unit + integration)
./mvnw test

# 5. Start all three services (in separate terminals):

# Terminal 1 - Account Service (Port 8081)
cd account-service && mvn spring-boot:run

# Terminal 2 - Fraud Service (Port 8082)
cd fraud-service && mvn spring-boot:run

# Terminal 3 - Payment Service (Port 8083)
cd payment-service && mvn spring-boot:run

# 6. Test a payment (Terminal 4)
curl -X POST http://localhost:8083/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-payment-1" \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "payeeAccountId": "00000000-0000-0000-0000-000000000002",
    "amount": 100.00,
    "currency": "INR"
  }'
```

✅ **Done!** The payment system is now running. See [API Endpoints](#api-endpoints) for more examples.

---

## What You'll Learn

This is a **teaching project**, not a toy. Every concept has a real-world reason:

| Concept | What You Learn | Why It Matters |
|---|---|---|
| **Microservices** | Split monolith into independent deployable services with separate databases | Scaling, team autonomy, independent deployments |
| **Saga Pattern** | Distributed transactions without 2-phase commit | Avoid database locks across services; handle failures gracefully |
| **Idempotency** | Retry requests safely without duplicate effects | Network failures happen; safe retries are essential in production |
| **Outbox Pattern** | Guarantee async events are published even if Kafka is down | Sync DB commit + async publish don't naturally align; outbox fixes this |
| **Feign + Resilience4j** | Sync service calls with circuit breakers, retries, timeouts | Prevent cascading failures; degrade gracefully when a service is slow |
| **Kafka + Spring Kafka** | Async events for notifications and read models | Decouple services; enable independent consumer scaling |
| **CQRS (Command Query Responsibility Segregation)** | Build separate read models from multiple event sources | Queries don't need to hit transactional databases |
| **Distributed Tracing** | Track a single payment through all services (coming in Phase 2) | Debug production issues across service boundaries |
| **Database Migrations (Flyway)** | Version control for schemas across services | Predictable, repeatable deployments |
| **Test Organization** | Unit, integration, and acceptance tests in separate directories | Clear intent; run fast tests by default, slow tests when needed |
| **Kubernetes & Helm** | Deploy on K8s with templated, reusable charts (Phase 3) | Cloud-native scaling, self-healing, GitOps |

---

## Architecture Overview

### System Diagram

```
┌─────────────────┐
│  Client/API     │
│  (Browser/curl) │
└────────┬────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────┐
│            API Gateway (Future Phase)                      │
│     • Single entry point for all clients                   │
│     • JWT validation (Keycloak)                            │
│     • Rate limiting & routing                              │
└────────┬─────────────────────────────────────────────────┘
         │
         ▼ HTTP (Synchronous)
┌──────────────────────────────────────────────────────────┐
│           PAYMENT SERVICE (Orchestrator)                   │
│  Port: 8083                                                │
│  Responsibility: Coordinate the payment saga              │
│  • Persist payment & idempotency key                      │
│  • Call Fraud Service (risk check)                        │
│  • Call Account Service (debit/credit)                    │
│  • Update status → Complete/Failed                        │
│  • Publish events to Kafka (via outbox)                   │
└─────┬──────────────────────────────────────┬──────────────┘
      │                                      │
      │ Feign (Sync)                         │ Feign (Sync)
      ▼                                      ▼
┌─────────────────────────┐      ┌─────────────────────────┐
│   ACCOUNT SERVICE       │      │    FRAUD SERVICE        │
│   Port: 8081            │      │    Port: 8082           │
│                         │      │                         │
│   • Manage accounts     │      │  • Evaluate risk score  │
│   • Debit/credit        │      │  • Return APPROVE/      │
│   • Ledger entries      │      │    REJECT decision      │
│   • Idempotency via     │      │                         │
│     unique reference ID │      │                         │
└─────────────────────────┘      └─────────────────────────┘
      │                                     │
      │ Publish (Kafka)                     │
      ▼                                     ▼
┌──────────────────────────────────────────────────────────┐
│                    KAFKA TOPICS                            │
│                                                            │
│  • payment.completed ◄── Payment Service                  │
│  • payment.failed    ◄── Payment Service                  │
│  • account.debited   ◄── Account Service                  │
│  • account.credited  ◄── Account Service                  │
└────┬───────────────────────────────────────┬──────────────┘
     │                                        │
     │ Kafka Consumer                         │ Kafka Consumer
     ▼                                        ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│   NOTIFICATION SERVICE       │  │ TRANSACTION HISTORY SERVICE  │
│   (Phase 2 - Consumer Only)  │  │ (Phase 2 - Consumer Only)    │
│                              │  │                              │
│  • Listen to payment events  │  │ • Build denormalized view    │
│  • Send notifications (SMS)  │  │ • Provide search API         │
│  • Dedupe on eventId         │  │ • CQRS-style read model      │
└──────────────────────────────┘  └──────────────────────────────┘

DATABASE TIER (PostgreSQL)
┌──────────────────┬──────────────────┬──────────────────┐
│   payment_db     │   account_db     │    fraud_db      │
│                  │                  │                  │
│ • payments       │ • accounts       │ • risk_rules     │
│ • outbox_events  │ • ledger_entries │ • risk_history   │
└──────────────────┴──────────────────┴──────────────────┘
```

### Communication Patterns

**Synchronous (Feign/HTTP):** Used when the caller *cannot proceed* without an answer
- Payment Service → Account Service (must debit before returning response)
- Payment Service → Fraud Service (must evaluate risk before proceeding)

**Asynchronous (Kafka):** Used when eventual consistency is acceptable
- Payment Service → Notifications (notify user later, doesn't block payment)
- Payment Service → Transaction History (build read model, doesn't affect payment)

---

## Prerequisites & Setup

### What You Need to Install

#### 1. **Java 21 (Required)**

**Windows:**
```powershell
# Download from https://www.oracle.com/java/technologies/downloads/#java21
# Or use a package manager:
choco install openjdk21

# Verify installation
java -version  # Should show: openjdk version "21.x.x"
```

**macOS:**
```bash
brew install openjdk@21

# Verify
java -version
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk

# Verify
java -version
```

#### 2. **Maven (Required)**

Maven is included in the project as `mvnw` (Maven Wrapper), so you don't need to install it separately. The wrapper automatically downloads the correct version.

**Verify Maven wrapper works:**
```bash
cd payment-platform
./mvnw --version  # Should show Maven 3.8.x and Java 21
```

#### 3. **PostgreSQL 16 (Required for Integration Tests)**

This can run **locally** or in **Docker** (recommended).

**Option A: Docker (Easiest for Windows/Mac):**
```bash
# Pull PostgreSQL 16 image (only first time)
docker pull postgres:16-alpine

# Run PostgreSQL container
docker run --name payments-postgres \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=root \
  -e POSTGRES_DB=postgres \
  -p 5432:5432 \
  -d postgres:16-alpine

# Verify it's running
docker ps | grep payments-postgres
```

**Option B: Local Installation:**
- **Windows:** Download from https://www.postgresql.org/download/windows/
- **macOS:** `brew install postgresql@16`
- **Linux:** `sudo apt install postgresql-16`

Then start PostgreSQL and ensure it's on port 5432.

#### 4. **Docker & Docker Compose (Optional, for full environment)**

**Windows/Mac:**
1. Download [Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Install and start the application
3. Verify: `docker --version && docker-compose --version`

**Linux:**
```bash
sudo apt install docker.io docker-compose
sudo usermod -aG docker $USER  # Run without sudo
newgrp docker
```

---

## Detailed Setup Guide

### Step 1: Clone & Navigate to Project

```bash
# Clone the repository
git clone <your-repo-url>
cd payment-platform

# Verify you're in the right place
ls -la  # Should show: pom.xml, account-service/, fraud-service/, payment-service/, etc.
```

### Step 2: Verify Java & Maven

```bash
# Check Java
java -version
# Expected output: openjdk version "21.x.x"

# Check Maven (via wrapper)
./mvnw --version
# Expected output: Apache Maven 3.8.x and Java 21
```

### Step 3: Ensure PostgreSQL is Running

If using Docker:
```bash
docker ps | grep payments-postgres
# If not running, restart it:
docker start payments-postgres
```

If local installation:
```bash
# macOS
brew services start postgresql@16

# Linux
sudo systemctl start postgresql

# Windows
net start postgresql-x64-16
```

Verify connection:
```bash
psql -U root -d postgres -h 127.0.0.1 -c "SELECT version();"
# Should print PostgreSQL 16 version
```

### Step 4: Create Databases

PostgreSQL automatically creates schemas per service. The first run will create them via Flyway migrations.

```bash
# Log in to PostgreSQL
psql -U root -h 127.0.0.1

# Then in psql:
CREATE DATABASE payment_db;
CREATE DATABASE account_db;
CREATE DATABASE fraud_db;
\q
```

Or via a script:
```bash
# Create databases in one go
psql -U root -h 127.0.0.1 -c "CREATE DATABASE payment_db;"
psql -U root -h 127.0.0.1 -c "CREATE DATABASE account_db;"
psql -U root -h 127.0.0.1 -c "CREATE DATABASE fraud_db;"
```

### Step 5: Build the Entire Project

```bash
cd payment-platform

# Clean build (downloads dependencies, compiles, runs tests)
./mvnw clean build

# Expected output:
# [INFO] payments-platform ........................ SUCCESS
# [INFO] account-service ......................... SUCCESS
# [INFO] fraud-service ........................... SUCCESS
# [INFO] payment-service ......................... SUCCESS
# [INFO] BUILD SUCCESS
```

**What just happened:**
- Maven downloaded all dependencies (Spring Boot, Kafka, PostgreSQL driver, etc.)
- Compiled all 3 services
- Ran unit + integration tests
- Created JAR files in each service's `target/` folder

### Step 6: Verify Database Schemas

After the build, Flyway automatically created the schemas. Verify:

```bash
# Connect to each database
psql -U root -h 127.0.0.1 -d payment_db -c "\dt"
# Should show: payments, outbox_events tables

psql -U root -h 127.0.0.1 -d account_db -c "\dt"
# Should show: accounts, ledger_entries tables

psql -U root -h 127.0.0.1 -d fraud_db -c "\dt"
# Should show: (tables created by migrations)
```

---

## Running the Platform

### Starting Services Locally

Each service runs independently. Start them in **separate terminals**:

#### Terminal 1: Account Service (Port 8081)

```bash
cd payment-platform/account-service
./mvnw spring-boot:run

# Expected output:
# Started AccountServiceApplication in 4.567 seconds
# Listening on port 8081
```

**Verify it's running:**
```bash
curl http://localhost:8081/actuator/health
# Should return: {"status":"UP"}
```

#### Terminal 2: Fraud Service (Port 8082)

```bash
cd payment-platform/fraud-service
./mvnw spring-boot:run

# Expected output:
# Started FraudDetectionServiceApplication in 3.234 seconds
# Listening on port 8082
```

**Verify it's running:**
```bash
curl http://localhost:8082/actuator/health
# Should return: {"status":"UP"}
```

#### Terminal 3: Payment Service (Port 8083)

```bash
cd payment-platform/payment-service
./mvnw spring-boot:run

# Expected output:
# Started PaymentServiceApplication in 5.123 seconds
# Listening on port 8083
```

**Verify it's running:**
```bash
curl http://localhost:8083/actuator/health
# Should return: {"status":"UP"}
```

#### Terminal 4: Test the System

```bash
# Create a payment
curl -X POST http://localhost:8083/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-payment-1" \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "payeeAccountId": "00000000-0000-0000-0000-000000000002",
    "amount": 100.00,
    "currency": "INR"
  }'

# Should return:
# {
#   "paymentId": "12345678-abcd-...",
#   "status": "INITIATED",
#   "createdAt": "2026-08-15T..."
# }
```

### Stopping Services

```bash
# In each terminal, press Ctrl+C to stop the service
Ctrl+C
```

---

## Running Tests

The project uses **Maven profiles** to organize and run different test categories.

### Available Test Profiles

| Profile | What It Runs | Speed | When to Use |
|---|---|---|---|
| **(default)** | unit + integration tests | ~40s | During development |
| `unit` | unit tests only | ~5s | Quick feedback, mocked dependencies |
| `integration` | integration + context tests | ~30s | With real database |
| `acceptance` | full end-to-end tests | ~2m | Before committing |
| `all-tests` | every test category | ~2m | CI/CD pipeline |
| `coverage` | run tests + enforce 95% code coverage | ~40s | Code quality check |

### Running Tests Locally

**Default (unit + integration tests):**
```bash
cd payment-platform
./mvnw test

# Output:
# [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

**Unit tests only (fastest, ~5 seconds):**
```bash
./mvnw -P unit test

# Runs only tests in src/test/java/unit/
```

**Integration tests:**
```bash
./mvnw -P integration test

# Runs tests in src/test/java/integration/ + ApplicationTests
# Starts real PostgreSQL via Testcontainers
```

**Code coverage enforcement (95% per package):**
```bash
./mvnw -P coverage test

# Runs all tests AND checks that code coverage is ≥ 95%
# on core business packages:
# - paymentservice.orchestration
# - paymentservice.client
# - paymentservice.api
# - paymentservice.outbox
# - accountservice.ledger
# - accountservice.api
# - fraudservice.detection
# - fraudservice.api

# If coverage is below 95%, the build fails with a detailed report
```

**All tests:**
```bash
./mvnw -P all-tests test

# Runs every test: unit + integration + acceptance
```

### Viewing Test Coverage Reports

After running tests with `-P coverage`, a JaCoCo report is generated:

```bash
# For each service, the coverage report is at:
# account-service/target/site/jacoco/index.html
# fraud-service/target/site/jacoco/index.html
# payment-service/target/site/jacoco/index.html

# Open in browser (example for payment-service):
open payment-service/target/site/jacoco/index.html

# On Windows:
start payment-service\target\site\jacoco\index.html

# On Linux:
firefox payment-service/target/site/jacoco/index.html
```

### Test Directory Structure

```
payment-service/src/test/java/
├── unit/                           # Fast, mocked tests
│   └── (tests here run with -P unit)
├── integration/                    # Real DB, mocked services
│   ├── PaymentServiceApplicationTests.java
│   └── outbox/
│       ├── OutboxIntegrationTest.java
│       └── OutboxPublisherTest.java
└── acceptance/                     # Full end-to-end (currently empty)
    └── (place E2E tests here)

account-service/src/test/java/
├── unit/
├── integration/
│   └── AccountServiceApplicationTests.java
└── acceptance/

fraud-service/src/test/java/
├── unit/
├── integration/
│   └── FraudDetectionServiceApplicationTests.java
└── acceptance/
```

---

## API Endpoints

### Payment Service (Port 8083)

#### 1. Initiate a Payment

**Endpoint:** `POST /api/v1/payments`

**Request:**
```bash
curl -X POST http://localhost:8083/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "payeeAccountId": "00000000-0000-0000-0000-000000000002",
    "amount": 500.50,
    "currency": "INR"
  }'
```

**Response (202 Accepted):**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "INITIATED",
  "createdAt": "2026-08-15T10:30:45Z"
}
```

**Note:** `Idempotency-Key` header makes this request safe to retry. Same key = same response, no duplicate payment.

#### 2. Get Payment Status

**Endpoint:** `GET /api/v1/payments/{paymentId}`

**Request:**
```bash
curl http://localhost:8083/api/v1/payments/a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6
```

**Response:**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "COMPLETED",
  "amount": 500.50,
  "currency": "INR",
  "payerAccountId": "00000000-0000-0000-0000-000000000001",
  "payeeAccountId": "00000000-0000-0000-0000-000000000002",
  "createdAt": "2026-08-15T10:30:45Z",
  "updatedAt": "2026-08-15T10:30:47Z"
}
```

### Account Service (Port 8081)

#### 1. Get Account Balance

**Endpoint:** `GET /api/v1/accounts/{accountId}/balance`

**Request:**
```bash
curl http://localhost:8081/api/v1/accounts/00000000-0000-0000-0000-000000000001/balance
```

**Response:**
```json
{
  "accountId": "00000000-0000-0000-0000-000000000001",
  "balance": 10000.00,
  "currency": "INR"
}
```

#### 2. Get Ledger Entries (Transaction History)

**Endpoint:** `GET /api/v1/accounts/{accountId}/ledger`

**Request:**
```bash
curl http://localhost:8081/api/v1/accounts/00000000-0000-0000-0000-000000000001/ledger
```

**Response:**
```json
{
  "accountId": "00000000-0000-0000-0000-000000000001",
  "entries": [
    {
      "id": "entry-uuid-1",
      "amount": 500.50,
      "type": "DEBIT",
      "referenceId": "payment-uuid-1",
      "createdAt": "2026-08-15T10:30:47Z"
    }
  ]
}
```

### Fraud Service (Port 8082)

#### 1. Evaluate Risk

**Endpoint:** `POST /api/v1/risk/evaluate`

**Request:**
```bash
curl -X POST http://localhost:8082/api/v1/risk/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "amount": 500.50,
    "currency": "INR"
  }'
```

**Response:**
```json
{
  "riskScore": 25,
  "decision": "APPROVE",
  "reasons": []
}
```

Or (if amount > 10,000):
```json
{
  "riskScore": 85,
  "decision": "REJECT",
  "reasons": ["Amount exceeds maximum transaction limit of 10000"]
}
```

### Health & Monitoring Endpoints (All Services)

Each service exposes Spring Boot Actuator endpoints:

```bash
# Health check
curl http://localhost:8081/actuator/health
# Returns: {"status":"UP"}

# Readiness (for Kubernetes)
curl http://localhost:8081/actuator/health/readiness
# Returns: {"status":"UP"}

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
# Returns: Prometheus-format metrics (CPU, JVM, HTTP requests, etc.)
```

---

## Project Structure

```
payment-platform/                   # Root project (Maven aggregator)
├── pom.xml                          # Parent POM with shared dependencies & profiles
├── README.md                        # This file
├── payments-microservices-design.md # Complete system design document
│
├── payment-service/                 # Saga Orchestrator
│   ├── pom.xml                      # Service-specific dependencies
│   ├── src/main/java/com/payments/platform/paymentservice/
│   │   ├── orchestration/           # Saga logic
│   │   │   ├── PaymentOrchestratorService.java
│   │   │   └── PaymentStatus.java
│   │   ├── api/                     # REST controllers & DTOs
│   │   │   ├── PaymentController.java
│   │   │   ├── PaymentRequest.java
│   │   │   └── PaymentResponse.java
│   │   ├── client/                  # Feign clients for external services
│   │   │   ├── AccountClient.java
│   │   │   ├── FraudClient.java
│   │   │   └── dto/
│   │   ├── persistence/             # JPA repository & entities
│   │   │   ├── PaymentRepository.java
│   │   │   └── entity/Payment.java
│   │   ├── outbox/                  # Outbox pattern implementation
│   │   │   ├── OutboxEvent.java
│   │   │   ├── OutboxPublisher.java
│   │   │   └── OutboxSender.java
│   │   ├── config/                  # Spring configuration
│   │   ├── exception/               # Global exception handling
│   │   └── PaymentServiceApplication.java
│   ├── src/test/java/
│   │   ├── unit/                    # Unit tests (mocked)
│   │   ├── integration/             # Integration tests (real DB)
│   │   │   └── outbox/
│   │   └── acceptance/              # End-to-end tests
│   └── src/main/resources/
│       ├── application.yml          # Default config
│       ├── application-test.yml     # Test-specific config
│       └── db/migration/            # Flyway migrations
│           ├── V1__initial_schema.sql
│           ├── V2__add_idempotency.sql
│           └── ...
│
├── account-service/                 # Account & Ledger Management
│   ├── pom.xml
│   ├── src/main/java/com/payments/platform/accountservice/
│   │   ├── ledger/                  # Core domain logic
│   │   │   ├── AccountService.java
│   │   │   ├── Account.java
│   │   │   └── LedgerEntry.java
│   │   ├── api/                     # REST endpoints
│   │   │   ├── AccountController.java
│   │   │   ├── DebitRequest.java
│   │   │   └── ...
│   │   ├── persistence/
│   │   ├── config/
│   │   └── AccountServiceApplication.java
│   ├── src/test/java/
│   │   ├── unit/
│   │   ├── integration/
│   │   └── acceptance/
│   └── src/main/resources/
│       └── db/migration/
│
├── fraud-service/                   # Risk Detection & Scoring
│   ├── pom.xml
│   ├── src/main/java/com/payments/platform/fraudservice/
│   │   ├── detection/               # Risk logic
│   │   │   └── FraudDetectionService.java
│   │   ├── api/                     # REST endpoints
│   │   │   ├── FraudController.java
│   │   │   └── ...
│   │   ├── config/
│   │   └── FraudDetectionServiceApplication.java
│   ├── src/test/java/
│   │   ├── unit/
│   │   ├── integration/
│   │   └── acceptance/
│   └── src/main/resources/
│       └── db/migration/
│
└── docs/                            # (Optional) Additional documentation
    ├── architecture/
    └── deployment/
```

### Key Directories Explained

**`pom.xml` (Parent):** Central Maven configuration
- Manages dependency versions for all 3 services
- Defines Maven profiles (unit, integration, coverage)
- Configures JaCoCo code coverage plugin
- Configures Surefire test runner

**`orchestration/`:** Payment saga orchestration logic
- Manages state transitions: INITIATED → FRAUD_CHECK → DEBITED → COMPLETED
- Calls Fraud & Account services synchronously
- Publishes events asynchronously

**`outbox/`:** Guaranteed async delivery
- Outbox pattern prevents "DB commit OK, Kafka publish failed" race condition
- OutboxEvent persisted in same transaction as payment
- Separate OutboxSender polls and publishes to Kafka

**`src/test/java/`:** Test organization
- `unit/`: Fast tests with mocked dependencies (~5s)
- `integration/`: Real DB via Testcontainers (~30s)
- `acceptance/`: Full E2E tests (~2m, currently empty)

**`src/main/resources/db/migration/`:** Flyway database migrations
- Version-controlled SQL scripts for schema changes
- Automatically executed on app startup
- Ensures consistent schema across all deployments

---

## Next Phases

### Phase 2: Complete Infrastructure
- [ ] **Notification Service** — Kafka consumer for payment.completed events
- [ ] **Transaction History Service** — CQRS read model from multiple Kafka topics
- [ ] **OpenTelemetry Integration** — Distributed tracing across services
- [ ] **Prometheus & Grafana** — Metrics and dashboards
- [ ] **Keycloak Integration** — OAuth2/JWT service authentication
- [ ] **API Gateway** — Single entry point with rate limiting

### Phase 3: Kubernetes Deployment
- [ ] **Dockerfile per service** — Multi-stage builds for production images
- [ ] **Docker Compose** — Local multi-service orchestration
- [ ] **Kubernetes Manifests** — Deployments, Services, ConfigMaps per service
- [ ] **Helm Charts** — Templated, reusable K8s deployments
- [ ] **Local K8s** — Deploy to Kind or Minikube

### Phase 4: Cloud Deployment (AWS)
- [ ] **EKS Cluster** — Elastic Kubernetes Service
- [ ] **Aurora PostgreSQL** — Managed relational database
- [ ] **MSK** — Managed Streaming for Kafka
- [ ] **AWS X-Ray** — Distributed tracing in production
- [ ] **CI/CD Pipeline** — GitHub Actions → ECR → EKS

### Phase 5: Advanced Patterns
- [ ] **Circuit Breaker** — Resilience4j circuit breaker examples
- [ ] **Velocity Checks** — Fraud detection rule improvements
- [ ] **Account Age Risk** — Adjust risk based on account creation date
- [ ] **Distributed Tracing** — Full trace ID propagation
- [ ] **Dead Letter Queues** — Kafka DLT for failed messages

---

## Troubleshooting

### 1. Build Fails: "Java version 21 not found"

```bash
# Check your Java version
java -version

# Should output: openjdk version "21.x.x"

# If not, set JAVA_HOME explicitly:
# Windows (PowerShell):
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.0"

# macOS/Linux:
export JAVA_HOME=/usr/libexec/java_home -v 21

# Then retry:
./mvnw clean build
```

### 2. Tests Fail: "PostgreSQL connection refused"

```bash
# Ensure PostgreSQL is running:
docker ps | grep payments-postgres

# If not running:
docker start payments-postgres

# Verify connection:
psql -U root -h 127.0.0.1 -d postgres -c "SELECT 1;"

# Or check if local PostgreSQL is running:
# macOS: brew services list
# Linux: sudo systemctl status postgresql
# Windows: net start postgresql-x64-16
```

### 3. Integration Tests Timeout

The test uses **Testcontainers** to start a PostgreSQL container automatically. If tests timeout:

```bash
# Check if Docker is running
docker --version

# Pull the postgres image (large, one-time):
docker pull postgres:16-alpine

# Then retry tests:
./mvnw -P integration test
```

### 4. Service Starts but "Connection to Account Service Failed"

Ensure all 3 services are running:

```bash
# Check each service is up:
curl http://localhost:8081/actuator/health  # Account
curl http://localhost:8082/actuator/health  # Fraud
curl http://localhost:8083/actuator/health  # Payment

# If one fails, start it in a new terminal:
cd account-service && ./mvnw spring-boot:run
```

### 5. Port Already in Use

If port 8081, 8082, or 8083 is in use, either:

**Option A:** Stop the existing process on that port

**Windows (PowerShell):**
```powershell
# Find process using port 8083
netstat -ano | findstr :8083

# Kill process by PID (example: PID 1234)
taskkill /PID 1234 /F
```

**macOS/Linux:**
```bash
# Find and kill process on port 8083
lsof -i :8083
kill -9 <PID>
```

**Option B:** Change the port in `application.yml`

```yaml
server:
  port: 8090  # Use 8090 instead of 8083
```

### 6. Coverage Report Shows 0% Coverage

```bash
# Run tests with coverage explicitly:
./mvnw clean -P coverage test

# Check report exists:
ls payment-service/target/site/jacoco/

# If missing, rebuild with clean:
./mvnw clean test jacoco:report
```

### 7. Flyway Migration Fails

```bash
# This usually means the database is locked or schema already exists.
# Solution: Drop and recreate the database:

psql -U root -h 127.0.0.1 -c "DROP DATABASE payment_db;"
psql -U root -h 127.0.0.1 -c "CREATE DATABASE payment_db;"

# Then rebuild:
./mvnw clean build
```

---

## Development Tips

### Quick Development Cycle

1. Make code changes
2. Recompile: `./mvnw compile`
3. Run unit tests: `./mvnw -P unit test`
4. Manually test via curl (see [API Endpoints](#api-endpoints))
5. Commit & push

### Debugging a Failing Test

```bash
# Run a single test with verbose output:
./mvnw -P integration test -Dtest=OutboxIntegrationTest -X

# -X flag shows full debug output
# -Dtest=ClassName runs only that test class
```

### Viewing Database State During Testing

```bash
# Start a PostgreSQL container for manual inspection:
docker run -it --rm \
  -e PGPASSWORD=root \
  postgres:16-alpine \
  psql -h host.docker.internal -U root -d payment_db

# Then inspect tables:
SELECT * FROM payments;
SELECT * FROM outbox_events;
```

### IDE Setup (IntelliJ IDEA / VS Code)

**IntelliJ:**
- File > New > Project from Existing Sources > Choose `pom.xml`
- Let IntelliJ detect the multi-module Maven project
- Mark `src/test/java` as Test Sources Root
- Enable JDK 21 in Project Settings

**VS Code:**
- Install "Extension Pack for Java"
- Install "Maven for Java"
- Open the root folder
- Let extensions auto-detect `pom.xml`

---

## Key Concepts Reference

### Idempotency
**Problem:** Network can fail after a request succeeds but before the response reaches the client. Retrying without idempotency causes duplicate payments.

**Solution:** Clients send an `Idempotency-Key` header (unique UUID). Server stores this key with the payment. Same key = same response, no re-processing.

### Saga Pattern
**Problem:** Distributed transactions across multiple databases don't have ACID guarantees like a single-database transaction.

**Solution:** Payment Service orchestrates a series of local transactions:
1. Create payment record (status INITIATED)
2. Call Fraud Service → update status
3. Call Account Service to debit → update status
4. Publish event for async consumers

If any step fails, status is marked FAILED and rollback is logged (no compensating transactions needed here since nothing committed yet).

### Outbox Pattern
**Problem:** Database commit succeeds, but Kafka publish fails. Now the payment is in the DB but consumers never hear about it.

**Solution:**
1. Within the same DB transaction that updates the payment, insert a row into `outbox_events` table
2. Separate OutboxSender process polls the outbox and publishes to Kafka
3. Guarantees at-least-once delivery without distributed transactions

### Feign + Resilience4j
**Problem:** Payment Service calls Account Service synchronously. If Account Service is slow or down, Payment Service hangs.

**Solution:** Feign + Resilience4j adds:
- **Circuit Breaker:** After N failures, stop calling the service (fail fast instead of hanging)
- **Retry:** Automatically retry transient failures (network timeouts)
- **Timeout:** Fail after 2 seconds instead of waiting forever

---

## Contact & Contribution

This is a learning project. Questions or improvements?

- **GitHub Issues:** Report bugs or request features
- **Pull Requests:** Contributions welcome!
- **Design Document:** See `payments-microservices-design.md` for architectural decisions

---
