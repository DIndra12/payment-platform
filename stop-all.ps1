#Requires -Version 5.0
<#
.SYNOPSIS
    Payment Platform - Stop All Services Script (Windows PowerShell)

.DESCRIPTION
    This script gracefully shuts down all services:
    1. Terminates Spring Boot services
    2. Stops Docker containers
    3. Displays cleanup summary

.PARAMETER RemoveVolumes
    Remove Docker volumes (clean database)

.EXAMPLE
    .\stop-all.ps1
    .\stop-all.ps1 -RemoveVolumes
#>

param(
    [switch]$RemoveVolumes
)

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerComposeFile = "$ProjectDir\docker-compose.yml"

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

function Stop-Services {
    Print-Header "Stopping Services"

    Print-Step "Checking for running Spring Boot services..."

    $JavaProcesses = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*spring-boot*" }

    if ($JavaProcesses) {
        Print-Step "Found $($JavaProcesses.Count) Java process(es) running Spring Boot"
        Print-Step "Terminating processes..."

        foreach ($Process in $JavaProcesses) {
            try {
                Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
                Write-Host "  Killed process ID: $($Process.Id)"
            }
            catch {
                Print-Error "Failed to kill process ID: $($Process.Id)"
            }
        }

        Start-Sleep -Seconds 2
        Print-Success "Java processes terminated"
    }
    else {
        Print-Step "No Java processes found"
    }
}

function Stop-Docker {
    Print-Header "Stopping Docker Infrastructure"

    if (-not (Test-Path $DockerComposeFile)) {
        Print-Error "docker-compose.yml not found at $DockerComposeFile"
        return
    }

    Print-Step "Stopping docker-compose services..."
    Set-Location $ProjectDir

    try {
        if ($RemoveVolumes) {
            Print-Step "Removing containers and volumes (RemoveVolumes flag set)..."
            & docker-compose -f "$DockerComposeFile" down -v
            Print-Success "Containers and volumes removed"
        }
        else {
            Print-Step "Stopping containers (volumes preserved)..."
            & docker-compose -f "$DockerComposeFile" stop
            Print-Success "Containers stopped"
        }
    }
    catch {
        Print-Error "Failed to stop docker-compose: $_"
    }
}

function Print-Summary {
    Print-Header "Shutdown Complete!"

    Write-Host "All services have been stopped:"
    Write-Host ""
    Write-Host "  [OK] Spring Boot services terminated"
    Write-Host "  [OK] Docker containers stopped"
    if ($RemoveVolumes) {
        Write-Host "  [OK] Volumes removed (databases cleared)"
    }
    Write-Host ""

    Write-Host "To restart services:"
    Write-Host ""
    if ($RemoveVolumes) {
        Write-Host "  .\start-all.ps1 -Clean"
    }
    else {
        Write-Host "  .\start-all.ps1"
    }
    Write-Host ""

    Write-Host "Useful commands:"
    Write-Host ""
    Write-Host "  Start only Docker (no services):"
    Write-Host "    docker-compose up -d"
    Write-Host ""
    Write-Host "  Check Docker status:"
    Write-Host "    docker-compose ps"
    Write-Host ""
    Write-Host "  View Docker logs:"
    Write-Host "    docker-compose logs -f postgres"
    Write-Host ""
    Write-Host "  Remove all data and containers:"
    Write-Host "    docker-compose down -v"
    Write-Host ""
}

function Main {
    Print-Header "Payment Platform - Stop All Services"

    Stop-Services
    Stop-Docker
    Print-Summary
}

Main
