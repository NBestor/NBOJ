#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"

if ! docker compose ps postgres >/dev/null 2>&1; then
  echo "Postgres service is not available through docker compose." >&2
  exit 1
fi

docker compose exec -T postgres psql -U "${DB_USER}" "${DB_NAME}" <<'SQL'
\set ON_ERROR_STOP on

SELECT current_database() AS database_name, current_user AS database_user, version() AS postgres_version;

SELECT 'problems' AS table_name, count(*) AS row_count FROM problems
UNION ALL
SELECT 'test_cases', count(*) FROM test_cases
UNION ALL
SELECT 'informatics_knowledge_nodes', count(*) FROM informatics_knowledge_nodes
UNION ALL
SELECT 'ai_standard_skill_units', count(*) FROM ai_standard_skill_units
UNION ALL
SELECT 'ai_standard_mistake_points', count(*) FROM ai_standard_mistake_points
UNION ALL
SELECT 'ai_standard_improvement_points', count(*) FROM ai_standard_improvement_points
UNION ALL
SELECT 'ai_standard_application_scenarios', count(*) FROM ai_standard_application_scenarios
UNION ALL
SELECT 'ai_standard_library_items', count(*) FROM ai_standard_library_items
ORDER BY table_name;

SELECT count(*) AS enabled_legacy_full_coverage_basic_causes
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

SELECT code, name, path
FROM informatics_knowledge_nodes
WHERE enabled = true
  AND code IN ('BASIC.BRANCH.IF.单分支判断', 'BASIC.BRANCH.IF.多分支链')
ORDER BY code;

SELECT code, name, primary_knowledge_node_code
FROM ai_standard_skill_units
WHERE enabled = true
ORDER BY code
LIMIT 10;
SQL
