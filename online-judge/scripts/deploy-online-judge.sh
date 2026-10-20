#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SSH_COMMAND="deploy-online-judge --confirm-build"

if [[ $# -eq 1 && "$1" == "--confirm-build" ]]; then
  :
elif [[ $# -eq 0 && "${SSH_ORIGINAL_COMMAND:-}" == "${EXPECTED_SSH_COMMAND}" ]]; then
  # A restricted authorized_keys entry invokes this script without argv and
  # exposes the requested command through SSH_ORIGINAL_COMMAND. Normalize the
  # already verified command so a self-refresh preserves the confirmation.
  set -- "--confirm-build"
else
  echo "生产构建必须由人工明确确认。" >&2
  echo "用法：deploy-online-judge --confirm-build" >&2
  exit 2
fi

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

REPO_ROOT="${OJ_DEPLOY_REPO_ROOT:-/opt/Code}"
APP_ROOT="${REPO_ROOT}/online-judge"
LOCK_FILE="${OJ_DEPLOY_LOCK_FILE:-/var/lock/online-judge-deploy.lock}"
CADDY_CONFIG="${OJ_CADDY_CONFIG:-/etc/caddy/Caddyfile}"
ROUTE_CONTRACT="${OJ_ROUTE_CONTRACT:-${APP_ROOT}/config/route-ownership.json}"
APP_START_TIMEOUT_SECONDS="${OJ_APP_START_TIMEOUT_SECONDS:-120}"

if [[ ! -d "${REPO_ROOT}/.git" || ! -d "${APP_ROOT}" ]]; then
  echo "部署目录不存在：${APP_ROOT}" >&2
  exit 1
fi

SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"
SCRIPT_SHA_BEFORE="$(sha256sum "${SCRIPT_PATH}" | awk '{print $1}')"

if [[ "${OJ_DEPLOY_LOCK_HELD:-false}" == "true" ]]; then
  : >&9
else
  exec 9>"${LOCK_FILE}"
  flock -n 9 || {
    echo "已有部署任务正在运行。" >&2
    exit 1
  }
fi

for attempt in 1 2 3; do
  if git -C "${REPO_ROOT}" fetch origin main \
    && git -C "${REPO_ROOT}" checkout main \
    && git -C "${REPO_ROOT}" pull --ff-only origin main; then
    break
  fi
  if [[ "${attempt}" == "3" ]]; then
    echo "无法将服务器仓库快进到 origin/main。" >&2
    exit 1
  fi
  sleep 5
done

SCRIPT_SHA_AFTER="$(sha256sum "${SCRIPT_PATH}" | awk '{print $1}')"
if [[ "${OJ_DEPLOY_REFRESHED:-false}" != "true" && "${SCRIPT_SHA_BEFORE}" != "${SCRIPT_SHA_AFTER}" ]]; then
  exec env OJ_DEPLOY_REFRESHED=true OJ_DEPLOY_LOCK_HELD=true bash "${SCRIPT_PATH}" "$@"
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "生产部署需要 jq 读取统一路由所有权合同。" >&2
  exit 2
fi
if [[ ! -f "${ROUTE_CONTRACT}" ]]; then
  echo "路由所有权合同不存在：${ROUTE_CONTRACT}" >&2
  exit 2
fi
if [[ ! "${APP_START_TIMEOUT_SECONDS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "OJ_APP_START_TIMEOUT_SECONDS 必须是正整数秒数。" >&2
  exit 2
fi

PUBLIC_HOST="$(jq -er '.productionHost' "${ROUTE_CONTRACT}")"
PUBLIC_PATH="$(jq -er '.onlineJudge.publicPath' "${ROUTE_CONTRACT}")"
PUBLIC_API_PATH="$(jq -er '.onlineJudge.apiPath' "${ROUTE_CONTRACT}")"
PUBLIC_ASSETS_PATH="$(jq -er '.onlineJudge.assetsPath' "${ROUTE_CONTRACT}")"
PROXY_MARKER="$(jq -er '.onlineJudge.proxyMarker' "${ROUTE_CONTRACT}")"

if [[ ! "${PUBLIC_PATH}" =~ ^/[a-z0-9][a-z0-9/_-]*/$ ]] \
  || [[ "${PUBLIC_API_PATH}" != "${PUBLIC_PATH}"* ]] \
  || [[ "${PUBLIC_ASSETS_PATH}" != "${PUBLIC_PATH}"* ]]; then
  echo "路由所有权合同中的 Online Judge 路径无效。" >&2
  exit 2
fi

PUBLIC_PREFIX="${PUBLIC_PATH%/}"
PUBLIC_API_PREFIX="${PUBLIC_API_PATH%/}"
PUBLIC_ASSETS_PREFIX="${PUBLIC_ASSETS_PATH%/}"

cd "${APP_ROOT}"
bash scripts/build-school-images.sh --confirm-build
bash scripts/start-school.sh

docker compose ps

APP_START_DEADLINE=$((SECONDS + APP_START_TIMEOUT_SECONDS))
while true; do
  if curl --fail --silent --show-error --max-time 5 \
    "http://127.0.0.1:${SERVER_PORT:-8081}${PUBLIC_PATH}" >/dev/null \
    && curl --fail --silent --show-error --max-time 5 \
      "http://127.0.0.1:${SERVER_PORT:-8081}${PUBLIC_API_PREFIX}/system/readiness" >/dev/null; then
    break
  fi
  if ((SECONDS >= APP_START_DEADLINE)); then
    echo "应用在 ${APP_START_TIMEOUT_SECONDS} 秒内未通过 ${PUBLIC_PATH} 页面或 API 前缀探针。" >&2
    exit 1
  fi
  sleep 2
done

bash scripts/check-database-schema-readiness.sh
bash scripts/check-discipline-data-quality.sh
bash scripts/check-test-case-semantic-quality.sh

caddy validate --config "${CADDY_CONFIG}"

PROBE_DIR="$(mktemp -d)"
trap 'rm -rf -- "${PROBE_DIR}"' EXIT

for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error --max-time 5 \
    --resolve "${PUBLIC_HOST}:443:127.0.0.1" \
    --dump-header "${PROBE_DIR}/code.headers" \
    "https://${PUBLIC_HOST}${PUBLIC_PATH}" >"${PROBE_DIR}/code.html" \
    && curl --fail --silent --show-error --max-time 5 \
      --resolve "${PUBLIC_HOST}:443:127.0.0.1" \
      "https://${PUBLIC_HOST}${PUBLIC_API_PREFIX}/system/readiness" >"${PROBE_DIR}/readiness.json" \
    && curl --fail --silent --show-error --max-time 5 \
      --resolve "${PUBLIC_HOST}:443:127.0.0.1" \
      "https://${PUBLIC_HOST}${PUBLIC_PREFIX}/student" >/dev/null; then
    break
  fi
  if [[ "${attempt}" == "30" ]]; then
    echo "主域名公开入口 https://${PUBLIC_HOST}${PUBLIC_PATH} 未通过页面、API 或 SPA 探针。" >&2
    exit 1
  fi
  sleep 2
done

if ! grep -Fqi "x-proxy-source: ${PROXY_MARKER}" "${PROBE_DIR}/code.headers"; then
  echo "正式入口未由路由合同声明的 Online Judge upstream 提供。" >&2
  exit 1
fi

ASSET_PATH=""
while IFS= read -r candidate; do
  if [[ "${candidate}" == "${PUBLIC_ASSETS_PREFIX}/"* ]]; then
    ASSET_PATH="${candidate}"
    break
  fi
done < <(grep -oE '(src|href)="[^"]+"' "${PROBE_DIR}/code.html" | sed -E 's#^(src|href)="([^"]+)"$#\2#')
if [[ "${ASSET_PATH}" != "${PUBLIC_ASSETS_PREFIX}/"* ]]; then
  echo "正式页面未引用 ${PUBLIC_ASSETS_PREFIX}/ 下的构建资源。" >&2
  exit 1
fi
curl --fail --silent --show-error --max-time 5 \
  --resolve "${PUBLIC_HOST}:443:127.0.0.1" \
  "https://${PUBLIC_HOST}${ASSET_PATH}" >/dev/null

while IFS= read -r platform_path; do
  curl --silent --show-error --max-time 5 \
    --resolve "${PUBLIC_HOST}:443:127.0.0.1" \
    --dump-header "${PROBE_DIR}/platform.headers" \
    "https://${PUBLIC_HOST}${platform_path}" >/dev/null
  if grep -Fqi "x-proxy-source: ${PROXY_MARKER}" "${PROBE_DIR}/platform.headers"; then
    echo "主平台路径 ${platform_path} 被 Online Judge 接管。" >&2
    exit 1
  fi
done < <(jq -er '.platform.reservedPaths[]' "${ROUTE_CONTRACT}")

echo "人工生产部署完成：https://${PUBLIC_HOST}${PUBLIC_PATH}"
