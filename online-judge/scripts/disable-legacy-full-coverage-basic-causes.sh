#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"
DRY_RUN="${DRY_RUN:-0}"
SKIP_BACKUP="${SKIP_BACKUP:-0}"

if [[ "${DRY_RUN}" != "1" && "${SKIP_BACKUP}" != "1" ]]; then
  bash scripts/backup-postgres.sh
fi

if [[ "${DRY_RUN}" == "1" ]]; then
  docker compose exec -T postgres psql -U "${DB_USER}" "${DB_NAME}" <<'SQL'
\set ON_ERROR_STOP on
SELECT count(*) AS legacy_full_coverage_basic_causes
FROM ai_standard_library_items
WHERE enabled = true
  AND layer = 'BASIC_CAUSE'
  AND code LIKE 'KB\_%' ESCAPE '\'
  AND (
    category LIKE '知识点错因/%'
    OR library_version = 'standard-library-db-v2-full-coverage'
    OR name LIKE '%掌握偏差'
    OR description LIKE '学生在「%相关代码中出现概念理解、边界处理或应用迁移偏差。'
  );
SQL
  exit 0
fi

docker compose exec -T postgres psql -U "${DB_USER}" "${DB_NAME}" <<'SQL'
\set ON_ERROR_STOP on
BEGIN;

WITH updated AS (
  UPDATE ai_standard_library_items
  SET enabled = false,
      updated_at = CURRENT_TIMESTAMP
  WHERE enabled = true
    AND layer = 'BASIC_CAUSE'
    AND code LIKE 'KB\_%' ESCAPE '\'
    AND (
      category LIKE '知识点错因/%'
      OR library_version = 'standard-library-db-v2-full-coverage'
      OR name LIKE '%掌握偏差'
      OR description LIKE '学生在「%相关代码中出现概念理解、边界处理或应用迁移偏差。'
    )
  RETURNING id
)
SELECT count(*) AS disabled_legacy_full_coverage_basic_causes
FROM updated;

SELECT count(*) AS remaining_enabled_legacy_full_coverage_basic_causes
FROM ai_standard_library_items
WHERE enabled = true
  AND layer = 'BASIC_CAUSE'
  AND code LIKE 'KB\_%' ESCAPE '\'
  AND (
    category LIKE '知识点错因/%'
    OR library_version = 'standard-library-db-v2-full-coverage'
    OR name LIKE '%掌握偏差'
    OR description LIKE '学生在「%相关代码中出现概念理解、边界处理或应用迁移偏差。'
  );

COMMIT;
SQL
