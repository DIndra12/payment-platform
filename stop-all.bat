@echo off
REM Payment Platform - Stop All Services Script (Windows Batch)

echo.
echo ========================================================================
echo Payment Platform - Stop All Services
echo ========================================================================
echo.

echo [*] Stopping Docker infrastructure...
cd /d "%~dp0"
docker-compose stop
if errorlevel 1 (
    echo [WARNING] Failed to stop docker-compose
) else (
    echo [OK] Docker containers stopped
)

echo.
echo ========================================================================
echo Shutdown Complete!
echo ========================================================================
echo.

echo [OK] Docker containers stopped (PostgreSQL, Kafka, Keycloak)
echo [OK] Volumes preserved (databases intact)
echo.
echo To stop Java services:
echo    - Close the service windows manually, OR
echo    - Close all command windows started by start-all.bat
echo.
echo Services that need manual stop:
echo    - api-gateway-service (Port 8080)
echo    - account-service (Port 8081)
echo    - fraud-service (Port 8082)
echo    - payment-service (Port 8083)
echo    - notification-service (Port 8084)
echo    - transaction-history-service (Port 8085)
echo.

echo To restart services:
echo    start-all.bat
echo.

echo To stop and delete everything:
echo    docker-compose down -v
echo.

pause
