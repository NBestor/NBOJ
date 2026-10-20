package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentHintEvalFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void studentHintFixturesAreHighQualityAndBalanced() throws IOException {
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadFixtures();

        assertThat(fixtures).hasSize(100);
        assertThat(fixtures)
                .extracting(StudentHintEvalFixtureLoader.Fixture::name)
                .doesNotHaveDuplicates();

        Map<String, Integer> bugPatternCounts = new LinkedHashMap<>();
        fixtures.forEach(fixture -> {
            bugPatternCounts.merge(fixture.quality().bugPattern(), 1, Integer::sum);
            assertThat(fixture.source()).isEqualTo("synthetic-student-hint-v1");
            assertThat(fixture.problem().description()).hasSizeGreaterThan(20);
            assertThat(fixture.submission().sourceCode()).hasSizeGreaterThan(20);
            assertThat(fixture.expected().issueTags()).isNotEmpty();
            assertThat(fixture.expected().teachingAction()).isNotBlank();
            assertThat(fixture.expected().mustMention()).isNotEmpty();
            assertThat(fixture.expected().mustNotMention())
                    .contains("完整代码", "参考答案", "隐藏测试点");
            assertThat(fixture.quality().misconception()).isNotBlank();
            assertThat(fixture.quality().expectedStudentMove()).isNotBlank();
        });

        assertThat(bugPatternCounts).hasSize(20);
        assertThat(bugPatternCounts.values()).allMatch(count -> count == 5);
    }

    @Test
    void negativeFixturesAreBalancedAndHaveForbiddenTags() throws IOException {
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadNegativeFixtures();

        assertThat(fixtures).hasSize(30);
        assertThat(fixtures)
                .extracting(StudentHintEvalFixtureLoader.Fixture::name)
                .doesNotHaveDuplicates();

        Map<String, Integer> bugPatternCounts = new LinkedHashMap<>();
        fixtures.forEach(fixture -> {
            bugPatternCounts.merge(fixture.quality().bugPattern(), 1, Integer::sum);
            assertThat(fixture.source()).isEqualTo("synthetic-student-hint-negative-v1");
            assertThat(fixture.problem().description()).hasSizeGreaterThan(20);
            assertThat(fixture.submission().sourceCode()).hasSizeGreaterThan(20);
            assertThat(fixture.expected().forbiddenIssueTags())
                    .as(fixture.name() + " forbidden issue tags")
                    .isNotNull();
            assertThat(fixture.expected().forbiddenFineTags())
                    .as(fixture.name() + " forbidden fine tags")
                    .isNotNull();
            assertThat(fixture.expected().forbiddenIssueTags().isEmpty()
                    && fixture.expected().forbiddenFineTags().isEmpty())
                    .as(fixture.name() + " has at least one forbidden tag")
                    .isFalse();
        });

        assertThat(bugPatternCounts).hasSize(6);
        assertThat(bugPatternCounts.values()).allMatch(count -> count == 5);
    }

    @Test
    void publicReplayFixturesAreAttributedAndSafe() throws IOException {
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadPublicReplayFixtures();

        assertThat(fixtures).hasSize(25);
        assertThat(fixtures)
                .extracting(StudentHintEvalFixtureLoader.Fixture::name)
                .doesNotHaveDuplicates();

        Map<String, Integer> verdictCounts = new LinkedHashMap<>();
        fixtures.forEach(fixture -> {
            verdictCounts.merge(fixture.submission().verdict(), 1, Integer::sum);
            assertThat(fixture.source()).isEqualTo("codestream-mendeley-cc-by-4.0-v1");
            assertThat(fixture.problem().description()).hasSizeGreaterThan(80);
            assertThat(fixture.submission().sourceCode()).hasSizeGreaterThan(80);
            assertThat(fixture.expected().issueTags()).isNotEmpty();
            assertThat(fixture.expected().teachingAction()).isNotBlank();
            assertThat(fixture.expected().requiredEvidenceRefs()).isNotEmpty();
            assertThat(fixture.quality().bugPattern()).startsWith("public-");
            assertThat(fixture.quality().misconception()).isNotBlank();
            assertThat(fixture.quality().expectedStudentMove()).isNotBlank();
            assertThat(String.join("\n",
                    fixture.name(),
                    fixture.problem().description(),
                    fixture.submission().sourceCode(),
                    fixture.baseline().analysisHeadline(),
                    fixture.baseline().studentHint(),
                    fixture.quality().misconception(),
                    fixture.quality().expectedStudentMove()
            )).doesNotContain("user_");
        });

        assertThat(verdictCounts.keySet())
                .contains("ACCEPTED", "WRONG_ANSWER", "TIME_LIMIT_EXCEEDED",
                        "MEMORY_LIMIT_EXCEEDED", "RUNTIME_ERROR", "COMPILATION_ERROR");
    }

    @Test
    void publicAttemptChainFixturesCoverLearningTrajectorySignals() throws IOException {
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadPublicAttemptChainFixtures();

        assertThat(fixtures).hasSize(12);
        assertThat(fixtures)
                .extracting(StudentHintEvalFixtureLoader.Fixture::name)
                .doesNotHaveDuplicates();

        Map<String, Integer> phaseCounts = new LinkedHashMap<>();
        fixtures.forEach(fixture -> {
            phaseCounts.merge(fixture.expected().trajectoryPhase(), 1, Integer::sum);
            assertThat(fixture.source()).isEqualTo("codestream-mendeley-cc-by-4.0-v1");
            assertThat(fixture.problem().description()).hasSizeGreaterThan(80);
            assertThat(fixture.submission().sourceCode()).hasSizeGreaterThan(80);
            assertThat(fixture.history()).isNotNull();
            assertThat(fixture.history().previousVerdict()).isNotBlank();
            assertThat(fixture.expected().trajectoryPhase()).isNotBlank();
            assertThat(fixture.expected().requiredEvidenceRefs()).isNotEmpty();
            assertThat(fixture.quality().bugPattern()).startsWith("public-chain-");
            assertThat(String.join("\n",
                    fixture.name(),
                    fixture.problem().description(),
                    fixture.submission().sourceCode(),
                    fixture.history().transitionSignal(),
                    fixture.quality().misconception(),
                    fixture.quality().expectedStudentMove()
            )).doesNotContain("user_");
        });

        assertThat(phaseCounts.keySet())
                .contains("FIXED_COMPILATION", "REPEATED_STUCK",
                        "RUNTIME_FIXED_CORRECTNESS_REMAINS", "ACCEPTED_AFTER_FIX", "REGRESSION");
    }

    @Test
    void backendAgentProducesStructuredStudentHintPlansForEvalFixtures() throws IOException {
        DiagnosticAgentService service = newService();
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadFixtures();

        int exactTeachingActionMatches = 0;
        int issueTagHits = 0;
        int fineTagHits = 0;
        int evidenceHits = 0;
        int safeOutputs = 0;
        int structuredPlans = 0;
        int structuredInterventions = 0;

        for (StudentHintEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    fixture.toBaseline(),
                    Assignment.HintPolicy.L2,
                    fixture.toHistoryEvidence()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            SubmissionAnalysisResponse.StudentHintPlan plan = analysis.getStudentHintPlan();

            assertThat(plan)
                    .as(fixture.name() + " studentHintPlan")
                    .isNotNull();
            assertThat(plan.getHintLevel())
                    .as(fixture.name() + " hint level")
                    .isEqualTo("L2");
            assertThat(plan.getProblemType())
                    .as(fixture.name() + " problem type")
                    .isNotBlank();
            assertThat(plan.getEvidenceAnchor())
                    .as(fixture.name() + " evidence anchor")
                    .isNotBlank();
            assertThat(plan.getNextAction())
                    .as(fixture.name() + " next action")
                    .isNotBlank();
            assertThat(plan.getCoachQuestion())
                    .as(fixture.name() + " coach question")
                    .isNotBlank()
                    .endsWith("？");
            assertThat(plan.getAnswerLeakRisk())
                    .as(fixture.name() + " answer leak risk")
                    .isIn("LOW", "MEDIUM", "UNKNOWN");
            assertStructuredIntervention(fixture.name(), analysis);
            assertThat(analysis.getDiagnosticTrace())
                    .as(fixture.name() + " diagnostic trace")
                    .contains("diagnostic-agent-v2");
            assertThat(analysis.getAiInvocation())
                    .as(fixture.name() + " ai invocation")
                    .isNotNull();

            if (analysis.getIssueTags().stream().anyMatch(fixture.expected().issueTags()::contains)) {
                issueTagHits += 1;
            }
            if (fixture.expected().fineTags().isEmpty()
                    || analysis.getFineGrainedTags().stream().anyMatch(fixture.expected().fineTags()::contains)) {
                fineTagHits += 1;
            }
            if (matchesTeachingAction(fixture, plan.getTeachingAction())) {
                exactTeachingActionMatches += 1;
            }
            if (fixture.expected().requiredEvidenceRefs().isEmpty()
                    || analysis.getEvidenceRefs().stream().anyMatch(fixture.expected().requiredEvidenceRefs()::contains)) {
                evidenceHits += 1;
            }
            structuredPlans += 1;
            structuredInterventions += 1;
            boolean safeOutput = true;
            for (String forbiddenPhrase : fixture.expected().mustNotMention()) {
                if (combinedStudentFacingText(analysis).contains(forbiddenPhrase)) {
                    safeOutput = false;
                }
            }
            if (safeOutput) {
                safeOutputs += 1;
            }
        }

        ObjectiveScore score = objectiveScore(
                issueTagHits,
                fineTagHits,
                exactTeachingActionMatches,
                evidenceHits,
                fixtures.size(),
                0,
                0,
                0,
                safeOutputs,
                structuredPlans
        );
        System.out.println("Student hint objective positive eval: " + score);

        assertThat(issueTagHits).as("issue tag hit rate").isGreaterThanOrEqualTo(85);
        assertThat(fineTagHits).as("fine tag hit rate").isGreaterThanOrEqualTo(70);
        assertThat(exactTeachingActionMatches).as("teaching action hit rate").isGreaterThanOrEqualTo(70);
        assertThat(evidenceHits).as("evidence hit rate").isGreaterThanOrEqualTo(80);
        assertThat(safeOutputs).as("safe output rate").isEqualTo(fixtures.size());
        assertThat(structuredPlans).as("structured plan rate").isEqualTo(fixtures.size());
        assertThat(structuredInterventions).as("structured intervention rate").isEqualTo(fixtures.size());
    }

    @Test
    @Disabled("旧本地规则信号器已归档，学生主链路不再追求本地规则信号覆盖率。")
    void backendAgentBlindEvalExposesRuleSignalCoverage() throws IOException {
        DiagnosticAgentService service = newService();
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadFixtures();

        Map<String, BlindStats> statsByPattern = new LinkedHashMap<>();
        int issueTagHits = 0;
        int fineTagHits = 0;
        int teachingActionMatches = 0;
        int evidenceHits = 0;
        int safeOutputs = 0;
        int structuredPlans = 0;
        int structuredInterventions = 0;

        for (StudentHintEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    blindBaseline(fixture),
                    Assignment.HintPolicy.L2,
                    fixture.toHistoryEvidence()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            SubmissionAnalysisResponse.StudentHintPlan plan = analysis.getStudentHintPlan();
            BlindStats stats = statsByPattern.computeIfAbsent(fixture.quality().bugPattern(), ignored -> new BlindStats());
            stats.total += 1;

            if (analysis.getIssueTags().stream().anyMatch(fixture.expected().issueTags()::contains)) {
                issueTagHits += 1;
                stats.issueHits += 1;
            }
            if (fixture.expected().fineTags().isEmpty()
                    || analysis.getFineGrainedTags().stream().anyMatch(fixture.expected().fineTags()::contains)) {
                fineTagHits += 1;
                stats.fineHits += 1;
            }
            if (plan != null && matchesTeachingAction(fixture, plan.getTeachingAction())) {
                teachingActionMatches += 1;
                stats.teachingHits += 1;
            }
            if (fixture.expected().requiredEvidenceRefs().isEmpty()
                    || analysis.getEvidenceRefs().stream().anyMatch(fixture.expected().requiredEvidenceRefs()::contains)) {
                evidenceHits += 1;
                stats.evidenceHits += 1;
            }
            if (plan != null) {
                structuredPlans += 1;
            }
            if (isStructuredIntervention(analysis)) {
                structuredInterventions += 1;
            }
            boolean safeOutput = true;
            for (String forbiddenPhrase : fixture.expected().mustNotMention()) {
                if (combinedStudentFacingText(analysis).contains(forbiddenPhrase)) {
                    safeOutput = false;
                }
            }
            if (safeOutput) {
                safeOutputs += 1;
            }
            stats.actualFineTags.addAll(analysis.getFineGrainedTags());
        }

        System.out.println(blindEvalReport(statsByPattern, issueTagHits, fineTagHits, teachingActionMatches, evidenceHits, fixtures.size()));
        ObjectiveScore score = objectiveScore(
                issueTagHits,
                fineTagHits,
                teachingActionMatches,
                evidenceHits,
                fixtures.size(),
                0,
                0,
                0,
                safeOutputs,
                structuredPlans
        );
        System.out.println("Student hint objective blind eval: " + score);

        assertThat(issueTagHits).as("blind issue tag hit rate").isGreaterThanOrEqualTo(70);
        assertThat(fineTagHits).as("blind fine tag hit rate").isGreaterThanOrEqualTo(45);
        assertThat(teachingActionMatches).as("blind teaching action hit rate").isGreaterThanOrEqualTo(45);
        assertThat(evidenceHits).as("blind evidence hit rate").isGreaterThanOrEqualTo(70);
        assertObjectiveMeetsCurrentGate(score);
        assertThat(structuredInterventions).as("blind structured intervention rate").isEqualTo(fixtures.size());
    }

    @Test
    @Disabled("旧本地规则信号器已归档，负例规则信号覆盖门禁不再适用。")
    void backendAgentNegativeEvalGuardsAgainstFalsePositiveTags() throws IOException {
        DiagnosticAgentService service = newService();
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadNegativeFixtures();

        Map<String, NegativeStats> statsByPattern = new LinkedHashMap<>();
        int issueFalsePositives = 0;
        int fineFalsePositives = 0;
        int requiredIssueHits = 0;
        int requiredFineHits = 0;
        int evidenceHits = 0;
        int safeOutputs = 0;
        int structuredPlans = 0;
        int structuredInterventions = 0;

        for (StudentHintEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    blindBaseline(fixture),
                    Assignment.HintPolicy.L2,
                    fixture.toHistoryEvidence()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            SubmissionAnalysisResponse.StudentHintPlan plan = analysis.getStudentHintPlan();
            NegativeStats stats = statsByPattern.computeIfAbsent(fixture.quality().bugPattern(), ignored -> new NegativeStats());
            stats.total += 1;

            boolean issueFalsePositive = intersects(analysis.getIssueTags(), fixture.expected().forbiddenIssueTags());
            boolean fineFalsePositive = intersects(analysis.getFineGrainedTags(), fixture.expected().forbiddenFineTags());
            if (issueFalsePositive) {
                issueFalsePositives += 1;
                stats.issueFalsePositives += 1;
            }
            if (fineFalsePositive) {
                fineFalsePositives += 1;
                stats.fineFalsePositives += 1;
            }
            if (fixture.expected().issueTags().isEmpty()
                    || analysis.getIssueTags().stream().anyMatch(fixture.expected().issueTags()::contains)) {
                requiredIssueHits += 1;
                stats.requiredIssueHits += 1;
            }
            if (fixture.expected().fineTags().isEmpty()
                    || analysis.getFineGrainedTags().stream().anyMatch(fixture.expected().fineTags()::contains)) {
                requiredFineHits += 1;
                stats.requiredFineHits += 1;
            }
            if (fixture.expected().requiredEvidenceRefs().isEmpty()
                    || analysis.getEvidenceRefs().stream().anyMatch(fixture.expected().requiredEvidenceRefs()::contains)) {
                evidenceHits += 1;
                stats.evidenceHits += 1;
            }
            assertThat(plan)
                    .as(fixture.name() + " studentHintPlan")
                    .isNotNull();
            structuredPlans += 1;
            assertThat(plan.getAnswerLeakRisk())
                    .as(fixture.name() + " answer leak risk")
                    .isIn("LOW", "MEDIUM", "UNKNOWN");
            assertStructuredIntervention(fixture.name(), analysis);
            structuredInterventions += 1;
            boolean safeOutput = true;
            for (String forbiddenPhrase : fixture.expected().mustNotMention()) {
                if (combinedStudentFacingText(analysis).contains(forbiddenPhrase)) {
                    safeOutput = false;
                }
            }
            if (safeOutput) {
                safeOutputs += 1;
            }
            stats.actualIssueTags.addAll(analysis.getIssueTags());
            stats.actualFineTags.addAll(analysis.getFineGrainedTags());
        }

        System.out.println(negativeEvalReport(
                statsByPattern,
                issueFalsePositives,
                fineFalsePositives,
                requiredIssueHits,
                requiredFineHits,
                evidenceHits,
                fixtures.size()
        ));
        ObjectiveScore score = objectiveScore(
                requiredIssueHits,
                fixtures.size(),
                fixtures.size(),
                evidenceHits,
                fixtures.size(),
                issueFalsePositives,
                fineFalsePositives,
                fixtures.size(),
                safeOutputs,
                structuredPlans
        );
        System.out.println("Student hint objective negative eval: " + score);

        assertThat(issueFalsePositives).as("negative issue false positives").isEqualTo(0);
        assertThat(fineFalsePositives).as("negative fine false positives").isEqualTo(0);
        assertThat(requiredIssueHits).as("negative required issue hit rate").isGreaterThanOrEqualTo(25);
        assertThat(requiredFineHits).as("negative required fine hit rate").isEqualTo(fixtures.size());
        assertThat(evidenceHits).as("negative evidence hit rate").isGreaterThanOrEqualTo(25);
        assertObjectiveMeetsCurrentGate(score);
        assertThat(structuredInterventions).as("negative structured intervention rate").isEqualTo(fixtures.size());
    }

    @Test
    @Disabled("旧本地规则信号器已归档，公开回放的本地 verdict 规则信号门禁不再适用。")
    void backendAgentPublicReplayEvalKeepsVerdictLevelSignalsStable() throws IOException {
        DiagnosticAgentService service = newService();
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadPublicReplayFixtures();

        Map<String, BlindStats> statsByPattern = new LinkedHashMap<>();
        int issueTagHits = 0;
        int evidenceHits = 0;
        int fineFalsePositives = 0;
        int safeOutputs = 0;
        int structuredPlans = 0;
        int structuredInterventions = 0;

        for (StudentHintEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    blindBaseline(fixture),
                    Assignment.HintPolicy.L2,
                    fixture.toHistoryEvidence()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            SubmissionAnalysisResponse.StudentHintPlan plan = analysis.getStudentHintPlan();
            BlindStats stats = statsByPattern.computeIfAbsent(fixture.quality().bugPattern(), ignored -> new BlindStats());
            stats.total += 1;

            if (analysis.getIssueTags().stream().anyMatch(fixture.expected().issueTags()::contains)) {
                issueTagHits += 1;
                stats.issueHits += 1;
            }
            if (fixture.expected().requiredEvidenceRefs().isEmpty()
                    || analysis.getEvidenceRefs().stream().anyMatch(fixture.expected().requiredEvidenceRefs()::contains)) {
                evidenceHits += 1;
                stats.evidenceHits += 1;
            }
            if (intersects(analysis.getFineGrainedTags(), fixture.expected().forbiddenFineTags())) {
                fineFalsePositives += 1;
                stats.fineFalsePositives += 1;
            }
            if (plan != null) {
                structuredPlans += 1;
            }
            if (isStructuredIntervention(analysis)) {
                structuredInterventions += 1;
            }
            boolean safeOutput = true;
            for (String forbiddenPhrase : fixture.expected().mustNotMention()) {
                if (combinedStudentFacingText(analysis).contains(forbiddenPhrase)) {
                    safeOutput = false;
                }
            }
            if (safeOutput) {
                safeOutputs += 1;
            }
            stats.actualFineTags.addAll(analysis.getFineGrainedTags());
        }

        System.out.println(publicReplayEvalReport(statsByPattern, issueTagHits, evidenceHits, fineFalsePositives, fixtures.size()));
        ObjectiveScore score = objectiveScore(
                issueTagHits,
                fixtures.size(),
                fixtures.size(),
                evidenceHits,
                fixtures.size(),
                0,
                fineFalsePositives,
                fixtures.size(),
                safeOutputs,
                structuredPlans
        );
        System.out.println("Student hint objective public replay eval: " + score);

        assertThat(issueTagHits).as("public replay verdict-level issue hit rate").isGreaterThanOrEqualTo(24);
        assertThat(evidenceHits).as("public replay evidence hit rate").isGreaterThanOrEqualTo(24);
        assertThat(fineFalsePositives).as("public replay forbidden fine tags").isEqualTo(0);
        assertThat(safeOutputs).as("public replay safe output rate").isEqualTo(fixtures.size());
        assertThat(structuredPlans).as("public replay structured plan rate").isEqualTo(fixtures.size());
        assertThat(structuredInterventions).as("public replay structured intervention rate").isEqualTo(fixtures.size());
        assertObjectiveMeetsCurrentGate(score);
    }

    @Test
    void backendAgentPublicAttemptChainEvalMatchesLearningTrajectoryPhases() throws IOException {
        DiagnosticAgentService service = newService();
        List<StudentHintEvalFixtureLoader.Fixture> fixtures = loadPublicAttemptChainFixtures();

        Map<String, BlindStats> statsByPattern = new LinkedHashMap<>();
        int phaseHits = 0;
        int teacherAttentionHits = 0;
        int evidenceHits = 0;
        int structuredSignals = 0;
        int structuredInterventions = 0;
        int safeOutputs = 0;

        for (StudentHintEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    blindBaseline(fixture),
                    Assignment.HintPolicy.L2,
                    fixture.toHistoryEvidence()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            SubmissionAnalysisResponse.LearningTrajectorySignal trajectory = analysis.getLearningTrajectorySignal();
            BlindStats stats = statsByPattern.computeIfAbsent(fixture.quality().bugPattern(), ignored -> new BlindStats());
            stats.total += 1;

            assertThat(trajectory)
                    .as(fixture.name() + " learningTrajectorySignal")
                    .isNotNull();
            structuredSignals += 1;
            if (fixture.expected().trajectoryPhase().equals(trajectory.getPhase())) {
                phaseHits += 1;
                stats.issueHits += 1;
            }
            boolean expectedAttention = Boolean.TRUE.equals(fixture.expected().needsTeacherAttention());
            if (trajectory.isNeedsTeacherAttention() == expectedAttention) {
                teacherAttentionHits += 1;
                stats.teachingHits += 1;
            }
            if (fixture.expected().requiredEvidenceRefs().isEmpty()
                    || analysis.getEvidenceRefs().stream().anyMatch(fixture.expected().requiredEvidenceRefs()::contains)) {
                evidenceHits += 1;
                stats.evidenceHits += 1;
            }
            assertThat(analysis.getProgressSignal())
                    .as(fixture.name() + " progress signal")
                    .isNotBlank();
            assertStructuredIntervention(fixture.name(), analysis);
            structuredInterventions += 1;
            assertThat(analysis.getDiagnosticTrace())
                    .as(fixture.name() + " diagnostic trace")
                    .contains("trajectory=" + fixture.expected().trajectoryPhase());

            boolean safeOutput = true;
            for (String forbiddenPhrase : fixture.expected().mustNotMention()) {
                if (combinedStudentFacingText(analysis).contains(forbiddenPhrase)) {
                    safeOutput = false;
                }
            }
            if (safeOutput) {
                safeOutputs += 1;
            }
        }

        System.out.println(publicAttemptChainEvalReport(
                statsByPattern,
                phaseHits,
                teacherAttentionHits,
                evidenceHits,
                structuredSignals,
                fixtures.size()
        ));

        assertThat(phaseHits).as("learning trajectory phase hit rate").isEqualTo(fixtures.size());
        assertThat(teacherAttentionHits).as("teacher attention hit rate").isEqualTo(fixtures.size());
        assertThat(evidenceHits).as("trajectory evidence hit rate").isEqualTo(fixtures.size());
        assertThat(structuredSignals).as("structured trajectory signal rate").isEqualTo(fixtures.size());
        assertThat(structuredInterventions).as("public attempt-chain structured intervention rate").isEqualTo(fixtures.size());
        assertThat(safeOutputs).as("public attempt-chain safe output rate").isEqualTo(fixtures.size());
    }

    private void assertStructuredIntervention(String caseName, SubmissionAnalysisResponse analysis) {
        assertThat(analysis.getLearningInterventionPlan())
                .as(caseName + " learningInterventionPlan")
                .isNotNull();
        assertThat(analysis.getLearningInterventionPlan().getInterventionType())
                .as(caseName + " intervention type")
                .isNotBlank();
        assertThat(analysis.getLearningInterventionPlan().getStudentTask())
                .as(caseName + " intervention student task")
                .isNotBlank();
        assertThat(analysis.getLearningInterventionPlan().getCheckQuestion())
                .as(caseName + " intervention check question")
                .isNotBlank();
        assertThat(analysis.getLearningInterventionPlan().getCompletionSignal())
                .as(caseName + " intervention completion signal")
                .isNotBlank();
        assertThat(analysis.getLearningInterventionPlan().getAnswerLeakRisk())
                .as(caseName + " intervention answer leak risk")
                .isIn("LOW", "MEDIUM", "UNKNOWN");
    }

    private boolean isStructuredIntervention(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.LearningInterventionPlan plan = analysis.getLearningInterventionPlan();
        return plan != null
                && plan.getInterventionType() != null && !plan.getInterventionType().isBlank()
                && plan.getStudentTask() != null && !plan.getStudentTask().isBlank()
                && plan.getCheckQuestion() != null && !plan.getCheckQuestion().isBlank()
                && plan.getCompletionSignal() != null && !plan.getCompletionSignal().isBlank();
    }

    private String combinedStudentFacingText(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.StudentHintPlan plan = analysis.getStudentHintPlan();
        SubmissionAnalysisResponse.LearningInterventionPlan interventionPlan = analysis.getLearningInterventionPlan();
        return String.join("\n",
                safe(analysis.getStudentHint()),
                plan == null ? "" : safe(plan.getProblemType()),
                plan == null ? "" : safe(plan.getEvidenceAnchor()),
                plan == null ? "" : safe(plan.getNextAction()),
                plan == null ? "" : safe(plan.getCoachQuestion()),
                interventionPlan == null ? "" : safe(interventionPlan.getGoal()),
                interventionPlan == null ? "" : safe(interventionPlan.getStudentTask()),
                interventionPlan == null ? "" : safe(interventionPlan.getCheckQuestion()),
                interventionPlan == null ? "" : safe(interventionPlan.getCompletionSignal()),
                safe(analysis.getReportMarkdown())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean matchesTeachingAction(StudentHintEvalFixtureLoader.Fixture fixture, String actual) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        List<String> acceptable = fixture.expected().acceptableTeachingActions();
        if (acceptable != null && !acceptable.isEmpty()) {
            return acceptable.contains(actual);
        }
        return actual.equals(fixture.expected().teachingAction());
    }

    private List<StudentHintEvalFixtureLoader.Fixture> loadFixtures() throws IOException {
        return new StudentHintEvalFixtureLoader(objectMapper).loadDefault();
    }

    private List<StudentHintEvalFixtureLoader.Fixture> loadNegativeFixtures() throws IOException {
        return new StudentHintEvalFixtureLoader(objectMapper).loadNegative();
    }

    private List<StudentHintEvalFixtureLoader.Fixture> loadPublicReplayFixtures() throws IOException {
        return new StudentHintEvalFixtureLoader(objectMapper).loadPublicReplay();
    }

    private List<StudentHintEvalFixtureLoader.Fixture> loadPublicAttemptChainFixtures() throws IOException {
        return new StudentHintEvalFixtureLoader(objectMapper).loadPublicAttemptChains();
    }

    private boolean intersects(List<String> actual, List<String> forbidden) {
        if (actual == null || forbidden == null || forbidden.isEmpty()) {
            return false;
        }
        return actual.stream().anyMatch(forbidden::contains);
    }

    private SubmissionAnalysisResponse blindBaseline(StudentHintEvalFixtureLoader.Fixture fixture) {
        return SubmissionAnalysisResponse.builder()
                .submissionId(fixture.toBaseline().getSubmissionId())
                .sourceType("STUDENT_HINT_BLIND_EVAL")
                .scenario(fixture.baseline().scenario())
                .headline("待诊断")
                .summary("仅根据评测事实、代码形态和历史信号诊断。")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of("eval:student_hint:" + fixture.caseId()))
                .firstFailedCase(fixture.toBaseline().getFirstFailedCase())
                .confidence(0.72)
                .answerLeakRisk("LOW")
                .build();
    }

    private String blindEvalReport(Map<String, BlindStats> statsByPattern,
                                   int issueTagHits,
                                   int fineTagHits,
                                   int teachingActionMatches,
                                   int evidenceHits,
                                   int total) {
        List<String> lines = new ArrayList<>();
        lines.add("Student hint blind eval summary:");
        lines.add("issueTags=" + issueTagHits + "/" + total
                + ", fineTags=" + fineTagHits + "/" + total
                + ", teachingActions=" + teachingActionMatches + "/" + total
                + ", evidenceRefs=" + evidenceHits + "/" + total);
        statsByPattern.forEach((pattern, stats) -> lines.add(pattern
                + " issue=" + stats.issueHits + "/" + stats.total
                + " fine=" + stats.fineHits + "/" + stats.total
                + " teaching=" + stats.teachingHits + "/" + stats.total
                + " evidence=" + stats.evidenceHits + "/" + stats.total
                + " actualFineTags=" + stats.actualFineTags));
        return String.join(System.lineSeparator(), lines);
    }

    private String negativeEvalReport(Map<String, NegativeStats> statsByPattern,
                                      int issueFalsePositives,
                                      int fineFalsePositives,
                                      int requiredIssueHits,
                                      int requiredFineHits,
                                      int evidenceHits,
                                      int total) {
        List<String> lines = new ArrayList<>();
        lines.add("Student hint negative eval summary:");
        lines.add("issueFalsePositives=" + issueFalsePositives + "/" + total
                + ", fineFalsePositives=" + fineFalsePositives + "/" + total
                + ", requiredIssueHits=" + requiredIssueHits + "/" + total
                + ", requiredFineHits=" + requiredFineHits + "/" + total
                + ", evidenceRefs=" + evidenceHits + "/" + total);
        statsByPattern.forEach((pattern, stats) -> lines.add(pattern
                + " issueFalsePositives=" + stats.issueFalsePositives + "/" + stats.total
                + " fineFalsePositives=" + stats.fineFalsePositives + "/" + stats.total
                + " requiredIssue=" + stats.requiredIssueHits + "/" + stats.total
                + " requiredFine=" + stats.requiredFineHits + "/" + stats.total
                + " evidence=" + stats.evidenceHits + "/" + stats.total
                + " actualIssueTags=" + stats.actualIssueTags
                + " actualFineTags=" + stats.actualFineTags));
        return String.join(System.lineSeparator(), lines);
    }

    private String publicReplayEvalReport(Map<String, BlindStats> statsByPattern,
                                          int issueTagHits,
                                          int evidenceHits,
                                          int fineFalsePositives,
                                          int total) {
        List<String> lines = new ArrayList<>();
        lines.add("Student hint public replay eval summary:");
        lines.add("issueTags=" + issueTagHits + "/" + total
                + ", evidenceRefs=" + evidenceHits + "/" + total
                + ", fineFalsePositives=" + fineFalsePositives + "/" + total);
        statsByPattern.forEach((pattern, stats) -> lines.add(pattern
                + " issue=" + stats.issueHits + "/" + stats.total
                + " evidence=" + stats.evidenceHits + "/" + stats.total
                + " fineFalsePositives=" + stats.fineFalsePositives + "/" + stats.total
                + " actualFineTags=" + stats.actualFineTags));
        return String.join(System.lineSeparator(), lines);
    }

    private String publicAttemptChainEvalReport(Map<String, BlindStats> statsByPattern,
                                                int phaseHits,
                                                int teacherAttentionHits,
                                                int evidenceHits,
                                                int structuredSignals,
                                                int total) {
        List<String> lines = new ArrayList<>();
        lines.add("Student hint public attempt-chain eval summary:");
        lines.add("trajectoryPhases=" + phaseHits + "/" + total
                + ", teacherAttention=" + teacherAttentionHits + "/" + total
                + ", evidenceRefs=" + evidenceHits + "/" + total
                + ", structuredSignals=" + structuredSignals + "/" + total);
        statsByPattern.forEach((pattern, stats) -> lines.add(pattern
                + " phase=" + stats.issueHits + "/" + stats.total
                + " teacherAttention=" + stats.teachingHits + "/" + stats.total
                + " evidence=" + stats.evidenceHits + "/" + stats.total));
        return String.join(System.lineSeparator(), lines);
    }

    private ObjectiveScore objectiveScore(int issueHits,
                                          int fineHits,
                                          int teachingHits,
                                          int evidenceHits,
                                          int positiveTotal,
                                          int issueFalsePositives,
                                          int fineFalsePositives,
                                          int negativeTotal,
                                          int safeOutputs,
                                          int structuredPlans) {
        double positiveRecall = positiveTotal == 0
                ? 1.0
                : (rate(issueHits, positiveTotal)
                + rate(fineHits, positiveTotal)
                + rate(teachingHits, positiveTotal)
                + rate(evidenceHits, positiveTotal)) / 4.0;
        double negativePrecision = negativeTotal == 0
                ? 1.0
                : 1.0 - rate(issueFalsePositives + fineFalsePositives, negativeTotal * 2);
        int safetyDenominator = positiveTotal == 0 ? negativeTotal : positiveTotal;
        double safety = safetyDenominator == 0 ? 1.0 : rate(safeOutputs, safetyDenominator);
        double structureCompleteness = safetyDenominator == 0 ? 1.0 : rate(structuredPlans, safetyDenominator);
        double objective = 0.45 * positiveRecall
                + 0.35 * negativePrecision
                + 0.10 * safety
                + 0.10 * structureCompleteness;
        return new ObjectiveScore(positiveRecall, negativePrecision, safety, structureCompleteness, objective);
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 1.0;
        }
        return (double) numerator / denominator;
    }

    private void assertObjectiveMeetsCurrentGate(ObjectiveScore score) {
        assertThat(score.objective()).as("objective").isGreaterThanOrEqualTo(0.95);
        assertThat(score.positiveRecall()).as("positive recall").isGreaterThanOrEqualTo(0.90);
        assertThat(score.negativePrecision()).as("negative precision").isGreaterThanOrEqualTo(0.98);
        assertThat(score.safety()).as("safety").isEqualTo(1.0);
        assertThat(score.structureCompleteness()).as("structure completeness").isEqualTo(1.0);
    }

    private DiagnosticAgentService newService() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        return new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new PassThroughAiReportService(),
                new HintSafetyService(null, objectMapper, taxonomy),
                taxonomy
        );
    }

    private static class PassThroughAiReportService extends AiReportService {
        PassThroughAiReportService() {
            super(new ObjectMapper(), new AiCodeAssistSupport());
        }

        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(com.onlinejudge.problem.domain.Problem problem,
                                                                    com.onlinejudge.submission.domain.Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            SubmissionAnalysisResponse response = new ObjectMapper().convertValue(fallback, SubmissionAnalysisResponse.class);
            response.setSourceType("AI_MODEL");
            response.setAiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                    .provider("MODEL_SCOPE")
                    .model("deepseek-ai/DeepSeek-V4-Pro")
                    .promptVersion(PromptTemplateRegistry.DIAGNOSIS_REPORT_V2)
                    .status("MODEL_COMPLETED")
                    .fallbackUsed(false)
                    .runtimeMode("diagnosis-report")
                    .runtimeProfile(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD)
                    .requestCompact(false)
                    .build());
            return response;
        }
    }

    private static class BlindStats {
        private int total;
        private int issueHits;
        private int fineHits;
        private int teachingHits;
        private int evidenceHits;
        private int fineFalsePositives;
        private final Set<String> actualFineTags = new LinkedHashSet<>();
    }

    private static class NegativeStats {
        private int total;
        private int issueFalsePositives;
        private int fineFalsePositives;
        private int requiredIssueHits;
        private int requiredFineHits;
        private int evidenceHits;
        private final Set<String> actualIssueTags = new LinkedHashSet<>();
        private final Set<String> actualFineTags = new LinkedHashSet<>();
    }

    private record ObjectiveScore(double positiveRecall,
                                  double negativePrecision,
                                  double safety,
                                  double structureCompleteness,
                                  double objective) {
    }
}
