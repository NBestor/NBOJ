$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RootDir

docker version --format "{{.Server.Version}}" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not running. Start Docker or Docker Engine first."
}

docker compose up --no-build -d --wait postgres
if ($LASTEXITCODE -ne 0) {
    throw "Postgres failed to start; application startup has been stopped."
}
& (Join-Path $PSScriptRoot "backup-postgres.ps1")

docker compose up --no-build -d
if ($LASTEXITCODE -ne 0) {
    throw "School startup requires prebuilt images. Run scripts/build-school-images.ps1 -ConfirmBuild in a controlled build environment, or load a verified release image. Production startup never builds images."
}

$Port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8081" }
Write-Host "Wenzhong OJ is starting at http://localhost:$Port/code/"
Write-Host "Teacher/system status: http://localhost:$Port/code/teacher-management"
