$ErrorActionPreference = "Stop"
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$PidPath = Join-Path $RootDir "output\local-server\server.pid"
$Port = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8081 }

if (-not (Test-Path -LiteralPath $PidPath)) {
    Write-Host "[DOWN] No managed local server PID file."
    exit 1
}

$ServerPid = [int](Get-Content -LiteralPath $PidPath -Raw).Trim()
if (-not (Get-Process -Id $ServerPid -ErrorAction SilentlyContinue)) {
    Write-Host "[DOWN] PID file is stale ($ServerPid)."
    exit 1
}

try {
    $Response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health" -UseBasicParsing -TimeoutSec 3
    if ($Response.StatusCode -eq 200) {
        Write-Host "[UP] Local server is healthy (PID $ServerPid): http://localhost:$Port/app/"
        exit 0
    }
}
catch {
    Write-Host "[STARTING] Process $ServerPid exists, but the health endpoint is not ready."
    exit 2
}

Write-Host "[DOWN] Health endpoint returned an unexpected response."
exit 1
