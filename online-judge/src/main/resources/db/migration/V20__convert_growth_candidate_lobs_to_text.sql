-- Hibernate 6.6 maps the growth-candidate JSON snapshots to PostgreSQL TEXT.
-- The original baseline used PostgreSQL large-object OIDs. Copy the payloads
-- before replacing the columns, then unlink the no-longer-referenced objects.
CREATE TEMP TABLE growth_candidate_lob_migration AS
SELECT id,
       before_snapshot AS before_snapshot_oid,
       diff_summary AS diff_summary_oid,
       rollback_info AS rollback_info_oid
FROM public.ai_standard_library_growth_candidates;

ALTER TABLE public.ai_standard_library_growth_candidates
    ADD COLUMN before_snapshot_text TEXT,
    ADD COLUMN diff_summary_text TEXT,
    ADD COLUMN rollback_info_text TEXT;

UPDATE public.ai_standard_library_growth_candidates candidate
SET before_snapshot_text = CASE
        WHEN migrated.before_snapshot_oid IS NULL THEN NULL
        WHEN EXISTS (SELECT 1 FROM pg_largeobject_metadata WHERE oid = migrated.before_snapshot_oid)
            THEN convert_from(lo_get(migrated.before_snapshot_oid), 'UTF8')
        ELSE NULL
    END,
    diff_summary_text = CASE
        WHEN migrated.diff_summary_oid IS NULL THEN NULL
        WHEN EXISTS (SELECT 1 FROM pg_largeobject_metadata WHERE oid = migrated.diff_summary_oid)
            THEN convert_from(lo_get(migrated.diff_summary_oid), 'UTF8')
        ELSE NULL
    END,
    rollback_info_text = CASE
        WHEN migrated.rollback_info_oid IS NULL THEN NULL
        WHEN EXISTS (SELECT 1 FROM pg_largeobject_metadata WHERE oid = migrated.rollback_info_oid)
            THEN convert_from(lo_get(migrated.rollback_info_oid), 'UTF8')
        ELSE NULL
    END
FROM growth_candidate_lob_migration migrated
WHERE candidate.id = migrated.id;

ALTER TABLE public.ai_standard_library_growth_candidates
    DROP COLUMN before_snapshot,
    DROP COLUMN diff_summary,
    DROP COLUMN rollback_info;

ALTER TABLE public.ai_standard_library_growth_candidates
    RENAME COLUMN before_snapshot_text TO before_snapshot;
ALTER TABLE public.ai_standard_library_growth_candidates
    RENAME COLUMN diff_summary_text TO diff_summary;
ALTER TABLE public.ai_standard_library_growth_candidates
    RENAME COLUMN rollback_info_text TO rollback_info;

SELECT lo_unlink(orphaned.oid_value)
FROM (
    SELECT before_snapshot_oid AS oid_value FROM growth_candidate_lob_migration
    UNION
    SELECT diff_summary_oid FROM growth_candidate_lob_migration
    UNION
    SELECT rollback_info_oid FROM growth_candidate_lob_migration
) orphaned
WHERE orphaned.oid_value IS NOT NULL
  AND EXISTS (SELECT 1 FROM pg_largeobject_metadata WHERE oid = orphaned.oid_value);

DROP TABLE growth_candidate_lob_migration;
