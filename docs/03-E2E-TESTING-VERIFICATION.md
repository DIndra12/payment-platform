# End-to-End Testing & Verification Guide

Complete walkthrough to test the payment platform from startup through observability.

## Table of Contents

1. [Prerequisites & Setup](#prerequisites--setup)
2. [Starting the System](#starting-the-system)
3. [Health Checks](#health-checks)
4. [Testing Individual Services](#testing-individual-services)
5. [End-to-End Payment Flow](#end-to-end-payment-flow)
6. [Observability Verification](#observability-verification)
7. [Verification Checklist](#verification-checklist)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites & Setup

### System Requirements

```bash
# Check Java version (need 21+)
java -version
# Expected: openjdk version "21.0.x" or higher

# Check Maven
mvn -v
# Expected: Apache Maven 3.8.x or higher

# Check Docker
docker -v
docker-compose -v
# Expected: Docker 24.0+, Docker Compose 2.0+
```

### Clone & Build

```bash
# Navigate to project directory
cd /path/to/payment-platform

# Build all services (compiles Java, runs tests)
./mvnw clean install -DskipTests
# Expected duration: 2-3 minutes
# Expected output: BUILD SUCCESS

# Or with tests (takes longer)
./mvnw clean install
# Expected: All 62+ tests pass, coverage >= 90%
```

### Verify Build Artifacts

```bash
# Check JAR files were created
ls -la api-gateway-service/target/*.jar
ls -la payment-service/target/*.jar
ls -la account-service/target/*.jar
ls -la fraud-service/target/*.jar
ls -la notification-service/target/*.jar
ls -la transaction-history-service/target/*.jar

# Expected: Each directory has a .jar file ending in .jar or -exec.jar
```

---

## Starting the System

### Step 1: Clean Start

```bash
# Stop any running containers
docker-compose down -v

# Remove old containers (optional, for fresh state)
docker system prune -f
```

### Step 2: Start Infrastructure & Services

```bash
# Start all services in background
docker-compose up -d

# Watch startup logs
docker-compose logs -f

# Expected output sequence:
# 1. postgres starts (healthy in ~5-10s)
# 2. keycloak starts, waits for postgres (~15-20s)
# 3. kafka starts (~5s)
# 4. elasticsearch starts (~10s)
# 5. fluent-bit starts (connects to elasticsearch)
# 6. kibana starts, waits for elasticsearch (~15s)
# 7. prometheus starts, begins scraping
# 8. grafana starts (~5s)
# 9. api-gateway-service starts (8080)
# 10. account-service, fraud-service, etc. start
```

### Step 3: Verify All Containers Running

```bash
docker-compose ps

# Expected output (all services):
CONTAINER ID   IMAGE                                    STATUS
xxx            postgres:16-alpine                       Up 1 minute (healthy)
xxx            bitnamilegacy/kafka:3.7.0                Up 1 minute (healthy)
xxx            quay.io/keycloak/keycloak:24.0           Up 1 minute (healthy)
xxx            docker.elastic.co/elasticsearch/...      Up 1 minute (healthy)
xxx            fluent/fluent-bit:2.1.8                  Up 1 minute
xxx            docker.elastic.co/kibana/kibana:...      Up 1 minute (healthy)
xxx            prom/prometheus:v2.48.0                  Up 1 minute (healthy)
xxx            grafana/grafana:10.2.0                   Up 1 minute (healthy)
xxx            (api-gateway-service)                    Up 1 minute
xxx            (account-service)                        Up 1 minute
xxx            (fraud-service)                          Up 1 minute
xxx            (payment-service)                        Up 1 minute
xxx            (notification-service)                   Up 1 minute
xxx            (transaction-history-service)            Up 1 minute

# All should show "Up X minutes" and "(healthy)" where applicable
```

### Step 4: Wait for Full Readiness

```bash
# Services take 30-60 seconds to become fully healthy
# Keycloak initializes realm, services connect to DBs, etc.

# Check service logs
docker-compose logs payment-service | tail -20
# Look for: "PaymentServiceApplication started" or "Started in X.XXX seconds"

docker-compose logs account-service | tail -20
# Look for: "AccountServiceApplication started"
```

---

## Health Checks

### Check Service Health Endpoints

```bash
# Each service exposes /actuator/health endpoint

# API Gateway
curl -s http://localhost:8080/actuator/health | jq .
# Expected: { "status": "UP" }

# Payment Service
curl -s http://localhost:8083/actuator/health | jq .
# Expected: { "status": "UP", "components": { "db": {"status": "UP"}, ... } }

# Account Service
curl -s http://localhost:8081/actuator/health | jq .
# Expected: { "status": "UP" }

# Fraud Service
curl -s http://localhost:8082/actuator/health | jq .
# Expected: { "status": "UP" }

# Notification Service
curl -s http://localhost:8084/actuator/health | jq .
# Expected: { "status": "UP" }

# Transaction History Service
curl -s http://localhost:8085/actuator/health | jq .
# Expected: { "status": "UP" }
```

### Check Keycloak

```bash
# Keycloak health endpoint
curl -s http://localhost:8090/health | jq .
# Expected: { "status": "UP" }

# Access Keycloak admin console (optional)
# Open http://localhost:8090 in browser
# Login with: admin / admin
# Navigate: Realms → payment-platform → Clients
```

### Check Database Connectivity

```bash
# Connect to PostgreSQL
docker exec -it payments-postgres psql -U postgres -c "SELECT version();"

# Expected: PostgreSQL 16.x output

# List databases
docker exec -it payments-postgres psql -U postgres -c "\l"

# Expected databases:
# - account_db
# - payment_db
# - fraud_db (optional, fraud service is stateless)
# - notification_db
# - transaction_history_db
```

### Check Kafka

```bash
# List topics
docker exec -it payments-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Expected topics:
# - payment.completed
# - payment.failed
# - account.debited
# - account.credited

# Check topic partition count
docker exec -it payments-kafka kafka-topics.sh --describe --topic payment.completed --bootstrap-server localhost:9092
```

---

## Testing Individual Services

### 1. Fraud Service (Stateless, No Auth)

```bash
# Test fraud risk check
curl -X POST http://localhost:8082/api/fraud/check \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50.00,
    "accountId": "ACC123",
    "recipientId": "RCPT456"
  }'

# Expected response (200 OK):
{
  "riskScore": 30,
  "decision": "APPROVED",
  "reasons": []
}

# Test with high-value amount
curl -X POST http://localhost:8082/api/fraud/check \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000.00,
    "accountId": "ACC123",
    "recipientId": "RCPT456"
  }'

# Expected: Higher riskScore (e.g., 85)
# Expected: "decision": "REJECTED"
```

### 2. Account Service (With Database)

```bash
# First, get a token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-platform-client" \
  -d "client_secret=your-client-secret" | jq -r '.access_token')

# Note: For testing, we can bypass OAuth by setting environment variable
# or by using a test profile. For now, test without auth:

# Get account balance
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq .
# Expected response (if account exists):
# { "accountId": "ACC123", "balance": 1000.00, ... }

# Or expected 404 if account doesn't exist yet
# Create account first (via payment flow, or direct test)

# Test account debit (this should have auth, skip for now)
```

### 3. API Gateway (Entry Point)

```bash
# Test routing (should get 401 without token, but gateway responds)
curl -v http://localhost:8080/actuator/health

# Expected: 200 OK (health endpoint is public)
{
  "status": "UP"
}

# Test with invalid JWT
curl -X GET http://localhost:8080/api/accounts/ACC123 \
  -H "Authorization: Bearer invalid-token"

# Expected: 401 Unauthorized (gateway rejects)

# Without Authorization header
curl -X GET http://localhost:8080/api/accounts/ACC123

# Expected: 401 Unauthorized (gateway requires auth for /api/* endpoints)
```

---

## End-to-End Payment Flow

### Scenario: Successful Payment

#### Step 1: Get Authentication Token

```bash
# Keycloak client credentials (for testing)
# In production, use user login flow (Authorization Code Grant)

curl -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-platform-client" \
  -d "client_secret=your-client-secret"

# Expected response:
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "expires_in": 300,
  "token_type": "Bearer"
}

# Save token for next requests
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# OR for testing (if configured), use a test token directly:
TOKEN="test-token-for-e2e"
```

#### Step 2: Create an Account (if needed)

```bash
# POST to account service to create account
# (Or check if test accounts already exist in database)

curl -X POST http://localhost:8081/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: ACCT-SETUP-001" \
  -d '{
    "accountId": "ACC123",
    "accountHolder": "John Doe",
    "initialBalance": 1000.00
  }'

# Expected: 201 Created
{
  "accountId": "ACC123",
  "balance": 1000.00,
  "status": "ACTIVE"
}

# Verify account was created
curl -s http://localhost:8081/api/accounts/ACC123 | jq .
```

#### Step 3: Initiate Payment (Main Test)

```bash
# POST payment request to API Gateway
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: PAY-001" \
  -d '{
    "accountId": "ACC123",
    "amount": 50.00,
    "recipientId": "RCPT456"
  }'

# Expected response (200 OK):
{
  "paymentId": "PAY-abc123def456",
  "status": "COMPLETED",
  "amount": 50.00,
  "accountId": "ACC123",
  "recipientId": "RCPT456",
  "timestamp": "2026-09-21T13:15:00Z"
}

# Save paymentId and correlationId for observability checks
PAYMENT_ID="PAY-abc123def456"
TRACE_ID="abc123def456"  # (obtained from response header or logs)
CORRELATION_ID="PAY-001"
```

#### Step 4: Verify Account Balance Changed

```bash
# Account balance should be reduced
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq .

# Expected: balance = 950.00 (was 1000.00, debited 50.00)
{
  "accountId": "ACC123",
  "balance": 950.00
}

# If different, check logs for why debit failed
```

#### Step 5: Verify Ledger Entry Created

```bash
# Query account service for ledger entries
curl -s http://localhost:8081/api/accounts/ACC123/ledger | jq .

# Expected: Array with entry showing debit
[
  {
    "id": 1,
    "type": "DEBIT",
    "amount": 50.00,
    "description": "Payment PAY-abc123def456",
    "timestamp": "2026-09-21T13:15:00Z"
  }
]
```

#### Step 6: Check Kafka Events (Optional, for advanced testing)

```bash
# Consume events from payment.completed topic
docker exec -it payments-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1

# Expected: JSON payload
{
  "paymentId": "PAY-abc123def456",
  "status": "COMPLETED",
  "amount": 50.00,
  "accountId": "ACC123",
  "timestamp": "2026-09-21T13:15:00Z",
  "traceId": "abc123def456",
  "correlationId": "PAY-001"
}
```

### Scenario: Fraud Rejection

```bash
# Try high-value payment (should trigger fraud check)
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: PAY-FRAUD-001" \
  -d '{
    "accountId": "ACC123",
    "amount": 50000.00,
    "recipientId": "RCPT789"
  }'

# Expected response (200 OK, but FAILED status):
{
  "paymentId": "PAY-xyz789abc",
  "status": "FAILED",
  "reason": "FRAUD_REJECTED",
  "amount": 50000.00,
  "accountId": "ACC123",
  "timestamp": "2026-09-21T13:15:30Z"
}

# Verify account balance NOT changed (payment was rejected)
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq .
# Expected: still 950.00 (no change)

# Verify ledger has NO entry for this failed payment
curl -s http://localhost:8081/api/accounts/ACC123/ledger | jq 'length'
# Expected: still 1 (only the first successful payment)
```

---

## Observability Verification

### 1. Grafana - Metrics Dashboard

#### Access Grafana

```bash
# Open in browser
http://localhost:3000

# Login
Username: admin
Password: admin

# Navigate: Home → Dashboards → Payment Platform Metrics
```

#### Verify Dashboard Panels

```
Panel 1: Request Rate (5m)
  - Should show activity from your payment requests
  - Look for spikes when you made API calls
  - Expected: Several req/sec during testing

Panel 2: P95 Latency (Gauge)
  - Should be in green zone (< 500ms typically)
  - Expected: 200-400ms for payment requests

Panel 3: Error Rate (5xx)
  - Should be near zero (flat line)
  - Expected: 0% if all requests succeeded

Panel 4: Request Rate by Service
  - Stacked area chart
  - Should show:
    * api-gateway-service (highest)
    * payment-service (spike when payments made)
    * account-service (same spike as payment-service)
    * fraud-service (same spike as payment-service)
    * notification-service (small spike after payment completes)
    * transaction-history-service (small spike after payment completes)
```

#### Query Prometheus Directly

```bash
# Open in browser
http://localhost:9090

# Query 1: Request count by service
Query: http_server_requests_seconds_count by (job)

# Expected results (example):
api-gateway-service    10
payment-service         5
account-service         5
fraud-service           5
notification-service    2
transaction-history     1

# Query 2: P95 latency
Query: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) by (job)

# Expected: Each service shows latency (milliseconds)

# Query 3: Error rate
Query: rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Expected: No results or 0 (no 5xx errors if tests passed)

# Query 4: Service up/down status
Query: up{job=~".*-service"}

# Expected: All services = 1 (up)
```

### 2. Kibana - Logs Dashboard

#### Access Kibana

```bash
# Open in browser
http://localhost:5601

# First time setup:
# - Click "Create index pattern"
# - Pattern: logs-*
# - Timestamp field: timestamp
# - Create index pattern

# Navigate: Discover
```

#### Search by Trace ID

```bash
# In Kibana Discover, search for your payment trace ID
# Query: traceId:<TRACE_ID>
# OR: correlationId:<CORRELATION_ID>

# Example:
traceId:abc123def456

# Expected results: All logs from payment flow
# Should see entries from:
#   - api-gateway-service
#   - payment-service
#   - account-service
#   - fraud-service
#   - (later) notification-service
#   - (later) transaction-history-service

# Each log entry should show:
#   - timestamp
#   - service name
#   - log level (INFO, DEBUG, ERROR)
#   - message
#   - traceId (same across all)
#   - spanId
#   - correlationId
```

#### Example Logs for Successful Payment

```
2026-09-21T13:15:00.100Z  [INFO]  api-gateway-service
  traceId=abc123def456
  message: "Processing request POST /api/payments"

2026-09-21T13:15:00.102Z  [INFO]  api-gateway-service
  traceId=abc123def456
  message: "JWT token validated successfully"

2026-09-21T13:15:00.103Z  [DEBUG] api-gateway-service
  traceId=abc123def456
  message: "Routing to payment-service:8083"

2026-09-21T13:15:00.110Z  [INFO]  payment-service
  traceId=abc123def456
  message: "Received payment request"
  paymentId: "PAY-abc123def456"

2026-09-21T13:15:00.112Z  [DEBUG] payment-service
  traceId=abc123def456
  message: "Starting saga orchestration"

2026-09-21T13:15:00.115Z  [DEBUG] payment-service
  traceId=abc123def456
  message: "Calling account-service to debit"

2026-09-21T13:15:00.120Z  [INFO]  account-service
  traceId=abc123def456
  message: "Processing debit request"
  amount: 50.00

2026-09-21T13:15:00.122Z  [DEBUG] account-service
  traceId=abc123def456
  message: "Checking account balance"
  accountId: "ACC123"
  currentBalance: 1000.00

2026-09-21T13:15:00.125Z  [INFO]  account-service
  traceId=abc123def456
  message: "Account debited successfully"
  newBalance: 950.00

2026-09-21T13:15:00.128Z  [DEBUG] payment-service
  traceId=abc123def456
  message: "Calling fraud-service to check risk"

2026-09-21T13:15:00.135Z  [INFO]  fraud-service
  traceId=abc123def456
  message: "Evaluating fraud risk"
  amount: 50.00

2026-09-21T13:15:00.138Z  [INFO]  fraud-service
  traceId=abc123def456
  message: "Risk assessment complete"
  riskScore: 30
  decision: "APPROVED"

2026-09-21T13:15:00.140Z  [DEBUG] payment-service
  traceId=abc123def456
  message: "Fraud check passed"

2026-09-21T13:15:00.142Z  [INFO]  payment-service
  traceId=abc123def456
  message: "Publishing payment.completed event"

2026-09-21T13:15:00.145Z  [INFO]  payment-service
  traceId=abc123def456
  message: "Saga completed successfully"

2026-09-21T13:15:00.150Z  [INFO]  api-gateway-service
  traceId=abc123def456
  message: "Response sent to client"
  status: 200
```

#### Search for Errors (if any)

```bash
# Query: level:ERROR

# Expected: No errors if tests passed
# If errors appear:
#   - Click on log entry for details
#   - Check stacktrace
#   - Look for which service failed
#   - Check docker logs: docker-compose logs <service-name>
```

#### Create a Dashboard (Optional)

```bash
# In Kibana, create a dashboard to visualize logs
# Canvas → Create dashboard
# Add visualizations:
#   - Request count over time
#   - Error count by service
#   - Latency percentiles
```

### 3. Jaeger - Request Tracing

#### Note: Jaeger Configuration

```
# In current setup, Jaeger backend is NOT running
# Spans are generated and ready to export, but exporter endpoint 
# (localhost:4317) is not configured in docker-compose

# To enable Jaeger:
# 1. Add jaeger service to docker-compose.yml
# 2. Update application.yml with exporter endpoint
# 3. Rebuild and redeploy

# For now, verify that tracing infrastructure is in place:
```

#### Verify Tracing is Active

```bash
# Check that spring-boot-actuator metrics include tracing metrics
curl -s http://localhost:8083/actuator/metrics | jq '.names[] | select(contains("trace"))'

# Expected output should show tracing metrics available
# (Even if not exported to Jaeger, they're recorded)

# Example metrics:
# - trace.http.requests
# - trace.errors
# - etc.
```

#### Manual Trace Inspection (Future)

```bash
# Once Jaeger is deployed, access at:
http://localhost:16686

# Search traces:
# Service: payment-service
# Operation: POST /api/payments
# Tags: correlationId=PAY-001

# Expected visualization:
# Timeline showing:
#  - api-gateway span (root)
#    └─ payment-service span
#       ├─ account-service span
#       └─ fraud-service span

# Latencies shown at each level
```

---

## Verification Checklist

### Infrastructure ✅

- [ ] All 13 containers running: `docker-compose ps`
- [ ] PostgreSQL healthy: `curl http://localhost:5432/` (connection test)
- [ ] Kafka ready: `docker exec payments-kafka kafka-topics.sh --list`
- [ ] Keycloak up: `curl http://localhost:8090/health`
- [ ] Elasticsearch up: `curl http://localhost:9200/_cluster/health`
- [ ] Fluent Bit connected to ES: `curl http://localhost:9200/_cat/indices`
- [ ] Kibana accessible: `curl http://localhost:5601/api/status`
- [ ] Prometheus scraping: `curl http://localhost:9090/api/v1/targets`
- [ ] Grafana accessible: `curl http://localhost:3000/api/health`

### Services ✅

- [ ] API Gateway (8080) responding: `curl http://localhost:8080/actuator/health`
- [ ] Account Service (8081) responding: `curl http://localhost:8081/actuator/health`
- [ ] Fraud Service (8082) responding: `curl http://localhost:8082/actuator/health`
- [ ] Payment Service (8083) responding: `curl http://localhost:8083/actuator/health`
- [ ] Notification Service (8084) responding: `curl http://localhost:8084/actuator/health`
- [ ] Transaction History (8085) responding: `curl http://localhost:8085/actuator/health`

### Database ✅

- [ ] Accounts table exists: `docker exec payments-postgres psql -U postgres -d account_db -c "\dt"`
- [ ] Payment table exists: `docker exec payments-postgres psql -U postgres -d payment_db -c "\dt"`
- [ ] Ledger table exists: `docker exec payments-postgres psql -U postgres -d account_db -c "\dt"`
- [ ] Outbox table exists: `docker exec payments-postgres psql -U postgres -d payment_db -c "\dt"`
- [ ] Transaction history table exists: `docker exec payments-postgres psql -U postgres -d transaction_history_db -c "\dt"`

### API Functionality ✅

- [ ] Fraud check endpoint works: `curl -X POST http://localhost:8082/api/fraud/check`
- [ ] Account balance readable: `curl http://localhost:8081/api/accounts/ACC123/balance`
- [ ] Payment creation works: `curl -X POST http://localhost:8080/api/payments`
- [ ] Accounts updated correctly after payment
- [ ] Ledger entries recorded
- [ ] Kafka events published to payment.completed topic

### Observability ✅

**Grafana:**
- [ ] Dashboard accessible: http://localhost:3000
- [ ] Request Rate panel shows data
- [ ] P95 Latency panel shows values
- [ ] Error Rate panel shows data
- [ ] Request Rate by Service shows all services

**Prometheus:**
- [ ] Metrics endpoint accessible: http://localhost:9090
- [ ] All 6 services showing in targets
- [ ] Queries return data (request count, latency, etc.)
- [ ] Alerts section shows configured rules

**Kibana:**
- [ ] Kibana accessible: http://localhost:5601
- [ ] Logs index exists (logs-*)
- [ ] Logs contain traceId fields
- [ ] Can search by correlationId
- [ ] Log entries show service name, level, message

---

## Troubleshooting

### Container Issues

#### Service won't start

```bash
# Check logs
docker-compose logs <service-name>

# Common issues:
# 1. Port already in use
#    → Change port in docker-compose.yml
#    → Or stop other services: lsof -i :8080

# 2. Database not ready
#    → Wait longer (30+ seconds)
#    → Check DB logs: docker-compose logs postgres

# 3. Memory issues
#    → Increase Docker memory limit
#    → Set: Settings → Resources → Memory to 4GB+
```

#### PostgreSQL not initializing

```bash
# Check init script
ls -la infrastructure/postgres/init.sql

# Reinit database
docker-compose down -v
docker volume rm payments_postgres_data  # If volume exists
docker-compose up -d postgres

# Wait 30 seconds, then check:
docker exec -it payments-postgres psql -U postgres -l
```

#### Kafka connection errors

```bash
# Service can't connect to Kafka
# Note: Use port 9092 from inside Docker, 9094 from host

# Check Kafka is ready
docker exec -it payments-kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Check advertised listeners
docker-compose logs kafka | grep "ADVERTISED_LISTENERS"
```

### API Errors

#### 401 Unauthorized on /api/* endpoints

```bash
# Missing or invalid JWT token
# Solutions:
# 1. Get token from Keycloak
# 2. Add Authorization header: Authorization: Bearer <TOKEN>
# 3. Check token isn't expired (expires in 300s)

# Or temporarily disable auth for testing (not for production):
# Edit application.yml: spring.security.oauth2... remove or comment out
```

#### 404 Not Found on payment request

```bash
# Service not running or wrong port
# Check: docker-compose ps
# Ensure payment-service shows "Up"

# Or routing issue in API Gateway
# Check logs: docker-compose logs api-gateway-service | grep -i error
```

#### Payment failed with "Connection refused"

```bash
# Payment service can't reach account/fraud service
# Check internal network: docker network ls
# Verify service names match docker-compose.yml

# Solutions:
# 1. Use service name (account-service:8081), not localhost
# 2. Check docker network is connected: docker network inspect payments_default
# 3. Restart services: docker-compose restart
```

### Database Issues

#### Foreign key constraint error

```bash
# Ledger entry references non-existent account
# Solution: Create account first before payment

curl -X POST http://localhost:8081/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC123", "initialBalance": 1000}'
```

#### "No database found to handle jdbc:postgresql"

```bash
# Flyway migration failed
# Solution: Check Flyway migration files in infrastructure/
# Verify init.sql ran: docker exec payments-postgres psql -U postgres -l

# If migrationssay failed:
docker-compose restart payment-service
docker-compose logs payment-service | tail -30
```

### Observability Issues

#### No data in Grafana

```bash
# Prometheus not scraping metrics
# Solutions:
# 1. Check Prometheus targets: http://localhost:9090/targets
#    All services should show "UP"
# 2. If "DOWN", check service health: curl http://localhost:8083/actuator/prometheus
# 3. Wait 15+ seconds for first scrape to complete

# 4. Check prometheus.yml config:
#    docker exec payments-prometheus cat /etc/prometheus/prometheus.yml
```

#### No logs in Kibana

```bash
# Fluent Bit not forwarding logs or ES not indexing
# Solutions:
# 1. Check Fluent Bit is running: docker-compose ps fluent-bit
# 2. Check logs: docker-compose logs fluent-bit | grep -i error
# 3. Verify ES is receiving:
#    curl http://localhost:9200/_cat/indices
#    Should show: logs-2026.09.21 (or today's date)

# 4. If no index:
#    - Check if services are logging to stdout
#    - Check logback-spring.xml configuration
#    - Redeploy service: docker-compose restart payment-service
```

#### Jaeger not receiving traces (if configured)

```bash
# Traces not exported
# Solutions:
# 1. Verify Jaeger service running and port accessible
# 2. Check application.yml for correct exporter endpoint
# 3. Check for export errors: docker-compose logs payment-service | grep -i export
# 4. Verify OpenTelemetry enabled: Check for "tracing" in application.yml
```

---

## Performance Expectations

### Response Times (P95)

```
Successful Payment Request:
  - API Gateway route:           50ms
  - Payment Service:             200ms
    ├─ Account Debit:           100ms
    └─ Fraud Check:              50ms
  - Total end-to-end:           250ms
  
Fraud-Rejected Payment:
  - Same as successful (same path, just returns FAILED status)

Single Account Query:
  - Database lookup:             20ms
  - Total:                       50ms
```

### Throughput

```
Single Instance:
  - Payment requests/second:     10-20 req/s
  - Limited by: Database (outbox), Kafka, network I/O
  - CPU usage:                   10-20% (single core used per JVM)
  - Memory per service:          200-400MB
  
Limiting Factors:
  - PostgreSQL write throughput
  - Kafka broker capacity
  - Network bandwidth
  - Database connection pool (default 10 per service)
```

### Resource Usage (at rest)

```
Container       Memory    CPU
────────────    ──────    ───
postgres        200MB     5%
kafka           300MB     3%
keycloak        400MB     10%
elasticsearch   1GB       5%
kibana          300MB     2%
prometheus      300MB     5%
grafana         200MB     2%
api-gateway     300MB     2%
payment-service 300MB     2%
account-service 300MB     2%
fraud-service   200MB     1%
notification    200MB     1%
tx-history      200MB     1%

Total:          ~4.5GB
```

### Database Query Performance

```
Account balance lookup:     < 5ms (indexed)
Payment insert:             5-10ms
Ledger insert:              5-10ms
Outbox insert:              5-10ms
Transaction rollback:       50-100ms (if failure)
```

---

## Sample Test Script

Save as `test-payment-platform.sh`:

```bash
#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Payment Platform E2E Test ===${NC}\n"

# Check if services are running
echo "1. Checking services..."
docker-compose ps | grep -q "payments-postgres" && echo -e "${GREEN}✓ PostgreSQL${NC}" || echo -e "${RED}✗ PostgreSQL${NC}"
docker-compose ps | grep -q "payments-kafka" && echo -e "${GREEN}✓ Kafka${NC}" || echo -e "${RED}✗ Kafka${NC}"
docker-compose ps | grep -q "payments-keycloak" && echo -e "${GREEN}✓ Keycloak${NC}" || echo -e "${RED}✗ Keycloak${NC}"

# Check health endpoints
echo -e "\n2. Checking health endpoints..."
curl -s http://localhost:8083/actuator/health | jq -e '.status == "UP"' > /dev/null && echo -e "${GREEN}✓ Payment Service${NC}" || echo -e "${RED}✗ Payment Service${NC}"
curl -s http://localhost:8081/actuator/health | jq -e '.status == "UP"' > /dev/null && echo -e "${GREEN}✓ Account Service${NC}" || echo -e "${RED}✗ Account Service${NC}"
curl -s http://localhost:8082/actuator/health | jq -e '.status == "UP"' > /dev/null && echo -e "${GREEN}✓ Fraud Service${NC}" || echo -e "${RED}✗ Fraud Service${NC}"

# Test fraud check
echo -e "\n3. Testing Fraud Check..."
FRAUD_RESPONSE=$(curl -s -X POST http://localhost:8082/api/fraud/check \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "accountId": "ACC123", "recipientId": "RCPT456"}')
echo "$FRAUD_RESPONSE" | jq -e '.decision == "APPROVED"' > /dev/null && echo -e "${GREEN}✓ Fraud check works${NC}" || echo -e "${RED}✗ Fraud check failed${NC}"

# Test Grafana
echo -e "\n4. Checking Observability..."
curl -s http://localhost:3000/api/health | jq -e '.database == "ok"' > /dev/null && echo -e "${GREEN}✓ Grafana${NC}" || echo -e "${RED}✗ Grafana${NC}"
curl -s http://localhost:9090/-/healthy > /dev/null && echo -e "${GREEN}✓ Prometheus${NC}" || echo -e "${RED}✗ Prometheus${NC}"
curl -s http://localhost:5601/api/status | jq -e '.state == "green"' > /dev/null && echo -e "${GREEN}✓ Kibana${NC}" || echo -e "${RED}✗ Kibana${NC}"

echo -e "\n${GREEN}=== Test Complete ===${NC}"
```

Run it:
```bash
chmod +x test-payment-platform.sh
./test-payment-platform.sh
```

---

## Next Steps After Verification

### If Everything Passes ✅

1. **Explore Dashboards**
   - Grafana: Make a payment, watch metrics update in real-time
   - Kibana: Search logs by correlationId
   - Prometheus: Query metrics directly

2. **Run Load Test (Phase 3)**
   - Use K6 or Apache JMeter
   - Make 100+ concurrent payments
   - Monitor system behavior under load

3. **Chaos Testing**
   - Simulate service failure: `docker-compose pause payment-service`
   - Verify circuit breaker kicks in
   - Resume service: `docker-compose unpause payment-service`
   - Verify recovery

4. **Production Readiness**
   - Deploy to Kubernetes (Phase 4)
   - Set up persistent volumes
   - Configure TLS/mTLS
   - Deploy service mesh (Istio)

### If Something Fails ❌

1. **Check Logs**
   ```bash
   docker-compose logs <service> | tail -50
   ```

2. **Check Connectivity**
   ```bash
   docker network inspect payments_default
   docker exec <service> ping <other-service>
   ```

3. **Reset & Retry**
   ```bash
   docker-compose down -v
   docker-compose up -d
   # Wait 60 seconds
   ./test-payment-platform.sh
   ```

4. **Check Troubleshooting Section**
   - Refer to section above for common issues
   - Check docker-compose.yml configuration
   - Verify port mappings in docker-compose.yml
