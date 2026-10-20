#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${OJ_CPP17_DOCKER_IMAGE:-wenzhong-oj-cpp17-runner:13}"
BASE_IMAGE="${OJ_CPP17_BASE_IMAGE:-gcc:13-bookworm}"

docker build --build-arg "CPP17_BASE_IMAGE=${BASE_IMAGE}" -t "${IMAGE_NAME}" "${ROOT_DIR}/docker/cpp17-runner"

WORK_DIR="$(mktemp -d)"
chmod 777 "${WORK_DIR}"
cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

cat > "${WORK_DIR}/solution.cpp" <<'CPP'
#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    vector<int> values{1, 2, 3};
    cout << accumulate(values.begin(), values.end(), 0) << '\n';
    return 0;
}
CPP

RESULT="$(docker run --rm --network none --cpus 1 --memory 128m --pids-limit 64 \
  -v "${WORK_DIR}:/workspace" -w /workspace "${IMAGE_NAME}" \
  sh -lc 'g++ -std=c++17 -O2 -pipe -o solution solution.cpp && ./solution')"

if [[ "${RESULT}" != "6" ]]; then
  echo "C++17 runner smoke failed: expected 6, got ${RESULT}" >&2
  exit 1
fi

echo "C++17 runner image is ready: ${IMAGE_NAME}"
