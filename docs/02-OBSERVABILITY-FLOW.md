# Observability Flow: Tracing, Logging, and Metrics

## Request Tracing Flow (OpenTelemetry + Jaeger)

### How Tracing Works

When a payment request arrives:

```
1. CLIENT SENDS REQUEST
   POST /api/payments
   Headers: (no trace headers)
   
   
2. API GATEWAY creates ROOT SPAN
   ├─ Generate: traceId = "abc123def456" (global request identifier)
   ├─ Generate: spanId = "gateway001"
   ├─ Generate: correlationId = "CORR-12345" (business identifier)
   ├─ Create span: "HTTP POST /api/payments"
   │  ├─ Attributes:
   │  │  ├─ http.method = POST
   │  │  ├─ http.url = /api/payments
   │  │  ├─ http.status_code = 200 (filled later)
   │  │  ├─ http.client_ip = 192.168.1.100
   │  │  └─ span.kind = SERVER
   │  └─ Events (recorded during request):
   │     ├─ "request.started"
   │     ├─ "auth.validated"
   │     ├─ "routing.to.payment_service"
   │     └─ "request.completed"
   │
   └─ Add to HTTP response headers:
      ├─ traceparent: 00-abc123def456...-gateway001-01
      └─ X-Trace-Id: abc123def456
      └─ X-Span-Id: gateway001
      └─ X-Correlation-Id: CORR-12345
   
   
3. GATEWAY CALLS PAYMENT SERVICE (Feign Client)
   ├─ Read trace headers from HTTP response context
   ├─ Create CHILD SPAN: "HTTP POST payment-service:8083/api/payments"
   │  ├─ Parent Span ID: gateway001 ◄── Links to gateway span
   │  ├─ Span ID: payment001
   │  ├─ Trace ID: abc123def456 ◄── SAME as root
   │  ├─ Attributes:
   │  │  ├─ rpc.service = payment-service
   │  │  ├─ http.method = POST
   │  │  └─ span.kind = CLIENT
   │  └─ Events:
   │     ├─ "rpc.message.send"
   │     ├─ "rpc.message.receive"
   │     └─ "rpc.finished"
   │
   └─ Add trace headers to outgoing Feign HTTP request:
      ├─ traceparent: 00-abc123def456...-payment001-01
      └─ X-Trace-Id: abc123def456
      └─ X-Span-Id: payment001
      └─ X-Correlation-Id: CORR-12345
   
   
4. PAYMENT SERVICE receives request
   ├─ Extract trace headers from HTTP request:
   │  ├─ traceId = abc123def456
   │  ├─ parentSpanId = gateway001
   │  └─ correlationId = CORR-12345
   │
   ├─ Create CHILD SPAN: "payment.process" (business operation)
   │  ├─ Parent Span ID: payment001 ◄── Links to Feign client span
   │  ├─ Span ID: payment_saga001
   │  ├─ Trace ID: abc123def456 ◄── SAME
   │  ├─ Attributes:
   │  │  ├─ payment.id = PAY123
   │  │  ├─ payment.amount = 50.00
   │  │  └─ span.kind = INTERNAL
   │  └─ Events:
   │     ├─ "saga.started"
   │     ├─ "debit.requested"
   │     ├─ "fraud.check.requested"
   │     ├─ "saga.completed"
   │     └─ "event.published"
   │
   ├─ Create nested spans for saga steps:
   │  │
   │  ├─ CHILD SPAN: "account.debit" (call to account-service)
   │  │  ├─ Parent Span ID: payment_saga001
   │  │  ├─ Span ID: account001
   │  │  ├─ Trace ID: abc123def456
   │  │  ├─ Attributes: { account.id=ACC123, amount=50 }
   │  │  └─ Status: OK / ERROR
   │  │
   │  └─ CHILD SPAN: "fraud.check" (call to fraud-service)
   │     ├─ Parent Span ID: payment_saga001
   │     ├─ Span ID: fraud001
   │     ├─ Trace ID: abc123def456
   │     ├─ Attributes: { risk_score=30, decision=APPROVED }
   │     └─ Status: OK
   │
   └─ Add trace context to Feign calls:
      POST account-service with headers:
        ├─ traceparent: 00-abc123def456...-account001-01
        └─ X-Trace-Id: abc123def456
      
      POST fraud-service with headers:
        ├─ traceparent: 00-abc123def456...-fraud001-01
        └─ X-Trace-Id: abc123def456
   
   
5. ACCOUNT SERVICE receives debit request
   ├─ Extract trace context:
   │  ├─ traceId = abc123def456
   │  ├─ parentSpanId = account001
   │  └─ Add to MDC (Mapped Diagnostic Context)
   │
   ├─ Create CHILD SPAN: "account.debit.execute"
   │  ├─ Parent Span ID: account001
   │  ├─ Span ID: account_debit_001
   │  ├─ Trace ID: abc123def456
   │  └─ Child spans for:
   │     ├─ database.transaction (SERIALIZABLE isolation)
   │     ├─ database.query (SELECT balance FOR UPDATE)
   │     └─ database.update (debit)
   │
   └─ All logs include traceId in MDC
   
   
6. FRAUD SERVICE receives risk check request
   ├─ Extract trace context (same process)
   ├─ Create CHILD SPAN: "fraud.evaluate"
   │  ├─ Parent Span ID: fraud001
   │  ├─ Span ID: fraud_eval_001
   │  └─ Attributes: { rules_evaluated=5, factors_checked=3 }
   └─ No database call (stateless service)
   
   
7. PAYMENT SERVICE completes saga
   ├─ Both children succeeded
   ├─ Finish CHILD SPAN: payment_saga001 (status=OK, duration=245ms)
   │
   └─ Publish events with trace context:
      ├─ payment.completed event:
      │  └─ Include: traceId, spanId, correlationId (metadata)
      └─ Event consumed by:
         ├─ notification-service (new trace context)
         └─ transaction-history-service (new trace context)
   
   
8. PAYMENT SERVICE returns to GATEWAY
   ├─ Finish CHILD SPAN: payment001 (status=OK, duration=250ms)
   └─ Response includes span context
   
   
9. GATEWAY returns to CLIENT
   ├─ Finish ROOT SPAN: gateway001 (status=OK, duration=260ms)
   └─ Response includes traceparent header (optional for client-side tracing)


10. TRACE EXPORTED TO JAEGER
    ├─ MeterRegistry batches spans
    ├─ OpenTelemetry OTLP exporter sends to Jaeger collector
    ├─ Jaeger stores trace tree in Elasticsearch backend
    │
    └─ Trace tree structure (visible in Jaeger UI):
       
       abc123def456 (traceId)
       └─ gateway001 (api-gateway, 260ms)
          ├─ Events: request.started, auth.validated, routing.to.payment_service
          │
          └─ payment001 (payment-service Feign call, 250ms)
             │
             └─ payment_saga001 (payment.process, 245ms)
                ├─ Events: saga.started, debit.requested, fraud.check.requested
                │
                ├─ account001 (account-service Feign call, 120ms)
                │  │
                │  └─ account_debit_001 (account.debit.execute, 115ms)
                │     ├─ Events: transaction.started, balance.verified, debited
                │     └─ Child spans: database.transaction, database.query, database.update
                │
                └─ fraud001 (fraud-service Feign call, 50ms)
                   │
                   └─ fraud_eval_001 (fraud.evaluate, 45ms)
                      └─ Events: rules_evaluated=5, decision_calculated
```

### Key Tracing Concepts

**Trace ID (Global)**
- Unique identifier for entire request flow
- Same across all services
- Example: `abc123def456`
- Used to correlate all logs and spans

**Span ID (Per Operation)**
- Unique within a trace
- Hierarchical: child spans have parent span ID
- Example: `gateway001`, `payment001`, `account_debit_001`
- Shows causality and timing

**Baggage & Context Propagation**
- Trace context stored in MDC (Mapped Diagnostic Context)
- Spring automatically adds to all logs
- Feign clients automatically propagate headers
- Database spans inherit context

## Logging Flow (Structured JSON + ELK)

### Log Collection Pipeline

```
1. PAYMENT SERVICE logs event
   
   logger.info("Processing payment", Map.of(
       "paymentId", "PAY123",
       "amount", 50.00
   ));
   
   
2. LOGBACK-SPRING.XML converts to JSON
   ├─ Logstash encoder intercepts log event
   ├─ Reads MDC values:
   │  ├─ traceId = "abc123def456" ◄── From OpenTelemetry Context
   │  ├─ spanId = "payment_saga001" ◄── From OpenTelemetry Context
   │  └─ correlationId = "CORR-12345" ◄── From HTTP header
   │
   └─ Outputs JSON to STDOUT:
      {
        "timestamp": "2026-09-21T13:15:00.123Z",
        "level": "INFO",
        "logger": "com.payments.platform.PaymentService",
        "message": "Processing payment",
        "service": "payment-service",
        "traceId": "abc123def456",           ◄── Injected
        "spanId": "payment_saga001",         ◄── Injected
        "correlationId": "CORR-12345",       ◄── Injected
        "idempotencyKey": null,
        "paymentId": "PAY123",               ◄── Custom fields
        "amount": 50.00
      }
   
   
3. DOCKER COLLECTS LOGS
   └─ Docker captures STDOUT/STDERR from container
   
   
4. FLUENT BIT reads Docker logs
   ├─ Input plugin: forward protocol (port 24224)
   ├─ Filter plugins:
   │  ├─ parser: Detect JSON format
   │  ├─ record_modifier: Add cluster/environment tags
   │  └─ enrichment: (future: add service IP, pod ID)
   │
   └─ Parse JSON:
      {
        "timestamp": "2026-09-21T13:15:00.123Z",
        "level": "INFO",
        "logger": "com.payments.platform.PaymentService",
        "message": "Processing payment",
        "service": "payment-service",
        "traceId": "abc123def456",
        "spanId": "payment_saga001",
        "correlationId": "CORR-12345",
        "paymentId": "PAY123",
        "amount": 50.00,
        "cluster": "payment-platform",       ◄── Added by filter
        "environment": "development"         ◄── Added by filter
      }
   
   
5. FLUENT BIT sends to ELASTICSEARCH
   ├─ Output plugin: Elasticsearch
   ├─ Index: logs-2026.09.21
   ├─ Type: _doc (no types in ES 8.x)
   │
   └─ Elasticsearch stores document:
      {
        "_index": "logs-2026.09.21",
        "_id": "uuid-12345",
        "_source": {
          "timestamp": "2026-09-21T13:15:00.123Z",
          "level": "INFO",
          "logger": "com.payments.platform.PaymentService",
          "message": "Processing payment",
          "service": "payment-service",
          "traceId": "abc123def456",
          "spanId": "payment_saga001",
          "correlationId": "CORR-12345",
          "paymentId": "PAY123",
          "amount": 50.00,
          "cluster": "payment-platform",
          "environment": "development"
        }
      }
   
   
6. KIBANA queries ELASTICSEARCH
   ├─ User query: traceId:abc123def456
   ├─ Elasticsearch returns all logs from this request:
   │
   └─ Results (chronological):
      
      13:15:00.100 payment-service  [abc123def456] Processing payment PAY123
      13:15:00.102 payment-service  [abc123def456] Calling account-service debit
      13:15:00.115 account-service  [abc123def456] Received debit request
      13:15:00.117 account-service  [abc123def456] Checking balance
      13:15:00.120 account-service  [abc123def456] Balance verified: 1000.00 > 50.00
      13:15:00.122 account-service  [abc123def456] Debiting account
      13:15:00.125 account-service  [abc123def456] Ledger entry created
      13:15:00.127 payment-service  [abc123def456] Account debit succeeded
      13:15:00.128 payment-service  [abc123def456] Calling fraud-service check
      13:15:00.145 fraud-service    [abc123def456] Evaluating fraud risk
      13:15:00.150 fraud-service    [abc123def456] Risk score: 30 (APPROVED)
      13:15:00.152 payment-service  [abc123def456] Fraud check passed
      13:15:00.153 payment-service  [abc123def456] Publishing payment.completed
      13:15:00.155 payment-service  [abc123def456] Saga completed successfully
```

### Kibana Queries

```
# Find all logs for a payment
POST /kibana/api/saved_objects/search
{
  "query": "traceId:abc123def456"
}

# Find all errors in payment-service in last hour
{
  "query": "service:payment-service AND level:ERROR AND @timestamp:[now-1h TO now]"
}

# Find all payment failures
{
  "query": "correlationId:* AND message:*FAILED* AND @timestamp:[now-24h TO now]"
}

# Find slow requests (P95 > 1s)
{
  "query": "duration_ms:>1000"
}

# Dashboard: Payment flow visualization
Group logs by traceId, render timeline of services
(Kibana Canvas or Kibana Lens)
```

## Metrics Collection Flow (Prometheus + Grafana)

### Metrics Collection Pipeline

```
1. APPLICATION RECORDS METRICS
   
   During payment processing:
   ├─ Spring Boot auto-instruments HTTP requests:
   │  └─ http_server_requests_seconds_bucket{job="payment-service", method="POST", uri="/api/payments", status="200", le="0.5"} = 0
   │     http_server_requests_seconds_bucket{job="payment-service", method="POST", uri="/api/payments", status="200", le="1"} = 1
   │     http_server_requests_seconds_bucket{job="payment-service", method="POST", uri="/api/payments", status="200", le="+Inf"} = 1
   │     http_server_requests_seconds_sum{job="payment-service", method="POST", uri="/api/payments", status="200"} = 0.245
   │     http_server_requests_seconds_count{job="payment-service", method="POST", uri="/api/payments", status="200"} = 1
   │
   ├─ Micrometer timers (Feign calls):
   │  └─ http_client_requests_seconds_sum{job="payment-service", target.host="account-service", method="POST", status="200"} = 0.120
   │
   ├─ Database connection pool:
   │  └─ tomcat_jdbc_connections_active{pool="payment-service"} = 2 (out of 10 max)
   │
   └─ Kafka producer:
      └─ kafka_producer_record_send_errors_total{job="payment-service"} = 0
   
   
2. PROMETHEUS SCRAPES METRICS
   ├─ Every 10 seconds, Prometheus makes HTTP request:
   │  GET payment-service:8083/actuator/prometheus
   │
   ├─ Receives text format:
   │  # HELP http_server_requests_seconds_bucket
   │  # TYPE http_server_requests_seconds_bucket histogram
   │  http_server_requests_seconds_bucket{job="payment-service",...} 0
   │  ...
   │  # HELP jvm_memory_used_bytes
   │  # TYPE jvm_memory_used_bytes gauge
   │  jvm_memory_used_bytes{area="heap",job="payment-service"} 256000000
   │  ...
   │
   └─ Stores time-series data (TSDB):
      Timestamp: 2026-09-21T13:15:00Z
      Metric: http_server_requests_seconds_count
      Labels: {job="payment-service", method="POST", uri="/api/payments", status="200"}
      Value: 1
      
      Timestamp: 2026-09-21T13:15:10Z
      Metric: http_server_requests_seconds_count
      Labels: {job="payment-service", method="POST", uri="/api/payments", status="200"}
      Value: 2 (second request)
      
      Timestamp: 2026-09-21T13:15:20Z
      Metric: http_server_requests_seconds_count
      Labels: {job="payment-service", method="POST", uri="/api/payments", status="200"}
      Value: 3 (third request)
   
   
3. PROMETHEUS EVALUATES RULES
   ├─ Every 15 seconds, evaluate alert rules:
   │
   ├─ Rule: "HighErrorRate"
   │  expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
   │  Calculation:
   │    Count of 5xx errors in last 5 minutes: 2
   │    Duration: 5 minutes = 300 seconds
   │    Rate: 2 / 300 = 0.0067 requests/second
   │    Threshold: 0.05
   │    Alert triggered?: NO (0.0067 < 0.05)
   │
   └─ Rule: "ServiceDown"
      expr: up{job=~".*-service"} == 0
      For: 1 minute
      If service stops responding, mark up=0
      Trigger alert if still 0 after 1 minute
   
   
4. GRAFANA QUERIES PROMETHEUS
   ├─ Query: rate(http_server_requests_seconds_count[5m])
   │  Returns:
   │  {job="api-gateway-service"}: 10.5 req/sec
   │  {job="payment-service"}: 2.3 req/sec
   │  {job="account-service"}: 2.5 req/sec
   │  {job="fraud-service"}: 1.8 req/sec
   │  {job="notification-service"}: 0.5 req/sec
   │  {job="transaction-history-service"}: 0.2 req/sec
   │
   ├─ Query: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
   │  Returns:
   │  {job="api-gateway-service"}: 0.15s (P95)
   │  {job="payment-service"}: 0.25s (P95)
   │  {job="account-service"}: 0.12s (P95)
   │  {job="fraud-service"}: 0.05s (P95)
   │
   └─ Query: up{job=~".*-service"}
      Returns:
      {job="api-gateway-service"}: 1 (up)
      {job="payment-service"}: 1 (up)
      {job="account-service"}: 1 (up)
      {job="fraud-service"}: 1 (up)
      {job="notification-service"}: 1 (up)
      {job="transaction-history-service"}: 1 (up)
   
   
5. GRAFANA RENDERS DASHBOARDS
   ├─ Panel 1: Request Rate
   │  Time series: 10.5 req/sec (api-gateway)
   │  ┌─────────────────────────────────┐
   │  │     ╱╲    ╱╲    ╱╲              │
   │  │ ╱╲╱  ╲╱╲╱  ╲╱╲╱  ╲            │
   │  │                                 │
   │  └─────────────────────────────────┘
   │
   ├─ Panel 2: P95 Latency (Gauge)
   │  ╭─────────────╮
   │  │      250ms  │ ◄── Warning (yellow)
   │  │  Threshold: │
   │  │  1000ms     │
   │  ╰─────────────╯
   │
   ├─ Panel 3: Error Rate
   │  Green line near zero
   │  (< 0.05 threshold)
   │
   └─ Panel 4: Service Status
      ✓ api-gateway-service
      ✓ payment-service
      ✓ account-service
      ✓ fraud-service
      ✓ notification-service
      ✓ transaction-history-service
```

## Complete Observability Loop

```
REQUEST                    TRACING                      LOGGING                    METRICS
─────────                  ───────                      ───────                    ───────
   │                          │                            │                          │
   ▼                          ▼                            ▼                          ▼
┌─────────────┐         ┌──────────────┐          ┌─────────────────┐      ┌──────────────────┐
│ Client POST │         │ Create span  │          │ JSON log event  │      │ Record histogram │
│ /api/        │        │ + traceId    │          │ + traceId       │      │ http_requests    │
│ payments    │         │ + spanId     │          │ + spanId        │      │ _seconds_bucket  │
└─────────────┘         └──────────────┘          └─────────────────┘      └──────────────────┘
                              │                            │                          │
                              │                            │                          │
     ┌────────────────────────┼────────────────────────────┼──────────────────────────┘
     │                        │                            │
     ▼                        ▼                            ▼
  PROPAGATE               EXPORT               FLUENT BIT              PROMETHEUS
  HEADERS                 SPANS                AGGREGATES              SCRAPES
     │                        │                            │                          │
     │ Add traceparent        │ Batch spans              │ Ships JSON               │ Every 10s
     │ X-Trace-Id             │ Send OTLP protocol       │ to ES                    │ Gets metrics
     │                        │ to Jaeger port 4317      │                          │ Stores TSDB
     │                        │                          │                          │
     ▼                        ▼                          ▼                          ▼
DOWNSTREAM           ┌──────────────────┐    ┌─────────────────┐      ┌──────────────────┐
SERVICE              │  JAEGER BACKEND  │    │  ELASTICSEARCH  │      │   PROMETHEUS     │
RECEIVES             │  (Elasticsearch) │    │   (Storage)     │      │  (Time-series)   │
CONTEXT              │                  │    │                 │      │                  │
                     │ Stores trace     │    │ Stores JSON     │      │ Stores metrics   │
                     │ trees            │    │ documents       │      │ with labels      │
                     └──────────────────┘    └─────────────────┘      └──────────────────┘
                              │                         │                          │
                              │                         │                          │
                              ▼                         ▼                          ▼
                        ┌──────────────────┐  ┌─────────────────┐      ┌──────────────────┐
                        │  JAEGER UI       │  │  KIBANA UI      │      │  GRAFANA UI      │
                        │  (Visualization) │  │  (Log Search)   │      │  (Dashboards)    │
                        │                  │  │                 │      │                  │
                        │ View trace tree  │  │ Query logs by   │      │ Real-time charts │
                        │ Span latencies   │  │ traceId         │      │ Alert status     │
                        │ Flamegraph       │  │ Correlate flow  │      │ Service health   │
                        └──────────────────┘  └─────────────────┘      └──────────────────┘
                              │                         │                          │
                              │                         │                          │
                    ┌──────────┴─────────────────────────┴──────────────────────────┐
                    │                                                              │
                    ▼                                                              ▼
               OPERATOR                                                      OPERATOR
               Sees trace of                                                 Sees metrics
               payment request                                               of system
               across all                                                    health and
               services                                                      performance
```

## Correlation IDs vs Trace IDs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TRACE ID (Technical)                                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Generated by API Gateway                                                  │
│ • Unique per HTTP request                                                   │
│ • Format: UUID or hex string                                                │
│ • Example: abc123def456                                                     │
│ • Used: Tracing, logging, metrics correlation                               │
│ • Scope: Entire request flow through all services                           │
│ • Resets: On async event processing (notification-service gets new traceId) │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ CORRELATION ID (Business)                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Provided by client in request header (X-Correlation-Id)                   │
│ • If not provided, gateway generates one                                    │
│ • Unique per business transaction (payment, refund, etc.)                   │
│ • Format: Depends on client system                                          │
│ • Example: CORR-12345, ORDER-567                                            │
│ • Used: Logging, business-level tracking, audit trails                      │
│ • Scope: Entire payment lifecycle (including async events)                  │
│ • Preserved: Passed through to Kafka events (outbox)                        │
│ • Purpose: Link related operations (payment + notifications + ledger)       │
└─────────────────────────────────────────────────────────────────────────────┘

Example logs from same payment request:

  13:15:00 api-gateway      traceId=abc123 correlationId=CORR-12345
  13:15:00 payment-service  traceId=abc123 correlationId=CORR-12345
  13:15:00 account-service  traceId=abc123 correlationId=CORR-12345
  13:15:00 fraud-service    traceId=abc123 correlationId=CORR-12345
  
  (Async event published to Kafka)
  
  13:15:02 notification-service  traceId=xyz789 correlationId=CORR-12345
  13:15:02 transaction-history   traceId=xyz999 correlationId=CORR-12345
  
  ↑ New trace IDs (async boundaries)
  But same correlationId (links them to original payment)
```

## Dashboard Interpretation

### Grafana Payment Platform Dashboard

**Panel: Request Rate (Time Series)**
- Shows: HTTP requests per second across all services
- High spike: Payment surge (marketing campaign, payday)
- Sudden drop: Service issue or network problem
- Line per service: Track which service is bottleneck

**Panel: P95 Latency (Gauge)**
- Shows: 95th percentile response time
- Green (<500ms): Healthy
- Yellow (500ms-1s): Slow, investigate
- Red (>1s): Critical, user-facing impact

**Panel: Error Rate (Time Series)**
- Shows: 5xx server errors per second
- Any spike: Check Prometheus alerts + Kibana logs
- Investigate: service-specific errors, circuit breakers

**Panel: Request Rate by Service (Stacked Area)**
- Shows: Traffic distribution across services
- High account-service: Database load?
- High fraud-service: Performance regression?
- High notification-service: Spam event surge?

### Prometheus Alert Firing

Example alert firing (in Grafana Alerts):

```
Alert: HighErrorRate
Status: FIRING
Severity: WARNING
Triggered: 13:15:30 (5 minutes ago)
Message: Error rate is 8.5% on payment-service

Affected metric:
  rate(http_server_requests_seconds_count{status=~"5..",job="payment-service"}[5m])
  Value: 0.085 (8.5% of requests are errors)
  Threshold: 0.05 (5%)

Next steps:
1. Check Kibana: logs with level=ERROR + service=payment-service
2. Check Jaeger: traces with status=ERROR
3. Check Grafana: other correlated spikes (latency, CPU, DB connections)
```

## Troubleshooting with Observability Tools

### Scenario: "Payment requests are slow"

**Step 1: Grafana Dashboard**
```
Check P95 Latency panel
If 1.5s average but usually 250ms:
  → Alert: HighLatency should be firing
  → Check which service in "Request Rate by Service" has spike
```

**Step 2: Prometheus Queries (Grafana -> Explore)**
```
Query: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="payment-service"}[5m]))
Result: 0.800s

Query: tomcat_jdbc_connections_active{pool="payment-service"}
Result: 9 (out of 10) – connection pool exhausted!

Query: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="account-service"}[5m]))
Result: 0.600s – account service is slow (database issue?)
```

**Step 3: Kibana Logs**
```
Query: service:payment-service AND level:WARN
Results:
  13:15:30 [ABC123] Feign call to account-service timed out
  13:15:31 [ABC456] Feign call to account-service timed out
  13:15:32 [ABC789] Circuit breaker opened for account-service

Query: service:account-service AND duration_ms:>1000
Results:
  13:15:20 [DEF111] SELECT query took 950ms (index missing?)
  13:15:21 [DEF222] SELECT query took 850ms
  
→ Diagnosis: Missing database index on account_id in balance lookup
```

**Step 4: Jaeger Trace**
```
Open trace ABC123 (slow payment request)
Spans:
  - api-gateway: 50ms
  - payment-service:
    - payment.process: 750ms ← slow
    - account.debit: 600ms ← very slow (parent: payment-service call)
  - account-service:
    - database.select: 595ms ← ROOT CAUSE
    
Recommendation: Add index on (account_id, status) on accounts table
```

**Step 5: Action**
```
CREATE INDEX idx_accounts_lookup ON accounts(account_id, status);
```

Then monitor:
```
Grafana: P95 latency should drop back to 250ms
Kibana: No more "query took >500ms" logs
Prometheus: Circuit breaker alert should clear
Jaeger: New traces show account.debit < 100ms
```

This is the power of integrated observability: metrics show there's a problem, logs pinpoint which service, traces show which operation, Prometheus confirms timing, database query plan explains why.
