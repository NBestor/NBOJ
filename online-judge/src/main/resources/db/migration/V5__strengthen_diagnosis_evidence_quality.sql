ALTER TABLE public.submission_diagnosis_facts
    ADD COLUMN IF NOT EXISTS provisional_node_code character varying(160);

CREATE INDEX IF NOT EXISTS idx_diagnosis_fact_provisional
    ON public.submission_diagnosis_facts (provisional_node_code);

CREATE TEMP TABLE diagnosis_evidence_v5_advice_items ON COMMIT DROP AS
SELECT a.id AS analysis_id,
       'REPAIR'::text AS fact_type,
       left(
           a.id || ':REPAIR:' ||
           COALESCE(
               NULLIF(btrim(item ->> 'issueId'), ''),
               NULLIF(btrim(item ->> 'mistakePointId'), ''),
               NULLIF(btrim(item ->> 'skillUnitId'), ''),
               NULLIF(btrim(item ->> 'title'), ''),
               'item-' || (ordinality - 1)
           ) || ':' || (ordinality - 1),
           180
       ) AS fact_key,
       NULLIF(btrim(item ->> 'provisionalNodeCode'), '') AS provisional_node_code
FROM public.submission_analyses a
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(a.report_json::jsonb -> 'basicLayerAdvice', '[]'::jsonb)
) WITH ORDINALITY AS advice(item, ordinality)
WHERE a.report_json IS NOT NULL AND btrim(a.report_json) <> ''
UNION ALL
SELECT a.id,
       'IMPROVEMENT',
       left(
           a.id || ':IMPROVEMENT:' ||
           COALESCE(
               NULLIF(btrim(item ->> 'issueId'), ''),
               NULLIF(btrim(item ->> 'improvementPointId'), ''),
               NULLIF(btrim(item ->> 'skillUnitId'), ''),
               NULLIF(btrim(item ->> 'title'), ''),
               'item-' || (ordinality - 1)
           ) || ':' || (ordinality - 1),
           180
       ),
       NULLIF(btrim(item ->> 'provisionalNodeCode'), '')
FROM public.submission_analyses a
CROSS JOIN LATERAL jsonb_array_elements(
    COALESCE(a.report_json::jsonb -> 'improvementLayerAdvice', '[]'::jsonb)
) WITH ORDINALITY AS advice(item, ordinality)
WHERE a.report_json IS NOT NULL AND btrim(a.report_json) <> '';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM diagnosis_evidence_v5_advice_items
        GROUP BY analysis_id, fact_type, fact_key
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V5 advice fact_key is not unique within an analysis and fact type';
    END IF;
END $$;

UPDATE public.submission_diagnosis_facts f
SET provisional_node_code = advice.provisional_node_code
FROM diagnosis_evidence_v5_advice_items advice
WHERE f.analysis_id = advice.analysis_id
  AND f.fact_type = advice.fact_type
  AND f.fact_key = advice.fact_key
  AND f.knowledge_path_status = 'PROVISIONAL'
  AND advice.provisional_node_code IS NOT NULL
  AND f.provisional_node_code IS DISTINCT FROM advice.provisional_node_code;

UPDATE public.submission_diagnosis_facts
SET skill_unit_id = NULLIF(btrim(skill_unit_id), ''),
    mistake_point_id = NULLIF(btrim(mistake_point_id), ''),
    improvement_point_id = NULLIF(btrim(improvement_point_id), ''),
    provisional_node_code = NULLIF(btrim(provisional_node_code), '');

-- 正式易错点和提升点自身是规范锚点；事实中的能力点必须服从规范条目的归属，
-- 不能继续保留模型历史输出中的交叉组合。
UPDATE public.submission_diagnosis_facts f
SET skill_unit_id = m.skill_unit_code
FROM public.ai_standard_mistake_points m
WHERE f.knowledge_path_status = 'FORMAL'
  AND f.fact_type = 'REPAIR'
  AND m.code = f.mistake_point_id
  AND m.enabled = true
  AND f.skill_unit_id IS DISTINCT FROM m.skill_unit_code;

UPDATE public.submission_diagnosis_facts f
SET skill_unit_id = i.skill_unit_code
FROM public.ai_standard_improvement_points i
WHERE f.knowledge_path_status = 'FORMAL'
  AND f.fact_type = 'IMPROVEMENT'
  AND i.code = f.improvement_point_id
  AND i.enabled = true
  AND f.skill_unit_id IS DISTINCT FROM i.skill_unit_code;

UPDATE public.submission_diagnosis_facts
SET library_fit = CASE knowledge_path_status
        WHEN 'FORMAL' THEN 'HIT'
        WHEN 'PROVISIONAL' THEN 'PARTIAL'
        WHEN 'INFERRED' THEN 'PARTIAL'
        ELSE 'MISS'
    END
WHERE library_fit IS NULL
   OR btrim(library_fit) = ''
   OR upper(btrim(library_fit)) NOT IN ('HIT', 'PARTIAL', 'MISS');

UPDATE public.submission_diagnosis_facts
SET library_fit = upper(btrim(library_fit));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.submission_diagnosis_facts f
        WHERE f.knowledge_path_status = 'PROVISIONAL'
          AND f.provisional_node_code IS NULL
    ) THEN
        RAISE EXCEPTION 'V5 provisional diagnosis fact is missing a recoverable provisionalNodeCode';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.submission_diagnosis_facts f
        LEFT JOIN public.ai_standard_library_growth_candidates g
          ON g.suggested_code = f.provisional_node_code
        WHERE f.knowledge_path_status = 'PROVISIONAL'
        GROUP BY f.id
        HAVING count(g.id) <> 1
    ) THEN
        RAISE EXCEPTION 'V5 provisional diagnosis fact does not resolve to exactly one growth candidate';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.submission_diagnosis_facts f
        JOIN public.ai_standard_library_growth_candidates g
          ON g.suggested_code = f.provisional_node_code
        LEFT JOIN public.informatics_knowledge_nodes n
          ON n.code = g.parent_knowledge_node_code AND n.enabled = true
        WHERE f.knowledge_path_status = 'PROVISIONAL'
          AND n.code IS NULL
    ) THEN
        RAISE EXCEPTION 'V5 provisional diagnosis fact has no valid enabled parent knowledge point';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.submission_diagnosis_facts f
        LEFT JOIN public.ai_standard_skill_units s
          ON s.code = f.skill_unit_id AND s.enabled = true
        LEFT JOIN public.ai_standard_mistake_points m
          ON m.code = f.mistake_point_id AND m.enabled = true
        LEFT JOIN public.ai_standard_improvement_points i
          ON i.code = f.improvement_point_id AND i.enabled = true
        WHERE f.knowledge_path_status = 'FORMAL'
          AND NOT CASE
              WHEN f.fact_type = 'REPAIR'
                THEN m.code IS NOT NULL
                 AND s.code IS NOT NULL
                 AND m.skill_unit_code = f.skill_unit_id
              WHEN f.fact_type = 'IMPROVEMENT'
                THEN i.code IS NOT NULL
                 AND (f.skill_unit_id IS NULL
                   OR (s.code IS NOT NULL AND i.skill_unit_code = f.skill_unit_id))
              ELSE COALESCE(m.code, i.code, s.code) IS NOT NULL
          END
    ) THEN
        RAISE EXCEPTION 'V5 formal diagnosis fact has an invalid or inconsistent standard-library anchor';
    END IF;
END $$;

ALTER TABLE public.submission_diagnosis_facts
    ALTER COLUMN library_fit SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_diagnosis_fact_provisional_identity'
          AND conrelid = 'public.submission_diagnosis_facts'::regclass
    ) THEN
        ALTER TABLE public.submission_diagnosis_facts
            ADD CONSTRAINT ck_diagnosis_fact_provisional_identity CHECK (
                (knowledge_path_status = 'PROVISIONAL') =
                (provisional_node_code IS NOT NULL AND btrim(provisional_node_code) <> '')
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_diagnosis_fact_library_fit'
          AND conrelid = 'public.submission_diagnosis_facts'::regclass
    ) THEN
        ALTER TABLE public.submission_diagnosis_facts
            ADD CONSTRAINT ck_diagnosis_fact_library_fit CHECK (
                library_fit IN ('HIT', 'PARTIAL', 'MISS')
            );
    END IF;
END $$;
