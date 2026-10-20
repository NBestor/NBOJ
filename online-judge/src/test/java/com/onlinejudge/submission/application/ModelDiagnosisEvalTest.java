package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.eval.LiveEvalBaselineRegressionGate;
import com.onlinejudge.eval.LiveEvalBaselineRegressionReport;
import com.onlinejudge.eval.LiveEvalBaselineRegressionReportFactory;
import com.onlinejudge.eval.LiveEvalQualityBaselineDraft;
import com.onlinejudge.eval.LiveEvalQualityBaselineDraftFactory;
import com.onlinejudge.eval.LiveEvalRuntimeFixtureDraft;
import com.onlinejudge.eval.LiveEvalRuntimeFixtureDraftFactory;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDiagnosisEvalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ComplexDiagnosisQualityScorer complexQualityScorer = new ComplexDiagnosisQualityScorer();
    private final DiagnosisChainRubricEvaluator rubricEvaluator = new DiagnosisChainRubricEvaluator();
    private static final int EXPECTED_COMPLEX_LIVE_CANDIDATE_COUNT = 42;
    private static final List<String> REPRESENTATIVE_COMPLEX_LIVE_CASE_IDS = List.of(
            "complex-live-01-multi-query-prefix",
            "complex-live-02-multi-case-run-reset",
            "complex-live-03-house-dp-state",
            "complex-live-04-coin-greedy-trap",
            "complex-live-05-large-range-simulation",
            "complex-live-06-output-format-extra",
            "complex-live-07-zero-division-guard",
            "complex-live-08-off-by-one-window",
            "complex-live-09-duplicate-count-set",
            "complex-live-10-initial-best-negative",
            "complex-live-11-pair-sum-bruteforce",
            "complex-live-12-sample-overfit-special",
            "complex-live-13-partial-fix-regression",
            "complex-live-14-in-place-swap-progress"
    );

    @Test
    void liveModelKeepsDiagnosisWithinExpectedTagsWhenEnabled() {
        String apiKey = System.getenv("AI_EVAL_API_KEY");
        Assumptions.assumeTrue(Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_LIVE_ENABLED"), "false")),
                "Set AI_EVAL_LIVE_ENABLED=true to run live model diagnosis eval.");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "Set AI_EVAL_API_KEY to run live model diagnosis eval.");
        Assumptions.assumeTrue(Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_FULL"), "false")),
                "Set AI_EVAL_FULL=true to run the full live model diagnosis eval.");

        DiagnosticAgentService service = newLiveService(apiKey);
        List<EvalCase> cases = allEvalCases();

        for (EvalCase evalCase : cases) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    evalCase.problem(),
                    evalCase.submission(),
                    evalCase.caseResults(),
                    evalCase.baseline(),
                    Assignment.HintPolicy.L2
            );

            assertThat(result.analysis().getIssueTags())
                    .as(evalCase.name() + " issue tags")
                    .containsAnyElementsOf(evalCase.expectedIssueTags());
            assertThat(result.analysis().getFineGrainedTags())
                    .as(evalCase.name() + " fine-grained tags")
                    .containsAnyElementsOf(evalCase.expectedFineTags());
            assertThat(result.analysis().getEvidenceRefs())
                    .as(evalCase.name() + " evidence refs")
                    .isNotEmpty();
            assertThat(result.analysis().getAnswerLeakRisk())
                    .as(evalCase.name() + " answer leak risk")
                    .isIn("LOW", "MEDIUM", "UNKNOWN");
            assertThat(result.traceSummary())
                    .as(evalCase.name() + " trace")
                    .contains("diagnostic-agent-v2");
            assertThat(result.analysis().getAiInvocation().isFallbackUsed())
                    .as(evalCase.name() + " live model should be used")
                    .isFalse();
            assertThat(result.traceSummary())
                    .as(evalCase.name() + " trace")
                    .contains("model=completed");
        }
    }

    @Test
    void liveModelSmokeProducesPerCaseReportWhenEnabled() throws IOException {
        String apiKey = System.getenv("AI_EVAL_API_KEY");
        Assumptions.assumeTrue(Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_LIVE_ENABLED"), "false")),
                "Set AI_EVAL_LIVE_ENABLED=true to run live model smoke eval.");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "Set AI_EVAL_API_KEY to run live model smoke eval.");

        DiagnosticAgentService service = newLiveService(apiKey);
        List<EvalCase> cases = selectedLiveEvalCases();

        LiveModelEvalReport report = runLiveEvalReport(service, cases);
        Path reportPath = writeLiveEvalReport(report);

        System.out.println("Live model eval report saved to: " + reportPath);
        System.out.println("Live model eval summary: " + liveModelSummaryLine(report));

        assertThat(report.getEntries()).hasSize(cases.size());
        assertThat(reportPath).exists();
        assertModelBaselineRegressionGateIfEnabled(report, reportPath);
    }

    @Test
    void liveServiceDefaultsRuntimeProfileToLowLatencyWithoutEnvOverride() {
        DiagnosticAgentService service = newLiveService("");
        Object aiReportService = ReflectionTestUtils.getField(service, "aiReportService");

        assertThat(ReflectionTestUtils.getField(aiReportService, "externalRuntimeProfile")).isEqualTo("low-latency");
    }

    @Test
    void evalCasesExposeStableExpectedTagsWithoutLiveModel() {
        List<EvalCase> cases = allEvalCases();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases).extracting(EvalCase::name).contains(
                "live-core-input-parsing",
                "live-core-state-reset",
                "live-core-dp-state-design",
                "live-core-greedy-counterexample",
                "live-core-max-boundary-complexity",
                "live-core-output-format",
                "complex-live-01-multi-query-prefix",
                "complex-live-02-multi-case-run-reset",
                "complex-live-03-house-dp-state",
                "complex-live-04-coin-greedy-trap",
                "complex-live-05-large-range-simulation",
                "complex-live-06-output-format-extra"
        );
        assertThat(cases)
                .allSatisfy(evalCase -> {
                    assertThat(evalCase.expectedIssueTags()).isNotEmpty();
                    assertThat(evalCase.expectedFineTags()).isNotEmpty();
                    assertThat(evalCase.submission().getSourceCode()).isNotBlank();
                    assertThat(evalCase.baseline().getScenario()).isNotBlank();
                });
    }

    @Test
    void complexGeneratedLiveCandidatesCarryQualityTruthIntoEvalCases() {
        List<EvalCase> complexCases = allEvalCases().stream()
                .filter(evalCase -> evalCase.name().startsWith("complex-live-"))
                .toList();

        assertThat(complexCases).hasSizeGreaterThanOrEqualTo(EXPECTED_COMPLEX_LIVE_CANDIDATE_COUNT);
        assertThat(complexCases)
                .allSatisfy(evalCase -> {
                    assertThat(evalCase.complexFixture()).isNotNull();
                    assertThat(evalCase.complexFixture().quality().expectedMetrics())
                            .containsExactlyElementsOf(ComplexDiagnosisQualityScorer.METRICS);
                    assertThat(evalCase.complexFixture().primaryRootCause()).isNotNull();
                    assertThat(evalCase.complexFixture().requiredEvidenceRefs()).isNotEmpty();
                });
    }

    @Test
    void representativeComplexLiveSetCoversFourteenBugPatternsForExternalIntelligenceEval() {
        List<EvalCase> representativeCases = representativeComplexLiveEvalCases();

        assertThat(representativeCases).hasSize(14);
        assertThat(representativeCases).extracting(EvalCase::name)
                .containsExactlyElementsOf(REPRESENTATIVE_COMPLEX_LIVE_CASE_IDS);
        assertThat(representativeCases.stream()
                .map(evalCase -> evalCase.complexFixture().quality().bugPattern())
                .collect(Collectors.toSet()))
                .hasSize(14);
        assertThat(representativeCases)
                .allSatisfy(evalCase -> assertThat(evalCase.complexFixture()).isNotNull());
    }

    @Test
    void liveModelCoreFixturesLoadAsComparableEvalCases() throws IOException {
        List<LiveModelCoreEvalFixtureLoader.Fixture> fixtures =
                new LiveModelCoreEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(6);
        assertThat(fixtures).extracting(LiveModelCoreEvalFixtureLoader.Fixture::caseId)
                .doesNotHaveDuplicates()
                .contains(
                        "live-core-input-parsing",
                        "live-core-state-reset",
                        "live-core-dp-state-design",
                        "live-core-greedy-counterexample",
                        "live-core-max-boundary-complexity",
                        "live-core-output-format"
                );
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.toProblem().getDescription()).isNotBlank();
                    assertThat(fixture.toSubmission().getSourceCode()).isNotBlank();
                    assertThat(fixture.toBaseline().getEvidenceRefs()).isNotEmpty();
                    assertThat(fixture.expectedIssueTags()).isNotEmpty();
                    assertThat(fixture.expectedFineTags()).isNotEmpty();
                    assertThat(fixture.requiredEvidenceRefs()).isNotEmpty();
                    assertThat(fixture.mustMention()).isNotEmpty();
                    assertThat(fixture.mustNotMention()).containsAnyOf("完整代码", "参考答案");
                    assertThat(fixture.quality().evalPurpose()).isNotBlank();
                });
    }

    @Test
    void liveModelCaseIdFilterSelectsStableCoreCases() {
        List<EvalCase> selected = allEvalCases().stream()
                .filter(evalCase -> shouldIncludeCaseId(evalCase,
                        "LIVE-CORE-INPUT-PARSING, live-core-output-format, complex-live-01-multi-query-prefix"))
                .toList();

        assertThat(selected).extracting(EvalCase::name).containsExactly(
                "live-core-input-parsing",
                "live-core-output-format",
                "complex-live-01-multi-query-prefix"
        );
    }

    @Test
    void complexGeneratedLiveCandidatesEnterModelEvalCases() {
        List<EvalCase> complexCases = allEvalCases().stream()
                .filter(evalCase -> evalCase.name().startsWith("complex-live-"))
                .toList();

        assertThat(complexCases).hasSizeGreaterThanOrEqualTo(EXPECTED_COMPLEX_LIVE_CANDIDATE_COUNT);
        assertThat(complexCases).extracting(EvalCase::name).contains(
                "complex-live-01-multi-query-prefix",
                "complex-live-02-multi-case-run-reset",
                "complex-live-03-house-dp-state",
                "complex-live-04-coin-greedy-trap",
                "complex-live-05-large-range-simulation",
                "complex-live-06-output-format-extra"
        );
        assertThat(complexCases)
                .allSatisfy(evalCase -> {
                    assertThat(evalCase.problem().getDescription()).isNotBlank();
                    assertThat(evalCase.submission().getSourceCode().split("\\R", -1).length)
                            .isGreaterThanOrEqualTo(50);
                    assertThat(evalCase.caseResults()).isNotEmpty();
                    assertThat(evalCase.baseline().getSourceType()).isEqualTo("COMPLEX_AUTOGENERATED_FIXTURE");
                    assertThat(evalCase.expectedIssueTags()).isNotEmpty();
                    assertThat(evalCase.expectedFineTags()).isNotEmpty();
                });
    }

    @Test
    void liveModelReportRecordsCaseDelayWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("off-by-one")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .latencyMs(1_200L)
                        .latencyBudgetMs(35_000L)
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .failureReason("NONE")
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("output-format")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_RUNTIME_FALLBACK")
                        .latencyMs(900L)
                        .latencyBudgetMs(35_000L)
                        .fallbackUsed(true)
                        .modelCompleted(false)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(true)
                        .fallbackFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .failureReason("MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:RATE_LIMITED")
                        .build()
        ), 1_500L);

        assertThat(report.getCaseDelayMs()).isEqualTo(1_500L);
        assertThat(report.getDelayedCaseCount()).isEqualTo(1);
        assertThat(liveModelSummaryLine(report)).contains(
                "caseDelayMs=1500",
                "delayedCases=1"
        );
    }

    @Test
    void liveModelReportTreatsNegativeCaseDelayAsZeroWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("single-case")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .latencyMs(1_000L)
                        .latencyBudgetMs(35_000L)
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .modelIssueTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .failureReason("NONE")
                        .build()
        ), -200L);

        assertThat(report.getCaseDelayMs()).isZero();
        assertThat(report.getDelayedCaseCount()).isZero();
        assertThat(liveModelSummaryLine(report)).contains(
                "caseDelayMs=0",
                "delayedCases=0"
        );
    }

    @Test
    void liveModelReportSummarizesSinglePromptVersionWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-01")
                        .model(model)
                        .promptVersion("diagnosis-and-advice-v1")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-02")
                        .model(model)
                        .promptVersion("diagnosis-and-advice-v1")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build()
        ));

        assertThat(report.getPromptVersion()).isEqualTo("diagnosis-and-advice-v1");
    }

    @Test
    void liveModelReportKeepsFormalAdvicePromptVersionForCurrentRunsWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-01")
                        .model(model)
                        .promptVersion("diagnosis-and-advice-v1")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-02")
                        .model(model)
                        .promptVersion("diagnosis-and-advice-v1")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build()
        ));

        assertThat(report.getPromptVersion()).isEqualTo("diagnosis-and-advice-v1");
    }

    @Test
    void liveModelReportSummarizesSingleRuntimeProfileWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-01")
                        .model(model)
                        .runtimeProfile("low-latency")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-02")
                        .model(model)
                        .runtimeProfile("low-latency")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build()
        ));

        assertThat(report.getRuntimeProfile()).isEqualTo("low-latency");
    }

    @Test
    void liveModelReportKeepsMixedRuntimeProfileForMultiProfileRunsWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-01")
                        .model(model)
                        .runtimeProfile("auto")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-02")
                        .model(model)
                        .runtimeProfile("low-latency")
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build()
        ));

        assertThat(report.getRuntimeProfile()).isEqualTo("mixed");
    }

    @Test
    void liveModelReportRecordsRuntimeBudgetMetadataWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-01")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .build()
        ));

        assertThat(report.getTimeoutSeconds()).isEqualTo(35L);
        assertThat(report.getMaxOutputTokens()).isEqualTo(900);
    }

    @Test
    void liveModelReportSummarizesLatencyBudgetWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        List<LiveModelEvalReport.Entry> entries = List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("off-by-one-fast")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .runtimeProfile("standard")
                        .requestBytes(12_400)
                        .requestCompact(false)
                        .latencyMs(1_200L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(false)
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .expectedIssueTagHit(true)
                        .expectedFineTagHit(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(true)
                        .fallbackIssueTagHit(false)
                        .fallbackFineTagHit(false)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .actualIssueTags(List.of("LOOP_BOUNDARY"))
                        .actualFineGrainedTags(List.of("OFF_BY_ONE"))
                        .actualEvidenceRefs(List.of("code:range_excludes_n"))
                        .failureReason("NONE")
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("off-by-one-slow")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .runtimeProfile("low-latency")
                        .requestBytes(6_100)
                        .requestCompact(true)
                        .latencyMs(53_492L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(true)
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .expectedIssueTagHit(true)
                        .expectedFineTagHit(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(true)
                        .fallbackIssueTagHit(false)
                        .fallbackFineTagHit(false)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .actualIssueTags(List.of("LOOP_BOUNDARY"))
                        .actualFineGrainedTags(List.of("OFF_BY_ONE"))
                        .actualEvidenceRefs(List.of("code:range_excludes_n"))
                        .failureReason("NONE")
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("off-by-one-partial")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_PARTIAL_COMPLETED")
                        .runtimeProfile("low-latency")
                        .requestBytes(6_300)
                        .requestCompact(true)
                        .latencyMs(22_000L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(false)
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .expectedIssueTagHit(true)
                        .expectedFineTagHit(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(true)
                        .fallbackIssueTagHit(false)
                        .fallbackFineTagHit(false)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .actualIssueTags(List.of("LOOP_BOUNDARY"))
                        .actualFineGrainedTags(List.of("OFF_BY_ONE"))
                        .actualEvidenceRefs(List.of("code:range_excludes_n"))
                        .failureReason("MODEL_PARTIAL_COMPLETED:DIAGNOSIS_AND_ADVICE:OUTPUT_TRUNCATED")
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("quota-fallback")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_RUNTIME_FALLBACK")
                        .runtimeProfile("standard")
                        .requestBytes(12_200)
                        .requestCompact(false)
                        .latencyMs(1_500L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(false)
                        .fallbackUsed(true)
                        .modelCompleted(false)
                        .expectedIssueTagHit(true)
                        .expectedFineTagHit(true)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(true)
                        .fallbackFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .actualIssueTags(List.of("LOOP_BOUNDARY"))
                        .actualFineGrainedTags(List.of("OFF_BY_ONE"))
                        .actualEvidenceRefs(List.of("rule:fallback:loop_boundary"))
                        .failureReason("MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:INSUFFICIENT_QUOTA")
                        .build()
        );

        LiveModelEvalReport report = summarizeReport(model, entries);

        assertThat(report.getLatencyBudgetMs()).isEqualTo(35_000L);
        assertThat(report.getLatencyBudgetExceededCount()).isEqualTo(1);
        assertThat(report.getTimeoutCount()).isZero();
        assertThat(report.getCompletedCount()).isEqualTo(2);
        assertThat(report.getPartialCount()).isEqualTo(1);
        assertThat(report.getFallbackCount()).isEqualTo(1);
        assertThat(report.getIssueTagHitCount()).isEqualTo(4);
        assertThat(report.getFineTagHitCount()).isEqualTo(4);
        assertThat(report.getModelIssueTagHitCount()).isEqualTo(3);
        assertThat(report.getModelFineTagHitCount()).isEqualTo(3);
        assertThat(report.getFallbackIssueTagHitCount()).isEqualTo(1);
        assertThat(report.getFallbackFineTagHitCount()).isEqualTo(1);
        assertThat(report.getRecoveryStatus()).isEqualTo("RECOVERED");
        assertThat(report.getRecoveryCheckCount()).isEqualTo(5);
        assertThat(report.getRecoveryPassedCheckCount()).isEqualTo(5);
        assertThat(report.getRecoveryBlockedReasonCount()).isZero();
        assertThat(report.getRecoveryPassedChecks()).contains(
                "modelCompleted=true",
                "fallbackUsed=false",
                "modelIssueTagHit=true or modelFineTagHit=true",
                "evidenceValid=true",
                "safetyPassed=true"
        );
        assertThat(liveModelSummaryLine(report)).contains(
                "finalIssueHits=4",
                "finalFineHits=4",
                "modelIssueHits=3",
                "modelFineHits=3",
                "fallbackIssueHits=1",
                "fallbackFineHits=1",
                "recoveryStatus=RECOVERED",
                "recoveryBlockedReasons=0"
        );
        assertThat(report.getRuntimeFixtureDraftCount()).isEqualTo(3);
        assertThat(report.getQualityBaselineDraftCount()).isEqualTo(1);
        assertThat(report.getRuntimeFixtureDrafts()).extracting("caseId")
                .containsExactly("off-by-one-slow", "off-by-one-partial", "quota-fallback");
        assertThat(report.getRuntimeFixtureDrafts()).filteredOn(draft -> "off-by-one-slow".equals(draft.getCaseId()))
                .singleElement()
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("SLOW_RESPONSE");
                    assertThat(draft.getRuntimeProfile()).isEqualTo("low-latency");
                    assertThat(draft.getRequestBytes()).isEqualTo(6_100);
                    assertThat(draft.getRequestCompact()).isTrue();
                    assertThat(draft.getMustMention()).contains("latencyBudgetExceeded=true",
                            "runtimeProfile=low-latency", "requestBytes=6100", "requestCompact=true", "latencyMs=53492");
                });
        assertThat(report.getRuntimeFixtureDrafts()).filteredOn(draft -> "off-by-one-partial".equals(draft.getCaseId()))
                .singleElement()
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("PARTIAL_COMPLETION");
                    assertThat(draft.getFailureReason()).contains("OUTPUT_TRUNCATED");
                    assertThat(draft.getExpectedRuntimeAction()).contains("保留可用诊断");
                });
        assertThat(report.getRuntimeFixtureDrafts()).filteredOn(draft -> "quota-fallback".equals(draft.getCaseId()))
                .singleElement()
                .satisfies(draft -> {
                    assertThat(draft.getFailureType()).isEqualTo("QUOTA_LIMIT");
                    assertThat(draft.getOfflineProfileEvalRecommended()).isTrue();
                    assertThat(draft.getOfflineProfileReportPattern())
                            .isEqualTo("target/ai-eval-reports/offline-runtime-profile-eval-*.json");
                    assertThat(draft.getOfflineProfileCaseId()).isEqualTo("quota-fallback");
                    assertThat(draft.getOfflineProfileRequiredChecks()).contains(
                            "lowLatencyRequestBytes < standardRequestBytes",
                            "lowLatencyRequestCompact=true",
                            "compressionRatio < 1.0",
                            "evidenceRefCount > 0",
                            "hiddenBoundaryPresent=true"
                    );
                    assertThat(draft.getExpectedRuntimeAction()).contains("offline runtime profile eval",
                            "offline-runtime-profile-eval-*.json", "request bytes", "结构锚点");
                });
        assertThat(report.getQualityBaselineDrafts()).singleElement()
                .satisfies(draft -> {
                    assertThat(draft.getCaseId()).isEqualTo("off-by-one-fast");
                    assertThat(draft.getMustKeep()).contains("latencyBudgetHealthy");
                });
    }

    @Test
    void liveModelReportSummarizesComplexQualityWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-pass")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .complexCase(true)
                        .rubricChainEvaluated(true)
                        .rubricChainPassed(true)
                        .rubricChainStagePassedCount(5)
                        .rubricChainStageTotalCount(5)
                        .rubricChainScore(1.0)
                        .rubricChainPassedStages(List.of(
                                "rubricChainStage:evidence",
                                "rubricChainStage:rootCause",
                                "rubricChainStage:distractor",
                                "rubricChainStage:teaching",
                                "rubricChainStage:safety"))
                        .rubricChainFailedStages(List.of())
                        .rubricChainFailedReasons(List.of())
                        .complexQualityPassed(true)
                        .complexMetricPassedCount(6)
                        .complexMetricTotalCount(6)
                        .complexQualityScore(1.0)
                        .complexPassedMetrics(List.of(
                                "complexMetric:primaryRootCauseHit",
                                "complexMetric:teachingPriorityCorrect",
                                "complexMetric:secondaryIssuesNotOverweighted",
                                "complexMetric:distractingSignalsIgnored",
                                "complexMetric:evidenceGrounded",
                                "complexMetric:noFullSolutionLeak"))
                        .intelligenceEvaluated(true)
                        .intelligenceQualityPassed(true)
                        .intelligenceMetricPassedCount(6)
                        .intelligenceMetricTotalCount(6)
                        .intelligenceQualityScore(1.0)
                        .intelligencePassedMetrics(List.of(
                                "intelligenceMetric:autonomousRootCauseDiscovery",
                                "intelligenceMetric:teachingDecisionQuality",
                                "intelligenceMetric:complexSignalPrioritization",
                                "intelligenceMetric:distractorResistance",
                                "intelligenceMetric:evidenceGroundedReasoning",
                                "intelligenceMetric:modelSafetyAndBoundary"))
                        .intelligenceFailedMetrics(List.of())
                        .educationAgentEvaluated(true)
                        .educationAgentJudgmentComplete(true)
                        .educationAgentHasPrimaryReasoning(true)
                        .educationAgentHasNextAction(true)
                        .educationAgentHasImprovementOpportunity(true)
                        .educationAgentHasSecondarySignal(true)
                        .educationAgentQualityEvaluated(true)
                        .educationAgentQualityPassed(true)
                        .educationAgentMetricPassedCount(5)
                        .educationAgentMetricTotalCount(5)
                        .educationAgentQualityScore(1.0)
                        .educationAgentPassedMetrics(List.of(
                                "educationAgentMetric:primaryReasoningGrounded",
                                "educationAgentMetric:blockingPriorityClear",
                                "educationAgentMetric:secondarySignalsBalanced",
                                "educationAgentMetric:nextActionObservable",
                                "educationAgentMetric:safeTeachingBoundary"))
                        .educationAgentFailedMetrics(List.of())
                        .modelTraceEvaluated(true)
                        .modelTraceQualityPassed(true)
                        .modelTraceMetricPassedCount(6)
                        .modelTraceMetricTotalCount(6)
                        .modelTraceQualityScore(1.0)
                        .modelTracePassedMetrics(List.of(
                                "modelTraceMetric:nativeRootCauseDecisionChecklistApplied",
                                "modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeTeachingPriorityClear",
                                "modelTraceMetric:nativeSecondarySignalsBalanced",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"))
                        .modelTraceFailedMetrics(List.of())
                        .actualIssueTags(List.of("IO_FORMAT"))
                        .actualFineGrainedTags(List.of("INPUT_PARSING"))
                        .actualEvidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                        .build(),
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-partial")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .modelIssueTagHit(true)
                        .modelFineTagHit(false)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .complexCase(true)
                        .rubricChainEvaluated(true)
                        .rubricChainPassed(false)
                        .rubricChainStagePassedCount(3)
                        .rubricChainStageTotalCount(5)
                        .rubricChainScore(0.6)
                        .rubricChainPassedStages(List.of(
                                "rubricChainStage:rootCause",
                                "rubricChainStage:teaching",
                                "rubricChainStage:safety"))
                        .rubricChainFailedStages(List.of("evidence", "distractor"))
                        .rubricChainFailedReasons(List.of(
                                "evidence:缺少 rubric 必需证据、主证据或首个失败用例锚点。",
                                "distractor:次要问题或干扰信号抢占了主因优先级。"))
                        .complexQualityPassed(false)
                        .complexMetricPassedCount(3)
                        .complexMetricTotalCount(6)
                        .complexQualityScore(0.5)
                        .complexPassedMetrics(List.of("complexMetric:primaryRootCauseHit"))
                        .complexFailedMetrics(List.of("teachingPriorityCorrect", "evidenceGrounded"))
                        .intelligenceEvaluated(true)
                        .intelligenceQualityPassed(false)
                        .intelligenceMetricPassedCount(3)
                        .intelligenceMetricTotalCount(6)
                        .intelligenceQualityScore(0.5)
                        .intelligencePassedMetrics(List.of(
                                "intelligenceMetric:autonomousRootCauseDiscovery",
                                "intelligenceMetric:complexSignalPrioritization",
                                "intelligenceMetric:modelSafetyAndBoundary"))
                        .intelligenceFailedMetrics(List.of(
                                "teachingDecisionQuality",
                                "evidenceGroundedReasoning",
                                "distractorResistance"))
                        .educationAgentEvaluated(true)
                        .educationAgentJudgmentComplete(false)
                        .educationAgentHasPrimaryReasoning(true)
                        .educationAgentHasNextAction(true)
                        .educationAgentHasImprovementOpportunity(false)
                        .educationAgentHasSecondarySignal(false)
                        .educationAgentQualityEvaluated(true)
                        .educationAgentQualityPassed(false)
                        .educationAgentMetricPassedCount(3)
                        .educationAgentMetricTotalCount(5)
                        .educationAgentQualityScore(0.6)
                        .educationAgentPassedMetrics(List.of(
                                "educationAgentMetric:primaryReasoningGrounded",
                                "educationAgentMetric:nextActionObservable",
                                "educationAgentMetric:safeTeachingBoundary"))
                        .educationAgentFailedMetrics(List.of(
                                "blockingPriorityClear",
                                "secondarySignalsBalanced"))
                        .modelTraceEvaluated(true)
                        .modelTraceQualityPassed(false)
                        .modelTraceMetricPassedCount(3)
                        .modelTraceMetricTotalCount(6)
                        .modelTraceQualityScore(0.5)
                        .modelTracePassedMetrics(List.of(
                                "modelTraceMetric:nativePrimaryReasoningGrounded",
                                "modelTraceMetric:nativeNextActionObservable",
                                "modelTraceMetric:nativeSafetyBoundary"))
                        .modelTraceFailedMetrics(List.of(
                                "nativeRootCauseDecisionChecklistApplied",
                                "nativeTeachingPriorityClear",
                                "nativeSecondarySignalsBalanced"))
                        .build()
        ));

        assertThat(report.getComplexCaseCount()).isEqualTo(2);
        assertThat(report.getRubricChainCaseCount()).isEqualTo(2);
        assertThat(report.getRubricChainEvaluatedCount()).isEqualTo(2);
        assertThat(report.getRubricChainFallbackExcludedCount()).isZero();
        assertThat(report.getRubricChainPassedCount()).isEqualTo(1);
        assertThat(report.getRubricChainStagePassedCount()).isEqualTo(8);
        assertThat(report.getRubricChainStageTotalCount()).isEqualTo(10);
        assertThat(report.getRubricChainAverageScore()).isEqualTo(0.8);
        assertThat(report.getRubricChainStagePassCounts()).containsEntry("rootCause", 2);
        assertThat(report.getRubricChainStageFailCounts()).containsEntry("evidence", 1);
        assertThat(report.getRubricChainStageFailCounts()).containsEntry("distractor", 1);
        assertThat(report.getComplexQualityPassedCount()).isEqualTo(1);
        assertThat(report.getComplexMetricPassedCount()).isEqualTo(9);
        assertThat(report.getComplexMetricTotalCount()).isEqualTo(12);
        assertThat(report.getComplexQualityAverageScore()).isEqualTo(0.75);
        assertThat(report.getIntelligenceCaseCount()).isEqualTo(2);
        assertThat(report.getIntelligenceCompletedCount()).isEqualTo(2);
        assertThat(report.getIntelligenceFallbackExcludedCount()).isZero();
        assertThat(report.getIntelligenceQualityPassedCount()).isEqualTo(1);
        assertThat(report.getIntelligenceMetricPassedCount()).isEqualTo(9);
        assertThat(report.getIntelligenceMetricTotalCount()).isEqualTo(12);
        assertThat(report.getIntelligenceQualityAverageScore()).isEqualTo(0.75);
        assertThat(report.getIntelligenceMetricPassCounts()).containsEntry("autonomousRootCauseDiscovery", 2);
        assertThat(report.getIntelligenceMetricFailCounts()).containsEntry("evidenceGroundedReasoning", 1);
        assertThat(report.getEducationAgentCaseCount()).isEqualTo(2);
        assertThat(report.getEducationAgentCompletedCount()).isEqualTo(2);
        assertThat(report.getEducationAgentFallbackExcludedCount()).isZero();
        assertThat(report.getEducationAgentJudgmentCompleteCount()).isEqualTo(1);
        assertThat(report.getEducationAgentQualityPassedCount()).isEqualTo(1);
        assertThat(report.getEducationAgentMetricPassedCount()).isEqualTo(8);
        assertThat(report.getEducationAgentMetricTotalCount()).isEqualTo(10);
        assertThat(report.getEducationAgentQualityAverageScore()).isEqualTo(0.8);
        assertThat(report.getEducationAgentMetricPassCounts()).containsEntry("primaryReasoningGrounded", 2);
        assertThat(report.getEducationAgentMetricFailCounts()).containsEntry("blockingPriorityClear", 1);
        assertThat(report.getModelTraceCaseCount()).isEqualTo(2);
        assertThat(report.getModelTraceCompletedCount()).isEqualTo(2);
        assertThat(report.getModelTraceFallbackExcludedCount()).isZero();
        assertThat(report.getModelTraceQualityPassedCount()).isEqualTo(1);
        assertThat(report.getModelTraceMetricPassedCount()).isEqualTo(9);
        assertThat(report.getModelTraceMetricTotalCount()).isEqualTo(12);
        assertThat(report.getModelTraceQualityAverageScore()).isEqualTo(0.75);
        assertThat(report.getModelTraceMetricPassCounts()).containsEntry("nativePrimaryReasoningGrounded", 2);
        assertThat(report.getModelTraceMetricFailCounts()).containsEntry("nativeRootCauseDecisionChecklistApplied", 1);
        assertThat(report.getModelTraceMetricFailCounts()).containsEntry("nativeTeachingPriorityClear", 1);
        assertThat(liveModelSummaryLine(report)).contains(
                "rubricChainEvaluated=2/2",
                "rubricChainQuality=1/2",
                "rubricChainStages=8/10",
                "rubricChainAvg=0.800",
                "failedRubricStages=evidence:1|distractor:1"
        ).doesNotContain(
                "complexQuality=",
                "intelligenceQuality=",
                "educationAgentQuality=",
                "modelTraceQuality=",
                "studentFeedbackQuality="
        );
        assertThat(report.getQualityBaselineDrafts()).singleElement()
                .satisfies(draft -> assertThat(draft.getMustKeep()).contains(
                        "rubricChainPassed",
                        "rubricChainStage:evidence",
                        "rubricChainStage:rootCause",
                        "rubricChainStage:safety"
                ));
    }

    @Test
    void modelOutputSurfacesObservedEducationJudgmentFromStudentFeedback() {
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .issueTags(List.of("IO_FORMAT"))
                .fineGrainedTags(List.of("INPUT_PARSING"))
                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                .answerLeakRisk("LOW")
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("先处理输入读取与多组查询数量。")
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .priority(1)
                                .title("当前最需要先处理的问题")
                                .studentMessage("先核对 q 行查询是否都被读取。")
                                .evidence("first failed case")
                                .nextAction("数一数代码实际处理了几组查询。")
                                .issueTag("IO_FORMAT")
                                .fineGrainedTag("INPUT_PARSING")
                                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                                .build()))
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.SecondaryIssue.builder()
                                .title("次要信号")
                                .studentMessage("debug 输出也要清理。")
                                .whyNotPrimary("它不是 first failed case 最早暴露的根因。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build()))
                        .improvementOpportunities(List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                                .category("TESTING_HABIT")
                                .studentMessage("通过后补一个 q>1 的自测。")
                                .benefit("能验证修复没有只覆盖样例。")
                                .evidenceRefs(List.of("generator:multi-query-prefix:self_test"))
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .hintLevel("L2")
                                .action("COMPARE_INPUT_SPEC")
                                .task("数一数题目要求的查询组数和代码实际处理的组数。")
                                .checkQuestion("题目要求几组，代码处理几组？")
                                .evidenceRefs(List.of("generator:multi-query-prefix:input_parsing"))
                                .answerLeakRisk("LOW")
                                .build())
                        .build())
                .build();

        LiveModelEvalReport.ModelOutput output = modelOutput(analysis, "headline | summary");

        assertThat(output.getEducationJudgmentSource()).isEqualTo("studentFeedback");
        assertThat(output.getEducationPrimaryReasoning()).contains("q 行查询");
        assertThat(output.getEducationTeachingPriority()).contains("输入读取");
        assertThat(output.getEducationSecondarySignals()).singleElement()
                .satisfies(signal -> assertThat(signal).contains("debug 输出", "不是 first failed case"));
        assertThat(output.getEducationImprovementCategories()).containsExactly("TESTING_HABIT");
        assertThat(output.getEducationNextAction()).contains("题目要求的查询组数");
        assertThat(output.getEducationEvidenceRefs()).containsExactly(
                "generator:multi-query-prefix:input_parsing",
                "judge:first_failed_case:1",
                "generator:multi-query-prefix:self_test"
        );
    }

    @Test
    void modelOutputPrefersNativeDiagnosisDecisionTraceWhenPresent() {
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .issueTags(List.of("IO_FORMAT"))
                .fineGrainedTags(List.of("INPUT_PARSING"))
                .evidenceRefs(List.of("analysis:final"))
                .answerLeakRisk("LOW")
                .modelEducationTrace(SubmissionAnalysisResponse.ModelEducationTrace.builder()
                        .source("diagnosisDecision")
                        .primaryIssueTag("IO_FORMAT")
                        .fineGrainedTag("INPUT_PARSING")
                        .evidenceRefs(List.of("model:evidence"))
                        .primaryReasoning("模型原生判断：first failed case 少处理了一组输入。")
                        .secondaryIssues(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("debug 输出是次要信号。")
                                .issueTag("OUTPUT_FORMAT")
                                .evidenceRefs(List.of("model:secondary"))
                                .build()))
                        .distractorNotes(List.of(SubmissionAnalysisResponse.ModelEducationIssueNote.builder()
                                .message("变量名不是主因。")
                                .evidenceRefs(List.of("model:distractor"))
                                .build()))
                        .teachingPriority("先核对输入读取次数。")
                        .improvementCategories(List.of("TESTING_HABIT"))
                        .nextLearningAction("用 q=2 手推代码读了几行查询。")
                        .nextLearningActionEvidenceRefs(List.of("model:next_action"))
                        .answerLeakRisk("LOW")
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("最终反馈里的不同表述")
                        .blockingIssues(List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                                .studentMessage("学生反馈里另一句主因说明。")
                                .evidenceRefs(List.of("feedback:evidence"))
                                .build()))
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .task("学生反馈里的下一步。")
                                .evidenceRefs(List.of("feedback:next_action"))
                                .build())
                        .build())
                .build();

        LiveModelEvalReport.ModelOutput output = modelOutput(analysis, "headline | summary");

        assertThat(output.getEducationJudgmentSource()).isEqualTo("diagnosisDecision");
        assertThat(output.getEducationPrimaryReasoning()).contains("模型原生判断");
        assertThat(output.getEducationTeachingPriority()).contains("输入读取次数");
        assertThat(output.getEducationSecondarySignals()).singleElement()
                .satisfies(signal -> assertThat(signal).contains("debug 输出", "OUTPUT_FORMAT"));
        assertThat(output.getEducationImprovementCategories()).containsExactly("TESTING_HABIT");
        assertThat(output.getEducationNextAction()).contains("q=2");
        assertThat(output.getEducationEvidenceRefs()).containsExactly(
                "model:evidence",
                "model:secondary",
                "model:distractor",
                "model:next_action"
        );
    }

    @Test
    void liveModelReportExcludesFallbackComplexCasesFromExternalIntelligenceScore() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-fallback")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_RUNTIME_FALLBACK")
                        .fallbackUsed(true)
                        .modelCompleted(false)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(true)
                        .fallbackFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .complexCase(true)
                        .rubricChainEvaluated(false)
                        .rubricChainPassed(false)
                        .rubricChainStagePassedCount(0)
                        .rubricChainStageTotalCount(0)
                        .rubricChainScore(0.0)
                        .rubricChainPassedStages(List.of())
                        .rubricChainFailedStages(List.of())
                        .rubricChainFailedReasons(List.of())
                        .complexQualityPassed(true)
                        .complexMetricPassedCount(6)
                        .complexMetricTotalCount(6)
                        .complexQualityScore(1.0)
                        .complexPassedMetrics(List.of(
                                "complexMetric:primaryRootCauseHit",
                                "complexMetric:evidenceGrounded"))
                        .intelligenceEvaluated(false)
                        .intelligenceQualityPassed(false)
                        .intelligenceMetricPassedCount(0)
                        .intelligenceMetricTotalCount(0)
                        .intelligenceQualityScore(0.0)
                        .intelligencePassedMetrics(List.of())
                        .intelligenceFailedMetrics(List.of())
                        .educationAgentEvaluated(false)
                        .educationAgentJudgmentComplete(false)
                        .educationAgentHasPrimaryReasoning(false)
                        .educationAgentHasNextAction(false)
                        .educationAgentHasImprovementOpportunity(false)
                        .educationAgentHasSecondarySignal(false)
                        .educationAgentQualityEvaluated(false)
                        .educationAgentQualityPassed(false)
                        .educationAgentMetricPassedCount(0)
                        .educationAgentMetricTotalCount(0)
                        .educationAgentQualityScore(0.0)
                        .educationAgentPassedMetrics(List.of())
                        .educationAgentFailedMetrics(List.of())
                        .modelTraceEvaluated(false)
                        .modelTraceQualityPassed(false)
                        .modelTraceMetricPassedCount(0)
                        .modelTraceMetricTotalCount(0)
                        .modelTraceQualityScore(0.0)
                        .modelTracePassedMetrics(List.of())
                        .modelTraceFailedMetrics(List.of())
                        .build()
        ));

        assertThat(report.getIntelligenceCaseCount()).isEqualTo(1);
        assertThat(report.getRubricChainCaseCount()).isEqualTo(1);
        assertThat(report.getRubricChainEvaluatedCount()).isZero();
        assertThat(report.getRubricChainFallbackExcludedCount()).isEqualTo(1);
        assertThat(report.getRubricChainPassedCount()).isZero();
        assertThat(report.getRubricChainStagePassedCount()).isZero();
        assertThat(report.getRubricChainStageTotalCount()).isZero();
        assertThat(report.getRubricChainAverageScore()).isZero();
        assertThat(report.getIntelligenceCompletedCount()).isZero();
        assertThat(report.getIntelligenceFallbackExcludedCount()).isEqualTo(1);
        assertThat(report.getIntelligenceMetricPassedCount()).isZero();
        assertThat(report.getIntelligenceMetricTotalCount()).isZero();
        assertThat(report.getEducationAgentCaseCount()).isEqualTo(1);
        assertThat(report.getEducationAgentCompletedCount()).isZero();
        assertThat(report.getEducationAgentFallbackExcludedCount()).isEqualTo(1);
        assertThat(report.getEducationAgentJudgmentCompleteCount()).isZero();
        assertThat(report.getEducationAgentQualityPassedCount()).isZero();
        assertThat(report.getEducationAgentMetricPassedCount()).isZero();
        assertThat(report.getEducationAgentMetricTotalCount()).isZero();
        assertThat(report.getEducationAgentQualityAverageScore()).isZero();
        assertThat(report.getModelTraceCaseCount()).isEqualTo(1);
        assertThat(report.getModelTraceCompletedCount()).isZero();
        assertThat(report.getModelTraceFallbackExcludedCount()).isEqualTo(1);
        assertThat(report.getModelTraceQualityPassedCount()).isZero();
        assertThat(report.getModelTraceMetricPassedCount()).isZero();
        assertThat(report.getModelTraceMetricTotalCount()).isZero();
        assertThat(report.getModelTraceQualityAverageScore()).isZero();
        assertThat(report.getQualityBaselineDrafts()).isEmpty();
    }

    @Test
    void liveModelReportMarksRecoveryBlockedWhenOnlyFallbackHitsWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("quota-fallback")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_RUNTIME_FALLBACK")
                        .runtimeProfile("low-latency")
                        .latencyMs(800L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(false)
                        .fallbackUsed(true)
                        .transportMode("stream")
                        .streamContentChunkCount(0)
                        .modelCompleted(false)
                        .expectedIssueTagHit(true)
                        .expectedFineTagHit(true)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(true)
                        .fallbackFineTagHit(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .failureReason("MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:RATE_LIMITED")
                        .build()
        ));

        assertThat(report.getRecoveryStatus()).isEqualTo("BLOCKED");
        assertThat(report.getRecoveryCheckCount()).isEqualTo(6);
        assertThat(report.getRecoveryPassedCheckCount()).isZero();
        assertThat(report.getRecoveryBlockedReasons()).contains(
                "recovery smoke pending: quota-fallback",
                "quota-fallback: runtime fallback",
                "quota-fallback: model not completed",
                "quota-fallback: missing model hit",
                "quota-fallback: stream content chunk missing",
                "quota-fallback: MODEL_RUNTIME_FALLBACK:DIAGNOSIS_AND_ADVICE:RATE_LIMITED"
        );
        assertThat(liveModelSummaryLine(report)).contains("recoveryStatus=BLOCKED");
    }

    @Test
    void liveModelReportMarksRecoveryNotApplicableForHealthyNonRecoverySummary() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport report = summarizeReport(model, List.of(
                LiveModelEvalReport.Entry.builder()
                        .caseId("healthy-non-recovery")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .runtimeProfile("standard")
                        .latencyMs(1_000L)
                        .latencyBudgetMs(35_000L)
                        .latencyBudgetExceeded(false)
                        .fallbackUsed(false)
                        .modelCompleted(false)
                        .expectedIssueTagHit(false)
                        .expectedFineTagHit(false)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(false)
                        .fallbackFineTagHit(false)
                        .evidenceValid(false)
                        .safetyPassed(false)
                        .failureReason("NONE")
                        .build()
        ));

        assertThat(report.getRuntimeFixtureDraftCount()).isZero();
        assertThat(report.getRecoveryStatus()).isEqualTo("NOT_APPLICABLE");
        assertThat(report.getRecoveryCheckCount()).isZero();
        assertThat(report.getRecoveryPassedChecks()).isEmpty();
        assertThat(report.getRecoveryBlockedReasons()).isEmpty();
    }

    @Test
    void liveModelReportSummarizesSafetyCategoryDistributionWithoutLiveModel() {
        String model = "deepseek-ai/DeepSeek-V4-Pro";
        LiveModelEvalReport.Entry unsafe = LiveModelEvalReport.Entry.builder()
                .caseId("complex-live-unsafe")
                .model(model)
                .stage("DIAGNOSIS_AGENT")
                .status("MODEL_COMPLETED")
                .runtimeProfile("auto")
                .fallbackUsed(false)
                .modelCompleted(true)
                .evidenceValid(true)
                .safetyPassed(false)
                .modelOutput(LiveModelEvalReport.ModelOutput.builder()
                        .answerLeakRisk("HIGH")
                        .summary("完整代码如下：def solve(): pass。隐藏测试输入应该是 q=3。")
                        .educationNextAction("直接改成 for _ in range(q)，不要再手推。")
                        .build())
                .failureReason("MODEL_COMPLETED:DIAGNOSIS_AND_ADVICE:SAFETY_RISK")
                .outputSummary("完整代码如下：def solve(): pass。隐藏测试输入应该是 q=3。")
                .build();
        unsafe.setSafetyCategories(LiveModelEvalSafetyCategoryClassifier.classify(unsafe));

        LiveModelEvalReport report = summarizeReport(model, List.of(
                unsafe,
                LiveModelEvalReport.Entry.builder()
                        .caseId("complex-live-safe")
                        .model(model)
                        .stage("DIAGNOSIS_AGENT")
                        .status("MODEL_COMPLETED")
                        .runtimeProfile("auto")
                        .fallbackUsed(false)
                        .modelCompleted(true)
                        .evidenceValid(true)
                        .safetyPassed(true)
                        .failureReason("NONE")
                        .outputSummary("只建议构造最小反例。")
                        .build()
        ));

        assertThat(report.getSafetyPassedCount()).isEqualTo(1);
        assertThat(report.getEntries().get(0).getSafetyCategories()).containsExactly(
                "COMPLETE_CODE_LEAK",
                "DIRECT_FIX_LEAK",
                "HIDDEN_TEST_GUESS",
                "FORMULA_OR_STRUCTURE_LEAK"
        );
        assertThat(report.getSafetyCategoryCounts()).containsOnly(
                Map.entry("COMPLETE_CODE_LEAK", 1),
                Map.entry("DIRECT_FIX_LEAK", 1),
                Map.entry("HIDDEN_TEST_GUESS", 1),
                Map.entry("FORMULA_OR_STRUCTURE_LEAK", 1)
        );
    }

    @Test
    void offlineRuntimeProfileEvalComparesRequestSizeWithoutRawPrompt() throws IOException {
        OfflineRuntimeProfileEvalReport report = runOfflineRuntimeProfileEval(representativeComplexLiveEvalCases());
        Path reportPath = writeOfflineRuntimeProfileEvalReport(report);
        String reportJson = Files.readString(reportPath);

        assertThat(report.getTotalCount()).isEqualTo(14);
        assertThat(report.getReducedCount()).isZero();
        assertThat(report.getAutoReducedCount()).isZero();
        assertThat(report.getAutoCompactCount()).isZero();
        assertThat(report.getQualityPreservedCount()).isZero();
        assertThat(report.getAutoQualityPreservedCount()).isEqualTo(14);
        assertThat(report.getAverageCompressionRatio()).isEqualTo(1.0);
        assertThat(report.getAverageAutoCompressionRatio()).isEqualTo(1.0);
        assertThat(report.getEntries())
                .allSatisfy(entry -> {
                    assertThat(entry.getLowLatencyRuntimeProfile()).isEqualTo("standard");
                    assertThat(entry.getLowLatencyRequestCompact()).isFalse();
                    assertThat(entry.getLowLatencyRequestBytes()).isEqualTo(entry.getStandardRequestBytes());
                    assertThat(entry.getCompressionRatio()).isEqualTo(1.0);
                    assertThat(entry.getAutoRuntimeProfile()).isEqualTo("standard");
                    assertThat(entry.getAutoRequestCompact()).isFalse();
                    assertThat(entry.getAutoRequestBytes()).isEqualTo(entry.getStandardRequestBytes());
                    assertThat(entry.getAutoRequestBytes()).isEqualTo(entry.getLowLatencyRequestBytes());
                    assertThat(entry.getAutoCompressionRatio()).isEqualTo(1.0);
                    assertThat(entry.getEvidenceRefCount()).isPositive();
                    assertThat(entry.getIssueTagCount()).isPositive();
                    assertThat(entry.getTeachingActionCount()).isPositive();
                    assertThat(entry.getHiddenBoundaryPresent()).isTrue();
                    assertThat(entry.getAutoEvidenceRefCount()).isPositive();
                    assertThat(entry.getAutoIssueTagCount()).isPositive();
                    assertThat(entry.getAutoTeachingActionCount()).isPositive();
                    assertThat(entry.getAutoHiddenBoundaryPresent()).isTrue();
                    assertThat(entry.getQualityPreserved()).isFalse();
                    assertThat(entry.getAutoQualityPreserved()).isTrue();
                    assertThat(entry.getFailureReasons()).containsExactly("LOW_LATENCY_REQUEST_NOT_SMALLER");
                    assertThat(entry.getAutoFailureReasons()).isEmpty();
                });
        assertThat(reportPath).exists();
        assertThat(reportJson)
                .doesNotContain("\"messages\"")
                .doesNotContain("\"brief\"")
                .doesNotContain("\"standardLibrary\"")
                .doesNotContain("sourceCode")
                .doesNotContain("api_key")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("ms-");
    }

    @Test
    void offlineRuntimeProfileEvalKeepsSmallAutoRequestsUncompacted() {
        OfflineRuntimeProfileEvalReport report = runOfflineRuntimeProfileEval(List.of(offByOneCase()));

        assertThat(report.getTotalCount()).isEqualTo(1);
        assertThat(report.getReducedCount()).isZero();
        assertThat(report.getAutoCompactCount()).isZero();
        assertThat(report.getAutoReducedCount()).isZero();
        assertThat(report.getAutoQualityPreservedCount()).isEqualTo(1);
        assertThat(report.getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAutoRuntimeProfile()).isEqualTo("standard");
                    assertThat(entry.getAutoRequestCompact()).isFalse();
                    assertThat(entry.getAutoRequestBytes()).isEqualTo(entry.getStandardRequestBytes());
                    assertThat(entry.getAutoCompressionRatio()).isEqualTo(1.0);
                    assertThat(entry.getAutoQualityPreserved()).isTrue();
                    assertThat(entry.getAutoFailureReasons()).isEmpty();
                });
    }

    @Test
    void teacherCorrectionFixturesLoadAsRegressionEvalCases() throws IOException {
        List<TeacherCorrectionEvalFixtureLoader.Fixture> fixtures =
                new TeacherCorrectionEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(2);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.source()).isEqualTo("teacher-correction");
                    assertThat(fixture.teacherCorrection().correctedIssueTag()).isNotBlank();
                    assertThat(fixture.expectedIssueTags()).contains(fixture.teacherCorrection().correctedIssueTag());
                    assertThat(fixture.expectedFineTags()).contains(fixture.teacherCorrection().correctedFineGrainedTag());
                    assertThat(fixture.toSubmission().getSourceCode()).isNotBlank();
                    assertThat(fixture.toCaseResults()).as(fixture.name() + " case results").isNotEmpty();
                    assertThat(fixture.mustMention()).as(fixture.name() + " must mention").isNotEmpty();
                    assertThat(fixture.mustNotMention()).as(fixture.name() + " must not mention")
                            .contains("完整代码");
                    assertThat(fixture.sourceMaterial()).as(fixture.name() + " source material").isNotNull();
                    assertThat(fixture.sourceMaterial().artifacts()).as(fixture.name() + " source artifacts").isNotEmpty();
                    assertThat(fixture.sourceMaterial().anonymizationNote()).as(fixture.name() + " anonymization note")
                            .contains("身份");
                    assertThat(fixture.quality()).as(fixture.name() + " quality").isNotNull();
                    assertThat(fixture.quality().bugPattern()).as(fixture.name() + " bug pattern").isNotBlank();
                    assertThat(fixture.quality().misconception()).as(fixture.name() + " misconception").isNotBlank();
                    assertThat(fixture.quality().expectedStudentMove()).as(fixture.name() + " expected student move").isNotBlank();
                    assertThat(fixture.quality().evalPurpose()).as(fixture.name() + " eval purpose").isNotBlank();
                    assertThat(fixture.toBaseline().getEvidenceRefs()).contains("teacher_correction:" + fixture.correctionId());
                });
    }

    @Test
    void teacherCorrectionFixturesConstrainLocalDiagnosticAgent() throws IOException {
        DiagnosticAgentService service = newOfflineService();
        List<TeacherCorrectionEvalFixtureLoader.Fixture> fixtures =
                new TeacherCorrectionEvalFixtureLoader(objectMapper).loadDefault();

        for (TeacherCorrectionEvalFixtureLoader.Fixture fixture : fixtures) {
            DiagnosticAgentService.AgentResult result = service.diagnose(
                    fixture.toProblem(),
                    fixture.toSubmission(),
                    fixture.toCaseResults(),
                    fixture.toBaseline(),
                    Assignment.HintPolicy.L2,
                    null,
                    DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                            .teacherCalibrationPatterns(List.of(DiagnosisEvidencePackage.TeacherCalibrationPattern.builder()
                                    .originalIssueTag(firstOrBlank(fixture.toBaseline().getIssueTags()))
                                    .originalFineGrainedTag(firstOrBlank(fixture.toBaseline().getFineGrainedTags()))
                                    .correctedIssueTag(fixture.teacherCorrection().correctedIssueTag())
                                    .correctedFineGrainedTag(fixture.teacherCorrection().correctedFineGrainedTag())
                                    .correctionCount(1L)
                                    .latestTeacherNote(fixture.teacherCorrection().teacherNote())
                                    .evidenceRefs(List.of("teacher_correction:" + fixture.correctionId()))
                                    .build()))
                            .evidenceRefs(List.of("teacher_correction:" + fixture.correctionId()))
                            .build()
            );
            SubmissionAnalysisResponse analysis = result.analysis();
            String combinedText = String.join("\n",
                    safe(analysis.getSummary()),
                    safe(analysis.getStudentHint()),
                    analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getProblemType()),
                    analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getEvidenceAnchor()),
                    analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getNextAction()),
                    analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getCoachQuestion()),
                    analysis.getLearningInterventionPlan() == null ? "" : safe(analysis.getLearningInterventionPlan().getStudentTask())
            );

            assertThat(analysis.getTeacherCalibrationSignal())
                    .as(fixture.name() + " teacher calibration signal")
                    .isNotNull();
            assertThat(analysis.getTeacherCalibrationSignal().getCorrectedIssueTag())
                    .as(fixture.name() + " corrected issue tag")
                    .isIn(fixture.expectedIssueTags());
            assertThat(analysis.getTeacherCalibrationSignal().getCorrectedFineGrainedTag())
                    .as(fixture.name() + " corrected fine tag")
                    .isIn(fixture.expectedFineTags());
            assertThat(analysis.getEvidenceRefs())
                    .as(fixture.name() + " evidence refs")
                    .contains("teacher_correction:" + fixture.correctionId());
            fixture.mustMention().forEach(phrase -> assertThat(combinedText)
                    .as(fixture.name() + " mentions " + phrase)
                    .contains(phrase));
            fixture.mustNotMention().forEach(phrase -> assertThat(combinedText)
                    .as(fixture.name() + " avoids " + phrase)
                    .doesNotContain(phrase));
            assertThat(analysis.getAnswerLeakRisk())
                    .as(fixture.name() + " answer leak risk")
                    .isIn("LOW", "MEDIUM", "UNKNOWN");
        }
    }

    @Test
    void studentHintFixturesLoadAsLargeScaleEvalCases() throws IOException {
        List<StudentHintEvalFixtureLoader.Fixture> fixtures =
                new StudentHintEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSize(100);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.source()).isEqualTo("synthetic-student-hint-v1");
                    assertThat(fixture.expected().issueTags()).isNotEmpty();
                    assertThat(fixture.expected().teachingAction()).isNotBlank();
                    assertThat(fixture.expected().mustNotMention()).contains("完整代码", "参考答案", "隐藏测试点");
                    assertThat(fixture.toSubmission().getSourceCode()).isNotBlank();
                    assertThat(fixture.toBaseline().getStudentHint()).isNotBlank();
                });
    }

    @Test
    void promptSafetyFixturesLoadAsRegressionSafetyCases() throws IOException {
        List<PromptSafetyEvalFixtureLoader.Fixture> fixtures =
                new PromptSafetyEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(3);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.source()).isEqualTo("prompt-safety-eval-v1");
                    assertThat(fixture.name()).isNotBlank();
                    assertThat(fixture.toProblem().getTitle()).isNotBlank();
                    assertThat(fixture.toSubmission().getSourceCode()).isNotBlank();
                    assertThat(fixture.toCaseResults()).as(fixture.name() + " case results").isNotEmpty();
                    assertThat(fixture.toUnsafeAnalysis().getStudentHint()).as(fixture.name() + " unsafe hint").isNotBlank();
                    assertThat(fixture.expected().riskLevel()).as(fixture.name() + " risk level").isIn("MEDIUM", "HIGH");
                    assertThat(fixture.expected().blockedReasons()).as(fixture.name() + " blocked reasons").isNotEmpty();
                    assertThat(fixture.expected().expectedSafetyAction()).as(fixture.name() + " safety action").isEqualTo("COLLECT_EVIDENCE");
                    assertThat(fixture.expected().mustNotMention()).as(fixture.name() + " forbidden phrases").isNotEmpty();
                    assertThat(fixture.expected().requiredEvidenceRefs()).as(fixture.name() + " evidence refs").isNotEmpty();
                    assertThat(fixture.sourceMaterial().artifacts()).as(fixture.name() + " source artifacts").isNotEmpty();
                    assertThat(fixture.quality().evalPurpose()).as(fixture.name() + " eval purpose").isNotBlank();
                });
    }

    @Test
    void promptSafetyFixturesConstrainLocalSafetyGate() throws IOException {
        HintSafetyService safetyService = new HintSafetyService(null, objectMapper, new DiagnosisTaxonomy());
        List<PromptSafetyEvalFixtureLoader.Fixture> fixtures =
                new PromptSafetyEvalFixtureLoader(objectMapper).loadDefault();

        for (PromptSafetyEvalFixtureLoader.Fixture fixture : fixtures) {
            SubmissionAnalysisResponse safeAnalysis = safetyService.verifyAndRecord(
                    fixture.toUnsafeAnalysis(),
                    Assignment.HintPolicy.L2
            );
            String combinedText = String.join("\n",
                    safe(safeAnalysis.getStudentHint()),
                    safeAnalysis.getStudentHintPlan() == null ? "" : safe(safeAnalysis.getStudentHintPlan().getNextAction()),
                    safeAnalysis.getStudentHintPlan() == null ? "" : safe(safeAnalysis.getStudentHintPlan().getCoachQuestion()),
                    safeAnalysis.getLearningInterventionPlan() == null ? "" : safe(safeAnalysis.getLearningInterventionPlan().getStudentTask()),
                    safeAnalysis.getLearningInterventionPlan() == null ? "" : safe(safeAnalysis.getLearningInterventionPlan().getCheckQuestion()),
                    safe(safeAnalysis.getReportMarkdown())
            ).toLowerCase();

            assertThat(riskWeight(safeAnalysis.getAnswerLeakRisk()))
                    .as(fixture.name() + " risk level")
                    .isGreaterThanOrEqualTo(riskWeight(fixture.expected().riskLevel()));
            assertThat(safeAnalysis.getStudentHintPlan()).as(fixture.name() + " safe hint plan").isNotNull();
            assertThat(safeAnalysis.getStudentHintPlan().getTeachingAction())
                    .as(fixture.name() + " teaching action")
                    .isEqualTo(fixture.expected().expectedSafetyAction());
            assertThat(safeAnalysis.getLearningInterventionPlan()).as(fixture.name() + " intervention plan").isNotNull();
            assertThat(safeAnalysis.getLearningInterventionPlan().getInterventionType())
                    .as(fixture.name() + " intervention action")
                    .isEqualTo(fixture.expected().expectedSafetyAction());
            assertThat(safeAnalysis.getEvidenceRefs()).as(fixture.name() + " evidence refs")
                    .containsAll(fixture.expected().requiredEvidenceRefs());
            fixture.expected().mustNotMention().forEach(phrase -> assertThat(combinedText)
                    .as(fixture.name() + " avoids " + phrase)
                    .doesNotContain(phrase.toLowerCase()));
            assertThat(combinedText)
                    .as(fixture.name() + " safe evidence action")
                    .containsAnyOf("最小", "evidence", "样例", "输出对比", "验证");
        }
    }

    private DiagnosticAgentService newLiveService(String apiKey) {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(taxonomy),
                new PromptTemplateRegistry(),
                new ModelOutputValidator()
        );
        AiReportService aiReportService = new AiReportService(objectMapper, new AiCodeAssistSupport(), runtime);
        ReflectionTestUtils.setField(aiReportService, "enabled", true);
        ReflectionTestUtils.setField(aiReportService, "apiKey", apiKey);
        ReflectionTestUtils.setField(aiReportService, "baseUrl", valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"));
        ReflectionTestUtils.setField(aiReportService, "model", valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Flash"));
        ReflectionTestUtils.setField(aiReportService, "timeoutSeconds", longValueOrDefault(System.getenv("AI_EVAL_TIMEOUT_SECONDS"), 30L));
        ReflectionTestUtils.setField(aiReportService, "externalRuntimeEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_EXTERNAL_RUNTIME_ENABLED"), "true")));
        ReflectionTestUtils.setField(aiReportService, "externalRuntimeProfile",
                valueOrDefault(System.getenv("AI_EVAL_RUNTIME_PROFILE"), "low-latency"));
        ReflectionTestUtils.setField(aiReportService, "maxOutputTokens",
                (int) longValueOrDefault(System.getenv("AI_EVAL_MAX_OUTPUT_TOKENS"), 900L));
        ReflectionTestUtils.setField(aiReportService, "streamEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_ENABLED"), "true")));
        ReflectionTestUtils.setField(aiReportService, "streamFallbackEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_FALLBACK_ENABLED"), "false")));
        return new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                aiReportService,
                new HintSafetyService(null, new ObjectMapper(), taxonomy),
                taxonomy
        );
    }

    private DiagnosticAgentService newOfflineService() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        return new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new PassThroughAiReportService(),
                new HintSafetyService(null, objectMapper, taxonomy),
                taxonomy
        );
    }

    private EvalCase offByOneCase() {
        Submission submission = Submission.builder()
                .id(101L)
                .problemId(1L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        total = 0
                        for i in range(1, n):
                            total += i
                        print(total)
                        """)
                .build();
        return new EvalCase(
                "off-by-one",
                Problem.builder()
                        .id(1L)
                        .title("求 1 到 n 的和")
                        .description("输入一个正整数 n，输出 1 到 n 的整数和。")
                        .difficulty(Problem.Difficulty.EASY)
                        .timeLimit(1000)
                        .memoryLimit(65536)
                        .build(),
                submission,
                List.of(),
                baseline(submission, "WA", List.of("BOUNDARY_CONDITION"), List.of()),
                List.of("LOOP_BOUNDARY", "BOUNDARY_CONDITION"),
                List.of("OFF_BY_ONE"),
                null
        );
    }

    private EvalCase outputFormatCase() {
        Submission submission = Submission.builder()
                .id(102L)
                .problemId(2L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        print(n, end=" ")
                        """)
                .build();
        List<SubmissionCaseResult> cases = List.of(SubmissionCaseResult.builder()
                .testCaseNumber(1)
                .passed(false)
                .hidden(false)
                .actualOutput("42 ")
                .expectedOutput("42")
                .build());
        return new EvalCase(
                "output-format",
                Problem.builder()
                        .id(2L)
                        .title("原样输出")
                        .description("输入一个整数，输出该整数，不要输出多余空格。")
                        .difficulty(Problem.Difficulty.EASY)
                        .timeLimit(1000)
                        .memoryLimit(65536)
                        .build(),
                submission,
                cases,
                baseline(submission, "WA", List.of("IO_FORMAT"), List.of()),
                List.of("IO_FORMAT"),
                List.of("OUTPUT_FORMAT_DETAIL"),
                null
        );
    }

    private EvalCase bruteForceCase() {
        Submission submission = Submission.builder()
                .id(103L)
                .problemId(3L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.TIME_LIMIT_EXCEEDED)
                .sourceCode("""
                        n = int(input())
                        arr = list(map(int, input().split()))
                        ans = 0
                        for i in range(n):
                            for j in range(n):
                                if arr[i] < arr[j]:
                                    ans += 1
                        print(ans)
                        """)
                .build();
        return new EvalCase(
                "brute-force",
                Problem.builder()
                        .id(3L)
                        .title("统计更大元素")
                        .description("给定 n 个整数，统计每个元素右侧比它大的数量总和，n 最大为 200000。")
                        .difficulty(Problem.Difficulty.MEDIUM)
                        .timeLimit(1000)
                        .memoryLimit(65536)
                        .build(),
                submission,
                List.of(),
                baseline(submission, "TLE", List.of("TIME_COMPLEXITY"), List.of()),
                List.of("TIME_COMPLEXITY"),
                List.of("BRUTE_FORCE_LIMIT"),
                null
        );
    }

    private SubmissionAnalysisResponse baseline(Submission submission,
                                                String scenario,
                                                List<String> issueTags,
                                                List<String> fineTags) {
        return SubmissionAnalysisResponse.builder()
                .submissionId(submission.getId())
                .sourceType("RULE_BASED_V1")
                .scenario(scenario)
                .headline("规则层初步诊断")
                .summary("规则层初步诊断")
                .issueTags(issueTags)
                .fineGrainedTags(fineTags)
                .evidenceRefs(List.of("verdict:" + scenario.toLowerCase()))
                .confidence(0.7)
                .answerLeakRisk("LOW")
                .build();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int riskWeight(String risk) {
        return switch (risk == null ? "" : risk.toUpperCase()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private long longValueOrDefault(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<EvalCase> allEvalCases() {
        List<EvalCase> cases = new ArrayList<>();
        cases.add(offByOneCase());
        cases.add(outputFormatCase());
        cases.add(bruteForceCase());
        cases.addAll(teacherCorrectionEvalCases());
        cases.addAll(liveModelCoreEvalCases());
        cases.addAll(complexGeneratedLiveEvalCases());
        return cases;
    }

    private List<EvalCase> complexGeneratedLiveEvalCases() {
        try {
            return new ComplexStudentSubmissionEvalFixtureLoader(objectMapper).loadDefault()
                    .stream()
                    .filter(ComplexStudentSubmissionEvalFixtureLoader.Fixture::liveCandidate)
                    .map(fixture -> new EvalCase(
                            fixture.caseId(),
                            fixture.toProblem(),
                            fixture.toSubmission(),
                            fixture.toCaseResults(),
                            fixture.toBaseline(),
                            fixture.expectedIssueTags(),
                            fixture.expectedFineTags(),
                            fixture
                    ))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load complex generated eval fixtures", exception);
        }
    }

    private List<EvalCase> representativeComplexLiveEvalCases() {
        Map<String, EvalCase> casesById = complexGeneratedLiveEvalCases().stream()
                .collect(Collectors.toMap(EvalCase::name, evalCase -> evalCase, (left, ignored) -> left, LinkedHashMap::new));
        return REPRESENTATIVE_COMPLEX_LIVE_CASE_IDS.stream()
                .map(caseId -> {
                    EvalCase evalCase = casesById.get(caseId);
                    if (evalCase == null) {
                        throw new IllegalStateException("Missing representative complex live eval case: " + caseId);
                    }
                    return evalCase;
                })
                .toList();
    }

    private List<EvalCase> liveModelCoreEvalCases() {
        try {
            return new LiveModelCoreEvalFixtureLoader(objectMapper).loadDefault()
                    .stream()
                    .map(fixture -> new EvalCase(
                            fixture.caseId(),
                            fixture.toProblem(),
                            fixture.toSubmission(),
                            fixture.toCaseResults(),
                            fixture.toBaseline(),
                            fixture.expectedIssueTags(),
                            fixture.expectedFineTags(),
                            null
                    ))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load live model core eval fixtures", exception);
        }
    }

    private boolean shouldIncludeCaseId(EvalCase evalCase, String caseIds) {
        if (caseIds == null || caseIds.isBlank()) {
            return true;
        }
        Set<String> allowed = java.util.Arrays.stream(caseIds.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        return allowed.isEmpty() || allowed.contains(evalCase.name().toLowerCase(Locale.ROOT));
    }

    private List<EvalCase> selectedLiveEvalCases() {
        String caseIds = System.getenv("AI_EVAL_CASE_IDS");
        boolean representativeIntelligenceRun = Boolean.parseBoolean(
                valueOrDefault(System.getenv("AI_EVAL_COMPLEX_INTELLIGENCE_14"), "false"));
        List<EvalCase> sourceCases = representativeIntelligenceRun && (caseIds == null || caseIds.isBlank())
                ? representativeComplexLiveEvalCases()
                : allEvalCases();
        return sourceCases.stream()
                .filter(evalCase -> shouldIncludeCaseId(evalCase, caseIds))
                .limit(Math.max(1, (int) longValueOrDefault(System.getenv("AI_EVAL_SMOKE_LIMIT"),
                        representativeIntelligenceRun ? 14L : 2L)))
                .toList();
    }

    private List<EvalCase> teacherCorrectionEvalCases() {
        try {
            return new TeacherCorrectionEvalFixtureLoader(objectMapper).loadDefault()
                    .stream()
                    .map(fixture -> new EvalCase(
                            fixture.name(),
                            fixture.toProblem(),
                            fixture.toSubmission(),
                            fixture.toCaseResults(),
                            fixture.toBaseline(),
                            fixture.expectedIssueTags(),
                            fixture.expectedFineTags(),
                            null
                    ))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load teacher correction eval fixtures", exception);
        }
    }

    private LiveModelEvalReport runLiveEvalReport(DiagnosticAgentService service, List<EvalCase> cases) {
        String model = valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Pro");
        long latencyBudgetMs = longValueOrDefault(System.getenv("AI_EVAL_LATENCY_BUDGET_MS"), 35_000L);
        long caseDelayMs = Math.max(0, longValueOrDefault(System.getenv("AI_EVAL_CASE_DELAY_MS"), 0L));
        List<LiveModelEvalReport.Entry> entries = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            EvalCase evalCase = cases.get(index);
            waitBetweenLiveModelCases(index, caseDelayMs);
            long startedAt = System.nanoTime();
            try {
                DiagnosticAgentService.AgentResult result = service.diagnose(
                        evalCase.problem(),
                        evalCase.submission(),
                        evalCase.caseResults(),
                        evalCase.baseline(),
                        Assignment.HintPolicy.L2
                );
                long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
                SubmissionAnalysisResponse analysis = result.analysis();
                SubmissionAnalysisResponse.AiInvocation invocation = analysis.getAiInvocation();
                boolean fallbackUsed = invocation == null || invocation.isFallbackUsed();
                boolean issueHit = intersects(analysis.getIssueTags(), evalCase.expectedIssueTags());
                boolean fineHit = intersects(analysis.getFineGrainedTags(), evalCase.expectedFineTags());
                boolean modelCompleted = modelCompleted(invocation, fallbackUsed);
                boolean evidenceValid = analysis.getEvidenceRefs() != null && !analysis.getEvidenceRefs().isEmpty();
                boolean safetyPassed = !"HIGH".equalsIgnoreCase(analysis.getAnswerLeakRisk());
                ComplexDiagnosisQualityScorer.Result complexQuality = complexQualityScorer.score(evalCase.complexFixture(), analysis);
                ComplexDiagnosisQualityScorer.IntelligenceResult intelligenceQuality =
                        complexQualityScorer.intelligence(complexQuality, modelCompleted, fallbackUsed);
                ComplexDiagnosisQualityScorer.StudentFeedbackResult studentFeedbackQuality =
                        complexQualityScorer.studentFeedback(evalCase.complexFixture(), analysis, modelCompleted, fallbackUsed);
                ComplexDiagnosisQualityScorer.EducationAgentQualityResult educationAgentQuality =
                        complexQualityScorer.educationAgentQuality(evalCase.complexFixture(), analysis, modelCompleted, fallbackUsed);
                ComplexDiagnosisQualityScorer.ModelTraceQualityResult modelTraceQuality =
                        complexQualityScorer.modelEducationTrace(evalCase.complexFixture(), analysis, modelCompleted, fallbackUsed);
                DiagnosisChainRubricEvaluator.Result rubricChainQuality = rubricEvaluator.evaluate(
                        DiagnosisQualityRubric.from(evalCase.complexFixture()),
                        analysis,
                        modelCompleted,
                        fallbackUsed
                );
                EducationAgentEval educationAgentEval = educationAgentEval(analysis, modelCompleted, fallbackUsed,
                        evalCase.complexFixture() != null);
                Integer requestBytes = invocation == null || invocation.getRequestBytes() == null
                        ? 0
                        : invocation.getRequestBytes();
                String failureReason = fallbackUsed || "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(safe(invocation == null ? "" : invocation.getStatus()))
                        ? failureReasonFromInvocation(invocation, result.traceSummary(), analysis.getUncertainty())
                        : "NONE";
                String outputSummary = safe(analysis.getHeadline()) + " | " + safe(analysis.getSummary());
                LiveModelEvalReport.Entry entry = LiveModelEvalReport.Entry.builder()
                        .caseId(evalCase.name())
                        .model(model)
                        .promptVersion(invocation == null ? "unknown" : invocation.getPromptVersion())
                        .stage("DIAGNOSIS_AGENT")
                        .latencyMs(latencyMs)
                        .latencyBudgetMs(latencyBudgetMs)
                        .latencyBudgetExceeded(latencyMs > latencyBudgetMs)
                        .status(invocation == null ? "UNKNOWN" : invocation.getStatus())
                        .failureStage(failureStageFromInvocation(invocation, analysis.getUncertainty()))
                        .fallbackUsed(fallbackUsed)
                        .runtimeProfile(invocation == null ? "auto" : invocation.getRuntimeProfile())
                        .requestBytes(requestBytes)
                        .requestCompact(invocation != null && Boolean.TRUE.equals(invocation.getRequestCompact()))
                        .transportMode(invocation == null ? "" : invocation.getTransportMode())
                        .streamChunkCount(invocation == null ? 0 : invocation.getStreamChunkCount())
                        .streamContentChunkCount(invocation == null ? 0 : invocation.getStreamContentChunkCount())
                        .streamReasoningChunkCount(invocation == null ? 0 : invocation.getStreamReasoningChunkCount())
                        .streamInvalidChunkCount(invocation == null ? 0 : invocation.getStreamInvalidChunkCount())
                        .streamFinishReason(invocation == null ? "" : invocation.getStreamFinishReason())
                        .streamFallbackRetryUsed(invocation != null && Boolean.TRUE.equals(invocation.getStreamFallbackRetryUsed()))
                        .jsonValid(!fallbackUsed)
                        .modelCompleted(modelCompleted)
                        .expectedIssueTagHit(issueHit)
                        .expectedFineTagHit(fineHit)
                        .modelIssueTagHit(modelCompleted && issueHit)
                        .modelFineTagHit(modelCompleted && fineHit)
                        .fallbackIssueTagHit(fallbackUsed && issueHit)
                        .fallbackFineTagHit(fallbackUsed && fineHit)
                        .evidenceValid(evidenceValid)
                        .safetyPassed(safetyPassed)
                        .complexCase(complexQuality.complexCase())
                        .rubricChainEvaluated(rubricChainQuality.evaluated())
                        .rubricChainPassed(rubricChainQuality.overallVerdict())
                        .rubricChainStagePassedCount(rubricChainQuality.passedStageCount())
                        .rubricChainStageTotalCount(rubricChainQuality.totalStageCount())
                        .rubricChainScore(rubricChainQuality.score())
                        .rubricChainPassedStages(rubricChainQuality.passedStageSignals())
                        .rubricChainFailedStages(rubricChainQuality.failedStages())
                        .rubricChainFailedReasons(rubricChainQuality.failedReasons())
                        .complexQualityPassed(complexQuality.passed())
                        .complexMetricPassedCount(complexQuality.passedMetricCount())
                        .complexMetricTotalCount(complexQuality.totalMetricCount())
                        .complexQualityScore(complexQuality.score())
                        .complexPassedMetrics(complexQuality.passedMetricSignals())
                        .complexFailedMetrics(complexQuality.failedMetrics())
                        .intelligenceEvaluated(intelligenceQuality.evaluated())
                        .intelligenceQualityPassed(intelligenceQuality.passed())
                        .intelligenceMetricPassedCount(intelligenceQuality.passedMetricCount())
                        .intelligenceMetricTotalCount(intelligenceQuality.totalMetricCount())
                        .intelligenceQualityScore(intelligenceQuality.score())
                        .intelligencePassedMetrics(intelligenceQuality.passedMetricSignals())
                        .intelligenceFailedMetrics(intelligenceQuality.failedMetrics())
                        .educationAgentEvaluated(educationAgentEval.evaluated())
                        .educationAgentJudgmentComplete(educationAgentEval.judgmentComplete())
                        .educationAgentHasPrimaryReasoning(educationAgentEval.hasPrimaryReasoning())
                        .educationAgentHasNextAction(educationAgentEval.hasNextAction())
                        .educationAgentHasImprovementOpportunity(educationAgentEval.hasImprovementOpportunity())
                        .educationAgentHasSecondarySignal(educationAgentEval.hasSecondarySignal())
                        .educationAgentQualityEvaluated(educationAgentQuality.evaluated())
                        .educationAgentQualityPassed(educationAgentQuality.passed())
                        .educationAgentMetricPassedCount(educationAgentQuality.passedMetricCount())
                        .educationAgentMetricTotalCount(educationAgentQuality.totalMetricCount())
                        .educationAgentQualityScore(educationAgentQuality.score())
                        .educationAgentPassedMetrics(educationAgentQuality.passedMetricSignals())
                        .educationAgentFailedMetrics(educationAgentQuality.failedMetrics())
                        .modelTraceEvaluated(modelTraceQuality.evaluated())
                        .modelTraceQualityPassed(modelTraceQuality.passed())
                        .modelTraceMetricPassedCount(modelTraceQuality.passedMetricCount())
                        .modelTraceMetricTotalCount(modelTraceQuality.totalMetricCount())
                        .modelTraceQualityScore(modelTraceQuality.score())
                        .modelTracePassedMetrics(modelTraceQuality.passedMetricSignals())
                        .modelTraceFailedMetrics(modelTraceQuality.failedMetrics())
                        .studentFeedbackEvaluated(studentFeedbackQuality.evaluated())
                        .studentFeedbackQualityPassed(studentFeedbackQuality.passed())
                        .studentFeedbackMetricPassedCount(studentFeedbackQuality.passedMetricCount())
                        .studentFeedbackMetricTotalCount(studentFeedbackQuality.totalMetricCount())
                        .studentFeedbackQualityScore(studentFeedbackQuality.score())
                        .studentFeedbackPassedMetrics(studentFeedbackQuality.passedMetricSignals())
                        .studentFeedbackFailedMetrics(studentFeedbackQuality.failedMetrics())
                        .localTruth(localTruth(evalCase.complexFixture()))
                        .modelOutput(modelOutput(analysis, outputSummary))
                        .studentFeedback(studentFeedbackOutput(analysis.getStudentFeedback()))
                        .modelJudgment(modelJudgment(model, invocation, modelCompleted, fallbackUsed,
                                intelligenceQuality.evaluated(), failureReason))
                        .qualityScore(qualityScore(complexQuality, intelligenceQuality, educationAgentQuality, modelTraceQuality,
                                studentFeedbackQuality, rubricChainQuality))
                        .actualIssueTags(analysis.getIssueTags())
                        .actualFineGrainedTags(analysis.getFineGrainedTags())
                        .actualEvidenceRefs(analysis.getEvidenceRefs())
                        .failureReason(failureReason)
                        .outputSummary(outputSummary)
                        .build();
                entry.setSafetyCategories(safetyCategoriesForReport(entry));
                entries.add(entry);
            } catch (Exception exception) {
                long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
                ComplexDiagnosisQualityScorer.IntelligenceResult intelligenceQuality =
                        ComplexDiagnosisQualityScorer.IntelligenceResult.empty(evalCase.complexFixture() != null);
                ComplexDiagnosisQualityScorer.EducationAgentQualityResult educationAgentQuality =
                        ComplexDiagnosisQualityScorer.EducationAgentQualityResult.empty(evalCase.complexFixture() != null);
                ComplexDiagnosisQualityScorer.ModelTraceQualityResult modelTraceQuality =
                        ComplexDiagnosisQualityScorer.ModelTraceQualityResult.empty(evalCase.complexFixture() != null);
                ComplexDiagnosisQualityScorer.StudentFeedbackResult studentFeedbackQuality =
                        ComplexDiagnosisQualityScorer.StudentFeedbackResult.empty(evalCase.complexFixture() != null, false);
                DiagnosisChainRubricEvaluator.Result rubricChainQuality =
                        DiagnosisChainRubricEvaluator.Result.empty(evalCase.complexFixture() != null);
                String failureReason = classifyException(exception);
                LiveModelEvalReport.Entry entry = LiveModelEvalReport.Entry.builder()
                        .caseId(evalCase.name())
                        .model(model)
                        .promptVersion("unknown")
                        .stage("DIAGNOSIS_AGENT")
                        .latencyMs(latencyMs)
                        .latencyBudgetMs(latencyBudgetMs)
                        .latencyBudgetExceeded(latencyMs > latencyBudgetMs)
                        .status("EXCEPTION")
                        .fallbackUsed(true)
                        .runtimeProfile(valueOrDefault(System.getenv("AI_EVAL_RUNTIME_PROFILE"), "auto"))
                        .requestBytes(0)
                        .requestCompact(false)
                        .jsonValid(false)
                        .modelCompleted(false)
                        .expectedIssueTagHit(false)
                        .expectedFineTagHit(false)
                        .modelIssueTagHit(false)
                        .modelFineTagHit(false)
                        .fallbackIssueTagHit(false)
                        .fallbackFineTagHit(false)
                        .evidenceValid(false)
                        .safetyPassed(false)
                        .complexCase(evalCase.complexFixture() != null)
                        .rubricChainEvaluated(rubricChainQuality.evaluated())
                        .rubricChainPassed(rubricChainQuality.overallVerdict())
                        .rubricChainStagePassedCount(rubricChainQuality.passedStageCount())
                        .rubricChainStageTotalCount(rubricChainQuality.totalStageCount())
                        .rubricChainScore(rubricChainQuality.score())
                        .rubricChainPassedStages(rubricChainQuality.passedStageSignals())
                        .rubricChainFailedStages(rubricChainQuality.failedStages())
                        .rubricChainFailedReasons(rubricChainQuality.failedReasons())
                        .complexQualityPassed(false)
                        .complexMetricPassedCount(0)
                        .complexMetricTotalCount(evalCase.complexFixture() == null ? 0 : ComplexDiagnosisQualityScorer.METRICS.size())
                        .complexQualityScore(0.0)
                        .complexPassedMetrics(List.of())
                        .complexFailedMetrics(evalCase.complexFixture() == null ? List.of() : ComplexDiagnosisQualityScorer.METRICS)
                        .intelligenceEvaluated(intelligenceQuality.evaluated())
                        .intelligenceQualityPassed(false)
                        .intelligenceMetricPassedCount(0)
                        .intelligenceMetricTotalCount(0)
                        .intelligenceQualityScore(0.0)
                        .intelligencePassedMetrics(List.of())
                        .intelligenceFailedMetrics(List.of())
                        .educationAgentEvaluated(false)
                        .educationAgentJudgmentComplete(false)
                        .educationAgentHasPrimaryReasoning(false)
                        .educationAgentHasNextAction(false)
                        .educationAgentHasImprovementOpportunity(false)
                        .educationAgentHasSecondarySignal(false)
                        .educationAgentQualityEvaluated(false)
                        .educationAgentQualityPassed(false)
                        .educationAgentMetricPassedCount(0)
                        .educationAgentMetricTotalCount(0)
                        .educationAgentQualityScore(0.0)
                        .educationAgentPassedMetrics(List.of())
                        .educationAgentFailedMetrics(List.of())
                        .modelTraceEvaluated(false)
                        .modelTraceQualityPassed(false)
                        .modelTraceMetricPassedCount(0)
                        .modelTraceMetricTotalCount(0)
                        .modelTraceQualityScore(0.0)
                        .modelTracePassedMetrics(List.of())
                        .modelTraceFailedMetrics(List.of())
                        .studentFeedbackEvaluated(false)
                        .studentFeedbackQualityPassed(false)
                        .studentFeedbackMetricPassedCount(0)
                        .studentFeedbackMetricTotalCount(0)
                        .studentFeedbackQualityScore(0.0)
                        .studentFeedbackPassedMetrics(List.of())
                        .studentFeedbackFailedMetrics(List.of())
                        .localTruth(localTruth(evalCase.complexFixture()))
                        .modelOutput(LiveModelEvalReport.ModelOutput.builder()
                                .summary(exception.getClass().getSimpleName() + ": " + exception.getMessage())
                                .build())
                        .modelJudgment(LiveModelEvalReport.ModelJudgment.builder()
                                .modelCompleted(false)
                                .fallbackUsed(true)
                                .countedAsIntelligence(false)
                                .status("EXCEPTION")
                                .failureReason(failureReason)
                                .provider("ModelScope")
                                .model(model)
                                .baseUrl(valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"))
                                .runtimeProfile(valueOrDefault(System.getenv("AI_EVAL_RUNTIME_PROFILE"), "auto"))
                                .build())
                        .qualityScore(qualityScore(null, intelligenceQuality, educationAgentQuality, modelTraceQuality,
                                studentFeedbackQuality, rubricChainQuality))
                        .failureReason(failureReason)
                        .outputSummary(exception.getClass().getSimpleName() + ": " + exception.getMessage())
                        .build();
                entry.setSafetyCategories(safetyCategoriesForReport(entry));
                entries.add(entry);
            }
        }
        return summarizeReport(model, entries, caseDelayMs);
    }

    private void waitBetweenLiveModelCases(int index, long caseDelayMs) {
        if (index <= 0 || caseDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(caseDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Live model eval interrupted while waiting between cases.", exception);
        }
    }

    private LiveModelEvalReport.LocalTruth localTruth(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture) {
        if (fixture == null) {
            return null;
        }
        return LiveModelEvalReport.LocalTruth.builder()
                .caseId(fixture.caseId())
                .bugPattern(fixture.quality() == null ? "" : fixture.quality().bugPattern())
                .primaryIssueTag(fixture.primaryRootCause() == null ? "" : fixture.primaryRootCause().issueTag())
                .primaryFineGrainedTag(fixture.primaryRootCause() == null ? "" : fixture.primaryRootCause().fineGrainedTag())
                .primaryEvidenceRef(fixture.primaryRootCause() == null ? "" : fixture.primaryRootCause().evidenceRef())
                .expectedTeachingPriority(fixture.expectedTeachingPriority())
                .requiredEvidenceRefs(fixture.requiredEvidenceRefs())
                .distractingSignals(fixture.distractingSignals())
                .mustMention(fixture.mustMention())
                .mustNotMention(fixture.mustNotMention())
                .expectedImprovementOpportunities(fixture.expectedImprovementOpportunities() == null
                        ? List.of()
                        : fixture.expectedImprovementOpportunities().stream()
                        .map(item -> LiveModelEvalReport.ExpectedImprovementOpportunity.builder()
                                .category(item.category())
                                .studentMessage(item.studentMessage())
                                .benefit(item.benefit())
                                .build())
                        .toList())
                .build();
    }

    private LiveModelEvalReport.StudentFeedbackOutput studentFeedbackOutput(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null) {
            return null;
        }
        return LiveModelEvalReport.StudentFeedbackOutput.builder()
                .summary(feedback.getSummary())
                .blockingMessages(feedback.getBlockingIssues() == null ? List.of() : feedback.getBlockingIssues().stream()
                        .map(SubmissionAnalysisResponse.FeedbackIssue::getStudentMessage)
                        .toList())
                .secondaryMessages(feedback.getSecondaryIssues() == null ? List.of() : feedback.getSecondaryIssues().stream()
                        .map(SubmissionAnalysisResponse.SecondaryIssue::getStudentMessage)
                        .toList())
                .improvementCategories(feedback.getImprovementOpportunities() == null ? List.of() : feedback.getImprovementOpportunities().stream()
                        .map(SubmissionAnalysisResponse.ImprovementOpportunity::getCategory)
                        .toList())
                .improvementMessages(feedback.getImprovementOpportunities() == null ? List.of() : feedback.getImprovementOpportunities().stream()
                        .map(SubmissionAnalysisResponse.ImprovementOpportunity::getStudentMessage)
                        .toList())
                .nextAction(feedback.getNextLearningAction() == null ? "" : feedback.getNextLearningAction().getTask())
                .evidenceRefs(studentFeedbackEvidenceRefs(feedback))
                .build();
    }

    private EducationAgentEval educationAgentEval(SubmissionAnalysisResponse analysis,
                                                  boolean modelCompleted,
                                                  boolean fallbackUsed,
                                                  boolean complexCase) {
        boolean evaluated = complexCase && modelCompleted && !fallbackUsed;
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis == null ? null : analysis.getStudentFeedback();
        boolean hasPrimaryReasoning = feedback != null
                && feedback.getBlockingIssues() != null
                && feedback.getBlockingIssues().stream()
                .anyMatch(issue -> issue != null && !safe(issue.getStudentMessage()).isBlank());
        boolean hasNextAction = feedback != null
                && feedback.getNextLearningAction() != null
                && !safe(feedback.getNextLearningAction().getTask()).isBlank();
        boolean hasImprovement = feedback != null
                && feedback.getImprovementOpportunities() != null
                && feedback.getImprovementOpportunities().stream()
                .anyMatch(item -> item != null && !safe(item.getCategory()).isBlank()
                        && !safe(item.getBenefit()).isBlank());
        boolean hasSecondarySignal = feedback != null
                && feedback.getSecondaryIssues() != null
                && feedback.getSecondaryIssues().stream()
                .anyMatch(issue -> issue != null
                        && (!safe(issue.getStudentMessage()).isBlank() || !safe(issue.getWhyNotPrimary()).isBlank()));
        boolean judgmentComplete = evaluated && hasPrimaryReasoning && hasNextAction && hasImprovement;
        return new EducationAgentEval(evaluated, judgmentComplete, hasPrimaryReasoning, hasNextAction,
                hasImprovement, hasSecondarySignal);
    }

    private record EducationAgentEval(boolean evaluated,
                                      boolean judgmentComplete,
                                      boolean hasPrimaryReasoning,
                                      boolean hasNextAction,
                                      boolean hasImprovementOpportunity,
                                      boolean hasSecondarySignal) {
    }

    private LiveModelEvalReport.ModelOutput modelOutput(SubmissionAnalysisResponse analysis, String outputSummary) {
        if (analysis == null) {
            return null;
        }
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
        SubmissionAnalysisResponse.ModelEducationTrace educationTrace = analysis.getModelEducationTrace();
        boolean hasEducationTrace = educationTrace != null;
        return LiveModelEvalReport.ModelOutput.builder()
                .issueTags(analysis.getIssueTags())
                .fineGrainedTags(analysis.getFineGrainedTags())
                .evidenceRefs(analysis.getEvidenceRefs())
                .answerLeakRisk(analysis.getAnswerLeakRisk())
                .summary(outputSummary)
                .educationJudgmentSource(hasEducationTrace ? safe(educationTrace.getSource())
                        : feedback == null ? "" : "studentFeedback")
                .educationPrimaryReasoning(hasEducationTrace ? safe(educationTrace.getPrimaryReasoning()) : firstBlockingMessage(feedback))
                .educationTeachingPriority(hasEducationTrace ? safe(educationTrace.getTeachingPriority())
                        : feedback == null ? "" : safe(feedback.getSummary()))
                .educationSecondarySignals(hasEducationTrace ? modelEducationSecondarySignals(educationTrace)
                        : secondarySignalTexts(feedback))
                .educationImprovementCategories(hasEducationTrace ? safeList(educationTrace.getImprovementCategories())
                        : improvementCategories(feedback))
                .educationNextAction(hasEducationTrace ? safe(educationTrace.getNextLearningAction()) : feedbackNextAction(feedback))
                .educationEvidenceRefs(hasEducationTrace ? modelEducationEvidenceRefs(educationTrace)
                        : studentFeedbackEvidenceRefs(feedback))
                .build();
    }

    private List<String> modelEducationSecondarySignals(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null || trace.getSecondaryIssues() == null) {
            return List.of();
        }
        return trace.getSecondaryIssues().stream()
                .map(note -> {
                    if (note == null) {
                        return "";
                    }
                    String message = safe(note.getMessage());
                    String tag = safe(note.getIssueTag());
                    if (message.isBlank()) {
                        return tag;
                    }
                    if (tag.isBlank()) {
                        return message;
                    }
                    return message + " | " + tag;
                })
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> modelEducationEvidenceRefs(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        addEvidenceRefs(refs, trace.getEvidenceRefs());
        if (trace.getSecondaryIssues() != null) {
            trace.getSecondaryIssues().forEach(note -> {
                if (note != null) {
                    addEvidenceRefs(refs, note.getEvidenceRefs());
                }
            });
        }
        if (trace.getDistractorNotes() != null) {
            trace.getDistractorNotes().forEach(note -> {
                if (note != null) {
                    addEvidenceRefs(refs, note.getEvidenceRefs());
                }
            });
        }
        addEvidenceRefs(refs, trace.getNextLearningActionEvidenceRefs());
        return refs.stream()
                .filter(ref -> !safe(ref).isBlank())
                .distinct()
                .toList();
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> !safe(value).isBlank())
                .distinct()
                .toList();
    }

    private String firstBlockingMessage(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getBlockingIssues() == null) {
            return "";
        }
        for (SubmissionAnalysisResponse.FeedbackIssue issue : feedback.getBlockingIssues()) {
            if (issue == null) {
                continue;
            }
            String message = safe(issue.getStudentMessage());
            if (!message.isBlank()) {
                return message;
            }
        }
        return "";
    }

    private List<String> secondarySignalTexts(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getSecondaryIssues() == null) {
            return List.of();
        }
        return feedback.getSecondaryIssues().stream()
                .map(issue -> {
                    if (issue == null) {
                        return "";
                    }
                    String message = safe(issue.getStudentMessage());
                    String whyNotPrimary = safe(issue.getWhyNotPrimary());
                    if (message.isBlank()) {
                        return whyNotPrimary;
                    }
                    if (whyNotPrimary.isBlank()) {
                        return message;
                    }
                    return message + " | " + whyNotPrimary;
                })
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> improvementCategories(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getImprovementOpportunities() == null) {
            return List.of();
        }
        return feedback.getImprovementOpportunities().stream()
                .map(item -> item == null ? "" : safe(item.getCategory()))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String feedbackNextAction(SubmissionAnalysisResponse.StudentFeedback feedback) {
        return feedback == null || feedback.getNextLearningAction() == null
                ? ""
                : safe(feedback.getNextLearningAction().getTask());
    }

    private List<String> studentFeedbackEvidenceRefs(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        if (feedback.getBlockingIssues() != null) {
            feedback.getBlockingIssues().forEach(issue -> {
                if (issue != null) {
                    addEvidenceRefs(refs, issue.getEvidenceRefs());
                }
            });
        }
        if (feedback.getSecondaryIssues() != null) {
            feedback.getSecondaryIssues().forEach(issue -> {
                if (issue != null) {
                    addEvidenceRefs(refs, issue.getEvidenceRefs());
                }
            });
        }
        if (feedback.getImprovementOpportunities() != null) {
            feedback.getImprovementOpportunities().forEach(item -> {
                if (item != null) {
                    addEvidenceRefs(refs, item.getEvidenceRefs());
                }
            });
        }
        if (feedback.getNextLearningAction() != null) {
            addEvidenceRefs(refs, feedback.getNextLearningAction().getEvidenceRefs());
        }
        return refs.stream()
                .filter(ref -> !safe(ref).isBlank())
                .distinct()
                .toList();
    }

    private void addEvidenceRefs(List<String> refs, List<String> evidenceRefs) {
        if (evidenceRefs != null) {
            refs.addAll(evidenceRefs);
        }
    }

    private LiveModelEvalReport.ModelJudgment modelJudgment(String model,
                                                            SubmissionAnalysisResponse.AiInvocation invocation,
                                                            boolean modelCompleted,
                                                            boolean fallbackUsed,
                                                            boolean countedAsIntelligence,
                                                            String failureReason) {
        return LiveModelEvalReport.ModelJudgment.builder()
                .modelCompleted(modelCompleted)
                .fallbackUsed(fallbackUsed)
                .countedAsIntelligence(countedAsIntelligence)
                .status(invocation == null ? "UNKNOWN" : invocation.getStatus())
                .failureReason(failureReason)
                .provider("ModelScope")
                .model(model)
                .baseUrl(valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"))
                .runtimeProfile(invocation == null ? valueOrDefault(System.getenv("AI_EVAL_RUNTIME_PROFILE"), "auto")
                        : invocation.getRuntimeProfile())
                .build();
    }

    private LiveModelEvalReport.QualityScore qualityScore(ComplexDiagnosisQualityScorer.Result complexQuality,
                                                          ComplexDiagnosisQualityScorer.IntelligenceResult intelligenceQuality,
                                                          ComplexDiagnosisQualityScorer.EducationAgentQualityResult educationAgentQuality,
                                                          ComplexDiagnosisQualityScorer.ModelTraceQualityResult modelTraceQuality,
                                                          ComplexDiagnosisQualityScorer.StudentFeedbackResult studentFeedbackQuality,
                                                          DiagnosisChainRubricEvaluator.Result rubricChainQuality) {
        return LiveModelEvalReport.QualityScore.builder()
                .complexQualityScore(complexQuality == null ? 0.0 : complexQuality.score())
                .intelligenceQualityScore(intelligenceQuality == null ? 0.0 : intelligenceQuality.score())
                .educationAgentQualityScore(educationAgentQuality == null ? 0.0 : educationAgentQuality.score())
                .modelTraceQualityScore(modelTraceQuality == null ? 0.0 : modelTraceQuality.score())
                .studentFeedbackQualityScore(studentFeedbackQuality == null ? 0.0 : studentFeedbackQuality.score())
                .rubricChainScore(rubricChainQuality == null ? 0.0 : rubricChainQuality.score())
                .complexMetrics(complexQuality == null ? Map.of() : complexQuality.metrics())
                .intelligenceMetrics(intelligenceQuality == null ? Map.of() : intelligenceQuality.metrics())
                .educationAgentMetrics(educationAgentQuality == null ? Map.of() : educationAgentQuality.metrics())
                .modelTraceMetrics(modelTraceQuality == null ? Map.of() : modelTraceQuality.metrics())
                .studentFeedbackMetrics(studentFeedbackQuality == null ? Map.of() : studentFeedbackQuality.metrics())
                .rubricChainStages(rubricChainQuality == null ? Map.of() : rubricChainQuality.stagePasses())
                .build();
    }

    private OfflineRuntimeProfileEvalReport runOfflineRuntimeProfileEval(List<EvalCase> cases) {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(taxonomy),
                new PromptTemplateRegistry(),
                new ModelOutputValidator()
        );
        DiagnosisEvidencePackageBuilder evidenceBuilder = new DiagnosisEvidencePackageBuilder();
        List<OfflineRuntimeProfileEvalReportFactory.OfflineEvalCase> offlineCases = cases.stream()
                .map(evalCase -> {
                    DiagnosisEvidencePackage evidencePackage = evidenceBuilder.build(
                            evalCase.problem(),
                            evalCase.submission(),
                            evalCase.caseResults(),
                            evalCase.baseline(),
                            Assignment.HintPolicy.L2,
                            null,
                            null
                    );
                    return new OfflineRuntimeProfileEvalReportFactory.OfflineEvalCase(
                            evalCase.name(),
                            evidencePackage,
                            evalCase.baseline()
                    );
                })
                .toList();
        return new OfflineRuntimeProfileEvalReportFactory(objectMapper, runtime)
                .fromCases(offlineCases);
    }

    private void assertModelBaselineRegressionGateIfEnabled(LiveModelEvalReport report, Path reportPath) throws IOException {
        String baselineReport = System.getenv("AI_EVAL_MODEL_BASELINE_REPORT");
        if (baselineReport == null || baselineReport.isBlank()) {
            return;
        }
        Path baselinePath = Path.of(baselineReport);
        LiveModelEvalReport baseline = objectMapper.readValue(baselinePath.toFile(), LiveModelEvalReport.class);
        List<String> violations = LiveEvalBaselineRegressionGate.evaluateModel(report, baseline.getQualityBaselineDrafts());
        LiveEvalBaselineRegressionReport regressionReport = new LiveEvalBaselineRegressionReportFactory()
                .fromModel(report, baseline.getQualityBaselineDrafts(),
                        baselinePath.toString(), reportPath.toString(), violations);
        Path regressionReportPath = writeBaselineRegressionReport(
                "live-model-eval-baseline-regression",
                regressionReport
        );
        LiveModelEvalComparisonReport comparisonReport =
                new LiveModelEvalComparisonReportFactory().compare(baseline, report);
        Path comparisonReportPath = writeLiveModelEvalComparisonReport(comparisonReport);
        System.out.println("Live model eval baseline regression report saved to: " + regressionReportPath);
        System.out.println("Live model eval baseline regression summary: " + regressionReport.consoleSummary());
        System.out.println("Live model eval comparison report saved to: " + comparisonReportPath);
        System.out.println("Live model eval comparison summary: " + comparisonReport.consoleSummary());
        assertThat(violations)
                .as("live model eval baseline regression violations against " + baselinePath)
                .isEmpty();
    }

    private LiveModelEvalReport summarizeReport(String model, List<LiveModelEvalReport.Entry> entries) {
        return summarizeReport(model, entries, 0L);
    }

    private LiveModelEvalReport summarizeReport(String model, List<LiveModelEvalReport.Entry> entries, long caseDelayMs) {
        List<LiveEvalRuntimeFixtureDraft> runtimeDrafts =
                new LiveEvalRuntimeFixtureDraftFactory().fromModelEntries(entries);
        List<LiveEvalQualityBaselineDraft> qualityBaselines =
                new LiveEvalQualityBaselineDraftFactory().fromModelEntries(entries);
        RecoverySummary recoverySummary = summarizeRecovery(entries, runtimeDrafts);
        long safeCaseDelayMs = Math.max(0, caseDelayMs);
        return LiveModelEvalReport.builder()
                .model(model)
                .promptVersion(summarizePromptVersion(entries))
                .provider("ModelScope")
                .baseUrl(valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"))
                .runtimeProfile(summarizeRuntimeProfile(entries))
                .timeoutSeconds(longValueOrDefault(System.getenv("AI_EVAL_TIMEOUT_SECONDS"), 35L))
                .maxOutputTokens((int) longValueOrDefault(System.getenv("AI_EVAL_MAX_OUTPUT_TOKENS"), 900L))
                .totalCount(entries.size())
                .completedCount((int) entries.stream()
                        .filter(entry -> "MODEL_COMPLETED".equalsIgnoreCase(safe(entry.getStatus())))
                        .count())
                .partialCount((int) entries.stream()
                        .filter(entry -> "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(safe(entry.getStatus())))
                        .count())
                .fallbackCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getFallbackUsed())).count())
                .timeoutCount((int) entries.stream()
                        .filter(entry -> entry.getFailureReason() != null && entry.getFailureReason().contains("TIMEOUT"))
                        .count())
                .caseDelayMs(safeCaseDelayMs)
                .delayedCaseCount(safeCaseDelayMs <= 0 ? 0 : Math.max(0, entries.size() - 1))
                .latencyBudgetMs(entries.stream()
                        .map(LiveModelEvalReport.Entry::getLatencyBudgetMs)
                        .filter(value -> value != null && value > 0)
                        .findFirst()
                        .orElse(0L))
                .latencyBudgetExceededCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getLatencyBudgetExceeded()))
                        .count())
                .issueTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getExpectedIssueTagHit())).count())
                .fineTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getExpectedFineTagHit())).count())
                .modelIssueTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getModelIssueTagHit())).count())
                .modelFineTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getModelFineTagHit())).count())
                .fallbackIssueTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getFallbackIssueTagHit())).count())
                .fallbackFineTagHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getFallbackFineTagHit())).count())
                .safetyPassedCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getSafetyPassed())).count())
                .safetyCategoryCounts(safetyCategoryCounts(entries))
                .complexCaseCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())).count())
                .rubricChainCaseCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())).count())
                .rubricChainEvaluatedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getRubricChainEvaluated()))
                        .count())
                .rubricChainFallbackExcludedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())
                                && !Boolean.TRUE.equals(entry.getRubricChainEvaluated()))
                        .count())
                .rubricChainPassedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getRubricChainPassed()))
                        .count())
                .rubricChainStagePassedCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getRubricChainStagePassedCount()))
                        .sum())
                .rubricChainStageTotalCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getRubricChainStageTotalCount()))
                        .sum())
                .rubricChainAverageScore(rubricChainAverageScore(entries))
                .rubricChainStagePassCounts(rubricChainStageCounts(entries, true))
                .rubricChainStageFailCounts(rubricChainStageCounts(entries, false))
                .complexQualityPassedCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getComplexQualityPassed())).count())
                .complexMetricPassedCount(entries.stream().mapToInt(entry -> intValue(entry.getComplexMetricPassedCount())).sum())
                .complexMetricTotalCount(entries.stream().mapToInt(entry -> intValue(entry.getComplexMetricTotalCount())).sum())
                .complexQualityAverageScore(complexQualityAverageScore(entries))
                .intelligenceCaseCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())).count())
                .intelligenceCompletedCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getIntelligenceEvaluated())).count())
                .intelligenceFallbackExcludedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())
                                && !Boolean.TRUE.equals(entry.getIntelligenceEvaluated()))
                        .count())
                .intelligenceQualityPassedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getIntelligenceQualityPassed()))
                        .count())
                .intelligenceMetricPassedCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getIntelligenceMetricPassedCount()))
                        .sum())
                .intelligenceMetricTotalCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getIntelligenceMetricTotalCount()))
                        .sum())
                .intelligenceQualityAverageScore(intelligenceQualityAverageScore(entries))
                .intelligenceMetricPassCounts(intelligenceMetricCounts(entries, true))
                .intelligenceMetricFailCounts(intelligenceMetricCounts(entries, false))
                .educationAgentCaseCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase()))
                        .count())
                .educationAgentCompletedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getEducationAgentEvaluated()))
                        .count())
                .educationAgentFallbackExcludedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())
                                && !Boolean.TRUE.equals(entry.getEducationAgentEvaluated()))
                        .count())
                .educationAgentJudgmentCompleteCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getEducationAgentJudgmentComplete()))
                        .count())
                .educationAgentQualityPassedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getEducationAgentQualityPassed()))
                        .count())
                .educationAgentMetricPassedCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getEducationAgentMetricPassedCount()))
                        .sum())
                .educationAgentMetricTotalCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getEducationAgentMetricTotalCount()))
                        .sum())
                .educationAgentQualityAverageScore(educationAgentQualityAverageScore(entries))
                .educationAgentMetricPassCounts(educationAgentMetricCounts(entries, true))
                .educationAgentMetricFailCounts(educationAgentMetricCounts(entries, false))
                .modelTraceCaseCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase()))
                        .count())
                .modelTraceCompletedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getModelTraceEvaluated()))
                        .count())
                .modelTraceFallbackExcludedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase())
                                && !Boolean.TRUE.equals(entry.getModelTraceEvaluated()))
                        .count())
                .modelTraceQualityPassedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getModelTraceQualityPassed()))
                        .count())
                .modelTraceMetricPassedCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getModelTraceMetricPassedCount()))
                        .sum())
                .modelTraceMetricTotalCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getModelTraceMetricTotalCount()))
                        .sum())
                .modelTraceQualityAverageScore(modelTraceQualityAverageScore(entries))
                .modelTraceMetricPassCounts(modelTraceMetricCounts(entries, true))
                .modelTraceMetricFailCounts(modelTraceMetricCounts(entries, false))
                .studentFeedbackCaseCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase()))
                        .count())
                .studentFeedbackCompletedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getStudentFeedbackEvaluated()))
                        .count())
                .studentFeedbackQualityPassedCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getStudentFeedbackQualityPassed()))
                        .count())
                .studentFeedbackMetricPassedCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getStudentFeedbackMetricPassedCount()))
                        .sum())
                .studentFeedbackMetricTotalCount(entries.stream()
                        .mapToInt(entry -> intValue(entry.getStudentFeedbackMetricTotalCount()))
                        .sum())
                .studentFeedbackQualityAverageScore(studentFeedbackQualityAverageScore(entries))
                .studentFeedbackMetricPassCounts(studentFeedbackMetricCounts(entries, true))
                .studentFeedbackMetricFailCounts(studentFeedbackMetricCounts(entries, false))
                .runtimeFixtureDraftCount(runtimeDrafts.size())
                .qualityBaselineDraftCount(qualityBaselines.size())
                .recoveryStatus(recoverySummary.status())
                .recoveryCheckCount(recoverySummary.checkCount())
                .recoveryPassedCheckCount(recoverySummary.passedCheckCount())
                .recoveryBlockedReasonCount(recoverySummary.blockedReasons().size())
                .recoveryPassedChecks(recoverySummary.passedChecks())
                .recoveryBlockedReasons(recoverySummary.blockedReasons())
                .entries(entries)
                .runtimeFixtureDrafts(runtimeDrafts)
                .qualityBaselineDrafts(qualityBaselines)
                .build();
    }

    private Map<String, Integer> safetyCategoryCounts(List<LiveModelEvalReport.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        return entries.stream()
                .flatMap(entry -> safeList(entry.getSafetyCategories()).stream())
                .filter(category -> category != null && !category.isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        Function.identity(),
                        ignored -> 1,
                        Integer::sum,
                        java.util.LinkedHashMap::new
                ));
    }

    private List<String> safetyCategoriesForReport(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return List.of();
        }
        String answerLeakRisk = entry.getModelOutput() == null ? "" : safe(entry.getModelOutput().getAnswerLeakRisk());
        boolean safetyRisk = Boolean.FALSE.equals(entry.getSafetyPassed())
                || "HIGH".equalsIgnoreCase(answerLeakRisk)
                || safe(entry.getFailureReason()).contains("SAFETY_RISK")
                || safe(entry.getFailureStage()).contains("SAFETY_RISK");
        return safetyRisk ? LiveModelEvalSafetyCategoryClassifier.classify(entry) : List.of();
    }

    private String summarizePromptVersion(List<LiveModelEvalReport.Entry> entries) {
        List<String> promptVersions = entries == null ? List.of() : entries.stream()
                .map(LiveModelEvalReport.Entry::getPromptVersion)
                .map(this::safe)
                .filter(version -> !version.isBlank())
                .distinct()
                .toList();
        if (promptVersions.isEmpty()) {
            return "unknown";
        }
        if (promptVersions.size() == 1) {
            return promptVersions.get(0);
        }
        return "mixed";
    }

    private String summarizeRuntimeProfile(List<LiveModelEvalReport.Entry> entries) {
        List<String> runtimeProfiles = entries == null ? List.of() : entries.stream()
                .map(LiveModelEvalReport.Entry::getRuntimeProfile)
                .map(this::safe)
                .filter(profile -> !profile.isBlank())
                .distinct()
                .toList();
        if (runtimeProfiles.isEmpty()) {
            return valueOrDefault(System.getenv("AI_EVAL_RUNTIME_PROFILE"), "auto");
        }
        if (runtimeProfiles.size() == 1) {
            return runtimeProfiles.get(0);
        }
        return "mixed";
    }

    private RecoverySummary summarizeRecovery(List<LiveModelEvalReport.Entry> entries,
                                              List<LiveEvalRuntimeFixtureDraft> runtimeDrafts) {
        List<LiveModelEvalReport.Entry> safeEntries = entries == null ? List.of() : entries;
        List<String> passedChecks = new ArrayList<>();
        for (LiveModelEvalReport.Entry entry : safeEntries) {
            List<String> entryChecks = recoveryPassedChecks(entry);
            int requiredCount = recoveryRequiredCheckCount(entry);
            if (entryChecks.size() == requiredCount && requiredCount > 0) {
                passedChecks.addAll(entryChecks);
                return new RecoverySummary(
                        "RECOVERED",
                        requiredCount,
                        entryChecks.size(),
                        passedChecks.stream().distinct().toList(),
                        List.of()
                );
            }
        }

        boolean hasRecoveryContext = safeEntries.stream().anyMatch(this::runtimeFailureEntry)
                || (runtimeDrafts != null && runtimeDrafts.stream()
                .anyMatch(draft -> Boolean.TRUE.equals(draft.getRecoverySmokeRecommended())));
        if (!hasRecoveryContext) {
            return new RecoverySummary("NOT_APPLICABLE", 0, 0, List.of(), List.of());
        }

        List<String> blockedReasons = new ArrayList<>();
        if (runtimeDrafts != null) {
            runtimeDrafts.stream()
                    .filter(draft -> Boolean.TRUE.equals(draft.getRecoverySmokeRecommended()))
                    .map(draft -> "recovery smoke pending: " + safe(draft.getRecoverySmokeCaseId()))
                    .forEach(blockedReasons::add);
        }
        safeEntries.forEach(entry -> blockedReasons.addAll(recoveryBlockedReasons(entry)));
        List<String> distinctReasons = blockedReasons.stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .distinct()
                .limit(12)
                .toList();
        return new RecoverySummary("BLOCKED", 6, 0, List.of(), distinctReasons);
    }

    private boolean runtimeFailureEntry(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return false;
        }
        return Boolean.TRUE.equals(entry.getFallbackUsed())
                || "MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(safe(entry.getStatus()))
                || "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(safe(entry.getStatus()))
                || "EXCEPTION".equalsIgnoreCase(safe(entry.getStatus()))
                || Boolean.TRUE.equals(entry.getLatencyBudgetExceeded());
    }

    private List<String> recoveryPassedChecks(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return List.of();
        }
        List<String> checks = new ArrayList<>();
        if (Boolean.TRUE.equals(entry.getModelCompleted())) {
            checks.add("modelCompleted=true");
        }
        if (!Boolean.TRUE.equals(entry.getFallbackUsed())) {
            checks.add("fallbackUsed=false");
        }
        if (Boolean.TRUE.equals(entry.getModelIssueTagHit()) || Boolean.TRUE.equals(entry.getModelFineTagHit())) {
            checks.add("modelIssueTagHit=true or modelFineTagHit=true");
        }
        if (Boolean.TRUE.equals(entry.getEvidenceValid())) {
            checks.add("evidenceValid=true");
        }
        if (Boolean.TRUE.equals(entry.getSafetyPassed())) {
            checks.add("safetyPassed=true");
        }
        if ("stream".equalsIgnoreCase(safe(entry.getTransportMode())) && intValue(entry.getStreamContentChunkCount()) > 0) {
            checks.add("streamContentChunkCount>0");
        }
        return checks;
    }

    private int recoveryRequiredCheckCount(LiveModelEvalReport.Entry entry) {
        return "stream".equalsIgnoreCase(safe(entry == null ? "" : entry.getTransportMode())) ? 6 : 5;
    }

    private List<String> recoveryBlockedReasons(LiveModelEvalReport.Entry entry) {
        if (entry == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        String caseId = safe(entry.getCaseId()).isBlank() ? "unknown-case" : safe(entry.getCaseId());
        if (Boolean.TRUE.equals(entry.getFallbackUsed())
                || "MODEL_RUNTIME_FALLBACK".equalsIgnoreCase(safe(entry.getStatus()))) {
            reasons.add(caseId + ": runtime fallback");
        }
        if (!Boolean.TRUE.equals(entry.getModelCompleted())) {
            reasons.add(caseId + ": model not completed");
        }
        if (!Boolean.TRUE.equals(entry.getModelIssueTagHit()) && !Boolean.TRUE.equals(entry.getModelFineTagHit())) {
            reasons.add(caseId + ": missing model hit");
        }
        if (!Boolean.TRUE.equals(entry.getEvidenceValid())) {
            reasons.add(caseId + ": missing evidence");
        }
        if (!Boolean.TRUE.equals(entry.getSafetyPassed())) {
            reasons.add(caseId + ": safety failed");
        }
        if ("stream".equalsIgnoreCase(safe(entry.getTransportMode())) && intValue(entry.getStreamContentChunkCount()) <= 0) {
            reasons.add(caseId + ": stream content chunk missing");
        }
        if (!safe(entry.getFailureReason()).isBlank() && !"NONE".equalsIgnoreCase(safe(entry.getFailureReason()))) {
            reasons.add(caseId + ": " + safe(entry.getFailureReason()));
        }
        return reasons;
    }

    private boolean modelCompleted(SubmissionAnalysisResponse.AiInvocation invocation, boolean fallbackUsed) {
        if (invocation == null || fallbackUsed) {
            return false;
        }
        String status = safe(invocation.getStatus());
        return "MODEL_COMPLETED".equalsIgnoreCase(status)
                || "MODEL_PARTIAL_COMPLETED".equalsIgnoreCase(status);
    }

    private String liveModelSummaryLine(LiveModelEvalReport report) {
        return "total=" + count(report.getTotalCount())
                + ", completed=" + count(report.getCompletedCount())
                + ", partial=" + count(report.getPartialCount())
                + ", fallback=" + count(report.getFallbackCount())
                + ", timeout=" + count(report.getTimeoutCount())
                + ", caseDelayMs=" + longCount(report.getCaseDelayMs())
                + ", delayedCases=" + count(report.getDelayedCaseCount())
                + ", finalIssueHits=" + count(report.getIssueTagHitCount())
                + ", finalFineHits=" + count(report.getFineTagHitCount())
                + ", modelIssueHits=" + count(report.getModelIssueTagHitCount())
                + ", modelFineHits=" + count(report.getModelFineTagHitCount())
                + ", fallbackIssueHits=" + count(report.getFallbackIssueTagHitCount())
                + ", fallbackFineHits=" + count(report.getFallbackFineTagHitCount())
                + ", recoveryStatus=" + safe(report.getRecoveryStatus())
                + ", recoveryBlockedReasons=" + count(report.getRecoveryBlockedReasonCount())
                + ", rubricChainEvaluated=" + count(report.getRubricChainEvaluatedCount()) + "/" + count(report.getRubricChainCaseCount())
                + ", rubricChainQuality=" + count(report.getRubricChainPassedCount()) + "/" + count(report.getRubricChainEvaluatedCount())
                + ", rubricChainStages=" + count(report.getRubricChainStagePassedCount()) + "/" + count(report.getRubricChainStageTotalCount())
                + ", rubricChainAvg=" + decimal(report.getRubricChainAverageScore())
                + ", failedRubricStages=" + failedRubricStagesSummary(report.getRubricChainStageFailCounts())
                + ", safetyPassed=" + count(report.getSafetyPassedCount());
    }

    private double rubricChainAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> evaluatedEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getRubricChainEvaluated()))
                .toList();
        if (evaluatedEntries.isEmpty()) {
            return 0.0;
        }
        double total = evaluatedEntries.stream()
                .map(LiveModelEvalReport.Entry::getRubricChainScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / evaluatedEntries.size();
    }

    private Map<String, Integer> rubricChainStageCounts(List<LiveModelEvalReport.Entry> entries, boolean passed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        DiagnosisChainRubricEvaluator.STAGES.forEach(stage -> counts.put(stage, 0));
        if (entries == null) {
            return counts;
        }
        entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getRubricChainEvaluated()))
                .forEach(entry -> {
                    List<String> stages = passed
                            ? entry.getRubricChainPassedStages()
                            : entry.getRubricChainFailedStages();
                    if (stages == null) {
                        return;
                    }
                    stages.stream()
                            .map(stage -> stage.replace("rubricChainStage:", ""))
                            .filter(counts::containsKey)
                            .forEach(stage -> counts.merge(stage, 1, Integer::sum));
                });
        return counts;
    }

    private double complexQualityAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> complexEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getComplexCase()))
                .toList();
        if (complexEntries.isEmpty()) {
            return 0.0;
        }
        double total = complexEntries.stream()
                .map(LiveModelEvalReport.Entry::getComplexQualityScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / complexEntries.size();
    }

    private double intelligenceQualityAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> evaluatedEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getIntelligenceEvaluated()))
                .toList();
        if (evaluatedEntries.isEmpty()) {
            return 0.0;
        }
        double total = evaluatedEntries.stream()
                .map(LiveModelEvalReport.Entry::getIntelligenceQualityScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / evaluatedEntries.size();
    }

    private Map<String, Integer> intelligenceMetricCounts(List<LiveModelEvalReport.Entry> entries, boolean passed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ComplexDiagnosisQualityScorer.INTELLIGENCE_METRICS.forEach(metric -> counts.put(metric, 0));
        if (entries == null) {
            return counts;
        }
        entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getIntelligenceEvaluated()))
                .forEach(entry -> {
                    List<String> metrics = passed
                            ? entry.getIntelligencePassedMetrics()
                            : entry.getIntelligenceFailedMetrics();
                    if (metrics == null) {
                        return;
                    }
                    metrics.stream()
                            .map(metric -> metric.replace("intelligenceMetric:", ""))
                            .filter(counts::containsKey)
                            .forEach(metric -> counts.merge(metric, 1, Integer::sum));
                });
        return counts;
    }

    private double educationAgentQualityAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> evaluatedEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getEducationAgentQualityEvaluated()))
                .toList();
        if (evaluatedEntries.isEmpty()) {
            return 0.0;
        }
        double total = evaluatedEntries.stream()
                .map(LiveModelEvalReport.Entry::getEducationAgentQualityScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / evaluatedEntries.size();
    }

    private Map<String, Integer> educationAgentMetricCounts(List<LiveModelEvalReport.Entry> entries, boolean passed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ComplexDiagnosisQualityScorer.EDUCATION_AGENT_METRICS.forEach(metric -> counts.put(metric, 0));
        if (entries == null) {
            return counts;
        }
        entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getEducationAgentQualityEvaluated()))
                .forEach(entry -> {
                    List<String> metrics = passed
                            ? entry.getEducationAgentPassedMetrics()
                            : entry.getEducationAgentFailedMetrics();
                    if (metrics == null) {
                        return;
                    }
                    metrics.stream()
                            .map(metric -> metric.replace("educationAgentMetric:", ""))
                            .filter(counts::containsKey)
                            .forEach(metric -> counts.merge(metric, 1, Integer::sum));
                });
        return counts;
    }

    private double modelTraceQualityAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> evaluatedEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getModelTraceEvaluated()))
                .toList();
        if (evaluatedEntries.isEmpty()) {
            return 0.0;
        }
        double total = evaluatedEntries.stream()
                .map(LiveModelEvalReport.Entry::getModelTraceQualityScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / evaluatedEntries.size();
    }

    private Map<String, Integer> modelTraceMetricCounts(List<LiveModelEvalReport.Entry> entries, boolean passed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ComplexDiagnosisQualityScorer.MODEL_TRACE_METRICS.forEach(metric -> counts.put(metric, 0));
        if (entries == null) {
            return counts;
        }
        entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getModelTraceEvaluated()))
                .forEach(entry -> {
                    List<String> metrics = passed
                            ? entry.getModelTracePassedMetrics()
                            : entry.getModelTraceFailedMetrics();
                    if (metrics == null) {
                        return;
                    }
                    metrics.stream()
                            .map(metric -> metric.replace("modelTraceMetric:", ""))
                            .filter(counts::containsKey)
                            .forEach(metric -> counts.merge(metric, 1, Integer::sum));
                });
        return counts;
    }

    private double studentFeedbackQualityAverageScore(List<LiveModelEvalReport.Entry> entries) {
        List<LiveModelEvalReport.Entry> evaluatedEntries = entries == null ? List.of() : entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getStudentFeedbackEvaluated()))
                .toList();
        if (evaluatedEntries.isEmpty()) {
            return 0.0;
        }
        double total = evaluatedEntries.stream()
                .map(LiveModelEvalReport.Entry::getStudentFeedbackQualityScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return total / evaluatedEntries.size();
    }

    private Map<String, Integer> studentFeedbackMetricCounts(List<LiveModelEvalReport.Entry> entries, boolean passed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ComplexDiagnosisQualityScorer.STUDENT_FEEDBACK_METRICS.forEach(metric -> counts.put(metric, 0));
        if (entries == null) {
            return counts;
        }
        entries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getStudentFeedbackEvaluated()))
                .forEach(entry -> {
                    List<String> metrics = passed
                            ? entry.getStudentFeedbackPassedMetrics()
                            : entry.getStudentFeedbackFailedMetrics();
                    if (metrics == null) {
                        return;
                    }
                    metrics.stream()
                            .map(metric -> metric.replace("studentFeedbackMetric:", ""))
                            .filter(counts::containsKey)
                            .forEach(metric -> counts.merge(metric, 1, Integer::sum));
                });
        return counts;
    }

    private String failedIntelligenceMetricsSummary(Map<String, Integer> failCounts) {
        if (failCounts == null || failCounts.isEmpty()) {
            return "none";
        }
        String summary = failCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("|"));
        return summary.isBlank() ? "none" : summary;
    }

    private String failedRubricStagesSummary(Map<String, Integer> failCounts) {
        if (failCounts == null || failCounts.isEmpty()) {
            return "none";
        }
        String summary = failCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("|"));
        return summary.isBlank() ? "none" : summary;
    }

    private String decimal(Double value) {
        if (value == null) {
            return "0.000";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private long longCount(Long value) {
        return value == null ? 0L : value;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private Path writeLiveEvalReport(LiveModelEvalReport report) throws IOException {
        Path reportDir = Path.of("target", "ai-eval-reports");
        Files.createDirectories(reportDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportPath = reportDir.resolve("live-model-eval-" + timestamp + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return reportPath;
    }

    private Path writeOfflineRuntimeProfileEvalReport(OfflineRuntimeProfileEvalReport report) throws IOException {
        Path reportDir = Path.of("target", "ai-eval-reports");
        Files.createDirectories(reportDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportPath = reportDir.resolve("offline-runtime-profile-eval-" + timestamp + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return reportPath;
    }

    private Path writeBaselineRegressionReport(String prefix,
                                               LiveEvalBaselineRegressionReport report) throws IOException {
        Path reportDir = Path.of("target", "ai-eval-reports");
        Files.createDirectories(reportDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportPath = reportDir.resolve(prefix + "-" + timestamp + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return reportPath;
    }

    private Path writeLiveModelEvalComparisonReport(LiveModelEvalComparisonReport report) throws IOException {
        Path reportDir = Path.of("target", "ai-eval-reports");
        Files.createDirectories(reportDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportPath = reportDir.resolve("live-model-eval-comparison-" + timestamp + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        return reportPath;
    }

    private boolean intersects(List<String> actual, List<String> expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.stream().anyMatch(expected::contains);
    }

    private String failureReasonFromTrace(String traceSummary) {
        String trace = traceSummary == null ? "" : traceSummary;
        if (trace.contains("rule-fallback")) {
            return "RULE_FALLBACK";
        }
        return "UNKNOWN";
    }

    private String failureReasonFromInvocation(SubmissionAnalysisResponse.AiInvocation invocation,
                                               String traceSummary,
                                               String uncertainty) {
        String status = invocation == null ? "" : safe(invocation.getStatus());
        String detail = structuredFailureReason(invocation);
        if (detail.isBlank()) {
            detail = extractRuntimeFailureReason(uncertainty);
        }
        if (!status.isBlank()) {
            return detail.isBlank() ? status : status + ":" + detail;
        }
        return failureReasonFromTrace(traceSummary);
    }

    private String failureStageFromInvocation(SubmissionAnalysisResponse.AiInvocation invocation,
                                              String uncertainty) {
        String structured = invocation == null ? "" : safe(invocation.getFailureStage());
        if (!structured.isBlank()) {
            return structured;
        }
        String text = safe(uncertainty);
        for (String stage : List.of("DIAGNOSIS_AND_ADVICE", "DIAGNOSIS_AND_ADVICE", "DIAGNOSIS_AND_ADVICE", "SUBMISSION_ANALYSIS")) {
            if (text.contains(stage)) {
                return stage;
            }
        }
        return "";
    }

    private String structuredFailureReason(SubmissionAnalysisResponse.AiInvocation invocation) {
        if (invocation == null) {
            return "";
        }
        List<String> values = List.of(
                        safe(invocation.getFailureStage()),
                        safe(invocation.getFailureReason()))
                .stream()
                .filter(value -> !value.isBlank())
                .toList();
        return String.join(":", values);
    }

    private String extractRuntimeFailureReason(String uncertainty) {
        String text = safe(uncertainty);
        String marker = "失败原因：";
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        String tail = text.substring(start + marker.length()).trim();
        int end = tail.indexOf('，');
        if (end < 0) {
            end = tail.indexOf('。');
        }
        return end < 0 ? tail : tail.substring(0, end);
    }

    private String classifyException(Exception exception) {
        String text = (exception.getClass().getName() + " " + exception.getMessage()).toLowerCase();
        if (text.contains("timeout")) {
            return "TIMEOUT";
        }
        if (text.contains("insufficient_quota") || text.contains("exceeded your current quota")) {
            return "INSUFFICIENT_QUOTA";
        }
        if (text.contains("429") || text.contains("rate")) {
            return "RATE_LIMITED";
        }
        if (text.contains("has no provider supported") || text.contains("model unsupported")) {
            return "MODEL_UNSUPPORTED";
        }
        return "EXCEPTION";
    }

    private record RecoverySummary(String status,
                                   int checkCount,
                                   int passedCheckCount,
                                   List<String> passedChecks,
                                   List<String> blockedReasons) {
    }

    private record EvalCase(String name,
                            Problem problem,
                            Submission submission,
                            List<SubmissionCaseResult> caseResults,
                            SubmissionAnalysisResponse baseline,
                            List<String> expectedIssueTags,
                            List<String> expectedFineTags,
                            ComplexStudentSubmissionEvalFixtureLoader.Fixture complexFixture) {
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstOrBlank(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private static class PassThroughAiReportService extends AiReportService {
        PassThroughAiReportService() {
            super(new ObjectMapper(), new AiCodeAssistSupport());
        }

        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
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
}
