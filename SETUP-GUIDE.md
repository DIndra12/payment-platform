# Complete Setup & Startup Guide

Everything you need to get the Payment Platform running locally with a single command.

---

## Prerequisites

### Required Software

- **Docker Desktop** (includes Docker & Docker Compose)
  - [Download for Windows](https://www.docker.com/products/docker-desktop)
  - [Download for macOS](https://www.docker.com/products/docker-desktop)
  - [Download for Linux](https://docs.docker.com/engine/install/)

- **Java 21**
  - [Download OpenJDK 21](https://adoptium.net/temurin/releases/?version=21)
  - Verify: `java -version` → should show "21.x.x"

- **Maven** (optional - included as mvnw wrapper)
  - Already included in project as `./mvnw` (Unix/Mac) or `.\mvnw.cmd` (Windows)

### Recommended

- **Postman** (for testing APIs)
  - [Download from postman.com](https://www.postman.com/downloads/)
- **Terminal Multiplexer** (optional but recommended)
  - **macOS/Linux:** `tmux` or `screen`
  - **Windows:** PowerShell (built-in)

---

## Quick Start (3 steps)

### Step 1: Verify Prerequisites

**macOS/Linux:**
```bash
# Check Docker
docker --version
docker-compose --version

# Check Java
java -version

# Check Maven wrapper
./mvnw --version
```

**Windows (PowerShell):**
```powershell
# Check Docker
docker --version
docker-compose --version

# Check Java
java -version

# Check Maven wrapper
.\mvnw.cmd --version
```

### Step 2: Run Startup Script

**macOS/Linux:**
```bash
chmod +x start-all.sh  # Make script executable (first time only)
./start-all.sh
```

**Windows (PowerShell):**
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser  # First time only
.\start-all.ps1
```

### Step 3: Wait for Services

The script will:
1. ✅ Start Docker containers (PostgreSQL, Kafka, Keycloak)
2. ✅ Build all 5 microservices
3. ✅ Start each service in a separate terminal
4. ✅ Verify all services are healthy
5. ✅ Display summary with next steps

**Expected output:**
```
✓ account-service is running on port 8081
✓ fraud-service is running on port 8082
✓ payment-service is running on port 8083
✓ notification-service is running on port 8084
✓ transaction-history-service is running on port 8085

All services are healthy and running!
```

---

## What Gets Started

### Services (5 Spring Boot Applications)

| Service | Port | Purpose |
|---------|------|---------|
| Account Service | 8081 | Account & ledger management |
| Fraud Service | 8082 | Risk evaluation |
| Payment Service | 8083 | Payment orchestration |
| Notification Service | 8084 | Event consumer (SMS notifications) |
| Transaction History | 8085 | CQRS read model & query API |

### Infrastructure (3 Docker Containers)

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5432 | Database (5 separate databases) |
| Kafka | 9094 | Message broker |
| Keycloak | 8080 | Identity provider (not yet integrated) |

---

## Usage

### Normal Startup

```bash
# macOS/Linux
./start-all.sh

# Windows PowerShell
.\start-all.ps1
```

### Fresh Start (Clean Everything)

```bash
# macOS/Linux
./start-all.sh --clean

# Windows PowerShell
.\start-all.ps1 -Clean
```

Removes all Docker containers and volumes, then starts fresh with new databases.

### Skip Docker (Use Existing Infrastructure)

```bash
# macOS/Linux
./start-all.sh --skip-docker

# Windows PowerShell
.\start-all.ps1 -SkipDocker
```

Useful if Docker infrastructure is already running and you only want to rebuild/restart services.

### Skip Build (Use Existing JARs)

```bash
# macOS/Linux
./start-all.sh --skip-build

# Windows PowerShell
.\start-all.ps1 -SkipBuild
```

Faster startup if you haven't changed code. Skips Maven build.

### Combine Flags

```bash
# macOS/Linux
./start-all.sh --skip-docker --skip-build

# Windows PowerShell
.\start-all.ps1 -SkipDocker -SkipBuild
```

---

## Stopping Services

### Stop All Services (Keep Data)

```bash
# macOS/Linux
./stop-all.sh

# Windows PowerShell
.\stop-all.ps1
```

Stops services but preserves Docker volumes (databases intact).

### Stop All Services (Delete Data)

```bash
# macOS/Linux
./stop-all.sh --remove-volumes

# Windows PowerShell
.\stop-all.ps1 -RemoveVolumes
```

Removes everything including databases. Same as `--clean` start.

---

## Testing the System

### Using Postman

1. **Import Collection:**
   - Open Postman
   - File → Import
   - Select: `postman-collection.json`

2. **Initialize Variables:**
   - Go to "Setup & Variables" → "Get test accounts (Setup)"
   - Click Send
   - ✅ Variables are now set

3. **Test Any Request:**
   - Go to "Payment Service" → "Create Payment - Valid"
   - Click Send
   - See response + automatic test results

See [POSTMAN-GUIDE.md](POSTMAN-GUIDE.md) for complete testing guide.

### Using curl

```bash
# Health check
curl http://localhost:8083/actuator/health

# Create a payment
curl -X POST http://localhost:8083/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-payment-1" \
  -d '{
    "payerAccountId": "00000000-0000-0000-0000-000000000001",
    "payeeAccountId": "00000000-0000-0000-0000-000000000002",
    "amount": 100.00,
    "currency": "USD"
  }'
```

### Check Service Health

```bash
# macOS/Linux
for port in 8081 8082 8083 8084 8085; do
  curl -s http://localhost:$port/actuator/health | jq .
done

# Windows PowerShell
@(8081, 8082, 8083, 8084, 8085) | ForEach-Object {
  $response = Invoke-WebRequest "http://localhost:$_/actuator/health" -ErrorAction SilentlyContinue
  "Port $_`: $($response.StatusCode)"
}
```

---

## Troubleshooting

### Issue 1: "Docker is not running"

**Solution:**
1. Open Docker Desktop
2. Wait for it to fully start
3. Run: `docker ps` to verify
4. Run startup script again

### Issue 2: "Port already in use"

**If Docker container already running:**
```bash
# See what's running
docker-compose ps

# Stop existing containers
docker-compose stop

# Run startup script
./start-all.sh --skip-docker
```

**If something else is using the port:**
```bash
# macOS/Linux - Find what's using port 8083
lsof -i :8083
kill -9 <PID>

# Windows PowerShell
Get-Process | Where-Object { $_.Port -eq 8083 }
```

### Issue 3: "mvnw command not found" (macOS/Linux)

**Solution:**
```bash
# Make mvnw executable
chmod +x mvnw

# Try again
./start-all.sh
```

### Issue 4: "Services not starting" or "Timeout waiting for services"

**Check Docker logs:**
```bash
docker-compose logs -f postgres
docker-compose logs -f kafka
```

**Check service logs:**
- **macOS/Linux (tmux):** `tmux attach-session -t payment-platform`
- **macOS/Linux (screen):** `screen -r payment-platform`
- **Windows:** Check PowerShell windows for error messages

### Issue 5: "Cannot connect to Docker daemon"

**Solution:**
1. Ensure Docker Desktop is running
2. On Linux, start Docker service: `sudo systemctl start docker`
3. Give Docker permission (Linux): `sudo usermod -aG docker $USER`
4. Logout and login again

### Issue 6: "Port mapping error" on macOS M1/M2

**Solution:** Update Docker Desktop to latest version and ensure Rosetta 2 emulation is enabled.

---

## Service Logs

### View Logs in Real-Time

**macOS/Linux with tmux:**
```bash
tmux attach-session -t payment-platform
# Use Ctrl+B then N to switch windows
```

**macOS/Linux with screen:**
```bash
screen -r payment-platform
# Use Ctrl+A then N to switch windows
```

**Windows PowerShell:**
- Check individual PowerShell windows that opened
- Or view files: `Get-Content $env:TEMP\<service-name>.log -Tail 50 -Wait`

**Docker containers:**
```bash
docker-compose logs -f postgres   # PostgreSQL logs
docker-compose logs -f kafka       # Kafka logs
docker-compose logs -f keycloak    # Keycloak logs
```

---

## Database Access

### PostgreSQL Connection

**Connection Details:**
- Host: `localhost`
- Port: `5432`
- User: `postgres`
- Password: `postgres`

**Using psql (command-line):**
```bash
psql -h localhost -U postgres -d postgres
```

**Using pgAdmin (GUI):**
1. Install: [pgadmin.org](https://www.pgadmin.org/)
2. Connect to: `localhost:5432`
3. Credentials: `postgres` / `postgres`

**Databases:**
- `payment_db` - Payment Service
- `account_db` - Account Service
- `fraud_db` - Fraud Service
- `notification_db` - Notification Service
- `transaction_history_db` - Transaction History Service

---

## Kafka Topics

### View Topics

```bash
# List all topics
docker exec payments-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe a topic
docker exec payments-kafka kafka-topics.sh --describe --topic payment.completed --bootstrap-server localhost:9092
```

### Topics in Use

- `payment.completed` - Payment completed events
- `payment.failed` - Payment failed events
- `account.debited` - Account debit events
- `account.credited` - Account credit events
- `payment.completed.DLT` - Dead-letter topic for payment events
- `account.DLT` - Dead-letter topic for account events

---

## Advanced Operations

### Restart Just the Services (Keep Docker)

```bash
# Stop services only (not Docker)
./stop-all.sh

# Start services only (use existing Docker)
./start-all.sh --skip-docker
```

### Rebuild Everything

```bash
# Full clean rebuild
./start-all.sh --clean
```

### Monitor Resource Usage

```bash
# macOS/Linux
docker stats

# Windows PowerShell
docker stats
```

### View All Container Details

```bash
docker-compose ps -a
```

### Execute Commands in Containers

```bash
# Connect to PostgreSQL container
docker exec -it payments-postgres psql -U postgres

# Execute SQL query
docker exec payments-postgres psql -U postgres -c "SELECT * FROM information_schema.tables WHERE table_schema='public';"

# View Kafka broker info
docker exec payments-kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

---

## Performance Tuning

### Increase Docker Memory (If Services Are Slow)

**Windows/macOS Docker Desktop:**
1. Open Docker Desktop Settings
2. Go to Resources
3. Increase Memory to 4GB or more
4. Restart Docker
5. Re-run startup script

**Linux:**
Check available memory: `free -h`

### Increase JVM Heap Size (If Services OOM)

Edit service startup or set environment variable:
```bash
export JAVA_OPTS="-Xmx1024m -Xms512m"
./start-all.sh
```

---

## Development Workflow

### Code Change → Test Cycle

1. **Edit code** in your IDE
2. **Run startup script** (with `--skip-docker` to save time)
3. **Services rebuild** and restart
4. **Test changes** using Postman or curl
5. **View logs** in terminal multiplexer window

### Debug Specific Service

```bash
# macOS/Linux - Attach to service window
tmux attach-session -t payment-platform
# Then use arrow keys to switch windows

# Windows - Click on the PowerShell window
# Add breakpoints in IDE for step debugging
```

### Add Logging

Add to your code:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyClass {
    public void myMethod() {
        log.info("Debug info: {}", variable);
        log.error("Error occurred", exception);
    }
}
```

---

## Next Steps After Setup

1. **Test APIs with Postman** - See [POSTMAN-GUIDE.md](POSTMAN-GUIDE.md)
2. **Review Design Documents** - See [payments-microservices-design.md](payments-microservices-design.md)
3. **Read Production Roadmap** - See [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md)
4. **Check Implementation Progress** - See [IMPLEMENTATION-CHECKLIST.md](IMPLEMENTATION-CHECKLIST.md)

---

## Useful Links

- [Docker Documentation](https://docs.docker.com/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Postman Learning Center](https://learning.postman.com/)

---

## Getting Help

1. **Check logs** - First place to look for errors
2. **Check POSTMAN-GUIDE.md** - For API testing issues
3. **Check README.md** - For architecture and setup details
4. **Check service terminal window** - Real-time error messages
5. **Review Docker logs** - See infrastructure errors

---

**Last Updated:** 2026-09-20  
**Tested On:** macOS, Linux, Windows PowerShell
