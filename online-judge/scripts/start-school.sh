#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker or OrbStack first." >&2
  exit 1
fi

if ! docker compose up --no-build -d --wait postgres; then
  echo "Postgres failed to start; application startup has been stopped." >&2
  exit 1
fi
bash scripts/backup-postgres.sh

if ! docker compose up --no-build -d; then
  echo "School startup requires prebuilt images. Build them in a controlled environment with:" >&2
  echo "  bash scripts/build-school-images.sh --confirm-build" >&2
  echo "Or load a verified release image before retrying. Production startup never builds images." >&2
  exit 1
fi
echo "Wenzhong OJ is starting at http://localhost:${SERVER_PORT:-8081}/code/"
echo "Teacher/system status: http://localhost:${SERVER_PORT:-8081}/code/teacher-management"
