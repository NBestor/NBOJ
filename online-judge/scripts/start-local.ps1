param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RuntimeDir = Join-Path $RootDir "output\local-server"
$PidPath = Join-Path $RuntimeDir "server.pid"
$StdoutPath = Join-Path $RuntimeDir "server.out.log"
$StderrPath = Join-Path $RuntimeDir "server.err.log"
$Port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8081 }
$HealthUrl = "http://127.0.0.1:$Port/actuator/health"

function Test-LocalHealth {
    try {
        $Response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2
        return $Response.StatusCode -eq 200
    }
    catch {
        return $false
    }
}

New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null

if (Test-Path -LiteralPath $PidPath) {
    $ExistingPid = [int](Get-Content -LiteralPath $PidPath -Raw).Trim()
    $ExistingProcess = Get-Process -Id $ExistingPid -ErrorAction SilentlyContinue
    if ($ExistingProcess -and (Test-LocalHealth)) {
        Write-Host "[OK] Local server is already running (PID $ExistingPid): http://localhost:$Port/app/"
        exit 0
    }
    if ($ExistingProcess) {
        throw "PID $ExistingPid is still running but the health check failed. Run scripts/stop-local.ps1 before restarting."
    }
    Remove-Item -LiteralPath $PidPath -Force
}

$Listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Listener) {
    throw "Port $Port is already occupied by PID $($Listener.OwningProcess). It was not started by scripts/start-local.ps1."
}

Set-Location $RootDir
if (-not $SkipBuild) {
    Push-Location (Join-Path $RootDir "frontend")
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }
    }
    finally {
        Pop-Location
    }

    & (Join-Path $RootDir "mvnw.cmd") "-DskipTests" "-Dskip.frontend=true" package
    if ($LASTEXITCODE -ne 0) { throw "Backend package failed." }
}

$Jar = Get-ChildItem -LiteralPath (Join-Path $RootDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*.original*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $Jar) {
    throw "No runnable jar was found. Run without -SkipBuild first."
}

$Java = (Get-Command java -ErrorAction Stop).Source
$Process = Start-Process -FilePath $Java `
    -ArgumentList @("-jar", "`"$($Jar.FullName)`"") `
    -WorkingDirectory $RootDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $StdoutPath `
    -RedirectStandardError $StderrPath `
    -PassThru
[IO.File]::WriteAllText($PidPath, [string]$Process.Id)

$Deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $Deadline) {
    if ($Process.HasExited) {
        Remove-Item -LiteralPath $PidPath -Force -ErrorAction SilentlyContinue
        throw "Local server exited during startup. Check $StderrPath and $StdoutPath."
    }
    if (Test-LocalHealth) {
        Write-Host "[OK] Local server started (PID $($Process.Id)): http://localhost:$Port/app/"
        Write-Host "     Logs: $StdoutPath"
        exit 0
    }
    Start-Sleep -Milliseconds 500
}

Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $PidPath -Force -ErrorAction SilentlyContinue
throw "Local server did not become healthy within 90 seconds. Check $StderrPath and $StdoutPath."
