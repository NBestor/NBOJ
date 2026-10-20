ALTER TABLE student_ai_feedback_revisions
    ADD COLUMN analysis_json TEXT;

ALTER TABLE student_ai_feedback_revisions
    ADD COLUMN evidence_json TEXT;

ALTER TABLE teacher_diagnosis_corrections
    ADD COLUMN feedback_revision_id BIGINT;

CREATE INDEX idx_teacher_corrections_feedback_revision
    ON teacher_diagnosis_corrections (feedback_revision_id);
