package com.onlinejudge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.CoachAgentService;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.application.AiCodeAssistSupport;
import com.onlinejudge.submission.application.AiReportService;
import com.onlinejudge.submission.application.DiagnosisEvidencePackageBuilder;
import com.onlinejudge.submission.application.DiagnosticAgentService;
import com.onlinejudge.submission.application.ExternalModelAgentRuntime;
import com.onlinejudge.submission.application.ModelDiagnosisBriefBuilder;
import com.onlinejudge.submission.application.ModelOutputValidator;
import com.onlinejudge.submission.application.PromptTemplateRegistry;
import com.onlinejudge.submission.application.StandardLibraryPackBuilder;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantLiveEvalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();

    @Test
    void assistantEvalFixturesExposeTeacherGradeExpectations() throws IOException {
        List<AssistantEvalFixtureLoader.Fixture> fixtures =
                new AssistantEvalFixtureLoader(objectMapper).loadDefault();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(6);
        assertThat(fixtures)
                .allSatisfy(fixture -> {
                    assertThat(fixture.caseId()).isNotBlank();
                    assertThat(fixture.assistantType()).isIn("SUBMISSION_DIAGNOSIS", "COACH_QUESTION", "GROWTH_REPORT");
                    assertThat(fixture.teacherExpectation()).isNotBlank();
                    assertThat(fixture.qualityNotes()).isNotBlank();
                    assertThat(fixture.rubric()).isNotNull();
                    assertNoMojibake(fixture.caseId(), objectMapper.writeValueAsString(fixture));
                    assertThat(fixture.rubric().expectedSignals()).as(fixture.caseId()).isNotEmpty();
                    assertThat(fixture.rubric().forbiddenPhrases()).as(fixture.caseId()).isNotEmpty();
                    if (fixture.isDiagnosis()) {
                        assertThat(fixture.diagnosis()).isNotNull();
                        assertThat(fixture.diagnosis().toSubmission().getSourceCode()).isNotBlank();
                        assertThat(fixture.rubric().expectedIssueTags()).isNotEmpty();
                        assertThat(fixture.rubric().expectedFineGrainedTags()).isNotEmpty();
                        assertThat(fixture.rubric().requiredEvidenceRefs()).isNotEmpty();
                    }
                    if (fixture.isCoach()) {
                        assertThat(fixture.coach()).isNotNull();
                        assertThat(fixture.coach().evidenceRefs()).isNotEmpty();
                        assertThat(fixture.rubric().requiredEvidenceRefs()).isNotEmpty();
                    }
                    if (fixture.isGrowthReport()) {
                        assertThat(fixture.growthReport()).isNotNull();
                        assertThat(fixture.growthReport().timeline()).hasSizeGreaterThanOrEqualTo(2);
                    }
                });
        long complexDiagnosisCount = fixtures.stream()
                .filter(AssistantEvalFixtureLoader.Fixture::isDiagnosis)
                .filter(fixture -> lineCount(fixture.diagnosis().toSubmission().getSourceCode()) >= 18)
                .count();
        assertThat(complexDiagnosisCount)
                .as("live eval must include enough medium-length diagnosis cases, not only toy snippets")
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    void studentHintNegativeFixturesDoNotContainCorruptedForbiddenPhrases() throws IOException {
        Path fixturePath = Path.of(
                "src",
                "test",
                "resources",
                "diagnosis-eval-fixtures",
                "student-hint-negative-cases.json"
        );
        String fixtureJson = Files.readString(fixturePath);

        assertNoMojibake("student-hint-negative-cases", fixtureJson);
        assertThat(fixtureJson).contains("隐藏测试点");
    }

    @Test
    void liveEvalUsesProviderFriendlyDefaultDelay() {
        assertThat(defaultLiveEvalCaseDelayMs("Qwen/Qwen3.5-397B-A17B")).isEqualTo(60_000L);
        assertThat(defaultLiveEvalCaseDelayMs("deepseek-ai/DeepSeek-V4-Flash")).isEqualTo(15_000L);
        assertThat(defaultLiveEvalCaseDelayMs("local-test-model")).isZero();
    }

    @Test
    void liveExternalAssistantsProduceComparableReportWhenEnabled() throws IOException {
        String apiKey = System.getenv("AI_EVAL_API_KEY");
        Assumptions.assumeTrue(Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_LIVE_ENABLED"), "false")),
                "Set AI_EVAL_LIVE_ENABLED=true to run live external assistant eval.");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Set AI_EVAL_API_KEY to run live external assistant eval.");

        List<AssistantEvalFixtureLoader.Fixture> fixtures =
                loadFixtures().stream()
                        .filter(fixture -> shouldIncludeAssistantType(fixture, System.getenv("AI_EVAL_ASSISTANT_TYPES")))
                        .filter(fixture -> shouldIncludeCaseId(fixture, System.getenv("AI_EVAL_CASE_IDS")))
                        .limit(Math.max(1, (int) longValueOrDefault(System.getenv("AI_EVAL_ASSISTANT_SMOKE_LIMIT"), 3L)))
                        .toList();
        assertThat(fixtures)
                .as("live eval fixture filter should match at least one case")
                .isNotEmpty();

        AssistantLiveEvalReport report = runLiveEval(fixtures, apiKey);
        Path reportPath = writeReport(report);

        System.out.println("Assistant live eval report saved to: " + reportPath);
        System.out.println("Assistant live eval summary: total=" + report.getTotalCount()
                + ", completed=" + report.getCompletedCount()
                + ", runtimeFailures=" + report.getRuntimeFailureCount()
                + ", qualityMisses=" + report.getQualityMissCount()
                + ", safetyFailures=" + report.getSafetyFailureCount()
                + ", signalHits=" + report.getExpectedSignalHitCount()
                + ", studentVisibleQualityPasses=" + report.getStudentVisibleQualityPassCount()
                + ", studentVisibleQualityRisks=" + report.getStudentVisibleQualityRiskCount());

        assertThat(report.getEntries()).hasSize(fixtures.size());
        assertThat(reportPath).exists();
        assertLiveEvalQualityGateIfEnabled(report);
        assertBaselineRegressionGateIfEnabled(report, reportPath);
    }

    private List<AssistantEvalFixtureLoader.Fixture> loadFixtures() throws IOException {
        AssistantEvalFixtureLoader loader = new AssistantEvalFixtureLoader(objectMapper);
        String fixturePath = System.getenv("AI_EVAL_FIXTURE_PATH");
        if (fixturePath == null || fixturePath.isBlank()) {
            return loader.loadDefault();
        }
        return loader.load(Path.of(fixturePath));
    }

    private AssistantLiveEvalReport runLiveEval(List<AssistantEvalFixtureLoader.Fixture> fixtures,
                                                String apiKey) {
        String model = valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Pro");
        long caseDelayMs = liveEvalCaseDelayMs(model);
        AiReportService aiReportService = newLiveAiReportService(apiKey);
        DiagnosticAgentService diagnosticAgentService = newDiagnosticAgentService(aiReportService);
        CoachAgentService coachAgentService = newLiveCoachAgentService(apiKey);
        List<AssistantLiveEvalReport.Entry> entries = new ArrayList<>();

        for (int index = 0; index < fixtures.size(); index++) {
            AssistantEvalFixtureLoader.Fixture fixture = fixtures.get(index);
            waitBetweenCases(index, caseDelayMs);
            long startedAt = System.nanoTime();
            try {
                AssistantLiveEvalReport.Entry entry = switch (fixture.assistantType()) {
                    case "SUBMISSION_DIAGNOSIS" -> evaluateDiagnosis(fixture, diagnosticAgentService, model, startedAt);
                    case "COACH_QUESTION" -> evaluateCoach(fixture, coachAgentService, model, startedAt);
                    case "GROWTH_REPORT" -> evaluateGrowthReport(fixture, aiReportService, model, startedAt);
                    default -> throw new IllegalArgumentException("Unknown assistant type: " + fixture.assistantType());
                };
                entries.add(entry);
            } catch (Exception exception) {
                entries.add(exceptionEntry(fixture, model, startedAt, exception));
            }
        }
        return summarize(model, entries);
    }

    private void waitBetweenCases(int index, long caseDelayMs) {
        if (index <= 0 || caseDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(caseDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Assistant live eval interrupted while waiting between cases.", exception);
        }
    }

    long liveEvalCaseDelayMs(String model) {
        String configured = System.getenv("AI_EVAL_CASE_DELAY_MS");
        if (configured != null && !configured.isBlank()) {
            return Math.max(0, longValueOrDefault(configured, 0L));
        }
        return defaultLiveEvalCaseDelayMs(model);
    }

    private long defaultLiveEvalCaseDelayMs(String model) {
        String normalized = safe(model).toLowerCase(Locale.ROOT);
        if (normalized.contains("qwen")) {
            return 60_000L;
        }
        if (normalized.contains("modelscope") || normalized.contains("/") || normalized.contains("deepseek")) {
            return 15_000L;
        }
        return 0L;
    }

    private AssistantLiveEvalReport.Entry evaluateDiagnosis(AssistantEvalFixtureLoader.Fixture fixture,
                                                            DiagnosticAgentService service,
                                                            String model,
                                                            long startedAt) {
        DiagnosticAgentService.AgentResult result = service.diagnose(
                fixture.diagnosis().toProblem(),
                fixture.diagnosis().toSubmission(),
                fixture.diagnosis().toCaseResults(),
                fixture.diagnosis().toBaseline(),
                Assignment.HintPolicy.L2
        );
        SubmissionAnalysisResponse analysis = result.analysis();
        SubmissionAnalysisResponse.AiInvocation invocation = analysis.getAiInvocation();
        boolean fallbackUsed = invocation == null || invocation.isFallbackUsed();
        boolean partialCompleted = invocation != null
                && "MODEL_PARTIAL_COMPLETED".equals(invocation.getStatus());
        boolean signalHit = intersects(analysis.getIssueTags(), fixture.rubric().expectedIssueTags())
                || intersects(analysis.getFineGrainedTags(), fixture.rubric().expectedFineGrainedTags())
                || containsAny(combinedAnalysisText(analysis), fixture.rubric().expectedSignals());
        boolean evidenceValid = evidenceValid(analysis.getEvidenceRefs(), fixture.rubric().requiredEvidenceRefs());
        boolean safetyPassed = !"HIGH".equalsIgnoreCase(analysis.getAnswerLeakRisk())
                && avoidsForbidden(combinedAnalysisText(analysis), fixture.rubric().forbiddenPhrases());
        String combinedText = combinedAnalysisText(analysis);
        VisibleStudentFeedback visibleFeedback = visibleStudentFeedback(analysis);
        List<String> visibleQualityFlags = AssistantLiveEvalQualityGate.studentVisibleQualityFlags(
                visibleFeedback.basicText(),
                visibleFeedback.improvementText(),
                visibleFeedback.nextActionText(),
                visibleInputSnapshots(fixture)
        );
        VisibleStudentFeedback studentReportFeedback = studentReportFeedback(visibleFeedback);
        List<String> studentReportQualityFlags = studentReportFeedback.allText().isBlank()
                ? studentReportFeedback.enabled() ? List.of("STUDENT_REPORT_MISSING") : List.of()
                : AssistantLiveEvalQualityGate.studentVisibleQualityFlags(
                        studentReportFeedback.basicText(),
                        studentReportFeedback.improvementText(),
                        studentReportFeedback.nextActionText(),
                        visibleInputSnapshots(fixture)
                );
        List<String> allVisibleQualityFlags = List.of(visibleQualityFlags, studentReportQualityFlags).stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        boolean studentVisibleQualityPassed = allVisibleQualityFlags.isEmpty();
        boolean teachingActionValid = expectedTeachingActionValid(analysis, fixture);
        String failureReason = resolveFailureReason(fallbackUsed, partialCompleted, invocation,
                analysis.getUncertainty(), signalHit, safetyPassed, teachingActionValid,
                studentVisibleQualityPassed, allVisibleQualityFlags);
        return baseEntry(fixture, model, startedAt)
                .promptVersion(invocation == null ? "unknown" : invocation.getPromptVersion())
                .status(invocation == null ? "UNKNOWN" : invocation.getStatus())
                .fallbackUsed(fallbackUsed)
                .completedOutput(!fallbackUsed)
                .expectedSignalHit(signalHit)
                .evidenceValid(evidenceValid)
                .safetyPassed(safetyPassed)
                .teachingActionValid(teachingActionValid)
                .actualIssueTags(analysis.getIssueTags())
                .actualFineGrainedTags(analysis.getFineGrainedTags())
                .actualEvidenceRefs(analysis.getEvidenceRefs())
                .teachingAction(analysis.getStudentHintPlan() == null ? "" : analysis.getStudentHintPlan().getTeachingAction())
                .safetyTrigger(resolveSafetyTrigger(analysis.getAnswerLeakRisk(), combinedText, fixture.rubric().forbiddenPhrases()))
                .failureStage(resolveFailureStage(fallbackUsed, partialCompleted, invocation, analysis.getUncertainty()))
                .failureReason(failureReason)
                .outputSummary(truncate(safe(analysis.getHeadline()) + " | " + safe(analysis.getSummary()), 220))
                .outputDetail(truncate(combinedText, 900))
                .studentVisibleBasicText(visibleFeedback.basicText())
                .studentVisibleImprovementText(visibleFeedback.improvementText())
                .studentVisibleNextActionText(visibleFeedback.nextActionText())
                .studentVisibleText(visibleFeedback.allText())
                .studentReportBasicText(studentReportFeedback.basicText())
                .studentReportImprovementText(studentReportFeedback.improvementText())
                .studentReportNextActionText(studentReportFeedback.nextActionText())
                .studentReportText(studentReportFeedback.allText())
                .studentVisibleQualityPassed(studentVisibleQualityPassed)
                .studentVisibleQualityFlags(allVisibleQualityFlags)
                .aiBetterThanTeacher(signalHit && evidenceValid ? "AI 输出包含结构化标签和证据，可用于后续系统统计。" : "本条未观察到明显优于老师期望的部分。")
                .teacherBetterThanAi(signalHit ? "老师期望更明确地限定了不要直接给改法。" : "老师期望更准确地定位了核心错因。")
                .iterationSuggestion(iterationSuggestion(fixture.assistantType(), fallbackUsed, signalHit, safetyPassed, ""))
                .build();
    }

    private AssistantLiveEvalReport.Entry evaluateCoach(AssistantEvalFixtureLoader.Fixture fixture,
                                                        CoachAgentService service,
                                                        String model,
                                                        long startedAt) {
        Assignment.HintPolicy policy = parseHintPolicy(fixture.coach().hintPolicy());
        CoachAgentService.CoachDraft draft;
        if ("FOLLOW_UP".equals(fixture.coach().turnType())) {
            draft = service.generateFollowUpQuestion(
                    fixture.coach().toSubmission(),
                    fixture.coach().toAnalysis(),
                    fixture.coach().primaryTag(),
                    policy,
                    fixture.coach().contextSummary(),
                    fixture.coach().evidenceRefs(),
                    fixture.coach().studentAnswer(),
                    1
            );
        } else {
            draft = service.generateInitialQuestion(
                    fixture.coach().toSubmission(),
                    fixture.coach().toAnalysis(),
                    fixture.coach().primaryTag(),
                    policy,
                    fixture.coach().contextSummary(),
                    fixture.coach().evidenceRefs()
            );
        }
        boolean fallbackUsed = !"MODEL".equals(draft.getSource());
        String combined = safe(draft.getQuestion()) + "\n" + safe(draft.getRationale());
        boolean signalHit = containsAny(combined, fixture.rubric().expectedSignals());
        boolean evidenceValid = containsAny(draft.getEvidenceRefs(), fixture.rubric().requiredEvidenceRefs());
        boolean safetyPassed = !"HIGH".equalsIgnoreCase(draft.getAnswerLeakRisk())
                && avoidsForbidden(combined, fixture.rubric().forbiddenPhrases());
        String coachFailureReason = resolveCoachFailureReason(fallbackUsed, draft.getFailureReason(), signalHit, safetyPassed);
        boolean modelAttemptSafetyPassed = safetyPassed && !coachFailureReason.contains("SAFETY_REJECTED");
        return baseEntry(fixture, model, startedAt)
                .promptVersion("coach-question-v1")
                .status(fallbackUsed ? "MODEL_RUNTIME_FALLBACK" : "MODEL_COMPLETED")
                .fallbackUsed(fallbackUsed)
                .completedOutput(!fallbackUsed)
                .expectedSignalHit(signalHit)
                .evidenceValid(evidenceValid)
                .safetyPassed(modelAttemptSafetyPassed)
                .teachingActionValid(combined.contains("?") || combined.contains("？"))
                .actualEvidenceRefs(draft.getEvidenceRefs())
                .safetyTrigger(resolveSafetyTrigger(draft.getAnswerLeakRisk(), combined, fixture.rubric().forbiddenPhrases()))
                .failureStage(fallbackUsed ? "COACH_QUESTION" : "NONE")
                .failureReason(coachFailureReason)
                .outputSummary(truncate(safe(draft.getQuestion()), 220))
                .outputDetail(truncate(combined, 900))
                .aiBetterThanTeacher(signalHit && safetyPassed ? "AI 追问保留了苏格拉底式引导，没有直接给答案。" : "本条未观察到明显优于老师期望的部分。")
                .teacherBetterThanAi(signalHit ? "老师期望更清楚地限定了追问边界。" : "老师追问更能指向当前学生回答里的缺口。")
                .iterationSuggestion(iterationSuggestion(fixture.assistantType(), fallbackUsed, signalHit, modelAttemptSafetyPassed, coachFailureReason))
                .build();
    }

    private AssistantLiveEvalReport.Entry evaluateGrowthReport(AssistantEvalFixtureLoader.Fixture fixture,
                                                               AiReportService service,
                                                               String model,
                                                               long startedAt) {
        String markdown = service.enhanceGrowthReportMarkdown(
                fixture.growthReport().toProblem(),
                fixture.growthReport().timeline()
        );
        String visibleMarkdown = stripGrowthReportFailureMarker(markdown);
        boolean fallbackUsed = visibleMarkdown.isBlank()
                || visibleMarkdown.contains("暂不可用")
                || safe(markdown).contains("<!-- AI_FAILURE:");
        boolean signalHit = containsAny(visibleMarkdown, fixture.rubric().expectedSignals());
        boolean evidenceValid = fixture.growthReport().timeline().stream()
                .map(item -> String.valueOf(item.getOrDefault("submissionId", "")))
                .anyMatch(id -> visibleMarkdown != null && visibleMarkdown.contains(id));
        boolean safetyPassed = avoidsForbidden(visibleMarkdown, fixture.rubric().forbiddenPhrases());
        return baseEntry(fixture, model, startedAt)
                .promptVersion("growth-report-markdown-v1")
                .status(fallbackUsed ? "MODEL_RUNTIME_FALLBACK" : "MODEL_COMPLETED")
                .fallbackUsed(fallbackUsed)
                .completedOutput(!fallbackUsed)
                .expectedSignalHit(signalHit)
                .evidenceValid(evidenceValid)
                .safetyPassed(safetyPassed)
                .teachingActionValid(signalHit && containsAny(markdown, List.of("下一步", "复盘", "建议", "行动", "练习")))
                .safetyTrigger(resolveSafetyTrigger("", visibleMarkdown, fixture.rubric().forbiddenPhrases()))
                .failureStage(fallbackUsed ? "GROWTH_REPORT" : "NONE")
                .failureReason(resolveFailureReason(fallbackUsed, false, null,
                        extractGrowthReportFailureReason(markdown),
                        signalHit, safetyPassed, Boolean.TRUE.equals(signalHit)))
                .outputSummary(truncate(visibleMarkdown, 260))
                .outputDetail(truncate(visibleMarkdown, 900))
                .aiBetterThanTeacher(signalHit && evidenceValid ? "AI 能把多次提交串成学习轨迹，适合教师快速浏览。" : "本条未观察到明显优于老师期望的部分。")
                .teacherBetterThanAi(signalHit ? "老师期望对教师介入信号更明确。" : "老师期望更能抓住连续提交背后的学习状态。")
                .iterationSuggestion(iterationSuggestion(fixture.assistantType(), fallbackUsed, signalHit, safetyPassed, ""))
                .build();
    }

    private AssistantLiveEvalReport.Entry.EntryBuilder baseEntry(
            AssistantEvalFixtureLoader.Fixture fixture,
            String model,
            long startedAt) {
        return AssistantLiveEvalReport.Entry.builder()
                .caseId(fixture.caseId())
                .assistantType(fixture.assistantType())
                .model(model)
                .latencyMs((System.nanoTime() - startedAt) / 1_000_000)
                .teacherExpectation(fixture.teacherExpectation());
    }

    private AssistantLiveEvalReport.Entry exceptionEntry(AssistantEvalFixtureLoader.Fixture fixture,
                                                         String model,
                                                         long startedAt,
                                                         Exception exception) {
        return baseEntry(fixture, model, startedAt)
                .promptVersion("unknown")
                .status("EXCEPTION")
                .fallbackUsed(true)
                .completedOutput(false)
                .expectedSignalHit(false)
                .evidenceValid(false)
                .safetyPassed(false)
                .teachingActionValid(false)
                .failureStage(fixture.assistantType())
                .failureReason(classifyException(exception))
                .outputSummary(truncate(exception.getClass().getSimpleName() + ": " + exception.getMessage(), 220))
                .aiBetterThanTeacher("外部模型调用未完成，不能判断。")
                .teacherBetterThanAi("老师期望仍可作为离线质量基线。")
                .iterationSuggestion("先处理外部模型调用稳定性或失败归因，再评估教学质量。")
                .build();
    }

    private DiagnosticAgentService newDiagnosticAgentService(AiReportService aiReportService) {
        return new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                aiReportService,
                new HintSafetyService(null, objectMapper, taxonomy),
                taxonomy
        );
    }

    private AiReportService newLiveAiReportService(String apiKey) {
        ExternalModelAgentRuntime runtime = new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(taxonomy),
                new PromptTemplateRegistry(),
                new ModelOutputValidator()
        );
        AiReportService service = new AiReportService(objectMapper, new AiCodeAssistSupport(), runtime);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "baseUrl", valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"));
        ReflectionTestUtils.setField(service, "model", valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Pro"));
        ReflectionTestUtils.setField(service, "timeoutSeconds", longValueOrDefault(System.getenv("AI_EVAL_TIMEOUT_SECONDS"), 35L));
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_EXTERNAL_RUNTIME_ENABLED"), "true")));        ReflectionTestUtils.setField(service, "maxOutputTokens", (int) longValueOrDefault(System.getenv("AI_EVAL_MAX_OUTPUT_TOKENS"), 900L));
        ReflectionTestUtils.setField(service, "streamEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_ENABLED"), "true")));
        ReflectionTestUtils.setField(service, "streamFallbackEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_FALLBACK_ENABLED"), "true")));
        return service;
    }

    private CoachAgentService newLiveCoachAgentService(String apiKey) {
        CoachAgentService service = new CoachAgentService(objectMapper, taxonomy);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "baseUrl", valueOrDefault(System.getenv("AI_EVAL_BASE_URL"), "https://api-inference.modelscope.cn/v1"));
        ReflectionTestUtils.setField(service, "model", valueOrDefault(System.getenv("AI_EVAL_MODEL"), "deepseek-ai/DeepSeek-V4-Pro"));
        ReflectionTestUtils.setField(service, "timeoutSeconds", longValueOrDefault(System.getenv("AI_EVAL_TIMEOUT_SECONDS"), 35L));
        ReflectionTestUtils.setField(service, "streamEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_ENABLED"), "true")));
        ReflectionTestUtils.setField(service, "streamFallbackEnabled",
                Boolean.parseBoolean(valueOrDefault(System.getenv("AI_STREAM_FALLBACK_ENABLED"), "true")));
        return service;
    }

    private AssistantLiveEvalReport summarize(String model, List<AssistantLiveEvalReport.Entry> entries) {
        List<LiveEvalRuntimeFixtureDraft> runtimeDrafts =
                new LiveEvalRuntimeFixtureDraftFactory().fromAssistantEntries(entries);
        List<LiveEvalQualityBaselineDraft> qualityBaselines =
                new LiveEvalQualityBaselineDraftFactory().fromAssistantEntries(entries);
        return AssistantLiveEvalReport.builder()
                .model(model)
                .totalCount(entries.size())
                .completedCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getCompletedOutput())).count())
                .runtimeFailureCount((int) entries.stream().filter(entry -> !Boolean.TRUE.equals(entry.getCompletedOutput())).count())
                .qualityMissCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getCompletedOutput()))
                        .filter(entry -> !Boolean.TRUE.equals(entry.getExpectedSignalHit())
                                || !Boolean.TRUE.equals(entry.getEvidenceValid())
                                || ("SUBMISSION_DIAGNOSIS".equals(entry.getAssistantType())
                                && !Boolean.TRUE.equals(entry.getStudentVisibleQualityPassed())))
                        .count())
                .safetyFailureCount((int) entries.stream().filter(entry -> !Boolean.TRUE.equals(entry.getSafetyPassed())).count())
                .expectedSignalHitCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getExpectedSignalHit())).count())
                .evidenceValidCount((int) entries.stream().filter(entry -> Boolean.TRUE.equals(entry.getEvidenceValid())).count())
                .studentVisibleQualityPassCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getCompletedOutput()))
                        .filter(entry -> Boolean.TRUE.equals(entry.getStudentVisibleQualityPassed()))
                        .count())
                .studentVisibleQualityRiskCount((int) entries.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.getCompletedOutput()))
                        .filter(entry -> entry.getStudentVisibleQualityFlags() != null && !entry.getStudentVisibleQualityFlags().isEmpty())
                        .count())
                .runtimeFixtureDraftCount(runtimeDrafts.size())
                .qualityBaselineDraftCount(qualityBaselines.size())
                .entries(entries)
                .runtimeFixtureDrafts(runtimeDrafts)
                .qualityBaselineDrafts(qualityBaselines)
                .build();
    }

    private void assertLiveEvalQualityGateIfEnabled(AssistantLiveEvalReport report) {
        if (!Boolean.parseBoolean(valueOrDefault(
                System.getenv("AI_EVAL_ENFORCE_THRESHOLDS"),
                valueOrDefault(System.getenv("AI_LIVE_EVAL_STRICT"), "false")))) {
            return;
        }
        AssistantLiveEvalQualityGate.Thresholds thresholds = new AssistantLiveEvalQualityGate.Thresholds(
                doubleValueOrDefault(System.getenv("AI_EVAL_MIN_SIGNAL_HIT_RATE"), 0.60),
                doubleValueOrDefault(System.getenv("AI_EVAL_MIN_EVIDENCE_VALID_RATE"), 0.80),
                doubleValueOrDefault(System.getenv("AI_EVAL_MIN_SAFETY_PASS_RATE"), 1.00),
                doubleValueOrDefault(System.getenv("AI_EVAL_MAX_FALLBACK_RATE"), 0.35),
                doubleValueOrDefault(System.getenv("AI_EVAL_MIN_TEACHING_ACTION_VALID_RATE"), 0.80),
                doubleValueOrDefault(System.getenv("AI_EVAL_MIN_STUDENT_VISIBLE_QUALITY_PASS_RATE"), 0.70),
                longValueOrDefault(System.getenv("AI_EVAL_MAX_AVERAGE_COMPLETED_LATENCY_MS"), 35_000L)
        );
        List<String> violations = AssistantLiveEvalQualityGate.evaluate(report, thresholds);
        assertThat(violations)
                .as("assistant live eval quality gate violations")
                .isEmpty();
    }

    private void assertBaselineRegressionGateIfEnabled(AssistantLiveEvalReport report, Path reportPath) throws IOException {
        String baselineReport = System.getenv("AI_EVAL_BASELINE_REPORT");
        if (baselineReport == null || baselineReport.isBlank()) {
            return;
        }
        Path baselinePath = Path.of(baselineReport);
        AssistantLiveEvalReport baseline = objectMapper.readValue(baselinePath.toFile(), AssistantLiveEvalReport.class);
        List<String> violations = LiveEvalBaselineRegressionGate.evaluate(report, baseline.getQualityBaselineDrafts());
        LiveEvalBaselineRegressionReport regressionReport = new LiveEvalBaselineRegressionReportFactory()
                .fromAssistant(report, baseline.getQualityBaselineDrafts(),
                        baselinePath.toString(), reportPath.toString(), violations);
        Path regressionReportPath = writeBaselineRegressionReport(
                "assistant-live-eval-baseline-regression",
                regressionReport
        );
        System.out.println("Assistant live eval baseline regression report saved to: " + regressionReportPath);
        System.out.println("Assistant live eval baseline regression summary: " + regressionReport.consoleSummary());
        assertThat(violations)
                .as("assistant live eval baseline regression violations against " + baselinePath)
                .isEmpty();
    }

    private Path writeReport(AssistantLiveEvalReport report) throws IOException {
        Path reportDir = Path.of("target", "ai-eval-reports");
        Files.createDirectories(reportDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path reportPath = reportDir.resolve("assistant-live-eval-" + timestamp + ".json");
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

    private boolean intersects(List<String> actual, List<String> expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.stream().anyMatch(expected::contains);
    }

    private boolean containsAny(List<String> actual, List<String> expected) {
        if (actual == null || expected == null || expected.isEmpty()) {
            return false;
        }
        return actual.stream().anyMatch(expected::contains);
    }

    private boolean evidenceValid(List<String> actualRefs, List<String> requiredRefs) {
        if (requiredRefs != null && !requiredRefs.isEmpty()) {
            return containsAny(actualRefs, requiredRefs);
        }
        return actualRefs != null && !actualRefs.isEmpty();
    }

    private boolean expectedTeachingActionValid(SubmissionAnalysisResponse analysis,
                                                AssistantEvalFixtureLoader.Fixture fixture) {
        if (analysis == null || analysis.getStudentHintPlan() == null) {
            return false;
        }
        String actualAction = safe(analysis.getStudentHintPlan().getTeachingAction()).toUpperCase(Locale.ROOT);
        List<String> expectedActions = expectedTeachingActions(fixture);
        return expectedActions.isEmpty() || expectedActions.contains(actualAction);
    }

    private List<String> expectedTeachingActions(AssistantEvalFixtureLoader.Fixture fixture) {
        if (fixture == null || fixture.rubric() == null) {
            return List.of();
        }
        List<String> expectedTags = new ArrayList<>();
        if (fixture.rubric().expectedFineGrainedTags() != null) {
            expectedTags.addAll(fixture.rubric().expectedFineGrainedTags());
        }
        if (fixture.rubric().expectedIssueTags() != null) {
            expectedTags.addAll(fixture.rubric().expectedIssueTags());
        }
        return expectedTags.stream()
                .map(this::expectedTeachingAction)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String expectedTeachingAction(String tag) {
        return switch (tag == null ? "" : tag.toUpperCase(Locale.ROOT)) {
            case "OFF_BY_ONE", "LOOP_BOUNDARY", "BINARY_SEARCH_BOUNDARY",
                    "ARRAY_INDEX_OUT_OF_BOUNDS", "INTEGER_DIVISION_PRECISION" -> "TRACE_VARIABLES";
            case "INPUT_PARSING", "IO_FORMAT" -> "COMPARE_INPUT_SPEC";
            case "OUTPUT_FORMAT_DETAIL" -> "COMPARE_OUTPUT";
            case "TIME_COMPLEXITY", "BRUTE_FORCE_LIMIT", "OVER_SIMULATION", "MAX_BOUNDARY" -> "COUNT_COMPLEXITY";
            case "EMPTY_INPUT", "BOUNDARY_CONDITION" -> "ASK_MIN_CASE";
            case "STATE_RESET", "INITIAL_STATE", "VARIABLE_INITIALIZATION", "UNINITIALIZED_VARIABLE" -> "TRACE_STATE";
            case "DP_STATE_DESIGN", "STATE_TRANSITION", "IN_PLACE_STATE_PROGRESS" -> "DEFINE_STATE";
            case "RECURSION_BASE_CASE" -> "DRAW_RECURSION_TREE";
            case "GREEDY_ASSUMPTION", "ALGORITHM_STRATEGY", "SORT_KEY" -> "CHECK_INVARIANT";
            case "RUNTIME_STABILITY" -> "CHECK_RUNTIME_GUARDS";
            case "SAMPLE_OVERFIT", "SAMPLE_ONLY" -> "BUILD_COUNTEREXAMPLE";
            case "PARTIAL_FIX_REGRESSION" -> "COMPARE_SUBMISSIONS";
            default -> "";
        };
    }

    private boolean containsAny(String actual, List<String> expected) {
        if (actual == null || expected == null || expected.isEmpty()) {
            return false;
        }
        String normalized = actual.toLowerCase(Locale.ROOT);
        return expected.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private long lineCount(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return 0;
        }
        return sourceCode.lines().count();
    }

    private boolean shouldIncludeAssistantType(AssistantEvalFixtureLoader.Fixture fixture, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        List<String> allowed = List.of(filter.split(",")).stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        return allowed.isEmpty() || allowed.contains(fixture.assistantType());
    }

    private boolean shouldIncludeCaseId(AssistantEvalFixtureLoader.Fixture fixture, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        List<String> allowed = List.of(filter.split(",")).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        return allowed.isEmpty() || allowed.contains(fixture.caseId().toLowerCase(Locale.ROOT));
    }

    private boolean avoidsForbidden(String actual, List<String> forbidden) {
        if (forbidden == null || forbidden.isEmpty()) {
            return true;
        }
        String normalized = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        return forbidden.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .noneMatch(normalized::contains);
    }

    private String resolveSafetyTrigger(String answerLeakRisk, String actual, List<String> forbidden) {
        if ("HIGH".equalsIgnoreCase(answerLeakRisk)) {
            return "answerLeakRisk=HIGH";
        }
        if (forbidden == null || forbidden.isEmpty()) {
            return "";
        }
        String normalized = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        return forbidden.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> normalized.contains(value.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse("");
    }

    private void assertNoMojibake(String caseId, String text) {
        List<String> mojibakeMarkers = List.of(
                "闅愯棌",
                "瀛︾敓",
                "鑰佸笀",
                "涓€",
                "鈥",
                "鐐?",
                "锛?",
                "銆"
        );
        assertThat(text)
                .as("fixture %s must not contain corrupted Chinese text", caseId)
                .doesNotContain(mojibakeMarkers.toArray(String[]::new));
    }

    private String combinedAnalysisText(SubmissionAnalysisResponse analysis) {
        if (analysis == null) {
            return "";
        }
        return String.join("\n",
                safe(analysis.getHeadline()),
                safe(analysis.getSummary()),
                safe(analysis.getStudentHint()),
                safe(analysis.getTeacherNote()),
                safe(analysis.getUncertainty()),
                analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getProblemType()),
                analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getNextAction()),
                analysis.getStudentHintPlan() == null ? "" : safe(analysis.getStudentHintPlan().getCoachQuestion())
        );
    }

    private VisibleStudentFeedback visibleStudentFeedback(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.StudentFeedbackView view = analysis == null ? null : analysis.getStudentFeedbackView();
        if (view == null) {
            return new VisibleStudentFeedback("", "", "", "");
        }
        String basic = firstFeedbackBody(view.getRepairItems());
        String improvement = String.join("\n", safe(view.getImprovementItems()).stream()
                .map(item -> safe(item.getBody()).trim())
                .filter(text -> !text.isBlank())
                .toList());
        String next = safe(view.getNextQuestion()).trim();
        String all = String.join("\n", List.of(basic, improvement, next).stream()
                .filter(text -> !text.isBlank())
                .toList());
        return new VisibleStudentFeedback(basic, improvement, next, all);
    }

    private String firstFeedbackBody(List<SubmissionAnalysisResponse.FeedbackViewItem> items) {
        return safe(items).stream()
                .map(item -> safe(item.getBody()).trim())
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private VisibleStudentFeedback studentReportFeedback(VisibleStudentFeedback visibleFeedback) {
        if (!Boolean.parseBoolean(valueOrDefault(System.getenv("AI_EVAL_INCLUDE_STUDENT_AI_FEEDBACK"), "false"))) {
            return new VisibleStudentFeedback("", "", "", "", false);
        }
        return new VisibleStudentFeedback(
                visibleFeedback.basicText(),
                visibleFeedback.improvementText(),
                visibleFeedback.nextActionText(),
                visibleFeedback.allText(),
                true
        );
    }

    private List<String> visibleInputSnapshots(AssistantEvalFixtureLoader.Fixture fixture) {
        if (fixture == null || fixture.diagnosis() == null || fixture.diagnosis().caseResults() == null) {
            return List.of();
        }
        return fixture.diagnosis().caseResults().stream()
                .filter(item -> item != null && !Boolean.TRUE.equals(item.hidden()))
                .map(item -> String.join("\n", List.of(
                        safe(item.inputSnapshot()),
                        safe(item.actualOutput()),
                        safe(item.expectedOutput())
                )))
                .filter(text -> !text.isBlank())
                .toList();
    }

    private String resolveFailureReason(boolean fallbackUsed,
                                        boolean partialCompleted,
                                        SubmissionAnalysisResponse.AiInvocation invocation,
                                        String uncertainty,
                                        boolean signalHit,
                                        boolean safetyPassed,
                                        boolean teachingActionValid) {
        return resolveFailureReason(fallbackUsed, partialCompleted, invocation, uncertainty,
                signalHit, safetyPassed, teachingActionValid, true, List.of());
    }

    private String resolveFailureReason(boolean fallbackUsed,
                                        boolean partialCompleted,
                                        SubmissionAnalysisResponse.AiInvocation invocation,
                                        String uncertainty,
                                        boolean signalHit,
                                        boolean safetyPassed,
                                        boolean teachingActionValid,
                                        boolean studentVisibleQualityPassed,
                                        List<String> studentVisibleQualityFlags) {
        if (fallbackUsed) {
            String status = invocation == null ? "MODEL_RUNTIME_FALLBACK" : safe(invocation.getStatus());
            String detail = structuredFailureReason(invocation);
            if (detail.isBlank()) {
                detail = extractRuntimeFailureReason(uncertainty);
            }
            return detail.isBlank() ? status : status + ":" + detail;
        }
        if (partialCompleted) {
            String detail = structuredFailureReason(invocation);
            if (detail.isBlank()) {
                detail = extractRuntimeFailureReason(uncertainty);
            }
            return detail.isBlank() ? "MODEL_PARTIAL_COMPLETED" : "MODEL_PARTIAL_COMPLETED:" + detail;
        }
        if (!safetyPassed) {
            return "SAFETY_REJECTED";
        }
        if (!signalHit) {
            return "QUALITY_MISS";
        }
        if (!teachingActionValid) {
            return "TEACHING_ACTION_MISMATCH";
        }
        if (!studentVisibleQualityPassed) {
            return "STUDENT_VISIBLE_QUALITY_RISK:" + String.join("|", safe(studentVisibleQualityFlags));
        }
        return "NONE";
    }

    private String resolveCoachFailureReason(boolean fallbackUsed,
                                             String failureReason,
                                             boolean signalHit,
                                             boolean safetyPassed) {
        if (fallbackUsed) {
            String detail = safe(failureReason);
            return detail.isBlank() ? "MODEL_RUNTIME_FALLBACK" : "MODEL_RUNTIME_FALLBACK:" + detail;
        }
        if (!safetyPassed) {
            return "SAFETY_REJECTED";
        }
        if (!signalHit) {
            return "QUALITY_MISS";
        }
        return "NONE";
    }

    private String extractRuntimeFailureReason(String uncertainty) {
        String text = safe(uncertainty);
        List<String> knownReasons = List.of(
                "INSUFFICIENT_QUOTA",
                "RATE_LIMITED",
                "TIMEOUT",
                "MODEL_UNSUPPORTED",
                "EMPTY_RESPONSE",
                "INVALID_JSON",
                "INVALID_TAG",
                "INVALID_EVIDENCE_REF",
                "SAFETY_RISK",
                "API_ERROR",
                "UNKNOWN_ERROR"
        );
        for (String reason : knownReasons) {
            if (text.contains(reason)) {
                return reason;
            }
        }
        return "";
    }

    private String extractGrowthReportFailureReason(String markdown) {
        String text = safe(markdown);
        if (text.isBlank()) {
            return "";
        }
        String marker = "<!-- AI_FAILURE:";
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = text.indexOf("-->", start);
        if (end < 0) {
            return "";
        }
        return text.substring(start + marker.length(), end).trim();
    }

    private String stripGrowthReportFailureMarker(String markdown) {
        String text = safe(markdown);
        return text.replaceAll("(?s)\\n*<!-- AI_FAILURE:.*?-->\\s*$", "").trim();
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

    private String resolveFailureStage(boolean fallbackUsed,
                                       boolean partialCompleted,
                                       SubmissionAnalysisResponse.AiInvocation invocation,
                                       String uncertainty) {
        if (fallbackUsed || partialCompleted) {
            String structuredStage = invocation == null ? "" : safe(invocation.getFailureStage());
            if (!structuredStage.isBlank()) {
                return structuredStage;
            }
            return extractRuntimeFailureStage(uncertainty);
        }
        return "NONE";
    }

    private String extractRuntimeFailureStage(String uncertainty) {
        String text = safe(uncertainty);
        List<String> knownStages = List.of(
                "DIAGNOSIS_AND_ADVICE",
                "DIAGNOSIS_AND_ADVICE",
                "DIAGNOSIS_AND_ADVICE",
                "SUBMISSION_ANALYSIS",
                "UNKNOWN_STAGE"
        );
        for (String stage : knownStages) {
            if (text.contains(stage)) {
                return stage;
            }
        }
        return text.isBlank() ? "NONE" : "UNKNOWN_STAGE";
    }

    private String classifyException(Exception exception) {
        String text = (exception.getClass().getName() + " " + exception.getMessage()).toLowerCase(Locale.ROOT);
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
        if (text.contains("json")) {
            return "JSON_INVALID";
        }
        return "EXCEPTION";
    }

    private String iterationSuggestion(String assistantType,
                                       boolean fallbackUsed,
                                       boolean signalHit,
                                       boolean safetyPassed,
                                       String failureReason) {
        if (isBudgetLimitedFailure(failureReason)) {
            return "优先处理外部模型预算、限流、调用间隔或模型路由；本条不应优先归因到 prompt 或教学策略。";
        }
        if (failureReason != null && failureReason.contains("SAFETY_REJECTED")) {
            return "优先收紧 " + assistantType + " 的 prompt，降低直接引用代码片段、直接改法或过度提示的概率。";
        }
        if (fallbackUsed) {
            return "优先改进外部模型调用稳定性、阶段失败归因或降低单样本调用成本。";
        }
        if (!safetyPassed) {
            return "优先收紧 " + assistantType + " 的安全提示和泄题拦截规则。";
        }
        if (!signalHit) {
            return switch (assistantType) {
                case "SUBMISSION_DIAGNOSIS" -> "优先调整诊断标准库裁剪和 diagnosis prompt，让模型必须命中老师期望的证据链。";
                case "COACH_QUESTION" -> "优先调整 Coach prompt，让模型追问学生当前回答中的具体缺口。";
                case "GROWTH_REPORT" -> "优先增强轨迹输入和成长报告 rubric，要求显式总结进步、卡点和下一步。";
                default -> "优先补充该助手的评分点和提示词约束。";
            };
        }
        return "该样本可作为正向基线，后续扩展同类变体。";
    }

    private boolean isBudgetLimitedFailure(String failureReason) {
        String text = safe(failureReason);
        return text.contains("INSUFFICIENT_QUOTA")
                || text.contains("RATE_LIMITED");
    }

    private Assignment.HintPolicy parseHintPolicy(String value) {
        if (value == null || value.isBlank()) {
            return Assignment.HintPolicy.L2;
        }
        return Assignment.HintPolicy.valueOf(value);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private double doubleValueOrDefault(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = safe(value).replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 15) + "... [truncated]";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record VisibleStudentFeedback(String basicText,
                                          String improvementText,
                                          String nextActionText,
                                          String allText,
                                          boolean enabled) {
        private VisibleStudentFeedback(String basicText,
                                       String improvementText,
                                       String nextActionText,
                                       String allText) {
            this(basicText, improvementText, nextActionText, allText, false);
        }
    }
}
