# Testing Quickstart - Payment Platform

Complete E2E Testing Guide - Get Started in 15 Minutes

## TL;DR (Quick Start)

```bash
# 1. Start the system
docker-compose down -v && docker-compose up -d && sleep 60

# 2. Test all services are healthy
curl http://localhost:8080/actuator/health && \
curl http://localhost:8083/actuator/health && \
curl http://localhost:8081/actuator/health

# 3. Make a payment
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: TEST-PAY-001" \
  -d '{"accountId":"ACC123","amount":50,"recipientId":"RCPT456"}'

# 4. Check logs
# Open: http://localhost:5601 → Discover → Search: correlationId:TEST-PAY-001

# 5. Check metrics
# Open: http://localhost:3000 (admin/admin) → Payment Platform Metrics

# Expected: Payment completes, balance changes, logs appear in Kibana, metrics in Grafana
```

## Complete Testing Workflow

### Prerequisites (5 min)

```bash
# Verify Java 21+
java -version

# Verify Docker running
docker -v && docker-compose -v

# Build the project
cd /path/to/payment-platform
./mvnw clean install -DskipTests   # ~2 min (skip tests for speed)
```

### Start System (1 min)

```bash
# Clean start (removes previous data)
docker-compose down -v
docker-compose up -d

# Watch startup (should complete in 30-60 seconds)
docker-compose logs -f

# Wait for: "Started in X.XXX seconds" messages
# (Press Ctrl+C after services start)
```

### Verify All Running (2 min)

```bash
# Check all containers
docker-compose ps
# Expected: All services showing "Up X minutes"

# Quick health check
for i in 8080 8081 8082 8083 8084 8085; do
  echo "Port $i:" && \
  curl -s http://localhost:$i/actuator/health | jq .status
done
# Expected: All showing "UP"
```

### Test Individual Services (3 min)

**Fraud Service (Stateless Risk Check)**
```bash
# Low-value payment (should approve)
curl -X POST http://localhost:8082/api/fraud/check \
  -H "Content-Type: application/json" \
  -d '{"amount": 50, "accountId": "ACC123", "recipientId": "RCPT456"}' | jq .

# Expected: { "riskScore": 30, "decision": "APPROVED" }

# High-value payment (should reject)
curl -X POST http://localhost:8082/api/fraud/check \
  -H "Content-Type: application/json" \
  -d '{"amount": 50000, "accountId": "ACC123", "recipientId": "RCPT456"}' | jq .

# Expected: { "riskScore": 85, "decision": "REJECTED" }
```

**Account Service (Database)**
```bash
# Check if account exists
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq .

# If 404, create account first (via API or database)
# Expected: { "accountId": "ACC123", "balance": 1000.00 }
```

### End-to-End Payment Flow (5 min)

**Happy Path: Successful Payment**
```bash
# Make payment request
RESPONSE=$(curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: HAPPY-PAY-001" \
  -d '{
    "accountId": "ACC123",
    "amount": 50.00,
    "recipientId": "RCPT456"
  }')

echo "$RESPONSE" | jq .

# Expected response:
# {
#   "paymentId": "PAY-abc123def456",
#   "status": "COMPLETED",
#   "amount": 50.00,
#   "accountId": "ACC123",
#   "timestamp": "2026-09-21T13:15:00Z"
# }

# Verify balance changed
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq '.balance'
# Expected: 950.00 (was 1000.00, debited 50.00)

# Verify ledger entry
curl -s http://localhost:8081/api/accounts/ACC123/ledger | jq '.[] | {type, amount}'
# Expected: { "type": "DEBIT", "amount": 50.00 }
```

**Sad Path: Fraud Rejection**
```bash
# Make high-value payment (should be rejected by fraud service)
RESPONSE=$(curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: FRAUD-PAY-002" \
  -d '{
    "accountId": "ACC123",
    "amount": 50000.00,
    "recipientId": "RCPT789"
  }')

echo "$RESPONSE" | jq .

# Expected response:
# {
#   "paymentId": "PAY-xyz789abc",
#   "status": "FAILED",
#   "reason": "FRAUD_REJECTED",
#   "amount": 50000.00
# }

# Verify balance NOT changed (payment rejected)
curl -s http://localhost:8081/api/accounts/ACC123/balance | jq '.balance'
# Expected: still 950.00 (no change from failed payment)
```

## Observability Verification (5 min)

### Grafana - Metrics Dashboard

**Access Dashboard**
```
Open: http://localhost:3000
Login: admin / admin
Navigate: Home → Dashboards → Payment Platform Metrics
```

**Verify Panels**
- **Request Rate (5m)**: Should spike when you made payments (10+ req/sec)
- **P95 Latency**: Should be 200-400ms (green zone)
- **Error Rate (5xx)**: Should be near zero (flat line)
- **Request Rate by Service**: Shows distribution across 6 services

### Kibana - Log Search

**Access Kibana**
```
Open: http://localhost:5601
Navigate: Discover
```

**Search for Your Payment**
```
Query: correlationId:HAPPY-PAY-001

# Expected results (in order):
# 1. api-gateway: Received POST /api/payments
# 2. payment-service: Started payment processing
# 3. account-service: Received debit request
# 4. account-service: Balance verified
# 5. fraud-service: Evaluating risk
# 6. fraud-service: Risk approved
# 7. payment-service: Publishing event
# 8. payment-service: Saga completed

# Each log should show:
# - timestamp
# - service name
# - traceId (same across all logs)
# - message and details
```

### Prometheus - Metric Queries

**Access Prometheus**
```
Open: http://localhost:9090
```

**Query Examples**
```
# Request count by service
http_server_requests_seconds_count by (job)

# P95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) by (job)

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Service health (1=up, 0=down)
up{job=~".*-service"}
```

## Verification Checklist

- [ ] All 13 containers running: `docker-compose ps`
- [ ] All 6 services healthy: Health endpoints return "UP"
- [ ] Fraud check works: Both low and high value test
- [ ] Successful payment: Balance changes, ledger updated
- [ ] Failed payment: Balance unchanged, no ledger entry
- [ ] Grafana dashboard loads and shows data
- [ ] Kibana shows logs with traceId/correlationId
- [ ] Prometheus metrics available
- [ ] Payment flow in Kibana shows 8+ log entries across services

## Troubleshooting

### Services not starting

```bash
# Check logs
docker-compose logs payment-service | tail -50

# Common issue: Port already in use
lsof -i :8080  # See what's using the port

# Solution: Wait longer or restart
docker-compose restart
sleep 30
```

### No logs in Kibana

```bash
# Wait longer (logs may not appear immediately)
sleep 10

# Or check if Fluent Bit is running
docker-compose logs fluent-bit

# Check Elasticsearch has data
curl http://localhost:9200/_cat/indices
# Should show: logs-2026.09.21 (or today's date)
```

### Payment returns error

```bash
# Check Payment Service logs
docker-compose logs payment-service | grep -i error

# Likely causes:
# 1. Account doesn't exist → Create account first
# 2. Connection refused → Services not ready, wait 30s more
# 3. Out of memory → Increase Docker memory limit
```

### No metrics in Grafana

```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq .

# All services should show "up": true
# If not, services not responding to metrics endpoint

# Check if metrics endpoint works
curl http://localhost:8083/actuator/prometheus | head -20

# Should show: # HELP http_server_requests_seconds
```

## Performance Expectations

| Metric | Expected | Status |
|--------|----------|--------|
| Payment latency (P95) | 250ms | ✓ |
| Successful payment rate | 100% (happy path) | ✓ |
| Fraud rejection rate | 100% (high-value) | ✓ |
| Database query time | <50ms | ✓ |
| Kafka publish latency | <20ms | ✓ |
| Total system memory | ~4.5GB | ✓ |
| Container startup | <60s | ✓ |

## What Each Tool Shows

| Tool | Purpose | What to Check |
|------|---------|---------------|
| **Grafana** | Metrics & alerts | Real-time system health, spikes, errors |
| **Kibana** | Log search & analysis | Request flow by traceId, debug details |
| **Prometheus** | Metrics collection | Direct metric queries, alert rules |
| **Docker** | Service status | Container health, logs, resource usage |

## Full Documentation

For detailed information, see:

1. **docs/01-ARCHITECTURE-OVERVIEW.md**
   - System design and data flows
   - Service responsibilities
   - Phase roadmap

2. **docs/02-OBSERVABILITY-FLOW.md**
   - How tracing works end-to-end
   - Structured logging pipeline
   - Metrics collection flow

3. **docs/03-E2E-TESTING-VERIFICATION.md**
   - Complete step-by-step guide
   - Advanced testing scenarios
   - Comprehensive troubleshooting

## Next Steps

### If All Tests Pass ✅

1. **Explore More Payments**
   - Try different amounts
   - Multiple accounts
   - Rapid succession (stress test)

2. **Explore Dashboards**
   - Grafana: Watch metrics update in real-time
   - Kibana: Search logs for patterns
   - Prometheus: Write complex queries

3. **Move to Phase 3: API Docs & Load Testing**
   - Generate OpenAPI documentation
   - Run load tests with K6 or JMeter
   - Measure system limits

### If Tests Fail ❌

1. **Check Logs**
   - `docker-compose logs <service>`
   - Look for error messages

2. **Check Connectivity**
   - `docker exec <service> ping <other-service>`
   - Verify port mappings

3. **Reset & Retry**
   - `docker-compose down -v`
   - `docker-compose up -d`
   - Wait 60 seconds
   - Re-run tests

4. **See Troubleshooting Section**
   - More details in docs/03-E2E-TESTING-VERIFICATION.md

## Support

For issues or questions:

1. Check the troubleshooting section above
2. Review detailed docs in docs/ directory
3. Check Docker logs: `docker-compose logs`
4. Verify prerequisites are met
5. Try full reset: `docker-compose down -v && docker-compose up -d`

---

**Last Updated:** 2026-09-21
**Status:** Payment Platform Phase 2 (Observability) Complete
**Next:** Phase 3 (API Docs & Load Testing)
