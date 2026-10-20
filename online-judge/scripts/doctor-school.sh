#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

WARNINGS=0
CHECK_TIMEOUT_SECONDS="${OJ_DOCTOR_TIMEOUT_SECONDS:-20}"

ok() {
  printf '[OK] %s\n' "$1"
}

warn() {
  printf '[WARN] %s\n' "$1"
  WARNINGS=$((WARNINGS + 1))
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

check_required_env() {
  local key="$1"
  local label="$2"
  local value
  value="$(env_value "${key}" "")"
  if [[ -z "${value}" ]]; then
    fail "${label} is not configured. Set ${key} in .env before school deployment."
  fi
  if [[ "${value}" == change-this* || "${value}" == dev-* ]]; then
    fail "${label} still uses a placeholder value. Replace ${key} with a real secret."
  fi
  ok "${label} is configured"
}

env_value() {
  local key="$1"
  local default_value="$2"
  local value="${!key:-}"
  if [[ -z "${value}" && -f .env ]]; then
    value="$(awk -F= -v key="${key}" '
      $0 !~ /^[[:space:]]*#/ && $1 == key {
        sub(/^[^=]*=/, "")
        print
        exit
      }
    ' .env)"
  fi
  printf '%s' "${value:-${default_value}}"
}

check_command() {
  local name="$1"
  local version_command="$2"
  if ! command -v "${name}" >/dev/null 2>&1; then
    warn "${name} is not on PATH"
    return
  fi
  local version_output
  version_output="$(bash -lc "${version_command}" 2>&1 | head -n 1 || true)"
  ok "${version_output:-${name} found}"
}

check_image_source() {
  local label="$1"
  local image="$2"
  local output_file
  local error_file
  local registry
  output_file="$(mktemp)"
  error_file="$(mktemp)"
  registry="registry-1.docker.io"
  if [[ "${image}" == */* ]]; then
    local first_part="${image%%/*}"
    if [[ "${first_part}" == *.* || "${first_part}" == *:* || "${first_part}" == "localhost" ]]; then
      registry="${first_part}"
    fi
  fi
  if docker image inspect "${image}" >/dev/null 2>&1; then
    ok "${label} image exists locally: ${image}"
  elif command -v curl >/dev/null 2>&1; then
    local http_code
    http_code="$(curl --silent --show-error --output "${output_file}" --write-out '%{http_code}' \
      --connect-timeout "${CHECK_TIMEOUT_SECONDS}" --max-time "${CHECK_TIMEOUT_SECONDS}" \
      "https://${registry}/v2/" 2>"${error_file}" || true)"
    if [[ "${http_code}" == "200" || "${http_code}" == "401" || "${http_code}" == "403" ]]; then
      ok "${label} registry is reachable: ${registry}"
    else
      warn "${label} registry is not reachable now: ${registry}"
      sed -n '1,2p' "${error_file}" | sed 's/^/       /'
    fi
  else
    warn "curl is not on PATH; skipped ${label} registry check"
  fi
  rm -f "${output_file}" "${error_file}"
}

check_command java "java -version"
check_command node "node --version"
check_command npm "npm --version"

APP_PROFILE_VALUE="$(env_value APP_PROFILE school)"
if [[ "${APP_PROFILE_VALUE}" != "school" ]]; then
  fail "APP_PROFILE must be school for classroom deployment."
fi
ok "APP_PROFILE=school"

EXECUTOR_MODE_VALUE="$(env_value EXECUTOR_MODE docker)"
if [[ "${EXECUTOR_MODE_VALUE}" != "docker" ]]; then
  fail "EXECUTOR_MODE must be docker for classroom deployment."
fi
ok "EXECUTOR_MODE=docker"

check_required_env POSTGRES_PASSWORD "Postgres password"
check_required_env TEACHER_PASSWORD "Teacher password"
check_required_env TEACHER_SESSION_SECRET "Teacher session secret"
check_required_env STUDENT_TOKEN_SECRET "Student token secret"

if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  fail "Docker daemon is not available. Start Docker Desktop, OrbStack, or Docker Engine first."
fi
ok "Docker daemon: $(docker version --format '{{.Server.Version}}')"

if ! docker compose version >/dev/null 2>&1; then
  fail "docker compose is not available"
fi
ok "$(docker compose version)"

if docker compose config >/dev/null; then
  ok "docker compose config is valid"
else
  fail "docker compose config is invalid"
fi

RUNNER_IMAGE="$(env_value OJ_CPP17_DOCKER_IMAGE wenzhong-oj-cpp17-runner:13)"
CPP17_BASE_IMAGE="$(env_value OJ_CPP17_BASE_IMAGE gcc:13-bookworm)"
PYTHON3_IMAGE="$(env_value OJ_PYTHON3_DOCKER_IMAGE python:3.12-slim)"
NODE_BASE_IMAGE="$(env_value OJ_NODE_BASE_IMAGE node:24-bookworm-slim)"
MAVEN_BASE_IMAGE="$(env_value OJ_MAVEN_BASE_IMAGE maven:3.9.9-eclipse-temurin-17)"
JRE_BASE_IMAGE="$(env_value OJ_JRE_BASE_IMAGE eclipse-temurin:17-jre)"
DOCKER_CLI_IMAGE="$(env_value OJ_DOCKER_CLI_IMAGE docker:29-cli)"
POSTGRES_IMAGE="$(env_value OJ_POSTGRES_IMAGE postgres:16-alpine)"

if docker image inspect "${RUNNER_IMAGE}" >/dev/null 2>&1; then
  ok "C++17 runner image exists locally: ${RUNNER_IMAGE}"
else
  warn "C++17 runner image is not built yet: ${RUNNER_IMAGE}"
fi

check_image_source "C++17 base" "${CPP17_BASE_IMAGE}"
check_image_source "Python 3 runner" "${PYTHON3_IMAGE}"
check_image_source "frontend build base" "${NODE_BASE_IMAGE}"
check_image_source "backend build base" "${MAVEN_BASE_IMAGE}"
check_image_source "runtime JRE base" "${JRE_BASE_IMAGE}"
check_image_source "Docker CLI base" "${DOCKER_CLI_IMAGE}"
check_image_source "Postgres" "${POSTGRES_IMAGE}"

if (( WARNINGS > 0 )); then
  printf '\nFinished with %d warning(s). Fix image registry access before the first school deployment build.\n' "${WARNINGS}"
else
  printf '\nSchool deployment preflight passed.\n'
fi
