#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法：bash scripts/rehearse-postgres-restore.sh backups/onlinejudge-YYYYMMDD-HHMMSS.dump" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
INPUT="$1"
if [[ ! -s "${INPUT}" ]]; then
  echo "备份文件不存在或为空：${INPUT}" >&2
  exit 1
fi

NAME="oj-postgres-restore-rehearsal-$$"
IMAGE="${OJ_POSTGRES_IMAGE:-pgvector/pgvector:pg16}"
PASSWORD="restore-rehearsal-only"
cleanup() {
  docker rm -f "${NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker run -d --name "${NAME}" \
  -e POSTGRES_DB=onlinejudge \
  -e POSTGRES_USER=onlinejudge \
  -e POSTGRES_PASSWORD="${PASSWORD}" \
  "${IMAGE}" >/dev/null

for _ in {1..60}; do
  if docker exec "${NAME}" pg_isready -U onlinejudge -d onlinejudge >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "${NAME}" pg_isready -U onlinejudge -d onlinejudge >/dev/null

case "${INPUT}" in
  *.dump)
    if [[ -s "${INPUT}.sha256" ]]; then
      if command -v sha256sum >/dev/null 2>&1; then
        (cd "$(dirname "${INPUT}")" && sha256sum -c "$(basename "${INPUT}.sha256")" >/dev/null)
      else
        (cd "$(dirname "${INPUT}")" && shasum -a 256 -c "$(basename "${INPUT}.sha256")" >/dev/null)
      fi
    fi
    docker exec -i "${NAME}" pg_restore --list < "${INPUT}" >/dev/null
    docker exec -i "${NAME}" pg_restore -U onlinejudge -d onlinejudge --exit-on-error --no-owner --no-privileges < "${INPUT}"
    ;;
  *.sql)
    docker exec -i "${NAME}" psql -v ON_ERROR_STOP=1 -U onlinejudge -d onlinejudge < "${INPUT}"
    ;;
  *)
    echo "不支持的备份格式：${INPUT}" >&2
    exit 1
    ;;
esac

docker exec -i "${NAME}" psql -v ON_ERROR_STOP=1 -U onlinejudge -d onlinejudge -P pager=off <<'SQL'
SELECT count(*) AS business_table_count
FROM information_schema.tables
WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history';

SELECT 'problems' AS table_name, count(*) AS row_count FROM problems
UNION ALL SELECT 'test_cases', count(*) FROM test_cases
UNION ALL SELECT 'informatics_knowledge_nodes', count(*) FROM informatics_knowledge_nodes
UNION ALL SELECT 'ai_standard_skill_units', count(*) FROM ai_standard_skill_units
UNION ALL SELECT 'ai_standard_mistake_points', count(*) FROM ai_standard_mistake_points
UNION ALL SELECT 'ai_standard_improvement_points', count(*) FROM ai_standard_improvement_points
ORDER BY 1;

SELECT CASE
  WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'UNBASELINED_BACKUP'
  ELSE 'FLYWAY_HISTORY_PRESENT'
END AS flyway_history_status;
SQL

echo "隔离恢复演练 PASS：临时容器=${NAME}，正式数据库和 Volume 未挂载。"
