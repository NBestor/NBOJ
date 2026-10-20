package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReportServiceExternalRuntimeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void externalRuntimeCompletesAdviceGenerationWithStandardLibraryNavigation() {
        StubAiReportService service = newService(validAdviceResponse());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(4);
        assertThat(service.lastSystemPrompt()).contains("diagnosis-report-v4");
        assertThat(service.lastSystemPrompt())
                .contains("diagnosisDecision")
                .doesNotContain("teachingHint");
        assertThat(service.lastUserPrompt())
                .contains("task", "contextPolicy", "brief", "issues", "libraryAnchors", "standardLibrary")
                .contains("标准库")
                .contains("不能给完整答案")
                .contains("\"status\":\"LAYERED_ATTACHMENT\"");
        assertThat(analysis.getSourceType()).isEqualTo("MODEL_SCOPE_EXTERNAL_MODEL");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(analysis.getAiInvocation().getPromptVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(analysis.getAiInvocation().getRuntimeMode()).isEqualTo("diagnosis-report");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEmpty();
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> assertThat(item.getMistakePointId()).isEqualTo("MP_RANGE_RIGHT_ENDPOINT_MISSING"));
        assertThat(analysis.getStudentFeedback().getBlockingIssues()).singleElement()
                .satisfies(issue -> assertThat(issue.getTitle()).contains("循环右边界"));
        assertThat(analysis.getReportMarkdown()).contains("## AI 完整诊断与建议", "### 基础层", "### 提高层");
    }

    @Test
    void adviceValidationFailureFallsBackToRuleResult() {
        StubAiReportService service = newService("""
                {
                  "caseUnderstanding": {
                    "problemGoal": "输出 1 到 n 的整数和。",
                    "codeIntent": "学生想用循环累加。",
                    "behaviorGap": "循环没有覆盖末端。",
                    "primaryEvidenceRef": "invented:evidence"
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "OFF_BY_ONE",
                    "skillUnitId": null,
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求。",
                    "whyItMatters": "结果会偏小。",
                    "studentAction": "先手推最小样例。",
                    "checkQuestion": "末端有没有进入循环？",
                    "evidenceRefs": ["invented:evidence"],
                    "confidence": 0.9
                  }],
                  "improvementLayerAdvice": [],
                  "nextStepPlan": [{
                    "step": 1,
                    "target": "手推循环变量取值。",
                    "reason": "这是当前阻塞通过的问题。",
                    "evidenceRef": "invented:evidence"
                  }],
                  "studentSummary": "边界没有对齐。"
                }
                """);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getSourceType()).isEqualTo("MODEL_SCOPE_EXTERNAL_MODEL");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_FAILED");
        assertThat(analysis.getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(analysis.getAiInvocation().getFailureStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
        assertThat(analysis.getAiInvocation().getFailureReason()).contains("INVALID_EVIDENCE_REF");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("FAILED");
        assertThat(analysis.getIssueTags()).isEmpty();
        assertThat(analysis.getFineGrainedTags()).isEmpty();
        assertThat(analysis.getLineIssues()).isEmpty();
        assertThat(analysis.getUncertainty()).contains("未使用本地规则兜底");
    }

    @Test
    void providerFailureDoesNotShortCircuitLaterGrowthReport() {
        StubAiReportService service = new StubAiReportService(
                objectMapper,
                runtime(),
                new IOException("AI API returned status 429: {\"error\":{\"code\":\"insufficient_quota\"}}"),
                new IOException("AI API returned status 429: {\"error\":{\"code\":\"insufficient_quota\"}}")
        );
        configure(service);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getSourceType()).isEqualTo("MODEL_SCOPE_EXTERNAL_MODEL");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_FAILED");
        assertThat(analysis.getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(analysis.getAiInvocation().getRuntimeMode()).isEqualTo("diagnosis-report");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEqualTo("FREE_DIAGNOSIS");
        assertThat(analysis.getAiInvocation().getFailureReason()).isEqualTo("INSUFFICIENT_QUOTA");
        assertThat(analysis.getUncertainty()).contains("INSUFFICIENT_QUOTA");
        assertThat(analysis.getIssueTags()).isEmpty();
        assertThat(service.callCount()).isEqualTo(1);

        String markdown = service.enhanceGrowthReportMarkdown(problem(), List.of());

        assertThat(markdown).contains("AI 成长报告", "暂不可用", "INSUFFICIENT_QUOTA", "未使用本地报告兜底");
        assertThat(service.callCount()).isEqualTo(2);
    }

    @Test
    void compatibleRequestMergesSystemPromptForModelScope() throws Exception {
        CapturingAiReportService service = new CapturingAiReportService(
                objectMapper,
                apiResponse(freeDiagnosisResponse()),
                apiResponse(attachmentSelectResponse("BASIC")),
                apiResponse(attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING", "TESTING_HABIT")),
                apiResponse(validAdviceResponse())
        );
        configure(service);
        ReflectionTestUtils.setField(service, "streamEnabled", false);
        ReflectionTestUtils.setField(service, "modelScopeCompatibleRequest", "true");

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(service.lastRequestBody()).contains("diagnosis-report-v4");
        assertThat(service.lastRequestBody()).contains("\"role\":\"user\"");
        assertThat(service.lastRequestBody()).doesNotContain("\"role\":\"system\"");
    }

    @Test
    void smokeFallsBackToNextModelAfterProviderLimit() throws Exception {
        CapturingAiReportService service = new CapturingAiReportService(
                objectMapper,
                new IOException("AI API returned status 429: {\"error\":{\"message\":\"rate limit\"}}"),
                apiResponse("OK")
        );
        configure(service);
        ReflectionTestUtils.setField(service, "model", "primary-model");
        ReflectionTestUtils.setField(service, "modelPool", "primary-model,backup-model");
        ReflectionTestUtils.setField(service, "streamEnabled", false);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 1);

        String result = service.smokeChatCompletion();

        assertThat(result).isEqualTo("OK");
        assertThat(service.requestBodies()).hasSize(2);
        assertThat(service.requestBodies().get(0)).contains("\"model\":\"primary-model\"");
        assertThat(service.requestBodies().get(1)).contains("\"model\":\"backup-model\"");
        assertThat(service.modelName()).isEqualTo("backup-model");
    }

    private StubAiReportService newService(Object... responses) {
        StubAiReportService service = new StubAiReportService(objectMapper, runtime(), withNavigationResponses(responses));
        configure(service);
        return service;
    }

    private void configure(AiReportService service) {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api-inference.modelscope.cn/v1");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled", true);
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 1);
    }

    private ExternalModelAgentRuntime runtime() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        return new ExternalModelAgentRuntime(
                new ModelDiagnosisBriefBuilder(),
                new StandardLibraryPackBuilder(taxonomy),
                new PromptTemplateRegistry(),
                new ModelOutputValidator()
        );
    }

    private Problem problem() {
        return Problem.builder()
                .id(1L)
                .title("求 1 到 n 的和")
                .description("输入正整数 n，输出 1 到 n 的整数和。")
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
    }

    private Submission submission() {
        return Submission.builder()
                .id(11L)
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
    }

    private DiagnosisEvidencePackage evidencePackage() {
        return DiagnosisEvidencePackage.builder()
                .schemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .problem(DiagnosisEvidencePackage.ProblemEvidence.builder()
                        .title("求 1 到 n 的和")
                        .description("输入正整数 n，输出 1 到 n 的整数和。")
                        .build())
                .submission(DiagnosisEvidencePackage.SubmissionEvidence.builder()
                        .id(11L)
                        .language("Python 3")
                        .verdict("WRONG_ANSWER")
                        .sourceCode(submission().getSourceCode())
                        .sourceCodeWithLineNumbers("""
                                1: n = int(input())
                                2: total = 0
                                3: for i in range(1, n):
                                4:     total += i
                                5: print(total)
                                """)
                        .sourceCodeLineCount(5)
                        .build())
                .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                        .hiddenFailureObserved(false)
                        .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                .testCaseNumber(1)
                                .hidden(false)
                                .input("1")
                                .expectedOutput("1")
                                .actualOutput("0")
                                .build())
                        .build())
                .build();
    }

    private SubmissionAnalysisResponse fallback() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(11L)
                .analysisSchemaVersion("diagnosis-v1")
                .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .headline("规则层初步诊断")
                .summary("规则层认为可能存在循环边界问题。")
                .issueTags(List.of("LOOP_BOUNDARY"))
                .fineGrainedTags(List.of("OFF_BY_ONE"))
                .abilityPoints(List.of("循环边界"))
                .focusPoints(List.of("range 右边界"))
                .fixDirections(List.of("手推最小样例"))
                .evidenceRefs(List.of("code:range_excludes_n"))
                .studentHint("先手推最小样例。")
                .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                        .hintLevel("L2")
                        .problemType("循环边界")
                        .evidenceAnchor("code:range_excludes_n")
                        .nextAction("手推 range。")
                        .coachQuestion("右边界包含吗？")
                        .teachingAction("TRACE_VARIABLES")
                        .evidenceRefs(List.of("code:range_excludes_n"))
                        .answerLeakRisk("LOW")
                        .build())
                .confidence(0.72)
                .uncertainty("规则层初步判断。")
                .answerLeakRisk("LOW")
                .lineIssues(List.of(SubmissionAnalysisResponse.LineIssue.builder()
                        .lineNumber(3)
                        .error("循环未覆盖 n")
                        .suggestion("核对 range 的右边界")
                        .build()))
                .build();
    }

    private Object[] withNavigationResponses(Object... adviceResponses) {
        List<Object> responses = new ArrayList<>();
        responses.add(freeDiagnosisResponse());
        responses.add(attachmentSelectResponse("BASIC"));
        responses.add(attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING", "TESTING_HABIT"));
        responses.addAll(List.of(adviceResponses));
        return responses.toArray(Object[]::new);
    }

    private String apiResponse(String content) {
        return """
                {
                  "choices": [{
                    "message": {
                      "content": %s
                    },
                    "finish_reason": "stop"
                  }]
                }
                """.formatted(objectMapper.valueToTree(content).toString());
    }

    private String freeDiagnosisResponse() {
        return """
                {
                  "problemUnderstanding": "题目要求输出 1 到 n 的整数和。",
                  "codeIntent": "学生想用循环累加 total。",
                  "behaviorGap": "循环没有覆盖题目要求的末端。",
                  "issues": [{
                    "issueId": "I1",
                    "title": "循环右边界漏取",
                    "whatHappened": "range(1, n) 没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "右端点漏取会让求和结果偏小。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "severity": "MAJOR",
                    "confidence": 0.9
                  }],
                  "navigationIntent": {
                    "preferredDirections": ["基础语法", "循环边界"],
                    "reason": "当前错因首先落在循环边界。"
                  }
                }
                """;
    }

    private String attachmentSelectResponse(String... codes) {
        String renderedCodes = List.of(codes).stream()
                .map(code -> "\"" + code + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return """
                {
                  "action": "SELECT",
                  "codes": [%s],
                  "reason": "该分支最贴近循环边界问题。",
                  "confidence": 0.86
                }
                """.formatted(renderedCodes);
    }

    private String validAdviceResponse() {
        return """
                {
                  "caseUnderstanding": {
                    "problemGoal": "题目要求输出 1 到 n 的整数和。",
                    "codeIntent": "学生使用循环累加 total。",
                    "behaviorGap": "循环实际没有覆盖题目要求的末端。",
                    "primaryEvidenceRef": "code:range_excludes_n"
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "少处理一个端点会让求和结果偏小。",
                    "studentAction": "先手推 n=1 和 n=2 时循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.92
                  }],
                  "improvementLayerAdvice": [{
                    "improvementPointId": "TESTING_HABIT",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "补充边界样例意识",
                    "currentLimit": "这类问题不是算法方向错，而是边界验证不足。",
                    "suggestion": "修复后补测最小值、端点值和最大值附近样例。",
                    "studentBenefit": "能更早发现开闭区间和下标边界问题。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.8
                  }],
                  "nextStepPlan": [{
                    "step": 1,
                    "target": "手推循环变量取值。",
                    "reason": "这是当前阻塞通过的主要问题。",
                    "evidenceRef": "code:range_excludes_n"
                  }],
                  "studentSummary": "这次主要卡在循环边界和题目要求范围没有对齐。"
                }
                """;
    }

    private static class StubAiReportService extends AiReportService {
        private final Queue<Object> responses = new ArrayDeque<>();
        private final List<String> systemPrompts = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();
        private int callCount;

        StubAiReportService(ObjectMapper objectMapper,
                            ExternalModelAgentRuntime runtime,
                            Object... responses) {
            this(objectMapper, runtime, navigationDeps(), responses);
        }

        StubAiReportService(ObjectMapper objectMapper,
                            ExternalModelAgentRuntime runtime,
                            NavigationDeps navigationDeps,
                            Object... responses) {
            super(objectMapper,
                    new AiCodeAssistSupport(),
                    runtime,
                    new ExternalModelFailureClassifier(),
                    new ExternalModelChatRequestFactory(),
                    null,
                    navigationDeps.standardLibraryService(),
                    new StandardLibraryNavigationOutputValidator(),
                    new StandardLibraryNavigationPackBuilder(
                            navigationDeps.standardLibraryService(),
                            navigationDeps.knowledgeRepository()));
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected String chatCompletion(String systemPrompt, String userPrompt) throws IOException {
            if (systemPrompt.contains("teacher-insight-v1")) {
                return teacherInsightResponse(userPrompt);
            }
            callCount++;
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            return pollResponse();
        }

        @Override
        protected String chatCompletionWithOverrides(String systemPrompt,
                                                     String userPrompt,
                                                     boolean stream,
                                                     int outputTokens) throws IOException {
            if (systemPrompt.contains("teacher-insight-v1")) {
                return teacherInsightResponse(userPrompt);
            }
            callCount++;
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            return pollResponse();
        }

        private String teacherInsightResponse(String userPrompt) {
            java.util.LinkedHashSet<String> issueIds = new java.util.LinkedHashSet<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\\"issueId\\\":\\\"([^\\\"]+)\\\"")
                    .matcher(userPrompt);
            while (matcher.find()) {
                issueIds.add(matcher.group(1));
            }
            String observations = issueIds.stream()
                    .map(id -> "{\"issueId\":\"" + id
                            + "\",\"teachingObservation\":\"继续观察该问题的证据变化。\","
                            + "\"evidenceRefs\":[],\"priority\":1}")
                    .collect(java.util.stream.Collectors.joining(","));
            return "{\"summary\":\"本次教师观察与核心诊断一致。\",\"issueObservations\":["
                    + observations + "],\"uncertainty\":\"\"}";
        }

        private String pollResponse() throws IOException {
            Object response = responses.poll();
            if (response instanceof IOException exception) {
                throw exception;
            }
            if (response == null) {
                throw new IOException("No stub response configured.");
            }
            return response.toString();
        }

        int callCount() {
            return callCount;
        }

        String lastSystemPrompt() {
            return systemPrompts.isEmpty() ? "" : systemPrompts.get(systemPrompts.size() - 1);
        }

        String lastUserPrompt() {
            return userPrompts.isEmpty() ? "" : userPrompts.get(userPrompts.size() - 1);
        }
    }

    private static class CapturingAiReportService extends AiReportService {
        private final Queue<Object> responses = new ArrayDeque<>();
        private final List<String> requestBodies = new ArrayList<>();

        CapturingAiReportService(ObjectMapper objectMapper, Object... responses) {
            this(objectMapper, navigationDeps(), responses);
        }

        private CapturingAiReportService(ObjectMapper objectMapper,
                                         NavigationDeps navigationDeps,
                                         Object... responses) {
            super(objectMapper,
                    new AiCodeAssistSupport(),
                    new ExternalModelAgentRuntime(
                            new ModelDiagnosisBriefBuilder(),
                            new StandardLibraryPackBuilder(new DiagnosisTaxonomy()),
                            new PromptTemplateRegistry(),
                            new ModelOutputValidator()
                    ),
                    new ExternalModelFailureClassifier(),
                    new ExternalModelChatRequestFactory(),
                    null,
                    navigationDeps.standardLibraryService(),
                    new StandardLibraryNavigationOutputValidator(),
                    new StandardLibraryNavigationPackBuilder(
                            navigationDeps.standardLibraryService(),
                            navigationDeps.knowledgeRepository()));
            this.responses.addAll(List.of(responses));
        }

        @Override
        protected String sendChatCompletionRequest(String requestBody, boolean stream) throws IOException {
            requestBodies.add(requestBody);
            Object response = responses.poll();
            if (response instanceof IOException exception) {
                throw exception;
            }
            if (response == null) {
                throw new IOException("No stub response configured.");
            }
            return response.toString();
        }

        String lastRequestBody() {
            return requestBodies.isEmpty() ? "" : requestBodies.get(requestBodies.size() - 1);
        }

        List<String> requestBodies() {
            return requestBodies;
        }
    }

    private record NavigationDeps(AiStandardLibraryService standardLibraryService,
                                  InformaticsKnowledgeNodeRepository knowledgeRepository) {
    }

    private static NavigationDeps navigationDeps() {
        AiStandardLibraryService libraryService = mock(AiStandardLibraryService.class);
        when(libraryService.listRootKnowledgeAreas()).thenReturn(List.of(
                AiStandardLibraryNavigationNodeResponse.builder()
                        .code("BASIC")
                        .type("DOMAIN")
                        .name("基础语法")
                        .path("基础语法")
                        .hasChildren(true)
                        .build()
        ));
        when(libraryService.expandDiagnosticLayer("BASIC")).thenReturn(diagnosticLayer());
        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.SKILL_UNIT, "SK_RANGE_BOUNDARY"))
                .thenReturn(Optional.of(item("SK_RANGE_BOUNDARY", AiStandardLibraryLayer.SKILL_UNIT)));
        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.MISTAKE_POINT, "MP_RANGE_RIGHT_ENDPOINT_MISSING"))
                .thenReturn(Optional.of(item("MP_RANGE_RIGHT_ENDPOINT_MISSING", AiStandardLibraryLayer.MISTAKE_POINT)));
        when(libraryService.findFormalItemAsLegacy(AiStandardLibraryLayer.IMPROVEMENT_POINT, "TESTING_HABIT"))
                .thenReturn(Optional.of(item("TESTING_HABIT", AiStandardLibraryLayer.IMPROVEMENT_POINT)));
        InformaticsKnowledgeNodeRepository repository = mock(InformaticsKnowledgeNodeRepository.class);
        when(repository.findByCode("BASIC.LOOP.BOUNDARY")).thenReturn(Optional.empty());
        return new NavigationDeps(libraryService, repository);
    }

    private static AiStandardLibraryDiagnosticLayerResponse diagnosticLayer() {
        return AiStandardLibraryDiagnosticLayerResponse.builder()
                .knowledgePoint(AiStandardLibraryNavigationNodeResponse.builder()
                        .code("BASIC")
                        .type("KNOWLEDGE_POINT")
                        .name("循环边界")
                        .path("基础语法 / 循环边界")
                        .hasChildren(false)
                        .hasDiagnosticLayer(true)
                        .build())
                .skillUnits(List.of(AiStandardLibraryDiagnosticLayerResponse.SkillUnit.builder()
                        .code("SK_RANGE_BOUNDARY")
                        .category("循环边界")
                        .name("闭区间与 range 边界对应")
                        .description("能把题目闭区间要求转成实际循环范围。")
                        .primaryKnowledgeNodeCode("BASIC")
                        .knowledgeNodeCodes(List.of("BASIC"))
                        .mistakePoints(List.of(AiStandardLibraryDiagnosticLayerResponse.MistakePoint.builder()
                                .code("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .category("循环边界")
                                .name("右端点漏取")
                                .description("使用不含右端点的循环范围表达闭区间。")
                                .skillUnitCode("SK_RANGE_BOUNDARY")
                                .primaryKnowledgeNodeCode("BASIC")
                                .knowledgeNodeCodes(List.of("BASIC"))
                                .build()))
                        .improvementPoints(List.of(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint.builder()
                                .code("TESTING_HABIT")
                                .category("自测")
                                .name("补充边界样例意识")
                                .description("用最小值和端点样例检查边界。")
                                .skillUnitCode("SK_RANGE_BOUNDARY")
                                .primaryKnowledgeNodeCode("BASIC")
                                .knowledgeNodeCodes(List.of("BASIC"))
                                .build()))
                        .build()))
                .directImprovementPoints(List.of())
                .build();
    }

    private static AiStandardLibraryItem item(String code, AiStandardLibraryLayer layer) {
        return AiStandardLibraryItem.builder()
                .layer(layer)
                .code(code)
                .category("循环边界")
                .name(code)
                .description("循环边界诊断项。")
                .studentExplanation("循环边界诊断项。")
                .teacherExplanation("循环边界诊断项。")
                .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY")
                .knowledgeNodeCodes("BASIC.LOOP.BOUNDARY")
                .mistakeType("BOUNDARY")
                .commonMisconception("没有区分 range 右端开区间和题目闭区间。")
                .skillUnitCode(layer == AiStandardLibraryLayer.SKILL_UNIT ? "" : "SK_RANGE_BOUNDARY")
                .abilityPoint("")
                .requiredEvidence("code:range_excludes_n")
                .whenToUse("修复边界问题后复盘。")
                .studentBenefit("能更早发现端点漏取。")
                .applicableLanguages("PYTHON")
                .build();
    }
}
