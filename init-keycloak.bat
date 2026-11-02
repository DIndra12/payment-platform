@echo off
REM Payment Platform - Initialize Keycloak Realm
REM Runs the init-keycloak.sh script to set up realm, client, and test users

echo.
echo ========================================================================
echo Keycloak Realm Initialization
echo ========================================================================
echo.

REM Check if Docker is running
docker info > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Please start Docker first.
    echo.
    pause
    exit /b 1
)

echo [*] Waiting for Keycloak to be ready...
echo.

REM Run the init-keycloak.sh script in a Docker container
REM The dos2unix conversion fixes Windows line endings (CRLF) to Unix (LF)
docker run --rm ^
  --network payment-platform_default ^
  -e KEYCLOAK_URL=http://keycloak:8080 ^
  -e KEYCLOAK_ADMIN=admin ^
  -e KEYCLOAK_ADMIN_PASSWORD=admin ^
  -v "%~dp0infrastructure\keycloak:/scripts" ^
  alpine:latest ^
  sh -c "apk add --no-cache dos2unix && dos2unix /scripts/init-keycloak.sh && sh /scripts/init-keycloak.sh"

if errorlevel 1 (
    echo.
    echo [ERROR] Keycloak initialization failed
    echo.
    echo Troubleshooting:
    echo   1. Make sure Keycloak is running: docker-compose ps
    echo   2. Check Keycloak logs: docker-compose logs keycloak
    echo   3. Ensure the network exists: docker network ls
    echo.
) else (
    echo.
    echo [OK] Keycloak initialization completed successfully!
    echo.
    echo Next steps:
    echo   1. Verify in Keycloak Admin Console: http://localhost:8090
    echo   2. Login: admin / admin
    echo   3. Check realm "payment-platform" exists
    echo   4. Check client "payment-api" exists
    echo.
)

pause
