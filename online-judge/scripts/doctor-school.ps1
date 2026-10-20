$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $RootDir

$Warnings = 0
$CheckTimeoutSeconds = if ($env:OJ_DOCTOR_TIMEOUT_SECONDS) { [int]$env:OJ_DOCTOR_TIMEOUT_SECONDS } else { 20 }

function Ok($Message) {
    Write-Host "[OK] $Message"
}

function Warn($Message) {
    Write-Host "[WARN] $Message"
    $script:Warnings += 1
}

function Fail($Message) {
    Write-Error "[FAIL] $Message"
    exit 1
}

function Check-RequiredEnv($Name, $Label) {
    $Value = Get-EnvValue $Name ""
    if (-not $Value) {
        Fail "$Label is not configured. Set $Name in .env before school deployment."
    }
    if ($Value.StartsWith("change-this") -or $Value.StartsWith("dev-")) {
        Fail "$Label still uses a placeholder value. Replace $Name with a real secret."
    }
    Ok "$Label is configured"
}

function Get-EnvValue($Name, $DefaultValue) {
    $Value = [Environment]::GetEnvironmentVariable($Name)
    if (-not $Value -and (Test-Path ".env")) {
        $Line = Get-Content ".env" | Where-Object {
            $_ -notmatch "^\s*#" -and $_ -match "^$([regex]::Escape($Name))="
        } | Select-Object -First 1
        if ($Line) {
            $Value = $Line.Substring($Line.IndexOf("=") + 1)
        }
    }
    if ($Value) { return $Value }
    return $DefaultValue
}

function Check-Command($Name, $VersionCommand) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Warn "$Name is not on PATH"
        return
    }
    try {
        $Version = Invoke-Expression $VersionCommand 2>$null | Select-Object -First 1
        if ($Version) {
            Ok $Version
        }
        else {
            Ok "$Name found"
        }
    }
    catch {
        Ok "$Name found"
    }
}

function Get-RegistryHost($Image) {
    if ($Image -notlike "*/*") {
        return "registry-1.docker.io"
    }
    $FirstPart = $Image.Split("/")[0]
    if ($FirstPart.Contains(".") -or $FirstPart.Contains(":") -or $FirstPart -eq "localhost") {
        return $FirstPart
    }
    return "registry-1.docker.io"
}

function Check-ImageSource($Label, $Image) {
    docker image inspect $Image *> $null
    if ($LASTEXITCODE -eq 0) {
        Ok "$Label image exists locally: $Image"
        return
    }

    $Registry = Get-RegistryHost $Image
    try {
        $Response = Invoke-WebRequest -Uri "https://$Registry/v2/" -Method Get -TimeoutSec $CheckTimeoutSeconds -UseBasicParsing
        if ($Response.StatusCode -in @(200, 401, 403)) {
            Ok "$Label registry is reachable: $Registry"
        }
        else {
            Warn "$Label registry returned HTTP $($Response.StatusCode): $Registry"
        }
    }
    catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        if ($StatusCode -in @(401, 403)) {
            Ok "$Label registry is reachable: $Registry"
        }
        else {
            Warn "$Label registry is not reachable now: $Registry"
        }
    }
}

Check-Command "java" "java -version"
Check-Command "node" "node --version"
Check-Command "npm" "npm --version"

$AppProfile = Get-EnvValue "APP_PROFILE" "school"
if ($AppProfile -ne "school") {
    Fail "APP_PROFILE must be school for classroom deployment."
}
Ok "APP_PROFILE=school"

$ExecutorMode = Get-EnvValue "EXECUTOR_MODE" "docker"
if ($ExecutorMode -ne "docker") {
    Fail "EXECUTOR_MODE must be docker for classroom deployment."
}
Ok "EXECUTOR_MODE=docker"

Check-RequiredEnv "POSTGRES_PASSWORD" "Postgres password"
Check-RequiredEnv "TEACHER_PASSWORD" "Teacher password"
Check-RequiredEnv "TEACHER_SESSION_SECRET" "Teacher session secret"
Check-RequiredEnv "STUDENT_TOKEN_SECRET" "Student token secret"

docker version --format "{{.Server.Version}}" *> $null
if ($LASTEXITCODE -ne 0) {
    Fail "Docker daemon is not available. Start Docker Desktop, OrbStack, or Docker Engine first."
}
Ok "Docker daemon: $(docker version --format '{{.Server.Version}}')"

docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Fail "docker compose is not available"
}
Ok "$(docker compose version)"

docker compose config *> $null
if ($LASTEXITCODE -ne 0) {
    Fail "docker compose config is invalid"
}
Ok "docker compose config is valid"

$RunnerImage = Get-EnvValue "OJ_CPP17_DOCKER_IMAGE" "wenzhong-oj-cpp17-runner:13"
$Cpp17BaseImage = Get-EnvValue "OJ_CPP17_BASE_IMAGE" "gcc:13-bookworm"
$Python3Image = Get-EnvValue "OJ_PYTHON3_DOCKER_IMAGE" "python:3.12-slim"
$NodeBaseImage = Get-EnvValue "OJ_NODE_BASE_IMAGE" "node:24-bookworm-slim"
$MavenBaseImage = Get-EnvValue "OJ_MAVEN_BASE_IMAGE" "maven:3.9.9-eclipse-temurin-17"
$JreBaseImage = Get-EnvValue "OJ_JRE_BASE_IMAGE" "eclipse-temurin:17-jre"
$DockerCliImage = Get-EnvValue "OJ_DOCKER_CLI_IMAGE" "docker:29-cli"
$PostgresImage = Get-EnvValue "OJ_POSTGRES_IMAGE" "postgres:16-alpine"

docker image inspect $RunnerImage *> $null
if ($LASTEXITCODE -eq 0) {
    Ok "C++17 runner image exists locally: $RunnerImage"
}
else {
    Warn "C++17 runner image is not built yet: $RunnerImage"
}

Check-ImageSource "C++17 base" $Cpp17BaseImage
Check-ImageSource "Python 3 runner" $Python3Image
Check-ImageSource "frontend build base" $NodeBaseImage
Check-ImageSource "backend build base" $MavenBaseImage
Check-ImageSource "runtime JRE base" $JreBaseImage
Check-ImageSource "Docker CLI base" $DockerCliImage
Check-ImageSource "Postgres" $PostgresImage

if ($Warnings -gt 0) {
    Write-Host ""
    Write-Host "Finished with $Warnings warning(s). Fix image registry access before the first school deployment build."
}
else {
    Write-Host ""
    Write-Host "School deployment preflight passed."
}
