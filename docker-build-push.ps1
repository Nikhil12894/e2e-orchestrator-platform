# ==============================================================================
# E2E Orchestrator Platform - Docker Build & Push Script for Windows (PowerShell)
# Builds both Frontend and Backend Docker images and pushes them to Docker Hub.
#
# Usage:
#   .\docker-build-push.ps1 -DockerUsername <username> [-Tag <tag>] [-MultiArch]
#
# Examples:
#   .\docker-build-push.ps1 -DockerUsername nalinkumar
#   .\docker-build-push.ps1 -DockerUsername nalinkumar -Tag 1.0.0
#   .\docker-build-push.ps1 -DockerUsername nalinkumar -Tag 1.0.0 -MultiArch
# ==============================================================================

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $false)]
    [string]$DockerUsername = $env:DOCKER_USERNAME,

    [Parameter(Position = 1, Mandatory = $false)]
    [string]$Tag = "latest",

    [Parameter(Mandatory = $false)]
    [switch]$MultiArch
)

$ErrorActionPreference = "Stop"

# Project root directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "      E2E Orchestrator Platform - Windows Docker Build & Push to Docker Hub   " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host ""

# Prompt for Docker Hub username if not provided
if ([string]::IsNullOrWhiteSpace($DockerUsername)) {
    Write-Host "Docker Hub username not specified as parameter or environment variable." -ForegroundColor Yellow
    $DockerUsername = Read-Host "Enter your Docker Hub username or organization"
    if ([string]::IsNullOrWhiteSpace($DockerUsername)) {
        Write-Error "Error: Docker Hub username is required."
        exit 1
    }
}

$BackendImage = "${DockerUsername}/e2e-orchestrator-backend"
$FrontendImage = "${DockerUsername}/e2e-orchestrator-frontend"

Write-Host "Target Configuration:" -ForegroundColor White
Write-Host "  • Docker Hub User : " -NoNewline; Write-Host $DockerUsername -ForegroundColor Green
Write-Host "  • Image Tag       : " -NoNewline; Write-Host $Tag -ForegroundColor Green
Write-Host "  • Multi-Arch Build: " -NoNewline; Write-Host $MultiArch.IsPresent -ForegroundColor Green
Write-Host "  • Backend Image   : " -NoNewline; Write-Host "${BackendImage}:${Tag}" -ForegroundColor Cyan
Write-Host "  • Frontend Image  : " -NoNewline; Write-Host "${FrontendImage}:${Tag}" -ForegroundColor Cyan
Write-Host ""

# 1. Check if Docker daemon is running
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not running."
    }
} catch {
    Write-Host "Error: Docker daemon is not running. Please start Docker Desktop and retry." -ForegroundColor Red
    exit 1
}

# 2. Check Docker Hub authentication
Write-Host "==> Checking Docker Hub authentication..." -ForegroundColor Cyan
if ($dockerInfo -notmatch "Username:") {
    Write-Host "Notice: You may not be logged into Docker Hub." -ForegroundColor Yellow
    docker login -u $DockerUsername
}

# 3. Setup Docker Buildx if multi-arch requested
if ($MultiArch.IsPresent) {
    Write-Host "==> Setting up Docker Buildx for multi-architecture builds..." -ForegroundColor Cyan
    $builderExists = docker buildx ls | Select-String "e2e-builder"
    if (-not $builderExists) {
        # Using driver-opt network=host to prevent IPv6 connectex issues on Windows
        docker buildx create --name e2e-builder --driver docker-container --driver-opt network=host --bootstrap --use
    } else {
        docker buildx use e2e-builder
    }
    $Platforms = "linux/amd64,linux/arm64"
}

# ------------------------------------------------------------------------------
# 4. Build & Push Backend Image
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==> [1/2] Building and Pushing Backend Image..." -ForegroundColor Cyan
Write-Host "    Source: $ScriptDir\backend"
Write-Host "    Target: ${BackendImage}:${Tag}"

try {
    if ($MultiArch.IsPresent) {
        $extraTags = @("-t", "${BackendImage}:${Tag}")
        if ($Tag -ne "latest") {
            $extraTags += @("-t", "${BackendImage}:latest")
        }
        docker buildx build --platform $Platforms -f "$ScriptDir\backend\Dockerfile" $extraTags --push "$ScriptDir\backend"
    } else {
        docker build -f "$ScriptDir\backend\Dockerfile" -t "${BackendImage}:${Tag}" "$ScriptDir\backend"
        if ($Tag -ne "latest") {
            docker tag "${BackendImage}:${Tag}" "${BackendImage}:latest"
        }
        Write-Host "==> Pushing Backend image to Docker Hub..." -ForegroundColor Cyan
        docker push "${BackendImage}:${Tag}"
        if ($Tag -ne "latest") {
            docker push "${BackendImage}:latest"
        }
    }
    Write-Host "✓ Backend image successfully built and pushed!" -ForegroundColor Green
} catch {
    Write-Host "Error building/pushing backend image: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "TROUBLESHOOTING WINDOWS 11 DOCKER CLOUDFRONT / CONNECTEX ERROR:" -ForegroundColor Yellow
    Write-Host "If you see 'connectex: An attempt was made to access a socket in a way forbidden by its access permissions':" -ForegroundColor Yellow
    Write-Host "1. Disable IPv6 in Windows (forces IPv4):" -ForegroundColor White
    Write-Host "   Run PowerShell as Administrator:" -ForegroundColor White
    Write-Host "   Disable-NetAdapterBinding -Name '*' -ComponentID ms_tcpip6" -ForegroundColor Cyan
    Write-Host "2. Restart Windows NAT:" -ForegroundColor White
    Write-Host "   net stop winnat; net start winnat" -ForegroundColor Cyan
    exit 1
}

# ------------------------------------------------------------------------------
# 5. Build & Push Frontend Image
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==> [2/2] Building and Pushing Frontend Image..." -ForegroundColor Cyan
Write-Host "    Source: $ScriptDir\frontend"
Write-Host "    Target: ${FrontendImage}:${Tag}"

try {
    if ($MultiArch.IsPresent) {
        $extraTags = @("-t", "${FrontendImage}:${Tag}")
        if ($Tag -ne "latest") {
            $extraTags += @("-t", "${FrontendImage}:latest")
        }
        docker buildx build --platform $Platforms -f "$ScriptDir\frontend\Dockerfile" $extraTags --push "$ScriptDir\frontend"
    } else {
        docker build -f "$ScriptDir\frontend\Dockerfile" -t "${FrontendImage}:${Tag}" "$ScriptDir\frontend"
        if ($Tag -ne "latest") {
            docker tag "${FrontendImage}:${Tag}" "${FrontendImage}:latest"
        }
        Write-Host "==> Pushing Frontend image to Docker Hub..." -ForegroundColor Cyan
        docker push "${FrontendImage}:${Tag}"
        if ($Tag -ne "latest") {
            docker push "${FrontendImage}:latest"
        }
    }
    Write-Host "✓ Frontend image successfully built and pushed!" -ForegroundColor Green
} catch {
    Write-Host "Error building/pushing frontend image: $_" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# Summary Output
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Green
Write-Host "                  Build & Push Completed Successfully!                        " -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Published Docker Images:" -ForegroundColor White
Write-Host "  • docker pull ${BackendImage}:${Tag}" -ForegroundColor Cyan
Write-Host "  • docker pull ${FrontendImage}:${Tag}" -ForegroundColor Cyan
if ($Tag -ne "latest") {
    Write-Host "  • docker pull ${BackendImage}:latest" -ForegroundColor Cyan
    Write-Host "  • docker pull ${FrontendImage}:latest" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "How to run anywhere with a single command:" -ForegroundColor White
Write-Host "  `$env:DOCKER_USERNAME = '$DockerUsername'; docker compose -f docker-compose.hub.yml up -d" -ForegroundColor Yellow
Write-Host ""
