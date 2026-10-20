#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

NAME="oj-postgres-migration-test-$$"
PORT="${OJ_MIGRATION_TEST_PORT:-55433}"
IMAGE="${OJ_POSTGRES_IMAGE:-public.ecr.aws/docker/library/postgres:16}"
PASSWORD="flyway-integration-test"
JAR="target/nboj-1.0.0.jar"
LOG_DIR="target/flyway-test-logs"
mkdir -p "${LOG_DIR}"

cleanup() {
  docker rm -f "${NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

if [[ "${OJ_SKIP_MIGRATION_TEST_BUILD:-false}" != "true" ]]; then
  ./mvnw -q -Dskip.frontend=true -DskipTests package
elif [[ ! -f "${JAR}" ]]; then
  echo "OJ_SKIP_MIGRATION_TEST_BUILD=true，但缺少 ${JAR}。" >&2
  exit 1
fi

docker run -d --name "${NAME}" \
  -e POSTGRES_DB=onlinejudge \
  -e POSTGRES_USER=onlinejudge \
  -e POSTGRES_PASSWORD="${PASSWORD}" \
  -p "${PORT}:5432" \
  "${IMAGE}" >/dev/null

for _ in {1..60}; do
  if docker exec "${NAME}" pg_isready -U onlinejudge -d onlinejudge >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "${NAME}" pg_isready -U onlinejudge -d onlinejudge >/dev/null

run_app() {
  local database="$1"
  local baseline="$2"
  local log_file="$3"
  APP_PROFILE=school \
  POSTGRES_JDBC_URL="jdbc:postgresql://127.0.0.1:${PORT}/${database}" \
  POSTGRES_USER=onlinejudge \
  POSTGRES_PASSWORD="${PASSWORD}" \
  FLYWAY_BASELINE_ON_MIGRATE="${baseline}" \
  APP_DATABASE_MIGRATION_EXIT_ON_COMPLETE=true \
  AI_ENABLED=false \
  EXECUTOR_MODE=local \
    java -jar "${JAR}" --spring.main.web-application-type=none > "${log_file}" 2>&1
}

echo "[1/4] 验证空 PostgreSQL 执行完整迁移链并通过 Hibernate validate"
run_app onlinejudge false "${LOG_DIR}/empty-database.log"
VERSION="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc "select version from flyway_schema_history where success order by installed_rank desc limit 1")"
[[ "${VERSION}" == "20" ]]
TABLES="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc "select count(*) from pg_tables where schemaname='public' and tablename <> 'flyway_schema_history'")"
[[ "${TABLES}" == "43" ]]
MAPPING_TABLE="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc "select to_regclass('public.informatics_discipline_scope_mappings') is not null")"
[[ "${MAPPING_TABLE}" == "t" ]]
SCENARIO_ROWS="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc "select count(*) from ai_standard_application_scenarios where enabled")"
[[ "${SCENARIO_ROWS}" == "0" ]]
SEMANTIC_ROWS="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc "select count(*) from test_cases where semantic_code is not null")"
[[ "${SEMANTIC_ROWS}" == "0" ]]
OJ_POSTGRES_CONTAINER="${NAME}" bash scripts/check-test-case-semantic-quality.sh

echo "[2/4] 验证重复启动不重复执行迁移"
HISTORY_BEFORE="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc 'select count(*) from flyway_schema_history')"
run_app onlinejudge false "${LOG_DIR}/repeat-migration.log"
HISTORY_AFTER="$(docker exec "${NAME}" psql -U onlinejudge -d onlinejudge -Atc 'select count(*) from flyway_schema_history')"
[[ "${HISTORY_BEFORE}" == "${HISTORY_AFTER}" ]]

echo "[3/4] 验证非空旧库默认拒绝、显式授权后只登记基线"
docker exec "${NAME}" createdb -U onlinejudge -O onlinejudge legacy
docker exec -i "${NAME}" psql -v ON_ERROR_STOP=1 -U onlinejudge -d legacy < src/main/resources/db/migration/V1__baseline_schema.sql >/dev/null
if run_app legacy false "${LOG_DIR}/legacy-without-baseline.log"; then
  echo "非空旧库在无基线授权时意外启动成功。" >&2
  exit 1
fi
run_app legacy true "${LOG_DIR}/legacy-baseline.log"
BASELINE_TYPE="$(docker exec "${NAME}" psql -U onlinejudge -d legacy -Atc "select type from flyway_schema_history where version='1' and success")"
[[ "${BASELINE_TYPE}" == "BASELINE" ]]
LEGACY_VERSION="$(docker exec "${NAME}" psql -U onlinejudge -d legacy -Atc "select version from flyway_schema_history where success order by installed_rank desc limit 1")"
[[ "${LEGACY_VERSION}" == "20" ]]

echo "[4/4] 验证 Schema 漂移阻止应用启动"
docker exec "${NAME}" psql -v ON_ERROR_STOP=1 -U onlinejudge -d legacy -c 'alter table problems drop column title;' >/dev/null
if run_app legacy false "${LOG_DIR}/schema-drift.log"; then
  echo "缺少关键列时应用意外启动成功。" >&2
  exit 1
fi
grep -Eq 'Schema-validation|missing column|title' "${LOG_DIR}/schema-drift.log"

echo "PostgreSQL Flyway 集成验证 PASS：V1-V20 空库、幂等、显式基线、漂移阻断均通过。"
