#!/usr/bin/env bash
set -euo pipefail

ALLOW_UNBASELINED=false
if [[ $# -gt 1 ]]; then
  echo "用法：bash scripts/check-database-schema-readiness.sh [--allow-unbaselined]" >&2
  exit 1
fi
if [[ $# -eq 1 ]]; then
  if [[ "$1" != "--allow-unbaselined" ]]; then
    echo "未知参数：$1" >&2
    exit 1
  fi
  ALLOW_UNBASELINED=true
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"
DB_NAME="${POSTGRES_DB:-onlinejudge}"
DB_USER="${POSTGRES_USER:-onlinejudge}"

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" <<'SQL'
DO $$
DECLARE missing text;
BEGIN
  WITH expected(name) AS (VALUES
    ('ai_diagnosis_runs'), ('ai_diagnosis_stage_runs'),
    ('ai_standard_application_scenarios'),
    ('ai_standard_improvement_points'), ('ai_standard_library_growth_candidates'),
    ('ai_standard_library_items'), ('ai_standard_library_legacy_mappings'),
    ('ai_standard_library_relations'), ('ai_standard_mistake_points'), ('ai_standard_skill_units'),
    ('assignment_invites'), ('assignment_tasks'), ('assignments'), ('class_groups'),
    ('class_review_feedback'), ('coach_prompts'), ('hint_safety_checks'),
    ('informatics_discipline_scope_mappings'), ('informatics_knowledge_nodes'),
    ('problems'), ('student_ai_feedback_events'),
    ('student_ai_feedback_revisions'), ('student_ai_feedbacks'), ('student_profiles'),
    ('student_recommendation_events'), ('submission_analyses'), ('submission_case_results'),
    ('submission_diagnosis_facts'), ('submission_evidence_backfill_batches'),
    ('submission_issue_transitions'), ('submissions'), ('teacher_diagnosis_corrections'), ('test_cases')
  )
  SELECT string_agg(e.name, ', ' ORDER BY e.name) INTO missing
  FROM expected e
  LEFT JOIN information_schema.tables t ON t.table_schema = 'public' AND t.table_name = e.name
  WHERE t.table_name IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION '缺少业务表: %', missing;
  END IF;
END $$;

DO $$
DECLARE missing text;
BEGIN
  WITH expected(table_name, column_name) AS (VALUES
    ('problems', 'id'), ('problems', 'title'), ('test_cases', 'problem_id'),
    ('test_cases', 'semantic_code'), ('test_cases', 'intent_type'),
    ('test_cases', 'learning_objective'), ('test_cases', 'reveal_policy'),
    ('test_cases', 'knowledge_node_code'), ('test_cases', 'skill_unit_code'),
    ('informatics_knowledge_nodes', 'code'),
    ('informatics_discipline_scope_mappings', 'framework_code'),
    ('informatics_discipline_scope_mappings', 'scope_code'),
    ('ai_standard_skill_units', 'primary_knowledge_node_code'),
    ('ai_standard_mistake_points', 'skill_unit_code'),
    ('ai_standard_improvement_points', 'skill_unit_code'),
    ('ai_standard_application_scenarios', 'transfer_pair_code'),
    ('ai_standard_application_scenarios', 'context_type'),
    ('ai_standard_application_scenarios', 'knowledge_point_code'),
    ('ai_standard_application_scenarios', 'skill_unit_code'),
    ('ai_standard_application_scenarios', 'observable_evidence'),
    ('ai_standard_application_scenarios', 'success_criteria'),
    ('submission_diagnosis_facts', 'provisional_node_code'),
    ('submission_case_results', 'test_case_id'),
    ('submission_case_results', 'test_semantic_code'),
    ('submission_case_results', 'test_intent_summary'),
    ('student_recommendation_events', 'source_submission_id'),
    ('student_recommendation_events', 'focus_point_keys'),
    ('student_recommendation_events', 'focus_test_semantic_codes'),
    ('student_recommendation_events', 'followup_point_keys'),
    ('student_recommendation_events', 'followup_failed_test_semantic_codes'),
    ('student_ai_feedback_revisions', 'analysis_json'),
    ('student_ai_feedback_revisions', 'evidence_json'),
    ('teacher_diagnosis_corrections', 'feedback_revision_id'),
    ('submissions', 'problem_id'), ('student_ai_feedbacks', 'submission_id'),
    ('ai_diagnosis_runs', 'submission_id')
  )
  SELECT string_agg(e.table_name || '.' || e.column_name, ', ' ORDER BY e.table_name, e.column_name) INTO missing
  FROM expected e
  LEFT JOIN information_schema.columns c
    ON c.table_schema = 'public' AND c.table_name = e.table_name AND c.column_name = e.column_name
  WHERE c.column_name IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION '缺少关键列: %', missing;
  END IF;
END $$;

DO $$
DECLARE missing text;
BEGIN
  WITH expected(name) AS (VALUES
    ('uk_informatics_knowledge_node_code'), ('uk_discipline_scope_mapping'),
    ('idx_discipline_scope_framework'), ('idx_discipline_scope_knowledge'),
    ('uk_ai_standard_skill_unit_code'),
    ('uk_ai_standard_mistake_point_code'), ('uk_ai_standard_improvement_point_code'),
    ('uk_ai_standard_application_scenario_code'),
    ('uk_ai_standard_application_scenario_pair_context'),
    ('idx_ai_standard_application_scenario_knowledge'),
    ('idx_ai_standard_application_scenario_skill'),
    ('uk_test_case_semantic_code'), ('idx_test_case_semantic_knowledge'),
    ('idx_test_case_semantic_skill'), ('idx_submission_case_results_test_case'),
    ('idx_submissions_problem_submitted_at'), ('idx_ai_diagnosis_run_submission'),
    ('idx_issue_transition_student_point'), ('idx_diagnosis_fact_provisional')
    , ('idx_reco_events_source_submission'), ('idx_teacher_corrections_feedback_revision')
  )
  SELECT string_agg(e.name, ', ' ORDER BY e.name) INTO missing
  FROM expected e
  LEFT JOIN pg_indexes i ON i.schemaname = 'public' AND i.indexname = e.name
  WHERE i.indexname IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION '缺少关键索引: %', missing;
  END IF;
END $$;
SQL

HISTORY_TABLE="$(docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -Atc "select to_regclass('public.flyway_schema_history') is not null")"
if [[ "${HISTORY_TABLE}" != "t" ]]; then
  if [[ "${ALLOW_UNBASELINED}" == "true" ]]; then
    echo "Schema 结构预检通过；Flyway 历史尚未建立（允许基线前状态）。"
    exit 0
  fi
  echo "缺少 flyway_schema_history，数据库尚未受 Flyway 管理。" >&2
  exit 1
fi

FAILED="$(docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -Atc 'select count(*) from flyway_schema_history where not success')"
CURRENT="$(docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -Atc "select coalesce((select version from flyway_schema_history where success order by installed_rank desc limit 1), 'none')")"
if [[ "${FAILED}" != "0" ]]; then
  echo "Flyway 存在 ${FAILED} 条失败记录。" >&2
  exit 1
fi

echo "Schema readiness PASS：Flyway 当前版本=${CURRENT}，失败记录=0，关键表/列/索引完整。"
