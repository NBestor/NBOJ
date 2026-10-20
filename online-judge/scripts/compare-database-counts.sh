#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "用法：bash scripts/compare-database-counts.sh <迁移前 TSV> <迁移后 TSV>" >&2
  exit 1
fi

BEFORE="$1"
AFTER="$2"
if [[ ! -s "${BEFORE}" || ! -s "${AFTER}" ]]; then
  echo "计数快照不存在或为空。" >&2
  exit 1
fi

awk -F '\t' '
  NR == FNR { before[$1] = $2; seen_before[$1] = 1; next }
  {
    seen_after[$1] = 1
    if (!seen_before[$1]) {
      printf "迁移后出现未登记表：%s\n", $1 > "/dev/stderr"
      failed = 1
    } else if ($2 < before[$1]) {
      printf "关键表计数下降：%s %s -> %s\n", $1, before[$1], $2 > "/dev/stderr"
      failed = 1
    } else {
      printf "%s\t%s -> %s\n", $1, before[$1], $2
    }
  }
  END {
    for (table in seen_before) {
      if (!seen_after[table]) {
        printf "迁移后缺少计数：%s\n", table > "/dev/stderr"
        failed = 1
      }
    }
    exit failed
  }
' "${BEFORE}" "${AFTER}"
