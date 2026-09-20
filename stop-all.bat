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

echo [OK] All services have been stopped
echo [OK] Docker containers stopped
echo [OK] Volumes preserved (databases intact)
echo.

echo To restart services:
echo    start-all.bat
echo.

echo To stop and delete everything:
echo    docker-compose down -v
echo.

pause
