@echo off
REM Payment Platform - Complete Startup Script (Windows Batch)
REM Simple, reliable startup for all services

setlocal enabledelayedexpansion

echo.
echo ========================================================================
echo Payment Platform - Complete Startup
echo ========================================================================
echo.

REM Check if Docker is running
echo [*] Checking Docker...
docker ps >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)
echo [OK] Docker is running

REM Start Docker infrastructure
echo.
echo ========================================================================
echo Starting Docker Infrastructure
echo ========================================================================
echo.

echo [*] Starting docker-compose services...
cd /d "%~dp0"
docker-compose up -d
if errorlevel 1 (
    echo [ERROR] Failed to start docker-compose
    pause
    exit /b 1
)
echo [OK] Docker infrastructure started

REM Wait for PostgreSQL
echo.
echo [*] Waiting for PostgreSQL to be ready...
timeout /t 5 /nobreak

REM Build services
echo.
echo ========================================================================
echo Building Microservices
echo ========================================================================
echo.

echo [*] Running Maven build...
call mvnw.cmd clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed
    pause
    exit /b 1
)
echo [OK] All services built successfully

REM Start services
echo.
echo ========================================================================
echo Starting Microservices
echo ========================================================================
echo.

echo [*] Starting each service in a new window...
echo.

echo   Starting: account-service (Port 8081)
start "account-service" cmd /k "cd /d "%~dp0account-service" && mvnw.cmd spring-boot:run"
timeout /t 1 /nobreak >nul

echo   Starting: fraud-service (Port 8082)
start "fraud-service" cmd /k "cd /d "%~dp0fraud-service" && mvnw.cmd spring-boot:run"
timeout /t 1 /nobreak >nul

echo   Starting: payment-service (Port 8083)
start "payment-service" cmd /k "cd /d "%~dp0payment-service" && mvnw.cmd spring-boot:run"
timeout /t 1 /nobreak >nul

echo   Starting: notification-service (Port 8084)
start "notification-service" cmd /k "cd /d "%~dp0notification-service" && mvnw.cmd spring-boot:run"
timeout /t 1 /nobreak >nul

echo   Starting: transaction-history-service (Port 8085)
start "transaction-history-service" cmd /k "cd /d "%~dp0transaction-history-service" && mvnw.cmd spring-boot:run"
timeout /t 1 /nobreak >nul

REM Wait for services to start
echo.
echo [*] Waiting for services to start (60 seconds)...
timeout /t 60 /nobreak

REM Display summary
echo.
echo ========================================================================
echo Setup Complete!
echo ========================================================================
echo.

echo All services should now be running:
echo.
echo   [OK] account-service:               http://localhost:8081
echo   [OK] fraud-service:                 http://localhost:8082
echo   [OK] payment-service:               http://localhost:8083
echo   [OK] notification-service:          http://localhost:8084
echo   [OK] transaction-history-service:   http://localhost:8085
echo.

echo Infrastructure:
echo   [OK] PostgreSQL:                    localhost:5432
echo   [OK] Kafka:                         localhost:9094
echo   [OK] Keycloak:                      http://localhost:8080
echo.

echo Next Steps:
echo.
echo 1. Import Postman Collection:
echo    - Open Postman
echo    - File ^> Import
echo    - Select: postman-collection.json
echo.
echo 2. Run Test Requests:
echo    - Go to "Setup ^& Variables" ^> "Get test accounts"
echo    - Click Send
echo    - Then test any API
echo.

echo To stop services:
echo    - Close the service windows manually, OR
echo    - Run: stop-all.bat
echo.

pause
