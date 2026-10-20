#!/usr/bin/env bash
set -euo pipefail

MODE="gate"
if [[ $# -gt 1 ]]; then
  echo "用法：bash scripts/check-discipline-data-quality.sh [--report-only]" >&2
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
WITH RECURSIVE
active_objects(entity_type, code) AS (
    SELECT 'KNOWLEDGE_NODE'::text, code::text
    FROM informatics_knowledge_nodes WHERE enabled = true
    UNION ALL
    SELECT 'SKILL_UNIT', code::text
    FROM ai_standard_skill_units WHERE enabled = true
    UNION ALL
    SELECT 'MISTAKE_POINT', code::text
    FROM ai_standard_mistake_points WHERE enabled = true
    UNION ALL
    SELECT 'IMPROVEMENT_POINT', code::text
    FROM ai_standard_improvement_points WHERE enabled = true
),
walk(code, parent_code, trail, cycle) AS (
    SELECT code::text, parent_code::text, ARRAY[code::text], false
    FROM informatics_knowledge_nodes
    UNION ALL
    SELECT w.code, p.parent_code::text, w.trail || p.code::text,
           p.code::text = ANY(w.trail)
    FROM walk w
    JOIN informatics_knowledge_nodes p ON p.code::text = w.parent_code
    WHERE NOT w.cycle
),
lineage(node_code, ancestor_code, parent_code, depth) AS (
    SELECT code::text, code::text, parent_code::text, 0
    FROM informatics_knowledge_nodes WHERE enabled = true
    UNION ALL
    SELECT l.node_code, p.code::text, p.parent_code::text, l.depth + 1
    FROM lineage l
    JOIN informatics_knowledge_nodes p ON p.code::text = l.parent_code
    WHERE l.depth < 16
),
covered_nodes AS (
    SELECT DISTINCT l.node_code
    FROM lineage l
    JOIN informatics_discipline_scope_mappings m
      ON m.knowledge_node_code::text = l.ancestor_code
     AND m.enabled = true
),
duplicate_mistakes AS (
    SELECT primary_knowledge_node_code,
           regexp_replace(lower(name), '[[:space:]（）()_-]+', '', 'g') AS normalized_name,
           count(*) AS copies
    FROM ai_standard_mistake_points
    WHERE enabled = true
    GROUP BY primary_knowledge_node_code,
             regexp_replace(lower(name), '[[:space:]（）()_-]+', '', 'g')
    HAVING count(*) > 1
),
invalid_prerequisites AS (
    SELECT count(*) AS rows
    FROM informatics_knowledge_nodes n
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(n.prerequisites, ''), E'\n') ref
    LEFT JOIN informatics_knowledge_nodes target ON target.code = btrim(ref)
    WHERE btrim(ref) <> '' AND target.code IS NULL
),
raw_metrics(metric, severity, value, direction, target, definition) AS (
    SELECT 'knowledge_parent_cycles', 'BLOCKER', count(DISTINCT code), 'MAX', 0,
           '父子层级中出现循环的节点数'
    FROM walk WHERE cycle
    UNION ALL
    SELECT 'knowledge_orphan_non_domain', 'BLOCKER', count(*), 'MAX', 0,
           '非领域节点缺少父节点的数量'
    FROM informatics_knowledge_nodes n
    LEFT JOIN informatics_knowledge_nodes p ON p.code = n.parent_code
    WHERE n.type <> 'DOMAIN' AND p.code IS NULL
    UNION ALL
    SELECT 'knowledge_invalid_prerequisite_refs', 'BLOCKER', rows, 'MAX', 0,
           '真实前置知识引用不存在节点的数量'
    FROM invalid_prerequisites
    UNION ALL
    SELECT 'knowledge_parent_as_prerequisite', 'BLOCKER', count(*), 'MAX', 0,
           '把 parent_code 机械复制为 prerequisites 的节点数'
    FROM informatics_knowledge_nodes
    WHERE parent_code IS NOT NULL
      AND btrim(COALESCE(prerequisites, '')) = btrim(parent_code)
    UNION ALL
    SELECT 'knowledge_self_only_aliases', 'BLOCKER', count(*), 'MAX', 0,
           'aliases 只重复主名的节点数'
    FROM informatics_knowledge_nodes
    WHERE btrim(COALESCE(aliases, '')) = btrim(name)
    UNION ALL
    SELECT 'enabled_skill_orphan_node', 'BLOCKER', count(*), 'MAX', 0,
           '启用能力点缺少主知识节点的数量'
    FROM ai_standard_skill_units s
    LEFT JOIN informatics_knowledge_nodes n
      ON n.code = s.primary_knowledge_node_code AND n.enabled = true
    WHERE s.enabled = true AND n.code IS NULL
    UNION ALL
    SELECT 'enabled_mistake_orphan_parent', 'BLOCKER', count(*), 'MAX', 0,
           '启用易错点缺少启用能力点或主知识节点的数量'
    FROM ai_standard_mistake_points m
    LEFT JOIN ai_standard_skill_units s
      ON s.code = m.skill_unit_code AND s.enabled = true
    LEFT JOIN informatics_knowledge_nodes n
      ON n.code = m.primary_knowledge_node_code AND n.enabled = true
    WHERE m.enabled = true AND (s.code IS NULL OR n.code IS NULL)
    UNION ALL
    SELECT 'enabled_improvement_orphan_parent', 'BLOCKER', count(*), 'MAX', 0,
           '启用提升点引用不存在能力点或知识节点的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_skill_units s
      ON s.code = i.skill_unit_code AND s.enabled = true
    LEFT JOIN informatics_knowledge_nodes n
      ON n.code = i.primary_knowledge_node_code AND n.enabled = true
    WHERE i.enabled = true
      AND ((i.skill_unit_code IS NOT NULL AND s.code IS NULL)
        OR (i.primary_knowledge_node_code IS NOT NULL AND n.code IS NULL))
    UNION ALL
    SELECT 'enabled_relation_missing_source', 'BLOCKER', count(*), 'MAX', 0,
           '启用关系的源对象不存在或已停用'
    FROM ai_standard_library_relations r
    LEFT JOIN active_objects a
      ON a.entity_type = r.source_type::text AND a.code = r.source_code::text
    WHERE r.enabled = true AND a.code IS NULL
    UNION ALL
    SELECT 'enabled_relation_missing_target', 'BLOCKER', count(*), 'MAX', 0,
           '启用关系的目标对象不存在或已停用'
    FROM ai_standard_library_relations r
    LEFT JOIN active_objects a
      ON a.entity_type = r.target_type::text AND a.code = r.target_code::text
    WHERE r.enabled = true AND a.code IS NULL
    UNION ALL
    SELECT 'mapped_legacy_inactive_target', 'BLOCKER', count(*), 'MAX', 0,
           '仍标记 MAPPED 但目标不存在或已停用的兼容映射'
    FROM ai_standard_library_legacy_mappings m
    LEFT JOIN active_objects a
      ON a.entity_type = m.target_type::text AND a.code = m.target_code::text
    WHERE m.migration_status = 'MAPPED' AND a.code IS NULL
    UNION ALL
    SELECT 'duplicate_mistake_same_anchor_name', 'BLOCKER',
           COALESCE(sum(copies - 1), 0), 'MAX', 0,
           '同一主知识节点下规范化名称相同的额外启用易错点'
    FROM duplicate_mistakes
    UNION ALL
    SELECT 'implementation_terms_in_enabled_category', 'BLOCKER', count(*), 'MAX', 0,
           '启用正式分类中残留版本或兜底实现词的条目数'
    FROM (
        SELECT category FROM ai_standard_skill_units WHERE enabled = true
        UNION ALL SELECT category FROM ai_standard_mistake_points WHERE enabled = true
        UNION ALL SELECT category FROM ai_standard_improvement_points WHERE enabled = true
        UNION ALL SELECT category FROM ai_standard_library_items WHERE enabled = true
    ) categories
    WHERE category ~* '/V[0-9]+|兜底吸收|兜底榨取-(A类|B类)'
    UNION ALL
    SELECT 'discipline_scope_mapping_rows', 'BLOCKER', count(*), 'MIN', 39,
           '第四批发布后启用且有来源的领域与章节级学科范围锚点数'
    FROM informatics_discipline_scope_mappings
    WHERE enabled = true AND btrim(source_reference) <> ''
    UNION ALL
    SELECT 'discipline_framework_count', 'BLOCKER', count(DISTINCT framework_code), 'MIN', 3,
           '已接入的统一、高中和竞赛框架数量'
    FROM informatics_discipline_scope_mappings WHERE enabled = true
    UNION ALL
    SELECT 'curated_knowledge_points', 'BLOCKER', count(*), 'MIN', 132,
           '第一批至第四批累计人工精修知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version IN (
          'informatics-knowledge-discipline-v1',
          'informatics-knowledge-discipline-v2',
          'informatics-knowledge-discipline-v3',
          'informatics-knowledge-discipline-v4'
      )
      AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_2', 'BLOCKER', count(*), 'MIN', 30,
           '第二批人工精修且已标记 discipline-v2 的知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v2'
      AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'discipline_v2_improvement_points', 'BLOCKER', count(*), 'MIN', 12,
           '第二批新增且启用的正式提升点数'
    FROM ai_standard_improvement_points
    WHERE enabled = true
      AND library_version = 'informatics-discipline-quality-v2'
    UNION ALL
    SELECT 'discipline_v2_improvement_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第二批提升点缺少目标、练习、学生收益、教师解释或关联错因的数量'
    FROM ai_standard_improvement_points
    WHERE enabled = true
      AND library_version = 'informatics-discipline-quality-v2'
      AND (btrim(COALESCE(improvement_goal, '')) = ''
        OR btrim(COALESCE(practice_strategy, '')) = ''
        OR btrim(COALESCE(student_benefit, '')) = ''
        OR btrim(COALESCE(teacher_explanation, '')) = ''
        OR btrim(COALESCE(related_mistake_codes, '')) = '')
    UNION ALL
    SELECT 'discipline_v2_snapshot_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第二批规范提升点缺少同 code 启用快照或关键归属不一致的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_items item
      ON item.layer = 'IMPROVEMENT_POINT' AND item.code = i.code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v2'
      AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
        OR item.skill_unit_code IS DISTINCT FROM i.skill_unit_code
        OR item.primary_knowledge_node_code IS DISTINCT FROM i.primary_knowledge_node_code
        OR item.related_items IS DISTINCT FROM i.related_mistake_codes)
    UNION ALL
    SELECT 'discipline_v2_legacy_mapping_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第二批规范提升点缺少同 code MAPPED 兼容映射的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_legacy_mappings m
      ON m.legacy_layer = 'IMPROVEMENT_POINT' AND m.legacy_code = i.code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v2'
      AND (m.id IS NULL OR m.migration_status <> 'MAPPED'
        OR m.target_type <> 'IMPROVEMENT_POINT'
        OR m.target_code <> i.code)
    UNION ALL
    SELECT 'curated_knowledge_points_batch_3', 'BLOCKER', count(*), 'MIN', 30,
           '第三批人工精修且已标记 discipline-v3 的知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v3'
      AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_3_algo', 'BLOCKER', count(DISTINCT n.code), 'MIN', 20,
           '第三批 ALGO 领域人工精修知识点数'
    FROM informatics_knowledge_nodes n
    JOIN lineage l ON l.node_code = n.code
    JOIN informatics_knowledge_nodes d
      ON d.code = l.ancestor_code AND d.type = 'DOMAIN' AND d.code = 'ALGO'
    WHERE n.enabled = true AND n.type = 'KNOWLEDGE_POINT'
      AND n.library_version = 'informatics-knowledge-discipline-v3'
      AND n.description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_3_ds', 'BLOCKER', count(DISTINCT n.code), 'MIN', 10,
           '第三批 DS 领域人工精修知识点数'
    FROM informatics_knowledge_nodes n
    JOIN lineage l ON l.node_code = n.code
    JOIN informatics_knowledge_nodes d
      ON d.code = l.ancestor_code AND d.type = 'DOMAIN' AND d.code = 'DS'
    WHERE n.enabled = true AND n.type = 'KNOWLEDGE_POINT'
      AND n.library_version = 'informatics-knowledge-discipline-v3'
      AND n.description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'discipline_v3_improvement_points', 'BLOCKER', count(*), 'MIN', 12,
           '第三批新增且启用的正式提升点数'
    FROM ai_standard_improvement_points
    WHERE enabled = true
      AND library_version = 'informatics-discipline-quality-v3'
    UNION ALL
    SELECT 'discipline_v3_improvement_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第三批提升点缺少目标、练习、学生收益、教师解释或关联错因的数量'
    FROM ai_standard_improvement_points
    WHERE enabled = true
      AND library_version = 'informatics-discipline-quality-v3'
      AND (btrim(COALESCE(improvement_goal, '')) = ''
        OR btrim(COALESCE(practice_strategy, '')) = ''
        OR btrim(COALESCE(student_benefit, '')) = ''
        OR btrim(COALESCE(teacher_explanation, '')) = ''
        OR btrim(COALESCE(related_mistake_codes, '')) = '')
    UNION ALL
    SELECT 'discipline_v3_improvement_invalid_mistake_refs', 'BLOCKER', count(*), 'MAX', 0,
           '第三批提升点关联不存在、停用或归属其他能力的易错点引用数量'
    FROM ai_standard_improvement_points i
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
    LEFT JOIN ai_standard_mistake_points m
      ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = i.skill_unit_code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v3'
      AND btrim(ref) <> '' AND m.code IS NULL
    UNION ALL
    SELECT 'discipline_v3_snapshot_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第三批规范提升点缺少同 code 启用快照或关键归属不一致的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_items item
      ON item.layer = 'IMPROVEMENT_POINT' AND item.code = i.code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v3'
      AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
        OR item.skill_unit_code IS DISTINCT FROM i.skill_unit_code
        OR item.primary_knowledge_node_code IS DISTINCT FROM i.primary_knowledge_node_code
        OR item.related_items IS DISTINCT FROM i.related_mistake_codes)
    UNION ALL
    SELECT 'discipline_v3_legacy_mapping_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第三批规范提升点缺少同 code MAPPED 兼容映射的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_legacy_mappings m
      ON m.legacy_layer = 'IMPROVEMENT_POINT' AND m.legacy_code = i.code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v3'
      AND (m.id IS NULL OR m.migration_status <> 'MAPPED'
        OR m.target_type <> 'IMPROVEMENT_POINT'
        OR m.target_code <> i.code)
    UNION ALL
    SELECT 'discipline_v3_compatibility_skill_target', 'BLOCKER', count(*), 'MAX', 0,
           '第三批正式提升点错误关联兼容占位能力的数量'
    FROM ai_standard_improvement_points i
    JOIN ai_standard_skill_units s ON s.code = i.skill_unit_code
    WHERE i.enabled = true
      AND i.library_version = 'informatics-discipline-quality-v3'
      AND (s.code LIKE 'SK_COMPAT_%'
        OR s.description LIKE '%用于兼容旧 AI 标准库标签%')
    UNION ALL
    SELECT 'discipline_v4_chapter_scope_mappings', 'BLOCKER', count(*), 'MIN', 26,
           '第四批新增且可追溯到高中课程或 NOI 大纲的章节级范围映射数'
    FROM informatics_discipline_scope_mappings m
    JOIN informatics_knowledge_nodes n ON n.code = m.knowledge_node_code
    WHERE m.enabled = true AND n.type = 'CHAPTER'
      AND ((m.framework_code = 'MOE_HIGH_SCHOOL_IT_2020'
            AND m.source_reference LIKE 'https://jyj.changdu.gov.cn/%')
        OR (m.framework_code = 'CCF_NOI_2025'
            AND m.source_reference = 'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf'))
    UNION ALL
    SELECT 'curated_knowledge_points_batch_4', 'BLOCKER', count(*), 'MIN', 48,
           '第四批人工精修且已标记 discipline-v4 的知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v4'
      AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_4_basic', 'BLOCKER', count(*), 'MIN', 12,
           '第四批 BASIC 领域人工精修知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v4'
      AND code LIKE 'BASIC.%' AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_4_math', 'BLOCKER', count(*), 'MIN', 12,
           '第四批 MATH 领域人工精修知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v4'
      AND code LIKE 'MATH.%' AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_4_eng', 'BLOCKER', count(*), 'MIN', 12,
           '第四批 ENG 领域人工精修知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v4'
      AND code LIKE 'ENG.%' AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'curated_knowledge_points_batch_4_contest', 'BLOCKER', count(*), 'MIN', 12,
           '第四批 CONTEST 领域人工精修知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v4'
      AND code LIKE 'CONTEST.%' AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'discipline_v4_mistake_points', 'BLOCKER', count(*), 'MIN', 22,
           '第四批新增且启用的细颗粒易错点数'
    FROM ai_standard_mistake_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
    UNION ALL
    SELECT 'discipline_v4_improvement_points', 'BLOCKER', count(*), 'MIN', 22,
           '第四批新增且启用的正式提升点数'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
    UNION ALL
    SELECT 'discipline_v4_mistake_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第四批易错点缺少误区、症状、修复策略、能力或知识归属的数量'
    FROM ai_standard_mistake_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
      AND (btrim(COALESCE(misconception, '')) = ''
        OR btrim(COALESCE(symptom, '')) = ''
        OR btrim(COALESCE(repair_strategy, '')) = ''
        OR btrim(COALESCE(skill_unit_code, '')) = ''
        OR btrim(COALESCE(primary_knowledge_node_code, '')) = ''
        OR btrim(COALESCE(knowledge_node_codes, '')) = '')
    UNION ALL
    SELECT 'discipline_v4_improvement_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第四批提升点缺少目标、练习、学生收益、教师解释或关联错因的数量'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
      AND (btrim(COALESCE(improvement_goal, '')) = ''
        OR btrim(COALESCE(practice_strategy, '')) = ''
        OR btrim(COALESCE(student_benefit, '')) = ''
        OR btrim(COALESCE(teacher_explanation, '')) = ''
        OR btrim(COALESCE(related_mistake_codes, '')) = '')
    UNION ALL
    SELECT 'discipline_v4_improvement_invalid_mistake_refs', 'BLOCKER', count(*), 'MAX', 0,
           '第四批提升点关联不存在、停用或归属其他能力的易错点引用数量'
    FROM ai_standard_improvement_points i
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
    LEFT JOIN ai_standard_mistake_points m
      ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = i.skill_unit_code
    WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v4'
      AND btrim(ref) <> '' AND m.code IS NULL
    UNION ALL
    SELECT 'discipline_v4_unlinked_new_mistakes', 'BLOCKER', count(*), 'MAX', 0,
           '第四批易错点没有被同能力的第四批提升点关联的数量'
    FROM ai_standard_mistake_points m
    WHERE m.enabled = true AND m.library_version = 'informatics-discipline-quality-v4'
      AND NOT EXISTS (
          SELECT 1
          FROM ai_standard_improvement_points i
          CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
          WHERE i.enabled = true
            AND i.library_version = 'informatics-discipline-quality-v4'
            AND i.skill_unit_code = m.skill_unit_code
            AND btrim(ref) = m.code
      )
    UNION ALL
    SELECT 'discipline_v4_snapshot_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第四批规范易错点或提升点缺少同 code 启用快照或关键归属不一致的数量'
    FROM (
        SELECT m.code
        FROM ai_standard_mistake_points m
        LEFT JOIN ai_standard_library_items item
          ON item.layer = 'MISTAKE_POINT' AND item.code = m.code
        WHERE m.enabled = true AND m.library_version = 'informatics-discipline-quality-v4'
          AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
            OR item.skill_unit_code IS DISTINCT FROM m.skill_unit_code
            OR item.primary_knowledge_node_code IS DISTINCT FROM m.primary_knowledge_node_code
            OR item.related_knowledge_node_codes IS DISTINCT FROM m.knowledge_node_codes)
        UNION ALL
        SELECT i.code
        FROM ai_standard_improvement_points i
        LEFT JOIN ai_standard_library_items item
          ON item.layer = 'IMPROVEMENT_POINT' AND item.code = i.code
        WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v4'
          AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
            OR item.skill_unit_code IS DISTINCT FROM i.skill_unit_code
            OR item.primary_knowledge_node_code IS DISTINCT FROM i.primary_knowledge_node_code
            OR item.related_items IS DISTINCT FROM i.related_mistake_codes)
    ) mismatches
    UNION ALL
    SELECT 'discipline_v4_legacy_mapping_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第四批规范易错点或提升点缺少同 code MAPPED 兼容映射的数量'
    FROM (
        SELECT 'MISTAKE_POINT'::text AS layer, code
        FROM ai_standard_mistake_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
        UNION ALL
        SELECT 'IMPROVEMENT_POINT', code
        FROM ai_standard_improvement_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
    ) canonical
    LEFT JOIN ai_standard_library_legacy_mappings m
      ON m.legacy_layer = canonical.layer AND m.legacy_code = canonical.code
    WHERE m.id IS NULL OR m.migration_status <> 'MAPPED'
       OR m.target_type <> canonical.layer OR m.target_code <> canonical.code
    UNION ALL
    SELECT 'discipline_v4_compatibility_skill_target', 'BLOCKER', count(*), 'MAX', 0,
           '第四批正式易错点或提升点错误关联兼容占位能力的数量'
    FROM (
        SELECT skill_unit_code FROM ai_standard_mistake_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
        UNION ALL
        SELECT skill_unit_code FROM ai_standard_improvement_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
    ) v4
    JOIN ai_standard_skill_units s ON s.code = v4.skill_unit_code
    WHERE s.code LIKE 'SK_COMPAT_%'
       OR s.description LIKE '%用于兼容旧 AI 标准库标签%'
    UNION ALL
    SELECT 'discipline_v4_first_path_skills', 'BLOCKER', count(DISTINCT skill_unit_code), 'MIN', 14,
           '第四批为 BASIC 与 CONTEST 正式能力补齐首条提升路径的能力数'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4'
      AND skill_unit_code IN (
          'SK_BRANCH_CASE_COVERAGE', 'SK_FUNCTION_CONTRACT', 'SK_INDEX_BASE_MAPPING',
          'SK_INTEGER_RANGE_PROTECTION', 'SK_IO_STRUCTURE_MAPPING',
          'SK_MATRIX_COORDINATE_MAPPING', 'SK_RECURSION_BACKTRACK_STATE',
          'SK_RECURSION_BASE_PROGRESS', 'SK_STRING_SLICE_ENDPOINT',
          'SK_V7_MULTICASE_STATE_RESET_CONTRACT', 'SK_V7_NUMERIC_RANGE_PRECISION_CONTRACT',
          'SK_V7_STRING_BOUNDARY_ENCODING_CONTRACT', 'SK_READING_OBJECTIVE_CONSTRAINT',
          'SK_READING_SAMPLE_CONSTRAINT_CROSSCHECK'
      )
    UNION ALL
    SELECT 'discipline_v5_improvement_points', 'BLOCKER', count(*), 'MIN', 17,
           '第五批为全部正式无路径能力新增的提升点数'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v5'
    UNION ALL
    SELECT 'discipline_v5_improvement_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第五批提升点缺少目标、练习、学生收益、教师解释或关联错因的数量'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v5'
      AND (btrim(COALESCE(improvement_goal, '')) = ''
        OR btrim(COALESCE(practice_strategy, '')) = ''
        OR btrim(COALESCE(student_benefit, '')) = ''
        OR btrim(COALESCE(teacher_explanation, '')) = ''
        OR btrim(COALESCE(related_mistake_codes, '')) = '')
    UNION ALL
    SELECT 'discipline_v5_improvement_invalid_mistake_refs', 'BLOCKER', count(*), 'MAX', 0,
           '第五批提升点关联不存在、停用或归属其他能力的易错点引用数量'
    FROM ai_standard_improvement_points i
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
    LEFT JOIN ai_standard_mistake_points m
      ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = i.skill_unit_code
    WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v5'
      AND btrim(ref) <> '' AND m.code IS NULL
    UNION ALL
    SELECT 'discipline_v5_snapshot_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第五批规范提升点缺少同 code 启用快照或关键归属不一致的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_items item
      ON item.layer = 'IMPROVEMENT_POINT' AND item.code = i.code
    WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v5'
      AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
        OR item.skill_unit_code IS DISTINCT FROM i.skill_unit_code
        OR item.primary_knowledge_node_code IS DISTINCT FROM i.primary_knowledge_node_code
        OR item.related_items IS DISTINCT FROM i.related_mistake_codes)
    UNION ALL
    SELECT 'discipline_v5_legacy_mapping_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第五批规范提升点缺少同 code MAPPED 兼容映射的数量'
    FROM ai_standard_improvement_points i
    LEFT JOIN ai_standard_library_legacy_mappings m
      ON m.legacy_layer = 'IMPROVEMENT_POINT' AND m.legacy_code = i.code
    WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v5'
      AND (m.id IS NULL OR m.migration_status <> 'MAPPED'
        OR m.target_type <> 'IMPROVEMENT_POINT' OR m.target_code <> i.code)
    UNION ALL
    SELECT 'discipline_v5_compatibility_skill_target', 'BLOCKER', count(*), 'MAX', 0,
           '第五批正式提升点错误关联兼容占位能力的数量'
    FROM ai_standard_improvement_points i
    JOIN ai_standard_skill_units s ON s.code = i.skill_unit_code
    WHERE i.enabled = true AND i.library_version = 'informatics-discipline-quality-v5'
      AND (s.code LIKE 'SK_COMPAT_%'
        OR s.description LIKE '%用于兼容旧 AI 标准库标签%')
    UNION ALL
    SELECT 'discipline_v5_application_scenarios', 'BLOCKER', count(*), 'MIN', 34,
           '第五批启用课堂—竞赛应用场景数'
    FROM ai_standard_application_scenarios
    WHERE enabled = true AND library_version = 'informatics-discipline-application-v1'
    UNION ALL
    SELECT 'discipline_v5_transfer_pairs', 'BLOCKER', count(DISTINCT transfer_pair_code), 'MIN', 17,
           '第五批课堂—竞赛迁移对数'
    FROM ai_standard_application_scenarios
    WHERE enabled = true AND library_version = 'informatics-discipline-application-v1'
    UNION ALL
    SELECT 'discipline_v5_incomplete_transfer_pairs', 'BLOCKER', count(*), 'MAX', 0,
           '缺少课堂端、竞赛端或跨越多个能力点的第五批迁移对数量'
    FROM (
        SELECT transfer_pair_code
        FROM ai_standard_application_scenarios
        WHERE enabled = true AND library_version = 'informatics-discipline-application-v1'
        GROUP BY transfer_pair_code
        HAVING count(*) <> 2
           OR count(*) FILTER (WHERE context_type = 'CLASSROOM') <> 1
           OR count(*) FILTER (WHERE context_type = 'CONTEST') <> 1
           OR count(DISTINCT skill_unit_code) <> 1
           OR count(DISTINCT knowledge_point_code) <> 1
    ) invalid_pairs
    UNION ALL
    SELECT 'discipline_v5_scenario_invalid_parent', 'BLOCKER', count(*), 'MAX', 0,
           '第五批场景引用非知识点、停用能力、兼容能力或错误主锚点的数量'
    FROM ai_standard_application_scenarios a
    LEFT JOIN informatics_knowledge_nodes n
      ON n.code = a.knowledge_point_code AND n.enabled = true AND n.type = 'KNOWLEDGE_POINT'
    LEFT JOIN ai_standard_skill_units s
      ON s.code = a.skill_unit_code AND s.enabled = true
    WHERE a.enabled = true AND a.library_version = 'informatics-discipline-application-v1'
      AND (n.code IS NULL OR s.code IS NULL OR s.code LIKE 'SK_COMPAT_%'
        OR s.primary_knowledge_node_code IS DISTINCT FROM a.knowledge_point_code)
    UNION ALL
    SELECT 'discipline_v5_scenario_invalid_mistake_refs', 'BLOCKER', count(*), 'MAX', 0,
           '第五批场景关联不存在、停用或跨能力易错点的引用数量'
    FROM ai_standard_application_scenarios a
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(a.linked_mistake_codes, ''), E'\n') ref
    LEFT JOIN ai_standard_mistake_points m
      ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = a.skill_unit_code
    WHERE a.enabled = true AND a.library_version = 'informatics-discipline-application-v1'
      AND btrim(ref) <> '' AND m.code IS NULL
    UNION ALL
    SELECT 'discipline_v5_scenario_invalid_improvement_refs', 'BLOCKER', count(*), 'MAX', 0,
           '第五批场景关联不存在、停用或跨能力提升点的引用数量'
    FROM ai_standard_application_scenarios a
    CROSS JOIN LATERAL regexp_split_to_table(COALESCE(a.linked_improvement_codes, ''), E'\n') ref
    LEFT JOIN ai_standard_improvement_points i
      ON i.code = btrim(ref) AND i.enabled = true AND i.skill_unit_code = a.skill_unit_code
    WHERE a.enabled = true AND a.library_version = 'informatics-discipline-application-v1'
      AND btrim(ref) <> '' AND i.code IS NULL
    UNION ALL
    SELECT 'discipline_v5_scenario_thin_content', 'BLOCKER', count(*), 'MAX', 0,
           '第五批场景缺少可观察任务、检查动作、约束或成功标准的数量'
    FROM ai_standard_application_scenarios
    WHERE enabled = true AND library_version = 'informatics-discipline-application-v1'
      AND (length(btrim(task_context)) < 30
        OR length(btrim(student_task)) < 30
        OR length(btrim(observable_evidence)) < 30
        OR length(btrim(common_failure)) < 20
        OR length(btrim(teacher_move)) < 25
        OR length(btrim(student_check)) < 20
        OR length(btrim(constraint_profile)) < 25
        OR length(btrim(success_criteria)) < 25
        OR length(btrim(transfer_note)) < 25
        OR task_context LIKE '%待补%'
        OR teacher_move LIKE '%加强练习%'
        OR success_criteria LIKE '%待定%')
    UNION ALL
    SELECT 'discipline_v5_scenario_source_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           '第五批课堂或竞赛场景来源框架、链接或审校状态不一致的数量'
    FROM ai_standard_application_scenarios
    WHERE enabled = true AND library_version = 'informatics-discipline-application-v1'
      AND ((context_type = 'CLASSROOM'
            AND (source_framework <> 'MOE_HIGH_SCHOOL_IT_2020'
              OR source_reference NOT LIKE 'https://jyj.changdu.gov.cn/%'))
        OR (context_type = 'CONTEST'
            AND (source_framework <> 'CCF_NOI_2025'
              OR source_reference <> 'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf'))
        OR review_status <> 'INFERRED_REVIEWED')
    UNION ALL
    SELECT 'formal_skills_without_improvement_v5', 'BLOCKER', count(*), 'MAX', 0,
           '第五批发布后仍无提升路径的正式非兼容能力点数'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true AND s.code NOT LIKE 'SK_COMPAT_%'
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
      )
    UNION ALL
    SELECT 'skills_without_improvement_batch_5_limit', 'BLOCKER', count(*), 'MAX', 10,
           '第五批发布后缺少提升点能力只允许保留历史兼容能力'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
      )
    UNION ALL
    SELECT 'discipline_v6_curated_knowledge', 'BLOCKER', count(*), 'MIN', 48,
           '第六批跨六领域精修的启用知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v6'
      AND description NOT LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'discipline_v6_domain_count', 'BLOCKER', count(DISTINCT split_part(code, '.', 1)), 'MIN', 6,
           '第六批覆盖的 ALGO、BASIC、CONTEST、DS、ENG、MATH 领域数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND library_version = 'informatics-knowledge-discipline-v6'
    UNION ALL
    SELECT 'discipline_v6_unbalanced_domains', 'BLOCKER', count(*), 'MAX', 0,
           '第六批精修知识点数量不是每领域 8 个的领域数'
    FROM (
        SELECT split_part(code, '.', 1) AS domain_code
        FROM informatics_knowledge_nodes
        WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
          AND library_version = 'informatics-knowledge-discipline-v6'
        GROUP BY split_part(code, '.', 1)
        HAVING count(*) <> 8
    ) unbalanced_v6
    UNION ALL
    SELECT 'discipline_v6_skill_units', 'BLOCKER', count(*), 'MIN', 48,
           '第六批新增且启用的正式能力点数'
    FROM ai_standard_skill_units
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
    UNION ALL
    SELECT 'discipline_v6_mistake_points', 'BLOCKER', count(*), 'MIN', 144,
           '第六批新增且启用的细颗粒易错点数'
    FROM ai_standard_mistake_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
    UNION ALL
    SELECT 'discipline_v6_improvement_points', 'BLOCKER', count(*), 'MIN', 48,
           '第六批新增且启用的可执行提升点数'
    FROM ai_standard_improvement_points
    WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
    UNION ALL
    SELECT 'discipline_v6_application_scenarios', 'BLOCKER', count(*), 'MIN', 96,
           '第六批新增且启用的课堂—竞赛应用场景数'
    FROM ai_standard_application_scenarios
    WHERE enabled = true AND library_version = 'informatics-discipline-application-v2'
    UNION ALL
    SELECT 'discipline_v6_incomplete_theme_packs', 'BLOCKER', count(*), 'MAX', 0,
           '第六批缺少 3 个错因、1 个提升点或成对场景的主题包数'
    FROM (
        SELECT s.code
        FROM ai_standard_skill_units s
        LEFT JOIN ai_standard_mistake_points m
          ON m.skill_unit_code = s.code AND m.enabled = true
         AND m.library_version = 'informatics-discipline-quality-v6'
        LEFT JOIN ai_standard_improvement_points i
          ON i.skill_unit_code = s.code AND i.enabled = true
         AND i.library_version = 'informatics-discipline-quality-v6'
        LEFT JOIN ai_standard_application_scenarios a
          ON a.skill_unit_code = s.code AND a.enabled = true
         AND a.library_version = 'informatics-discipline-application-v2'
        WHERE s.enabled = true AND s.library_version = 'informatics-discipline-quality-v6'
        GROUP BY s.code
        HAVING count(DISTINCT m.code) <> 3
            OR count(DISTINCT i.code) <> 1
            OR count(DISTINCT a.code) <> 2
            OR count(DISTINCT a.context_type) <> 2
    ) incomplete_v6
    UNION ALL
    SELECT 'discipline_v6_duplicate_mistake_content', 'BLOCKER', count(*), 'MAX', 0,
           '第六批同一能力下易错点名称、症状或修复动作重复的能力数'
    FROM (
        SELECT skill_unit_code
        FROM ai_standard_mistake_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
        GROUP BY skill_unit_code
        HAVING count(*) <> count(DISTINCT regexp_replace(lower(name), '[[:space:]（）()_-]+', '', 'g'))
            OR count(*) <> count(DISTINCT symptom)
            OR count(*) <> count(DISTINCT repair_strategy)
    ) duplicate_v6
    UNION ALL
    SELECT 'discipline_v6_invalid_content', 'BLOCKER', count(*), 'MAX', 0,
           '第六批规范条目缺少具体对象、症状、修复、练习或教师验收文本的数量'
    FROM (
        SELECT code
        FROM ai_standard_skill_units
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
          AND (length(btrim(description)) < 60 OR length(btrim(learning_goal)) < 25)
        UNION ALL
        SELECT code
        FROM ai_standard_mistake_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
          AND (length(btrim(symptom)) < 15 OR length(btrim(repair_strategy)) < 15
            OR description LIKE '%理解或应用偏差%')
        UNION ALL
        SELECT code
        FROM ai_standard_improvement_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
          AND (length(btrim(practice_strategy)) < 30 OR length(btrim(teacher_explanation)) < 30
            OR practice_strategy LIKE '%多做练习%')
        UNION ALL
        SELECT code
        FROM ai_standard_application_scenarios
        WHERE enabled = true AND library_version = 'informatics-discipline-application-v2'
          AND (length(btrim(student_task)) < 22 OR length(btrim(observable_evidence)) < 25
            OR length(btrim(success_criteria)) < 25)
    ) invalid_v6_content
    UNION ALL
    SELECT 'discipline_v6_missing_flat_or_mapping', 'BLOCKER', count(*), 'MAX', 0,
           '第六批规范条目缺少同 code 启用平铺快照或 MAPPED 兼容映射的数量'
    FROM (
        SELECT code, 'SKILL_UNIT'::text AS target_type
        FROM ai_standard_skill_units
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
        UNION ALL
        SELECT code, 'MISTAKE_POINT'
        FROM ai_standard_mistake_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
        UNION ALL
        SELECT code, 'IMPROVEMENT_POINT'
        FROM ai_standard_improvement_points
        WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6'
    ) normalized_v6
    WHERE NOT EXISTS (
              SELECT 1 FROM ai_standard_library_items flat
              WHERE flat.enabled = true AND flat.code = normalized_v6.code
                AND flat.layer = normalized_v6.target_type
                AND flat.library_version = 'informatics-discipline-quality-v6'
          )
       OR NOT EXISTS (
              SELECT 1 FROM ai_standard_library_legacy_mappings mapping
              WHERE mapping.legacy_code = normalized_v6.code
                AND mapping.legacy_layer = normalized_v6.target_type
                AND mapping.target_code = normalized_v6.code
                AND mapping.target_type = normalized_v6.target_type
                AND mapping.migration_status = 'MAPPED'
                AND mapping.source_version = 'informatics-discipline-quality-v6'
          )
    UNION ALL
    SELECT 'discipline_v6_effective_text_chars', 'BLOCKER',
           (SELECT COALESCE(sum(length(COALESCE(description, ''))
                              + length(COALESCE(learning_objectives, ''))
                              + length(COALESCE(typical_problems, ''))
                              + length(COALESCE(aliases, ''))), 0)
            FROM informatics_knowledge_nodes
            WHERE enabled = true AND library_version = 'informatics-knowledge-discipline-v6')
         + (SELECT COALESCE(sum(length(COALESCE(description, ''))
                              + length(COALESCE(learning_goal, ''))
                              + length(COALESCE(knowledge_node_codes, ''))), 0)
            FROM ai_standard_skill_units
            WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6')
         + (SELECT COALESCE(sum(length(COALESCE(description, ''))
                              + length(COALESCE(misconception, ''))
                              + length(COALESCE(symptom, ''))
                              + length(COALESCE(repair_strategy, ''))), 0)
            FROM ai_standard_mistake_points
            WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6')
         + (SELECT COALESCE(sum(length(COALESCE(description, ''))
                              + length(COALESCE(improvement_goal, ''))
                              + length(COALESCE(practice_strategy, ''))
                              + length(COALESCE(student_benefit, ''))
                              + length(COALESCE(teacher_explanation, ''))), 0)
            FROM ai_standard_improvement_points
            WHERE enabled = true AND library_version = 'informatics-discipline-quality-v6')
         + (SELECT COALESCE(sum(length(COALESCE(task_context, ''))
                              + length(COALESCE(student_task, ''))
                              + length(COALESCE(observable_evidence, ''))
                              + length(COALESCE(common_failure, ''))
                              + length(COALESCE(teacher_move, ''))
                              + length(COALESCE(student_check, ''))
                              + length(COALESCE(constraint_profile, ''))
                              + length(COALESCE(success_criteria, ''))
                              + length(COALESCE(transfer_note, ''))), 0)
            FROM ai_standard_application_scenarios
            WHERE enabled = true AND library_version = 'informatics-discipline-application-v2'),
           'MIN', 100000,
           '第六批五层有效文本总字符数，不以空字段或模板占位凑体积'
    UNION ALL
    SELECT 'v12_enabled_knowledge_duplicate_names', 'BLOCKER',
           COALESCE(sum(copies - 1), 0), 'MAX', 0,
           'V12 治理后启用知识节点仍使用相同展示名的额外记录数'
    FROM (
        SELECT name, count(*) AS copies
        FROM informatics_knowledge_nodes
        WHERE enabled = true
        GROUP BY name
        HAVING count(*) > 1
    ) duplicate_knowledge_names
    UNION ALL
    SELECT 'v12_enabled_mistake_duplicate_names', 'BLOCKER',
           COALESCE(sum(copies - 1), 0), 'MAX', 0,
           'V12 治理后启用易错点仍使用相同展示名的额外记录数'
    FROM (
        SELECT name, count(*) AS copies
        FROM ai_standard_mistake_points
        WHERE enabled = true
        GROUP BY name
        HAVING count(*) > 1
    ) duplicate_mistake_names
    UNION ALL
    SELECT 'v12_meta_repair_instructions', 'BLOCKER', count(*), 'MAX', 0,
           '启用易错点仍把具体修正建议重新交给 AI 生成的元说明数量'
    FROM ai_standard_mistake_points
    WHERE enabled = true
      AND repair_strategy LIKE '%具体诊断、修正建议和提高建议由 AI%'
    UNION ALL
    SELECT 'v12_actionable_legacy_repairs', 'BLOCKER', count(*), 'MIN', 348,
           'V12 已补成可复现、可修改、可复测协议的旧版正式易错点数'
    FROM ai_standard_mistake_points
    WHERE enabled = true
      AND skill_unit_code NOT LIKE 'SK_COMPAT_%'
      AND library_version = 'standard-library-v3-skill-mistake'
      AND (repair_strategy LIKE '针对“%'
        OR code IN ('MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED',
                    'MP_V8_STRING_OVERLAP_MATCH_SKIPPED'))
    UNION ALL
    SELECT 'v12_compatibility_routing_contracts', 'BLOCKER', count(*), 'MIN', 10,
           'V12 明确只作旧 ID 路由、不生成正式教学结论的兼容易错点数'
    FROM ai_standard_mistake_points
    WHERE enabled = true
      AND skill_unit_code LIKE 'SK_COMPAT_%'
      AND repair_strategy LIKE '该条目只保留旧版 code 兼容%'
    UNION ALL
    SELECT 'v12_repaired_flat_snapshot_mismatch', 'BLOCKER', count(*), 'MAX', 0,
           'V12 已治理规范错因与平铺快照在名称、描述、误区、提示或症状上不一致的数量'
    FROM ai_standard_mistake_points m
    LEFT JOIN ai_standard_library_items item
      ON item.layer = 'MISTAKE_POINT' AND item.code = m.code
    WHERE m.enabled = true
      AND (m.repair_strategy LIKE '针对“%'
        OR m.repair_strategy LIKE '该条目只保留旧版 code 兼容%'
        OR m.code IN ('MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED',
                      'MP_V8_STRING_OVERLAP_MATCH_SKIPPED'))
      AND (item.id IS NULL
        OR item.name IS DISTINCT FROM m.name
        OR item.description IS DISTINCT FROM m.description
        OR item.common_misconception IS DISTINCT FROM m.misconception
        OR item.hintl3 IS DISTINCT FROM left(m.repair_strategy, 800)
        OR item.evidence_signals IS DISTINCT FROM m.symptom)
    UNION ALL
    SELECT 'v12_v11_transfer_pair_same_teacher_move', 'BLOCKER', count(*), 'MAX', 0,
           '第六批同一迁移对的课堂与竞赛场景机械复用相同教师动作的数量'
    FROM (
        SELECT transfer_pair_code
        FROM ai_standard_application_scenarios
        WHERE enabled = true
          AND library_version = 'informatics-discipline-application-v2'
        GROUP BY transfer_pair_code
        HAVING count(*) = 2
           AND count(DISTINCT context_type) = 2
           AND max(teacher_move) FILTER (WHERE context_type = 'CLASSROOM')
               = max(teacher_move) FILTER (WHERE context_type = 'CONTEST')
    ) same_teacher_move_pairs
    UNION ALL
    SELECT 'template_knowledge_descriptions_v12_limit', 'BLOCKER', count(*), 'MAX', 384,
           'V12 存量治理后模板化知识点描述的允许上限'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'template_knowledge_descriptions_batch_6_limit', 'BLOCKER', count(*), 'MAX', 406,
           '第六批发布后模板化知识点描述的允许上限'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'template_knowledge_descriptions_batch_4_limit', 'BLOCKER', count(*), 'MAX', 454,
           '第四批发布后模板化知识点描述的允许上限'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'skills_without_improvement_batch_4_limit', 'BLOCKER', count(*), 'MAX', 27,
           '第四批发布后缺少提升点能力点的允许上限'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
      )
    UNION ALL
    SELECT 'template_knowledge_descriptions_batch_3_limit', 'BLOCKER', count(*), 'MAX', 502,
           '第三批发布后模板化知识点描述的允许上限'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'skills_without_improvement_batch_3_limit', 'BLOCKER', count(*), 'MAX', 41,
           '第三批发布后缺少提升点能力点的允许上限'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
      )
    UNION ALL
    SELECT 'template_knowledge_descriptions_batch_2_limit', 'BLOCKER', count(*), 'MAX', 532,
           '第二批发布后模板化知识点描述的允许上限'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'skills_without_improvement_batch_2_limit', 'BLOCKER', count(*), 'MAX', 53,
           '第二批发布后缺少提升点能力点的允许上限'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
      )
    UNION ALL
    SELECT 'knowledge_scope_covered_nodes', 'INFO', count(*), 'INFO', 0,
           '通过直接或最近祖先映射获得学科范围的启用节点数'
    FROM covered_nodes
    UNION ALL
    SELECT 'template_knowledge_descriptions', 'DEBT', count(*), 'INFO', 0,
           '仍使用“细颗粒知识点”占位描述的启用知识点数'
    FROM informatics_knowledge_nodes
    WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
      AND description LIKE '细颗粒知识点：%'
    UNION ALL
    SELECT 'skills_without_improvement', 'DEBT', count(*), 'INFO', 0,
           '尚未关联启用提升点的启用能力点数'
    FROM ai_standard_skill_units s
    WHERE s.enabled = true
      AND NOT EXISTS (
          SELECT 1 FROM ai_standard_improvement_points i
          WHERE i.enabled = true AND i.skill_unit_code = s.code
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

echo "学科数据质量检查：database=${DB_NAME} mode=${MODE} checked_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if command -v column >/dev/null 2>&1; then
  column -t -s $'\t' "${TMP}"
else
  cat "${TMP}"
fi

FAILURES="$(awk -F $'\t' '$2 == "BLOCKER" && $5 == "FAIL" {count++} END {print count + 0}' "${TMP}")"
if [[ "${MODE}" == "gate" && "${FAILURES}" != "0" ]]; then
  echo "学科数据质量门禁 FAIL：${FAILURES} 个阻断指标未达标。" >&2
  exit 1
fi

if [[ "${FAILURES}" == "0" ]]; then
  echo "学科数据质量门禁 PASS：阻断指标全部达标；DEBT 指标按后续批次持续下降。"
else
  echo "学科数据质量报告完成：仍有 ${FAILURES} 个阻断指标。"
fi
