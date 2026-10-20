#!/usr/bin/env bash
set -euo pipefail

MODE="gate"
if [[ $# -gt 1 ]]; then
  echo "用法：bash scripts/check-test-case-semantic-quality.sh [--report-only]" >&2
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
managed_titles(title) AS (
    VALUES
        ('两数求和'), ('回文判断'), ('FizzBuzz'), ('阶乘计算'), ('质数判断'),
        ('潮汐道路最早到达'), ('相邻石子合并最小代价'), ('课程树选课收益'),
        ('可撤销道路连通性'), ('最长重复路线片段'), ('仓库滑窗调平代价'),
        ('分层优惠最短路'), ('双工位装配最短完成时间'), ('矩形能量场统计'),
        ('子数组最小值贡献和'), ('潮汐折扣最短路')
),
managed_problems AS (
    SELECT p.id, p.title
    FROM problems p
    JOIN managed_titles t ON t.title = p.title
),
managed_cases AS (
    SELECT tc.*, p.title AS problem_title
    FROM test_cases tc
    JOIN managed_problems p ON p.id = tc.problem_id
),
raw_metrics(metric, severity, value, direction, target, definition) AS (
    SELECT 'managed_problem_count', 'BLOCKER', count(*), 'EXACT_IF_PRESENT', 16,
           '正式精修题目应为空库 0 道或生产完整 16 道'
    FROM managed_problems
    UNION ALL
    SELECT 'managed_test_case_count', 'BLOCKER', count(*), 'EXACT_IF_PRESENT', 45,
           '正式精修测试点应为空库 0 个或生产完整 45 个'
    FROM managed_cases
    UNION ALL
    SELECT 'semantic_profile_missing', 'BLOCKER', count(*), 'MAX', 0,
           '正式测试点缺少完整语义字段的数量'
    FROM managed_cases
    WHERE semantic_code IS NULL
       OR intent_type IS NULL
       OR intent_title IS NULL
       OR intent_summary IS NULL
       OR learning_objective IS NULL
       OR contest_role IS NULL
       OR reveal_policy IS NULL
       OR knowledge_node_code IS NULL
       OR skill_unit_code IS NULL
       OR review_status <> 'REVIEWED'
       OR source_reference IS NULL
       OR library_version <> 'test-case-semantic-quality-v1'
       OR reviewed_at IS NULL
    UNION ALL
    SELECT 'semantic_code_duplicate', 'BLOCKER', coalesce(sum(copies - 1), 0), 'MAX', 0,
           '测试点稳定语义 code 重复的数量'
    FROM (
        SELECT semantic_code, count(*) AS copies
        FROM test_cases
        WHERE semantic_code IS NOT NULL
        GROUP BY semantic_code
        HAVING count(*) > 1
    ) duplicates
    UNION ALL
    SELECT 'semantic_value_invalid', 'BLOCKER', count(*), 'MAX', 0,
           '意图类型、竞赛角色或审核状态使用非法枚举的数量'
    FROM managed_cases
    WHERE intent_type NOT IN (
              'REPRESENTATIVE', 'BOUNDARY', 'EDGE_CASE', 'STRUCTURAL',
              'STATE_SPACE', 'SCALE', 'PERFORMANCE', 'ROBUSTNESS'
          )
       OR contest_role NOT IN (
              'SAMPLE_EXPLANATION', 'CORRECTNESS_GUARD', 'SUBTASK_GATE', 'COMPLEXITY_STRESS'
          )
       OR review_status <> 'REVIEWED'
    UNION ALL
    SELECT 'semantic_reveal_policy_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '公开性与语义揭示策略冲突的数量'
    FROM managed_cases
    WHERE (is_hidden = true AND reveal_policy <> 'AI_GENERALIZED')
       OR (is_hidden = false AND reveal_policy <> 'PUBLIC_EXAMPLE')
    UNION ALL
    SELECT 'semantic_thin_content', 'BLOCKER', count(*), 'MAX', 0,
           '测试点语义标题、说明或学习目标过薄的数量'
    FROM managed_cases
    WHERE length(btrim(coalesce(intent_title, ''))) < 4
       OR length(btrim(coalesce(intent_summary, ''))) < 24
       OR length(btrim(coalesce(learning_objective, ''))) < 24
       OR intent_summary LIKE '%待补%'
       OR learning_objective LIKE '%待定%'
    UNION ALL
    SELECT 'semantic_hidden_raw_input_leak', 'BLOCKER', count(*), 'MAX', 0,
           '隐藏测试点的泛化语义包含完整原始输入或答案的数量'
    FROM managed_cases
    WHERE is_hidden = true
      AND (
          (
              length(btrim(input)) >= 4
              AND position(btrim(input) IN coalesce(intent_title, '') || coalesce(intent_summary, '') || coalesce(learning_objective, '')) > 0
          )
          OR
          (
              length(btrim(expected_output)) >= 4
              AND position(btrim(expected_output) IN coalesce(intent_title, '') || coalesce(intent_summary, '') || coalesce(learning_objective, '')) > 0
          )
      )
    UNION ALL
    SELECT 'semantic_invalid_knowledge_skill_path', 'BLOCKER', count(*), 'MAX', 0,
           '语义引用不存在或跨路径知识点与能力点的数量'
    FROM managed_cases tc
    LEFT JOIN informatics_knowledge_nodes k
      ON k.code = tc.knowledge_node_code AND k.enabled = true
    LEFT JOIN ai_standard_skill_units s
      ON s.code = tc.skill_unit_code AND s.enabled = true
    WHERE k.code IS NULL
       OR s.code IS NULL
       OR NOT (
            s.primary_knowledge_node_code = tc.knowledge_node_code
            OR tc.knowledge_node_code = ANY (
                regexp_split_to_array(replace(coalesce(s.knowledge_node_codes, ''), E'\r', ''), E'\n+')
            )
       )
    UNION ALL
    SELECT 'problems_without_intent_diversity', 'BLOCKER', count(*), 'MAX', 0,
           '测试点未形成至少两类互补评测意图的正式题目数'
    FROM (
        SELECT problem_id
        FROM managed_cases
        GROUP BY problem_id
        HAVING count(DISTINCT intent_type) < 2
    ) thin_problem
    UNION ALL
    SELECT 'mapped_case_result_missing_snapshot', 'BLOCKER', count(*), 'MAX', 0,
           '已映射正式测试点但缺少语义快照的历史判题结果数'
    FROM submission_case_results scr
    JOIN managed_cases tc ON tc.id = scr.test_case_id
    WHERE scr.test_semantic_code IS NULL
       OR scr.test_intent_type IS NULL
       OR scr.test_intent_title IS NULL
       OR scr.test_intent_summary IS NULL
       OR scr.test_learning_objective IS NULL
       OR scr.test_contest_role IS NULL
       OR scr.test_reveal_policy IS NULL
    UNION ALL
    SELECT 'semantic_profiles', 'INFO', count(*), 'INFO', 0,
           '当前正式测试点语义档案总数'
    FROM managed_cases
    WHERE semantic_code IS NOT NULL
    UNION ALL
    SELECT 'semantic_knowledge_nodes', 'INFO', count(DISTINCT knowledge_node_code), 'INFO', 0,
           '正式测试点直接覆盖的知识点数'
    FROM managed_cases
    WHERE semantic_code IS NOT NULL
    UNION ALL
    SELECT 'semantic_skill_units', 'INFO', count(DISTINCT skill_unit_code), 'INFO', 0,
           '正式测试点直接覆盖的能力点数'
    FROM managed_cases
    WHERE semantic_code IS NOT NULL
    UNION ALL
    SELECT 'semantic_intent_types', 'INFO', count(DISTINCT intent_type), 'INFO', 0,
           '正式测试点覆盖的评测意图类型数'
    FROM managed_cases
    WHERE semantic_code IS NOT NULL
)
SELECT metric, severity, value, direction || ' ' || target,
       CASE
           WHEN direction = 'MAX' AND value > target THEN 'FAIL'
           WHEN direction = 'EXACT_IF_PRESENT' AND value NOT IN (0, target) THEN 'FAIL'
           ELSE 'PASS'
       END,
       definition
FROM raw_metrics
ORDER BY CASE severity WHEN 'BLOCKER' THEN 1 ELSE 2 END, metric;
SQL

echo "测试点语义质量检查：database=${DB_NAME} mode=${MODE} checked_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if command -v column >/dev/null 2>&1; then
  column -t -s $'\t' "${TMP}"
else
  cat "${TMP}"
fi

FAILURES="$(awk -F $'\t' '$2 == "BLOCKER" && $5 == "FAIL" {count++} END {print count + 0}' "${TMP}")"
if [[ "${MODE}" == "gate" && "${FAILURES}" != "0" ]]; then
  echo "测试点语义质量门禁 FAIL：${FAILURES} 个阻断指标未达标。" >&2
  exit 1
fi

if [[ "${FAILURES}" == "0" ]]; then
  echo "测试点语义质量门禁 PASS：正式内容、标准库路径、历史快照与隐藏边界一致。"
else
  echo "测试点语义质量报告完成：仍有 ${FAILURES} 个阻断指标。"
fi
