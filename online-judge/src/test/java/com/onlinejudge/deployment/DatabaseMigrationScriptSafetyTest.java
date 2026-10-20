package com.onlinejudge.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationScriptSafetyTest {

    private static final Path SCRIPTS = Path.of("scripts");

    @Test
    void baselineRequiresConfirmationBackupAndSchemaChecks() throws IOException {
        String script = read("baseline-postgres-flyway.sh");

        assertThat(script)
                .contains("--confirm-baseline")
                .contains("check-database-schema-readiness.sh --allow-unbaselined")
                .contains("backup-postgres.sh")
                .contains("rehearse-postgres-restore.sh")
                .contains("capture-database-counts.sh")
                .contains("compare-database-counts.sh")
                .contains("FLYWAY_BASELINE_ON_MIGRATE=true");
        assertThat(script).doesNotContain("docker compose down -v", "docker volume prune");
    }

    @Test
    void backupProducesVerifiableCustomArchiveAndMetadata() throws IOException {
        String script = read("backup-postgres.sh");

        assertThat(script)
                .contains("--format=custom")
                .contains("verify-postgres-backup.sh")
                .contains("sha256")
                .contains("postgres_version=")
                .contains("postgres_image=");
    }

    @Test
    void restoreRequiresConfirmationAndRefusesRunningApplication() throws IOException {
        String script = read("restore-postgres.sh");

        assertThat(script)
                .contains("--confirm-restore")
                .contains("verify-postgres-backup.sh")
                .contains("--exit-on-error")
                .contains("应用容器仍在运行");
        assertThat(script).doesNotContain("docker compose down -v", "docker volume prune");
    }

    @Test
    void restoreRehearsalUsesIsolatedContainerWithoutProjectVolume() throws IOException {
        String script = read("rehearse-postgres-restore.sh");

        assertThat(script)
                .contains("docker run -d --name")
                .contains("pg_restore")
                .contains("docker exec -i \"${NAME}\" psql -v ON_ERROR_STOP=1")
                .contains("正式数据库和 Volume 未挂载");
        assertThat(script).doesNotContain("postgres-data", "docker compose exec");
    }

    @Test
    void productionDeployWaitsForApplicationMigrationBeforeDatabaseGates() throws IOException {
        String script = read("deploy-online-judge.sh");

        int applicationProbe = script.indexOf("\"http://127.0.0.1:${SERVER_PORT:-8081}${PUBLIC_PATH}\"");
        int schemaGate = script.indexOf("bash scripts/check-database-schema-readiness.sh");
        int disciplineGate = script.indexOf("bash scripts/check-discipline-data-quality.sh");
        int semanticGate = script.indexOf("bash scripts/check-test-case-semantic-quality.sh");

        assertThat(applicationProbe).isGreaterThan(0);
        assertThat(schemaGate).isGreaterThan(applicationProbe);
        assertThat(disciplineGate).isGreaterThan(applicationProbe);
        assertThat(semanticGate).isGreaterThan(applicationProbe);
    }

    @Test
    void teacherAnalysisVersionMigrationIsCoveredByDeploymentGates() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V10__preserve_teacher_analysis_versions.sql"));
        String readiness = read("check-database-schema-readiness.sh");
        String migrationTest = read("test-postgres-migrations.sh");

        assertThat(migration)
                .contains("analysis_json", "evidence_json", "feedback_revision_id")
                .contains("idx_teacher_corrections_feedback_revision")
                .doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
        assertThat(readiness)
                .contains("student_ai_feedback_revisions', 'analysis_json")
                .contains("student_ai_feedback_revisions', 'evidence_json")
                .contains("teacher_diagnosis_corrections', 'feedback_revision_id")
                .contains("idx_teacher_corrections_feedback_revision");
        assertThat(migrationTest).contains("V1-V20", "${VERSION}\" == \"20", "${LEGACY_VERSION}\" == \"20");
    }

    @Test
    void schoolProfileUsesFlywayAndHibernateValidation() throws IOException {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application)
                .contains("baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}")
                .contains("clean-disabled: true")
                .contains("ddl-auto: validate");
    }

    @Test
    void disciplineBatchTwoUsesVersionedMigrationAndThreeTableConsistencyGate() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V3__refine_discipline_quality_batch_2.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");

        assertThat(migration)
                .contains("informatics-knowledge-discipline-v2")
                .contains("informatics-discipline-quality-v2")
                .contains("discipline_quality_v2_improvements")
                .contains("INSERT INTO public.ai_standard_improvement_points")
                .contains("INSERT INTO public.ai_standard_library_items")
                .contains("INSERT INTO public.ai_standard_library_legacy_mappings")
                .doesNotContain("DELETE FROM", "TRUNCATE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("curated_knowledge_points_batch_2")
                .contains("discipline_v2_snapshot_mismatch")
                .contains("discipline_v2_legacy_mapping_mismatch")
                .contains("template_knowledge_descriptions_batch_2_limit")
                .contains("skills_without_improvement_batch_2_limit");
    }

    @Test
    void disciplineBatchThreeUsesEvidenceDrivenMigrationAndCompatibilityGuard() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V4__refine_discipline_quality_batch_3.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");

        assertThat(migration)
                .contains("informatics-knowledge-discipline-v3")
                .contains("informatics-discipline-quality-v3")
                .contains("discipline_quality_v3_improvements")
                .contains("s.code NOT LIKE 'SK_COMPAT_%'")
                .contains("INSERT INTO public.ai_standard_improvement_points")
                .contains("INSERT INTO public.ai_standard_library_items")
                .contains("INSERT INTO public.ai_standard_library_legacy_mappings")
                .doesNotContain("DELETE FROM", "TRUNCATE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("curated_knowledge_points_batch_3_algo")
                .contains("curated_knowledge_points_batch_3_ds")
                .contains("discipline_v3_improvement_invalid_mistake_refs")
                .contains("discipline_v3_snapshot_mismatch")
                .contains("discipline_v3_legacy_mapping_mismatch")
                .contains("discipline_v3_compatibility_skill_target")
                .contains("template_knowledge_descriptions_batch_3_limit")
                .contains("skills_without_improvement_batch_3_limit");
    }

    @Test
    void diagnosisEvidenceV5UsesDeterministicBackfillAndStableIdentityGate() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V5__strengthen_diagnosis_evidence_quality.sql"));
        String qualityGate = read("check-diagnosis-evidence-quality.sh");
        String integration = read("test-postgres-migrations.sh");

        assertThat(migration)
                .contains("ADD COLUMN IF NOT EXISTS provisional_node_code")
                .contains("diagnosis_evidence_v5_advice_items")
                .contains("provisionalNodeCode")
                .contains("f.skill_unit_id IS DISTINCT FROM m.skill_unit_code")
                .contains("invalid or inconsistent standard-library anchor")
                .contains("ck_diagnosis_fact_provisional_identity")
                .contains("ck_diagnosis_fact_library_fit")
                .contains("does not resolve to exactly one growth candidate")
                .doesNotContain("DELETE FROM", "TRUNCATE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("analysis_projection_mismatch")
                .contains("provisional_fact_missing_code")
                .contains("provisional_code_missing_candidate")
                .contains("provisional_identity_source_mismatch")
                .contains("formal_anchor_invalid")
                .contains("library_fit_invalid")
                .contains("post_v5_fact_rows");
        assertThat(integration)
                .contains("[[ \"${VERSION}\" == \"20\" ]]")
                .contains("V1-V20");
    }

    @Test
    void disciplineBatchFourUsesStandardsDrivenGranularExpansionAndCompatibilityGuards() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V6__expand_discipline_library_batch_4.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");

        assertThat(migration)
                .contains("informatics-knowledge-discipline-v4")
                .contains("informatics-discipline-quality-v4")
                .contains("discipline_quality_v4_mistakes")
                .contains("discipline_quality_v4_improvements")
                .contains("MOE_HIGH_SCHOOL_IT_2020")
                .contains("CCF_NOI_2025")
                .contains("expected exactly 48 curated knowledge points")
                .contains("expected exactly 22 normalized mistake points")
                .contains("expected exactly 22 normalized improvement points")
                .contains("s.code NOT LIKE 'SK_COMPAT_%'")
                .contains("INSERT INTO public.ai_standard_mistake_points")
                .contains("INSERT INTO public.ai_standard_improvement_points")
                .contains("INSERT INTO public.ai_standard_library_items")
                .contains("INSERT INTO public.ai_standard_library_legacy_mappings")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("discipline_v4_chapter_scope_mappings")
                .contains("curated_knowledge_points_batch_4_basic")
                .contains("curated_knowledge_points_batch_4_math")
                .contains("curated_knowledge_points_batch_4_eng")
                .contains("curated_knowledge_points_batch_4_contest")
                .contains("discipline_v4_unlinked_new_mistakes")
                .contains("discipline_v4_snapshot_mismatch")
                .contains("discipline_v4_legacy_mapping_mismatch")
                .contains("template_knowledge_descriptions_batch_4_limit")
                .contains("skills_without_improvement_batch_4_limit");
    }

    @Test
    void disciplineBatchFiveAddsPairedTeachingContestScenariosAndFormalSkillClosure() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V7__add_teaching_contest_application_scenarios.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");
        String schemaReadiness = read("check-database-schema-readiness.sh");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS public.ai_standard_application_scenarios")
                .contains("uk_ai_standard_application_scenario_pair_context")
                .contains("discipline_quality_v5_improvements")
                .contains("discipline_quality_v5_scenarios")
                .contains("expected exactly 17 normalized improvement points")
                .contains("expected exactly 34 application scenarios")
                .contains("expected exactly 17 teaching-contest transfer pairs")
                .contains("one classroom and one contest context")
                .contains("every formal non-compatibility skill to have an improvement path")
                .contains("s.code NOT LIKE 'SK_COMPAT_%'")
                .contains("INSERT INTO public.ai_standard_improvement_points")
                .contains("INSERT INTO public.ai_standard_library_items")
                .contains("INSERT INTO public.ai_standard_library_legacy_mappings")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("discipline_v5_improvement_points")
                .contains("discipline_v5_application_scenarios")
                .contains("discipline_v5_incomplete_transfer_pairs")
                .contains("discipline_v5_scenario_invalid_mistake_refs")
                .contains("discipline_v5_scenario_invalid_improvement_refs")
                .contains("discipline_v5_scenario_thin_content")
                .contains("formal_skills_without_improvement_v5")
                .contains("skills_without_improvement_batch_5_limit");
        assertThat(schemaReadiness)
                .contains("ai_standard_application_scenarios")
                .contains("uk_ai_standard_application_scenario_pair_context")
                .contains("idx_ai_standard_application_scenario_skill");
    }

    @Test
    void testCaseSemanticV8AddsReviewedCoverageSnapshotsAndHiddenDataGate() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V8__add_test_case_semantic_evidence.sql"));
        String qualityGate = read("check-test-case-semantic-quality.sh");
        String schemaReadiness = read("check-database-schema-readiness.sh");
        String integration = read("test-postgres-migrations.sh");
        String deploy = read("deploy-online-judge.sh");

        assertThat(migration)
                .contains("ADD COLUMN IF NOT EXISTS semantic_code")
                .contains("ADD COLUMN IF NOT EXISTS test_case_id")
                .contains("test_case_semantic_v1")
                .contains("expected exactly 45 managed test cases")
                .contains("expected 45 reviewed semantic test cases")
                .contains("test-case-semantic-quality-v1")
                .contains("AI_GENERALIZED")
                .contains("PUBLIC_EXAMPLE")
                .contains("mapped historical case results without semantic snapshots")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("semantic_profile_missing")
                .contains("semantic_reveal_policy_mismatch")
                .contains("semantic_hidden_raw_input_leak")
                .contains("semantic_invalid_knowledge_skill_path")
                .contains("problems_without_intent_diversity")
                .contains("mapped_case_result_missing_snapshot");
        assertThat(schemaReadiness)
                .contains("test_cases', 'semantic_code")
                .contains("submission_case_results', 'test_case_id")
                .contains("uk_test_case_semantic_code")
                .contains("idx_submission_case_results_test_case");
        assertThat(integration)
                .contains("[[ \"${VERSION}\" == \"20\" ]]")
                .contains("V1-V20")
                .contains("check-test-case-semantic-quality.sh");
        assertThat(deploy).contains("check-test-case-semantic-quality.sh");
    }

    @Test
    void learningActionV9AddsStableEvidenceSnapshotsWithoutDestructiveChanges() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V9__close_learning_action_evidence_loop.sql"));
        String schemaReadiness = read("check-database-schema-readiness.sh");

        assertThat(migration)
                .contains("ADD COLUMN IF NOT EXISTS source_submission_id")
                .contains("ADD COLUMN IF NOT EXISTS focus_point_keys")
                .contains("ADD COLUMN IF NOT EXISTS focus_test_semantic_codes")
                .contains("ADD COLUMN IF NOT EXISTS followup_point_keys")
                .contains("ADD COLUMN IF NOT EXISTS followup_failed_test_semantic_codes")
                .contains("idx_reco_events_source_submission")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(schemaReadiness)
                .contains("student_recommendation_events', 'focus_point_keys")
                .contains("student_recommendation_events', 'followup_failed_test_semantic_codes")
                .contains("idx_reco_events_source_submission");
    }

    @Test
    void disciplineBatchSixAddsBalancedClosedThemePacksWithoutDestructiveChanges() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V11__expand_discipline_library_batch_6.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");

        assertThat(migration)
                .contains("informatics-knowledge-discipline-v6")
                .contains("informatics-discipline-quality-v6")
                .contains("informatics-discipline-application-v2")
                .contains("expected exactly 48 theme packs")
                .contains("expected exactly 48 curated knowledge points")
                .contains("normalized theme pack counts are incomplete")
                .contains("effective text volume")
                .contains("INSERT INTO public.ai_standard_skill_units")
                .contains("INSERT INTO public.ai_standard_mistake_points")
                .contains("INSERT INTO public.ai_standard_improvement_points")
                .contains("INSERT INTO public.ai_standard_library_items")
                .contains("INSERT INTO public.ai_standard_library_legacy_mappings")
                .contains("INSERT INTO public.ai_standard_application_scenarios")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("discipline_v6_curated_knowledge")
                .contains("discipline_v6_domain_count")
                .contains("discipline_v6_unbalanced_domains")
                .contains("discipline_v6_skill_units")
                .contains("discipline_v6_mistake_points")
                .contains("discipline_v6_improvement_points")
                .contains("discipline_v6_application_scenarios")
                .contains("discipline_v6_incomplete_theme_packs")
                .contains("discipline_v6_duplicate_mistake_content")
                .contains("discipline_v6_invalid_content")
                .contains("discipline_v6_missing_flat_or_mapping")
                .contains("discipline_v6_effective_text_chars")
                .contains("template_knowledge_descriptions_batch_6_limit");
    }

    @Test
    void standardLibraryV12GovernsExistingContentWithoutChangingStableFacts() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V12__govern_standard_knowledge_library_quality.sql"));
        String qualityGate = read("check-discipline-data-quality.sh");
        String integration = read("test-postgres-migrations.sh");

        assertThat(migration)
                .contains("standard_library_v12_knowledge_curations")
                .contains("standard_library_v12_repair_targets")
                .contains("该条目只保留旧版 code 兼容")
                .contains("未确认重叠语义就按模式长度推进")
                .contains("匹配后按完整模式长度推进导致漏计重叠")
                .contains("V12 meta repair instructions remain")
                .contains("V12 normalized mistakes and flat snapshots diverged")
                .contains("V12 changed business facts")
                .doesNotContain("DELETE FROM", "TRUNCATE TABLE", "DROP TABLE public");
        assertThat(qualityGate)
                .contains("v12_enabled_knowledge_duplicate_names")
                .contains("v12_enabled_mistake_duplicate_names")
                .contains("v12_meta_repair_instructions")
                .contains("v12_actionable_legacy_repairs")
                .contains("v12_compatibility_routing_contracts")
                .contains("v12_repaired_flat_snapshot_mismatch")
                .contains("v12_v11_transfer_pair_same_teacher_move")
                .contains("template_knowledge_descriptions_v12_limit");
        assertThat(integration)
                .contains("[[ \"${VERSION}\" == \"20\" ]]")
                .contains("V1-V20");
    }

    private String read(String name) throws IOException {
        return Files.readString(SCRIPTS.resolve(name));
    }
}
