# ⚡ RUN E2E TESTS NOW! Complete Walkthrough

**Status**: ✅ All tools ready, just execute the steps below
**Time**: 30-45 minutes
**What you'll do**: Start system → Import Postman → Run tests → Verify results

---

## 📦 What You Already Have

✅ **Start/Stop Scripts**
- `start-all.bat` (Windows)
- `start-all.sh` (Linux/Mac)

✅ **Postman Collections**
- `postman-collection.json` (Main API tests)
- `postman-collection-gateway.json` (With API Gateway)

✅ **Docker Compose** (13 containers)
- PostgreSQL (Database)
- Kafka (Events)
- Keycloak (Auth)
- Elasticsearch (Logs)
- Kibana (Log Dashboard)
- Prometheus (Metrics)
- Grafana (Metrics Dashboard)

✅ **Microservices** (6 services)
- Account Service (8081)
- Fraud Service (8082)
- Payment Service (8083)
- Notification Service (8084)
- Transaction History Service (8085)
- API Gateway Service (8080)

✅ **Documentation**
- `docs/04-E2E-TESTING-MASTER.md` (Complete guide)
- `docs/03-E2E-TESTING-VERIFICATION.md` (Detailed procedures)
- `E2E_TESTING_QUICKSTART.md` (Quick reference - NEW!)

---

## 🚀 STEP-BY-STEP: Start System

### **1️⃣ Prerequisites Check (1 minute)**

Open PowerShell and verify:

```powershell
# Check Docker is running
docker ps

# Check Java
java -version
# Should show: openjdk version "21"

# Check Maven
mvn -version
```

**✅ If all 3 work**, proceed. **❌ If any fail**, see Troubleshooting below.

### **2️⃣ Start Everything (Windows)**

```powershell
# Navigate to project root
cd C:\coding\payment-platform

# Run start script
.\start-all.bat
```

**What happens:**
1. ✅ Checks Docker is running
2. ✅ Starts Docker Compose (7 infrastructure containers)
3. ✅ Builds all microservices
4. ✅ Starts 6 Java services
5. ✅ Waits 60 seconds for stability

**Expected Output:**
```
[OK] Docker is running
[OK] Docker infrastructure started
[*] Building services...
[OK] All services built successfully
[*] Starting services...
   Starting: account-service (8081)
   Starting: fraud-service (8082)
   Starting: payment-service (8083)
   Starting: notification-service (8084)
   Starting: transaction-history-service (8085)
   Starting: api-gateway-service (8080)

[*] Waiting for services to start (60 seconds)...

Setup Complete!
All services should now be running:
   [OK] account-service:               http://localhost:8081
   [OK] fraud-service:                 http://localhost:8082
   [OK] payment-service:               http://localhost:8083
   [OK] notification-service:          http://localhost:8084
   [OK] transaction-history-service:   http://localhost:8085
   [OK] api-gateway-service:           http://localhost:8080

Next Steps:
1. Import Postman Collection: postman-collection-gateway.json
2. Run "Setup & Variables" > "Generate JWT Token"
3. Test any API with the token
```

**⏳ While waiting (60 seconds), do Step 3 below...**

---

## 🔗 STEP-BY-STEP: Import Postman Collection

### **3️⃣ Open Postman (While system is starting)**

1. Open **Postman** (download from https://www.postman.com/downloads/ if needed)
2. Click **File** → **Import**
3. Browse to: `C:\coding\payment-platform\postman-collection.json`
4. Click **Import**

**You should see these test folders:**
```
📁 Setup & Variables
   ├─ Get test accounts (Setup)
   └─ Generate JWT Token (optional)

📁 1. Payment Service (Port 8083)
   ├─ Health Check
   ├─ Create Payment - Valid (Happy Path)
   ├─ Create Payment - Fraud Reject
   └─ Get Payment Status

📁 2. Account Service (Port 8081)
   ├─ Health Check
   ├─ Get Account Balance
   ├─ Get Account Ledger
   └─ Debit Account

📁 3. Fraud Service (Port 8082)
   ├─ Health Check
   ├─ Evaluate Risk - Low Amount (APPROVE)
   └─ Evaluate Risk - High Amount (REJECT)

📁 4. Transaction History Service (Port 8085)
   ├─ Health Check
   ├─ Get Account Transaction History
   └─ Get Single Transaction

📁 5. Failure Scenarios (Advanced)
   ├─ Idempotency Test
   ├─ Network Timeout Simulation
   └─ Saga Failure Handling
```

---

## 🧪 STEP-BY-STEP: Run Tests

### **4️⃣ Setup (Initialize Variables)**

1. In Postman, click folder: **Setup & Variables**
2. Click request: **Get test accounts (Setup)**
3. Click **Send** (blue button)

**Expected Response:**
```
Status: 200 OK

Response shows in console:
✅ Test environment variables set successfully
Payer Account: 11111111-1111-1111-1111-111111111111
Payee Account: 22222222-2222-2222-2222-222222222222
```

✅ **Variables are now set for all future tests**

---

### **5️⃣ Test Happy Path (Success Scenario)**

#### **Test 5.1: Create Payment**

1. Go to folder: **1. Payment Service (Port 8083)**
2. Click request: **Create Payment - Valid (Happy Path)**
3. Click **Send**

**Expected Response (200 OK):**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "INITIATED",
  "createdAt": "2026-11-02T10:30:45Z"
}
```

**⭐ SAVE the paymentId** (you'll use it next)

#### **Test 5.2: Get Payment Status**

1. Same folder: **1. Payment Service**
2. Click request: **Get Payment Status**
3. Click **Send**

**Expected Response (200 OK):**
```json
{
  "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "status": "COMPLETED",  ← Changed from INITIATED!
  "amount": 100.00,
  "currency": "INR",
  "payerAccountId": "11111111-1111-1111-1111-111111111111",
  "payeeAccountId": "22222222-2222-2222-2222-222222222222",
  "createdAt": "2026-11-02T10:30:45Z",
  "updatedAt": "2026-11-02T10:30:47Z"
}
```

✅ **Status changed from INITIATED → COMPLETED (Saga worked!)**

#### **Test 5.3: Check Account Balance**

1. Go to folder: **2. Account Service (Port 8081)**
2. Click request: **Get Account Balance**
3. Click **Send**

**Expected Response (200 OK):**
```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "balance": 9900.00,  ← Decreased by 100 (payment amount)!
  "currency": "INR"
}
```

✅ **Balance decreased (Debit worked!)**

#### **Test 5.4: Check Transaction History**

1. Go to folder: **4. Transaction History Service (Port 8085)**
2. Click request: **Get Account Transaction History**
3. Click **Send**

**Expected Response (200 OK):**
```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "totalTransactions": 1,
  "transactions": [
    {
      "paymentId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
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

✅ **Transaction appears in CQRS read model (Event projection worked!)**

---

### **6️⃣ Test Failure Scenarios**

#### **Test 6.1: Fraud Rejection**

1. Go to folder: **3. Fraud Service (Port 8082)**
2. Click request: **Evaluate Risk - High Amount (Should REJECT)**
3. Click **Send**

**Expected Response (200 OK):**
```json
{
  "riskScore": 85,
  "decision": "REJECT",
  "reasons": ["Amount exceeds maximum transaction limit of 10000"]
}
```

✅ **Fraud detection working (High amounts rejected)**

#### **Test 6.2: Idempotency Test**

1. Go to folder: **5. Failure Scenarios**
2. Click request: **Idempotency Test**
3. Click **Send** (first time)
4. Click **Send** (second time with SAME Idempotency-Key)

**Expected**: Both responses have same `paymentId` (no duplicate payment created)

✅ **Idempotency working (Safe retries)**

---

## 📊 STEP-BY-STEP: View Observability

### **7️⃣ Check Real-Time Dashboards (Optional but Cool!)**

While tests are running, open these in browser:

#### **Grafana (Metrics)**
```
URL: http://localhost:3000
Login: admin / admin
```
**What to look for:**
- Request latency
- Throughput
- Error rates
- System health

#### **Kibana (Logs)**
```
URL: http://localhost:5601
```
**What to look for:**
- Structured logs from all services
- Search by service name or traceId
- Payment events

#### **Prometheus (Raw Metrics)**
```
URL: http://localhost:9090
```
**What to look for:**
- HTTP request count
- Response time percentiles
- System metrics (JVM, CPU)

---

## ✅ COMPLETE VERIFICATION CHECKLIST

Track your progress:

### Setup
- [ ] Docker is running
- [ ] `start-all.bat` completed successfully
- [ ] All 6 services are UP
- [ ] All 7 infrastructure containers are UP

### Postman Import
- [ ] Collection imported
- [ ] Test folders visible

### Variables Setup
- [ ] "Get test accounts" passed
- [ ] Payer/Payee account IDs set

### Happy Path Tests
- [ ] Create Payment returned 200 OK
- [ ] Payment status changed to COMPLETED
- [ ] Account balance decreased
- [ ] Transaction appears in history

### Failure Scenarios
- [ ] Fraud rejection works (high amount)
- [ ] Idempotency test passed (no duplicates)

### Dashboards (Optional)
- [ ] Grafana loads and shows metrics
- [ ] Kibana loads and shows logs
- [ ] Prometheus shows metrics

**All checked? ✅ SYSTEM IS WORKING PERFECTLY!**

---

## 🛑 Stop the System (When Done)

```powershell
# Method 1: Keep databases (restart without rebuild)
docker-compose stop

# Method 2: Stop and remove containers (clean shutdown)
docker-compose down

# Method 3: Nuke everything (start fresh next time)
docker-compose down -v  # -v removes volumes too
```

**To restart later:**
```powershell
# Just run again
.\start-all.bat
```

---

## 🐛 Common Issues & Quick Fixes

### ❌ "Connection refused on port 8083"
**Solution**: Wait another 30 seconds, services might still starting
```powershell
# Check status
docker-compose ps
# Or view logs
docker-compose logs payment-service
```

### ❌ "Postman can't find postman-collection.json"
**Solution**: Make sure you're importing from project root
```
File → Import → Browse to: C:\coding\payment-platform\postman-collection.json
```

### ❌ "Port 5432 already in use"
**Solution**: Something else is using the port
```powershell
# Stop other PostgreSQL
docker ps
docker stop <container_id>

# Or change port in docker-compose.yml
```

### ❌ Services won't start
**Solution**: Rebuild from scratch
```powershell
docker-compose down -v
.\start-all.bat
```

### ❌ "Docker is not running"
**Solution**: Start Docker Desktop, wait 30 seconds, then retry
```powershell
# Windows: Start Docker Desktop from Start menu
# Then retry start-all.bat
```

---

## 📚 Next Level Tests

Once basic tests pass, try:

### **Read the Master Guide**
```
docs/04-E2E-TESTING-MASTER.md
```
- Advanced test scenarios
- Performance validation
- Observability verification
- 30+ item checklist

### **Detailed Procedures**
```
docs/03-E2E-TESTING-VERIFICATION.md
```
- Health checks for all 13 containers
- Step-by-step troubleshooting
- Expected outputs for every test
- Data integrity verification

### **Architecture Deep Dive**
```
docs/01-ARCHITECTURE-OVERVIEW.md
```
- Saga pattern explanation
- Event-driven design
- Resilience patterns
- Phase roadmap

---

## 🎯 SUCCESS CRITERIA

You'll know everything works when:

✅ All 6 services are UP and healthy  
✅ Create Payment returns 200 OK  
✅ Payment status changes to COMPLETED  
✅ Account balance decreases  
✅ Transaction appears in history  
✅ Fraud detection rejects high amounts  
✅ Same payment (idempotent) returns same ID  

**If all 7 pass, your system is working!** 🎉

---

## 🚀 Ready?

**Just run:**
```powershell
cd C:\coding\payment-platform
.\start-all.bat
```

**Then follow the steps above starting from Step 3 (Open Postman).**

**All tests pass? → Congratulations! Move to Phase 3 (Kubernetes) or continue with advanced testing in docs/04-E2E-TESTING-MASTER.md**

---

**Happy Testing! 🧪**
