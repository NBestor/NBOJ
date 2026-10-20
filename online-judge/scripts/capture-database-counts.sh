#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法：bash scripts/capture-database-counts.sh <输出 TSV 路径>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

OUTPUT="$1"
DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"
mkdir -p "$(dirname "${OUTPUT}")"
TMP="${OUTPUT}.tmp"
trap 'rm -f "${TMP}"' ERR INT TERM

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" -AtF $'\t' <<'SQL' > "${TMP}"
SELECT 'problems', count(*) FROM problems
UNION ALL SELECT 'test_cases', count(*) FROM test_cases
UNION ALL SELECT 'informatics_knowledge_nodes', count(*) FROM informatics_knowledge_nodes
UNION ALL SELECT 'ai_standard_skill_units', count(*) FROM ai_standard_skill_units
UNION ALL SELECT 'ai_standard_mistake_points', count(*) FROM ai_standard_mistake_points
UNION ALL SELECT 'ai_standard_improvement_points', count(*) FROM ai_standard_improvement_points
UNION ALL SELECT 'ai_standard_library_items', count(*) FROM ai_standard_library_items
UNION ALL SELECT 'submissions', count(*) FROM submissions
UNION ALL SELECT 'submission_analyses', count(*) FROM submission_analyses
UNION ALL SELECT 'student_ai_feedbacks', count(*) FROM student_ai_feedbacks
ORDER BY 1;
SQL

mv "${TMP}" "${OUTPUT}"
trap - ERR INT TERM
echo "数据库计数快照已保存：${OUTPUT}"
