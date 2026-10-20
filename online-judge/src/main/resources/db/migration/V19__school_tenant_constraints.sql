DELETE FROM ai_usage_events WHERE school_id IS NULL;
ALTER TABLE ai_usage_events ALTER COLUMN school_id SET NOT NULL;

ALTER TABLE teacher_accounts ADD CONSTRAINT ck_teacher_account_school_scope
    CHECK ((role = 'PLATFORM_ADMIN' AND school_id IS NULL)
        OR (role IN ('SCHOOL_ADMIN', 'TEACHER') AND school_id IS NOT NULL));

ALTER TABLE teacher_accounts ADD CONSTRAINT fk_teacher_account_school
    FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE school_ai_quotas ADD CONSTRAINT fk_school_ai_quota_school
    FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE ai_usage_events ADD CONSTRAINT fk_ai_usage_school
    FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE schools ADD CONSTRAINT fk_school_created_by_platform
    FOREIGN KEY (created_by) REFERENCES teacher_accounts (id);

CREATE INDEX IF NOT EXISTS idx_schools_status ON schools (status);
CREATE INDEX IF NOT EXISTS idx_school_ai_quotas_school_month ON school_ai_quotas (school_id, quota_month);
