package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthAgentService;
import com.onlinejudge.aiquota.application.AiInvocationContext;
import com.onlinejudge.aiquota.application.AiInvocationContextResolver;
import com.onlinejudge.aiquota.application.AiProviderGateway;
import com.onlinejudge.aiquota.domain.QuotaExhaustedException;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationExpansionResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiReportService {

    private static final String AI_SOURCE = "MODEL_SCOPE_EXTERNAL_MODEL";
    private static final String PROVIDER = "ModelScope";
    private static final String PROMPT_VERSION = "submission-diagnosis-prompt-v2";
    private static final int STANDARD_LIBRARY_NAVIGATION_DEFAULT_MAX_ROUNDS = 6;
    private static final int STANDARD_LIBRARY_NAVIGATION_MAX_BRANCHES = 3;

    private final ObjectMapper objectMapper;
    private final AiCodeAssistSupport aiCodeAssistSupport;
    private final ExternalModelAgentRuntime externalModelAgentRuntime;
    private final ExternalModelFailureClassifier failureClassifier;
    private final ExternalModelChatRequestFactory chatRequestFactory;
    private final AiStandardLibraryGrowthAgentService standardLibraryGrowthAgentService;
    private final AiStandardLibraryService standardLibraryService;
    private final StandardLibraryNavigationOutputValidator standardLibraryNavigationOutputValidator;
    private final StandardLibraryNavigationPackBuilder standardLibraryNavigationPackBuilder;
    private final ThreadLocal<ExternalModelCallTelemetry> lastCallTelemetry = ThreadLocal.withInitial(ExternalModelCallTelemetry::empty);
    private final ThreadLocal<ExternalModelCallTelemetry> lastStructuredRetrySourceTelemetry =
            ThreadLocal.withInitial(ExternalModelCallTelemetry::empty);
    private final ThreadLocal<Boolean> structuredRetryUsedInInvocation = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<ExternalModelRequestContext> nextRequestContext =
            ThreadLocal.withInitial(ExternalModelRequestContext::standard);
    private final ThreadLocal<String> lastModelStageRawContent = ThreadLocal.withInitial(() -> "");
    private final ThreadLocal<String> activeModel = ThreadLocal.withInitial(() -> "");
    private final ThreadLocal<AiInvocationContext> activeInvocationContext = ThreadLocal.withInitial(
            () -> AiInvocationContext.anonymous("SYSTEM_AI", "system-ai-unscoped"));
    private final ThreadLocal<Boolean> providerHealthCheck = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Consumer<ModelCallTraceEvent>> MODEL_CALL_TRACE_SINK = new ThreadLocal<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private AiDiagnosisWorkflowService diagnosisWorkflowService;
    private AiProviderGateway aiProviderGateway;
    private AiInvocationContextResolver aiInvocationContextResolver;
    private Executor diagnosisBranchExecutor = Runnable::run;

    @Autowired(required = false)
    void setDiagnosisWorkflowService(AiDiagnosisWorkflowService diagnosisWorkflowService) {
        this.diagnosisWorkflowService = diagnosisWorkflowService;
    }

    @Autowired(required = false)
    void setDiagnosisBranchExecutor(@Qualifier("aiDiagnosisBranchExecutor") Executor diagnosisBranchExecutor) {
        if (diagnosisBranchExecutor != null) {
            this.diagnosisBranchExecutor = diagnosisBranchExecutor;
        }
    }

    @Autowired(required = false)
    void setAiProviderGateway(AiProviderGateway aiProviderGateway) {
        this.aiProviderGateway = aiProviderGateway;
    }

    @Autowired(required = false)
    void setAiInvocationContextResolver(AiInvocationContextResolver aiInvocationContextResolver) {
        this.aiInvocationContextResolver = aiInvocationContextResolver;
    }

    public AiReportService(ObjectMapper objectMapper, AiCodeAssistSupport aiCodeAssistSupport) {
        this(objectMapper, aiCodeAssistSupport, null, new ExternalModelFailureClassifier());
    }

    public AiReportService(ObjectMapper objectMapper,
                           AiCodeAssistSupport aiCodeAssistSupport,
                           ExternalModelAgentRuntime externalModelAgentRuntime) {
        this(objectMapper, aiCodeAssistSupport, externalModelAgentRuntime, new ExternalModelFailureClassifier());
    }

    public AiReportService(ObjectMapper objectMapper,
                           AiCodeAssistSupport aiCodeAssistSupport,
                           ExternalModelAgentRuntime externalModelAgentRuntime,
                           ExternalModelFailureClassifier failureClassifier) {
        this(objectMapper, aiCodeAssistSupport, externalModelAgentRuntime, failureClassifier,
                (ExternalModelChatRequestFactory) null, null, null, null, null);
    }

    public AiReportService(ObjectMapper objectMapper,
                           AiCodeAssistSupport aiCodeAssistSupport,
                           ExternalModelAgentRuntime externalModelAgentRuntime,
                           ExternalModelFailureClassifier failureClassifier,
                           ExternalModelChatRequestFactory chatRequestFactory) {
        this(objectMapper, aiCodeAssistSupport, externalModelAgentRuntime, failureClassifier,
                chatRequestFactory, null, null, null, null);
    }

    @Autowired
    public AiReportService(ObjectMapper objectMapper,
                           AiCodeAssistSupport aiCodeAssistSupport,
                           ExternalModelAgentRuntime externalModelAgentRuntime,
                           ExternalModelFailureClassifier failureClassifier,
                           AiStandardLibraryGrowthAgentService standardLibraryGrowthAgentService,
                           AiStandardLibraryService standardLibraryService,
                           StandardLibraryNavigationOutputValidator standardLibraryNavigationOutputValidator,
                           StandardLibraryNavigationPackBuilder standardLibraryNavigationPackBuilder) {
        this(objectMapper, aiCodeAssistSupport, externalModelAgentRuntime, failureClassifier,
                new ExternalModelChatRequestFactory(), standardLibraryGrowthAgentService, standardLibraryService,
                standardLibraryNavigationOutputValidator, standardLibraryNavigationPackBuilder);
    }

    public AiReportService(ObjectMapper objectMapper,
                           AiCodeAssistSupport aiCodeAssistSupport,
                           ExternalModelAgentRuntime externalModelAgentRuntime,
                           ExternalModelFailureClassifier failureClassifier,
                           ExternalModelChatRequestFactory chatRequestFactory,
                           AiStandardLibraryGrowthAgentService standardLibraryGrowthAgentService,
                           AiStandardLibraryService standardLibraryService,
                           StandardLibraryNavigationOutputValidator standardLibraryNavigationOutputValidator,
                           StandardLibraryNavigationPackBuilder standardLibraryNavigationPackBuilder) {
        this.objectMapper = objectMapper;
        this.aiCodeAssistSupport = aiCodeAssistSupport;
        this.externalModelAgentRuntime = externalModelAgentRuntime;
        this.failureClassifier = failureClassifier == null ? new ExternalModelFailureClassifier() : failureClassifier;
        this.chatRequestFactory = chatRequestFactory == null ? new ExternalModelChatRequestFactory() : chatRequestFactory;
        this.standardLibraryGrowthAgentService = standardLibraryGrowthAgentService;
        this.standardLibraryService = standardLibraryService;
        this.standardLibraryNavigationOutputValidator = standardLibraryNavigationOutputValidator == null
                ? new StandardLibraryNavigationOutputValidator()
                : standardLibraryNavigationOutputValidator;
        this.standardLibraryNavigationPackBuilder = standardLibraryNavigationPackBuilder;
    }

    @Value("${ai.enabled:true}")
    private boolean enabled;

    @Value("${ai.base-url:https://api-inference.modelscope.cn/v1}")
    private String baseUrl;

    /** Test-only compatibility for directly constructed service instances; production keys live in AiProviderGateway. */
    private String apiKey;

    @Value("${ai.model:Qwen/Qwen3-235B-A22B-Instruct-2507}")
    private String model;

    @Value("${ai.model-pool:}")
    private String modelPool;

    @Value("${ai.modelscope-compatible-request:auto}")
    private String modelScopeCompatibleRequest = "auto";

    @Value("${ai.enable-thinking:false}")
    private boolean enableThinking;

    @Value("${ai.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${ai.external-runtime-enabled:true}")
    private boolean externalRuntimeEnabled;

    @Value("${ai.external-runtime-profile:standard}")
    private String externalRuntimeProfile = ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD;

    @Value("${ai.max-output-tokens:3200}")
    private int maxOutputTokens = 3200;

    @Value("${ai.structured-retry-output-tokens:4200}")
    private int structuredRetryOutputTokens = 4200;

    @Value("${ai.stream-enabled:true}")
    private boolean streamEnabled;

    @Value("${ai.stream-fallback-enabled:false}")
    private boolean streamFallbackEnabled = false;

    @Value("${ai.structured-retry-enabled:true}")
    private boolean structuredRetryEnabled = true;

    @Value("${ai.retry.max-attempts:1}")
    private int retryMaxAttempts;

    @Value("${ai.retry.backoff-ms:700}")
    private long retryBackoffMs;

    @Value("${ai.standard-library-navigation.max-rounds:6}")
    private int standardLibraryNavigationMaxRounds = STANDARD_LIBRARY_NAVIGATION_DEFAULT_MAX_ROUNDS;

    public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                Submission submission,
                                                                SubmissionAnalysisResponse baseline) {
        return enhanceSubmissionAnalysis(problem, submission, baseline, null);
    }

    public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                Submission submission,
                                                                SubmissionAnalysisResponse baseline,
                                                                DiagnosisEvidencePackage evidencePackage) {
        AiInvocationContext invocationContext = submissionContext(submission, "SUBMISSION_FEEDBACK",
                "submission-feedback:" + (submission == null ? "unknown" : submission.getId()));
        if (!canCallAi() || (aiProviderGateway != null && invocationContext.teacherId() == null)) {
            log.info("AI submission analysis skipped because AI access is unavailable. submissionId={}", submission.getId());
            return runtimeFailure(baseline, aiUnavailableFailure("SUBMISSION_ANALYSIS"));
        }

        activeInvocationContext.set(invocationContext);
        try {
            structuredRetryUsedInInvocation.set(false);
            log.info("AI submission analysis started. submissionId={}, problemId={}, language={}",
                    submission.getId(),
                    submission.getProblemId(),
                    submission.getLanguageName());
            String rawSourceCode = submission.getSourceCode() == null ? "" : submission.getSourceCode();
            List<SubmissionAnalysisResponse.LineIssue> baselineLineIssues = baseline == null || baseline.getLineIssues() == null
                    ? List.of()
                    : baseline.getLineIssues();
            if (shouldUseExternalRuntime(evidencePackage)) {
                return enhanceWithExternalRuntime(
                        submission,
                        baseline,
                        evidencePackage,
                        rawSourceCode,
                        baselineLineIssues
                );
            }
            log.info("AI submission analysis skipped because new runtime context is incomplete. submissionId={}",
                    submission.getId());
            return runtimeFailure(baseline, stageFailure(
                    "SUBMISSION_ANALYSIS",
                    ModelStageFailureReason.UNKNOWN_ERROR,
                    "External model runtime context is incomplete."
            ));
        } catch (Exception exception) {
            log.error("AI submission analysis enhancement failed. submissionId={}", submission.getId(), exception);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (exception instanceof QuotaExhaustedException) {
                return runtimeFailure(baseline, stageFailure("SUBMISSION_ANALYSIS",
                        ModelStageFailureReason.INSUFFICIENT_QUOTA, "QUOTA_EXHAUSTED"));
            }
            if (shouldUseExternalRuntime(evidencePackage)) {
                return runtimeFailure(baseline, stageFailureFromException("SUBMISSION_ANALYSIS", exception));
            }
            return runtimeFailure(baseline, stageFailureFromException("SUBMISSION_ANALYSIS", exception));
        } finally {
            activeInvocationContext.remove();
        }
    }

    private boolean shouldUseExternalRuntime(DiagnosisEvidencePackage evidencePackage) {
        return externalRuntimeEnabled
                && externalModelAgentRuntime != null
                && evidencePackage != null;
    }

    private SubmissionAnalysisResponse enhanceWithExternalRuntime(Submission submission,
                                                                  SubmissionAnalysisResponse baseline,
                                                                  DiagnosisEvidencePackage evidencePackage,
                                                                  String rawSourceCode,
                                                                  List<SubmissionAnalysisResponse.LineIssue> baselineLineIssues)
            throws JsonProcessingException, IOException, InterruptedException {
        ExternalModelAgentRuntime.RuntimePlan runtimePlan = externalModelAgentRuntime.prepare(
                evidencePackage,
                baseline,
                teacherDiagnosisRuntimeProfile()
        );
        ExternalModelStagePayloads.StageValidationResult navigationResult =
                applyAiStandardLibraryNavigation(runtimePlan);
        if (!navigationResult.isValid()) {
            return runtimeFailure(baseline, runtimePlan, navigationResult);
        }
        return enhanceWithAdviceGenerationRuntime(
                submission,
                baseline,
                runtimePlan,
                rawSourceCode,
                baselineLineIssues
        );
    }

    private ExternalModelAgentRuntime.RuntimePlan prepareStudentFeedbackRuntimePlan(
            DiagnosisEvidencePackage evidencePackage) {
        ExternalModelAgentRuntime.RuntimePlan runtimePlan = null;
        if (externalModelAgentRuntime == null || evidencePackage == null) {
            StandardLibraryPack pack = localStudentStandardLibraryPack(evidencePackage);
            return pack == null ? null : ExternalModelAgentRuntime.RuntimePlan.builder()
                    .standardLibraryPack(pack)
                    .build();
        }
        try {
            runtimePlan = externalModelAgentRuntime.prepare(
                    evidencePackage,
                    null,
                    externalRuntimeProfile
            );
        } catch (Exception exception) {
            log.warn("Student AI feedback runtime context unavailable. reason={}", exception.getMessage());
        }
        StandardLibraryPack pack = localStudentStandardLibraryPack(evidencePackage);
        if (pack == null) {
            return runtimePlan;
        }
        if (runtimePlan == null) {
            return ExternalModelAgentRuntime.RuntimePlan.builder()
                    .standardLibraryPack(pack)
                    .build();
        }
        if (runtimePlan.getStandardLibraryPack() == null) {
            runtimePlan.setStandardLibraryPack(pack);
        }
        return runtimePlan;
    }

    private StandardLibraryPack localStudentStandardLibraryPack(DiagnosisEvidencePackage evidencePackage) {
        if (standardLibraryService == null || standardLibraryNavigationPackBuilder == null || evidencePackage == null) {
            return null;
        }
        try {
            List<AiStandardLibraryItem> items = standardLibraryService.enabledNavigationItems();
            if (items.isEmpty()) {
                return emptyStandardLibraryPack("LIBRARY_EMPTY", "", "");
            }
            String haystack = studentRecallHaystack(evidencePackage);
            List<AiStandardLibraryItem> ranked = items.stream()
                    .filter(item -> item != null)
                    .sorted(Comparator
                            .comparingInt((AiStandardLibraryItem item) -> localRecallScore(item, haystack))
                            .reversed()
                            .thenComparing(item -> defaultIfBlank(item.getCategory(), ""))
                            .thenComparing(item -> defaultIfBlank(item.getCode(), "")))
                    .limit(40)
                    .toList();
            return standardLibraryNavigationPackBuilder.buildFromItems(ranked, "LOCAL_RECALL",
                    "本地召回树形标准库包，供单诊断 Agent 参考。");
        } catch (Exception exception) {
            log.warn("Student AI feedback local standard library recall failed. reason={}", exception.getMessage());
            return emptyStandardLibraryPack("ATTACHMENT_FAILED", exception.getMessage(), "");
        }
    }

    private String studentRecallHaystack(DiagnosisEvidencePackage evidencePackage) {
        StringBuilder builder = new StringBuilder();
        DiagnosisEvidencePackage.ProblemEvidence problem = evidencePackage.getProblem();
        if (problem != null) {
            builder.append(problem.getTitle()).append('\n')
                    .append(problem.getDescription()).append('\n')
                    .append(problem.getDifficulty()).append('\n')
                    .append(String.join("\n", cleanList(problem.getKnowledgePoints(), List.of()))).append('\n')
                    .append(String.join("\n", cleanList(problem.getCommonMistakes(), List.of()))).append('\n');
        }
        DiagnosisEvidencePackage.SubmissionEvidence submission = evidencePackage.getSubmission();
        if (submission != null) {
            builder.append(submission.getLanguage()).append('\n')
                    .append(submission.getVerdict()).append('\n')
                    .append(submission.getSourceCodeWithLineNumbers()).append('\n');
        }
        DiagnosisEvidencePackage.JudgeFacts judgeFacts = evidencePackage.getJudgeFacts();
        if (judgeFacts != null) {
            builder.append(judgeFacts.getRuntimeErrorMessage()).append('\n')
                    .append(judgeFacts.getCompileOutput()).append('\n');
            for (DiagnosisEvidencePackage.CaseSummary item : judgeFacts.getCaseResultsSummary() == null
                    ? List.<DiagnosisEvidencePackage.CaseSummary>of()
                    : judgeFacts.getCaseResultsSummary()) {
                if (item == null || Boolean.TRUE.equals(item.getHidden())) {
                    continue;
                }
                builder.append(item.getActualOutputPreview()).append('\n')
                        .append(item.getExpectedOutputPreview()).append('\n');
            }
        }
        return normalizeRecallText(builder.toString());
    }

    private int localRecallScore(AiStandardLibraryItem item, String haystack) {
        if (item == null || haystack == null || haystack.isBlank()) {
            return 0;
        }
        int score = 0;
        score += recallFieldScore(haystack, item.getName(), 8);
        score += recallFieldScore(haystack, item.getCategory(), 5);
        score += recallFieldScore(haystack, item.getDescription(), 3);
        score += recallFieldScore(haystack, item.getCommonMisconception(), 3);
        score += recallFieldScore(haystack, item.getEvidenceSignals(), 4);
        score += recallFieldScore(haystack, item.getCommonCodePatterns(), 6);
        score += recallFieldScore(haystack, item.getJudgeSignals(), 6);
        score += recallFieldScore(haystack, item.getWhenToUse(), 3);
        score += recallFieldScore(haystack, item.getStudentBenefit(), 2);
        return score;
    }

    private int recallFieldScore(String haystack, String rawValue, int weight) {
        String value = normalizeRecallText(rawValue);
        if (value.isBlank()) {
            return 0;
        }
        if (haystack.contains(value)) {
            return weight * 3;
        }
        int score = 0;
        for (String token : value.split("[^\\p{IsHan}A-Za-z0-9_]+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                score += weight;
            }
        }
        for (int index = 0; index + 4 <= value.length(); index += 2) {
            String fragment = value.substring(index, index + 4);
            if (haystack.contains(fragment)) {
                score += Math.max(1, weight / 2);
                break;
            }
        }
        return score;
    }

    private String normalizeRecallText(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private SubmissionAnalysisResponse enhanceWithAdviceGenerationRuntime(
            Submission submission,
            SubmissionAnalysisResponse baseline,
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            String rawSourceCode,
            List<SubmissionAnalysisResponse.LineIssue> baselineLineIssues)
            throws JsonProcessingException, IOException, InterruptedException {
        String promptVersion = runtimePromptVersion(runtimePlan);
        Long activeRunId = AiDiagnosisWorkflowContext.currentRunId().orElse(null);
        Consumer<ModelCallTraceEvent> activeTraceSink = MODEL_CALL_TRACE_SINK.get();
        CompletableFuture<StudentOutputStageResult> studentFuture = CompletableFuture.supplyAsync(
                () -> withBranchContext(activeRunId, activeTraceSink,
                        () -> executeStudentOutputStage(runtimePlan)),
                diagnosisBranchExecutor
        );
        boolean durableWorkflowActive = diagnosisWorkflowService != null
                && AiDiagnosisWorkflowContext.currentRunId().isPresent();
        CompletableFuture<TeacherInsightOutput> teacherFuture = durableWorkflowActive
                ? CompletableFuture.supplyAsync(
                        () -> withBranchContext(activeRunId, activeTraceSink,
                                () -> executeTeacherOutputStage(runtimePlan)),
                        diagnosisBranchExecutor
                )
                : CompletableFuture.completedFuture(null);
        StudentOutputStageResult studentOutput;
        TeacherInsightOutput teacherInsight;
        try {
            studentOutput = studentFuture.join();
        } catch (Exception exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            ExternalModelStagePayloads.StageValidationResult failure = cause instanceof StageValidationException validation
                    ? withStage("DIAGNOSIS_AND_ADVICE", validation.validationResult())
                    : stageFailureFromException("DIAGNOSIS_AND_ADVICE",
                    cause instanceof Exception value ? value : exception);
            runtimePlan.setAdviceGenerationResult(AdviceGenerationResult.failed(
                    adviceFailureReason(failure),
                    promptVersion
            ));
            return runtimeFailure(baseline, runtimePlan, failure);
        }
        try {
            teacherInsight = teacherFuture.join();
        } catch (Exception exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            ExternalModelStagePayloads.StageValidationResult failure =
                    stageFailureFromException("TEACHER_INSIGHT",
                            cause instanceof Exception value ? value : exception);
            return runtimeFailure(baseline, runtimePlan, failure);
        }
        AdviceGenerationOutput adviceOutput = studentOutput.getAdviceOutput();
        SubmissionAnalysisResponse.StudentFeedback studentFeedback = studentOutput.getStudentFeedback();
        runtimePlan.setTeacherInsightOutput(teacherInsight);
        persistStandardLibraryGrowthCandidates(adviceOutput, submission);
        runtimePlan.setAdviceGenerationResult(AdviceGenerationResult.success(adviceOutput, promptVersion));

        SubmissionAnalysisResponse response = buildAdviceRuntimeAnalysisResponse(
                baseline,
                runtimePlan,
                adviceOutput,
                studentFeedback,
                rawSourceCode,
                baselineLineIssues
        );
        if (teacherInsight != null) {
            response.setTeacherNote(teacherInsightSummary(teacherInsight, response.getTeacherNote()));
        }
        return response;
    }

    private StudentOutputStageResult executeStudentOutputStage(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        try {
            StudentOutputStageResult result = executeWorkflowStage(
                    "STUDENT_OUTPUT",
                    "STUDENT_OUTPUT",
                    null,
                    outputStageInput(runtimePlan),
                    StudentOutputStageResult.class,
                    PromptTemplateRegistry.DIAGNOSIS_REPORT_V4,
                    () -> generateValidatedStudentOutput(runtimePlan)
            );
            runtimePlan.setAdviceGenerationResult(AdviceGenerationResult.success(
                    result.getAdviceOutput(),
                    runtimePromptVersion(runtimePlan)
            ));
            return result;
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private StudentOutputStageResult generateValidatedStudentOutput(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) throws Exception {
        AdviceGenerationOutput adviceOutput = callAdviceGenerationStage(runtimePlan);
        adviceOutput = externalModelAgentRuntime.normalizeAdviceGeneration(adviceOutput, runtimePlan);
        linkAdviceToFreeDiagnosisIssues(adviceOutput, runtimePlan);

        ExternalModelStagePayloads.StageValidationResult adviceValidation =
                validateAdviceGenerationWithCoverageContract(adviceOutput, runtimePlan);
        if (!adviceValidation.isValid() && isAdviceCoverageFailure(adviceValidation)) {
            AdviceGenerationOutput rewrittenOutput =
                    retryAdviceGenerationForCoverage(runtimePlan, adviceOutput, adviceValidation);
            if (rewrittenOutput != null) {
                rewrittenOutput = externalModelAgentRuntime.normalizeAdviceGeneration(rewrittenOutput, runtimePlan);
                linkAdviceToFreeDiagnosisIssues(rewrittenOutput, runtimePlan);
                adviceOutput = rewrittenOutput;
                adviceValidation = validateAdviceGenerationWithCoverageContract(adviceOutput, runtimePlan);
            }
        }
        if (!adviceValidation.isValid()
                && adviceValidation.getFailureReason() == ModelStageFailureReason.SAFETY_RISK) {
            AdviceGenerationOutput rewrittenOutput =
                    retryAdviceGenerationForSafety(runtimePlan, adviceOutput, adviceValidation);
            rewrittenOutput = externalModelAgentRuntime.normalizeAdviceGeneration(rewrittenOutput, runtimePlan);
            linkAdviceToFreeDiagnosisIssues(rewrittenOutput, runtimePlan);
            adviceOutput = rewrittenOutput;
            adviceValidation = validateAdviceGenerationWithCoverageContract(adviceOutput, runtimePlan);
        }
        if (!adviceValidation.isValid()) {
            throw new StageValidationException(adviceValidation);
        }

        reconcileAdviceKnowledgePaths(adviceOutput, runtimePlan);
        SubmissionAnalysisResponse.StudentFeedback studentFeedback =
                externalModelAgentRuntime.mapAdviceStudentFeedback(adviceOutput, runtimePlan);
        studentFeedback = externalModelAgentRuntime.normalizeStudentFeedback(studentFeedback, runtimePlan);
        ExternalModelStagePayloads.StageValidationResult feedbackValidation =
                externalModelAgentRuntime.validateStudentFeedback(studentFeedback, runtimePlan);
        if (!feedbackValidation.isValid()) {
            throw new StageValidationException(feedbackValidation);
        }
        return StudentOutputStageResult.builder()
                .adviceOutput(adviceOutput)
                .studentFeedback(studentFeedback)
                .build();
    }

    private TeacherInsightOutput executeTeacherOutputStage(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        try {
            TeacherInsightOutput output = executeWorkflowStage(
                    "TEACHER_OUTPUT",
                    "TEACHER_OUTPUT",
                    null,
                    outputStageInput(runtimePlan),
                    TeacherInsightOutput.class,
                    PromptTemplateRegistry.TEACHER_INSIGHT_V1,
                    () -> {
                        TeacherInsightOutput generated = callTeacherInsightStage(runtimePlan);
                        validateTeacherInsight(generated, runtimePlan);
                        return generated;
                    }
            );
            validateTeacherInsight(output, runtimePlan);
            return output;
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private void persistStandardLibraryGrowthCandidates(AdviceGenerationOutput adviceOutput, Submission submission) {
        if (standardLibraryGrowthAgentService == null || adviceOutput == null) {
            return;
        }
        try {
            Long sourceProblemId = submission == null ? null : submission.getProblemId();
            Long sourceSubmissionId = submission == null ? null : submission.getId();
            executeWorkflowStage(
                    "LIBRARY_GROWTH_PROJECTION",
                    "LIBRARY_GROWTH_PROJECTION",
                    null,
                    Map.of("problemId", sourceProblemId == null ? -1L : sourceProblemId,
                            "submissionId", sourceSubmissionId == null ? -1L : sourceSubmissionId,
                            "candidateCount", adviceOutput.getLibraryGrowth() == null
                                    || adviceOutput.getLibraryGrowth().getCandidates() == null
                                    ? 0
                                    : adviceOutput.getLibraryGrowth().getCandidates().size()),
                    AiDiagnosisProjectionResult.class,
                    PromptTemplateRegistry.DIAGNOSIS_REPORT_V4,
                    () -> {
                        standardLibraryGrowthAgentService.proposeFromDiagnosisOutput(
                                adviceOutput,
                                sourceProblemId,
                                sourceSubmissionId,
                                null
                        );
                        int count = adviceOutput.getLibraryGrowth() == null
                                || adviceOutput.getLibraryGrowth().getCandidates() == null
                                ? 0
                                : adviceOutput.getLibraryGrowth().getCandidates().size();
                        return AiDiagnosisProjectionResult.completed("LIBRARY_GROWTH_PROJECTION", count);
                    }
            );
        } catch (Exception exception) {
            log.warn("Failed to persist AI standard library growth candidates. reason={}", exception.getMessage());
        }
    }

    private void reconcileAdviceKnowledgePaths(AdviceGenerationOutput output,
                                               ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (output == null) {
            return;
        }
        List<IssueLibraryAnchor> anchors = safeList(
                runtimePlan == null ? null : runtimePlan.getIssueLibraryAnchors());
        List<AdviceGenerationOutput.LibraryGrowthCandidate> candidates = new ArrayList<>();
        int index = 0;
        for (AdviceGenerationOutput.BasicLayerAdvice advice : safeList(output.getBasicLayerAdvice())) {
            IssueLibraryAnchor anchor = adviceAnchor(anchors, advice == null ? null : advice.getIssueId(), index++);
            reconcileBasicAdvice(advice, anchor, candidates);
        }
        index = 0;
        for (AdviceGenerationOutput.ImprovementLayerAdvice advice : safeList(output.getImprovementLayerAdvice())) {
            IssueLibraryAnchor anchor = adviceAnchor(anchors, advice == null ? null : advice.getIssueId(), index++);
            reconcileImprovementAdvice(advice, anchor, candidates);
        }
        output.setLibraryGrowth(AdviceGenerationOutput.LibraryGrowth.builder()
                .candidates(candidates)
                .build());
    }

    private void reconcileBasicAdvice(AdviceGenerationOutput.BasicLayerAdvice advice,
                                      IssueLibraryAnchor anchor,
                                      List<AdviceGenerationOutput.LibraryGrowthCandidate> candidates) {
        if (advice == null) {
            return;
        }
        if (anchor != null && !defaultIfBlank(anchor.getMistakePointCode(), "").isBlank()) {
            advice.setMistakePointId(anchor.getMistakePointCode());
            advice.setSkillUnitId(anchor.getSkillUnitCode());
            advice.setKnowledgePath(pathNames(anchor.getPath()));
            advice.setKnowledgePathStatus("FORMAL");
            return;
        }
        if (applySelectedProvisional(
                anchor,
                "MISTAKE_POINT",
                advice::setKnowledgePath,
                advice::setKnowledgePathStatus,
                advice::setProvisionalNodeCode)) {
            return;
        }
        AdviceGenerationOutput.LibraryGrowthCandidate candidate = provisionalCandidate(
                advice.getIssueId(),
                "MISTAKE_POINT",
                advice.getTitle(),
                advice.getWhatHappened(),
                advice.getStudentAction(),
                advice.getEvidenceRefs(),
                advice.getConfidence(),
                anchor);
        if (candidate == null) {
            markUnclassified(advice::setKnowledgePath, advice::setKnowledgePathStatus);
            return;
        }
        advice.setKnowledgePath(candidate.getSuggestedPath());
        advice.setKnowledgePathStatus("PROVISIONAL");
        advice.setProvisionalNodeCode(provisionalCode(candidate));
        attachProvisionalToAnchor(anchor, candidate, advice.getProvisionalNodeCode());
        candidates.add(candidate);
    }

    private void reconcileImprovementAdvice(AdviceGenerationOutput.ImprovementLayerAdvice advice,
                                            IssueLibraryAnchor anchor,
                                            List<AdviceGenerationOutput.LibraryGrowthCandidate> candidates) {
        if (advice == null) {
            return;
        }
        if (anchor != null && !defaultIfBlank(anchor.getImprovementPointCode(), "").isBlank()) {
            advice.setImprovementPointId(anchor.getImprovementPointCode());
            advice.setSkillUnitId(anchor.getSkillUnitCode());
            advice.setKnowledgePath(pathNames(anchor.getPath()));
            advice.setKnowledgePathStatus("FORMAL");
            return;
        }
        if (applySelectedProvisional(
                anchor,
                "IMPROVEMENT_POINT",
                advice::setKnowledgePath,
                advice::setKnowledgePathStatus,
                advice::setProvisionalNodeCode)) {
            return;
        }
        AdviceGenerationOutput.LibraryGrowthCandidate candidate = provisionalCandidate(
                advice.getIssueId(),
                "IMPROVEMENT_POINT",
                advice.getTitle(),
                advice.getCurrentLimit(),
                advice.getSuggestion(),
                advice.getEvidenceRefs(),
                advice.getConfidence(),
                anchor);
        if (candidate == null) {
            markUnclassified(advice::setKnowledgePath, advice::setKnowledgePathStatus);
            return;
        }
        advice.setKnowledgePath(candidate.getSuggestedPath());
        advice.setKnowledgePathStatus("PROVISIONAL");
        advice.setProvisionalNodeCode(provisionalCode(candidate));
        attachProvisionalToAnchor(anchor, candidate, advice.getProvisionalNodeCode());
        candidates.add(candidate);
    }

    private IssueLibraryAnchor adviceAnchor(List<IssueLibraryAnchor> anchors, String issueId, int index) {
        String normalizedIssueId = defaultIfBlank(issueId, "").trim();
        if (!normalizedIssueId.isBlank()) {
            IssueLibraryAnchor explicit = safeList(anchors).stream()
                    .filter(anchor -> normalizedIssueId.equals(defaultIfBlank(anchor.getIssueId(), "").trim()))
                    .findFirst()
                    .orElse(null);
            if (explicit != null) {
                return explicit;
            }
        }
        return index >= 0 && index < safeList(anchors).size() ? safeList(anchors).get(index) : null;
    }

    private AdviceGenerationOutput.LibraryGrowthCandidate provisionalCandidate(
            String issueId,
            String layer,
            String title,
            String symptom,
            String studentAction,
            List<String> evidenceRefs,
            Double confidence,
            IssueLibraryAnchor anchor) {
        IssueLibraryAnchor.PathNode parent = lastKnowledgePoint(anchor == null ? null : anchor.getPath());
        String normalizedTitle = cleanupAiText(title);
        if (parent == null || normalizedTitle.isBlank()) {
            return null;
        }
        List<String> path = new ArrayList<>(formalPathNames(anchor.getPath()));
        path.add(normalizedTitle);
        List<String> refs = cleanList(evidenceRefs, List.of());
        return AdviceGenerationOutput.LibraryGrowthCandidate.builder()
                .issueId(cleanupAiText(issueId))
                .layer(layer)
                .name(normalizedTitle)
                .suggestedPath(path)
                .parentKnowledgeNodeCode(parent.getCode())
                .similarExistingItems(List.of())
                .evidenceRefs(refs)
                .evidenceStatus(refs.isEmpty() ? "NO_DIRECT_CODE_EVIDENCE" : "SUPPORTED")
                .errorSymptom(cleanupAiText(symptom))
                .studentExplanation(cleanupAiText(studentAction))
                .reason("自由诊断已定位，但正式诊断层没有匹配项；按最后一个有效知识点归入临时候选。")
                .status("NEEDS_REVIEW")
                .confidence(confidence == null ? 0.75 : confidence)
                .build();
    }

    private boolean applySelectedProvisional(
            IssueLibraryAnchor anchor,
            String expectedLayer,
            Consumer<List<String>> pathSetter,
            Consumer<String> statusSetter,
            Consumer<String> codeSetter) {
        if (anchor == null
                || defaultIfBlank(anchor.getProvisionalCandidateCode(), "").isBlank()
                || !expectedLayer.equals(defaultIfBlank(anchor.getProvisionalCandidateLayer(), ""))) {
            return false;
        }
        pathSetter.accept(pathNames(anchor.getPath()));
        statusSetter.accept("PROVISIONAL");
        codeSetter.accept(anchor.getProvisionalCandidateCode());
        return true;
    }

    private void attachProvisionalToAnchor(IssueLibraryAnchor anchor,
                                           AdviceGenerationOutput.LibraryGrowthCandidate candidate,
                                           String code) {
        if (anchor == null || candidate == null) {
            return;
        }
        anchor.setAnchorStatus("PROVISIONAL");
        anchor.setProvisionalCandidateCode(code);
        anchor.setProvisionalCandidateName(candidate.getName());
        anchor.setProvisionalCandidateLayer(candidate.getLayer());
    }

    private void markUnclassified(Consumer<List<String>> pathSetter, Consumer<String> statusSetter) {
        pathSetter.accept(List.of());
        statusSetter.accept("UNCLASSIFIED");
    }

    private IssueLibraryAnchor.PathNode lastKnowledgePoint(List<IssueLibraryAnchor.PathNode> path) {
        List<IssueLibraryAnchor.PathNode> safePath = safeList(path);
        for (int index = safePath.size() - 1; index >= 0; index--) {
            IssueLibraryAnchor.PathNode node = safePath.get(index);
            if (node != null && "KNOWLEDGE_POINT".equals(defaultIfBlank(node.getType(), ""))) {
                return node;
            }
        }
        return null;
    }

    private List<String> formalPathNames(List<IssueLibraryAnchor.PathNode> path) {
        return safeList(path).stream()
                .filter(node -> node != null
                        && !defaultIfBlank(node.getType(), "").startsWith("PROVISIONAL_"))
                .map(node -> defaultIfBlank(node.getName(), node.getCode()))
                .filter(name -> !name.isBlank())
                .toList();
    }

    private List<String> pathNames(List<IssueLibraryAnchor.PathNode> path) {
        return safeList(path).stream()
                .filter(node -> node != null)
                .map(node -> defaultIfBlank(node.getName(), node.getCode()))
                .filter(name -> !name.isBlank())
                .toList();
    }

    private String provisionalCode(AdviceGenerationOutput.LibraryGrowthCandidate candidate) {
        String prefix = "IMPROVEMENT_POINT".equals(candidate.getLayer()) ? "IP_" : "MP_";
        String stableSource = defaultIfBlank(candidate.getParentKnowledgeNodeCode(), "").trim()
                + "|" + defaultIfBlank(candidate.getName(), "").trim()
                + "|" + String.join(".", safeList(candidate.getSuggestedPath()));
        return prefix + "AI_"
                + Integer.toUnsignedString(stableSource.hashCode(), 36).toUpperCase();
    }

    private String teacherDiagnosisRuntimeProfile() {
        return ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD;
    }

    private AdviceGenerationOutput callAdviceGenerationStage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "请完成一次高中信息学提交最终诊断：以自由诊断 issues 为主输入，结合可选标准库挂接结果，生成多条学生建议和结构化元数据。");
        request.put("contextPolicy", List.of(
                "problem.description 是完整题目描述；submission.sourceCodeWithLineNumbers 是完整学生代码或最大可用带行号代码。",
                "submission.verdict、visibleCaseFacts、runtimeErrorMessage、compileOutput 和 evidenceRefs 只是判题参考信号，用来验证诊断。",
                "testIntentFacts 只说明测试覆盖目标，不能单独证明错因；隐藏测试点只允许形成泛化检查动作，不能猜测或复述隐藏数据。",
                "freeDiagnosis.issues 是本阶段最重要的输入；每个 issue 都代表一个有证据支持的独立诊断点。",
                "libraryAnchors 是后端按 issue 逐层浏览标准库得到的可选挂接结果；anchorStatus 不是学生可见文案。",
                "standardLibrary 是教学参考规范包，用于统一术语、路径和颗粒度；即使为空或未命中，也必须继续基于 issues 生成学生建议。",
                "primaryKnowledgeNodeCode 是主知识路径，relatedKnowledgeNodeCodes 只是辅助上下文；学生端知识路径优先沿主路径表达，不把相关标签平铺成独立问题。",
                "basicLayerAdvice 应尽量一条对应一个主要 issue；多个 issue 不要压缩成一条笼统建议。",
                "improvementLayerAdvice 可以围绕复盘、自测、迁移和调试习惯给出多条方向，但不能重复基础层建议。",
                "如果某个 issue 没有标准库 anchor，相关标准库 id 可以留空，不要因此删除该 issue 的建议。",
                "studentReport 只能做摘要，不能替代 basicLayerAdvice 或 improvementLayerAdvice 数组。",
                "当自由诊断有多个有效 issue 时，basicLayerAdvice 和 improvementLayerAdvice 至少各覆盖两个独立问题，除非 issue 本身不足两个。",
                "学生可见反馈要自然、具体、可行动，但不能给完整答案或逐行改法。"
        ));
        request.put("brief", runtimePlan.getBrief());
        request.put("freeDiagnosis", runtimePlan.getFreeDiagnosisOutput());
        request.put("issues", runtimePlan.getFreeDiagnosisOutput() == null
                ? List.of()
                : safeList(runtimePlan.getFreeDiagnosisOutput().getIssues()));
        request.put("libraryAnchors", safeList(runtimePlan.getIssueLibraryAnchors()));
        request.put("navigationResult", runtimePlan.getStandardLibraryNavigationOutput());
        request.put("standardLibrary", runtimePlan.getStandardLibraryPack());
        activateRuntimePlan(runtimePlan);
        String systemPrompt = runtimePlan.getAdvicePrompt().getSystemPrompt();
        String userPrompt = objectMapper.writeValueAsString(request);
        String content = chatCompletionWithOverrides(systemPrompt, userPrompt, streamEnabled, adviceGenerationOutputTokens());
        lastStructuredRetrySourceTelemetry.set(ExternalModelCallTelemetry.empty());
        AdviceGenerationOutput output = parseModelStagePayload(content, AdviceGenerationOutput.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(
                systemPrompt,
                userPrompt,
                runtimePlan,
                AdviceGenerationOutput.class
        );
    }

    private TeacherInsightOutput callTeacherInsightStage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "基于已经确定的核心问题清单整理教师观察，不得重新诊断或改变问题集合。");
        request.put("brief", runtimePlan == null ? null : runtimePlan.getBrief());
        request.put("freeDiagnosis", runtimePlan == null ? null : runtimePlan.getFreeDiagnosisOutput());
        request.put("issues", runtimePlan == null || runtimePlan.getFreeDiagnosisOutput() == null
                ? List.of()
                : safeList(runtimePlan.getFreeDiagnosisOutput().getIssues()));
        request.put("libraryAnchors", runtimePlan == null ? List.of() : safeList(runtimePlan.getIssueLibraryAnchors()));
        activateRuntimePlan(runtimePlan);
        String systemPrompt = runtimePlan.getTeacherInsightPrompt().getSystemPrompt();
        String userPrompt = objectMapper.writeValueAsString(request);
        String content = chatCompletionWithOverrides(systemPrompt, userPrompt, streamEnabled,
                Math.min(2200, adviceGenerationOutputTokens()));
        TeacherInsightOutput output = parseModelStagePayload(content, TeacherInsightOutput.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(
                systemPrompt,
                userPrompt,
                runtimePlan,
                TeacherInsightOutput.class
        );
    }

    private void validateTeacherInsight(TeacherInsightOutput output,
                                        ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (output == null || cleanupAiText(output.getSummary()).isBlank()) {
            throw new IllegalStateException("teacher insight summary is empty");
        }
        List<FreeDiagnosisOutput.Issue> issues = runtimePlan == null || runtimePlan.getFreeDiagnosisOutput() == null
                ? List.of()
                : safeList(runtimePlan.getFreeDiagnosisOutput().getIssues());
        Set<String> expectedIssueIds = issues.stream()
                .map(FreeDiagnosisOutput.Issue::getIssueId)
                .map(this::cleanupAiText)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TeacherInsightOutput.IssueObservation> observations = safeList(output.getIssueObservations());
        Set<String> observedIssueIds = new LinkedHashSet<>();
        for (TeacherInsightOutput.IssueObservation observation : observations) {
            if (observation == null) {
                continue;
            }
            String issueId = cleanupAiText(observation.getIssueId());
            if (!expectedIssueIds.contains(issueId)) {
                throw new IllegalStateException("teacher insight contains unknown issueId=" + issueId);
            }
            if (!observedIssueIds.add(issueId)) {
                throw new IllegalStateException("teacher insight repeats issueId=" + issueId);
            }
            if (cleanupAiText(observation.getTeachingObservation()).isBlank()) {
                throw new IllegalStateException("teacher insight observation is empty for issueId=" + issueId);
            }
            for (String ref : safeList(observation.getEvidenceRefs())) {
                if (!EvidenceRefSupport.isValidEvidenceRef(ref, runtimePlan.getBrief())) {
                    throw new IllegalStateException("teacher insight contains invalid evidenceRef=" + ref);
                }
            }
        }
        if (!observedIssueIds.equals(expectedIssueIds)) {
            throw new IllegalStateException("teacher insight does not cover the canonical issue set");
        }
    }

    private Map<String, Object> outputStageInput(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("brief", runtimePlan == null ? null : runtimePlan.getBrief());
        input.put("issues", runtimePlan == null || runtimePlan.getFreeDiagnosisOutput() == null
                ? List.of()
                : safeList(runtimePlan.getFreeDiagnosisOutput().getIssues()));
        input.put("libraryAnchors", runtimePlan == null ? List.of() : safeList(runtimePlan.getIssueLibraryAnchors()));
        return input;
    }

    private String teacherInsightSummary(TeacherInsightOutput output, String fallback) {
        if (output == null) {
            return fallback;
        }
        List<String> observations = safeList(output.getIssueObservations()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        TeacherInsightOutput.IssueObservation::getPriority,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> cleanupAiText(item.getTeachingObservation()))
                .filter(value -> !value.isBlank())
                .toList();
        String summary = cleanupAiText(output.getSummary());
        if (observations.isEmpty()) {
            return defaultIfBlank(summary, fallback);
        }
        return summary + "\n" + String.join("\n", observations);
    }

    private ExternalModelStagePayloads.StageValidationResult applyAiStandardLibraryNavigation(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan == null) {
            return invalidStage("AI_NAVIGATION", ModelStageFailureReason.INVALID_JSON, "runtimePlan is missing.");
        }
        FreeDiagnosisOutput freeDiagnosis;
        try {
            freeDiagnosis = executeCoreDiagnosisStage(runtimePlan);
        } catch (Exception exception) {
            return stageFailureFromException("FREE_DIAGNOSIS", exception);
        }
        List<FreeDiagnosisOutput.Issue> issues = safeList(freeDiagnosis.getIssues());
        runtimePlan.setFreeDiagnosisOutput(freeDiagnosis);
        applyIssueLibraryAnchors(runtimePlan, issues);
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(true)
                .stage("AI_NAVIGATION")
                .failureReason(ModelStageFailureReason.NONE)
                .message("")
                .softFixes(List.of())
                .hardFailures(List.of())
                .build();
    }

    private FreeDiagnosisOutput executeCoreDiagnosisStage(ExternalModelAgentRuntime.RuntimePlan runtimePlan) throws Exception {
        return executeWorkflowStage(
                "CORE_DIAGNOSIS",
                "CORE_DIAGNOSIS",
                null,
                runtimePlan == null ? null : runtimePlan.getBrief(),
                FreeDiagnosisOutput.class,
                PromptTemplateRegistry.FREE_DIAGNOSIS_V2,
                () -> {
                    FreeDiagnosisOutput output = callFreeDiagnosisStage(runtimePlan);
                    if (output == null) {
                        throw new IllegalStateException("free diagnosis output is empty");
                    }
                    List<FreeDiagnosisOutput.Issue> issues = validatedFreeDiagnosisIssues(
                            output,
                            runtimePlan == null ? null : runtimePlan.getBrief()
                    );
                    if (issues.isEmpty()) {
                        throw new IllegalStateException("free diagnosis output contains no valid issues");
                    }
                    output.setIssues(issues);
                    return output;
                }
        );
    }

    private void applyIssueLibraryAnchors(ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                          List<FreeDiagnosisOutput.Issue> issues) {
        if (standardLibraryService == null || standardLibraryNavigationPackBuilder == null) {
            List<IssueLibraryAnchor> anchors = terminalIssueAnchors(
                    issues, "ATTACHMENT_FAILED", "STANDARD_LIBRARY_NAVIGATION_SERVICES_UNAVAILABLE");
            setLayeredAttachmentArtifacts(runtimePlan, anchors, "ATTACHMENT_FAILED",
                    "STANDARD_LIBRARY_NAVIGATION_SERVICES_UNAVAILABLE");
            return;
        }
        List<AiStandardLibraryNavigationNodeResponse> roots;
        try {
            roots = safeNavigationNodes(standardLibraryService.listRootKnowledgeAreas());
        } catch (Exception exception) {
            List<IssueLibraryAnchor> anchors = terminalIssueAnchors(
                    issues, "ATTACHMENT_FAILED", exception.getMessage());
            setLayeredAttachmentArtifacts(runtimePlan, anchors, "ATTACHMENT_FAILED", exception.getMessage());
            return;
        }
        if (roots.isEmpty()) {
            List<IssueLibraryAnchor> anchors = terminalIssueAnchors(
                    issues, "LIBRARY_EMPTY", "standard library root is empty.");
            setLayeredAttachmentArtifacts(runtimePlan, anchors, "LIBRARY_EMPTY", "standard library root is empty.");
            return;
        }
        List<FreeDiagnosisOutput.Issue> orderedIssues = safeList(issues);
        Long activeRunId = AiDiagnosisWorkflowContext.currentRunId().orElse(null);
        Consumer<ModelCallTraceEvent> activeTraceSink = MODEL_CALL_TRACE_SINK.get();
        List<CompletableFuture<IssueLibraryAnchor>> futures = orderedIssues.stream()
                .map(issue -> CompletableFuture.supplyAsync(
                        () -> withBranchContext(activeRunId, activeTraceSink,
                                () -> executeIssueAttachmentStage(runtimePlan, issue, roots)),
                        diagnosisBranchExecutor
                ))
                .toList();
        List<IssueLibraryAnchor> anchors = new ArrayList<>();
        for (int index = 0; index < futures.size(); index++) {
            FreeDiagnosisOutput.Issue issue = orderedIssues.get(index);
            try {
                anchors.add(futures.get(index).join());
            } catch (CompletionException failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                anchors.add(anchorWithStatus(issue, "ATTACHMENT_FAILED", List.of(), cause.getMessage(), null));
            }
        }
        setLayeredAttachmentArtifacts(runtimePlan, anchors, aggregateAnchorStatus(anchors), "");
    }

    private List<IssueLibraryAnchor> terminalIssueAnchors(List<FreeDiagnosisOutput.Issue> issues,
                                                          String status,
                                                          String reason) {
        List<IssueLibraryAnchor> anchors = new ArrayList<>();
        for (FreeDiagnosisOutput.Issue issue : safeList(issues)) {
            String issueId = defaultIfBlank(issue == null ? null : issue.getIssueId(), "UNKNOWN");
            try {
                anchors.add(executeWorkflowStage(
                        "ISSUE_ATTACHMENT:" + stableStageSegment(issueId),
                        "ISSUE_ATTACHMENT",
                        issueId,
                        Map.of("issue", issue, "terminalStatus", status),
                        IssueLibraryAnchor.class,
                        PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1,
                        () -> anchorWithStatus(issue, status, List.of(), reason, null)
                ));
            } catch (Exception exception) {
                anchors.add(anchorWithStatus(issue, "ATTACHMENT_FAILED", List.of(), exception.getMessage(), null));
            }
        }
        return anchors;
    }

    private IssueLibraryAnchor executeIssueAttachmentStage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            FreeDiagnosisOutput.Issue issue,
            List<AiStandardLibraryNavigationNodeResponse> roots) {
        String issueId = defaultIfBlank(issue == null ? null : issue.getIssueId(), "UNKNOWN");
        try {
            return executeWorkflowStage(
                    "ISSUE_ATTACHMENT:" + stableStageSegment(issueId),
                    "ISSUE_ATTACHMENT",
                    issueId,
                    Map.of("issue", issue, "roots", roots),
                    IssueLibraryAnchor.class,
                    PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1,
                    () -> attachIssueToStandardLibrary(runtimePlan, issue, roots)
            );
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private List<FreeDiagnosisOutput.Issue> validatedFreeDiagnosisIssues(FreeDiagnosisOutput output,
                                                                         ModelDiagnosisBrief brief) {
        List<FreeDiagnosisOutput.Issue> source = output == null ? List.of() : safeList(output.getIssues());
        if (source.isEmpty()) {
            source = issuesFromHypotheses(output);
        }
        Set<String> validRefs = EvidenceRefSupport.validEvidenceRefs(brief);
        List<String> orderedRefs = EvidenceRefSupport.orderedEvidenceRefs(brief);
        List<FreeDiagnosisOutput.Issue> validIssues = new ArrayList<>();
        int index = 1;
        for (FreeDiagnosisOutput.Issue raw : source) {
            if (raw == null) {
                continue;
            }
            List<String> evidenceRefs = EvidenceRefSupport.normalizeEvidenceRefs(
                    raw.getEvidenceRefs(),
                    validRefs,
                    orderedRefs,
                    brief,
                    null
            );
            String invalidEvidence = EvidenceRefSupport.invalidEvidenceRefs(
                    evidenceRefs,
                    validRefs,
                    brief,
                    "freeDiagnosis.issues.evidenceRefs",
                    true
            );
            if (!invalidEvidence.isBlank()) {
                evidenceRefs = fallbackFreeDiagnosisEvidenceRefs(raw, validRefs, orderedRefs);
                invalidEvidence = EvidenceRefSupport.invalidEvidenceRefs(
                        evidenceRefs,
                        validRefs,
                        brief,
                        "freeDiagnosis.issues.evidenceRefs",
                        true
                );
            }
            if (!invalidEvidence.isBlank()) {
                continue;
            }
            String title = cleanupAiText(raw.getTitle());
            if (title.isBlank()) {
                continue;
            }
            Double confidence = raw.getConfidence();
            if (confidence == null || confidence < 0 || confidence > 1
                    || confidence.isNaN() || confidence.isInfinite()) {
                confidence = 0.6;
            }
            validIssues.add(FreeDiagnosisOutput.Issue.builder()
                    .issueId(defaultIfBlank(cleanupAiText(raw.getIssueId()), "I" + index))
                    .title(title)
                    .whatHappened(defaultIfBlank(cleanupAiText(raw.getWhatHappened()), title))
                    .whyItMatters(defaultIfBlank(cleanupAiText(raw.getWhyItMatters()), cleanupAiText(raw.getWhatHappened())))
                    .evidenceRefs(evidenceRefs)
                    .severity(defaultIfBlank(cleanupAiText(raw.getSeverity()), "MAJOR"))
                    .confidence(confidence)
                    .build());
            index++;
        }
        return validIssues;
    }

    private List<String> fallbackFreeDiagnosisEvidenceRefs(FreeDiagnosisOutput.Issue raw,
                                                           Set<String> validRefs,
                                                           List<String> orderedRefs) {
        if (raw == null) {
            return raw == null || raw.getEvidenceRefs() == null ? List.of() : raw.getEvidenceRefs();
        }
        String fallback = firstUsableFreeDiagnosisEvidenceRef(validRefs, orderedRefs);
        if (fallback.isBlank()) {
            return raw.getEvidenceRefs() == null ? List.of() : raw.getEvidenceRefs();
        }
        return List.of(fallback);
    }

    private String firstUsableFreeDiagnosisEvidenceRef(Set<String> validRefs, List<String> orderedRefs) {
        if (orderedRefs == null || orderedRefs.isEmpty() || validRefs == null || validRefs.isEmpty()) {
            return "";
        }
        for (String ref : orderedRefs) {
            String cleaned = cleanupAiText(ref);
            if (!cleaned.isBlank() && validRefs.contains(cleaned)
                    && cleaned.startsWith("judge:first_failed_case")) {
                return cleaned;
            }
        }
        for (String ref : orderedRefs) {
            String cleaned = cleanupAiText(ref);
            if (!cleaned.isBlank() && validRefs.contains(cleaned)
                    && (cleaned.startsWith("code:")
                    || cleaned.startsWith("source:")
                    || cleaned.startsWith("submission:")
                    || cleaned.startsWith("verdict:"))) {
                return cleaned;
            }
        }
        return "";
    }

    private List<FreeDiagnosisOutput.Issue> issuesFromHypotheses(FreeDiagnosisOutput output) {
        if (output == null || output.getHypotheses() == null) {
            return List.of();
        }
        List<FreeDiagnosisOutput.Issue> issues = new ArrayList<>();
        int index = 1;
        for (FreeDiagnosisOutput.Hypothesis hypothesis : output.getHypotheses()) {
            if (hypothesis == null) {
                continue;
            }
            issues.add(FreeDiagnosisOutput.Issue.builder()
                    .issueId("I" + index++)
                    .title(hypothesis.getName())
                    .whatHappened(hypothesis.getReason())
                    .whyItMatters(hypothesis.getReason())
                    .evidenceRefs(hypothesis.getEvidenceRefs())
                    .severity("MAJOR")
                    .confidence(hypothesis.getConfidence())
                    .build());
        }
        return issues;
    }

    private IssueLibraryAnchor attachIssueToStandardLibrary(ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                                            FreeDiagnosisOutput.Issue issue,
                                                            List<AiStandardLibraryNavigationNodeResponse> roots) {
        List<IssueLibraryAnchor.PathNode> breadcrumb = new ArrayList<>();
        List<AiStandardLibraryNavigationNodeResponse> nodes = roots;
        List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers = List.of();
        int maxRounds = navigationMaxRounds();
        for (int round = 1; round <= maxRounds; round++) {
            if (nodes.isEmpty() && diagnosticLayers.isEmpty()) {
                return anchorWithStatus(issue, "NO_MATCH", breadcrumb, "current standard library layer is empty.", null);
            }
            LayeredAttachmentAction action;
            try {
                action = callLayeredAttachmentActionStage(
                        runtimePlan,
                        issue,
                        breadcrumb,
                        nodes,
                        diagnosticLayers,
                        round,
                        maxRounds
                );
            } catch (Exception exception) {
                return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb, exception.getMessage(), null);
            }
            if (action == null) {
                return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb, "layered attachment output is empty.", null);
            }
            String actionName = defaultIfBlank(action.getAction(), "").trim().toUpperCase();
            if ("DONE".equals(actionName)) {
                return anchorFromDiagnosticSelection(issue, breadcrumb, diagnosticLayers, action, false);
            }
            if ("NO_MATCH".equals(actionName)) {
                return anchorWithStatus(issue, "NO_MATCH", breadcrumb, action.getReason(), action.getConfidence());
            }
            if (!"SELECT".equals(actionName)) {
                return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb,
                        "invalid layered attachment action: " + action.getAction(), action.getConfidence());
            }
            List<String> codes = cleanList(action.getCodes(), List.of()).stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(2)
                    .toList();
            if (codes.isEmpty()) {
                return anchorWithStatus(issue, "NO_MATCH", breadcrumb, "SELECT action returned empty codes.",
                        action.getConfidence());
            }
            String code = codes.get(0);
            if (diagnosticCodeVisible(code, diagnosticLayers)) {
                return anchorFromDiagnosticSelection(issue, breadcrumb, diagnosticLayers, action, true);
            }
            AiStandardLibraryNavigationNodeResponse node = findNavigationNode(nodes, code);
            if (node == null) {
                return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb,
                        "ATTACHMENT_INVALID_CODE:" + code, action.getConfidence());
            }
            breadcrumb.add(pathNode(node));
            try {
                diagnosticLayers = List.of(standardLibraryService.expandDiagnosticLayer(code));
                nodes = List.of();
            } catch (IllegalArgumentException notKnowledgePoint) {
                try {
                    AiStandardLibraryNavigationExpansionResponse expansion =
                            standardLibraryService.expandKnowledgeNode(code, 0, 50);
                    nodes = safeNavigationNodes(expansion == null ? null : expansion.getChildren());
                    diagnosticLayers = List.of();
                } catch (Exception exception) {
                    return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb, exception.getMessage(),
                            action.getConfidence());
                }
            } catch (Exception exception) {
                return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb, exception.getMessage(),
                        action.getConfidence());
            }
        }
        if (!breadcrumb.isEmpty()) {
            return anchorWithStatus(issue, "PARTIAL", breadcrumb, "AI_NAVIGATION_ROUND_LIMIT_REACHED", null);
        }
        return anchorWithStatus(issue, "ATTACHMENT_FAILED", breadcrumb, "AI_NAVIGATION_ROUND_LIMIT_REACHED", null);
    }

    private LayeredAttachmentAction callLayeredAttachmentActionStage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            FreeDiagnosisOutput.Issue issue,
            List<IssueLibraryAnchor.PathNode> breadcrumb,
            List<AiStandardLibraryNavigationNodeResponse> nodes,
            List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers,
            int round,
            int maxRounds) throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "为当前 issue 从当前可见标准库目录中选择下一层，或结束挂接。");
        request.put("issue", issue);
        request.put("breadcrumb", breadcrumb);
        request.put("currentLayer", Map.of(
                "round", round,
                "maxRounds", maxRounds,
                "nodes", currentLayerNodes(nodes),
                "diagnosticItems", currentDiagnosticItems(diagnosticLayers)
        ));
        request.put("allowedActions", List.of("SELECT", "DONE", "NO_MATCH"));
        activateRuntimePlan(runtimePlan);
        String systemPrompt = runtimePlan.getStandardLibraryNavigationPrompt().getSystemPrompt();
        String userPrompt = objectMapper.writeValueAsString(request);
        String content = chatCompletion(systemPrompt, userPrompt);
        LayeredAttachmentAction output = parseModelStagePayload(content, LayeredAttachmentAction.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(systemPrompt, userPrompt, runtimePlan, LayeredAttachmentAction.class);
    }

    private List<Map<String, Object>> currentLayerNodes(List<AiStandardLibraryNavigationNodeResponse> nodes) {
        return safeNavigationNodes(nodes).stream()
                .map(node -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("code", node.getCode());
                    value.put("name", node.getName());
                    value.put("type", node.getType());
                    value.put("description", node.getDescription());
                    return value;
                })
                .toList();
    }

    private List<Map<String, Object>> currentDiagnosticItems(
            List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiStandardLibraryDiagnosticLayerResponse layer : safeDiagnosticLayers(diagnosticLayers)) {
            addDiagnosticItem(items, pathNode(layer.getKnowledgePoint()), "KNOWLEDGE_POINT", null);
            for (AiStandardLibraryDiagnosticLayerResponse.SkillUnit skill : safeList(layer.getSkillUnits())) {
                addDiagnosticItem(items, skill.getCode(), skill.getName(), "SKILL_UNIT", skill.getDescription());
                for (AiStandardLibraryDiagnosticLayerResponse.MistakePoint mistake : safeList(skill.getMistakePoints())) {
                    addDiagnosticItem(items, mistake.getCode(), mistake.getName(), "MISTAKE_POINT",
                            mistake.getDescription());
                }
                for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement :
                        safeList(skill.getImprovementPoints())) {
                    addDiagnosticItem(items, improvement.getCode(), improvement.getName(), "IMPROVEMENT_POINT",
                            improvement.getDescription());
                }
            }
            for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement :
                    safeList(layer.getDirectImprovementPoints())) {
                addDiagnosticItem(items, improvement.getCode(), improvement.getName(), "IMPROVEMENT_POINT",
                        improvement.getDescription());
            }
            for (AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate provisional :
                    safeList(layer.getProvisionalCandidates())) {
                addDiagnosticItem(items,
                        provisional.getCode(),
                        provisional.getName(),
                        "PROVISIONAL_" + defaultIfBlank(provisional.getLayer(), "MISTAKE_POINT"),
                        provisional.getDescription());
            }
        }
        return items;
    }

    private void addDiagnosticItem(List<Map<String, Object>> items,
                                   IssueLibraryAnchor.PathNode node,
                                   String type,
                                   String description) {
        if (node == null) {
            return;
        }
        addDiagnosticItem(items, node.getCode(), node.getName(), type, description);
    }

    private void addDiagnosticItem(List<Map<String, Object>> items,
                                   String code,
                                   String name,
                                   String type,
                                   String description) {
        String normalizedCode = defaultIfBlank(code, "").trim();
        if (normalizedCode.isBlank()) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", normalizedCode);
        item.put("name", defaultIfBlank(name, normalizedCode));
        item.put("type", type);
        item.put("description", defaultIfBlank(description, ""));
        items.add(item);
    }

    private IssueLibraryAnchor anchorFromDiagnosticSelection(FreeDiagnosisOutput.Issue issue,
                                                            List<IssueLibraryAnchor.PathNode> breadcrumb,
                                                            List<AiStandardLibraryDiagnosticLayerResponse> layers,
                                                            LayeredAttachmentAction action,
                                                            boolean selectedDiagnosticCode) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(cleanList(action == null ? null : action.getCodes(), List.of()));
        String skillUnitCode = "";
        String mistakePointCode = "";
        String improvementPointCode = "";
        AiStandardLibraryDiagnosticLayerResponse.SkillUnit selectedSkill = null;
        AiStandardLibraryDiagnosticLayerResponse.MistakePoint selectedMistake = null;
        AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint selectedImprovement = null;
        AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate selectedProvisional = null;
        List<IssueLibraryAnchor.PathNode> path = new ArrayList<>(breadcrumb);
        for (AiStandardLibraryDiagnosticLayerResponse layer : safeDiagnosticLayers(layers)) {
            IssueLibraryAnchor.PathNode knowledgePoint = pathNode(layer.getKnowledgePoint());
            if (knowledgePoint != null && path.stream().noneMatch(item -> knowledgePoint.getCode().equals(item.getCode()))) {
                path.add(knowledgePoint);
            }
            for (AiStandardLibraryDiagnosticLayerResponse.SkillUnit skill : safeList(layer.getSkillUnits())) {
                if (codes.contains(skill.getCode())) {
                    skillUnitCode = skill.getCode();
                    selectedSkill = skill;
                }
                for (AiStandardLibraryDiagnosticLayerResponse.MistakePoint mistake : safeList(skill.getMistakePoints())) {
                    if (codes.contains(mistake.getCode())) {
                        mistakePointCode = mistake.getCode();
                        skillUnitCode = defaultIfBlank(skillUnitCode, skill.getCode());
                        selectedSkill = skill;
                        selectedMistake = mistake;
                    }
                }
                for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement :
                        safeList(skill.getImprovementPoints())) {
                    if (codes.contains(improvement.getCode())) {
                        improvementPointCode = improvement.getCode();
                        skillUnitCode = defaultIfBlank(skillUnitCode, skill.getCode());
                        selectedSkill = skill;
                        selectedImprovement = improvement;
                    }
                }
            }
            for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement :
                    safeList(layer.getDirectImprovementPoints())) {
                if (codes.contains(improvement.getCode())) {
                    improvementPointCode = improvement.getCode();
                    selectedImprovement = improvement;
                }
            }
            for (AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate provisional :
                    safeList(layer.getProvisionalCandidates())) {
                if (codes.contains(provisional.getCode())) {
                    selectedProvisional = provisional;
                }
            }
        }
        boolean hasFormalAnchor = !skillUnitCode.isBlank() || !mistakePointCode.isBlank() || !improvementPointCode.isBlank();
        addPathNodeIfMissing(path,
                selectedSkill == null ? null : selectedSkill.getCode(),
                selectedSkill == null ? null : selectedSkill.getName(),
                "SKILL_UNIT");
        addPathNodeIfMissing(path,
                selectedMistake == null ? null : selectedMistake.getCode(),
                selectedMistake == null ? null : selectedMistake.getName(),
                "MISTAKE_POINT");
        addPathNodeIfMissing(path,
                selectedImprovement == null ? null : selectedImprovement.getCode(),
                selectedImprovement == null ? null : selectedImprovement.getName(),
                "IMPROVEMENT_POINT");
        AiStandardLibraryDiagnosticLayerResponse.ProvisionalCandidate provisionalSelection = selectedProvisional;
        if (provisionalSelection != null && path.stream()
                .noneMatch(item -> provisionalSelection.getCode().equals(item.getCode()))) {
            path.add(IssueLibraryAnchor.PathNode.builder()
                    .code(provisionalSelection.getCode())
                    .name(provisionalSelection.getName())
                    .type("PROVISIONAL_" + provisionalSelection.getLayer())
                    .build());
        }
        return IssueLibraryAnchor.builder()
                .issueId(issue.getIssueId())
                .anchorStatus(provisionalSelection != null ? "PROVISIONAL" : hasFormalAnchor ? "HIT" : "PARTIAL")
                .path(path)
                .skillUnitCode(blankToNull(skillUnitCode))
                .mistakePointCode(blankToNull(mistakePointCode))
                .improvementPointCode(blankToNull(improvementPointCode))
                .provisionalCandidateCode(provisionalSelection == null ? null : provisionalSelection.getCode())
                .provisionalCandidateName(provisionalSelection == null ? null : provisionalSelection.getName())
                .provisionalCandidateLayer(provisionalSelection == null ? null : provisionalSelection.getLayer())
                .reason(defaultIfBlank(action == null ? "" : action.getReason(), "standard library attachment completed."))
                .confidence(action == null ? null : action.getConfidence())
                .build();
    }

    private void addPathNodeIfMissing(List<IssueLibraryAnchor.PathNode> path,
                                      String code,
                                      String name,
                                      String type) {
        String normalizedCode = defaultIfBlank(code, "").trim();
        if (normalizedCode.isBlank() || path.stream()
                .anyMatch(item -> normalizedCode.equals(defaultIfBlank(item.getCode(), "").trim()))) {
            return;
        }
        path.add(IssueLibraryAnchor.PathNode.builder()
                .code(normalizedCode)
                .name(defaultIfBlank(name, normalizedCode))
                .type(type)
                .build());
    }

    private boolean diagnosticCodeVisible(String code, List<AiStandardLibraryDiagnosticLayerResponse> layers) {
        String normalized = defaultIfBlank(code, "").trim();
        if (normalized.isBlank()) {
            return false;
        }
        return currentDiagnosticItems(layers).stream()
                .map(item -> defaultIfBlank((String) item.get("code"), ""))
                .anyMatch(normalized::equals);
    }

    private AiStandardLibraryNavigationNodeResponse findNavigationNode(
            List<AiStandardLibraryNavigationNodeResponse> nodes,
            String code) {
        String normalized = defaultIfBlank(code, "").trim();
        return safeNavigationNodes(nodes).stream()
                .filter(node -> normalized.equals(defaultIfBlank(node.getCode(), "").trim()))
                .findFirst()
                .orElse(null);
    }

    private List<IssueLibraryAnchor> anchorsWithStatus(List<FreeDiagnosisOutput.Issue> issues,
                                                       String status,
                                                       String reason) {
        return safeList(issues).stream()
                .map(issue -> anchorWithStatus(issue, status, List.of(), reason, null))
                .toList();
    }

    private IssueLibraryAnchor anchorWithStatus(FreeDiagnosisOutput.Issue issue,
                                                String status,
                                                List<IssueLibraryAnchor.PathNode> breadcrumb,
                                                String reason,
                                                Double confidence) {
        return IssueLibraryAnchor.builder()
                .issueId(issue == null ? "" : issue.getIssueId())
                .anchorStatus(status)
                .path(breadcrumb == null ? List.of() : List.copyOf(breadcrumb))
                .reason(defaultIfBlank(reason, status))
                .confidence(confidence)
                .build();
    }

    private void setLayeredAttachmentArtifacts(ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                               List<IssueLibraryAnchor> anchors,
                                               String status,
                                               String failureReason) {
        StandardLibraryNavigationOutput output = navigationOutputFromAnchors(anchors);
        StandardLibraryPack pack = packForNavigationOutput(output, status, failureReason);
        runtimePlan.setIssueLibraryAnchors(anchors);
        runtimePlan.setStandardLibraryNavigationOutput(output);
        runtimePlan.setStandardLibraryPack(pack);
        runtimePlan.setStandardLibraryNavigationResult(StandardLibraryNavigationResult.builder()
                .enabled(true)
                .status(status)
                .failureReason(defaultIfBlank(failureReason, ""))
                .selectedCount(navigationSelectedCount(output))
                .selectedPack(pack)
                .output(output)
                .build());
    }

    private StandardLibraryNavigationOutput navigationOutputFromAnchors(List<IssueLibraryAnchor> anchors) {
        List<StandardLibraryNavigationOutput.SelectedPath> selectedPaths = safeList(anchors).stream()
                .filter(anchor -> List.of("HIT", "PARTIAL", "PROVISIONAL")
                        .contains(defaultIfBlank(anchor.getAnchorStatus(), "")))
                .map(anchor -> StandardLibraryNavigationOutput.SelectedPath.builder()
                        .knowledgeNodeCode(lastKnowledgePointCode(anchor.getPath()))
                        .skillUnitCode(anchor.getSkillUnitCode())
                        .mistakePointCode(anchor.getMistakePointCode())
                        .improvementPointCode(anchor.getImprovementPointCode())
                        .libraryFit("HIT".equals(anchor.getAnchorStatus()) ? "HIT" : "PARTIAL")
                        .reason(anchor.getReason())
                        .evidenceRefs(List.of())
                        .confidence(anchor.getConfidence())
                        .build())
                .toList();
        List<StandardLibraryNavigationOutput.UnresolvedGap> gaps = safeList(anchors).stream()
                .filter(anchor -> !List.of("HIT", "PARTIAL", "PROVISIONAL")
                        .contains(defaultIfBlank(anchor.getAnchorStatus(), "")))
                .map(anchor -> StandardLibraryNavigationOutput.UnresolvedGap.builder()
                        .name(defaultIfBlank(anchor.getIssueId(), "ISSUE"))
                        .reason(anchor.getAnchorStatus() + ":" + defaultIfBlank(anchor.getReason(), ""))
                        .suggestedPath(List.of())
                        .evidenceRefs(List.of())
                        .confidence(anchor.getConfidence())
                        .build())
                .toList();
        return StandardLibraryNavigationOutput.builder()
                .status(selectedPaths.isEmpty() ? "NO_MATCH" : "DONE")
                .selectedBranches(List.of())
                .selectedPaths(selectedPaths)
                .unresolvedGaps(gaps)
                .uncertainty(anchorSummary(anchors))
                .build();
    }

    private String lastKnowledgePointCode(List<IssueLibraryAnchor.PathNode> path) {
        IssueLibraryAnchor.PathNode knowledgePoint = lastKnowledgePoint(path);
        return knowledgePoint == null ? lastPathCode(path) : knowledgePoint.getCode();
    }

    private StandardLibraryPack packForNavigationOutput(StandardLibraryNavigationOutput output,
                                                        String status,
                                                        String failureReason) {
        StandardLibraryPack pack;
        try {
            pack = standardLibraryNavigationPackBuilder == null
                    ? emptyStandardLibraryPack(status, failureReason, output == null ? "" : output.getUncertainty())
                    : standardLibraryNavigationPackBuilder.build(output);
        } catch (Exception exception) {
            pack = emptyStandardLibraryPack("ATTACHMENT_FAILED", exception.getMessage(),
                    output == null ? "" : output.getUncertainty());
            status = "ATTACHMENT_FAILED";
            failureReason = exception.getMessage();
        }
        if (pack.getStandardLibraryNavigationSummary() == null) {
            pack.setStandardLibraryNavigationSummary(StandardLibraryPack.StandardLibraryNavigationSummary.builder()
                    .build());
        }
        pack.getStandardLibraryNavigationSummary().setStatus(status);
        pack.getStandardLibraryNavigationSummary().setFailureReason(defaultIfBlank(failureReason, ""));
        pack.getStandardLibraryNavigationSummary().setSelectedCount(navigationSelectedCount(output));
        pack.getStandardLibraryNavigationSummary().setUncertainty(output == null ? "" : output.getUncertainty());
        return pack;
    }

    private StandardLibraryPack emptyStandardLibraryPack(String status, String failureReason, String uncertainty) {
        return StandardLibraryPack.builder()
                .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                .structureVersion(StandardLibraryPack.STRUCTURE_VERSION)
                .knowledgeGroups(List.of())
                .basicCauses(List.of())
                .improvementPoints(List.of())
                .knowledgeAnchors(List.of())
                .skillUnits(List.of())
                .mistakePoints(List.of())
                .applicationScenarios(List.of())
                .standardLibraryNavigationSummary(StandardLibraryPack.StandardLibraryNavigationSummary.builder()
                        .status(status)
                        .failureReason(defaultIfBlank(failureReason, ""))
                        .selectedCount(0)
                        .uncertainty(defaultIfBlank(uncertainty, ""))
                        .build())
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .improvementTags(List.of())
                .teachingActions(List.of())
                .build();
    }

    private String aggregateAnchorStatus(List<IssueLibraryAnchor> anchors) {
        if (safeList(anchors).stream().anyMatch(anchor ->
                List.of("HIT", "PARTIAL").contains(defaultIfBlank(anchor.getAnchorStatus(), "")))) {
            return "LAYERED_ATTACHMENT";
        }
        if (!safeList(anchors).isEmpty() && safeList(anchors).stream()
                .allMatch(anchor -> "LIBRARY_EMPTY".equals(anchor.getAnchorStatus()))) {
            return "LIBRARY_EMPTY";
        }
        if (!safeList(anchors).isEmpty() && safeList(anchors).stream()
                .allMatch(anchor -> "NO_MATCH".equals(anchor.getAnchorStatus()))) {
            return "NO_MATCH";
        }
        return "ATTACHMENT_FAILED";
    }

    private String anchorSummary(List<IssueLibraryAnchor> anchors) {
        return safeList(anchors).stream()
                .map(anchor -> defaultIfBlank(anchor.getIssueId(), "ISSUE")
                        + "=" + defaultIfBlank(anchor.getAnchorStatus(), "UNKNOWN"))
                .toList()
                .toString();
    }

    private String lastPathCode(List<IssueLibraryAnchor.PathNode> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path.get(path.size() - 1).getCode();
    }

    private IssueLibraryAnchor.PathNode pathNode(AiStandardLibraryNavigationNodeResponse node) {
        if (node == null || defaultIfBlank(node.getCode(), "").isBlank()) {
            return null;
        }
        return IssueLibraryAnchor.PathNode.builder()
                .code(node.getCode())
                .name(node.getName())
                .type(node.getType())
                .build();
    }

    private String blankToNull(String value) {
        String normalized = defaultIfBlank(value, "").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private FreeDiagnosisOutput callFreeDiagnosisStage(ExternalModelAgentRuntime.RuntimePlan runtimePlan)
            throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "先独立理解题目、完整代码和判题事实，生成不含标准库 ID 的初步诊断。");
        request.put("brief", runtimePlan.getBrief());
        activateRuntimePlan(runtimePlan);
        String systemPrompt = runtimePlan.getFreeDiagnosisPrompt().getSystemPrompt();
        String userPrompt = objectMapper.writeValueAsString(request);
        String content = chatCompletion(systemPrompt, userPrompt);
        FreeDiagnosisOutput output = parseModelStagePayload(content, FreeDiagnosisOutput.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(systemPrompt, userPrompt, runtimePlan, FreeDiagnosisOutput.class);
    }

    private NavigationLoopResult callStandardLibraryNavigationLoop(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            FreeDiagnosisOutput freeDiagnosis)
            throws JsonProcessingException, IOException, InterruptedException {
        List<AiStandardLibraryNavigationNodeResponse> roots = safeNavigationNodes(standardLibraryService.listRootKnowledgeAreas());
        List<AiStandardLibraryNavigationExpansionResponse> expandedNodes = List.of();
        List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers = List.of();
        int maxRounds = navigationMaxRounds();
        StandardLibraryNavigationOutput latest = null;
        for (int round = 1; round <= maxRounds; round++) {
            List<AiStandardLibraryNavigationNodeResponse> visibleRoots = round == 1 ? roots : List.of();
            Map<String, Object> navigationView = navigationView(
                    round,
                    maxRounds,
                    visibleRoots,
                    expandedNodes,
                    diagnosticLayers,
                    null
            );
            latest = callStandardLibraryNavigationStage(runtimePlan, freeDiagnosis, navigationView);
            if (latest == null) {
                return NavigationLoopResult.empty();
            }
            String status = defaultIfBlank(latest.getStatus(), "").trim().toUpperCase();
            if ("DONE".equals(status) || "NO_MATCH".equals(status)) {
                return NavigationLoopResult.success(latest);
            }
            if (!"CONTINUE".equals(status)) {
                return NavigationLoopResult.success(latest);
            }
            if (round >= maxRounds) {
                return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                        ModelStageFailureReason.INVALID_JSON,
                        "AI_NAVIGATION_ROUND_LIMIT_REACHED"));
            }
            List<StandardLibraryNavigationOutput.SelectedBranch> branches = selectedNavigationBranches(latest);
            if (branches.isEmpty()) {
                return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                        ModelStageFailureReason.INVALID_JSON,
                        "CONTINUE navigation requires selectedBranches."));
            }
            Set<String> visibleKnowledgeCodes = visibleKnowledgeNodeCodes(visibleRoots, expandedNodes);
            List<String> invalidCodes = invalidSelectedBranchCodes(branches, visibleKnowledgeCodes);
            if (!invalidCodes.isEmpty()) {
                latest = repairInvalidNavigationBranches(
                        runtimePlan,
                        freeDiagnosis,
                        navigationView,
                        latest,
                        invalidCodes);
                if (latest == null) {
                    return NavigationLoopResult.empty();
                }
                status = defaultIfBlank(latest.getStatus(), "").trim().toUpperCase();
                if ("DONE".equals(status) || "NO_MATCH".equals(status)) {
                    return NavigationLoopResult.success(latest);
                }
                if (!"CONTINUE".equals(status)) {
                    return NavigationLoopResult.success(latest);
                }
                branches = selectedNavigationBranches(latest);
                invalidCodes = invalidSelectedBranchCodes(branches, visibleKnowledgeCodes);
                if (branches.isEmpty()) {
                    return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                            ModelStageFailureReason.INVALID_JSON,
                            "CONTINUE navigation repair requires selectedBranches."));
                }
                if (!invalidCodes.isEmpty()) {
                    return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                            ModelStageFailureReason.INVALID_TAG,
                            "selected branch knowledgeNodeCode is not visible in current navigation view: "
                                    + String.join(", ", invalidCodes)));
                }
            }
            NavigationExpansionBatch next = expandSelectedNavigationBranches(branches);
            if (next.expandedNodes().isEmpty() && next.diagnosticLayers().isEmpty()) {
                return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                        ModelStageFailureReason.INVALID_TAG,
                        "selected branches did not expand to knowledge nodes or diagnostic layers."));
            }
            expandedNodes = next.expandedNodes();
            diagnosticLayers = next.diagnosticLayers();
        }
        return NavigationLoopResult.failure(invalidStage("STANDARD_LIBRARY_NAVIGATION",
                ModelStageFailureReason.INVALID_JSON,
                "AI_NAVIGATION_ROUND_LIMIT_REACHED"));
    }

    private StandardLibraryNavigationOutput callStandardLibraryNavigationStage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            FreeDiagnosisOutput freeDiagnosis,
            Map<String, Object> navigationView)
            throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "按当前导航视图选择要继续展开的标准库分支，或返回最终标准库路径。");
        request.put("brief", runtimePlan.getBrief());
        request.put("freeDiagnosis", freeDiagnosis);
        request.put("navigationView", navigationView);
        activateRuntimePlan(runtimePlan);
        String systemPrompt = runtimePlan.getStandardLibraryNavigationPrompt().getSystemPrompt();
        String userPrompt = objectMapper.writeValueAsString(request);
        String content = chatCompletion(systemPrompt, userPrompt);
        StandardLibraryNavigationOutput output = parseModelStagePayload(content, StandardLibraryNavigationOutput.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(systemPrompt, userPrompt, runtimePlan,
                StandardLibraryNavigationOutput.class);
    }

    private Map<String, Object> navigationView(
            int round,
            int maxRounds,
            List<AiStandardLibraryNavigationNodeResponse> roots,
            List<AiStandardLibraryNavigationExpansionResponse> expandedNodes,
            List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers,
            Map<String, Object> repair) {
        Map<String, Object> navigationView = new LinkedHashMap<>();
        boolean mustFinishNow = round >= maxRounds;
        navigationView.put("round", round);
        navigationView.put("roots", roots);
        navigationView.put("expandedNode", firstOrNull(expandedNodes));
        navigationView.put("diagnosticLayer", firstOrNull(diagnosticLayers));
        navigationView.put("expandedNodes", expandedNodes);
        navigationView.put("diagnosticLayers", diagnosticLayers);
        navigationView.put("visibleKnowledgeNodeCodes", visibleKnowledgeNodeCodes(roots, expandedNodes));
        navigationView.put("visibleDiagnosticCodes", visibleDiagnosticCodes(diagnosticLayers));
        navigationView.put("mustFinishNow", mustFinishNow);
        navigationView.put("navigationInstruction", mustFinishNow
                ? "这是本次允许的最后一轮，必须返回 DONE 或 NO_MATCH，不要返回 CONTINUE。"
                : "如果需要继续展开，只能从 visibleKnowledgeNodeCodes 中选择 selectedBranches；如果已经看到 diagnosticLayers，优先返回 DONE 和 selectedPaths。");
        navigationView.put("maxRounds", maxRounds);
        navigationView.put("maxBranchesPerRound", STANDARD_LIBRARY_NAVIGATION_MAX_BRANCHES);
        navigationView.put("maxFinalAnchors", 12);
        if (repair != null && !repair.isEmpty()) {
            navigationView.put("repair", repair);
        }
        return navigationView;
    }

    private StandardLibraryNavigationOutput repairInvalidNavigationBranches(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            FreeDiagnosisOutput freeDiagnosis,
            Map<String, Object> previousNavigationView,
            StandardLibraryNavigationOutput invalidOutput,
            List<String> invalidCodes)
            throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> repair = new LinkedHashMap<>();
        repair.put("reason", "INVALID_BRANCH_CODE");
        repair.put("invalidKnowledgeNodeCodes", invalidCodes);
        repair.put("previousOutput", invalidOutput);
        repair.put("instruction", "上一轮 selectedBranches 引用了当前视图未出现的知识节点 code。请只从 visibleKnowledgeNodeCodes 中重选，或返回 NO_MATCH。");
        Map<String, Object> repairedView = new LinkedHashMap<>(previousNavigationView);
        repairedView.put("repair", repair);
        repairedView.put("navigationInstruction",
                "修正上一轮非法分支：只能从 visibleKnowledgeNodeCodes 中选择 selectedBranches；如果没有合适分支，返回 NO_MATCH。");
        return callStandardLibraryNavigationStage(runtimePlan, freeDiagnosis, repairedView);
    }

    private NavigationExpansionBatch expandSelectedNavigationBranches(
            List<StandardLibraryNavigationOutput.SelectedBranch> branches) {
        List<AiStandardLibraryNavigationExpansionResponse> expandedNodes = new ArrayList<>();
        List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers = new ArrayList<>();
        for (StandardLibraryNavigationOutput.SelectedBranch branch : branches) {
            String code = defaultIfBlank(branch.getKnowledgeNodeCode(), "").trim();
            if (code.isBlank()) {
                continue;
            }
            try {
                diagnosticLayers.add(standardLibraryService.expandDiagnosticLayer(code));
            } catch (IllegalArgumentException exception) {
                try {
                    expandedNodes.add(standardLibraryService.expandKnowledgeNode(code, 0, 50));
                } catch (IllegalArgumentException nested) {
                    throw new IllegalArgumentException("selected branch cannot be expanded: " + code, nested);
                }
            }
        }
        return new NavigationExpansionBatch(expandedNodes, diagnosticLayers);
    }

    private List<StandardLibraryNavigationOutput.SelectedBranch> selectedNavigationBranches(
            StandardLibraryNavigationOutput output) {
        if (output == null || output.getSelectedBranches() == null) {
            return List.of();
        }
        LinkedHashMap<String, StandardLibraryNavigationOutput.SelectedBranch> branches = new LinkedHashMap<>();
        for (StandardLibraryNavigationOutput.SelectedBranch branch : output.getSelectedBranches()) {
            if (branch == null || defaultIfBlank(branch.getKnowledgeNodeCode(), "").isBlank()) {
                continue;
            }
            String code = branch.getKnowledgeNodeCode().trim();
            branches.putIfAbsent(code, branch);
            if (branches.size() >= STANDARD_LIBRARY_NAVIGATION_MAX_BRANCHES) {
                break;
            }
        }
        return branches.values().stream().toList();
    }

    private List<String> invalidSelectedBranchCodes(
            List<StandardLibraryNavigationOutput.SelectedBranch> branches,
            Set<String> visibleKnowledgeCodes) {
        if (branches == null || branches.isEmpty()) {
            return List.of();
        }
        Set<String> visible = visibleKnowledgeCodes == null ? Set.of() : visibleKnowledgeCodes;
        return branches.stream()
                .map(StandardLibraryNavigationOutput.SelectedBranch::getKnowledgeNodeCode)
                .map(code -> defaultIfBlank(code, "").trim())
                .filter(code -> !code.isBlank())
                .filter(code -> !visible.contains(code))
                .distinct()
                .toList();
    }

    private Set<String> visibleKnowledgeNodeCodes(
            List<AiStandardLibraryNavigationNodeResponse> roots,
            List<AiStandardLibraryNavigationExpansionResponse> expandedNodes) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        safeNavigationNodes(roots).forEach(node -> addNavigationNodeCode(codes, node));
        safeNavigationExpansions(expandedNodes).forEach(expansion ->
                safeNavigationNodes(expansion.getChildren()).forEach(node -> addNavigationNodeCode(codes, node)));
        return codes;
    }

    private Map<String, Object> visibleDiagnosticCodes(
            List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers) {
        LinkedHashSet<String> knowledgeNodeCodes = new LinkedHashSet<>();
        LinkedHashSet<String> skillUnitCodes = new LinkedHashSet<>();
        LinkedHashSet<String> mistakePointCodes = new LinkedHashSet<>();
        LinkedHashSet<String> improvementPointCodes = new LinkedHashSet<>();
        for (AiStandardLibraryDiagnosticLayerResponse layer : safeDiagnosticLayers(diagnosticLayers)) {
            addNavigationNodeCode(knowledgeNodeCodes, layer.getKnowledgePoint());
            for (AiStandardLibraryDiagnosticLayerResponse.SkillUnit skill : safeList(layer.getSkillUnits())) {
                addIfNotBlank(skillUnitCodes, skill.getCode());
                for (AiStandardLibraryDiagnosticLayerResponse.MistakePoint mistake : safeList(skill.getMistakePoints())) {
                    addIfNotBlank(mistakePointCodes, mistake.getCode());
                }
                for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement : safeList(skill.getImprovementPoints())) {
                    addIfNotBlank(improvementPointCodes, improvement.getCode());
                }
            }
            for (AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint improvement :
                    safeList(layer.getDirectImprovementPoints())) {
                addIfNotBlank(improvementPointCodes, improvement.getCode());
            }
        }
        Map<String, Object> codes = new LinkedHashMap<>();
        codes.put("knowledgeNodeCodes", knowledgeNodeCodes.stream().toList());
        codes.put("skillUnitCodes", skillUnitCodes.stream().toList());
        codes.put("mistakePointCodes", mistakePointCodes.stream().toList());
        codes.put("improvementPointCodes", improvementPointCodes.stream().toList());
        return codes;
    }

    private int navigationMaxRounds() {
        return Math.max(1, Math.min(Math.max(standardLibraryNavigationMaxRounds, 0), 10));
    }

    private <T> T firstOrNull(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private List<AiStandardLibraryNavigationNodeResponse> safeNavigationNodes(
            List<AiStandardLibraryNavigationNodeResponse> values) {
        return values == null ? List.of() : values;
    }

    private List<AiStandardLibraryNavigationExpansionResponse> safeNavigationExpansions(
            List<AiStandardLibraryNavigationExpansionResponse> values) {
        return values == null ? List.of() : values;
    }

    private List<AiStandardLibraryDiagnosticLayerResponse> safeDiagnosticLayers(
            List<AiStandardLibraryDiagnosticLayerResponse> values) {
        return values == null ? List.of() : values;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void addNavigationNodeCode(Set<String> codes, AiStandardLibraryNavigationNodeResponse node) {
        if (node != null) {
            addIfNotBlank(codes, node.getCode());
        }
    }

    private void addIfNotBlank(Set<String> values, String value) {
        String normalized = defaultIfBlank(value, "").trim();
        if (!normalized.isBlank()) {
            values.add(normalized);
        }
    }

    private StandardLibraryNavigationOutput.SelectedBranch firstNavigationBranch(
            StandardLibraryNavigationOutput output) {
        if (output == null || output.getSelectedBranches() == null) {
            return null;
        }
        return output.getSelectedBranches().stream()
                .filter(item -> item != null && !defaultIfBlank(item.getKnowledgeNodeCode(), "").isBlank())
                .findFirst()
                .orElse(null);
    }

    private int navigationSelectedCount(StandardLibraryNavigationOutput output) {
        if (output == null) {
            return 0;
        }
        return size(output.getSelectedBranches()) + size(output.getSelectedPaths()) + size(output.getUnresolvedGaps());
    }

    private ExternalModelStagePayloads.StageValidationResult invalidStage(String stage,
                                                                          ModelStageFailureReason reason,
                                                                          String message) {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .stage(stage)
                .failureReason(reason)
                .message(message)
                .softFixes(List.of())
                .hardFailures(List.of(message))
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult validStage(String stage) {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(true)
                .stage(stage)
                .failureReason(ModelStageFailureReason.NONE)
                .message("")
                .softFixes(List.of())
                .hardFailures(List.of())
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult validateAdviceGenerationWithCoverageContract(
            AdviceGenerationOutput adviceOutput,
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        ExternalModelStagePayloads.StageValidationResult coverageValidation =
                validateAdviceCoverageContract(adviceOutput, runtimePlan);
        if (!coverageValidation.isValid()) {
            return coverageValidation;
        }
        ExternalModelStagePayloads.StageValidationResult schemaValidation =
                withStage("DIAGNOSIS_AND_ADVICE",
                        externalModelAgentRuntime.validateAdviceGeneration(adviceOutput, runtimePlan));
        if (!schemaValidation.isValid()) {
            return schemaValidation;
        }
        return schemaValidation;
    }

    private ExternalModelStagePayloads.StageValidationResult validateAdviceCoverageContract(
            AdviceGenerationOutput adviceOutput,
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (adviceOutput == null) {
            return validStage("DIAGNOSIS_AND_ADVICE");
        }
        if (isAccepted(runtimePlan == null ? null : runtimePlan.getBrief())) {
            return validStage("DIAGNOSIS_AND_ADVICE");
        }
        List<String> requiredIssueIds = requiredBasicAdviceIssueIds(runtimePlan);
        Set<String> coveredIssueIds = safeList(adviceOutput.getBasicLayerAdvice()).stream()
                .filter(Objects::nonNull)
                .map(AdviceGenerationOutput.BasicLayerAdvice::getIssueId)
                .map(this::cleanupAiText)
                .filter(issueId -> !issueId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missingIssueIds = requiredIssueIds.stream()
                .filter(issueId -> !coveredIssueIds.contains(issueId))
                .toList();
        if (missingIssueIds.isEmpty()) {
            return validStage("DIAGNOSIS_AND_ADVICE");
        }
        return invalidStage(
                "DIAGNOSIS_AND_ADVICE",
                ModelStageFailureReason.INVALID_JSON,
                "ADVICE_MISSING_ISSUE_COVERAGE: requiredIssueIds=" + requiredIssueIds
                        + ", coveredIssueIds=" + coveredIssueIds
                        + ", missingIssueIds=" + missingIssueIds
                        + ", improvementAdviceCount=" + size(adviceOutput.getImprovementLayerAdvice())
        );
    }

    private boolean isAdviceCoverageFailure(ExternalModelStagePayloads.StageValidationResult failure) {
        return failure != null
                && cleanupAiText(failure.getMessage()).startsWith("ADVICE_MISSING_ISSUE_COVERAGE:");
    }

    private List<FreeDiagnosisOutput.Issue> effectiveFreeDiagnosisIssues(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan == null || runtimePlan.getFreeDiagnosisOutput() == null) {
            return List.of();
        }
        return safeList(runtimePlan.getFreeDiagnosisOutput().getIssues()).stream()
                .filter(item -> item != null)
                .filter(item -> !defaultIfBlank(item.getTitle(), "").isBlank()
                        || !defaultIfBlank(item.getWhatHappened(), "").isBlank())
                .toList();
    }

    private List<String> requiredBasicAdviceIssueIds(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        return effectiveFreeDiagnosisIssues(runtimePlan).stream()
                .filter(issue -> {
                    String severity = defaultIfBlank(issue.getSeverity(), "MAJOR").trim().toUpperCase();
                    return !List.of("MINOR", "LOW", "INFO", "INFORMATIONAL").contains(severity);
                })
                .map(FreeDiagnosisOutput.Issue::getIssueId)
                .map(this::cleanupAiText)
                .filter(issueId -> !issueId.isBlank())
                .distinct()
                .toList();
    }

    private void linkAdviceToFreeDiagnosisIssues(AdviceGenerationOutput output,
                                                 ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (output == null) {
            return;
        }
        List<String> issueIds = effectiveFreeDiagnosisIssues(runtimePlan).stream()
                .map(FreeDiagnosisOutput.Issue::getIssueId)
                .map(this::cleanupAiText)
                .filter(issueId -> !issueId.isBlank())
                .distinct()
                .toList();
        if (issueIds.isEmpty()) {
            return;
        }
        Set<String> used = safeList(output.getBasicLayerAdvice()).stream()
                .filter(Objects::nonNull)
                .map(AdviceGenerationOutput.BasicLayerAdvice::getIssueId)
                .map(this::cleanupAiText)
                .filter(issueIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (AdviceGenerationOutput.BasicLayerAdvice advice : safeList(output.getBasicLayerAdvice())) {
            if (advice == null || !cleanupAiText(advice.getIssueId()).isBlank()) {
                continue;
            }
            String issueId = issueIds.stream().filter(candidate -> !used.contains(candidate)).findFirst().orElse(null);
            if (issueId != null) {
                advice.setIssueId(issueId);
                used.add(issueId);
                recordAdviceLinkSoftFix(output, "basicLayerAdvice.issueId filled by stable issue order: " + issueId);
            }
        }
        int index = 0;
        for (AdviceGenerationOutput.ImprovementLayerAdvice advice : safeList(output.getImprovementLayerAdvice())) {
            if (advice != null && cleanupAiText(advice.getIssueId()).isBlank() && index < issueIds.size()) {
                advice.setIssueId(issueIds.get(index));
                recordAdviceLinkSoftFix(output,
                        "improvementLayerAdvice.issueId filled by stable issue order: " + issueIds.get(index));
            }
            index++;
        }
    }

    private void recordAdviceLinkSoftFix(AdviceGenerationOutput output, String message) {
        if (output == null || message == null || message.isBlank()) {
            return;
        }
        AdviceGenerationOutput.TeacherTrace trace = output.getTeacherTrace();
        if (trace == null) {
            trace = AdviceGenerationOutput.TeacherTrace.builder()
                    .qualityFlags(List.of())
                    .softFixes(new ArrayList<>())
                    .hardFailures(List.of())
                    .build();
            output.setTeacherTrace(trace);
        }
        List<String> softFixes = new ArrayList<>(safeList(trace.getSoftFixes()));
        if (!softFixes.contains(message)) {
            softFixes.add(message);
            trace.setSoftFixes(softFixes);
        }
    }

    private boolean isAccepted(ModelDiagnosisBrief brief) {
        String verdict = brief == null || brief.getVerdict() == null
                ? ""
                : brief.getVerdict().trim().toUpperCase();
        return "AC".equals(verdict) || "ACCEPTED".equals(verdict);
    }

    private AdviceGenerationOutput retryAdviceGenerationForCoverage(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            AdviceGenerationOutput underProducedOutput,
            ExternalModelStagePayloads.StageValidationResult failure)
            throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        List<String> requiredIssueIds = requiredBasicAdviceIssueIds(runtimePlan);
        request.put("task", "上一轮基础建议没有覆盖全部主要问题。请保留正确诊断，只补齐缺失的问题关联与必要建议。");
        request.put("contextPolicy", List.of(
                "studentReport 只是摘要，不能替代 basicLayerAdvice 或 improvementLayerAdvice。",
                "basicLayerAdvice 必须覆盖这些主要 issueId：" + requiredIssueIds + "；每条填写对应 issueId。",
                "MINOR 问题只有在确实需要学生立即处理时才生成基础建议。",
                "improvementLayerAdvice 没有最低条数，可以为 0 条、1 条或多条，只保留真实且不重复的提升方向。",
                "不要为了凑数量拆分或重复同一句话；每条建议必须有独立 title、行动和 evidenceRefs。",
                "不要给完整答案、替换代码或逐行改法。"
        ));
        request.put("brief", runtimePlan.getBrief());
        request.put("freeDiagnosis", runtimePlan.getFreeDiagnosisOutput());
        request.put("issues", runtimePlan.getFreeDiagnosisOutput() == null
                ? List.of()
                : safeList(runtimePlan.getFreeDiagnosisOutput().getIssues()));
        request.put("libraryAnchors", safeList(runtimePlan.getIssueLibraryAnchors()));
        request.put("standardLibrary", runtimePlan.getStandardLibraryPack());
        request.put("previousOutput", underProducedOutput);
        request.put("validationFailure", Map.of(
                "stage", failure == null ? "DIAGNOSIS_AND_ADVICE" : defaultIfBlank(failure.getStage(), "DIAGNOSIS_AND_ADVICE"),
                "reason", failure == null || failure.getFailureReason() == null
                        ? ModelStageFailureReason.INVALID_JSON.name()
                        : failure.getFailureReason().name(),
                "message", failure == null ? "" : cleanupAiText(failure.getMessage())
        ));
        String systemPrompt = runtimePlan.getAdvicePrompt().getSystemPrompt()
                + "\n\n覆盖修复重试:\n"
                + "上一轮 JSON 没有覆盖全部主要问题，不能用 studentReport 摘要代替逐条建议。\n"
                + "请返回同一个输出 schema，补齐缺失的 basicLayerAdvice；提高建议按实际价值决定，不要求补数量。\n";
        activateRuntimePlan(runtimePlan);
        String content = chatCompletionWithOverrides(
                systemPrompt,
                objectMapper.writeValueAsString(request),
                false,
                adviceGenerationOutputTokens()
        );
        lastCallTelemetry.set(lastCallTelemetry.get().withFallbackRetryUsed(true));
        return parseModelStagePayload(content, AdviceGenerationOutput.class);
    }

    private AdviceGenerationOutput retryAdviceGenerationForSafety(
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            AdviceGenerationOutput unsafeOutput,
            ExternalModelStagePayloads.StageValidationResult failure)
            throws JsonProcessingException, IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "重写上一轮学生可见反馈，保留正确诊断，但去掉过度直接的答案式表达。");
        request.put("contextPolicy", List.of(
                "可以保留有效 evidenceRefs 和仍然匹配的标准库 id。",
                "只把直接答案改写成观察动作、手推方法或检查问题。",
                "不要给完整代码、替换表达式、逐行改法或隐藏测试推测。"
        ));
        request.put("brief", runtimePlan.getBrief());
        request.put("standardLibrary", runtimePlan.getStandardLibraryPack());
        request.put("standardLibraryNavigationSummary", runtimePlan.getStandardLibraryPack() == null
                ? null
                : runtimePlan.getStandardLibraryPack().getStandardLibraryNavigationSummary());
        request.put("previousOutput", unsafeOutput);
        request.put("validationFailure", Map.of(
                "stage", failure == null ? "DIAGNOSIS_AND_ADVICE" : defaultIfBlank(failure.getStage(), "DIAGNOSIS_AND_ADVICE"),
                "reason", failure == null || failure.getFailureReason() == null
                        ? ModelStageFailureReason.SAFETY_RISK.name()
                        : failure.getFailureReason().name(),
                "message", failure == null ? "" : cleanupAiText(failure.getMessage())
        ));
        String systemPrompt = runtimePlan.getAdvicePrompt().getSystemPrompt()
                + "\n\n安全重写重试:\n"
                + "上一轮 JSON 被拒绝，因为学生可见字段太接近直接答案。\n"
                + "请返回同一个输出 schema，但把不安全的学生可见文本改写为诊断式提示。\n"
                + "保留仍然有效的 evidenceRefs 和标准库 id。\n"
                + "不要使用直接改成、替换为、最终答案、完整代码、精确可执行表达式等表达。\n"
                + "边界问题只能引导学生手推取值、比较目标区间、检查端点是否进入循环。\n"
                + "DP 或状态设计问题只能提示学生重述状态含义、核对转移来源、手推可见失败样例；不要写前驱状态、具体下标组合、dp[i-1]、skip_current、take_current、两状态、多一维、空间压缩。\n"
                + "不要写出替换表达式或精确循环头。\n";
        activateRuntimePlan(runtimePlan);
        String content = chatCompletionWithOverrides(
                systemPrompt,
                objectMapper.writeValueAsString(request),
                streamEnabled,
                adviceGenerationOutputTokens()
        );
        lastCallTelemetry.set(lastCallTelemetry.get().withFallbackRetryUsed(true));
        AdviceGenerationOutput output = parseModelStagePayload(content, AdviceGenerationOutput.class);
        if (output != null) {
            return output;
        }
        return retryStructuredModelStagePayload(
                systemPrompt,
                objectMapper.writeValueAsString(request),
                runtimePlan,
                AdviceGenerationOutput.class
        );
    }

    private <T> T retryStructuredModelStagePayload(String systemPrompt,
                                                   String userPrompt,
                                                   ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                                   Class<T> payloadType)
            throws IOException, InterruptedException {
        ExternalModelCallTelemetry firstTelemetry = lastCallTelemetry.get();
        String firstRawContent = lastModelStageRawContent.get();
        if (!shouldRetryStructuredPayload(payloadType, firstTelemetry, firstRawContent)) {
            return null;
        }
        try {
            int retryMaxTokens = Math.max(Math.max(128, maxOutputTokens), Math.max(128, structuredRetryOutputTokens));
            activateRuntimePlan(runtimePlan);
            log.warn("Retrying structured AI payload after unusable {} output. streamFinishReason={}, retryMaxTokens={}",
                    payloadType.getSimpleName(),
                    firstTelemetry.streamFinishReason(),
                    retryMaxTokens);
            String retryContent = chatCompletionWithOverrides(systemPrompt, userPrompt, false, retryMaxTokens);
            T retryOutput = parseModelStagePayload(retryContent, payloadType);
            if (retryOutput != null) {
                lastCallTelemetry.set(lastCallTelemetry.get().withFallbackRetryUsed(true));
                lastStructuredRetrySourceTelemetry.set(firstTelemetry);
                structuredRetryUsedInInvocation.set(true);
                return retryOutput;
            }
            lastCallTelemetry.set(firstTelemetry);
            lastModelStageRawContent.set(firstRawContent);
            return null;
        } catch (IOException | InterruptedException exception) {
            lastCallTelemetry.set(firstTelemetry);
            lastModelStageRawContent.set(firstRawContent);
            throw exception;
        }
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private int adviceGenerationOutputTokens() {
        return Math.max(Math.max(512, maxOutputTokens), Math.max(512, structuredRetryOutputTokens));
    }

    private boolean shouldRetryStructuredPayload(Class<?> payloadType,
                                                 ExternalModelCallTelemetry telemetry,
                                                 String rawContent) {
        if (!structuredRetryEnabled) {
            return false;
        }
        String finishReason = telemetry == null ? "" : defaultIfBlank(telemetry.streamFinishReason(), "");
        String cleaned = cleanupAiText(rawContent);
        if ("length".equalsIgnoreCase(finishReason)) {
            return true;
        }
        if (cleaned.isBlank()) {
            return true;
        }
        if (payloadType == AdviceGenerationOutput.class) {
            return cleaned.contains("\"caseUnderstanding\"")
                    || cleaned.contains("\"basicLayerAdvice\"")
                    || cleaned.contains("\"improvementLayerAdvice\"")
                    || cleaned.contains("\"nextStepPlan\"")
                    || cleaned.contains("\"studentReport\"");
        }
        if (payloadType == FreeDiagnosisOutput.class) {
            return cleaned.contains("\"problemUnderstanding\"")
                    || cleaned.contains("\"codeIntent\"")
                    || cleaned.contains("\"behaviorGap\"")
                    || cleaned.contains("\"issues\"")
                    || cleaned.contains("\"navigationIntent\"");
        }
        if (payloadType == LayeredAttachmentAction.class) {
            return cleaned.contains("\"action\"")
                    || cleaned.contains("\"codes\"")
                    || cleaned.contains("\"confidence\"");
        }
        return false;
    }

    private String adviceFailureReason(ExternalModelStagePayloads.StageValidationResult failure) {
        if (failure == null) {
            return "UNKNOWN_ERROR";
        }
        String reason = failure.getFailureReason() == null
                ? "UNKNOWN_ERROR"
                : failure.getFailureReason().name();
        String message = cleanupAiText(failure.getMessage());
        return message.isBlank() ? reason : reason + ":" + message;
    }

    private SubmissionAnalysisResponse.CaseUnderstanding toResponseCaseUnderstanding(
            AdviceGenerationOutput.CaseUnderstanding source) {
        if (source == null) {
            return null;
        }
        return SubmissionAnalysisResponse.CaseUnderstanding.builder()
                .problemGoal(cleanupAiText(source.getProblemGoal()))
                .codeIntent(cleanupAiText(source.getCodeIntent()))
                .behaviorGap(cleanupAiText(source.getBehaviorGap()))
                .primaryEvidenceRef(cleanupAiText(source.getPrimaryEvidenceRef()))
                .build();
    }

    private List<SubmissionAnalysisResponse.BasicLayerAdvice> toResponseBasicLayerAdvice(
            List<AdviceGenerationOutput.BasicLayerAdvice> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null)
                .map(item -> SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                        .issueId(cleanupAiText(item.getIssueId()))
                        .mistakePointId(cleanupAiText(item.getMistakePointId()))
                        .skillUnitId(cleanupAiText(item.getSkillUnitId()))
                        .title(cleanupAiText(item.getTitle()))
                        .whatHappened(cleanupAiText(item.getWhatHappened()))
                        .whyItMatters(cleanupAiText(item.getWhyItMatters()))
                        .studentAction(cleanupAiText(item.getStudentAction()))
                        .checkQuestion(cleanupAiText(item.getCheckQuestion()))
                        .evidenceRefs(cleanList(item.getEvidenceRefs(), List.of()))
                        .confidence(item.getConfidence())
                        .knowledgePath(cleanList(item.getKnowledgePath(), List.of()))
                        .knowledgePathStatus(cleanupAiText(item.getKnowledgePathStatus()))
                        .provisionalNodeCode(cleanupAiText(item.getProvisionalNodeCode()))
                        .build())
                .toList();
    }

    private List<SubmissionAnalysisResponse.ImprovementLayerAdvice> toResponseImprovementLayerAdvice(
            List<AdviceGenerationOutput.ImprovementLayerAdvice> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null)
                .map(item -> SubmissionAnalysisResponse.ImprovementLayerAdvice.builder()
                        .issueId(cleanupAiText(item.getIssueId()))
                        .improvementPointId(cleanupAiText(item.getImprovementPointId()))
                        .skillUnitId(cleanupAiText(item.getSkillUnitId()))
                        .title(cleanupAiText(item.getTitle()))
                        .currentLimit(cleanupAiText(item.getCurrentLimit()))
                        .suggestion(cleanupAiText(item.getSuggestion()))
                        .studentBenefit(cleanupAiText(item.getStudentBenefit()))
                        .evidenceRefs(cleanList(item.getEvidenceRefs(), List.of()))
                        .confidence(item.getConfidence())
                        .knowledgePath(cleanList(item.getKnowledgePath(), List.of()))
                        .knowledgePathStatus(cleanupAiText(item.getKnowledgePathStatus()))
                        .provisionalNodeCode(cleanupAiText(item.getProvisionalNodeCode()))
                        .build())
                .toList();
    }

    private String buildAdviceReportMarkdown(ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                             AdviceGenerationOutput output) {
        if (output == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## AI 完整诊断与建议\n\n");
        if (output.getStudentReport() != null) {
            AdviceGenerationOutput.StudentReport report = output.getStudentReport();
            appendStudentReportSection(builder, "基础层", report.getBasicLayerText());
            appendStudentReportSection(builder, "提高层", report.getImprovementLayerText());
            appendStudentReportSection(builder, "下一步行动", report.getNextActionText());
        }
        if (output.getCaseUnderstanding() != null) {
            AdviceGenerationOutput.CaseUnderstanding understanding = output.getCaseUnderstanding();
            builder.append("### 题目与代码理解\n\n");
            appendMarkdownLine(builder, "题目目标", understanding.getProblemGoal());
            appendMarkdownLine(builder, "代码意图", understanding.getCodeIntent());
            appendMarkdownLine(builder, "行为差距", understanding.getBehaviorGap());
            appendMarkdownLine(builder, "主要证据", understanding.getPrimaryEvidenceRef());
            builder.append('\n');
        }
        if (output.getBasicLayerAdvice() != null && !output.getBasicLayerAdvice().isEmpty()) {
            builder.append(output.getStudentReport() == null ? "### 基础层\n\n" : "### 基础层明细\n\n");
            int index = 1;
            for (AdviceGenerationOutput.BasicLayerAdvice item : output.getBasicLayerAdvice()) {
                if (item == null) {
                    continue;
                }
                builder.append(index++).append(". ")
                        .append(defaultIfBlank(item.getTitle(), "基础层问题"))
                        .append('\n');
                appendMarkdownLine(builder, "发生了什么", item.getWhatHappened());
                appendMarkdownLine(builder, "为什么重要", item.getWhyItMatters());
                appendMarkdownLine(builder, "下一步", item.getStudentAction());
                appendMarkdownLine(builder, "自查问题", item.getCheckQuestion());
                if (item.getEvidenceRefs() != null && !item.getEvidenceRefs().isEmpty()) {
                    appendMarkdownLine(builder, "证据", String.join(", ", item.getEvidenceRefs()));
                }
                builder.append('\n');
            }
        }
        if (output.getImprovementLayerAdvice() != null && !output.getImprovementLayerAdvice().isEmpty()) {
            builder.append(output.getStudentReport() == null ? "### 提高层\n\n" : "### 提高层明细\n\n");
            int index = 1;
            for (AdviceGenerationOutput.ImprovementLayerAdvice item : output.getImprovementLayerAdvice()) {
                if (item == null) {
                    continue;
                }
                builder.append(index++).append(". ")
                        .append(defaultIfBlank(item.getTitle(), "提高层建议"))
                        .append('\n');
                appendMarkdownLine(builder, "当前限制", item.getCurrentLimit());
                appendMarkdownLine(builder, "建议", item.getSuggestion());
                appendMarkdownLine(builder, "收益", item.getStudentBenefit());
                if (item.getEvidenceRefs() != null && !item.getEvidenceRefs().isEmpty()) {
                    appendMarkdownLine(builder, "证据", String.join(", ", item.getEvidenceRefs()));
                }
                builder.append('\n');
            }
        }
        if (runtimePlan != null && runtimePlan.getStandardLibraryPack() != null
                && runtimePlan.getStandardLibraryPack().getStandardLibraryNavigationSummary() != null) {
            StandardLibraryPack.StandardLibraryNavigationSummary summary =
                    runtimePlan.getStandardLibraryPack().getStandardLibraryNavigationSummary();
            builder.append("### 定位说明\n\n");
            appendMarkdownLine(builder, "定位状态", summary.getStatus());
            appendMarkdownLine(builder, "选中数量", summary.getSelectedCount() == null ? "" : summary.getSelectedCount().toString());
        }
        String markdown = builder.toString().trim();
        return markdown;
    }

    private void appendStudentReportSection(StringBuilder builder, String title, String value) {
        String cleaned = cleanupAiText(value);
        if (!cleaned.isBlank()) {
            builder.append("### ").append(title).append("\n\n")
                    .append(cleaned)
                    .append("\n\n");
        }
    }

    private void appendMarkdownLine(StringBuilder builder, String label, String value) {
        String cleaned = cleanupAiText(value);
        if (!cleaned.isBlank()) {
            builder.append("- ").append(label).append("：").append(cleaned).append('\n');
        }
    }

    private SubmissionAnalysisResponse buildAdviceRuntimeAnalysisResponse(
            SubmissionAnalysisResponse baseline,
            ExternalModelAgentRuntime.RuntimePlan runtimePlan,
            AdviceGenerationOutput adviceOutput,
            SubmissionAnalysisResponse.StudentFeedback studentFeedback,
            String rawSourceCode,
            List<SubmissionAnalysisResponse.LineIssue> baselineLineIssues) {
        List<String> evidenceRefs = adviceEvidenceRefs(adviceOutput);
        SubmissionAnalysisResponse.NextLearningAction nextAction =
                studentFeedback == null ? null : studentFeedback.getNextLearningAction();
        return SubmissionAnalysisResponse.builder()
                .submissionId(baseline.getSubmissionId())
                .analysisSchemaVersion(baseline.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(baseline.getEvidenceSchemaVersion())
                .taxonomyVersion(baseline.getTaxonomyVersion())
                .sourceType(AI_SOURCE)
                .scenario(baseline.getScenario())
                .headline("AI 完整诊断已完成")
                .summary(defaultIfBlank(adviceOutput == null ? "" : adviceOutput.getStudentSummary(), "AI 已完成本次诊断。"))
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .abilityPoints(List.of())
                .focusPoints(adviceFocusPoints(adviceOutput))
                .fixDirections(adviceFixDirections(adviceOutput))
                .evidenceRefs(evidenceRefs)
                .studentHint(defaultIfBlank(nextAction == null ? "" : nextAction.getTask(),
                        firstAdviceAction(adviceOutput, "先复核模型指出的证据。")))
                .studentHintPlan(buildAdviceHintPlan(adviceOutput, studentFeedback))
                .studentFeedback(studentFeedback)
                .learningInterventionPlan(buildAdviceInterventionPlan(studentFeedback))
                .learningActionEvidence(null)
                .teacherNote(adviceTeacherNote(adviceOutput))
                .progressSignal("")
                .confidence(resolveConfidence(adviceConfidence(adviceOutput), null))
                .uncertainty(defaultIfBlank(runtimePlan == null || runtimePlan.getBrief() == null
                        ? ""
                        : runtimePlan.getBrief().getUncertainty(), ""))
                .diagnosticTrace("")
                .modelEducationTrace(adviceModelEducationTrace(adviceOutput))
                .caseUnderstanding(toResponseCaseUnderstanding(adviceOutput == null ? null : adviceOutput.getCaseUnderstanding()))
                .basicLayerAdvice(toResponseBasicLayerAdvice(adviceOutput == null ? null : adviceOutput.getBasicLayerAdvice()))
                .improvementLayerAdvice(toResponseImprovementLayerAdvice(adviceOutput == null ? null : adviceOutput.getImprovementLayerAdvice()))
                .aiInvocation(modelInvocation(baseline, "MODEL_COMPLETED", runtimePromptVersion(runtimePlan), runtimePlan, null))
                .answerLeakRisk(resolveAnswerLeakRisk(nextAction == null ? "" : nextAction.getAnswerLeakRisk(),
                        "LOW"))
                .wrongSolution(null)
                .correctSolution(null)
                .lineIssues(List.of())
                .firstFailedCase(baseline.getFirstFailedCase())
                .reportMarkdown(buildAdviceReportMarkdown(runtimePlan, adviceOutput))
                .generatedAt(baseline.getGeneratedAt())
                .build();
    }

    private List<String> adviceEvidenceRefs(AdviceGenerationOutput output) {
        List<String> refs = new ArrayList<>();
        if (output != null && output.getCaseUnderstanding() != null) {
            addIfNotBlank(refs, output.getCaseUnderstanding().getPrimaryEvidenceRef());
        }
        if (output != null && output.getBasicLayerAdvice() != null) {
            output.getBasicLayerAdvice().stream()
                    .filter(item -> item != null && item.getEvidenceRefs() != null)
                    .flatMap(item -> item.getEvidenceRefs().stream())
                    .forEach(ref -> addIfNotBlank(refs, ref));
        }
        if (output != null && output.getImprovementLayerAdvice() != null) {
            output.getImprovementLayerAdvice().stream()
                    .filter(item -> item != null && item.getEvidenceRefs() != null)
                    .flatMap(item -> item.getEvidenceRefs().stream())
                    .forEach(ref -> addIfNotBlank(refs, ref));
        }
        return refs.stream().distinct().toList();
    }

    private List<String> adviceFocusPoints(AdviceGenerationOutput output) {
        if (output == null || output.getBasicLayerAdvice() == null) {
            return List.of();
        }
        return output.getBasicLayerAdvice().stream()
                .filter(item -> item != null)
                .map(AdviceGenerationOutput.BasicLayerAdvice::getTitle)
                .map(this::cleanupAiText)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> adviceFixDirections(AdviceGenerationOutput output) {
        List<String> directions = new ArrayList<>();
        if (output != null && output.getNextStepPlan() != null) {
            output.getNextStepPlan().stream()
                    .filter(item -> item != null)
                    .map(AdviceGenerationOutput.NextStepAdvice::getTarget)
                    .forEach(value -> addIfNotBlank(directions, value));
        }
        if (directions.isEmpty() && output != null && output.getBasicLayerAdvice() != null) {
            output.getBasicLayerAdvice().stream()
                    .filter(item -> item != null)
                    .map(AdviceGenerationOutput.BasicLayerAdvice::getStudentAction)
                    .forEach(value -> addIfNotBlank(directions, value));
        }
        return directions.stream().distinct().limit(5).toList();
    }

    private String firstAdviceAction(AdviceGenerationOutput output, String defaultAction) {
        if (output != null && output.getNextStepPlan() != null) {
            String target = output.getNextStepPlan().stream()
                    .filter(item -> item != null)
                    .map(AdviceGenerationOutput.NextStepAdvice::getTarget)
                    .map(this::cleanupAiText)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("");
            if (!target.isBlank()) {
                return target;
            }
        }
        if (output != null && output.getBasicLayerAdvice() != null) {
            String action = output.getBasicLayerAdvice().stream()
                    .filter(item -> item != null)
                    .map(AdviceGenerationOutput.BasicLayerAdvice::getStudentAction)
                    .map(this::cleanupAiText)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("");
            if (!action.isBlank()) {
                return action;
            }
        }
        return defaultAction;
    }

    private SubmissionAnalysisResponse.StudentHintPlan buildAdviceHintPlan(
            AdviceGenerationOutput output,
            SubmissionAnalysisResponse.StudentFeedback studentFeedback) {
        SubmissionAnalysisResponse.NextLearningAction nextAction =
                studentFeedback == null ? null : studentFeedback.getNextLearningAction();
        if (nextAction == null) {
            return null;
        }
        List<String> evidenceRefs = cleanList(nextAction.getEvidenceRefs(), adviceEvidenceRefs(output));
        return SubmissionAnalysisResponse.StudentHintPlan.builder()
                .hintLevel(defaultIfBlank(nextAction.getHintLevel(), "L2"))
                .problemType("AI 完整诊断")
                .evidenceAnchor(evidenceRefs.isEmpty() ? "" : evidenceRefs.get(0))
                .nextAction(defaultIfBlank(nextAction.getTask(), firstAdviceAction(output, "")))
                .coachQuestion(defaultIfBlank(nextAction.getCheckQuestion(), firstAdviceQuestion(output)))
                .teachingAction(defaultIfBlank(nextAction.getAction(), "COLLECT_EVIDENCE"))
                .evidenceRefs(evidenceRefs)
                .answerLeakRisk(resolveAnswerLeakRisk(nextAction.getAnswerLeakRisk(), "LOW"))
                .build();
    }

    private SubmissionAnalysisResponse.LearningInterventionPlan buildAdviceInterventionPlan(
            SubmissionAnalysisResponse.StudentFeedback studentFeedback) {
        SubmissionAnalysisResponse.NextLearningAction nextAction =
                studentFeedback == null ? null : studentFeedback.getNextLearningAction();
        if (nextAction == null) {
            return null;
        }
        return SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                .interventionType("ADVICE_GENERATION")
                .goal("完成 AI 建议中的第一个可观察动作。")
                .studentTask(nextAction.getTask())
                .checkQuestion(nextAction.getCheckQuestion())
                .completionSignal("学生能用证据说明当前问题和下一步验证动作。")
                .evidenceRefs(cleanList(nextAction.getEvidenceRefs(), List.of()))
                .estimatedMinutes(6)
                .answerLeakRisk(resolveAnswerLeakRisk(nextAction.getAnswerLeakRisk(), "LOW"))
                .build();
    }

    private Double adviceConfidence(AdviceGenerationOutput output) {
        if (output == null || output.getBasicLayerAdvice() == null || output.getBasicLayerAdvice().isEmpty()) {
            return null;
        }
        return output.getBasicLayerAdvice().stream()
                .filter(item -> item != null && item.getConfidence() != null)
                .map(AdviceGenerationOutput.BasicLayerAdvice::getConfidence)
                .max(Double::compareTo)
                .orElse(null);
    }

    private String adviceTeacherNote(AdviceGenerationOutput output) {
        String reasoning = output == null || output.getTeacherTrace() == null
                ? ""
                : cleanupAiText(output.getTeacherTrace().getReasoningSummary());
        if (!reasoning.isBlank()) {
            return reasoning;
        }
        return "外部模型已生成基础层与提高层结构化建议。";
    }

    private SubmissionAnalysisResponse.ModelEducationTrace adviceModelEducationTrace(AdviceGenerationOutput output) {
        if (output == null) {
            return null;
        }
        AdviceGenerationOutput.BasicLayerAdvice firstBasic = firstBasicAdvice(output);
        return SubmissionAnalysisResponse.ModelEducationTrace.builder()
                .source("adviceGeneration")
                .primaryIssueTag(firstBasic == null ? "" : cleanupAiText(firstBasic.getSkillUnitId()).toUpperCase())
                .fineGrainedTag(firstBasic == null ? "" : cleanupAiText(firstBasic.getMistakePointId()).toUpperCase())
                .evidenceRefs(adviceEvidenceRefs(output))
                .primaryReasoning(firstBasic == null ? "" : cleanupAiText(firstBasic.getWhatHappened()))
                .secondaryIssues(List.of())
                .distractorNotes(List.of())
                .teachingPriority(cleanupAiText(output.getStudentSummary()))
                .improvementCategories(adviceImprovementCategories(output))
                .nextLearningAction(firstAdviceAction(output, ""))
                .nextLearningActionEvidenceRefs(firstStepEvidenceRefs(output))
                .confidence(adviceConfidence(output))
                .uncertainty("")
                .needsMoreEvidence(false)
                .answerLeakRisk("LOW")
                .build();
    }

    private List<String> adviceImprovementCategories(AdviceGenerationOutput output) {
        if (output == null || output.getImprovementLayerAdvice() == null) {
            return List.of();
        }
        return output.getImprovementLayerAdvice().stream()
                .filter(item -> item != null)
                .map(AdviceGenerationOutput.ImprovementLayerAdvice::getImprovementPointId)
                .map(this::cleanupAiText)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
    }

    private AdviceGenerationOutput.BasicLayerAdvice firstBasicAdvice(AdviceGenerationOutput output) {
        if (output == null || output.getBasicLayerAdvice() == null) {
            return null;
        }
        return output.getBasicLayerAdvice().stream()
                .filter(item -> item != null)
                .findFirst()
                .orElse(null);
    }

    private String firstAdviceQuestion(AdviceGenerationOutput output) {
        AdviceGenerationOutput.BasicLayerAdvice first = firstBasicAdvice(output);
        return first == null ? "这条证据说明了什么代码行为？" : first.getCheckQuestion();
    }

    private List<String> firstStepEvidenceRefs(AdviceGenerationOutput output) {
        if (output == null || output.getNextStepPlan() == null) {
            return List.of();
        }
        return output.getNextStepPlan().stream()
                .filter(item -> item != null && item.getEvidenceRef() != null && !item.getEvidenceRef().isBlank())
                .map(AdviceGenerationOutput.NextStepAdvice::getEvidenceRef)
                .distinct()
                .toList();
    }

    private void addIfNotBlank(List<String> values, String candidate) {
        String cleaned = cleanupAiText(candidate);
        if (!cleaned.isBlank()) {
            values.add(cleaned);
        }
    }

    private void activateRuntimePlan(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan == null) {
            nextRequestContext.set(ExternalModelRequestContext.standard());
            lastCallTelemetry.set(ExternalModelCallTelemetry.empty());
            return;
        }
        ExternalModelRequestContext context = new ExternalModelRequestContext(
                runtimePlan.getRuntimeProfile(),
                runtimePlan.isRequestCompact()
        );
        nextRequestContext.set(context);
        lastCallTelemetry.set(ExternalModelCallTelemetry.request(
                context.runtimeProfile(),
                context.requestCompact(),
                0
        ));
    }

    private String runtimePromptVersion(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan != null && runtimePlan.getAdvicePrompt() != null
                && !cleanupAiText(runtimePlan.getAdvicePrompt().getVersion()).isBlank()) {
            return cleanupAiText(runtimePlan.getAdvicePrompt().getVersion());
        }
        return PromptTemplateRegistry.DIAGNOSIS_REPORT_V4;
    }

    private String failureSummary(ExternalModelStagePayloads.StageValidationResult failure) {
        if (failure == null) {
            return "（失败阶段：UNKNOWN_STAGE；失败原因：UNKNOWN_ERROR）";
        }
        String stage = failure.getStage() == null || failure.getStage().isBlank() ? "UNKNOWN_STAGE" : failure.getStage();
        String reason = failure.getFailureReason() == null ? "UNKNOWN_ERROR" : failure.getFailureReason().name();
        return "（失败阶段：" + stage + "；失败原因：" + reason + "）";
    }

    private SubmissionAnalysisResponse runtimeFailure(SubmissionAnalysisResponse baseline,
                                                      ExternalModelStagePayloads.StageValidationResult validationResult) {
        return runtimeFailure(baseline, null, validationResult);
    }

    private SubmissionAnalysisResponse runtimeFailure(SubmissionAnalysisResponse baseline,
                                                      ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                                      ExternalModelStagePayloads.StageValidationResult validationResult) {
        if (baseline == null) {
            return null;
        }
        ExternalModelStagePayloads.StageValidationResult attributionResult =
                withTransportAttribution(validationResult);
        String reason = attributionResult == null || attributionResult.getFailureReason() == null
                ? ModelStageFailureReason.UNKNOWN_ERROR.name()
                : attributionResult.getFailureReason().name();
        String stage = attributionResult == null || attributionResult.getStage() == null || attributionResult.getStage().isBlank()
                ? "UNKNOWN_STAGE"
                : attributionResult.getStage();
        String message = attributionResult == null ? "" : cleanupAiText(attributionResult.getMessage());
        String uncertainty = "外部模型未完成，本次未使用本地规则兜底。失败阶段：" + stage + "；失败原因：" + reason
                + (message.isBlank() ? "" : "，" + message);
        return SubmissionAnalysisResponse.builder()
                .submissionId(baseline.getSubmissionId())
                .analysisSchemaVersion(baseline.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(baseline.getEvidenceSchemaVersion())
                .taxonomyVersion(baseline.getTaxonomyVersion())
                .sourceType(AI_SOURCE)
                .scenario(baseline.getScenario())
                .headline("AI 诊断暂不可用")
                .summary("本次没有生成 AI 诊断，请稍后重试或先查看原始评测结果。")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .abilityPoints(List.of())
                .focusPoints(List.of())
                .fixDirections(List.of())
                .evidenceRefs(List.of())
                .studentHint("AI 诊断未生成，请稍后重试。")
                .studentHintPlan(null)
                .studentFeedback(null)
                .learningInterventionPlan(null)
                .learningActionEvidence(null)
                .teacherNote(uncertainty)
                .progressSignal("")
                .confidence(null)
                .uncertainty(uncertainty)
                .diagnosticTrace("model=failed stage=" + stage + " reason=" + reason)
                .modelEducationTrace(null)
                .caseUnderstanding(null)
                .basicLayerAdvice(List.of())
                .improvementLayerAdvice(List.of())
                .aiInvocation(modelInvocation(
                        baseline,
                        "MODEL_FAILED",
                        runtimePromptVersion(runtimePlan),
                        runtimePlan,
                        attributionResult
                ))
                .answerLeakRisk(defaultIfBlank(baseline.getAnswerLeakRisk(), "LOW"))
                .wrongSolution(null)
                .correctSolution(null)
                .lineIssues(List.of())
                .firstFailedCase(baseline.getFirstFailedCase())
                .reportMarkdown("")
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult withTransportAttribution(
            ExternalModelStagePayloads.StageValidationResult result) {
        if (result == null) {
            return null;
        }
        if (!"length".equalsIgnoreCase(defaultIfBlank(lastCallTelemetry.get().streamFinishReason(), ""))) {
            return withStructuredRetrySourceAttribution(result);
        }
        ModelStageFailureReason reason = result.getFailureReason();
        if (reason != ModelStageFailureReason.EMPTY_RESPONSE
                && reason != ModelStageFailureReason.INVALID_JSON
                && reason != ModelStageFailureReason.UNKNOWN_ERROR) {
            return result;
        }
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .stage(result.getStage())
                .failureReason(ModelStageFailureReason.OUTPUT_TRUNCATED)
                .message(defaultIfBlank(result.getMessage(), "External model output ended with finish_reason=length."))
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult withStructuredRetrySourceAttribution(
            ExternalModelStagePayloads.StageValidationResult result) {
        ExternalModelCallTelemetry sourceTelemetry = lastStructuredRetrySourceTelemetry.get();
        if (!"length".equalsIgnoreCase(defaultIfBlank(sourceTelemetry.streamFinishReason(), ""))) {
            return result;
        }
        ModelStageFailureReason reason = result.getFailureReason();
        if (reason != ModelStageFailureReason.EMPTY_RESPONSE
                && reason != ModelStageFailureReason.INVALID_JSON
                && reason != ModelStageFailureReason.UNKNOWN_ERROR) {
            return result;
        }
        lastCallTelemetry.set(sourceTelemetry.withFallbackRetryUsed(true));
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .stage(result.getStage())
                .failureReason(ModelStageFailureReason.OUTPUT_TRUNCATED)
                .message(defaultIfBlank(result.getMessage(), "External model structured retry did not recover truncated output."))
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult withStage(String stage,
                                                                       ExternalModelStagePayloads.StageValidationResult result) {
        if (result == null) {
            return ExternalModelStagePayloads.StageValidationResult.builder()
                    .valid(false)
                    .stage(stage)
                    .failureReason(ModelStageFailureReason.UNKNOWN_ERROR)
                    .message("")
                    .build();
        }
        result.setStage(stage);
        return result;
    }

    private ExternalModelStagePayloads.StageValidationResult stageFailureFromException(String stage,
                                                                                      Exception exception) {
        ModelStageFailureReason reason = failureClassifier.classify(exception);
        return stageFailure(stage, reason, exception == null ? "" : exception.getMessage());
    }

    private ExternalModelStagePayloads.StageValidationResult aiUnavailableFailure(String stage) {
        return stageFailure(stage, ModelStageFailureReason.UNKNOWN_ERROR, "AI access is unavailable.");
    }

    private ExternalModelStagePayloads.StageValidationResult stageFailure(String stage,
                                                                         ModelStageFailureReason reason,
                                                                         String message) {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .stage(stage)
                .failureReason(reason)
                .message(message == null ? "" : message)
                .build();
    }

    public String enhanceGrowthReportMarkdown(Problem problem,
                                              List<Map<String, Object>> submissionTimeline) {
        if (!canCallAi()) {
            log.info("AI growth report unavailable because AI access is unavailable. problemId={}", problem.getId());
            return growthReportUnavailableMarkdown(problem, aiUnavailableFailure("GROWTH_REPORT"));
        }
        try {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("problemTitle", problem.getTitle());
            context.put("problemDescription", problem.getDescription());
            context.put("aiPromptDirection", problem.getAiPromptDirection() == null ? "" : problem.getAiPromptDirection());
            context.put("submissionTimeline", submissionTimeline);

            String content = chatCompletion(
                    """
                    你是中文 OJ 成长报告助手。
                    请直接输出 Markdown，不要输出额外解释，不要输出 <think>、思考过程、XML 标签或草稿。

                    报告需要包含：
                    1. 总览
                    2. 错误复盘
                    3. 优化历程
                    4. 学习总结

                    内容要结合提交轨迹，语言简洁、自然、可执行。
                    """,
                    "请基于以下上下文生成成长报告 Markdown：" + objectMapper.writeValueAsString(context)
            );

            String markdown = cleanupAiText(content);
            return markdown.isBlank()
                    ? growthReportUnavailableMarkdown(problem, stageFailure("GROWTH_REPORT", ModelStageFailureReason.INVALID_JSON,
                    "AI growth report response is blank."))
                    : markdown;
        } catch (Exception exception) {
            log.error("AI growth report generation failed. problemId={}", problem.getId(), exception);
            return growthReportUnavailableMarkdown(problem, stageFailureFromException("GROWTH_REPORT", exception));
        }
    }

    private String growthReportUnavailableMarkdown(Problem problem,
                                                   ExternalModelStagePayloads.StageValidationResult failure) {
        String title = problem == null || problem.getTitle() == null || problem.getTitle().isBlank()
                ? "AI 成长报告"
                : "AI 成长报告 - " + cleanupAiText(problem.getTitle());
        return "# " + title + " 暂不可用\n\n"
                + "外部模型未完成，本次未使用本地报告兜底。请在 AI smoke 通过或模型额度恢复后重新生成。\n\n"
                + "<!-- AI_FAILURE:" + failureSummary(failure) + " -->";
    }

    private boolean canCallAi() {
        return enabled && ((aiProviderGateway != null && aiProviderGateway.available())
                || (apiKey != null && !apiKey.isBlank()));
    }

    private AiInvocationContext submissionContext(Submission submission, String purpose, String idempotencyKey) {
        return aiInvocationContextResolver == null
                ? AiInvocationContext.anonymous(purpose, idempotencyKey)
                : aiInvocationContextResolver.forSubmission(submission, purpose, idempotencyKey);
    }

    private String providerBaseUrl() {
        return aiProviderGateway == null ? baseUrl : aiProviderGateway.baseUrl();
    }

    public String providerName() {
        return PROVIDER;
    }

    public String modelName() {
        return defaultIfBlank(activeModel.get(), model);
    }

    public static AutoCloseable captureModelCallTrace(Consumer<ModelCallTraceEvent> sink) {
        MODEL_CALL_TRACE_SINK.set(sink);
        return MODEL_CALL_TRACE_SINK::remove;
    }

    public record ModelCallTraceEvent(
            String stage,
            boolean stream,
            int outputTokens,
            String runtimeProfile,
            boolean requestCompact,
            String systemPrompt,
            String userPrompt,
            String requestBody,
            String responseBody,
            String content,
            String error,
            long latencyMs
    ) {
    }

    public String smokeChatCompletion() throws IOException, InterruptedException {
        if (!canCallAi()) {
            throw new IOException("AI is disabled or API key is blank");
        }
        providerHealthCheck.set(true);
        try {
            return chatCompletionWithOverrides(
                    "You are a production readiness smoke test. Reply with exactly OK.",
                    "Return OK.", streamEnabled, 128);
        } finally {
            providerHealthCheck.remove();
        }
    }

    protected String chatCompletion(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        ExternalModelRequestContext requestContext = nextRequestContext.get();
        nextRequestContext.set(ExternalModelRequestContext.standard());
        lastCallTelemetry.set(ExternalModelCallTelemetry.request(
                requestContext.runtimeProfile(),
                requestContext.requestCompact(),
                0
        ));
        activeModel.set(defaultIfBlank(model, ""));
        try {
            String content = doChatCompletionWithRetry(systemPrompt, userPrompt, streamEnabled, requestContext);
            return content;
        } catch (IOException exception) {
            if (!streamEnabled && streamFallbackEnabled && shouldRetryWithStreaming(exception)) {
                log.warn("Retrying AI chat completion with stream=true after non-stream response was unusable. model={}", model);
                String content = doChatCompletionWithRetry(systemPrompt, userPrompt, true, requestContext);
                lastCallTelemetry.set(lastCallTelemetry.get().withFallbackRetryUsed(true));
                return content;
            }
            throw exception;
        }
    }

    protected String chatCompletionWithOverrides(String systemPrompt,
                                                 String userPrompt,
                                                 boolean stream,
                                                 int outputTokens) throws IOException, InterruptedException {
        ExternalModelRequestContext requestContext = nextRequestContext.get();
        nextRequestContext.set(ExternalModelRequestContext.standard());
        lastCallTelemetry.set(ExternalModelCallTelemetry.request(
                requestContext.runtimeProfile(),
                requestContext.requestCompact(),
                0
        ));
        activeModel.set(defaultIfBlank(model, ""));
        try {
            String content = doChatCompletionWithRetry(systemPrompt, userPrompt, stream, requestContext, outputTokens);
            return content;
        } catch (IOException exception) {
            if (!stream && streamFallbackEnabled && shouldRetryWithStreaming(exception)) {
                log.warn("Retrying AI chat completion with stream=true after non-stream response was unusable. model={}", model);
                String content = doChatCompletionWithRetry(systemPrompt, userPrompt, true, requestContext, outputTokens);
                lastCallTelemetry.set(lastCallTelemetry.get().withFallbackRetryUsed(true));
                return content;
            }
            throw exception;
        }
    }

    private String doChatCompletionWithRetry(String systemPrompt,
                                             String userPrompt,
                                             boolean stream,
                                             ExternalModelRequestContext requestContext) throws IOException, InterruptedException {
        return doChatCompletionWithRetry(systemPrompt, userPrompt, stream, requestContext, maxOutputTokens);
    }

    private String doChatCompletionWithRetry(String systemPrompt,
                                             String userPrompt,
                                             boolean stream,
                                             ExternalModelRequestContext requestContext,
                                             int outputTokens) throws IOException, InterruptedException {
        int attempts = Math.max(1, retryMaxAttempts);
        IOException lastException = null;
        List<String> modelCandidates = ExternalModelPool.candidates(model, modelPool);
        for (int modelIndex = 0; modelIndex < modelCandidates.size(); modelIndex++) {
            String candidateModel = modelCandidates.get(modelIndex);
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    return doChatCompletion(candidateModel, systemPrompt, userPrompt, stream, requestContext, outputTokens);
                } catch (IOException exception) {
                    lastException = exception;
                    String reasonText = exception.getMessage();
                    ModelStageFailureReason reason = failureClassifier.classify(exception);
                    if (modelIndex + 1 < modelCandidates.size()
                            && ExternalModelPool.shouldFallback(reason, reasonText)) {
                        String nextModel = modelCandidates.get(modelIndex + 1);
                        log.warn("Switching AI chat completion model after provider failure. fromModel={}, toModel={}, reason={}",
                                candidateModel,
                                nextModel,
                                previewBody(reasonText));
                        break;
                    }
                    if (attempt >= attempts || !isRetryableCallFailure(exception)) {
                        throw exception;
                    }
                    long backoff = Math.max(0, retryBackoffMs) * attempt;
                    log.warn("Retrying AI chat completion after transient failure. model={}, attempt={}/{}, waitMs={}, reason={}",
                            candidateModel,
                            attempt + 1,
                            attempts,
                            backoff,
                            previewBody(reasonText));
                    sleepBeforeRetry(backoff);
                }
            }
        }
        throw lastException == null ? new IOException("AI API call failed") : lastException;
    }

    private String doChatCompletion(String systemPrompt,
                                    String userPrompt,
                                    boolean stream,
                                    ExternalModelRequestContext requestContext) throws IOException, InterruptedException {
        return doChatCompletion(systemPrompt, userPrompt, stream, requestContext, maxOutputTokens);
    }

    private String doChatCompletion(String systemPrompt,
                                    String userPrompt,
                                    boolean stream,
                                    ExternalModelRequestContext requestContext,
                                    int outputTokens) throws IOException, InterruptedException {
        return doChatCompletion(model, systemPrompt, userPrompt, stream, requestContext, outputTokens);
    }

    private String doChatCompletion(String selectedModel,
                                    String systemPrompt,
                                    String userPrompt,
                                    boolean stream,
                                    ExternalModelRequestContext requestContext,
                                    int outputTokens) throws IOException, InterruptedException {
        String effectiveModel = defaultIfBlank(selectedModel, model);
        activeModel.set(effectiveModel);
        String providerBaseUrl = providerBaseUrl();
        Map<String, Object> requestBody = chatRequestFactory.build(
                providerBaseUrl,
                modelScopeCompatibleRequest,
                enableThinking,
                effectiveModel,
                systemPrompt,
                userPrompt,
                stream,
                outputTokens
        );
        String endpoint = providerBaseUrl.endsWith("/") ? providerBaseUrl + "chat/completions" : providerBaseUrl + "/chat/completions";
        log.info("Calling AI chat completion. model={}, timeoutSeconds={}, stream={}, endpoint={}",
                effectiveModel,
                Math.max(timeoutSeconds, 5),
                stream,
                endpoint);
        String activeProfile = requestContext.runtimeProfile();
        boolean activeCompact = requestContext.requestCompact();

        String serializedRequest = objectMapper.writeValueAsString(requestBody);
        int requestBytes = serializedRequest.getBytes(StandardCharsets.UTF_8).length;
        lastCallTelemetry.set((stream
                ? ExternalModelCallTelemetry.stream(0, 0, 0, 0, "")
                : ExternalModelCallTelemetry.nonStream("")).withRequestTelemetry(requestBytes, activeProfile, activeCompact));

        long startedAt = System.nanoTime();
        String responseBody = "";
        String content = "";
        try {
            responseBody = sendChatCompletionRequest(serializedRequest, stream);
            if (stream) {
                ParsedStreamingContent parsed = extractStreamingChatMessageContent(responseBody);
                content = parsed.content();
                lastCallTelemetry.set(parsed.telemetry().withRequestTelemetry(requestBytes, activeProfile, activeCompact));
            } else {
                JsonNode root = objectMapper.readTree(responseBody);
                content = extractChatMessageContent(root);
                lastCallTelemetry.set(ExternalModelCallTelemetry.nonStream(extractFinishReason(root.path("choices").path(0)))
                        .withRequestTelemetry(requestBytes, activeProfile, activeCompact));
            }
            if (content.isBlank()) {
                log.warn("AI response did not include usable message content. bodyPreview={}",
                        previewBody(responseBody));
                throw new IOException("AI response did not include message content");
            }
            recordModelCallTrace(systemPrompt, userPrompt, serializedRequest, responseBody, content, "",
                    stream, outputTokens, activeProfile, activeCompact, startedAt);
            return content;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            recordModelCallTrace(systemPrompt, userPrompt, serializedRequest, responseBody, content,
                    exception.getMessage(), stream, outputTokens, activeProfile, activeCompact, startedAt);
            throw exception;
        }
    }

    private void recordModelCallTrace(String systemPrompt,
                                      String userPrompt,
                                      String requestBody,
                                      String responseBody,
                                      String content,
                                      String error,
                                      boolean stream,
                                      int outputTokens,
                                      String runtimeProfile,
                                      boolean requestCompact,
                                      long startedAt) {
        Consumer<ModelCallTraceEvent> sink = MODEL_CALL_TRACE_SINK.get();
        if (sink == null) {
            return;
        }
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        sink.accept(new ModelCallTraceEvent(
                traceStage(systemPrompt),
                stream,
                outputTokens,
                runtimeProfile,
                requestCompact,
                systemPrompt,
                userPrompt,
                requestBody,
                responseBody,
                content,
                defaultIfBlank(error, ""),
                latencyMs
        ));
    }

    private String traceStage(String systemPrompt) {
        String text = defaultIfBlank(systemPrompt, "");
        if (text.contains("free-diagnosis-v1") || text.contains("free-diagnosis-v2")) {
            return "FREE_DIAGNOSIS";
        }
        if (text.contains("standard-library-navigation-v1")) {
            return "LAYERED_ATTACHMENT";
        }
        if (text.contains("diagnosis-report-v3") || text.contains("diagnosis-report-v4")) {
            return "ADVICE_GENERATION";
        }
        return "MODEL_CALL";
    }

    protected String sendChatCompletionRequest(String requestBody, boolean stream) throws IOException, InterruptedException {
        if (aiProviderGateway != null) {
            AiProviderGateway.AiProviderRequest request = new AiProviderGateway.AiProviderRequest(requestBody, stream, modelName());
            return (Boolean.TRUE.equals(providerHealthCheck.get())
                    ? aiProviderGateway.healthCheck(request)
                    : aiProviderGateway.invoke(activeInvocationContext.get(), request)).responseBody();
        }
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 5)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("AI chat completion returned non-success status. status={}, bodyPreview={}",
                    response.statusCode(),
                    previewBody(response.body()));
            throw new IOException("AI API returned status " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private boolean shouldRetryWithStreaming(IOException exception) {
        String message = exception == null || exception.getMessage() == null ? "" : exception.getMessage();
        return message.contains("did not include message content");
    }

    private boolean isRetryableCallFailure(IOException exception) {
        String text = exception == null ? "" : exception.getMessage();
        return failureClassifier.isRetryable(failureClassifier.classify(exception), text);
    }

    private void sleepBeforeRetry(long waitMs) throws InterruptedException {
        if (waitMs <= 0) {
            return;
        }
        Thread.sleep(waitMs);
    }

    private String extractChatMessageContent(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        JsonNode firstChoice = root.path("choices").path(0);
        return firstNonBlank(
                extractContentNode(firstChoice.path("message").path("content")),
                extractContentNode(firstChoice.path("delta").path("content")),
                extractContentNode(firstChoice.path("message").path("reasoning_content")),
                extractContentNode(firstChoice.path("delta").path("reasoning_content"))
        );
    }

    private ParsedStreamingContent extractStreamingChatMessageContent(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            return new ParsedStreamingContent("", ExternalModelCallTelemetry.stream(0, 0, 0, 0, ""));
        }
        StringBuilder content = new StringBuilder();
        int chunkCount = 0;
        int contentChunkCount = 0;
        int reasoningChunkCount = 0;
        int invalidChunkCount = 0;
        String finishReason = "";
        String[] lines = responseBody.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String payload = trimmed.startsWith("data:") ? trimmed.substring(5).trim() : trimmed;
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            chunkCount++;
            if (!payload.startsWith("{")) {
                invalidChunkCount++;
                continue;
            }
            JsonNode root;
            try {
                root = objectMapper.readTree(payload);
            } catch (JsonProcessingException exception) {
                log.debug("Skipping unparsable AI stream chunk. chunkPreview={}", previewBody(payload));
                invalidChunkCount++;
                continue;
            }
            JsonNode choice = root.path("choices").path(0);
            finishReason = firstNonBlank(finishReason, extractFinishReason(choice));
            String reasoningChunk = extractStreamingChoiceReasoning(choice);
            if (!reasoningChunk.isEmpty()) {
                reasoningChunkCount++;
            }
            String chunk = extractStreamingChoiceContent(choice);
            if (!chunk.isEmpty()) {
                content.append(chunk);
                contentChunkCount++;
            }
        }
        return new ParsedStreamingContent(
                content.toString(),
                ExternalModelCallTelemetry.stream(
                        chunkCount,
                        contentChunkCount,
                        reasoningChunkCount,
                        invalidChunkCount,
                        finishReason
                )
        );
    }

    private String extractStreamingChoiceContent(JsonNode firstChoice) {
        String deltaContent = extractContentNode(firstChoice.path("delta").path("content"));
        if (!deltaContent.isEmpty()) {
            return deltaContent;
        }
        String messageContent = extractContentNode(firstChoice.path("message").path("content"));
        if (!messageContent.isEmpty()) {
            return messageContent;
        }
        return extractContentNode(firstChoice.path("text"));
    }

    private String extractStreamingChoiceReasoning(JsonNode firstChoice) {
        return firstNonBlank(
                extractContentNode(firstChoice.path("delta").path("reasoning_content")),
                extractContentNode(firstChoice.path("message").path("reasoning_content"))
        );
    }

    private String extractFinishReason(JsonNode firstChoice) {
        return extractContentNode(firstChoice.path("finish_reason"));
    }

    private String extractContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder content = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.hasNonNull("text")) {
                    content.append(node.get("text").asText());
                } else if (node.isTextual()) {
                    content.append(node.asText());
                }
            }
            return content.toString();
        }
        return contentNode.toString();
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private <T> T parseModelStagePayload(String rawContent, Class<T> payloadType) {
        String normalized = cleanupAiText(rawContent);
        lastModelStageRawContent.set(normalized);
        T repairedOutput = parseRepairedModelStagePayload(normalized, payloadType);
        if (repairedOutput != null) {
            return repairedOutput;
        }
        try {
            return objectMapper.readValue(normalized, payloadType);
        } catch (JsonProcessingException firstError) {
            repairedOutput = parseRepairedModelStagePayload(normalized, payloadType);
            if (repairedOutput != null) {
                return repairedOutput;
            }
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String objectPayload = normalized.substring(start, end + 1);
                try {
                    return objectMapper.readValue(objectPayload, payloadType);
                } catch (JsonProcessingException ignored) {
                    repairedOutput = parseRepairedModelStagePayload(objectPayload, payloadType);
                    if (repairedOutput != null) {
                        return repairedOutput;
                    }
                    log.warn("AI payload parsing failed. type={}, error={}, contentPreview={}",
                            payloadType.getSimpleName(),
                            ignored.getMessage(),
                            previewBody(normalized));
                }
            }
            return null;
        }
    }

    private <T> T parseRepairedModelStagePayload(String rawJson, Class<T> payloadType) {
        if ((payloadType != AdviceGenerationOutput.class && payloadType != FreeDiagnosisOutput.class)
                || rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!(root instanceof ObjectNode objectNode)) {
                return null;
            }
            boolean changed = payloadType == AdviceGenerationOutput.class
                    ? repairAdviceGenerationObjectNode(objectNode)
                    : repairFreeDiagnosisObjectNode(objectNode);
            if (!changed) {
                return null;
            }
            return objectMapper.treeToValue(objectNode, payloadType);
        } catch (JsonProcessingException exception) {
            log.warn("AI advice payload repair failed. error={}, contentPreview={}",
                    exception.getMessage(),
                    previewBody(rawJson));
            return null;
        }
    }

    private boolean repairFreeDiagnosisObjectNode(ObjectNode root) {
        boolean changed = false;
        changed |= renameFirstExisting(root, "issues", "diagnosisIssues", "findings", "rootCauses", "problems");
        changed |= wrapObjectFieldAsArray(root, "issues");
        changed |= repairFreeDiagnosisIssues(root, "issues");
        changed |= repairFreeDiagnosisIssues(root, "hypotheses");
        return changed;
    }

    private boolean repairFreeDiagnosisIssues(ObjectNode root, String arrayField) {
        JsonNode values = root.get(arrayField);
        if (values == null || !values.isArray()) {
            return false;
        }
        boolean changed = false;
        int index = 1;
        for (JsonNode value : values) {
            if (!(value instanceof ObjectNode issue)) {
                continue;
            }
            changed |= renameFirstExisting(issue, "title", "name", "issue", "problem", "rootCause");
            changed |= renameFirstExisting(issue, "whatHappened", "description", "currentIssue", "behaviorGap", "reason");
            changed |= renameFirstExisting(issue, "whyItMatters", "impact", "why", "whyImportant");
            changed |= repairSingleEvidenceRef(issue);
            if ("issues".equals(arrayField) && !issue.hasNonNull("issueId")) {
                issue.put("issueId", "I" + index);
                changed = true;
            }
            index++;
        }
        return changed;
    }

    private boolean renameFirstExisting(ObjectNode node, String targetField, String... aliasFields) {
        if (node == null || node.hasNonNull(targetField) || aliasFields == null) {
            return false;
        }
        for (String aliasField : aliasFields) {
            JsonNode value = node.get(aliasField);
            if (value != null && !value.isNull()) {
                node.set(targetField, value);
                if (!aliasField.equals(targetField)) {
                    node.remove(aliasField);
                }
                return true;
            }
        }
        return false;
    }

    private boolean wrapObjectFieldAsArray(ObjectNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (!(value instanceof ObjectNode)) {
            return false;
        }
        ArrayNode array = objectMapper.createArrayNode();
        array.add(value);
        root.set(fieldName, array);
        return true;
    }

    private boolean repairAdviceGenerationObjectNode(ObjectNode root) {
        boolean changed = false;
        changed |= repairAdviceStudentReport(root);
        changed |= repairAdviceDiagnosisDecision(root);
        changed |= repairAdviceDiagnosisCandidates(root);
        changed |= repairAdviceEvidenceRefs(root);
        changed |= repairAdviceNextStepPlan(root);
        changed |= repairAdviceLibraryGrowth(root);
        return changed;
    }

    private boolean repairAdviceStudentReport(ObjectNode root) {
        JsonNode studentReport = root.get("studentReport");
        if (studentReport == null || !studentReport.isTextual()) {
            return false;
        }
        String reportText = cleanupAiText(studentReport.asText());
        if (reportText.isBlank()) {
            return false;
        }
        ObjectNode repaired = objectMapper.createObjectNode();
        repaired.put("hintLevel", "L3");
        repaired.put("basicLayerText", stripInlineNextAction(reportText));
        repaired.put("improvementLayerText", "");
        repaired.put("nextActionText", extractInlineNextAction(reportText));
        root.set("studentReport", repaired);
        if (!root.hasNonNull("studentSummary")) {
            root.put("studentSummary", stripInlineNextAction(reportText));
        }
        return true;
    }

    private boolean repairAdviceDiagnosisDecision(ObjectNode root) {
        JsonNode decisionNode = root.get("diagnosisDecision");
        if (!(decisionNode instanceof ObjectNode decision)) {
            return false;
        }
        boolean changed = false;
        if (!decision.hasNonNull("libraryFit") && decision.hasNonNull("status")) {
            decision.set("libraryFit", decision.get("status"));
            changed = true;
        }
        if (!decision.has("anchors") && (decision.has("id") || decision.has("evidenceRefs") || decision.has("status"))) {
            ArrayNode anchors = objectMapper.createArrayNode();
            ObjectNode anchor = objectMapper.createObjectNode();
            if (decision.hasNonNull("id") && !decision.get("id").isNull()) {
                anchor.set("id", decision.get("id"));
            }
            String fit = decision.hasNonNull("libraryFit") ? decision.get("libraryFit").asText("") : "";
            anchor.put("type", "OUT_OF_LIBRARY".equalsIgnoreCase(fit) ? "OUT_OF_LIBRARY" : "KNOWLEDGE_NODE");
            anchor.put("role", "PRIMARY");
            if (decision.hasNonNull("confidence")) {
                anchor.set("confidence", decision.get("confidence"));
            } else {
                anchor.put("confidence", 0.75);
            }
            if (decision.has("evidenceRefs")) {
                anchor.set("evidenceRefs", ensureTextArray(decision.get("evidenceRefs")));
            }
            anchor.put("reason", decision.path("reason").asText("模型给出的诊断决策。"));
            anchors.add(anchor);
            decision.set("anchors", anchors);
            changed = true;
        }
        if (decision.has("libraryPath") && decision.get("libraryPath").isTextual()) {
            ArrayNode path = objectMapper.createArrayNode();
            path.add(decision.get("libraryPath").asText());
            decision.set("libraryPath", path);
            changed = true;
        }
        if (decision.has("id") || decision.has("status") || decision.has("evidenceRefs")
                || decision.has("confidence") || decision.has("reason") || decision.has("libraryPath")) {
            decision.remove(List.of("id", "status", "evidenceRefs", "confidence", "reason", "libraryPath"));
            changed = true;
        }
        return changed;
    }

    private boolean repairAdviceDiagnosisCandidates(ObjectNode root) {
        JsonNode candidatesNode = root.get("diagnosisCandidates");
        if (candidatesNode == null || !candidatesNode.isArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonNode node : candidatesNode) {
            if (!(node instanceof ObjectNode candidate)) {
                continue;
            }
            if (!candidate.hasNonNull("libraryFit") && candidate.hasNonNull("status")) {
                candidate.set("libraryFit", candidate.get("status"));
                changed = true;
            }
            if (!candidate.hasNonNull("anchorId") && candidate.hasNonNull("id")) {
                candidate.set("anchorId", candidate.get("id"));
                changed = true;
            }
            if (!candidate.hasNonNull("anchorType")) {
                String fit = candidate.path("libraryFit").asText("");
                candidate.put("anchorType", "OUT_OF_LIBRARY".equalsIgnoreCase(fit) ? "OUT_OF_LIBRARY" : "KNOWLEDGE_NODE");
                changed = true;
            }
            if (candidate.has("libraryPath") && candidate.get("libraryPath").isTextual()) {
                ArrayNode path = objectMapper.createArrayNode();
                path.add(candidate.get("libraryPath").asText());
                candidate.set("libraryPath", path);
                changed = true;
            }
            if (candidate.has("id") || candidate.has("status")) {
                candidate.remove(List.of("id", "status"));
                changed = true;
            }
        }
        return changed;
    }

    private boolean repairAdviceEvidenceRefs(ObjectNode root) {
        boolean changed = false;
        changed |= repairEvidenceRefs(root, "basicLayerAdvice");
        changed |= repairEvidenceRefs(root, "improvementLayerAdvice");
        changed |= repairEvidenceRefs(root, "diagnosisCandidates");
        JsonNode decisionNode = root.get("diagnosisDecision");
        if (decisionNode instanceof ObjectNode decision) {
            changed |= repairEvidenceRefs(decision, "anchors");
        }
        JsonNode growthNode = root.get("libraryGrowth");
        if (growthNode instanceof ObjectNode growth) {
            changed |= repairEvidenceRefs(growth, "candidates");
        }
        return changed;
    }

    private boolean repairEvidenceRefs(ObjectNode root, String arrayField) {
        JsonNode values = root.get(arrayField);
        if (values == null || !values.isArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonNode value : values) {
            if (value instanceof ObjectNode objectNode) {
                changed |= repairSingleEvidenceRef(objectNode);
            }
        }
        return changed;
    }

    private boolean repairSingleEvidenceRef(ObjectNode node) {
        if (!node.has("evidenceRef")) {
            return false;
        }
        JsonNode evidenceRef = node.get("evidenceRef");
        if (!node.has("evidenceRefs")) {
            node.set("evidenceRefs", ensureTextArray(evidenceRef));
        }
        node.remove("evidenceRef");
        return true;
    }

    private boolean repairAdviceNextStepPlan(ObjectNode root) {
        JsonNode nextStepPlan = root.get("nextStepPlan");
        if (nextStepPlan == null || !nextStepPlan.isArray()) {
            return false;
        }
        boolean changed = false;
        int step = 1;
        for (JsonNode node : nextStepPlan) {
            if (!(node instanceof ObjectNode item)) {
                continue;
            }
            if (!item.hasNonNull("target")) {
                JsonNode action = firstExisting(item, "action", "task", "studentAction", "nextAction");
                if (action != null) {
                    item.set("target", action);
                    changed = true;
                }
            }
            if (!item.hasNonNull("step")) {
                item.put("step", step);
                changed = true;
            }
            if (item.has("action") || item.has("task") || item.has("studentAction") || item.has("nextAction")) {
                item.remove(List.of("action", "task", "studentAction", "nextAction"));
                changed = true;
            }
            step++;
        }
        return changed;
    }

    private JsonNode firstExisting(ObjectNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private boolean repairAdviceLibraryGrowth(ObjectNode root) {
        JsonNode growthNode = root.get("libraryGrowth");
        if (!(growthNode instanceof ObjectNode growth)) {
            return false;
        }
        JsonNode candidatesNode = growth.get("candidates");
        if (candidatesNode == null || !candidatesNode.isArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonNode node : candidatesNode) {
            if (!(node instanceof ObjectNode candidate)) {
                continue;
            }
            if (candidate.has("suggestedPath") && candidate.get("suggestedPath").isTextual()) {
                ArrayNode path = objectMapper.createArrayNode();
                path.add(candidate.get("suggestedPath").asText());
                candidate.set("suggestedPath", path);
                changed = true;
            }
            if (!candidate.hasNonNull("status")) {
                candidate.put("status", "NEEDS_REVIEW");
                changed = true;
            }
        }
        return changed;
    }

    private ArrayNode ensureTextArray(JsonNode node) {
        ArrayNode array = objectMapper.createArrayNode();
        if (node == null || node.isNull()) {
            return array;
        }
        if (node.isArray()) {
            node.forEach(array::add);
            return array;
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            array.add(node.asText());
        }
        return array;
    }

    private String stripInlineNextAction(String text) {
        String normalized = cleanupAiText(text);
        int index = inlineNextActionIndex(normalized);
        if (index < 0) {
            return normalized;
        }
        return normalized.substring(0, index).trim();
    }

    private String extractInlineNextAction(String text) {
        String normalized = cleanupAiText(text);
        int index = inlineNextActionIndex(normalized);
        if (index < 0) {
            return "先复核模型指出的证据。";
        }
        String action = normalized.substring(index)
                .replaceFirst("^下一步(?:动作|建议|行动)?[：:]?", "")
                .trim();
        return action.isBlank() ? "先复核模型指出的证据。" : action;
    }

    private int inlineNextActionIndex(String text) {
        String normalized = cleanupAiText(text);
        int best = -1;
        for (String marker : List.of("下一步动作", "下一步建议", "下一步行动", "下一步")) {
            int index = normalized.indexOf(marker);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private String previewBody(String body) {
        String normalized = body == null ? "" : body.replace("\r", "").replace("\n", "\\n").trim();
        if (normalized.length() <= 320) {
            return normalized;
        }
        return normalized.substring(0, 317) + "...";
    }

    private List<String> cleanList(List<String> candidate, List<String> defaultValues) {
        List<String> source = candidate == null || candidate.isEmpty() ? defaultValues : candidate;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String normalized = cleanupAiText(item);
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private String truncateSourceCode(String sourceCode) {
        return sourceCode == null ? "" : sourceCode;
    }

    private String truncateText(String value, int maxLength) {
        String text = cleanupAiText(value);
        int limit = Math.max(0, maxLength);
        if (limit == 0 || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit).trim() + "...";
    }

    private String buildLineAwareSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }

        String normalized = sourceCode.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            String numberedLine = (index + 1) + ": " + lines[index];
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(numberedLine);
        }
        return builder.toString();
    }

    private int countSourceLines(String sourceCode) {
        if (sourceCode == null) {
            return 0;
        }
        return sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).length;
    }

    private String cleanupAiText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = stripReasoningBlocks(text.trim());
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        normalized = normalized.replace("<think>", "")
                .replace("</think>", "")
                .trim();
        return normalized;
    }

    private String stripReasoningBlocks(String text) {
        String normalized = text;
        normalized = normalized.replaceAll("(?is)<think>.*?</think>", "");
        normalized = normalized.replaceAll("(?is)```(?:thinking|thought|analysis)[^\\n]*\\n.*?```", "");
        normalized = normalized.replaceAll("(?is)^思考过程[:：].*?(?=\\n#|\\n##|\\n\\{|\\Z)", "");
        return normalized.trim();
    }

    private String defaultIfBlank(String candidate, String defaultValue) {
        String normalized = cleanupAiText(candidate);
        if (normalized.isBlank()) {
            return defaultValue;
        }
        return normalized;
    }

    private Double resolveConfidence(Double candidate, Double defaultValue) {
        if (candidate == null || candidate.isNaN() || candidate < 0 || candidate > 1) {
            return defaultValue;
        }
        return candidate;
    }

    private String resolveAnswerLeakRisk(String candidate, String defaultRisk) {
        String normalized = cleanupAiText(candidate).toUpperCase();
        if ("LOW".equals(normalized) || "MEDIUM".equals(normalized) || "HIGH".equals(normalized)) {
            return normalized;
        }
        return defaultRisk == null || defaultRisk.isBlank() ? "UNKNOWN" : defaultRisk;
    }

    private SubmissionAnalysisResponse.AiInvocation modelInvocation(SubmissionAnalysisResponse baseline,
                                                                    String status,
                                                                    String promptVersion,
                                                                    ExternalModelAgentRuntime.RuntimePlan runtimePlan,
                                                                    ExternalModelStagePayloads.StageValidationResult failure) {
        ExternalModelCallTelemetry telemetry = lastCallTelemetry.get();
        int requestBytes = telemetry.requestBytes() == null ? 0 : telemetry.requestBytes();
        String invocationModel = defaultIfBlank(activeModel.get(), model);
        return SubmissionAnalysisResponse.AiInvocation.builder()
                .provider(PROVIDER)
                .model(invocationModel)
                .modelVersion(invocationModel)
                .promptVersion(promptVersion)
                .agentVersion(baseline == null || baseline.getAiInvocation() == null
                        ? null
                        : baseline.getAiInvocation().getAgentVersion())
                .analysisSchemaVersion(baseline == null ? "diagnosis-v1" : baseline.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(baseline == null ? DiagnosisEvidencePackage.SCHEMA_VERSION : baseline.getEvidenceSchemaVersion())
                .taxonomyVersion(baseline == null ? null : baseline.getTaxonomyVersion())
                .status(status)
                .fallbackUsed(false)
                .runtimeMode(runtimeModeForPrompt(promptVersion))
                .runtimeProfile(defaultIfBlank(telemetry.runtimeProfile(),
                        ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD))
                .requestBytes(requestBytes)
                .requestCompact(Boolean.TRUE.equals(telemetry.requestCompact()))
                .failureStage(failure == null ? "" : defaultIfBlank(failure.getStage(), "UNKNOWN_STAGE"))
                .failureReason(failure == null || failure.getFailureReason() == null
                        ? ""
                        : failure.getFailureReason().name())
                .transportMode(telemetry.transportMode())
                .streamChunkCount(telemetry.streamChunkCount())
                .streamContentChunkCount(telemetry.streamContentChunkCount())
                .streamReasoningChunkCount(telemetry.streamReasoningChunkCount())
                .streamInvalidChunkCount(telemetry.streamInvalidChunkCount())
                .streamFinishReason(telemetry.streamFinishReason())
                .streamFallbackRetryUsed(Boolean.TRUE.equals(telemetry.streamFallbackRetryUsed())
                        || Boolean.TRUE.equals(structuredRetryUsedInInvocation.get()))
                .standardLibraryNavigationEnabled(standardLibraryNavigation(runtimePlan).enabled())
                .standardLibraryNavigationStatus(standardLibraryNavigation(runtimePlan).status())
                .standardLibraryNavigationSelectedCount(standardLibraryNavigation(runtimePlan).selectedCount())
                .standardLibraryNavigationFailureReason(standardLibraryNavigation(runtimePlan).failureReason())
                .adviceGenerationStatus(adviceGeneration(runtimePlan).status())
                .adviceGenerationFailureReason(adviceGeneration(runtimePlan).failureReason())
                .basicAdviceCount(adviceGeneration(runtimePlan).basicAdviceCount())
                .improvementAdviceCount(adviceGeneration(runtimePlan).improvementAdviceCount())
                .advicePromptVersion(adviceGeneration(runtimePlan).promptVersion())
                .diagnosisPromptVersion(promptVersion)
                .studentReportLength(studentReportLength(runtimePlan))
                .answerLeakRisk(answerLeakRisk(runtimePlan))
                .libraryFit(diagnosisLibraryFit(runtimePlan))
                .diagnosisLibraryFit(diagnosisLibraryFit(runtimePlan))
                .diagnosisSoftFixes(diagnosisSoftFixes(runtimePlan))
                .diagnosisHardFailures(diagnosisHardFailures(runtimePlan))
                .build();
    }

    private Integer studentReportLength(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput.StudentReport report = studentReport(runtimePlan);
        if (report == null) {
            return 0;
        }
        return cleanupAiText(report.getBasicLayerText()).length()
                + cleanupAiText(report.getImprovementLayerText()).length()
                + cleanupAiText(report.getNextActionText()).length();
    }

    private String answerLeakRisk(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput.StudentReport report = studentReport(runtimePlan);
        if (report == null) {
            return "UNKNOWN";
        }
        return ModelOutputSafetyPolicy.containsUnsafeLeak(
                report.getBasicLayerText(),
                report.getImprovementLayerText(),
                report.getNextActionText()
        ) ? "HIGH" : "LOW";
    }

    private AdviceGenerationOutput.StudentReport studentReport(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput output = adviceGeneration(runtimePlan).output();
        return output == null ? null : output.getStudentReport();
    }

    private String diagnosisLibraryFit(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput output = adviceGeneration(runtimePlan).output();
        return output == null || output.getDiagnosisDecision() == null
                ? ""
                : cleanupAiText(output.getDiagnosisDecision().getLibraryFit());
    }

    private List<String> diagnosisSoftFixes(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput output = adviceGeneration(runtimePlan).output();
        return output == null || output.getTeacherTrace() == null || output.getTeacherTrace().getSoftFixes() == null
                ? List.of()
                : output.getTeacherTrace().getSoftFixes();
    }

    private List<String> diagnosisHardFailures(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        AdviceGenerationOutput output = adviceGeneration(runtimePlan).output();
        return output == null || output.getTeacherTrace() == null || output.getTeacherTrace().getHardFailures() == null
                ? List.of()
                : output.getTeacherTrace().getHardFailures();
    }

    private StandardLibraryNavigationResult standardLibraryNavigation(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan == null || runtimePlan.getStandardLibraryNavigationResult() == null) {
            return StandardLibraryNavigationResult.disabled();
        }
        return runtimePlan.getStandardLibraryNavigationResult();
    }

    private AdviceGenerationResult adviceGeneration(ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (runtimePlan == null || runtimePlan.getAdviceGenerationResult() == null) {
            return AdviceGenerationResult.disabled();
        }
        return runtimePlan.getAdviceGenerationResult();
    }

    private String runtimeModeForPrompt(String promptVersion) {
        String version = cleanupAiText(promptVersion);
        if (PromptTemplateRegistry.DIAGNOSIS_AND_ADVICE_V1.equals(version)) {
            return "advice-generation";
        }
        if (PromptTemplateRegistry.DIAGNOSIS_REPORT_V2.equals(version)
                || PromptTemplateRegistry.DIAGNOSIS_REPORT_V3.equals(version)
                || PromptTemplateRegistry.DIAGNOSIS_REPORT_V4.equals(version)) {
            return "diagnosis-report";
        }
        return "external-model";
    }

    private record ParsedStreamingContent(String content, ExternalModelCallTelemetry telemetry) {
    }

    private record ExternalModelRequestContext(String runtimeProfile, boolean requestCompact) {
        ExternalModelRequestContext {
            runtimeProfile = runtimeProfile == null || runtimeProfile.isBlank()
                    ? ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD
                    : runtimeProfile;
        }

        static ExternalModelRequestContext standard() {
            return new ExternalModelRequestContext(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD, false);
        }
    }

    private record ExternalModelCallTelemetry(String transportMode,
                                              Integer streamChunkCount,
                                              Integer streamContentChunkCount,
                                              Integer streamReasoningChunkCount,
                                              Integer streamInvalidChunkCount,
                                              String streamFinishReason,
                                              Boolean streamFallbackRetryUsed,
                                              Integer requestBytes,
                                              String runtimeProfile,
                                              Boolean requestCompact) {
        static ExternalModelCallTelemetry empty() {
            return request(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD, false, 0);
        }

        static ExternalModelCallTelemetry request(String runtimeProfile,
                                                  boolean requestCompact,
                                                  int requestBytes) {
            return new ExternalModelCallTelemetry(
                    "",
                    0,
                    0,
                    0,
                    0,
                    "",
                    false,
                    Math.max(0, requestBytes),
                    runtimeProfile == null || runtimeProfile.isBlank()
                            ? ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD
                            : runtimeProfile,
                    requestCompact
            );
        }

        static ExternalModelCallTelemetry nonStream(String finishReason) {
            return new ExternalModelCallTelemetry(
                    "non-stream",
                    0,
                    0,
                    0,
                    0,
                    finishReason == null ? "" : finishReason,
                    false,
                    0,
                    ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD,
                    false
            );
        }

        static ExternalModelCallTelemetry stream(int chunkCount,
                                                 int contentChunkCount,
                                                 int reasoningChunkCount,
                                                 int invalidChunkCount,
                                                 String finishReason) {
            return new ExternalModelCallTelemetry(
                    "stream",
                    Math.max(0, chunkCount),
                    Math.max(0, contentChunkCount),
                    Math.max(0, reasoningChunkCount),
                    Math.max(0, invalidChunkCount),
                    finishReason == null ? "" : finishReason,
                    false,
                    0,
                    ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD,
                    false
            );
        }

        ExternalModelCallTelemetry withFallbackRetryUsed(boolean fallbackRetryUsed) {
            return new ExternalModelCallTelemetry(
                    transportMode,
                    streamChunkCount,
                    streamContentChunkCount,
                    streamReasoningChunkCount,
                    streamInvalidChunkCount,
                    streamFinishReason,
                    fallbackRetryUsed,
                    requestBytes,
                    runtimeProfile,
                    requestCompact
            );
        }

        ExternalModelCallTelemetry withRequestTelemetry(int requestBytes,
                                                        String runtimeProfile,
                                                        boolean requestCompact) {
            return new ExternalModelCallTelemetry(
                    transportMode,
                    streamChunkCount,
                    streamContentChunkCount,
                    streamReasoningChunkCount,
                    streamInvalidChunkCount,
                    streamFinishReason,
                    streamFallbackRetryUsed,
                    Math.max(0, requestBytes),
                    runtimeProfile == null || runtimeProfile.isBlank()
                            ? ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD
                            : runtimeProfile,
                    requestCompact
            );
        }
    }

    private Map<String, Object> compactProblemContext(Problem problem) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (problem == null) {
            return context;
        }
        context.put("id", problem.getId());
        context.put("title", problem.getTitle());
        context.put("description", problem.getDescription());
        context.put("difficulty", problem.getDifficulty() == null ? "" : problem.getDifficulty().name());
        context.put("timeLimit", problem.getTimeLimit());
        context.put("memoryLimit", problem.getMemoryLimit());
        context.put("knowledgePoints", problem.getKnowledgePoints());
        context.put("algorithmStrategies", problem.getAlgorithmStrategies());
        context.put("commonMistakes", problem.getCommonMistakes());
        context.put("boundaryTypes", problem.getBoundaryTypes());
        return context;
    }

    private Map<String, Object> compactSubmissionContext(Submission submission,
                                                         DiagnosisEvidencePackage evidencePackage) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (submission == null) {
            return context;
        }
        context.put("id", submission.getId());
        context.put("language", submission.getLanguageName());
        context.put("verdict", submission.getVerdict() == null ? "UNKNOWN" : submission.getVerdict().name());
        context.put("compileOutput", defaultIfBlank(submission.getCompileOutput(), ""));
        context.put("runtimeErrorMessage", defaultIfBlank(submission.getErrorMessage(), ""));
        context.put("sourceCodeWithLineNumbers", evidencePackage == null || evidencePackage.getSubmission() == null
                ? buildLineAwareSourceCode(submission.getSourceCode())
                : evidencePackage.getSubmission().getSourceCodeWithLineNumbers());
        context.put("sourceCodeLineCount", evidencePackage == null || evidencePackage.getSubmission() == null
                ? countSourceLines(submission.getSourceCode())
                : evidencePackage.getSubmission().getSourceCodeLineCount());
        return context;
    }

    private record NavigationLoopResult(StandardLibraryNavigationOutput output,
                                        ExternalModelStagePayloads.StageValidationResult failure) {
        static NavigationLoopResult success(StandardLibraryNavigationOutput output) {
            return new NavigationLoopResult(output, null);
        }

        static NavigationLoopResult failure(ExternalModelStagePayloads.StageValidationResult failure) {
            return new NavigationLoopResult(null, failure);
        }

        static NavigationLoopResult empty() {
            return new NavigationLoopResult(null, null);
        }
    }

    private record NavigationExpansionBatch(
            List<AiStandardLibraryNavigationExpansionResponse> expandedNodes,
            List<AiStandardLibraryDiagnosticLayerResponse> diagnosticLayers) {
    }

    private static final class StageValidationException extends IllegalStateException {
        private final ExternalModelStagePayloads.StageValidationResult validationResult;

        private StageValidationException(ExternalModelStagePayloads.StageValidationResult validationResult) {
            super(validationResult == null ? "stage validation failed" : validationResult.getMessage());
            this.validationResult = validationResult;
        }

        private ExternalModelStagePayloads.StageValidationResult validationResult() {
            return validationResult;
        }
    }

    private <T> T executeWorkflowStage(String stageKey,
                                       String stageType,
                                       String issueId,
                                       Object input,
                                       Class<T> outputType,
                                       String promptVersion,
                                       AiDiagnosisWorkflowService.StageCallable<T> callable) throws Exception {
        Long runId = AiDiagnosisWorkflowContext.currentRunId().orElse(null);
        if (diagnosisWorkflowService == null || runId == null) {
            return callable.call();
        }
        return diagnosisWorkflowService.executeStage(
                runId,
                stageKey,
                stageType,
                issueId,
                input,
                outputType,
                PROVIDER,
                defaultIfBlank(activeModel.get(), model),
                promptVersion,
                callable
        );
    }

    private String stableStageSegment(String value) {
        String cleaned = cleanupAiText(value).replaceAll("[^A-Za-z0-9_.-]", "_");
        if (cleaned.isBlank()) {
            return "UNKNOWN";
        }
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100);
    }

    private <T> T withBranchContext(Long runId,
                                    Consumer<ModelCallTraceEvent> traceSink,
                                    Supplier<T> supplier) {
        Consumer<ModelCallTraceEvent> previousTraceSink = MODEL_CALL_TRACE_SINK.get();
        if (traceSink == null) {
            MODEL_CALL_TRACE_SINK.remove();
        } else {
            MODEL_CALL_TRACE_SINK.set(traceSink);
        }
        try (AiDiagnosisWorkflowContext.Scope ignored = AiDiagnosisWorkflowContext.activate(runId)) {
            return supplier.get();
        } finally {
            if (previousTraceSink == null) {
                MODEL_CALL_TRACE_SINK.remove();
            } else {
                MODEL_CALL_TRACE_SINK.set(previousTraceSink);
            }
        }
    }

}
