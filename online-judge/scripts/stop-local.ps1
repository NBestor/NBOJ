$ErrorActionPreference = "Stop"
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RuntimeDir = Join-Path $RootDir "output\local-server"
$PidPath = Join-Path $RuntimeDir "server.pid"

if (-not (Test-Path -LiteralPath $PidPath)) {
    Write-Host "[OK] No managed local server is running."
    exit 0
}

$ServerPid = [int](Get-Content -LiteralPath $PidPath -Raw).Trim()
$Process = Get-CimInstance Win32_Process -Filter "ProcessId=$ServerPid" -ErrorAction SilentlyContinue
if (-not $Process) {
    Remove-Item -LiteralPath $PidPath -Force
    Write-Host "[OK] Removed a stale local server PID file."
    exit 0
}

$CommandLine = [string]$Process.CommandLine
$ExpectedRoot = [IO.Path]::GetFullPath($RootDir)
if ($Process.Name -ne "java.exe" -or $CommandLine -notlike "*-jar*" -or $CommandLine -notlike "*$ExpectedRoot*" -or $CommandLine -notlike "*nboj-*.jar*") {
    throw "PID $ServerPid does not match this workspace's Java server and will not be stopped."
}

Stop-Process -Id $ServerPid
try {
    Wait-Process -Id $ServerPid -Timeout 15 -ErrorAction Stop
}
catch {
    Stop-Process -Id $ServerPid -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $PidPath -Force
Write-Host "[OK] Local server stopped (PID $ServerPid)."
