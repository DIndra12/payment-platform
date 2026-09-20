# 04 - Setup & Run

## ⚡ Quick Start (3 Minutes)

```bash
cd C:\coding\payment-platform
start-all.bat
```

Wait ~60 seconds. All 7 services start automatically:
- API Gateway (8080)
- Account Service (8081)
- Fraud Service (8082)
- Payment Service (8083)
- Notification Service (8084)
- Transaction History (8085)
- Plus: PostgreSQL, Kafka, Keycloak

---

## 📋 Prerequisites

```bash
# Check Java 21
java -version
# Should show: openjdk 21.x.x

# Check Docker
docker --version
docker-compose --version

# Check Git
git --version
```

**If missing:**
- Java 21: https://adoptium.net
- Docker: https://www.docker.com/products/docker-desktop
- Git: https://git-scm.com

---

## 🚀 Starting Services

### Windows: start-all.bat
```bash
cd C:\coding\payment-platform
start-all.bat
```

**What it does:**
1. Checks Docker running
2. Starts docker-compose (PostgreSQL, Kafka, Keycloak)
3. Builds all 7 services with Maven
4. Starts each service in new window
5. Waits 60 seconds
6. Displays summary

### macOS/Linux: start-all.sh
```bash
cd /path/to/payment-platform
chmod +x start-all.sh
./start-all.sh
```

---

## ✅ Verify Services Running

```bash
# All should return {"status":"UP"}

curl http://localhost:8080/actuator/health     # Gateway
curl http://localhost:8081/actuator/health     # Account
curl http://localhost:8082/actuator/health     # Fraud
curl http://localhost:8083/actuator/health     # Payment
curl http://localhost:8084/actuator/health     # Notification
curl http://localhost:8085/actuator/health     # Transaction
```

---

## 🧪 Test with Postman

1. Open Postman
2. File > Import > Select: `postman-collection-gateway.json`
3. Go to: "Setup & Variables" > "Generate JWT Token" > Send
4. Go to: "Account Service" > "Get Account Balance" > Send

Should work! ✅

---

## 🛑 Stopping Services

### Windows
```bash
stop-all.bat
```

**What it does:**
- Stops Docker containers
- Preserves databases (volumes)
- You close Java service windows manually

### macOS/Linux
```bash
./stop-all.sh
```

---

## 🔄 Restart (Clean)

**Delete everything and restart fresh:**
```bash
# Stop all
stop-all.bat

# Delete Docker volumes (CAUTION: deletes databases!)
docker-compose down -v

# Start fresh
start-all.bat
```

---

## 📍 Service Locations

| Service | URL | Port |
|---------|-----|------|
| API Gateway | http://localhost:8080 | 8080 |
| Account Service | http://localhost:8081 | 8081 |
| Fraud Service | http://localhost:8082 | 8082 |
| Payment Service | http://localhost:8083 | 8083 |
| Notification | http://localhost:8084 | 8084 |
| Transaction | http://localhost:8085 | 8085 |

**Infrastructure:**
- PostgreSQL: localhost:5432
- Kafka: localhost:9094
- Keycloak: http://localhost:8080 (identity provider)

---

## 🚨 Troubleshooting

### "Docker is not running"
**Solution:** Open Docker Desktop and wait for it to start (2-3 min)

### "Port already in use"
```bash
# Find process using port
netstat -ano | findstr :8080

# Kill process
taskkill /PID <PID> /F
```

### "mvnw command not found"
```bash
# Make it executable (macOS/Linux)
chmod +x mvnw
```

### "Services not starting"
```bash
# Check Docker logs
docker-compose logs postgres
docker-compose logs kafka

# Check service logs: Look in service windows
```

### "Cannot connect to service"
```bash
# Services take 30-60 seconds to start
# Wait longer

# Or check if running:
curl http://localhost:8081/actuator/health
```

---

## 📊 What Gets Started

```
START-ALL.BAT:

1. Docker (3 services)
   └─ PostgreSQL 16 (5432)
   └─ Kafka 3.7 (9094)
   └─ Keycloak 24 (8080)

2. Maven Build (all 7 Java services)
   └─ Compiles all code
   └─ Runs tests
   └─ Creates artifacts

3. Java Services (6 services + gateway)
   ├─ api-gateway-service (8080) ← NEW
   ├─ account-service (8081)
   ├─ fraud-service (8082)
   ├─ payment-service (8083)
   ├─ notification-service (8084)
   └─ transaction-history-service (8085)

Total: 9 running processes
```

---

## ⚙️ Customization

### Override Service Port
```bash
java -jar payment-service.jar --server.port=9083
```

### Override Kafka Address
```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
java -jar notification-service.jar
```

### Override Database URL
```bash
export DB_HOST=mydb.example.com
export DB_PORT=5432
java -jar account-service.jar
```

---

## 🎯 Next Steps

1. **Services running?** → Go to **03-TESTING-GUIDE.md** to test them
2. **Want to understand code?** → Go to **02-CODE-WALKTHROUGH.md**
3. **Want architecture?** → Go to **05-ARCHITECTURE.md**
