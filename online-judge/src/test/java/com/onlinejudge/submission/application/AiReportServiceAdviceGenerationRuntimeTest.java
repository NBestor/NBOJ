package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthAgentService;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryItem;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryDiagnosticLayerResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationExpansionResponse;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryNavigationNodeResponse;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReportServiceAdviceGenerationRuntimeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void durableWorkflowPersistsOneCoreDiagnosisIssueAttachmentAndBothOutputs() throws Exception {
        StubAiReportService service = newService(validAdviceResponse());
        AiDiagnosisWorkflowService workflowService = mock(AiDiagnosisWorkflowService.class);
        when(workflowService.executeStage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> {
            AiDiagnosisWorkflowService.StageCallable<?> callable = invocation.getArgument(9);
            return callable.call();
        });
        service.setDiagnosisWorkflowService(workflowService);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        service.setDiagnosisBranchExecutor(executor);
        service.enableParallelOutputProbe();

        SubmissionAnalysisResponse analysis;
        try (AiDiagnosisWorkflowContext.Scope ignored = AiDiagnosisWorkflowContext.activate(42L)) {
            analysis = service.enhanceSubmissionAnalysis(
                    problem(), submission(), fallback(), evidencePackage());
        } finally {
            executor.shutdownNow();
        }

        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        org.mockito.ArgumentCaptor<String> stageKeys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(workflowService, atLeast(4)).executeStage(
                org.mockito.ArgumentMatchers.eq(42L),
                stageKeys.capture(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(stageKeys.getAllValues())
                .contains("CORE_DIAGNOSIS", "ISSUE_ATTACHMENT:I1", "STUDENT_OUTPUT", "TEACHER_OUTPUT");
        assertThat(service.parallelOutputThreads()).hasSize(2).doesNotHaveDuplicates();
        assertThat(analysis.getTeacherNote()).contains("本次教师观察与核心诊断一致");
    }

    @Test
    void defaultRuntimeGeneratesStructuredAdviceWithSingleDiagnosisCall() {
        StubAiReportService service = newService(validAdviceResponse());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(4);
        assertThat(service.systemPrompt(0)).contains("free-diagnosis-v2");
        assertThat(service.systemPrompt(1)).contains("standard-library-navigation-v1");
        assertThat(service.systemPrompt(3)).contains("diagnosis-report-v4");
        assertThat(service.outputTokens(3)).isEqualTo(4200);
        assertThat(service.userPrompt(1))
                .contains("currentLayer", "allowedActions", "maxRounds");
        assertThat(service.userPrompt(3))
                .contains("brief", "freeDiagnosis", "issues", "libraryAnchors", "standardLibrary")
                .contains("mistakePoints")
                .contains("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                .contains("\"status\":\"LAYERED_ATTACHMENT\"");
        assertThat(analysis.getAiInvocation().getPromptVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getDiagnosisSoftFixes())
                .contains("basicLayerAdvice.issueId filled by stable issue order: I1")
                .contains("improvementLayerAdvice.issueId filled by stable issue order: I1");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(1);
        assertThat(analysis.getStudentFeedback().getBlockingIssues().get(0).getTitle())
                .contains("循环右边界");
    }

    @Test
    void navigationLoopReachesDiagnosticLayerAcrossFullKnowledgeTreeDepth() {
        AiStandardLibraryService libraryService = deepNavigationLibraryService();
        StubAiReportService service = newServiceWithNavigationSequence(
                libraryService,
                List.of(
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("BASIC.LOOP"),
                        attachmentSelectResponse("BASIC.LOOP.BOUNDARY"),
                        attachmentSelectResponse("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                ),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(7);
        assertThat(service.userPrompt(4))
                .contains("\"nodes\"")
                .contains("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")
                .contains("\"maxRounds\":6");
        assertThat(service.userPrompt(5))
                .contains("\"diagnosticItems\"")
                .contains("SK_RANGE_BOUNDARY")
                .contains("MP_RANGE_RIGHT_ENDPOINT_MISSING");
        assertThat(service.userPrompt(6))
                .contains("\"anchorStatus\":\"HIT\"")
                .contains("\"skillUnitCode\":\"SK_RANGE_BOUNDARY\"")
                .contains("\"mistakePointCode\":\"MP_RANGE_RIGHT_ENDPOINT_MISSING\"");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationSelectedCount()).isGreaterThan(0);
        assertThat(analysis.getBasicLayerAdvice()).singleElement().satisfies(advice -> {
            assertThat(advice.getKnowledgePathStatus()).isEqualTo("FORMAL");
            assertThat(advice.getKnowledgePath())
                    .containsExactly(
                            "基础语法",
                            "循环结构",
                            "循环边界",
                            "闭区间循环边界",
                            "闭区间与 range 边界对应",
                            "右端点漏取");
        });
    }

    @Test
    void navigationRoundLimitKeepsPartialDirectoryAnchorForAdvice() {
        StubAiReportService service = newServiceWithNavigationSequence(
                standardLibraryService(),
                List.of(attachmentSelectResponse("BASIC")),
                validAdviceResponse()
        );
        ReflectionTestUtils.setField(service, "standardLibraryNavigationMaxRounds", 1);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(service.userPrompt(2))
                .contains("\"anchorStatus\":\"PARTIAL\"")
                .contains("AI_NAVIGATION_ROUND_LIMIT_REACHED");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void invalidNavigationBranchGetsOneRepairAttemptBeforeContinuing() {
        AiStandardLibraryService libraryService = deepNavigationLibraryService();
        StubAiReportService service = newServiceWithNavigationSequence(
                libraryService,
                List.of(
                        attachmentSelectResponse("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")
                ),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(service.userPrompt(1))
                .contains("\"allowedActions\"")
                .contains("\"code\":\"BASIC\"")
                .doesNotContain("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("ATTACHMENT_FAILED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void invalidNavigationBranchFailsClosedWhenRepairRepeatsIllegalCode() {
        AiStandardLibraryService libraryService = deepNavigationLibraryService();
        StubAiReportService service = newServiceWithNavigationSequence(
                libraryService,
                List.of(
                        """
                        {
                          "action": "WRONG",
                          "codes": ["BASIC"],
                          "reason": "动作不在允许范围内。",
                          "confidence": 0.4
                        }
                        """
                ),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEmpty();
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("ATTACHMENT_FAILED");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(1);
    }

    @Test
    void teacherDiagnosisUsesStandardProfileEvenWhenGlobalRuntimeRequestsLowLatency() {
        StubAiReportService service = newService(validAdviceResponse());
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", "low-latency");

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getAiInvocation().getRuntimeProfile())
                .isEqualTo(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        assertThat(analysis.getAiInvocation().getRequestCompact()).isFalse();
    }

    @Test
    void diagnosisReportV2MarkdownPrefersNaturalStudentReport() {
        StubAiReportService service = newService(diagnosisReportV2WithSoftFixesResponse());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getReportMarkdown())
                .contains("### 基础层", "循环范围和题目边界要求没有完全对齐")
                .contains("### 提高层", "固定自测清单")
                .contains("### 下一步行动", "写出循环变量序列")
                .contains("### 基础层明细", "循环右边界漏取")
                .contains("### 提高层明细", "补充边界样例意识");
    }

    @Test
    void diagnosisReportV2KeepsMultipleStructuredAdviceItems() {
        StubAiReportService service = newService(diagnosisReportV2WithMultipleAdviceResponse());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getBasicAdviceCount()).isEqualTo(2);
        assertThat(analysis.getAiInvocation().getImprovementAdviceCount()).isEqualTo(2);
        assertThat(analysis.getBasicLayerAdvice())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.BasicLayerAdvice::getTitle)
                .containsExactly("循环右边界漏取", "失败样例对照不足");
        assertThat(analysis.getImprovementLayerAdvice())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.ImprovementLayerAdvice::getTitle)
                .containsExactly("补充边界样例意识", "保留手推记录");
        assertThat(analysis.getStudentFeedback().getBlockingIssues())
                .hasSize(2)
                .extracting(SubmissionAnalysisResponse.FeedbackIssue::getTitle)
                .containsExactly("循环右边界漏取", "失败样例对照不足");
        assertThat(analysis.getStudentFeedback().getImprovementOpportunities()).hasSize(2);
        assertThat(analysis.getReportMarkdown())
                .contains("### 基础层", "### 基础层明细", "循环右边界漏取", "失败样例对照不足")
                .contains("### 提高层明细", "补充边界样例意识", "保留手推记录");
    }

    @Test
    void emptyStandardLibraryStillGeneratesAdviceForMultipleIssues() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                multiIssueFreeDiagnosisResponse(),
                List.of(),
                multiIssueAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(2);
        assertThat(service.userPrompt(1))
                .contains("\"issues\"")
                .contains("\"issueId\":\"I1\"", "\"issueId\":\"I2\"", "\"issueId\":\"I3\"")
                .contains("\"anchorStatus\":\"LIBRARY_EMPTY\"");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LIBRARY_EMPTY");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(3);
        assertThat(analysis.getImprovementLayerAdvice()).hasSize(2);
        assertThat(analysis.getStudentFeedback().getBlockingIssues()).hasSize(3);
    }

    @Test
    void freeDiagnosisFallsBackToVisibleFailedCaseWhenModelEvidenceRefsAreInvalid() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                freeDiagnosisResponseWithInvalidEvidenceRefs(),
                List.of(),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getFailureReason()).isBlank();
        assertThat(service.userPrompt(1))
                .contains("\"evidenceRefs\":[\"judge:first_failed_case\"]")
                .contains("\"anchorStatus\":\"LIBRARY_EMPTY\"");
    }

    @Test
    void freeDiagnosisRepairsCommonIssueAliasesAndMissingEvidenceRefs() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                freeDiagnosisResponseWithCommonAliases(),
                List.of(),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEmpty();
        assertThat(service.userPrompt(1))
                .contains("\"issues\"")
                .contains("\"title\":\"输出范围没有按题意覆盖\"")
                .contains("\"evidenceRefs\":[\"judge:first_failed_case\"]");
    }

    @Test
    void libraryAttachmentClassifiesEveryValidatedIssueWithoutIssueCap() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                standardLibraryService(),
                multiIssueFreeDiagnosisResponse(),
                List.of(
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING"),
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING"),
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                ),
                multiIssueAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(8);
        assertThat(service.userPrompt(7))
                .contains("\"issueId\":\"I1\"", "\"issueId\":\"I2\"", "\"issueId\":\"I3\"")
                .contains("\"status\":\"LAYERED_ATTACHMENT\"")
                .doesNotContain("skipped by max issue limit")
                .contains("\"anchorStatus\":\"HIT\"");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(3);
        assertThat(analysis.getImprovementLayerAdvice()).hasSize(2);
    }

    @Test
    void oneIssueAttachmentFailureDoesNotSkipRemainingIssues() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                standardLibraryService(),
                multiIssueFreeDiagnosisResponse(),
                List.of(
                        attachmentSelectResponse("NOT_VISIBLE"),
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING"),
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                ),
                multiIssueAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(7);
        assertThat(service.userPrompt(6))
                .contains("\"issueId\":\"I1\"", "\"anchorStatus\":\"ATTACHMENT_FAILED\"")
                .contains("\"issueId\":\"I2\"", "\"issueId\":\"I3\"")
                .contains("\"anchorStatus\":\"HIT\"");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(3);
    }

    @Test
    void repairsLiveAdviceShapeWhenStudentReportIsReturnedAsString() {
        StubAiReportService service = newService(
                liveAdviceResponseWithStringStudentReport(),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(5);
        assertThat(service.systemPrompt(4)).contains("覆盖修复重试");
        assertThat(service.userPrompt(4)).contains("previousOutput", "ADVICE_MISSING_ISSUE_COVERAGE");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getStudentFeedback().getSummary()).contains("循环边界");
        assertThat(analysis.getStudentFeedback().getNextLearningAction().getTask()).contains("手推");
        assertThat(analysis.getStudentFeedback().getNextLearningAction().getEvidenceRefs())
                .contains("code:range_excludes_n");
    }

    @Test
    void repairsQwenAdviceAliasesBeforeDeserializationWithoutDroppingModelContent() {
        StubAiReportService service = newService(qwenAdviceResponseWithCommonAliases());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(4);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isFalse();
        assertThat(analysis.getBasicLayerAdvice()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).contains("循环右边界");
                    assertThat(item.getEvidenceRefs()).contains("code:range_excludes_n");
                });
        assertThat(analysis.getFixDirections()).anySatisfy(item ->
                assertThat(item).contains("画出 n=3 时循环变量 i 的取值序列"));
        assertThat(analysis.getStudentFeedback().getBlockingIssues()).singleElement()
                .satisfies(item -> assertThat(item.getStudentMessage()).contains("没有包含数字 n 本身"));
    }

    @Test
    void adviceValidationFailureFallsBackWithoutPretendingSuccess() {
        StubAiReportService service = newService(
                """
                {
                  "caseUnderstanding": {
                    "problemGoal": "输出 1 到 n 的整数和。",
                    "codeIntent": "学生想用循环累加。",
                    "behaviorGap": "循环没有覆盖末端。",
                    "primaryEvidenceRef": "invented:evidence"
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "少处理端点会让结果偏小。",
                    "studentAction": "先手推 n=1 和 n=2。",
                    "checkQuestion": "最后一个数有没有进入循环？",
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
                """
        );

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
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("FAILED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationFailureReason()).contains("INVALID_EVIDENCE_REF");
        assertThat(analysis.getIssueTags()).isEmpty();
        assertThat(analysis.getFineGrainedTags()).isEmpty();
        assertThat(analysis.getLineIssues()).isEmpty();
        assertThat(analysis.getUncertainty()).contains("未使用本地规则兜底");
    }

    @Test
    void truncatedAdvicePayloadRetriesWithStructuredOutputBudget() {
        StubAiReportService service = newService(
                truncatedAdviceResponse(),
                validAdviceResponse()
        );
        ReflectionTestUtils.setField(service, "structuredRetryEnabled", true);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        ReflectionTestUtils.setField(service, "structuredRetryOutputTokens", 4200);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(5);
        assertThat(service.outputTokens(3)).isEqualTo(4200);
        assertThat(service.outputTokens(4)).isEqualTo(4200);
        assertThat(service.userPrompt(4)).isEqualTo(service.userPrompt(3));
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isTrue();
    }

    @Test
    void multiIssueAdviceAcceptsMajorIssueCoverageWithNoImprovementAdvice() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                multiIssueFreeDiagnosisResponse(),
                List.of(),
                majorIssueCoverageAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(2);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isFalse();
        assertThat(analysis.getBasicLayerAdvice()).hasSize(2)
                .extracting(SubmissionAnalysisResponse.BasicLayerAdvice::getIssueId)
                .containsExactly("I1", "I2");
        assertThat(analysis.getImprovementLayerAdvice()).isEmpty();
        assertThat(analysis.getStudentFeedback().getBlockingIssues()).hasSize(2);
        assertThat(analysis.getStudentFeedback().getImprovementOpportunities()).isEmpty();
    }

    @Test
    void missingMajorIssueAdviceRetriesOnceAndRepairsCoverage() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                multiIssueFreeDiagnosisResponse(),
                List.of(),
                validAdviceResponse(),
                majorIssueCoverageAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(service.systemPrompt(2)).contains("覆盖修复重试");
        assertThat(service.userPrompt(2)).contains("previousOutput", "ADVICE_MISSING_ISSUE_COVERAGE", "I2");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(2)
                .extracting(SubmissionAnalysisResponse.BasicLayerAdvice::getIssueId)
                .containsExactly("I1", "I2");
    }

    @Test
    void truncatedFreeDiagnosisPayloadRetriesWithStructuredOutputBudget() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                truncatedFreeDiagnosisResponse(),
                List.of(),
                multiIssueFreeDiagnosisResponse(),
                multiIssueAdviceResponse()
        );
        ReflectionTestUtils.setField(service, "structuredRetryEnabled", true);
        ReflectionTestUtils.setField(service, "structuredRetryOutputTokens", 4200);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(service.outputTokens(1)).isEqualTo(4200);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEmpty();
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isTrue();
        assertThat(analysis.getBasicLayerAdvice()).hasSize(3);
    }

    @Test
    void randomFreeDiagnosisTextDoesNotStructuredRetry() {
        StubAiReportService service = newServiceWithFreeDiagnosisAndNavigationSequence(
                emptyStandardLibraryService(),
                "not json at all",
                List.of(),
                multiIssueAdviceResponse()
        );
        ReflectionTestUtils.setField(service, "structuredRetryEnabled", true);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(1);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_FAILED");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEqualTo("FREE_DIAGNOSIS");
    }

    @Test
    void truncatedLayeredAttachmentPayloadRetriesWithStructuredOutputBudget() {
        StubAiReportService service = newServiceWithNavigationSequence(
                standardLibraryService(),
                List.of(
                        truncatedLayeredAttachmentResponse(),
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                ),
                validAdviceResponse()
        );
        ReflectionTestUtils.setField(service, "structuredRetryEnabled", true);
        ReflectionTestUtils.setField(service, "structuredRetryOutputTokens", 4200);

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(5);
        assertThat(service.outputTokens(2)).isEqualTo(4200);
        assertThat(service.userPrompt(2)).isEqualTo(service.userPrompt(1));
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isTrue();
    }

    @Test
    void navigationValidationFailureStopsBeforeFinalDiagnosisWithoutLocalRecall() {
        StubAiReportService service = newServiceWithNavigationResponse(
                """
                {
                  "action": "SELECT",
                  "codes": [],
                  "reason": "当前层没有合适分支。",
                  "confidence": 0.4
                }
                """,
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(3);
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getFailureStage()).isEmpty();
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("NO_MATCH");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getBasicLayerAdvice()).hasSize(1);
    }

    @Test
    void noDiagnosticMatchCreatesProvisionalAdviceUnderLastKnowledgePoint() {
        StubAiReportService service = newServiceWithNavigationSequence(
                deepNavigationLibraryService(),
                List.of(
                        attachmentSelectResponse("BASIC"),
                        attachmentSelectResponse("BASIC.LOOP"),
                        attachmentSelectResponse("BASIC.LOOP.BOUNDARY"),
                        attachmentSelectResponse("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"),
                        """
                        {
                          "action": "NO_MATCH",
                          "codes": [],
                          "reason": "正式诊断层没有匹配这个细颗粒问题。",
                          "confidence": 0.82
                        }
                        """
                ),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getBasicLayerAdvice()).singleElement().satisfies(advice -> {
            assertThat(advice.getKnowledgePathStatus()).isEqualTo("PROVISIONAL");
            assertThat(advice.getProvisionalNodeCode()).startsWith("MP_AI_");
            assertThat(advice.getKnowledgePath())
                    .containsExactly("基础语法", "循环结构", "循环边界", "闭区间循环边界", "循环右边界漏取");
        });
        assertThat(analysis.getImprovementLayerAdvice()).singleElement().satisfies(advice -> {
            assertThat(advice.getKnowledgePathStatus()).isEqualTo("PROVISIONAL");
            assertThat(advice.getProvisionalNodeCode()).startsWith("IP_AI_");
            assertThat(advice.getKnowledgePath()).endsWith("补充边界样例意识");
        });
    }

    @Test
    void answerLeakAdviceCompletesWithoutSafetyRewrite() {
        StubAiReportService service = newService(
                unsafeAdviceResponse(),
                validAdviceResponse()
        );

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(service.callCount()).isEqualTo(4);
        assertThat(analysis.getSourceType()).isEqualTo("MODEL_SCOPE_EXTERNAL_MODEL");
        assertThat(analysis.getAiInvocation().getStatus()).isEqualTo("MODEL_COMPLETED");
        assertThat(analysis.getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(analysis.getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("LAYERED_ATTACHMENT");
        assertThat(analysis.getAiInvocation().getStreamFallbackRetryUsed()).isFalse();
        assertThat(analysis.getAiInvocation().getFailureReason()).isEmpty();
        assertThat(analysis.getStudentFeedback().getBlockingIssues().get(0).getNextAction())
                .contains("直接改成", "range(1, n + 1)");
    }

    @Test
    void diagnosisReportV2SoftFixesAreVisibleInInvocationTrace() {
        StubAiReportService service = newService(diagnosisReportV2WithSoftFixesResponse());

        SubmissionAnalysisResponse analysis = service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        assertThat(analysis.getSourceType()).isEqualTo("MODEL_SCOPE_EXTERNAL_MODEL");
        assertThat(analysis.getAiInvocation().getPromptVersion()).isEqualTo(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);
        assertThat(analysis.getAiInvocation().getDiagnosisLibraryFit()).isEqualTo("PARTIAL");
        assertThat(analysis.getAiInvocation().getDiagnosisSoftFixes())
                .contains("evidenceRef alias code:range_excludes_n:line3 -> code:range_excludes_n")
                .contains("evidenceRef alias judge:first_failed_case:case1 -> judge:first_failed_case")
                .noneSatisfy(item -> assertThat(item).contains("unknown anchor id"));
        assertThat(analysis.getAiInvocation().getDiagnosisHardFailures()).isEmpty();
        assertThat(analysis.getStudentFeedback().getBlockingIssues()).singleElement()
                .satisfies(item -> assertThat(item.getStudentMessage()).contains("当前循环范围"));
    }

    @Test
    void diagnosisReportV2GrowthCandidatesArePersistedToCandidatePool() {
        AiStandardLibraryGrowthAgentService growthAgentService = mock(AiStandardLibraryGrowthAgentService.class);
        StubAiReportService service = newService(
                growthAgentService,
                diagnosisReportV2WithGrowthCandidateResponse()
        );

        service.enhanceSubmissionAnalysis(
                problem(),
                submission(),
                fallback(),
                evidencePackage()
        );

        verify(growthAgentService).proposeFromDiagnosisOutput(
                org.mockito.ArgumentMatchers.argThat(output ->
                        output != null
                                && output.getLibraryGrowth() != null
                                && output.getLibraryGrowth().getCandidates() != null
                                && output.getLibraryGrowth().getCandidates().size() == 1
                ),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void growthCandidatePersistenceUsesIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = AiStandardLibraryGrowthAgentService.class
                .getMethod("proposeFromDiagnosisOutput", AdviceGenerationOutput.class, Long.class, Long.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private StubAiReportService newService(AiStandardLibraryGrowthAgentService growthAgentService, String... responses) {
        AiStandardLibraryService libraryService = standardLibraryService();
        InformaticsKnowledgeNodeRepository knowledgeRepository = knowledgeRepository();
        StubAiReportService service = new StubAiReportService(
                objectMapper,
                runtime(),
                libraryService,
                new StandardLibraryNavigationPackBuilder(libraryService, knowledgeRepository),
                growthAgentService,
                withNavigationResponses(responses)
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled", true);
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        return service;
    }

    private StubAiReportService newService(String... responses) {
        AiStandardLibraryService libraryService = standardLibraryService();
        InformaticsKnowledgeNodeRepository knowledgeRepository = knowledgeRepository();
        StubAiReportService service = new StubAiReportService(
                objectMapper,
                runtime(),
                libraryService,
                new StandardLibraryNavigationPackBuilder(libraryService, knowledgeRepository),
                null,
                withNavigationResponses(responses)
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled", true);
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        return service;
    }

    private StubAiReportService newServiceWithNavigationSequence(AiStandardLibraryService libraryService,
                                                                 List<String> navigationResponses,
                                                                 String... adviceResponses) {
        return newServiceWithFreeDiagnosisAndNavigationSequence(
                libraryService,
                freeDiagnosisResponse(),
                navigationResponses,
                adviceResponses
        );
    }

    private StubAiReportService newServiceWithFreeDiagnosisAndNavigationSequence(
            AiStandardLibraryService libraryService,
            String freeDiagnosisResponse,
            List<String> navigationResponses,
            String... adviceResponses) {
        InformaticsKnowledgeNodeRepository knowledgeRepository = knowledgeRepository();
        StubAiReportService service = new StubAiReportService(
                objectMapper,
                runtime(),
                libraryService,
                new StandardLibraryNavigationPackBuilder(libraryService, knowledgeRepository),
                null,
                withFreeDiagnosisAndNavigationSequence(freeDiagnosisResponse, navigationResponses, adviceResponses)
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled", true);
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        return service;
    }

    private StubAiReportService newServiceWithNavigationResponse(String navigationResponse, String... responses) {
        AiStandardLibraryService libraryService = standardLibraryService();
        InformaticsKnowledgeNodeRepository knowledgeRepository = knowledgeRepository();
        StubAiReportService service = new StubAiReportService(
                objectMapper,
                runtime(),
                libraryService,
                new StandardLibraryNavigationPackBuilder(libraryService, knowledgeRepository),
                null,
                withNavigationResponse(navigationResponse, responses)
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "externalRuntimeEnabled", true);
        ReflectionTestUtils.setField(service, "externalRuntimeProfile", ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        return service;
    }

    private String[] withNavigationResponses(String... adviceResponses) {
        return withNavigationSequence(List.of(
                attachmentSelectResponse("BASIC"),
                attachmentSelectResponse("MP_RANGE_RIGHT_ENDPOINT_MISSING")
        ), adviceResponses);
    }

    private String[] withNavigationResponse(String navigationResponse, String... adviceResponses) {
        return withNavigationSequence(List.of(navigationResponse), adviceResponses);
    }

    private String[] withNavigationSequence(List<String> navigationResponses, String... adviceResponses) {
        return withFreeDiagnosisAndNavigationSequence(freeDiagnosisResponse(), navigationResponses, adviceResponses);
    }

    private String[] withFreeDiagnosisAndNavigationSequence(String freeDiagnosisResponse,
                                                            List<String> navigationResponses,
                                                            String... adviceResponses) {
        List<String> responses = new ArrayList<>();
        responses.add(freeDiagnosisResponse);
        responses.addAll(navigationResponses);
        responses.addAll(List.of(adviceResponses));
        return responses.toArray(String[]::new);
    }

    private String truncatedAdviceResponse() {
        return """
                {
                  "caseUnderstanding": {
                    "problemGoal": "输出 1 到 n 的整数和。"
                  },
                  "basicLayerAdvice": [{
                    "title": "循环右边界漏取"
                """;
    }

    private String truncatedFreeDiagnosisResponse() {
        return """
                {
                  "problemUnderstanding": "题目要求输出 1 到 n 的整数和。",
                  "codeIntent": "学生想用循环累加 total。",
                  "issues": [{
                    "issueId": "I1",
                    "title": "循环右边界漏取"
                """;
    }

    private String truncatedLayeredAttachmentResponse() {
        return """
                {
                  "action": "SELECT",
                  "codes": ["BASIC"
                """;
    }

    private AiStandardLibraryService standardLibraryService() {
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
        for (AiStandardLibraryItem item : navigationItems()) {
            when(libraryService.findFormalItemAsLegacy(item.getLayer(), item.getCode()))
                    .thenReturn(Optional.of(item));
        }
        return libraryService;
    }

    private AiStandardLibraryService emptyStandardLibraryService() {
        AiStandardLibraryService libraryService = mock(AiStandardLibraryService.class);
        when(libraryService.listRootKnowledgeAreas()).thenReturn(List.of());
        return libraryService;
    }

    private AiStandardLibraryService deepNavigationLibraryService() {
        AiStandardLibraryService libraryService = standardLibraryService();
        when(libraryService.expandDiagnosticLayer("BASIC"))
                .thenThrow(new IllegalArgumentException("not a knowledge point"));
        when(libraryService.expandDiagnosticLayer("BASIC.LOOP"))
                .thenThrow(new IllegalArgumentException("not a knowledge point"));
        when(libraryService.expandDiagnosticLayer("BASIC.LOOP.BOUNDARY"))
                .thenThrow(new IllegalArgumentException("not a knowledge point"));
        when(libraryService.expandDiagnosticLayer("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"))
                .thenReturn(diagnosticLayer());
        when(libraryService.expandKnowledgeNode("BASIC", 0, 50))
                .thenReturn(expansion(
                        node("BASIC", null, "DOMAIN", "基础语法", "基础语法", true, false),
                        List.of(node("BASIC.LOOP", "BASIC", "CHAPTER", "循环结构", "基础语法 / 循环结构", true, false))));
        when(libraryService.expandKnowledgeNode("BASIC.LOOP", 0, 50))
                .thenReturn(expansion(
                        node("BASIC.LOOP", "BASIC", "CHAPTER", "循环结构", "基础语法 / 循环结构", true, false),
                        List.of(node("BASIC.LOOP.BOUNDARY", "BASIC.LOOP", "TOPIC", "循环边界", "基础语法 / 循环结构 / 循环边界", true, false))));
        when(libraryService.expandKnowledgeNode("BASIC.LOOP.BOUNDARY", 0, 50))
                .thenReturn(expansion(
                        node("BASIC.LOOP.BOUNDARY", "BASIC.LOOP", "TOPIC", "循环边界", "基础语法 / 循环结构 / 循环边界", true, false),
                        List.of(node("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL", "BASIC.LOOP.BOUNDARY", "KNOWLEDGE_POINT",
                                "闭区间循环边界", "基础语法 / 循环结构 / 循环边界 / 闭区间循环边界", false, true))));
        return libraryService;
    }

    private AiStandardLibraryNavigationExpansionResponse expansion(
            AiStandardLibraryNavigationNodeResponse node,
            List<AiStandardLibraryNavigationNodeResponse> children) {
        return AiStandardLibraryNavigationExpansionResponse.builder()
                .node(node)
                .ancestors(List.of())
                .children(children)
                .childPage(0)
                .childSize(50)
                .childTotal(children.size())
                .childHasMore(false)
                .build();
    }

    private AiStandardLibraryDiagnosticLayerResponse diagnosticLayer() {
        return AiStandardLibraryDiagnosticLayerResponse.builder()
                .knowledgePoint(node("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL", "BASIC.LOOP.BOUNDARY", "KNOWLEDGE_POINT",
                        "闭区间循环边界", "基础语法 / 循环结构 / 循环边界 / 闭区间循环边界", false, true))
                .skillUnits(List.of(AiStandardLibraryDiagnosticLayerResponse.SkillUnit.builder()
                        .code("SK_RANGE_BOUNDARY")
                        .category("循环边界")
                        .name("闭区间与 range 边界对应")
                        .description("能把题目闭区间要求转成实际循环范围。")
                        .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")
                        .knowledgeNodeCodes(List.of("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"))
                        .mistakePoints(List.of(AiStandardLibraryDiagnosticLayerResponse.MistakePoint.builder()
                                .code("MP_RANGE_RIGHT_ENDPOINT_MISSING")
                                .category("循环边界")
                                .name("右端点漏取")
                                .description("使用不含右端点的循环范围表达闭区间。")
                                .skillUnitCode("SK_RANGE_BOUNDARY")
                                .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")
                                .knowledgeNodeCodes(List.of("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"))
                                .build()))
                        .improvementPoints(List.of(AiStandardLibraryDiagnosticLayerResponse.ImprovementPoint.builder()
                                .code("TESTING_HABIT")
                                .category("自测")
                                .name("补充边界样例意识")
                                .description("用最小值和端点样例检查边界。")
                                .skillUnitCode("SK_RANGE_BOUNDARY")
                                .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")
                                .knowledgeNodeCodes(List.of("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL"))
                                .build()))
                        .build()))
                .directImprovementPoints(List.of())
                .build();
    }

    private AiStandardLibraryNavigationNodeResponse node(String code,
                                                         String parentCode,
                                                         String type,
                                                         String name,
                                                         String path,
                                                         boolean hasChildren,
                                                         boolean hasDiagnosticLayer) {
        return AiStandardLibraryNavigationNodeResponse.builder()
                .code(code)
                .parentCode(parentCode)
                .type(type)
                .name(name)
                .path(path)
                .aliases(List.of())
                .hasChildren(hasChildren)
                .hasDiagnosticLayer(hasDiagnosticLayer)
                .build();
    }

    private InformaticsKnowledgeNodeRepository knowledgeRepository() {
        InformaticsKnowledgeNodeRepository repository = mock(InformaticsKnowledgeNodeRepository.class);
        when(repository.findByCode("BASIC.LOOP.BOUNDARY")).thenReturn(Optional.empty());
        when(repository.findByCode("BASIC.LOOP.BOUNDARY.CLOSED_INTERVAL")).thenReturn(Optional.empty());
        return repository;
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

    private List<AiStandardLibraryItem> navigationItems() {
        return List.of(
                item("SK_RANGE_BOUNDARY", AiStandardLibraryLayer.SKILL_UNIT,
                        "循环边界",
                        "理解 Python range 右端不包含以及闭区间题意之间的对应关系。"),
                item("MP_RANGE_RIGHT_ENDPOINT_MISSING", AiStandardLibraryLayer.MISTAKE_POINT,
                        "循环边界",
                        "使用 range(1, n) 表达 1 到 n 的闭区间，导致最后一个数没有处理。"),
                item("TESTING_HABIT", AiStandardLibraryLayer.IMPROVEMENT_POINT,
                        "自测",
                        "修复后补充最小边界和端点样例。"),
                item("SK_UNRELATED_ARRAY_INDEX", AiStandardLibraryLayer.SKILL_UNIT,
                        "数组下标",
                        "数组下标合法范围与越界检查。")
        );
    }

    private AiStandardLibraryItem item(String code,
                                       AiStandardLibraryLayer layer,
                                       String category,
                                       String description) {
        return AiStandardLibraryItem.builder()
                .id((long) Math.abs(code.hashCode()))
                .layer(layer)
                .code(code)
                .category(category)
                .name(description)
                .description(description)
                .studentExplanation(description)
                .teacherExplanation(description)
                .skillUnitCode("SK_RANGE_BOUNDARY")
                .mistakeType("OFF_BY_ONE")
                .commonMisconception(description)
                .evidenceSignals("code:range_excludes_n\njudge:first_failed_case")
                .commonCodePatterns("range(1, n)")
                .judgeSignals("WRONG_ANSWER")
                .abilityPoint("循环边界")
                .severity("HIGH")
                .applicableLanguages("PYTHON")
                .knowledgeNodeCodes("BASIC.LOOP.BOUNDARY")
                .teachingAction("TRACE_VARIABLES")
                .requiredEvidence("code:range_excludes_n")
                .whenToUse(description)
                .studentBenefit("能更早发现边界遗漏。")
                .enabled(true)
                .libraryVersion("test-v1")
                .build();
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

    private String freeDiagnosisResponse() {
        return """
                {
                  "problemUnderstanding": "题目要求输出 1 到 n 的整数和。",
                  "codeIntent": "学生想用循环累加 total。",
                  "behaviorGap": "循环没有覆盖题目要求的末端。",
                  "issues": [{
                    "issueId": "I1",
                    "title": "循环右边界漏取",
                    "whatHappened": "range(1, n) 与闭区间题意不一致。",
                    "whyItMatters": "端点漏处理会让求和结果偏小。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "severity": "MAJOR",
                    "confidence": 0.9
                  }],
                  "navigationIntent": {
                    "preferredDirections": ["基础语法", "循环结构", "循环边界"],
                    "reason": "当前错因首先落在循环边界。"
                  },
                  "uncertainty": "可见失败样例已经支持该方向。"
                }
                """;
    }

    private String multiIssueFreeDiagnosisResponse() {
        return """
                {
                  "problemUnderstanding": "题目要求输出 1 到 n 的整数和。",
                  "codeIntent": "学生想用循环累加 total。",
                  "behaviorGap": "循环范围和样例对照都暴露问题。",
                  "issues": [{
                    "issueId": "I1",
                    "title": "循环右边界漏取",
                    "whatHappened": "循环没有覆盖题目要求的末端。",
                    "whyItMatters": "端点漏处理会让结果偏小。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "severity": "MAJOR",
                    "confidence": 0.9
                  }, {
                    "issueId": "I2",
                    "title": "失败样例对照不足",
                    "whatHappened": "可见失败样例已经显示实际输出和期望不同。",
                    "whyItMatters": "不对照差异就容易只凭感觉修改。",
                    "evidenceRefs": ["judge:first_failed_case"],
                    "severity": "MAJOR",
                    "confidence": 0.8
                  }, {
                    "issueId": "I3",
                    "title": "循环变量手推缺失",
                    "whatHappened": "当前代码没有帮助自己确认循环变量实际取值。",
                    "whyItMatters": "缺少手推会让边界问题反复出现。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "severity": "MINOR",
                    "confidence": 0.7
                  }],
                  "uncertainty": ""
                }
                """;
    }

    private String freeDiagnosisResponseWithInvalidEvidenceRefs() {
        return """
                {
                  "problemUnderstanding": "题目要求输出 1 到 n 的整数和。",
                  "codeIntent": "学生想用循环累加 total。",
                  "behaviorGap": "公开失败样例显示实际输出和期望输出不同。",
                  "issues": [{
                    "issueId": "I1",
                    "title": "循环右边界漏取",
                    "whatHappened": "range(1, n) 与闭区间题意不一致。",
                    "whyItMatters": "端点漏处理会让求和结果偏小。",
                    "evidenceRefs": ["code:update_boundary", "judge:visible_output_diff"],
                    "severity": "MAJOR",
                    "confidence": 0.86
                  }],
                  "uncertainty": "公开失败样例支持该方向。"
                }
                """;
    }

    private String freeDiagnosisResponseWithCommonAliases() {
        return """
                {
                  "problemUnderstanding": "题目要求按指定范围统计并输出结果。",
                  "codeIntent": "学生想通过循环完成统计。",
                  "diagnosisIssues": [{
                    "name": "输出范围没有按题意覆盖",
                    "reason": "代码统计范围和题目要求不一致，导致可见样例输出错误。",
                    "impact": "如果范围不对，后续格式或算法优化都不能解决当前错误。",
                    "confidence": 0.83
                  }],
                  "uncertainty": "可见失败样例支持该诊断。"
                }
                """;
    }

    private String attachmentSelectResponse(String code) {
        return """
                {
                  "action": "SELECT",
                  "codes": ["%s"],
                  "reason": "该分支最贴近循环边界问题。",
                  "confidence": 0.86
                }
                """.formatted(code);
    }

    private String diagnosisReportV2WithGrowthCandidateResponse() {
        return """
                {
                  "diagnosisDecision": {
                    "libraryFit": "MISS",
                    "anchors": [{
                      "id": null,
                      "type": "OUT_OF_LIBRARY",
                      "role": "PRIMARY",
                      "confidence": 0.78,
                      "evidenceRefs": ["code:range_excludes_n"],
                      "reason": "当前候选不能精确表达该错因。"
                    }]
                  },
                  "studentReport": {
                    "hintLevel": "L3",
                    "basicLayerText": "基础层：循环范围和题目边界要求没有完全对齐。",
                    "improvementLayerText": "提高层：补充边界自测清单。",
                    "nextActionText": "下一步：手推 n=1 和 n=2。"
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": null,
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "端点漏处理会让求和结果偏小。",
                    "studentAction": "先手推 n=1 和 n=2 时循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.88
                  }],
                  "improvementLayerAdvice": [{
                    "improvementPointId": null,
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "补充边界样例意识",
                    "currentLimit": "这次更需要把端点纳入自测。",
                    "suggestion": "修复后补测最小值和端点附近样例。",
                    "studentBenefit": "能更早发现类似边界问题。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.74
                  }],
                  "libraryGrowth": {
                    "candidates": [{
                      "name": "可见失败样例定位端点漏取",
                      "suggestedPath": ["BASIC", "LOOP", "BOUNDARY", "VISIBLE_CASE_ENDPOINT"],
	                      "similarExistingItems": ["MP_RANGE_RIGHT_ENDPOINT_MISSING"],
	                      "evidenceRefs": ["code:range_excludes_n"],
	                      "evidenceStatus": "SUPPORTED",
	                      "errorSymptom": "可见样例暴露循环端点漏取。",
                      "typicalCodePattern": "循环条件没有覆盖题目要求的末端。",
                      "studentExplanation": "先手推端点是否进入循环，再决定是否需要补充标准库错因。",
                      "reason": "MISS 场景下发现更细颗粒错因。",
                      "status": "NEEDS_REVIEW",
                      "confidence": 0.78
                    }]
                  },
                  "studentSummary": "这次重点是循环边界。"
                }
                """;
    }

    private String liveAdviceResponseWithStringStudentReport() {
        return """
                {
                  "studentReport": "你的代码在处理求和范围时，循环范围没有覆盖题目要求的右端点。题目要求累加 1 到 n，但当前循环只走到 n 前一个位置。下一步动作：先手推 n=1 和 n=2 时循环变量实际出现过哪些值。",
                  "diagnosisDecision": {
                    "id": null,
                    "status": "OUT_OF_LIBRARY",
                    "evidenceRefs": ["code:line:3"],
                    "confidence": 0.86,
                    "reason": "模型识别到循环右端点漏取。"
                  },
                  "diagnosisCandidates": [{
                    "name": "循环右端点漏取",
                    "status": "OUT_OF_LIBRARY",
                    "anchorId": null,
                    "libraryPath": "循环边界",
                    "role": "PRIMARY",
                    "evidenceRefs": ["code:line:3"],
                    "reason": "range(1, n) 没有覆盖 n。",
                    "confidence": 0.86
                  }],
                  "teacherTrace": {
                    "reasoningSummary": "循环边界与题意闭区间不一致。",
                    "uncertainty": "可见失败样例支持该判断。",
                    "qualityFlags": [],
                    "softFixes": [],
                    "hardFailures": []
                  },
                  "libraryGrowth": {"candidates": []},
                  "studentSummary": "循环范围没有覆盖题目要求的右端点。"
                }
                """;
    }

    private String qwenAdviceResponseWithCommonAliases() {
        return """
                {
                  "studentReport": {
                    "hintLevel": "L2",
                    "basicLayerText": "你的代码在累加时没有包含数字 n 本身，导致结果偏小。",
                    "improvementLayerText": "建议你复习 range 函数的半开区间含义，并用小规模数据手推边界行为。",
                    "nextActionText": "画出 n=3 时循环变量 i 的取值序列。"
                  },
                  "caseUnderstanding": {
                    "problemGoal": "计算从 1 到 n 的所有整数之和。",
                    "codeIntent": "通过 for 循环遍历每个整数并累加。",
                    "behaviorGap": "循环范围错误地使用了 range(1, n)，导致未包含 n。",
                    "primaryEvidenceRef": "code:range_excludes_n"
                  },
                  "diagnosisDecision": {
                    "libraryFit": "HIT",
                    "anchors": [{
                      "id": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                      "type": "MISTAKE_POINT",
                      "role": "PRIMARY",
                      "confidence": 0.9,
                      "evidenceRefs": ["code:range_excludes_n"],
                      "reason": "循环右端点未覆盖。"
                    }]
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "你的代码在累加时没有包含数字 n 本身，导致结果偏小。",
                    "whyItMatters": "端点漏取会让每个测试结果都少加最后一项。",
                    "studentAction": "先手推 n=3 时循环变量 i 的实际取值。",
                    "checkQuestion": "循环变量 i 是否出现过 n？",
                    "evidenceRef": "code:range_excludes_n",
                    "confidence": 0.9,
                    "extraNote": "模型可能附加解释字段。"
                  }],
                  "improvementLayerAdvice": [{
                    "improvementPointId": "TESTING_HABIT",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "补充边界样例意识",
                    "currentLimit": "当前缺少对最小值和端点值的自测。",
                    "suggestion": "修复后补测 n=1、n=2、n=3。",
                    "studentBenefit": "能更早发现 range 右端不包含的问题。",
                    "evidenceRef": "code:range_excludes_n",
                    "confidence": 0.78
                  }],
                  "nextStepPlan": [{
                    "action": "画出 n=3 时循环变量 i 的取值序列。",
                    "reason": "这是确认边界是否覆盖的最小动作。",
                    "evidenceRef": "code:range_excludes_n"
                  }],
                  "studentSummary": "这次主要卡在循环范围和题目闭区间要求没有对齐。"
                }
                """;
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

    private String diagnosisReportV2WithSoftFixesResponse() {
        return """
                {
                  "diagnosisDecision": {
                    "libraryFit": "PARTIAL",
                    "anchors": [{
                      "id": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                      "type": "MISTAKE_POINT",
                      "role": "PRIMARY",
                      "confidence": 0.82,
	                      "evidenceRefs": ["code:range_excludes_n:line3", "judge:first_failed_case:case1"],
                      "reason": "模型发现了库里没有精确覆盖的边界错因。"
                    }]
                  },
                  "studentReport": {
                    "hintLevel": "L3",
                    "basicLayerText": "基础层：循环范围和题目边界要求没有完全对齐。先手推最小样例，确认端点是否进入循环。",
                    "improvementLayerText": "提高层：修好后把最小值、端点值、最大值附近样例加入固定自测清单。",
                    "nextActionText": "下一步：用 n=1 和 n=2 写出循环变量序列，再和题目要求逐项对照。"
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "端点漏处理会让求和结果偏小。",
                    "studentAction": "先手推最小样例里循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n:line3"],
                    "confidence": 0.9
                  }],
                  "improvementLayerAdvice": [{
                    "improvementPointId": "TESTING_HABIT",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "补充边界样例意识",
                    "currentLimit": "这次暴露的是端点验证不足。",
                    "suggestion": "修复后把最小值和端点值加入自测清单。",
                    "studentBenefit": "能更早发现开闭区间类问题。",
                    "evidenceRefs": ["judge:first_failed_case:case1"],
                    "confidence": 0.76
                  }],
                  "studentSummary": "这次重点是循环边界和边界自测。"
                }
                """;
    }

    private String diagnosisReportV2WithMultipleAdviceResponse() {
        return """
                {
                  "diagnosisDecision": {
                    "libraryFit": "HIT",
                    "anchors": [{
                      "id": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                      "type": "MISTAKE_POINT",
                      "role": "PRIMARY",
                      "confidence": 0.88,
                      "evidenceRefs": ["code:range_excludes_n"],
                      "reason": "循环范围没有覆盖闭区间末端。"
                    }]
                  },
                  "basicLayerAdvice": [{
                    "mistakePointId": "MP_RANGE_RIGHT_ENDPOINT_MISSING",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "端点漏处理会让求和结果偏小。",
                    "studentAction": "先手推循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.92
                  }, {
                    "mistakePointId": null,
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "失败样例对照不足",
                    "whatHappened": "可见失败样例已经显示实际输出与预期不一致。",
                    "whyItMatters": "不对照差异就容易只凭感觉修改。",
                    "studentAction": "把实际输出和预期输出逐项写在旁边。",
                    "checkQuestion": "第一处差异来自哪个循环取值？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.76
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
                  }, {
                    "improvementPointId": null,
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "保留手推记录",
                    "currentLimit": "目前缺少可复盘的变量取值记录。",
                    "suggestion": "修复前后都写一遍循环变量序列。",
                    "studentBenefit": "能看出修改是否真的覆盖了同类边界。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.72
                  }],
                  "studentReport": {
                    "hintLevel": "L3",
                    "basicLayerText": "基础层：这次重点是循环范围和失败样例对照。",
                    "improvementLayerText": "提高层：修复后补充边界自测和手推记录。",
                    "nextActionText": "先写出循环变量序列。"
                  },
                  "studentSummary": "这次有多个可独立检查的点。"
                }
                """;
    }

    private String multiIssueAdviceResponse() {
        return """
                {
                  "caseUnderstanding": {
                    "problemGoal": "题目要求输出 1 到 n 的整数和。",
                    "codeIntent": "学生使用循环累加 total。",
                    "behaviorGap": "循环范围没有覆盖题意，并且失败样例已经暴露差异。",
                    "primaryEvidenceRef": "code:range_excludes_n"
                  },
                  "basicLayerAdvice": [{
                    "issueId": "I1",
                    "mistakePointId": null,
                    "skillUnitId": null,
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "少处理一个端点会让求和结果偏小。",
                    "studentAction": "先手推 n=1 和 n=2 时循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.92
                  }, {
                    "issueId": "I2",
                    "mistakePointId": null,
                    "skillUnitId": null,
                    "title": "失败样例对照不足",
                    "whatHappened": "可见失败样例已经显示实际输出与预期不一致。",
                    "whyItMatters": "不对照差异就容易只凭感觉修改。",
                    "studentAction": "把实际输出和预期输出逐项写在旁边。",
                    "checkQuestion": "第一处差异来自哪个循环取值？",
                    "evidenceRefs": ["judge:first_failed_case"],
                    "confidence": 0.78
                  }, {
                    "issueId": "I3",
                    "mistakePointId": null,
                    "skillUnitId": null,
                    "title": "循环变量手推缺失",
                    "whatHappened": "当前缺少对循环变量取值序列的手推记录。",
                    "whyItMatters": "看不到变量序列就难以定位边界。",
                    "studentAction": "先写出循环变量实际取值序列。",
                    "checkQuestion": "变量序列和题目要求的闭区间一致吗？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.7
                  }],
                  "improvementLayerAdvice": [{
                    "issueId": "I1",
                    "improvementPointId": null,
                    "skillUnitId": null,
                    "title": "补充边界样例意识",
                    "currentLimit": "当前自测没有覆盖端点。",
                    "suggestion": "修复后补测最小值和端点附近样例。",
                    "studentBenefit": "能更早发现开闭区间问题。",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.8
                  }, {
                    "issueId": "I2",
                    "improvementPointId": null,
                    "skillUnitId": null,
                    "title": "保留手推记录",
                    "currentLimit": "调试过程缺少可复盘记录。",
                    "suggestion": "每次修改前后保留一份变量取值记录。",
                    "studentBenefit": "能判断修改是否真的解决同一类边界。",
                    "evidenceRefs": ["judge:first_failed_case"],
                    "confidence": 0.74
                  }],
                  "nextStepPlan": [{
                    "step": 1,
                    "target": "手推循环变量取值。",
                    "reason": "这是当前阻塞通过的问题。",
                    "evidenceRef": "code:range_excludes_n"
                  }],
                  "studentSummary": "这次有多个需要分开检查的问题。"
                }
                """;
    }

    private String majorIssueCoverageAdviceResponse() {
        return """
                {
                  "caseUnderstanding": {
                    "problemGoal": "题目要求输出 1 到 n 的整数和。",
                    "codeIntent": "学生使用循环累加 total。",
                    "behaviorGap": "循环范围没有覆盖题意，并且失败样例已经暴露差异。",
                    "primaryEvidenceRef": "code:range_excludes_n"
                  },
                  "basicLayerAdvice": [{
                    "issueId": "I1",
                    "mistakePointId": null,
                    "skillUnitId": null,
                    "title": "循环右边界漏取",
                    "whatHappened": "当前循环范围没有覆盖题目要求的最后一个数。",
                    "whyItMatters": "少处理一个端点会让求和结果偏小。",
                    "studentAction": "先手推循环变量实际出现过哪些值。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.92
                  }, {
                    "issueId": "I2",
                    "mistakePointId": null,
                    "skillUnitId": null,
                    "title": "失败样例对照不足",
                    "whatHappened": "可见失败样例已经显示实际输出与预期不一致。",
                    "whyItMatters": "不对照差异就容易只凭感觉修改。",
                    "studentAction": "把实际输出和预期输出逐项写在旁边。",
                    "checkQuestion": "第一处差异来自哪个循环取值？",
                    "evidenceRefs": ["judge:first_failed_case"],
                    "confidence": 0.78
                  }],
                  "improvementLayerAdvice": [],
                  "nextStepPlan": [{
                    "step": 1,
                    "target": "手推循环变量取值。",
                    "reason": "这是当前阻塞通过的问题。",
                    "evidenceRef": "code:range_excludes_n"
                  }],
                  "studentSummary": "先处理两个主要问题；当前没有必要额外增加提高建议。"
                }
                """;
    }

    private String unsafeAdviceResponse() {
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
                    "studentAction": "直接改成 range(1, n + 1)。",
                    "checkQuestion": "最后一个应该被处理的数有没有进入循环？",
                    "evidenceRefs": ["code:range_excludes_n"],
                    "confidence": 0.92
                  }],
                  "improvementLayerAdvice": [{
                    "improvementPointId": "TESTING_HABIT",
                    "skillUnitId": "SK_RANGE_BOUNDARY",
                    "title": "补充边界样例意识",
                    "currentLimit": "这类问题不是算法方向错，而是边界验证不足。",
                    "suggestion": "修复后补测最小值和端点样例。",
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
        private final Queue<String> responses = new ArrayDeque<>();
        private final List<String> systemPrompts = new ArrayList<>();
        private final List<String> userPrompts = new ArrayList<>();
        private final List<Integer> outputTokens = new ArrayList<>();
        private final List<String> parallelOutputThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        private java.util.concurrent.CyclicBarrier parallelOutputBarrier;
        private int callCount;

        StubAiReportService(ObjectMapper objectMapper,
                            ExternalModelAgentRuntime runtime,
                            AiStandardLibraryService standardLibraryService,
                            StandardLibraryNavigationPackBuilder navigationPackBuilder,
                            AiStandardLibraryGrowthAgentService growthAgentService,
                            String... responses) {
            super(objectMapper,
                    new AiCodeAssistSupport(),
                    runtime,
                    new ExternalModelFailureClassifier(),
                    new ExternalModelChatRequestFactory(),
                    growthAgentService,
                    standardLibraryService,
                    new StandardLibraryNavigationOutputValidator(),
                    navigationPackBuilder);
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
            outputTokens.add(null);
            String response = responses.poll();
            if (response == null) {
                throw new IOException("No stub response configured.");
            }
            return response;
        }

        @Override
        protected String chatCompletionWithOverrides(String systemPrompt,
                                                     String userPrompt,
                                                     boolean stream,
                                                     int outputTokens) throws IOException {
            awaitParallelOutputProbe(systemPrompt);
            if (systemPrompt.contains("teacher-insight-v1")) {
                return teacherInsightResponse(userPrompt);
            }
            callCount++;
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            this.outputTokens.add(outputTokens);
            String response = responses.poll();
            if (response == null) {
                throw new IOException("No stub response configured.");
            }
            return response;
        }

        void enableParallelOutputProbe() {
            parallelOutputBarrier = new java.util.concurrent.CyclicBarrier(2);
        }

        List<String> parallelOutputThreads() {
            return List.copyOf(parallelOutputThreads);
        }

        private void awaitParallelOutputProbe(String systemPrompt) throws IOException {
            if (parallelOutputBarrier == null || (!systemPrompt.contains("diagnosis-report-v4")
                    && !systemPrompt.contains("teacher-insight-v1"))) {
                return;
            }
            parallelOutputThreads.add(Thread.currentThread().getName());
            try {
                parallelOutputBarrier.await(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IOException("parallel output probe did not overlap", exception);
            }
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

        int callCount() {
            return callCount;
        }

        String userPrompt(int index) {
            return userPrompts.get(index);
        }

        String systemPrompt(int index) {
            return systemPrompts.get(index);
        }

        Integer outputTokens(int index) {
            return outputTokens.get(index);
        }

    }
}
