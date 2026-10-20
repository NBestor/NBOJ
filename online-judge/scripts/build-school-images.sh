#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ "${1:-}" != "--confirm-build" || "$#" -ne 1 ]]; then
  echo "Image building is intentionally separate from production startup." >&2
  echo "Run this only in a controlled build environment:" >&2
  echo "  bash scripts/build-school-images.sh --confirm-build" >&2
  exit 2
fi

if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker or Docker Engine first." >&2
  exit 1
fi

docker compose config --quiet
docker compose build app cpp17-runner

echo "School images are built. This script did not start or replace any container."
echo "Start with: bash scripts/start-school.sh"
