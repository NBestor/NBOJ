#!/usr/bin/env bash
set -euo pipefail

MODE="gate"
if [[ $# -gt 1 ]]; then
  echo "用法：bash scripts/check-diagnosis-evidence-quality.sh [--report-only]" >&2
  exit 2
fi
if [[ $# -eq 1 ]]; then
  if [[ "$1" != "--report-only" ]]; then
    echo "未知参数：$1" >&2
    exit 2
  fi
  MODE="report"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"
TMP="$(mktemp)"
trap 'rm -f "${TMP}"' EXIT INT TERM

if [[ -n "${OJ_POSTGRES_CONTAINER:-}" ]]; then
  PSQL=(docker exec -i "${OJ_POSTGRES_CONTAINER}" psql)
else
  PSQL=(docker compose exec -T postgres psql)
fi

"${PSQL[@]}" -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" -AtF $'\t' > "${TMP}" <<'SQL'
WITH
analysis_advice_counts AS (
    SELECT a.id AS analysis_id,
           jsonb_array_length(COALESCE(a.report_json::jsonb -> 'basicLayerAdvice', '[]'::jsonb))
         + jsonb_array_length(COALESCE(a.report_json::jsonb -> 'improvementLayerAdvice', '[]'::jsonb)) AS advice_count
    FROM submission_analyses a
    WHERE a.report_json IS NOT NULL AND btrim(a.report_json) <> ''
),
fact_counts AS (
    SELECT analysis_id, count(*) AS fact_count
    FROM submission_diagnosis_facts
    GROUP BY analysis_id
),
projection_mismatches AS (
    SELECT a.analysis_id,
           a.advice_count,
           COALESCE(f.fact_count, 0) AS fact_count
    FROM analysis_advice_counts a
    LEFT JOIN fact_counts f ON f.analysis_id = a.analysis_id
    WHERE a.advice_count > 0
      AND a.advice_count <> COALESCE(f.fact_count, 0)
),
formal_anchor_validity AS (
    SELECT f.id,
           CASE
               WHEN f.fact_type = 'REPAIR'
                 THEN m.code IS NOT NULL
                  AND s.code IS NOT NULL
                  AND m.skill_unit_code = f.skill_unit_id
               WHEN f.fact_type = 'IMPROVEMENT'
                 THEN i.code IS NOT NULL
                  AND (f.skill_unit_id IS NULL
                    OR (s.code IS NOT NULL AND i.skill_unit_code = f.skill_unit_id))
               ELSE COALESCE(m.code, i.code, s.code) IS NOT NULL
           END AS valid
    FROM submission_diagnosis_facts f
    LEFT JOIN ai_standard_skill_units s
      ON s.code = f.skill_unit_id AND s.enabled = true
    LEFT JOIN ai_standard_mistake_points m
      ON m.code = f.mistake_point_id AND m.enabled = true
    LEFT JOIN ai_standard_improvement_points i
      ON i.code = f.improvement_point_id AND i.enabled = true
    WHERE f.knowledge_path_status = 'FORMAL'
),
raw_metrics(metric, severity, value, direction, target, definition) AS (
    SELECT 'analysis_projection_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '包含建议但事实数与建议数不一致的分析数量'
    FROM projection_mismatches
    UNION ALL
    SELECT 'analysis_projection_missing_rows', 'BLOCKER',
           COALESCE(sum(abs(advice_count - fact_count)), 0), 'MAX', 0,
           '包含建议分析尚未投影或多投影的事实行差异'
    FROM projection_mismatches
    UNION ALL
    SELECT 'provisional_fact_missing_code', 'BLOCKER', count(*), 'MAX', 0,
           'PROVISIONAL 事实缺少稳定 provisional_node_code'
    FROM submission_diagnosis_facts
    WHERE knowledge_path_status = 'PROVISIONAL'
      AND (provisional_node_code IS NULL OR btrim(provisional_node_code) = '')
    UNION ALL
    SELECT 'provisional_code_missing_candidate', 'BLOCKER', count(*), 'MAX', 0,
           '临时候选事实 code 无法解析到唯一成长候选'
    FROM (
        SELECT f.id
        FROM submission_diagnosis_facts f
        LEFT JOIN ai_standard_library_growth_candidates g
          ON g.suggested_code = f.provisional_node_code
        WHERE f.knowledge_path_status = 'PROVISIONAL'
        GROUP BY f.id
        HAVING count(g.id) <> 1
    ) invalid
    UNION ALL
    SELECT 'provisional_candidate_invalid_parent', 'BLOCKER', count(*), 'MAX', 0,
           '临时候选事实无法追溯到启用父知识点'
    FROM submission_diagnosis_facts f
    JOIN ai_standard_library_growth_candidates g
      ON g.suggested_code = f.provisional_node_code
    LEFT JOIN informatics_knowledge_nodes n
      ON n.code = g.parent_knowledge_node_code AND n.enabled = true
    WHERE f.knowledge_path_status = 'PROVISIONAL'
      AND n.code IS NULL
    UNION ALL
    SELECT 'provisional_identity_source_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '临时候选事实没有使用 PROVISIONAL_ID 稳定问题身份'
    FROM submission_diagnosis_facts
    WHERE knowledge_path_status = 'PROVISIONAL'
      AND (point_key_source <> 'PROVISIONAL_ID'
        OR normalized_point_key NOT LIKE 'provisional:point-key-v1:%')
    UNION ALL
    SELECT 'non_provisional_with_provisional_code', 'BLOCKER', count(*), 'MAX', 0,
           '非 PROVISIONAL 事实错误保留 provisional_node_code'
    FROM submission_diagnosis_facts
    WHERE knowledge_path_status <> 'PROVISIONAL'
      AND provisional_node_code IS NOT NULL
    UNION ALL
    SELECT 'formal_anchor_invalid', 'BLOCKER', count(*), 'MAX', 0,
           'FORMAL 事实缺少启用正式条目或能力归属不一致'
    FROM formal_anchor_validity
    WHERE NOT valid
    UNION ALL
    SELECT 'library_fit_invalid', 'BLOCKER', count(*), 'MAX', 0,
           '事实 library_fit 为空、UNKNOWN 或超出 HIT/PARTIAL/MISS'
    FROM submission_diagnosis_facts
    WHERE library_fit IS NULL OR library_fit NOT IN ('HIT', 'PARTIAL', 'MISS')
    UNION ALL
    SELECT 'empty_identifier_values', 'BLOCKER', count(*), 'MAX', 0,
           '事实 ID 字段残留空字符串而非 NULL'
    FROM submission_diagnosis_facts
    WHERE skill_unit_id = '' OR mistake_point_id = ''
       OR improvement_point_id = '' OR provisional_node_code = ''
    UNION ALL
    SELECT 'invalid_json_shapes', 'BLOCKER', count(*), 'MAX', 0,
           '知识路径或证据引用不是 JSON 数组'
    FROM submission_diagnosis_facts
    WHERE jsonb_typeof(knowledge_path_json::jsonb) <> 'array'
       OR jsonb_typeof(evidence_refs_json::jsonb) <> 'array'
    UNION ALL
    SELECT 'fact_key_duplicates', 'BLOCKER', count(*), 'MAX', 0,
           '事实唯一 key 的重复组数'
    FROM (
        SELECT fact_key FROM submission_diagnosis_facts
        GROUP BY fact_key HAVING count(*) > 1
    ) duplicates
    UNION ALL
    SELECT 'formal_fact_rows', 'DEBT', count(*), 'INFO', 0,
           '正式标准库挂接事实数；需结合总样本和时间窗解释'
    FROM submission_diagnosis_facts WHERE knowledge_path_status = 'FORMAL'
    UNION ALL
    SELECT 'provisional_fact_rows', 'DEBT', count(*), 'INFO', 0,
           '临时候选挂接事实数；用于后续候选治理'
    FROM submission_diagnosis_facts WHERE knowledge_path_status = 'PROVISIONAL'
    UNION ALL
    SELECT 'unclassified_fact_rows', 'DEBT', count(*), 'INFO', 0,
           '尚未归类事实数；不能伪装为正式命中'
    FROM submission_diagnosis_facts WHERE knowledge_path_status = 'UNCLASSIFIED'
    UNION ALL
    SELECT 'analyses_without_advice', 'DEBT', count(*), 'INFO', 0,
           '没有基础或提升建议、因此不可投影事实的历史分析数'
    FROM analysis_advice_counts WHERE advice_count = 0
    UNION ALL
    SELECT 'post_v5_fact_rows', 'INFO', count(*), 'INFO', 0,
           'V5 安装后新增事实数；不足时不得判断 V4 内容效果'
    FROM submission_diagnosis_facts f
    WHERE f.created_at >= COALESCE(
        (SELECT installed_on FROM flyway_schema_history
         WHERE version = '5' AND success ORDER BY installed_rank DESC LIMIT 1),
        timestamp '9999-12-31'
    )
)
SELECT metric, severity, value, direction || ' ' || target,
       CASE
           WHEN direction = 'MAX' AND value > target THEN 'FAIL'
           WHEN direction = 'MIN' AND value < target THEN 'FAIL'
           WHEN severity = 'DEBT' AND value > 0 THEN 'DEBT'
           ELSE 'PASS'
       END,
       definition
FROM raw_metrics
ORDER BY CASE severity WHEN 'BLOCKER' THEN 1 WHEN 'DEBT' THEN 2 ELSE 3 END,
         metric;
SQL

echo "诊断证据质量检查：database=${DB_NAME} mode=${MODE} checked_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if command -v column >/dev/null 2>&1; then
  column -t -s $'\t' "${TMP}"
else
  cat "${TMP}"
fi

FAILURES="$(awk -F $'\t' '$2 == "BLOCKER" && $5 == "FAIL" {count++} END {print count + 0}' "${TMP}")"
if [[ "${MODE}" == "gate" && "${FAILURES}" != "0" ]]; then
  echo "诊断证据质量门禁 FAIL：${FAILURES} 个阻断指标未达标。" >&2
  exit 1
fi

if [[ "${FAILURES}" == "0" ]]; then
  echo "诊断证据质量门禁 PASS：稳定身份、投影覆盖和引用完整性均达标；效果债务继续按新样本观察。"
else
  echo "诊断证据质量报告完成：仍有 ${FAILURES} 个阻断指标。"
fi
