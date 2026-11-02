# End-to-End Testing Master Guide
## Complete Payment Platform Testing Workflow with Tools & Scripts

**Status**: Production-Ready | Phase 2 Complete | Full Observability Enabled  
**Last Updated**: 2026-09-21 | Version: 1.0

---

## Executive Summary (Senior Architect Overview)

This document provides a **single, comprehensive E2E testing workflow** for the Payment Platform. It integrates:

- **Existing Scripts**: `start-all.bat`, `stop-all.bat` (Docker + Java services)
- **Existing Postman Collection**: `postman-collection.json` (API testing)
- **Observability Tools**: Kibana, Grafana, Prometheus (real-time verification)
- **Infrastructure**: Docker Compose (13 containers)

**The workflow**:
```
START SYSTEM → VERIFY HEALTH → TEST APIs (Postman) → OBSERVE (Dashboards) → VERIFY DATA
```

**Time to complete**: 30-45 minutes (hands-on)

---

## Part 1: Understanding What We Have

### 1.1 Existing Start/Stop Scripts

**File**: `start-all.bat` (Windows) / `start-all.sh` (Linux/Mac)

**What it does**:
```
✓ Checks Docker is running
✓ Starts docker-compose (PostgreSQL, Kafka, Keycloak, Elasticsearch, Kibana, Prometheus, Grafana)
✓ Builds all microservices with Maven
✓ Starts each service in a new window
✓ Shows summary of endpoints
✓ Prints Postman setup instructions
```

**What it doesn't do** (by design):
- Doesn't open dashboards automatically
- Doesn't run tests
- Doesn't wait for all services to stabilize

**Expected output**: All 6 services + 7 infrastructure containers running

### 1.2 Existing Postman Collection

**File**: `postman-collection.json` (Main) + `postman-collection-gateway.json` (Gateway-only)

**Available test groups**:
1. **Setup & Variables** - Initialize test data
2. **Payment Service** - Create/query payments
3. **Account Service** - Balance, ledger, debit/credit
4. **Fraud Service** - Risk evaluation (approve/reject)
5. **Transaction History** - Query transactions
6. **Notification Service** - Event verification

**Built-in features**:
- Environment variables (URLs, account IDs, JWT tokens)
- Pre/post-test scripts (variable management)
- Response validation tests
- Error scenario testing

### 1.3 Existing Infrastructure

**Docker Compose**: 13 containers

```
Core Services (6):
├─ api-gateway-service (8080)
├─ account-service (8081)
├─ fraud-service (8082)
├─ payment-service (8083)
├─ notification-service (8084)
└─ transaction-history-service (8085)

Infrastructure (7):
├─ postgres (5432) - Databases
├─ kafka (9094) - Event streaming
├─ keycloak (8090) - Auth
├─ elasticsearch (9200) - Logs
├─ kibana (5601) - Log dashboard
├─ prometheus (9090) - Metrics
└─ grafana (3000) - Metrics dashboard
```

### 1.4 Existing Documentation

**Three detailed guides** (in `docs/` directory):
- `01-ARCHITECTURE-OVERVIEW.md` - System design
- `02-OBSERVABILITY-FLOW.md` - How tools work
- `03-E2E-TESTING-VERIFICATION.md` - Detailed testing procedures

**Quick start**: `TESTING-QUICKSTART.md`

---

## Part 2: Complete E2E Testing Workflow

### Step 1: Start the System (5 minutes)

#### Windows Users

```bash
# Navigate to project root
cd C:\path\to\payment-platform

# Run the start script
start-all.bat
```

**Expected output**:
```
[OK] Docker is running
[OK] Docker infrastructure started
[*] Building services...
[OK] All services built successfully
[*] Starting services...
   Starting: api-gateway-service (Port 8080)
   Starting: account-service (Port 8081)
   Starting: fraud-service (Port 8082)
   Starting: payment-service (Port 8083)
   Starting: notification-service (Port 8084)
   Starting: transaction-history-service (Port 8085)

[*] Waiting for services to start (60 seconds)...

Setup Complete!
All services should now be running:
   [OK] api-gateway-service:           http://localhost:8080
   [OK] account-service:               http://localhost:8081
   [OK] fraud-service:                 http://localhost:8082
   [OK] payment-service:               http://localhost:8083
   [OK] notification-service:          http://localhost:8084
   [OK] transaction-history-service:   http://localhost:8085

Next Steps:
1. Import Postman Collection: postman-collection-gateway.json
2. Run "Setup & Variables" > "Generate JWT Token"
3. Test any API with the token
```

#### Linux/Mac Users

```bash
chmod +x start-all.sh
./start-all.sh
```

**Same expected output as Windows**

#### What to do while waiting (60 seconds):

1. Open **Postman**: File → Import → Select `postman-collection-gateway.json`
2. Open dashboards in browser tabs:
   - Grafana: http://localhost:3000 (admin/admin)
   - Kibana: http://localhost:5601
   - Prometheus: http://localhost:9090
3. Have docker logs ready: `docker-compose logs -f`

---

### Step 2: Verify System Health (3 minutes)

#### Option A: Use Postman (Recommended)

**In Postman**:
1. Select collection: "Payment Platform - Microservices"
2. Go to: "Setup & Variables" → "Get test accounts (Setup)"
3. Click "Send"

**Expected**: 200 OK response

**This sets collection variables for all tests**:
- Payer Account ID
- Payee Account ID
- Service URLs
- JWT token (if available)

#### Option B: Use Curl

```bash
# Test each service health endpoint
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health

# Expected response (all services):
# {"status":"UP"}
```

#### Option C: Check Docker Compose

```bash
docker-compose ps

# Expected: All containers showing "Up" and "healthy"
```

---

### Step 3: Setup Authentication (2 minutes)

#### If using Postman Collection

**Go to**: Setup & Variables → Generate JWT Token

This request:
1. Calls Keycloak OAuth2 endpoint
2. Gets JWT token for client credentials flow
3. Automatically sets `Authorization` header for all subsequent requests

**Alternative**: Set manually in Postman
- Environment → current value: `your-jwt-token`
- All requests will use: `Authorization: Bearer {{token}}`

#### If using Curl

```bash
# Get JWT token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8090/realms/payment-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=payment-platform-client" \
  -d "client_secret=your-client-secret" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

echo $TOKEN
# Use in requests: -H "Authorization: Bearer $TOKEN"
```

---

### Step 4: Test Individual Services (8 minutes)

**Use Postman collection** (recommended):

#### 4.1 Payment Service

**Group**: "1. Payment Service (Port 8083)"

**Test sequence**:
1. ✓ "Health Check" → Should return 200 OK
2. ✓ "Create Payment - Valid (Happy Path)" 
   - Creates payment with $50
   - Expected: 200 OK, paymentId returned
   - **Note the paymentId for later verification**
3. ✓ "Get Payment Status"
   - Query the payment we just created
   - Expected: Status = "COMPLETED"

#### 4.2 Fraud Service

**Group**: "3. Fraud Service (Port 8082)"

**Test sequence**:
1. ✓ "Health Check" → 200 OK
2. ✓ "Evaluate Risk - Low Amount (Should APPROVE)"
   - $50 payment
   - Expected: `"decision": "APPROVED"`
3. ✓ "Evaluate Risk - High Amount (Should REJECT)"
   - $50,000 payment
   - Expected: `"decision": "REJECTED"`

#### 4.3 Account Service

**Group**: "2. Account Service (Port 8081)"

**Test sequence**:
1. ✓ "Health Check" → 200 OK
2. ✓ "Get Account Balance"
   - Check balance for payer account
   - Expected: Current balance (should be less after payment)
3. ✓ "Get Account Ledger (Transaction History)"
   - See all transactions for account
   - Expected: Debit entry for the payment we made
4. ✓ "Debit Account - Valid"
   - Test account debit operation
   - Expected: 200 OK, balance decreases

#### 4.4 Transaction History Service

**Group**: "4. Transaction History Service (Port 8085)"

**Test sequence**:
1. ✓ "Health Check" → 200 OK
2. ✓ "Get Account Transaction History"
   - Query transactions for payer account
   - Expected: List includes our payment
3. ✓ "Get Single Transaction by Payment ID"
   - Use paymentId from Step 4.1
   - Expected: Complete transaction details

#### 4.5 Notification Service

**Group**: "5. Notification Service (Port 8084)"

**Test**:
1. ✓ "Health Check" → 200 OK

---

### Step 5: Run Complete E2E Payment Flow (10 minutes)

This is the critical test combining all services with observability.

#### 5.1 Prepare Observability Tools

**Open in browser tabs** (keep them open):
- **Grafana**: http://localhost:3000 (admin/admin)
- **Kibana**: http://localhost:5601
- **Prometheus**: http://localhost:9090

#### 5.2 Test: Successful Payment

**In Postman**:

1. Run: "Setup & Variables" → "Get test accounts"
   - Sets all collection variables
   
2. Run: "1. Payment Service" → "Create Payment - Valid (Happy Path)"
   - This will:
     - Hit API Gateway (8080)
     - Route to Payment Service (8083)
     - Call Account Service (8081) to debit
     - Call Fraud Service (8082) for risk check
     - Publish event to Kafka
     - Update Transaction History (async)

**Expected response**:
```json
{
  "paymentId": "PAY-abc123def456",
  "status": "COMPLETED",
  "amount": 50.00,
  "accountId": "ACC-xxx",
  "recipientId": "RCPT-yyy",
  "timestamp": "2026-09-21T13:15:00Z"
}
```

**Save the paymentId and correlationId for next steps**

#### 5.3 Verify Data Flow (Database)

**In Postman**:

1. Run: "2. Account Service" → "Get Account Balance"
   - **Verify**: Balance decreased by $50
   - Expected: Original balance - 50.00

2. Run: "2. Account Service" → "Get Account Ledger"
   - **Verify**: New debit entry exists
   - Expected: `[{"type": "DEBIT", "amount": 50.00, ...}]`

3. Run: "4. Transaction History" → "Get Account Transaction History"
   - **Verify**: Payment appears in read model
   - Expected: Payment listed with COMPLETED status

#### 5.4 Verify Event Processing (Kafka)

**In terminal**:

```bash
# Consume from payment.completed topic to verify event was published
docker exec payments-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1

# Expected output:
# JSON with paymentId, status, amount, traceId, correlationId
```

---

### Step 6: Verify Observability Integration (10 minutes)

Now verify that **tracing, logging, and metrics** captured everything.

#### 6.1 Grafana - Metrics Dashboard

**Open**: http://localhost:3000 (admin/admin)

**Navigate**: Home → Dashboards → "Payment Platform Metrics"

**Verify panels**:

| Panel | What to Look For | Expected |
|-------|-----------------|----------|
| Request Rate (5m) | Spike in requests | Should show spike at test time |
| P95 Latency | Latency gauge | Should be 200-400ms (green) |
| Error Rate (5xx) | Error line | Should be flat at zero |
| Request Rate by Service | 6 service lines | Spikes on payment-service, account-service, fraud-service |

**Example**: After running payment test, you should see:
- api-gateway-service: 1 req
- payment-service: 1 req  
- account-service: 1 req
- fraud-service: 1 req
- (Later) notification-service: 1 req
- (Later) transaction-history-service: 1 req

#### 6.2 Kibana - Logs Search

**Open**: http://localhost:5601

**Navigate**: Discover

**Search for your payment**:
```
correlationId:PAY-abc123def456
```
(Use the correlationId from your payment response)

**Expected results** (in chronological order):
```
13:15:00.100  [api-gateway-service]      Processing payment
13:15:00.102  [api-gateway-service]      JWT validated
13:15:00.110  [payment-service]          Received payment request
13:15:00.115  [payment-service]          Calling account-service
13:15:00.120  [account-service]          Processing debit
13:15:00.125  [account-service]          Balance verified
13:15:00.128  [payment-service]          Calling fraud-service
13:15:00.135  [fraud-service]            Evaluating risk
13:15:00.140  [payment-service]          Fraud approved
13:15:00.145  [payment-service]          Publishing event
```

**Each log should show**:
- ✓ timestamp
- ✓ service name
- ✓ traceId (same across all logs)
- ✓ message and details

#### 6.3 Prometheus - Raw Metrics

**Open**: http://localhost:9090

**Query examples**:

```
# Request count by service
http_server_requests_seconds_count by (job)

# P95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Service health (1=up, 0=down)
up{job=~".*-service"}
```

**Expected** (after running tests):
- All services showing request counts > 0
- All services showing as "up" (value 1)
- Error count at 0
- Latency < 1 second

---

### Step 7: Test Failure Scenarios (8 minutes)

#### 7.1 Fraud Rejection (High-Value Payment)

**In Postman**:

Find the endpoint: "Create Payment - Valid (Happy Path)" but modify request body:

Change amount to: `50000.00` (was 50.00)

**Expected behavior**:
1. Payment request succeeds (200 OK)
2. But `status` is "FAILED" (not "COMPLETED")
3. Reason: "FRAUD_REJECTED"

**Verify in Kibana**:
- Search: `correlationId:YOUR-HIGH-VALUE-TEST`
- Should see fraud-service log: `"decision": "REJECTED"`
- Payment-service log: `"saga.compensation.started"`
- Account-service logs: debit → credit (reversal)

**Verify in Prometheus**:
- `http_server_requests_seconds_count{status="200"}` should still increment
- Error not counted as server error (payment logic, not system error)

#### 7.2 Validation Error

**In Postman**:

"Create Payment - Negative Amount (Validation Error)"

**Expected**: 400 Bad Request with error details

**Verify in Kibana**:
- Should see validation error log
- Not a system error (400 = client error)

#### 7.3 Idempotency Test

**In Postman**:

"Create Payment - Idempotency Test (Same Key)"

**What it does**:
- Sends same payment request twice with same idempotency key
- Second should return same response, not create duplicate payment

**Expected**:
- First request: 200 OK, paymentId = ABC123
- Second request: 200 OK, paymentId = ABC123 (same!)
- Only one debit in ledger

**Verify in Account Service Ledger**:
- Should see only ONE debit entry for this amount
- Not two (idempotency working)

---

### Step 8: Performance & Load Verification (5 minutes)

#### 8.1 Latency Check

**In Prometheus**:

Query: `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))`

**Expected results**:
```
api-gateway-service:        50-100ms
payment-service:            150-250ms
account-service:            50-100ms
fraud-service:              30-50ms
notification-service:       20-30ms
transaction-history:        20-30ms
```

**If too slow** (>500ms):
1. Check Grafana: Is CPU/memory spike?
2. Check Kibana: Any error logs?
3. Check database: `docker exec payments-postgres psql -U postgres -d payment_db -c "SELECT COUNT(*) FROM outbox WHERE published=false;"`

#### 8.2 Resource Usage

**In terminal**:

```bash
# Check container resource usage
docker stats

# Expected:
# postgres:        200MB, 5% CPU
# kafka:           300MB, 3% CPU
# elasticsearch:   1GB, 5% CPU
# All services:    200-400MB each, <5% CPU
```

If exceeding expected:
1. Check for memory leaks: `docker logs <service> | grep -i memory`
2. Check GC activity: Service logs should show periodic GC, not constant
3. Consider increasing Docker memory limit

---

### Step 9: Clean Shutdown (2 minutes)

#### Option A: Stop Only Docker (Keep Services Running)

```bash
# Stops: PostgreSQL, Kafka, Elasticsearch, etc.
# Services keep running (may error without DB)
docker-compose stop
```

#### Option B: Stop Everything (Windows)

```bash
stop-all.bat
```

**What it does**:
- Stops Docker containers
- You must manually close service windows
- Preserves database volumes (data intact)

#### Option C: Complete Cleanup

```bash
# Stops containers AND removes volumes (deletes all data)
docker-compose down -v

# Then close all service windows
```

---

## Part 3: Troubleshooting Quick Reference

### Issue: "Services won't start"

**Check**:
```bash
docker-compose logs payment-service | tail -50
```

**Common causes**:
1. **Port conflict**: Service already running on 8083
   - Solution: Kill existing process or change port
   
2. **Database not ready**: Services connecting too early
   - Solution: Wait 30+ seconds before running tests
   
3. **Memory issue**: Docker running out of memory
   - Solution: Increase Docker memory limit (Settings → Resources)

### Issue: "No logs in Kibana"

**Check**:
```bash
curl http://localhost:9200/_cat/indices
```

**If no logs-* index**:
1. Check Fluent Bit: `docker-compose logs fluent-bit`
2. Wait 10+ seconds after first log (Fluent Bit buffers)
3. Restart services: `docker-compose restart`

### Issue: "No metrics in Grafana"

**Check**:
```bash
curl http://localhost:9090/api/v1/targets
```

**If services show "down"**:
1. Verify service health: `curl http://localhost:8083/actuator/health`
2. Check Prometheus can reach service (network issue)
3. Wait 15+ seconds for first scrape

### Issue: "Payment fails with connection error"

**Check logs**:
```bash
docker-compose logs payment-service | grep -i "connection\|refused"
```

**Likely cause**: Service using `localhost` instead of `account-service`

**Solution**: Services should use DNS names (account-service:8081) not localhost

### Issue: "Postman getting 401 Unauthorized"

**Check**: Do you have a valid JWT token?

**Solution**:
1. Run: "Setup & Variables" → "Generate JWT Token"
2. Or check collection variable: `{{token}}`
3. Or in Environment: Ensure Authorization Bearer token is set

---

## Part 4: Integration Patterns (How Everything Works Together)

### 4.1 Request Flow with Observability

```
CLIENT
  ↓ POST /api/payments
API GATEWAY (generates traceId, spanId, correlationId)
  ↓ X-Trace-Id header
PAYMENT SERVICE (receives header, adds to MDC)
  ├─ Creates span "payment.process"
  ├─ JSON log includes: traceId, spanId, message
  │   └─ LOGBACK converts to JSON
  │       └─ stdout → Docker → Fluent Bit
  │           └─ Elasticsearch → Kibana (searchable)
  │
  ├─ Calls ACCOUNT SERVICE (Feign propagates traceId)
  │   └─ account-service span created (child of payment.process)
  │       └─ Logs include: same traceId, new spanId
  │
  └─ Calls FRAUD SERVICE (Feign propagates traceId)
      └─ fraud-service span created (child of payment.process)
          └─ Logs include: same traceId, new spanId

OBSERVABILITY CAPTURE:
  ├─ TRACING: Spans exported to Jaeger (via OpenTelemetry)
  │   └─ Jaeger shows: span tree, latency per service, flamegraph
  │
  ├─ LOGGING: Logs shipped to Elasticsearch
  │   └─ Kibana search: correlationId:XYZ
  │       └─ Shows all 5 services' logs in order
  │
  └─ METRICS: /actuator/prometheus scraped every 10s
      └─ Prometheus stores: request_count, request_latency, errors
          └─ Grafana charts: real-time dashboards
```

### 4.2 Data Consistency with Outbox Pattern

```
PAYMENT SERVICE writes:
  ├─ SAME TRANSACTION:
  │  ├─ Payment entity (status=PENDING)
  │  ├─ Outbox event (published=false)
  │  └─ COMMIT (all-or-nothing)
  │
  └─ ASYNC:
     ├─ OutboxPublisher polls: SELECT * FROM outbox WHERE published=false
     ├─ Publishes to Kafka: payment.completed
     └─ Updates: published=true
        └─ Guarantees: exactly-once delivery (idempotent consumers)

KAFKA CONSUMERS (async):
  ├─ NOTIFICATION SERVICE
  │  └─ Consumes: payment.completed
  │      └─ Sends email/SMS
  │
  └─ TRANSACTION HISTORY
     └─ Consumes: payment.completed
         └─ Updates read model (eventually consistent)

VERIFICATION:
  ├─ Database: Check payment entity exists
  ├─ Kafka: Check event was published
  ├─ Logs: Trace async processing
  └─ Read model: Query transaction history (may be 1-2s delayed)
```

### 4.3 Resilience with Circuit Breaker

```
PAYMENT SERVICE calling ACCOUNT SERVICE:

Normal State (CLOSED):
  Request → Account Service (fast) → Success
  ├─ Latency tracked
  ├─ Status recorded
  └─ Counter incremented

Degradation Detected (50% failure rate):
  ├─ Circuit OPENS (fail-fast mode)
  ├─ New requests fail immediately (no network call)
  └─ Logs show: "Circuit breaker OPEN for account-service"

Recovery Phase (HALF-OPEN):
  ├─ After 30 seconds, try 2 test requests
  ├─ If both succeed → Circuit CLOSES
  ├─ If either fails → Back to OPEN, wait 30s more
  └─ Logs show: "Circuit breaker HALF_OPEN, testing..."

VERIFICATION:
  ├─ Postman: Test should still get 200 OK (saga compensation)
  ├─ Kibana: Search "circuit" → see circuit breaker logs
  ├─ Grafana: See latency spike when circuit opens
  └─ Prometheus: http_client_requests_seconds shows increase
```

---

## Part 5: Testing Checklist

Use this to verify complete E2E workflow:

### Infrastructure ✓
- [ ] docker-compose ps → All 13 containers UP
- [ ] curl localhost:8080/actuator/health → UP
- [ ] curl localhost:8081/actuator/health → UP
- [ ] curl localhost:8082/actuator/health → UP
- [ ] curl localhost:8083/actuator/health → UP
- [ ] curl localhost:8084/actuator/health → UP
- [ ] curl localhost:8085/actuator/health → UP
- [ ] curl localhost:9200/_cluster/health → green

### Postman Tests ✓
- [ ] Setup & Variables → Get test accounts (200 OK)
- [ ] Payment Service → Health Check (200 OK)
- [ ] Payment Service → Create Payment Happy Path (200 OK, status=COMPLETED)
- [ ] Payment Service → Get Payment Status (returns COMPLETED)
- [ ] Account Service → Get Balance (balance decreased)
- [ ] Account Service → Get Ledger (debit entry exists)
- [ ] Fraud Service → Low Amount (decision=APPROVED)
- [ ] Fraud Service → High Amount (decision=REJECTED)
- [ ] Transaction History → Get History (payment appears)
- [ ] Fraud Payment → Status = FAILED

### Observability ✓
- [ ] Grafana dashboard loads (localhost:3000)
- [ ] Grafana shows request spike
- [ ] Grafana shows P95 latency (green zone)
- [ ] Kibana loads (localhost:5601)
- [ ] Kibana: Search by correlationId → 5+ log entries appear
- [ ] Kibana: Logs show traceId same across all services
- [ ] Prometheus targets → All services UP
- [ ] Prometheus: Query request count → >0 per service

### Data Integrity ✓
- [ ] Account balance decreases by payment amount
- [ ] Ledger shows debit entry with correct amount
- [ ] Kafka event published to payment.completed
- [ ] Transaction history record appears
- [ ] Idempotent payment (same key) returns same result

---

## Part 6: Performance Baselines

These are the **expected values** for a healthy system:

### Response Times (Percentiles)
```
P50 (Median):  80ms
P95 (95th):    250ms
P99 (99th):    500ms
Max:           <1000ms
```

### Throughput (Single Instance)
```
Requests/sec:  10-20
Limited by:    Database writes + Kafka
Bottleneck:    PostgreSQL sync commit (outbox insert)
```

### Resource Usage (at rest)
```
Memory:    4.5GB total
  - Elasticsearch: 1GB
  - Services: 200-400MB each
  - Docker: ~500MB

CPU:       5-15% (mostly Elasticsearch)
Disk:      500MB (app JARs + data)
```

### Scalability Factors
```
Memory:    Linear (per additional service instance)
Disk:      Linear (with data volume)
Database:  Non-linear (connection pool saturation at 10 concurrent)
Kafka:     Non-linear (broker memory with topics)
```

---

## Part 7: Next Steps After E2E Passes

Once you've verified everything works ✅:

### Immediate
1. **Run multiple payments** (5-10) to verify idempotency
2. **Check Grafana trends** over longer period (5+ min)
3. **Export Kibana logs** for audit trail
4. **Export Grafana dashboard** as JSON for backup

### Short-term (This Week)
1. **Load testing** (Phase 3)
   - Tools: K6 or Apache JMeter
   - Target: 100+ concurrent payments
   - Goal: Identify real bottlenecks

2. **API documentation** (Phase 3)
   - Generate OpenAPI from Postman
   - Publish on API portal

### Medium-term (This Month)
1. **Kubernetes deployment** (Phase 4)
   - Helm charts
   - Service discovery (DNS)
   - Load balancing

2. **Istio service mesh** (Phase 4)
   - Traffic management
   - mTLS encryption
   - Advanced circuit breaking

### Long-term (This Quarter)
1. **Analytics** (Phase 5)
   - Data warehouse (dbt)
   - BI dashboards
   - Fraud ML models

---

## Appendix A: Command Reference

### Docker Commands
```bash
# View all containers
docker-compose ps

# View container logs (real-time)
docker-compose logs -f payment-service

# Execute command in container
docker exec payments-postgres psql -U postgres -l

# Stop all containers
docker-compose stop

# Stop and remove everything
docker-compose down -v

# Restart specific service
docker-compose restart payment-service
```

### Postman Collection Usage
```
1. Import:
   File → Import → Select postman-collection.json

2. Set Environment:
   Environments → Create new or select "Payment Platform"

3. Run requests:
   - Select collection
   - Navigate to folder (Setup & Variables)
   - Click request
   - Click Send

4. View results:
   - Check Status code
   - Read Response body
   - View response time
```

### Useful Curl Commands
```bash
# Test service health
curl http://localhost:8083/actuator/health

# Make payment (requires JWT)
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC123","amount":50,"recipientId":"RCPT456"}'

# Check Kibana has logs
curl http://localhost:5601/api/status

# Check Prometheus metrics
curl http://localhost:9090/api/v1/query?query=up

# Check Elasticsearch
curl http://localhost:9200/_cat/indices
```

---

## Appendix B: Document Cross-Reference

| Need | Reference Document |
|------|-------------------|
| Understand architecture | `docs/01-ARCHITECTURE-OVERVIEW.md` |
| How observability works | `docs/02-OBSERVABILITY-FLOW.md` |
| Detailed test procedures | `docs/03-E2E-TESTING-VERIFICATION.md` |
| Quick reference | `TESTING-QUICKSTART.md` |
| This guide | `E2E-TESTING-MASTER.md` ← **You are here** |

---

## Summary

**This master guide provides**:
- ✅ Complete workflow from start to verification
- ✅ Integration of existing scripts (start-all.bat, Postman)
- ✅ Integration of observability tools (Grafana, Kibana, Prometheus)
- ✅ Clear step-by-step instructions (30-45 min to complete)
- ✅ Troubleshooting for common issues
- ✅ Performance baselines and expectations
- ✅ Verification checklist

**Time to complete full E2E test**: 30-45 minutes
- Start system: 5 min
- Verify health: 3 min
- Individual service tests: 8 min
- E2E payment flow: 10 min
- Observability verification: 10 min

**Ready to test?** Start with **Step 1: Start the System**

---

**Version**: 1.0  
**Status**: Production-Ready  
**Phase**: 2 (Observability) Complete  
**Next**: Phase 3 (Load Testing & API Docs)
