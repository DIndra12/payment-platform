# Postman Collection Guide - Payment Platform

Complete guide for testing the Payment Platform microservices using Postman.

---

## Quick Start (2 minutes)

### 1. Install Postman
- Download from [postman.com](https://www.postman.com/downloads/)
- Or use Postman Web: [web.postman.co](https://web.postman.co)

### 2. Import Collection
1. Open Postman
2. Click **File** → **Import**
3. Select `postman-collection.json` from the project root
4. Click **Import**

### 3. Start Services
```bash
# Terminal 1 - Account Service
cd account-service && ./mvnw spring-boot:run

# Terminal 2 - Fraud Service
cd fraud-service && ./mvnw spring-boot:run

# Terminal 3 - Payment Service
cd payment-service && ./mvnw spring-boot:run

# Terminal 4 - Notification Service
cd notification-service && ./mvnw spring-boot:run

# Terminal 5 - Transaction History Service
cd transaction-history-service && ./mvnw spring-boot:run
```

### 4. Run First Request
1. In Postman, go to **Setup & Variables** → **Get test accounts (Setup)**
2. Click **Send**
3. ✅ All collection variables are now configured

---

## Collection Structure

```
Payment Platform
├── Setup & Variables
│   └── Get test accounts (Setup) - Initialize test data
├── 1. Payment Service (Port 8083)
│   ├── Health Check
│   ├── Create Payment - Valid (Happy Path)
│   ├── Create Payment - Negative Amount (Validation Error)
│   ├── Create Payment - Invalid Currency (Validation Error)
│   ├── Create Payment - Idempotency Test (Same Key)
│   └── Get Payment Status
├── 2. Account Service (Port 8081)
│   ├── Health Check
│   ├── Get Account Balance
│   ├── Get Account Ledger
│   ├── Debit Account - Valid
│   ├── Debit Account - Negative Amount (Validation Error)
│   └── Credit Account - Valid
├── 3. Fraud Service (Port 8082)
│   ├── Health Check
│   ├── Evaluate Risk - Low Amount (Should APPROVE)
│   ├── Evaluate Risk - High Amount (Should REJECT)
│   └── Evaluate Risk - Invalid Currency (Validation Error)
├── 4. Transaction History Service (Port 8085)
│   ├── Health Check
│   ├── Get Account Transaction History
│   ├── Get Account Transaction History - With Filters
│   ├── Get Single Transaction by Payment ID
│   └── Get Account Transaction Summary
├── 5. Notification Service (Port 8084)
│   ├── Health Check
│   └── Info - Notification Service (Read-Only)
└── End-to-End Workflows
    └── Complete Payment Flow
        ├── Step 1 - Create Payment
        ├── Step 2 - Check Payment Status (Wait 2s)
        ├── Step 3 - Check Transaction History
        └── Step 4 - Verify Account Balances
```

---

## Available Test Accounts

| Account ID | Purpose | Default Balance |
|---|---|---|
| `00000000-0000-0000-0000-000000000001` | Payer (Account to debit from) | Variable* |
| `00000000-0000-0000-0000-000000000002` | Payee (Account to credit to) | Variable* |

*Balances are managed by the Account Service. Use `/api/v1/accounts/{accountId}/balance` to check current balance.

---

## How to Use

### Running Single Requests

1. **Navigate** to the request folder in the left panel
2. **Click** the request name (e.g., "Create Payment - Valid")
3. **Review** the request details (URL, headers, body)
4. **Click** the **Send** button
5. **Check** the response in the right panel

### Collection Variables

Variables are automatically set when you run "Setup & Variables" → "Get test accounts (Setup)":

```
{{payer_account_id}}                   = 00000000-0000-0000-0000-000000000001
{{payee_account_id}}                   = 00000000-0000-0000-0000-000000000002
{{payment_id}}                         = (Auto-populated after creating payment)
{{payment_service_url}}                = http://localhost:8083
{{account_service_url}}                = http://localhost:8081
{{fraud_service_url}}                  = http://localhost:8082
{{notification_service_url}}           = http://localhost:8084
{{transaction_history_service_url}}    = http://localhost:8085
{{idempotency_key}}                    = (Auto-generated for each request)
{{e2e_payment_id}}                     = (Auto-populated during E2E workflow)
```

### Changing Variables

1. Click the **Collection** name in the left panel
2. Go to the **Variables** tab
3. Edit any variable value
4. Variables are scoped to this collection only

### Environment Variables (Optional)

If you have multiple environments (dev, staging, prod):

1. Click **Environments** in the top-right
2. Click **Create New Environment**
3. Name it (e.g., "Production")
4. Set URLs for that environment:
   ```
   payment_service_url        = https://api.prod.example.com/payment
   account_service_url        = https://api.prod.example.com/account
   ...
   ```
5. Switch environments using the dropdown

---

## Test Scenarios

### Scenario 1: Happy Path Payment (Valid Request)

1. **Run:** "Setup & Variables" → "Get test accounts (Setup)"
2. **Run:** "Payment Service" → "Create Payment - Valid (Happy Path)"
   - ✅ Response: 202 Accepted with payment ID
   - Payment ID is automatically saved to `{{payment_id}}`
3. **Run:** "Payment Service" → "Get Payment Status"
   - ✅ Verify payment status (should be INITIATED or later)

### Scenario 2: Validation Errors

Test that invalid requests are rejected at the validation layer:

1. **Run:** "Payment Service" → "Create Payment - Negative Amount (Validation Error)"
   - ❌ Response: 400 Bad Request with validation error
   - Check error message: "Amount must be at least 0.01"

2. **Run:** "Fraud Service" → "Evaluate Risk - Invalid Currency (Validation Error)"
   - ❌ Response: 400 Bad Request with validation error
   - Check error message: "Currency must be a valid ISO 4217 code"

### Scenario 3: Idempotency

Test that the same payment request with the same Idempotency-Key returns the same response:

1. **Run:** "Payment Service" → "Create Payment - Idempotency Test (Same Key)"
   - ✅ Response: 202 Accepted (first time)
   - Payment ID is saved

2. **Run:** "Payment Service" → "Create Payment - Idempotency Test (Same Key)" again
   - ✅ Response: 202 Accepted (same payment ID as before)
   - Verifies request was not processed twice

### Scenario 4: Risk Evaluation

1. **Run:** "Fraud Service" → "Evaluate Risk - Low Amount (Should APPROVE)"
   - ✅ Response: 200 OK with decision = "APPROVE"

2. **Run:** "Fraud Service" → "Evaluate Risk - High Amount (Should REJECT)"
   - ❌ Response: 200 OK with decision = "REJECT"
   - Verify reason: "Amount exceeds maximum transaction limit"

### Scenario 5: Account Operations

1. **Run:** "Account Service" → "Get Account Balance"
   - ✅ See current balance for payer account

2. **Run:** "Account Service" → "Debit Account - Valid"
   - ✅ Debit operation creates ledger entry

3. **Run:** "Account Service" → "Credit Account - Valid"
   - ✅ Credit operation creates ledger entry

4. **Run:** "Account Service" → "Get Account Ledger (Transaction History)"
   - ✅ Verify both DEBIT and CREDIT entries are recorded

### Scenario 6: Complete Payment Flow (End-to-End)

Run all steps in sequence to see a complete payment journey:

1. **Open:** "End-to-End Workflows" → "Complete Payment Flow"
2. **Run:** Step 1 - Create Payment
   - ✅ Payment created, ID saved
3. **Run:** Step 2 - Check Payment Status
   - ✅ Status updated (with 2-second delay for async processing)
4. **Run:** Step 3 - Check Transaction History
   - ✅ Payment appears in transaction history (CQRS read model)
5. **Run:** Step 4 - Verify Account Balances
   - ✅ Payer balance decreased, payee balance increased

---

## Understanding Test Results

### Green ✅ (Test Passed)

```
Status: 200 OK
Test Results:
✓ Status is 200 OK
✓ Balance response has required fields
```

**Meaning:** Request succeeded and response meets expectations.

### Red ❌ (Test Failed)

```
Status: 400 Bad Request
Test Results:
✗ Status is 400 Bad Request
  Expected: 400
  Actual: 422
```

**Meaning:** Response doesn't match test expectations. Check error details.

### Response Body (Expected vs Actual)

**Successful Payment:**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "INITIATED",
  "createdAt": "2026-09-20T22:50:45Z"
}
```

**Validation Error:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid JSON or validation failed: Amount must be at least 0.01",
  "instance": "/api/v1/payments"
}
```

---

## Common Issues & Troubleshooting

### Issue 1: "Connection refused" (localhost:8083)

**Cause:** Payment Service is not running

**Solution:**
```bash
cd payment-service
./mvnw spring-boot:run
```

Verify: Open browser → `http://localhost:8083/actuator/health` → Should show `{"status":"UP"}`

### Issue 2: "Timeout" after 30 seconds

**Cause:** Service is slow or not responding

**Solution:**
1. Check if all 5 services are running (see "Start Services" above)
2. Check service logs for errors
3. Increase timeout in Postman:
   - Click **Settings** → **General** → **Request timeout (ms)** → Set to 10000

### Issue 3: "Idempotency-Key header missing" error

**Cause:** Header not included in request

**Solution:**
- The collection automatically adds this header
- If you manually edit a request, ensure the header is present:
  ```
  Key: Idempotency-Key
  Value: {{idempotency_key}}
  ```

### Issue 4: Variable is empty (shows as blank)

**Cause:** Variable wasn't set

**Solution:**
1. Run **Setup & Variables** → **Get test accounts (Setup)** first
2. Check the response in the **Tests** tab for success
3. Then variables will be available for other requests

### Issue 5: "Account not found" (404)

**Cause:** Using wrong account ID or account doesn't exist

**Solution:**
- Use the pre-defined account IDs from the collection
- Or create new test accounts by making requests (they're auto-created)

---

## Performance Testing

### Light Load (Manual)

Test a few requests to verify everything works:

1. Create 1 payment
2. Check status
3. Get transaction history

### Moderate Load (Postman Runner)

Run multiple requests in sequence:

1. Select multiple requests
2. Click **Runner** (or **Collection Runner** if Postman version < 9)
3. Set iterations: 5-10
4. Click **Run**
5. Check results: success rate, average response time

### Heavy Load (External Tool)

For stress testing, use **Apache JMeter** or **Gatling**:

```bash
# Example: Create 100 payments concurrently
jmeter -n -t payment-load-test.jmx -l results.jtl -e -o report/
```

---

## Advanced Features

### Pre-Request Scripts

Generate dynamic data before each request:

Example: Auto-generate unique idempotency key
```javascript
var timestamp = new Date().getTime();
var randomString = Math.random().toString(36).substring(7);
var idempotencyKey = 'payment-' + timestamp + '-' + randomString;
pm.environment.set('idempotency_key', idempotencyKey);
```

This is already configured in the collection for payment requests.

### Test Scripts

Validate responses after each request:

Example: Check payment status is valid
```javascript
pm.test('Payment status is valid', function() {
    var jsonData = pm.response.json();
    var validStatuses = ['INITIATED', 'FRAUD_CHECK', 'DEBITED', 'COMPLETED', 'FAILED'];
    pm.expect(validStatuses).to.include(jsonData.status);
});
```

The collection has these pre-configured for each request.

---

## Exporting Results

### Export Test Results

1. Run a collection (via Runner)
2. After completion, click **Export Results** (top-right)
3. Choose format: **JSON** or **CSV**
4. Save file for reporting

### Export Requests as cURL

1. Right-click a request
2. Select **Copy as cURL**
3. Paste into terminal or documentation

Example:
```bash
curl -X POST \
  http://localhost:8083/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: payment-12345' \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "payeeAccountId": "00000000-0000-0000-0000-000000000002",
    "amount": 100.00,
    "currency": "USD"
  }'
```

---

## API Documentation

### Payment Service

**Create Payment**
- **Method:** POST
- **Endpoint:** `/api/v1/payments`
- **Headers:** `Idempotency-Key: <unique-key>`
- **Body:**
  ```json
  {
    "payerAccountId": "uuid",
    "payeeAccountId": "uuid",
    "amount": 100.00,
    "currency": "USD"
  }
  ```
- **Response:** 202 Accepted with payment ID
- **Validation:** Amount >= 0.01, currency = 3 uppercase letters

**Get Payment Status**
- **Method:** GET
- **Endpoint:** `/api/v1/payments/{paymentId}`
- **Response:** 200 OK with payment details

### Account Service

**Get Balance**
- **Method:** GET
- **Endpoint:** `/api/v1/accounts/{accountId}/balance`
- **Response:** 200 OK with balance

**Get Ledger**
- **Method:** GET
- **Endpoint:** `/api/v1/accounts/{accountId}/ledger`
- **Response:** 200 OK with transaction entries

**Debit**
- **Method:** POST
- **Endpoint:** `/api/v1/accounts/{accountId}/debit`
- **Body:**
  ```json
  {
    "amount": 50.00,
    "referenceId": "uuid"
  }
  ```
- **Validation:** Amount > 0

**Credit**
- **Method:** POST
- **Endpoint:** `/api/v1/accounts/{accountId}/credit`
- **Body:**
  ```json
  {
    "amount": 75.00,
    "referenceId": "uuid"
  }
  ```
- **Validation:** Amount > 0

### Fraud Service

**Evaluate Risk**
- **Method:** POST
- **Endpoint:** `/api/v1/risk/evaluate`
- **Body:**
  ```json
  {
    "payerAccountId": "uuid",
    "amount": 500.00,
    "currency": "USD"
  }
  ```
- **Response:** 200 OK with riskScore and decision (APPROVE/REJECT)

### Transaction History Service

**Get Account History**
- **Method:** GET
- **Endpoint:** `/api/v1/transactions/account/{accountId}`
- **Query Params:** `status`, `direction`, `from`, `to`, `page`, `size`
- **Response:** 200 OK with paginated transactions

**Get Transaction Summary**
- **Method:** GET
- **Endpoint:** `/api/v1/transactions/account/{accountId}/summary`
- **Response:** 200 OK with aggregated transaction data

---

## Tips & Best Practices

1. **Always run Setup first** - Initialize variables before other requests
2. **Use unique Idempotency-Keys** - Prevents duplicate payments
3. **Wait between requests** - Async processing takes ~1-2 seconds
4. **Check the "Tests" tab** - See what assertions passed/failed
5. **Use the Console** - `Ctrl+Alt+C` (Cmd+Option+C on Mac) to debug
6. **Save responses** - Click **Save Response** to compare future changes
7. **Test error cases** - Not just the happy path
8. **Monitor logs** - Keep service terminal windows visible for errors

---

## Next Steps

- ✅ Run the collection to test all endpoints
- ✅ Modify requests to test different scenarios
- ✅ Create new requests based on API documentation
- ✅ Share collection with team (`postman-collection.json`)
- ✅ Use for regression testing before deployments

---

## Questions or Issues?

Refer to:
- README.md - Project setup and architecture
- PRODUCTION-READINESS.md - System design and gaps
- payments-microservices-design.md - Detailed architecture
- Service-specific logs (check terminal windows)

---

**Last Updated:** 2026-09-20  
**Collection Version:** 1.0  
**Platform Version:** 0.0.1-SNAPSHOT
