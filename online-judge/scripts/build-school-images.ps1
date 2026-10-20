param(
    [switch]$ConfirmBuild
)

$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RootDir

if (-not $ConfirmBuild) {
    throw "Image building is separate from production startup. Run this only in a controlled build environment with -ConfirmBuild."
}

docker version --format "{{.Server.Version}}" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not running. Start Docker or Docker Engine first."
}

docker compose config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose configuration is invalid."
}

docker compose build app cpp17-runner
if ($LASTEXITCODE -ne 0) {
    throw "School image build failed."
}

Write-Host "School images are built. This script did not start or replace any container."
Write-Host "Start with: powershell -ExecutionPolicy Bypass -File scripts/start-school.ps1"
