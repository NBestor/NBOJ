#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法：bash scripts/verify-postgres-backup.sh backups/onlinejudge-YYYYMMDD-HHMMSS.dump" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

INPUT="$1"
if [[ ! -s "${INPUT}" ]]; then
  echo "备份文件不存在或为空：${INPUT}" >&2
  exit 1
fi

CHECKSUM="${INPUT}.sha256"
META="${INPUT}.meta"
if [[ ! -s "${CHECKSUM}" || ! -s "${META}" ]]; then
  echo "备份缺少校验和或元数据：${INPUT}" >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "${INPUT}")" && sha256sum -c "$(basename "${CHECKSUM}")" >/dev/null)
else
  (cd "$(dirname "${INPUT}")" && shasum -a 256 -c "$(basename "${CHECKSUM}")" >/dev/null)
fi

if ! docker compose exec -T postgres pg_restore --list < "${INPUT}" >/dev/null; then
  echo "pg_restore 无法读取备份目录：${INPUT}" >&2
  exit 1
fi

if ! grep -q '^format=custom$' "${META}"; then
  echo "备份元数据格式不正确：${META}" >&2
  exit 1
fi

echo "PostgreSQL 备份校验通过：${INPUT}"
