-- Stable, append-only evidence snapshots for recommendation action verification.
-- Existing tag columns remain for backwards compatibility with pre-V9 events.

ALTER TABLE public.student_recommendation_events
    ADD COLUMN IF NOT EXISTS source_submission_id bigint,
    ADD COLUMN IF NOT EXISTS focus_issue_ids text,
    ADD COLUMN IF NOT EXISTS focus_point_keys text,
    ADD COLUMN IF NOT EXISTS focus_knowledge_node_codes text,
    ADD COLUMN IF NOT EXISTS focus_skill_unit_codes text,
    ADD COLUMN IF NOT EXISTS focus_mistake_point_codes text,
    ADD COLUMN IF NOT EXISTS focus_test_semantic_codes text,
    ADD COLUMN IF NOT EXISTS followup_issue_ids text,
    ADD COLUMN IF NOT EXISTS followup_point_keys text,
    ADD COLUMN IF NOT EXISTS followup_knowledge_node_codes text,
    ADD COLUMN IF NOT EXISTS followup_skill_unit_codes text,
    ADD COLUMN IF NOT EXISTS followup_mistake_point_codes text,
    ADD COLUMN IF NOT EXISTS followup_failed_test_semantic_codes text;

CREATE INDEX IF NOT EXISTS idx_reco_events_source_submission
    ON public.student_recommendation_events (source_submission_id)
    WHERE source_submission_id IS NOT NULL;
