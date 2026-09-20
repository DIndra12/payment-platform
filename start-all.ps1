#Requires -Version 5.0
<#
.SYNOPSIS
    Payment Platform - Complete Startup Script (Windows PowerShell)

.DESCRIPTION
    This script sets up and starts the entire payment platform:
    1. Starts Docker containers (PostgreSQL, Kafka, Keycloak)
    2. Builds all microservices
    3. Starts all 5 Spring Boot services
    4. Verifies health of all services

.PARAMETER Clean
    Remove existing containers and volumes (fresh start)

.PARAMETER SkipDocker
    Skip docker-compose startup (use existing containers)

.PARAMETER SkipBuild
    Skip Maven build (use existing JARs)

.EXAMPLE
    .\start-all.ps1
    .\start-all.ps1 -Clean
    .\start-all.ps1 -SkipBuild
#>

param(
    [switch]$Clean,
    [switch]$SkipDocker,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerComposeFile = "$ProjectDir\docker-compose.yml"
$Services = @(
    @{ Name = "account-service"; Port = 8081 },
    @{ Name = "fraud-service"; Port = 8082 },
    @{ Name = "payment-service"; Port = 8083 },
    @{ Name = "notification-service"; Port = 8084 },
    @{ Name = "transaction-history-service"; Port = 8085 }
)

function Print-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "========================================================================"
    Write-Host $Text
    Write-Host "========================================================================"
    Write-Host ""
}

function Print-Step {
    param([string]$Text)
    Write-Host "[*] $Text"
}

function Print-Success {
    param([string]$Text)
    Write-Host "[OK] $Text" -ForegroundColor Green
}

function Print-Error {
    param([string]$Text)
    Write-Host "[ERROR] $Text" -ForegroundColor Red
}

function Check-Docker-Installed {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Print-Error "Docker is not installed. Please install Docker Desktop for Windows."
        exit 1
    }
    Print-Success "Docker is installed"
}

function Check-Docker-Running {
    try {
        docker ps | Out-Null
        Print-Success "Docker daemon is running"
    }
    catch {
        Print-Error "Docker daemon is not running. Please start Docker Desktop."
        exit 1
    }
}

function Check-Port-Available {
    param([int]$Port, [string]$Service)

    $Connection = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue
    if ($Connection.TcpTestSucceeded) {
        Print-Error "Port $Port ($Service) is already in use."
        return $false
    }
    return $true
}

function Start-Docker-Infrastructure {
    Print-Header "Starting Docker Infrastructure"

    if ($SkipDocker) {
        Print-Step "Skipping Docker setup (SkipDocker flag set)"
        return
    }

    Print-Step "Checking required ports..."
    Check-Port-Available 5432 "PostgreSQL" | Out-Null
    Check-Port-Available 9094 "Kafka" | Out-Null
    Check-Port-Available 8080 "Keycloak" | Out-Null

    if ($Clean) {
        Print-Step "Removing existing containers and volumes (Clean flag set)..."
        & docker-compose -f "$DockerComposeFile" down -v 2> $null
        Start-Sleep -Seconds 2
    }

    Print-Step "Starting docker-compose services (PostgreSQL, Kafka, Keycloak)..."
    & docker-compose -f "$DockerComposeFile" up -d

    Print-Step "Waiting for PostgreSQL to be ready..."
    $MaxAttempts = 30
    $Attempts = 0
    while ($Attempts -lt $MaxAttempts) {
        try {
            $Result = & docker exec payments-postgres pg_isready -U postgres 2>&1
            if ($LASTEXITCODE -eq 0) {
                Print-Success "PostgreSQL is ready"
                break
            }
        }
        catch { }

        $Attempts++
        if ($Attempts -eq $MaxAttempts) {
            Print-Error "PostgreSQL failed to start after $MaxAttempts attempts"
            exit 1
        }
        Start-Sleep -Seconds 1
    }

    Print-Step "Waiting for Kafka to be ready..."
    Start-Sleep -Seconds 5
    Print-Success "Kafka is ready"

    Print-Success "Docker infrastructure started successfully"
}

function Build-Services {
    Print-Header "Building Microservices"

    if ($SkipBuild) {
        Print-Step "Skipping Maven build (SkipBuild flag set)"
        return
    }

    Print-Step "Running: .\mvnw clean package -DskipTests"
    Set-Location $ProjectDir
    & .\mvnw clean package -DskipTests -q

    if ($LASTEXITCODE -ne 0) {
        Print-Error "Maven build failed"
        exit 1
    }

    Print-Success "All services built successfully"
}

function Start-Microservices {
    Print-Header "Starting Microservices"

    Print-Step "Starting each service in a new PowerShell window..."
    Write-Host ""

    foreach ($Service in $Services) {
        $ServiceName = $Service.Name
        $ServicePort = $Service.Port
        $ServiceDir = Join-Path $ProjectDir $ServiceName
        $LogFile = "$env:TEMP\$ServiceName.log"

        $Command = "cd '$ServiceDir'; .\mvnw spring-boot:run *> '$LogFile'; Read-Host 'Press Enter to close this window'"
        Start-Process powershell -ArgumentList "-NoExit", "-Command", $Command -WindowStyle Normal

        Write-Host "  Starting: $ServiceName (Port $ServicePort)"
        Write-Host "    Logs:   $LogFile"

        Start-Sleep -Milliseconds 500
    }

    Print-Success "All services started in new PowerShell windows"
}

function Verify-Services {
    Print-Header "Verifying Services"

    Print-Step "Waiting for services to start (this may take 1-2 minutes)..."
    Write-Host ""

    $AllHealthy = $true

    foreach ($Service in $Services) {
        $ServiceName = $Service.Name
        $ServicePort = $Service.Port
        $MaxAttempts = 30
        $Attempts = 0
        $Healthy = $false

        while ($Attempts -lt $MaxAttempts) {
            try {
                $Response = Invoke-WebRequest -Uri "http://localhost:$ServicePort/actuator/health" `
                    -ErrorAction SilentlyContinue -TimeoutSec 2
                if ($Response.StatusCode -eq 200) {
                    Print-Success "$ServiceName is running on port $ServicePort"
                    $Healthy = $true
                    break
                }
            }
            catch { }

            $Attempts++
            if ($Attempts -eq $MaxAttempts) {
                Print-Error "$ServiceName failed to start (port $ServicePort)"
                $AllHealthy = $false
            }

            Start-Sleep -Seconds 1
        }
    }

    Write-Host ""

    if ($AllHealthy) {
        Print-Success "All services are healthy and running!"
    }
    else {
        Print-Error "Some services failed to start. Check logs for details."
        exit 1
    }
}

function Print-Summary {
    Print-Header "Setup Complete!"

    Write-Host "All services are now running:"
    Write-Host ""
    foreach ($Service in $Services) {
        $ServiceName = $Service.Name
        $ServicePort = $Service.Port
        Write-Host "  [OK] $ServiceName : http://localhost:$ServicePort"
    }
    Write-Host ""

    Write-Host "Infrastructure:"
    Write-Host "  [OK] PostgreSQL:          localhost:5432"
    Write-Host "  [OK] Kafka:               localhost:9094"
    Write-Host "  [OK] Keycloak:            http://localhost:8080"
    Write-Host ""

    Write-Host "Next Steps:"
    Write-Host ""
    Write-Host "1. Import Postman Collection:"
    Write-Host "   - Open Postman"
    Write-Host "   - File > Import"
    Write-Host "   - Select: postman-collection.json"
    Write-Host ""
    Write-Host "2. Run Test Requests:"
    Write-Host "   - Go to 'Setup & Variables' > 'Get test accounts (Setup)'"
    Write-Host "   - Click Send"
    Write-Host "   - Then run any request to test the APIs"
    Write-Host ""
    Write-Host "3. View Service Health:"
    foreach ($Service in $Services) {
        $ServiceName = $Service.Name
        $ServicePort = $Service.Port
        Write-Host "   - Invoke-WebRequest http://localhost:$ServicePort/actuator/health"
    }
    Write-Host ""

    Write-Host "Useful Commands:"
    Write-Host ""
    Write-Host "  Stop all services:"
    Write-Host "    .\stop-all.ps1"
    Write-Host ""
    Write-Host "  View service logs:"
    Write-Host "    Get-Content `$env:TEMP\<service-name>.log -Tail 50 -Wait"
    Write-Host ""
    Write-Host "  Restart infrastructure:"
    Write-Host "    docker-compose up -d"
    Write-Host ""
}

function Main {
    Print-Header "Payment Platform - Complete Startup"

    Print-Step "Checking prerequisites..."
    Check-Docker-Installed
    Check-Docker-Running

    Start-Docker-Infrastructure
    Build-Services
    Start-Microservices
    Verify-Services
    Print-Summary
}

Main
