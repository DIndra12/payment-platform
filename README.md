# 🚀 Payments Platform (Microservices Architecture)

Welcome to the Payments Platform! This project is a multi-module Spring Boot application designed to process payments using a distributed microservices architecture. It implements the **Saga Pattern** for distributed transactions, ensuring data consistency across multiple independent services.

## 🏗️ What Has Been Built So Far

The platform currently consists of three core microservices communicating synchronously via **Spring Cloud OpenFeign**:

1. **Payment Service (Orchestrator - Port 8083):**
    * Acts as the central coordinator for the transaction lifecycle (INITIATED -> FRAUD CHECK -> ACCOUNT DEBIT -> COMPLETED/FAILED).
    * Manages the Saga pattern state machine and handles graceful rollbacks/failure logging.
2. **Fraud Service (Port 8082):**
    * Evaluates the risk of a transaction.
    * Currently implements a rule-based engine that calculates a risk score (e.g., flagging transactions over 10,000 for rejection).
3. **Account Service (Port 8081):**
    * Manages user accounts and ledger entries.
    * Ensures **idempotency** using unique reference IDs so a payment is never debited twice.
    * Utilizes Optimistic Locking (`@Version`) to prevent race conditions during concurrent balance updates.

**Infrastructure:**
* **PostgreSQL:** Hosts independent schemas (`account_db`, `fraud_db`, `payment_db`).
* **Apache Kafka:** Available for future asynchronous event streaming.
* **Keycloak:** Available for future IAM/OAuth2 security integration.

---

## 🛠️ Prerequisites & Local Setup

Before starting, ensure your local development machine has the following installed:

1. **Java 21 & Maven:** Ensure your `JAVA_HOME` is set to JDK 21.
2. **WSL 2 (Windows Subsystem for Linux):** Required if developing on Windows.
3. **Docker Desktop:** Must be installed and configured to use the WSL 2 backend.

### Docker & WSL Setup (Windows Users)
1. Install [Docker Desktop](https://www.docker.com/products/docker-desktop/).
2. Go to Docker Desktop **Settings > General** and check **"Use the WSL 2 based engine"**.
3. Go to **Settings > Resources > WSL Integration** and enable integration for your default WSL distro (e.g., Ubuntu).
4. Restart Docker Desktop and verify the daemon is running by opening PowerShell and typing:
   ```powershell
   docker ps