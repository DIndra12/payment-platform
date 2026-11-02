# 🚀 E2E Testing Quick Start - Run NOW!

**Time to complete**: 30 minutes  
**Status**: All tools ready, just execute!

---

## 📋 Pre-Flight Checklist

Before you start, verify you have:
- [ ] Docker running (`docker ps` should work)
- [ ] Java 21 installed (`java -version`)
- [ ] Maven works (`mvn -v`)
- [ ] Postman installed (download from https://www.postman.com/downloads/)
- [ ] You're in the project root: `C:\coding\payment-platform`

**If any of these fail**: See README.md Prerequisites section

---

## ⚡ QUICK START (5 MINUTES TO FIRST TEST)

### **Step 1: Start Everything (Windows)**

Open **PowerShell** in the project root:

```powershell
# Navigate to project
cd C:\coding\payment-platform

# Start the system (this runs Docker + builds services)
.\start-all.bat
```

**You'll see output like:**
```
[OK] Docker is running
[OK] Docker infrastructure started
[*] Building services...
[OK] All services built successfully
[*] Starting services...
   Starting: account-service (8081)
   Starting: fraud-service (8082)
   Starting: payment-service (8083)
   ...
[*] Waiting for services to start (60 seconds)...
```

⏳ **Wait 60 seconds for services to stabilize**

### **Step 2: Import Postman Collection**

While waiting, open **Postman**:

1. **File** → **Import**
2. Select: `postman-collection.json` (in project root)
3. Click **Import**

You should see folders:
- Setup & Variables
- 1. Payment Service
- 2. Account Service
- 3. Fraud Service
- 4. Transaction History Service
- 5. Failure Scenarios

### **Step 3: Run First Test (Setup)**

In Postman:

1. Click folder: **Setup & Variables**
2. Click request: **Get test accounts (Setup)**
3. Click **Send** (blue button top right)

**Expected Response:**
```json
{
  "status": "UP"
}
```

**Console shows:**
```
✅ Test environment variables set successfully
Payer Account: 00000000-0000-0000-0000-000000000001
Payee Account: 00000000-0000-0000-0000-000000000002
```

✅ **If you see this, system is running!**

---

## 🧪 CORE TESTS (Happy Path) — 10 MINUTES

### **Test 1: Create a Payment**

In Postman, go to **1. Payment Service**:

1. Click: **Create Payment - Valid (Happy Path)**
2. Click **Send**

**Expected Response (201):**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "INITIATED",
  "createdAt": "2026-11-02T10:30:45Z"
}
```

**⭐ COPY the paymentId** (you'll need it next)

### **Test 2: Query Payment Status**

1. Click: **Get Payment Status**
2. Click **Send**

**Expected Response:**
```json
{
  "paymentId": "a1b2c3d4-...",
  "status": "COMPLETED",
  "amount": 100.00,
  "currency": "INR",
  "payerAccountId": "00000000-0000-0000-0000-000000000001",
  "payeeAccountId": "00000000-0000-0000-0000-000000000002",
  "createdAt": "2026-11-02T10:30:45Z",
  "updatedAt": "2026-11-02T10:30:47Z"
}
```

✅ **Status changed from INITIATED to COMPLETED!**

### **Test 3: Check Account Balance**

In Postman, go to **2. Account Service**:

1. Click: **Get Account Balance**
2. Click **Send**

**Expected Response:**
```json
{
  "accountId": "00000000-0000-0000-0000-000000000001",
  "balance": 9900.00,
  "currency": "INR"
}
```

✅ **Balance decreased by $100 (the payment amount)!**

### **Test 4: Check Transaction History**

In Postman, go to **4. Transaction History Service**:

1. Click: **Get Account Transaction History**
2. Click **Send**

**Expected Response:**
```json
{
  "accountId": "00000000-0000-0000-0000-000000000001",
  "totalTransactions": 1,
  "transactions": [
    {
      "paymentId": "a1b2c3d4-...",
      "direction": "DEBIT",
      "amount": 100.00,
      "currency": "INR",
      "status": "COMPLETED",
      "debitedAt": "2026-11-02T10:30:46",
      "completedAt": "2026-11-02T10:30:47"
    }
  ]
}
```

✅ **Complete payment flow verified!**

### **Test 5: Test Fraud Rejection**

In Postman, go to **3. Fraud Service**:

1. Click: **Evaluate Risk - High Amount (Should REJECT)**
2. Click **Send**

**Expected Response (200):**
```json
{
  "riskScore": 85,
  "decision": "REJECT",
  "reasons": [
    "Amount exceeds maximum transaction limit of 10000"
  ]
}
```

✅ **Fraud service correctly rejecting high-risk transactions!**

---

## 📊 OBSERVABILITY (Optional but Cool!) — 5 MINUTES

Once the system is running, check real-time dashboards:

### **Grafana (Metrics & Dashboards)**
- URL: http://localhost:3000
- Login: `admin` / `admin`
- Shows: Response times, throughput, error rates

### **Kibana (Logs)**
- URL: http://localhost:5601
- Shows: Structured logs from all services
- Search logs by traceId

### **Prometheus (Raw Metrics)**
- URL: http://localhost:9090
- Shows: System metrics (CPU, JVM, HTTP requests)

---

## ✅ COMPLETE TEST CHECKLIST

Run these tests in order and check them off:

### Setup
- [ ] Step 1: System started with `start-all.bat`
- [ ] Step 2: Postman collection imported
- [ ] Step 3: "Setup & Variables" test passed

### Happy Path
- [ ] Test 1: Payment created successfully
- [ ] Test 2: Payment status is COMPLETED
- [ ] Test 3: Account balance decreased
- [ ] Test 4: Transaction in history
- [ ] Test 5: Fraud rejection works

### All Passing? ✅ **SYSTEM IS WORKING!**

---

## 🐛 TROUBLESHOOTING QUICK FIXES

### "Connection refused on port 8083"
```powershell
# Services might still starting. Wait 30 more seconds, then retry
# Or check logs:
docker-compose logs payment-service
```

### "Postman can't find collection"
```
Make sure you're in the right directory and selected postman-collection.json
(NOT postman-collection-gateway.json unless using API Gateway)
```

### "401 Unauthorized"
```
Collections sometimes need JWT token refresh.
In Postman: Setup & Variables → Generate JWT Token (if available)
Most tests work without it.
```

### "Services not starting"
```powershell
# Check if Docker has enough resources
docker stats

# Rebuild from scratch
docker-compose down -v
docker-compose up -d
mvn clean build
```

### "Port 5432 already in use"
```powershell
# Stop existing PostgreSQL or containers
docker ps
docker stop <container_id>
```

---

## 📖 NEXT: Detailed Testing

Once you pass the checklist above, read:

**`docs/04-E2E-TESTING-MASTER.md`** — Complete E2E workflow with:
- All test groups (not just happy path)
- Failure scenarios (network timeout, fraud detection)
- Performance validation
- Data integrity checks
- Observability verification

**`docs/03-E2E-TESTING-VERIFICATION.md`** — Step-by-step procedures with:
- Health checks for all 13 containers
- Detailed error scenarios
- 30+ item verification checklist

---

## 🛑 STOPPING THE SYSTEM

When done testing:

```powershell
# Stop all services
.\stop-all.bat

# Or via Docker
docker-compose down

# Keep data (databases) - run again later without rebuild
docker-compose stop

# Delete everything and start fresh next time
docker-compose down -v
```

---

## 🎯 You're Ready!

**Go to Step 1 above and start testing.**

**Questions while testing?** Check the **Troubleshooting** section above first, then see `docs/00-QUICKSTART.md` or `docs/04-E2E-TESTING-MASTER.md` Part 4.

---

**Happy Testing! 🚀**
