#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 || "$1" != "--confirm-restore" ]]; then
  echo "恢复会覆盖目标数据库，必须显式确认。" >&2
  echo "用法：bash scripts/restore-postgres.sh --confirm-restore backups/onlinejudge-YYYYMMDD-HHMMSS.dump" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

INPUT="$2"
if [[ ! -s "${INPUT}" ]]; then
  echo "备份文件不存在或为空：${INPUT}" >&2
  exit 1
fi

DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"
APP_CONTAINER_ID="$(docker compose ps -q app 2>/dev/null || true)"
if [[ -n "${APP_CONTAINER_ID}" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "${APP_CONTAINER_ID}" 2>/dev/null || true)" == "true" ]]; then
  echo "应用容器仍在运行。请先停止 app 容器，确认维护窗口后再恢复数据库。" >&2
  exit 1
fi

case "${INPUT}" in
  *.dump)
    bash scripts/verify-postgres-backup.sh "${INPUT}"
    docker compose exec -T postgres pg_restore \
      -U "${DB_USER}" \
      -d "${DB_NAME}" \
      --clean \
      --if-exists \
      --exit-on-error \
      --no-owner \
      --no-privileges < "${INPUT}"
    ;;
  *.sql)
    echo "警告：正在兼容恢复历史 plain SQL；该格式没有 custom-format 目录校验能力。" >&2
    if [[ -s "${INPUT}.sha256" ]]; then
      if command -v sha256sum >/dev/null 2>&1; then
        (cd "$(dirname "${INPUT}")" && sha256sum -c "$(basename "${INPUT}.sha256")")
      else
        (cd "$(dirname "${INPUT}")" && shasum -a 256 -c "$(basename "${INPUT}.sha256")")
      fi
    fi
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" < "${INPUT}"
    ;;
  *)
    echo "不支持的备份格式：${INPUT}" >&2
    exit 1
    ;;
esac

echo "PostgreSQL 恢复完成：${INPUT}"
