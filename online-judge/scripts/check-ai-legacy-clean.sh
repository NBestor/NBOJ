#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

targets=(
  "src/main/java"
  "src/test/java"
  "src/main/resources"
  ".env.example"
)

patterns=(
  "diagnosis-and-teaching"
  "DIAGNOSIS_AND_TEACHING"
  "diagnosis-judge"
  "DIAGNOSIS_JUDGE"
  "teaching-hint"
  "TEACHING_HINT"
  "AiAnalysisPayload"
  "CombinedOutput"
  "legacy-long-prompt"
  "external-runtime-mode"
  "external-single-call-prompt-version"
  "externalRuntimeMode"
  "externalSingleCallPromptVersion"
  "AI_EXTERNAL_RUNTIME_MODE"
  "AI_EXTERNAL_SINGLE_CALL_PROMPT_VERSION"
)

for pattern in "${patterns[@]}"; do
  if rg -n --fixed-strings "$pattern" "${targets[@]}"; then
    echo "Blocked: legacy AI runtime marker still appears in active code: $pattern" >&2
    exit 1
  fi
done

if find legacy-archive/ai-runtime-legacy -name '*.java' -print -quit | grep -q .; then
  echo "Blocked: legacy archive must not contain compilable .java files." >&2
  find legacy-archive/ai-runtime-legacy -name '*.java' >&2
  exit 1
fi

for required in \
  legacy-archive/ai-runtime-legacy/README.md \
  legacy-archive/ai-runtime-legacy/inventory.md \
  legacy-archive/ai-runtime-legacy/relationship-map.md
do
  if [[ ! -f "$required" ]]; then
    echo "Blocked: missing required legacy archive file: $required" >&2
    exit 1
  fi
done

if ! rg -q "ARCHIVED_ONLY" legacy-archive/ai-runtime-legacy/inventory.md; then
  echo "Blocked: inventory.md must mark legacy assets as ARCHIVED_ONLY." >&2
  exit 1
fi

echo "AI legacy runtime cleanup check passed."
