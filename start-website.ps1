$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Join-Path $RootDir "online-judge"
$FrontendDir = Join-Path $AppDir "frontend"

$BackendPort = 8081
$FrontendPort = 5173
$BackendUrl = "http://localhost:$BackendPort/"
$FrontendUrl = "http://localhost:$FrontendPort/app/"

function Fail($Message) {
    Write-Host "Error: $Message" -ForegroundColor Red
    exit 1
}

function Test-CommandExists($Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-PortListening($Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Port($Port, $Name, $TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $Port) {
            Write-Host "$Name is ready on port $Port."
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

function Test-HttpReady($Url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

if (!(Test-Path (Join-Path $AppDir "mvnw.cmd"))) {
    Fail "backend startup file not found: $AppDir\mvnw.cmd"
}

if (!(Test-Path (Join-Path $FrontendDir "package.json"))) {
    Fail "frontend package file not found: $FrontendDir\package.json"
}

if (!(Test-CommandExists "java")) {
    Fail "Java 17+ was not found on this machine."
}

if (!(Test-CommandExists "node")) {
    Fail "Node.js was not found on this machine."
}

if (!(Test-CommandExists "npm")) {
    Fail "npm was not found on this machine."
}

if (!(Test-Path (Join-Path $FrontendDir "node_modules"))) {
    Write-Host "Installing frontend dependencies..."
    Push-Location $FrontendDir
    try {
        & npm install
        if ($LASTEXITCODE -ne 0) {
            Fail "frontend dependency installation failed."
        }
    } finally {
        Pop-Location
    }
}

Write-Host "Starting Wenzhong AI Learning Platform..."
Write-Host "Backend:  $BackendUrl"
Write-Host "Frontend: $FrontendUrl"
Write-Host ""

if (Test-PortListening $BackendPort) {
    Write-Host "Backend is already running on port $BackendPort."
} else {
    Write-Host "Starting backend service on port $BackendPort..."
    Start-Process `
        -FilePath (Join-Path $AppDir "mvnw.cmd") `
        -ArgumentList "-Dexec.skip=true", "spring-boot:run" `
        -WorkingDirectory $AppDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $AppDir "run.out.log") `
        -RedirectStandardError (Join-Path $AppDir "run.err.log")
}

if (Test-PortListening $FrontendPort) {
    Write-Host "Frontend is already running on port $FrontendPort."
} else {
    Write-Host "Starting frontend service on port $FrontendPort..."
    Start-Process `
        -FilePath "npm.cmd" `
        -ArgumentList "run dev" `
        -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $FrontendDir "vite-dev.out.log") `
        -RedirectStandardError (Join-Path $FrontendDir "vite-dev.err.log")
}

if (!(Wait-Port $BackendPort "Backend" 180)) {
    Write-Host "Backend logs:"
    Write-Host "  $(Join-Path $AppDir "run.out.log")"
    Write-Host "  $(Join-Path $AppDir "run.err.log")"
    Fail "backend did not become ready in time."
}

if (!(Wait-Port $FrontendPort "Frontend" 90)) {
    Write-Host "Frontend logs:"
    Write-Host "  $(Join-Path $FrontendDir "vite-dev.out.log")"
    Write-Host "  $(Join-Path $FrontendDir "vite-dev.err.log")"
    Fail "frontend did not become ready in time."
}

if (!(Test-HttpReady $BackendUrl)) {
    Fail "backend port is open, but $BackendUrl did not return a usable page."
}

if (!(Test-HttpReady $FrontendUrl)) {
    Fail "frontend port is open, but $FrontendUrl did not return a usable page."
}

Write-Host ""
Write-Host "Website is ready."
Write-Host "Opening $FrontendUrl"
Start-Process $FrontendUrl

exit 0
