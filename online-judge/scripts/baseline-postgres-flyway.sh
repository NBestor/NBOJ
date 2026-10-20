#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" != "--confirm-baseline" ]]; then
  echo "现有数据库基线是一次性正式操作，必须显式确认。" >&2
  echo "用法：bash scripts/baseline-postgres-flyway.sh --confirm-baseline" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"

echo "准备为现有 PostgreSQL 登记 Flyway V1 基线：database=${DB_NAME}, user=${DB_USER}, image=${OJ_APP_IMAGE:-wenzhong-oj-app:latest}"
bash scripts/check-database-schema-readiness.sh --allow-unbaselined

HISTORY_TABLE="$(docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -Atc "select to_regclass('public.flyway_schema_history') is not null")"
if [[ "${HISTORY_TABLE}" == "t" ]]; then
  echo "flyway_schema_history 已存在，拒绝重复执行基线。请运行 Schema readiness 检查。" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
AUDIT_DIR="${OJ_MIGRATION_AUDIT_DIR:-backups/migration-audit/${STAMP}-flyway-v1-baseline}"
mkdir -p "${AUDIT_DIR}"
BACKUP_PATH="$(bash scripts/backup-postgres.sh)"
printf '%s\n' "${BACKUP_PATH}" > "${AUDIT_DIR}/backup-path.txt"
bash scripts/rehearse-postgres-restore.sh "${BACKUP_PATH}" | tee "${AUDIT_DIR}/restore-rehearsal.txt"
bash scripts/capture-database-counts.sh "${AUDIT_DIR}/before.tsv"

docker compose run --rm --no-deps \
  -e FLYWAY_BASELINE_ON_MIGRATE=true \
  -e APP_DATABASE_MIGRATION_EXIT_ON_COMPLETE=true \
  -e AI_ENABLED=false \
  app --spring.main.web-application-type=none

bash scripts/check-database-schema-readiness.sh
bash scripts/capture-database-counts.sh "${AUDIT_DIR}/after.tsv"
bash scripts/compare-database-counts.sh "${AUDIT_DIR}/before.tsv" "${AUDIT_DIR}/after.tsv" | tee "${AUDIT_DIR}/count-comparison.txt"

docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -P pager=off \
  -c 'select installed_rank, version, description, type, installed_on, success from flyway_schema_history order by installed_rank;' \
  | tee "${AUDIT_DIR}/flyway-history.txt"

echo "Flyway V1 基线登记完成。审计目录：${AUDIT_DIR}"
