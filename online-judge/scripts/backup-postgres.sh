#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

BACKUP_DIR="${OJ_BACKUP_DIR:-backups}"
mkdir -p "${BACKUP_DIR}"

STAMP="$(date +%Y%m%d-%H%M%S)"
DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"
OUT="${BACKUP_DIR}/onlinejudge-${STAMP}.dump"
TMP="${OUT}.tmp"
CHECKSUM="${OUT}.sha256"
META="${OUT}.meta"

cleanup_partial() {
  rm -f "${TMP}" "${OUT}" "${CHECKSUM}" "${META}"
}
trap cleanup_partial ERR INT TERM

CONTAINER_ID="$(docker compose ps -q postgres)"
if [[ -z "${CONTAINER_ID}" ]] || [[ "$(docker inspect -f '{{.State.Running}}' "${CONTAINER_ID}" 2>/dev/null || true)" != "true" ]]; then
  echo "Postgres 容器未运行，拒绝生成不完整备份。" >&2
  exit 1
fi

POSTGRES_VERSION="$(docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -Atc 'select version()')"
CONTAINER_IMAGE="$(docker inspect -f '{{.Config.Image}}' "${CONTAINER_ID}")"

docker compose exec -T postgres pg_dump \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --format=custom \
  --compress=6 \
  --no-owner \
  --no-privileges > "${TMP}"

if [[ ! -s "${TMP}" ]]; then
  echo "备份归档为空，拒绝继续。" >&2
  exit 1
fi
mv "${TMP}" "${OUT}"

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "${OUT}")" && sha256sum "$(basename "${OUT}")" > "$(basename "${CHECKSUM}")")
else
  (cd "$(dirname "${OUT}")" && shasum -a 256 "$(basename "${OUT}")" > "$(basename "${CHECKSUM}")")
fi

{
  printf 'created_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'database=%s\n' "${DB_NAME}"
  printf 'database_user=%s\n' "${DB_USER}"
  printf 'postgres_container=%s\n' "${CONTAINER_ID}"
  printf 'postgres_image=%s\n' "${CONTAINER_IMAGE}"
  printf 'postgres_version=%s\n' "${POSTGRES_VERSION}"
  printf 'format=custom\n'
} > "${META}"

bash scripts/verify-postgres-backup.sh "${OUT}" >/dev/null
trap - ERR INT TERM

echo "PostgreSQL 备份已生成并验证：${OUT}" >&2
printf '%s\n' "${OUT}"
