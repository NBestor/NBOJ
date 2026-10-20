package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.StudentAiFeedback;
import com.onlinejudge.submission.domain.StudentAiFeedbackEvent;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import com.onlinejudge.submission.dto.StudentAiFeedbackResponse;
import com.onlinejudge.submission.persistence.StudentAiFeedbackEventRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRepository;
import com.onlinejudge.submission.persistence.StudentAiFeedbackRevisionRepository;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class StudentAiFeedbackEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final SubmissionCaseResultRepository caseResultRepository = mock(SubmissionCaseResultRepository.class);
    private final StudentAiFeedbackRepository feedbackRepository = mock(StudentAiFeedbackRepository.class);
    private final StudentAiFeedbackEventRepository eventRepository = mock(StudentAiFeedbackEventRepository.class);
    private final StudentAiFeedbackRevisionRepository revisionRepository = mock(StudentAiFeedbackRevisionRepository.class);
    private final SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
    private final SubmissionAnalysisService submissionAnalysisService = mock(SubmissionAnalysisService.class);
    private final AiStandardSkillUnitRepository skillUnitRepository = mock(AiStandardSkillUnitRepository.class);
    private final AiStandardMistakePointRepository mistakePointRepository = mock(AiStandardMistakePointRepository.class);
    private final AiStandardImprovementPointRepository improvementPointRepository = mock(AiStandardImprovementPointRepository.class);
    private final InformaticsKnowledgeNodeRepository knowledgeRepository = mock(InformaticsKnowledgeNodeRepository.class);
    private final StudentAiFeedbackService service = new StudentAiFeedbackService(
            submissionRepository,
            problemRepository,
            caseResultRepository,
            feedbackRepository,
            eventRepository,
            revisionRepository,
            analysisRepository,
            submissionAnalysisService,
            skillUnitRepository,
            mistakePointRepository,
            improvementPointRepository,
            knowledgeRepository,
            objectMapper
    );

    StudentAiFeedbackEventTest() {
        when(revisionRepository.save(any())).thenAnswer(invocation -> {
            var revision = invocation.<com.onlinejudge.submission.domain.StudentAiFeedbackRevision>getArgument(0);
            revision.setId(501L);
            return revision;
        });
    }

    @Test
    void recordsViewedEventWithSubmissionContext() throws Exception {
        Submission submission = submission();
        StudentAiFeedbackResponse response = readyFeedback();
        StudentAiFeedback entity = StudentAiFeedback.builder()
                .submissionId(7L)
                .status("READY")
                .source("MODEL")
                .feedbackJson(objectMapper.writeValueAsString(response))
                .build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.of(entity));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(7L, StudentAiFeedbackEvent.EVENT_VIEWED))
                .thenReturn(Optional.empty());

        service.recordViewed(7L);

        ArgumentCaptor<StudentAiFeedbackEvent> captor = ArgumentCaptor.forClass(StudentAiFeedbackEvent.class);
        verify(eventRepository).save(captor.capture());
        StudentAiFeedbackEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(StudentAiFeedbackEvent.EVENT_VIEWED);
        assertThat(event.getSubmissionId()).isEqualTo(7L);
        assertThat(event.getStudentProfileId()).isEqualTo(41L);
        assertThat(event.getAssignmentId()).isEqualTo(9L);
        assertThat(event.getProblemId()).isEqualTo(101L);
        assertThat(event.getFeedbackStatus()).isEqualTo("READY");
        assertThat(event.getFeedbackSource()).isEqualTo("MODEL");
        assertThat(event.getAnswerLeakRisk()).isEqualTo("LOW");
    }

    @Test
    void doesNotDuplicateViewedEventForSameSubmission() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(7L, StudentAiFeedbackEvent.EVENT_VIEWED))
                .thenReturn(Optional.of(StudentAiFeedbackEvent.builder().id(1L).build()));

        service.recordViewed(7L);

        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateAndStoreUsesFullSubmissionAnalysisAdvice() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(fullAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_READY)))
                .thenReturn(Optional.empty());
        stubKnowledgePath();

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getSource()).isEqualTo("MODEL");
        assertThat(response.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(response.getRepairItems()).hasSize(2);
        assertThat(response.getImprovementItems()).hasSize(2);
        assertThat(response.getRepairItems().get(0).getTitle()).contains("输入读取");
        assertThat(response.getRepairItems().get(0).getLibraryFit()).isEqualTo("PARTIAL");
        assertThat(response.getRepairItems().get(0).getKnowledgePath())
                .containsExactly("信息学基础", "输入输出", "输入格式", "输入读取", "漏读变量");
        assertThat(response.getImprovementItems().get(0).getLibraryFit()).isEqualTo("PARTIAL");
        assertThat(response.getImprovementItems().get(0).getKnowledgePath())
                .containsExactly("信息学基础", "输入输出", "输入格式", "输入读取", "边界样例覆盖");
        assertThat(response.getRepairItems().get(0).getEvidenceSnippets()).singleElement()
                .satisfies(snippet -> assertThat(snippet.getCode()).contains("a = int(input())"));
        assertThat(response.getStudentReport().getBasicLayerText()).contains("完整链路");
        assertThat(response.getStudentReport().getNextActionText()).contains("手推");
        verify(submissionAnalysisService).generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        );
    }

    @Test
    void generateAndStoreInfersKnowledgePathWhenAdviceHasNoStandardIds() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(noStandardIdAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_READY)))
                .thenReturn(Optional.empty());
        when(knowledgeRepository.findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc()).thenReturn(List.of(
                InformaticsKnowledgeNode.builder()
                        .code("ALGO.SELECT.MEDIAN")
                        .type(InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                        .name("中位数")
                        .path("算法设计 / 排序与选择 / 中位数")
                        .aliases("中位数,median")
                        .enabled(true)
                        .build(),
                InformaticsKnowledgeNode.builder()
                        .code("ALGO.WINDOW.SLIDING")
                        .type(InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                        .name("滑动窗口")
                        .path("算法设计 / 双指针 / 滑动窗口")
                        .aliases("滑窗")
                        .enabled(true)
                        .build()
        ));

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getRepairItems().get(0).getKnowledgePath())
                .containsExactly("算法设计", "排序与选择", "中位数");
        assertThat(response.getImprovementItems().get(0).getKnowledgePath())
                .containsExactly("算法设计", "双指针", "滑动窗口");
    }

    @Test
    void generateAndStorePreservesProvisionalAndUnclassifiedPathStatuses() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .answerLeakRisk("LOW")
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .diagnosisLibraryFit("PARTIAL")
                        .build())
                .basicLayerAdvice(List.of(SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                        .issueId("I1")
                        .title("输出位置与累计目标脱节")
                        .whatHappened("模型发现了正式诊断层之外的细颗粒问题。")
                        .studentAction("先手推累计变量和输出变量的对应关系。")
                        .knowledgePath(List.of("信息学基础", "循环结构", "循环边界", "输出位置与累计目标脱节"))
                        .knowledgePathStatus("PROVISIONAL")
                        .provisionalNodeCode("MP_AI_ABC123")
                        .evidenceRefs(List.of("code:line:1"))
                        .build()))
                .improvementLayerAdvice(List.of(SubmissionAnalysisResponse.ImprovementLayerAdvice.builder()
                        .issueId("I1")
                        .title("补充迁移练习")
                        .suggestion("尝试构造一个更小的输入检查状态变化。")
                        .knowledgePath(List.of())
                        .knowledgePathStatus("UNCLASSIFIED")
                        .evidenceRefs(List.of("judge:first_failed_case:1"))
                        .build()))
                .build());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_READY)))
                .thenReturn(Optional.empty());

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getRepairItems()).singleElement().satisfies(item -> {
            assertThat(item.getKnowledgePathStatus()).isEqualTo("PROVISIONAL");
            assertThat(item.getProvisionalNodeCode()).isEqualTo("MP_AI_ABC123");
            assertThat(item.getKnowledgePath())
                    .containsExactly("信息学基础", "循环结构", "循环边界", "输出位置与累计目标脱节");
        });
        assertThat(response.getImprovementItems()).singleElement().satisfies(item -> {
            assertThat(item.getKnowledgePathStatus()).isEqualTo("UNCLASSIFIED");
            assertThat(item.getKnowledgePath()).isEmpty();
        });
    }

    @Test
    void generateAndStoreInfersCodeEvidenceWhenModelOnlyReturnsJudgeEvidence() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(diffSubmission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(judgeOnlyCodeNamedAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_READY)))
                .thenReturn(Optional.empty());

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getRepairItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getEvidenceRefs()).contains("judge:first_failed_case:1", "code:line:4");
                    assertThat(item.getEvidenceSnippets()).singleElement()
                            .satisfies(snippet -> {
                                assertThat(snippet.getLineNumber()).isEqualTo(4);
                                assertThat(snippet.getCode()).contains("def apply_operations");
                            });
                });
    }

    @Test
    void generateAndStoreStoresFullChainFailureClearly() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(modelFailedAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_FAILED)))
                .thenReturn(Optional.empty());

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getSource()).isEqualTo("AI_UNAVAILABLE");
        assertThat(response.getSafety().getBlockedReasons()).contains("FULL_CHAIN_FAILED");
    }

    @Test
    void generateAndStoreFailsClearlyWhenFullChainFeedbackIsEmpty() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(emptyCompletedAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_FAILED)))
                .thenReturn(Optional.empty());

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getSafety().getBlockedReasons()).contains("FULL_CHAIN_FEEDBACK_EMPTY");
    }

    @Test
    void generateAndStoreDoesNotConvertStudentReportOnlyIntoSingleCard() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem()));
        when(caseResultRepository.findBySubmissionIdOrderByTestCaseNumberAsc(7L)).thenReturn(caseResults());
        when(submissionAnalysisService.generateAndStoreAnalysis(
                any(Problem.class),
                any(Submission.class),
                any()
        )).thenReturn(studentReportOnlyAnalysis());
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findTopBySubmissionIdAndEventTypeOrderByCreatedAtDesc(eq(7L), eq(StudentAiFeedbackEvent.EVENT_FAILED)))
                .thenReturn(Optional.empty());

        StudentAiFeedbackResponse response = service.generateAndStore(7L);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getRepairItems()).isEmpty();
        assertThat(response.getImprovementItems()).isEmpty();
        assertThat(response.getSafety().getBlockedReasons()).contains("FULL_CHAIN_FEEDBACK_EMPTY");
    }

    @Test
    void markGeneratingKeepsFreshGeneratingRecordWithoutRestarting() throws Exception {
        StudentAiFeedback existing = StudentAiFeedback.builder()
                .submissionId(7L)
                .status("GENERATING")
                .source("AI_UNAVAILABLE")
                .generatedAt(LocalDateTime.now())
                .feedbackJson(objectMapper.writeValueAsString(StudentAiFeedbackResponse.builder()
                        .submissionId(7L)
                        .status("GENERATING")
                        .source("AI_UNAVAILABLE")
                        .repairItems(List.of())
                        .improvementItems(List.of())
                        .build()))
                .build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existing));

        var lookup = service.markGenerating(7L);

        assertThat(lookup.getStatus()).isEqualTo("GENERATING");
        verify(feedbackRepository, never()).save(any(StudentAiFeedback.class));
    }

    @Test
    void getLookupHydratesReadyRecordMissingKnowledgePathWithoutRestarting() throws Exception {
        StudentAiFeedbackResponse oldReadyFeedback = StudentAiFeedbackResponse.builder()
                .submissionId(7L)
                .status("READY")
                .source("MODEL")
                .repairItems(List.of(StudentAiFeedbackResponse.FeedbackItem.builder()
                        .title("使用平均数代替中位数")
                        .body("当前建议有代码证据，但旧记录没有知识路径。")
                        .evidenceRefs(List.of("code:line:1"))
                        .build()))
                .improvementItems(List.of())
                .evidenceRefs(List.of("code:line:1"))
                .build();
        StudentAiFeedback existing = StudentAiFeedback.builder()
                .submissionId(7L)
                .status("READY")
                .source("MODEL")
                .feedbackJson(objectMapper.writeValueAsString(oldReadyFeedback))
                .build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existing));
        when(knowledgeRepository.findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc()).thenReturn(List.of(
                InformaticsKnowledgeNode.builder()
                        .code("ALGO.SELECT.MEDIAN")
                        .type(InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                        .name("中位数")
                        .path("算法设计 / 排序与选择 / 中位数")
                        .aliases("中位数")
                        .enabled(true)
                        .build()
        ));

        var lookup = service.getLookup(7L);

        assertThat(lookup.getStatus()).isEqualTo("READY");
        assertThat(lookup.getFeedback().getRepairItems()).singleElement()
                .satisfies(item -> assertThat(item.getKnowledgePath())
                        .containsExactly("算法设计", "排序与选择", "中位数"));
        assertThat(service.needsContractRefresh(7L)).isFalse();
        verify(feedbackRepository, never()).save(any(StudentAiFeedback.class));
    }

    @Test
    void markGeneratingRefreshesExpiredGeneratingRecord() {
        StudentAiFeedback existing = StudentAiFeedback.builder()
                .submissionId(7L)
                .status("GENERATING")
                .source("AI_UNAVAILABLE")
                .generatedAt(LocalDateTime.now().minusMinutes(6))
                .build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission()));
        when(feedbackRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existing));
        when(feedbackRepository.save(any(StudentAiFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var lookup = service.markGenerating(7L);

        assertThat(lookup.getStatus()).isEqualTo("GENERATING");
        ArgumentCaptor<StudentAiFeedback> captor = ArgumentCaptor.forClass(StudentAiFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("GENERATING");
        assertThat(captor.getValue().getFeedbackJson()).contains("\"status\":\"GENERATING\"");
    }

    private Submission submission() {
        return Submission.builder()
                .id(7L)
                .assignmentId(9L)
                .studentProfileId(41L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("a = int(input())\nprint(a)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .build();
    }

    private Submission diffSubmission() {
        return Submission.builder()
                .id(7L)
                .assignmentId(9L)
                .studentProfileId(41L)
                .problemId(101L)
                .languageId(71)
                .sourceCode("""
                        n, m = map(int, input().split())
                        diff = [[0] * (m + 2) for _ in range(n + 2)]

                        def apply_operations(x1, y1, x2, y2):
                            diff[x1][y1] += 1
                            diff[x2 + 1][y1] -= 1
                        """)
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .build();
    }

    private Problem problem() {
        return Problem.builder()
                .id(101L)
                .title("两数求和")
                .description("给定两个整数 a 和 b，输出它们的和。")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(128 * 1024)
                .build();
    }

    private List<SubmissionCaseResult> caseResults() {
        return List.of(SubmissionCaseResult.builder()
                .submissionId(7L)
                .testCaseNumber(1)
                .passed(false)
                .hidden(false)
                .inputSnapshot("3 5")
                .actualOutput("1")
                .expectedOutput("8")
                .executionTime(0.01)
                .memoryUsed(1024)
                .build());
    }

    private StudentAiFeedbackResponse readyFeedback() {
        return StudentAiFeedbackResponse.builder()
                .submissionId(7L)
                .status("READY")
                .source("MODEL")
                .repairItems(List.of())
                .improvementItems(List.of())
                .nextQuestion("哪里先偏离？")
                .safety(StudentAiFeedbackResponse.Safety.builder()
                        .answerLeakRisk("LOW")
                        .blockedReasons(List.of())
                        .build())
                .evidenceRefs(List.of("judge:first_failed_case:1"))
                .build();
    }

    private SubmissionAnalysisResponse fullAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .summary("完整链路：这次主要是输入读取和计算目标没有对齐。")
                .answerLeakRisk("LOW")
                .evidenceRefs(List.of("code:line:1", "judge:first_failed_case:1"))
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .diagnosisLibraryFit("PARTIAL")
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("完整链路：这次主要是输入读取和计算目标没有对齐。")
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .task("先手推第一行输入每个数分别被哪句代码读走。")
                                .checkQuestion("第一行输入里第二个数在哪里被读取？")
                                .answerLeakRisk("LOW")
                                .evidenceRefs(List.of("code:line:1"))
                                .build())
                        .build())
                .basicLayerAdvice(List.of(
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .title("输入读取只取了一个数")
                                .skillUnitId("SK_INPUT_READING")
                                .mistakePointId("MP_INPUT_SECOND_VALUE_MISSING")
                                .whatHappened("程序只读取了 a，没有读取 b。")
                                .whyItMatters("题目要求输出两个数的和，少读一个数会让结果偏小。")
                                .studentAction("手推输入 3 5 时每个变量的值。")
                                .evidenceRefs(List.of("code:line:1"))
                                .build(),
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .title("输出没有体现求和目标")
                                .whatHappened("当前输出只打印 a。")
                                .studentAction("对照首个失败点核对实际输出和期望输出。")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build()
                ))
                .improvementLayerAdvice(List.of(
                        SubmissionAnalysisResponse.ImprovementLayerAdvice.builder()
                                .title("边界样例覆盖")
                                .skillUnitId("SK_INPUT_READING")
                                .improvementPointId("IP_BOUNDARY_CASES")
                                .suggestion("修完后补测 0、负数和较大数。")
                                .studentBenefit("避免只适配样例。")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build(),
                        SubmissionAnalysisResponse.ImprovementLayerAdvice.builder()
                                .title("输入格式复核")
                                .skillUnitId("SK_INPUT_READING")
                                .improvementPointId("IP_INPUT_FORMAT_REVIEW")
                                .suggestion("先把题面输入格式拆成变量表。")
                                .studentBenefit("减少漏读变量。")
                                .evidenceRefs(List.of("code:line:1"))
                                .build()
                ))
                .build();
    }

    private SubmissionAnalysisResponse noStandardIdAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .summary("这次主要是中位数计算和窗口维护没有对齐。")
                .answerLeakRisk("LOW")
                .evidenceRefs(List.of("code:line:1", "judge:first_failed_case:1"))
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .diagnosisLibraryFit("PARTIAL")
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("这次主要是中位数计算和窗口维护没有对齐。")
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .task("先手推窗口内元素和目标中位数。")
                                .checkQuestion("当前 target 真的是窗口中位数吗？")
                                .answerLeakRisk("LOW")
                                .evidenceRefs(List.of("code:line:1"))
                                .build())
                        .build())
                .basicLayerAdvice(List.of(
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .title("使用平均数代替中位数")
                                .whatHappened("代码用窗口平均数判断目标，但题目要求窗口中位数。")
                                .whyItMatters("平均数和中位数不是同一个统计量。")
                                .studentAction("手推窗口排序后中间位置。")
                                .evidenceRefs(List.of("code:line:1"))
                                .build()
                ))
                .improvementLayerAdvice(List.of(
                        SubmissionAnalysisResponse.ImprovementLayerAdvice.builder()
                                .title("滑动窗口优化技巧")
                                .currentLimit("当前每次重新处理整个窗口。")
                                .suggestion("理解滑动窗口状态如何随左右端点更新。")
                                .studentBenefit("减少重复计算。")
                                .evidenceRefs(List.of("code:line:1"))
                                .build()
                ))
                .build();
    }

    private void stubKnowledgePath() {
        when(knowledgeRepository.findByCode("BASIC.IO.INPUT_FORMAT"))
                .thenReturn(Optional.of(InformaticsKnowledgeNode.builder()
                        .code("BASIC.IO.INPUT_FORMAT")
                        .type(InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                        .name("输入格式")
                        .path("信息学基础 / 输入输出 / 输入格式")
                        .enabled(true)
                        .build()));
        when(skillUnitRepository.findByCode("SK_INPUT_READING"))
                .thenReturn(Optional.of(AiStandardSkillUnit.builder()
                        .code("SK_INPUT_READING")
                        .name("输入读取")
                        .primaryKnowledgeNodeCode("BASIC.IO.INPUT_FORMAT")
                        .build()));
        when(mistakePointRepository.findByCode("MP_INPUT_SECOND_VALUE_MISSING"))
                .thenReturn(Optional.of(AiStandardMistakePoint.builder()
                        .code("MP_INPUT_SECOND_VALUE_MISSING")
                        .name("漏读变量")
                        .skillUnitCode("SK_INPUT_READING")
                        .primaryKnowledgeNodeCode("BASIC.IO.INPUT_FORMAT")
                        .build()));
        when(improvementPointRepository.findByCode("IP_BOUNDARY_CASES"))
                .thenReturn(Optional.of(AiStandardImprovementPoint.builder()
                        .code("IP_BOUNDARY_CASES")
                        .name("边界样例覆盖")
                        .skillUnitCode("SK_INPUT_READING")
                        .primaryKnowledgeNodeCode("BASIC.IO.INPUT_FORMAT")
                        .build()));
        when(improvementPointRepository.findByCode("IP_INPUT_FORMAT_REVIEW"))
                .thenReturn(Optional.of(AiStandardImprovementPoint.builder()
                        .code("IP_INPUT_FORMAT_REVIEW")
                        .name("输入格式复核")
                        .skillUnitCode("SK_INPUT_READING")
                        .primaryKnowledgeNodeCode("BASIC.IO.INPUT_FORMAT")
                        .build()));
    }

    private SubmissionAnalysisResponse judgeOnlyCodeNamedAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .summary("二维差分边界处理需要修正。")
                .answerLeakRisk("LOW")
                .evidenceRefs(List.of("judge:first_failed_case:1"))
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .build())
                .basicLayerAdvice(List.of(
                        SubmissionAnalysisResponse.BasicLayerAdvice.builder()
                                .title("二维差分边界条件错误")
                                .whatHappened("在 apply_operations 函数中，边界检查条件不完整。")
                                .whyItMatters("当 x2 == n 或 y2 == m 时，diff 的反向操作容易遗漏。")
                                .studentAction("先定位 apply_operations 并手推一个右边界样例。")
                                .evidenceRefs(List.of("judge:first_failed_case:1"))
                                .build()
                ))
                .build();
    }

    private SubmissionAnalysisResponse modelFailedAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_FAILED")
                        .build())
                .build();
    }

    private SubmissionAnalysisResponse emptyCompletedAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .build())
                .build();
    }

    private SubmissionAnalysisResponse studentReportOnlyAnalysis() {
        return SubmissionAnalysisResponse.builder()
                .submissionId(7L)
                .summary("这次重点是循环边界。")
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .status("MODEL_COMPLETED")
                        .build())
                .studentFeedback(SubmissionAnalysisResponse.StudentFeedback.builder()
                        .summary("基础层：循环范围和题目边界要求没有完全对齐。")
                        .blockingIssues(List.of())
                        .improvementOpportunities(List.of())
                        .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                                .task("先手推一个最小样例。")
                                .checkQuestion("端点有没有进入循环？")
                                .answerLeakRisk("LOW")
                                .evidenceRefs(List.of("code:line:1"))
                                .build())
                        .build())
                .build();
    }
}
