package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachPromptResponse;
import com.onlinejudge.classroom.dto.CoachReplyRequest;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.application.DiagnosisEvidencePackageReader;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CoachPromptServiceTest {

    private final FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
    private final FakeSubmissionAnalysisRepository analysisRepository = new FakeSubmissionAnalysisRepository();
    private final FakeAssignmentRepository assignmentRepository = new FakeAssignmentRepository();
    private final FakeCoachPromptRepository promptRepository = new FakeCoachPromptRepository();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoachPromptService service = new CoachPromptService(
            submissionRepository,
            analysisRepository,
            assignmentRepository,
            promptRepository,
            new DiagnosisReportReader(objectMapper, taxonomy),
            taxonomy,
            new DiagnosisEvidencePackageReader(objectMapper),
            new ModelStubCoachAgentService(objectMapper, taxonomy),
            objectMapper
    );

    private CoachPromptService serviceWith(CoachAgentService coachAgentService) {
        return new CoachPromptService(
                submissionRepository,
                analysisRepository,
                assignmentRepository,
                promptRepository,
                new DiagnosisReportReader(objectMapper, taxonomy),
                taxonomy,
                new DiagnosisEvidencePackageReader(objectMapper),
                coachAgentService,
                objectMapper
        );
    }

    @Test
    void generatesSocraticQuestionFromFineGrainedTag() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(11L, 7L, Submission.Verdict.WRONG_ANSWER);
        createSubmission(10L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(11L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]");
        createAnalysis(10L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[]");

        CoachPromptResponse response = service.generateNextQuestion(11L);

        assertThat(response.getQuestion()).contains("最小失败样例");
        assertThat(response.getQuestion()).contains("n=1");
        assertThat(response.getQuestion()).doesNotContain("完整代码");
        assertThat(response.getHintPolicy()).isEqualTo("L2");
        assertThat(response.getPromptType()).isEqualTo("SOCRATIC_NEXT_STEP_MODEL");
        assertThat(response.getEvidenceRefs()).contains("submission:11", "tag:OFF_BY_ONE", "code:plus_minus_one");
        assertThat(response.getAdaptiveStrategySignal()).isNotNull();
        assertThat(response.getAdaptiveStrategySignal().getStrategy()).isEqualTo("REDUCE_GRANULARITY");
        assertThat(response.getAdaptiveStrategySignal().getEvidenceRefs()).contains("coach-strategy:REDUCE_GRANULARITY");
        assertThat(response.getContextSummary()).contains("最近多次出现细分卡点");
        assertThat(response.getRationale()).contains("最近多次出现细分卡点");
        assertThat(response.getTurnIndex()).isEqualTo(1);
        assertThat(promptRepository.saved).hasSize(1);
    }

    @Test
    void reducesGranularityWhenPreviousLearningActionWasContradicted() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(16L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(16L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]", null, """
                "learningActionEvidence": {
                  "expectedActionType": "TRACE_VARIABLES",
                  "executionStatus": "CONTRADICTED",
                  "observedEvidence": "后续提交仍停留在相同错因。",
                  "confidence": 0.74,
                  "evidenceRefs": ["eval:submission:16", "action:CONTRADICTED"],
                  "nextAdjustment": "前次学习动作被后续证据反驳，需要降低提示粒度。"
                }
                """);

        CoachPromptResponse response = service.generateNextQuestion(16L);

        assertThat(response.getAdaptiveStrategySignal()).isNotNull();
        assertThat(response.getAdaptiveStrategySignal().getStrategy()).isEqualTo("REDUCE_GRANULARITY");
        assertThat(response.getAdaptiveStrategySignal().isNeedsTeacherAttention()).isTrue();
        assertThat(response.getAdaptiveStrategySignal().getReason()).contains("反驳");
        assertThat(response.getAdaptiveStrategySignal().getRecommendedCoachMove()).contains("最小失败样例");
        assertThat(response.getEvidenceRefs()).contains(
                "coach-strategy:REDUCE_GRANULARITY",
                "coach-adaptive:previous_action:CONTRADICTED",
                "eval:submission:16",
                "action:CONTRADICTED"
        );
        assertThat(response.getQuestion()).contains("最小失败样例");
        assertThat(response.getContextSummary()).contains("Coach 自适应策略");
        assertThat(response.getRationale()).contains("降低提示粒度");
    }

    @Test
    void generatesReviewQuestionForAcceptedSubmission() {
        createAssignment(7L, Assignment.HintPolicy.L3);
        createSubmission(12L, 7L, Submission.Verdict.ACCEPTED);
        createAnalysis(12L, "[\"GENERALIZATION_CHECK\"]", "[]", "[]");

        CoachPromptResponse response = service.generateNextQuestion(12L);

        assertThat(response.getQuestion()).contains("复杂度");
        assertThat(response.getQuestion()).contains("泛化能力");
        assertThat(response.getAdaptiveStrategySignal()).isNotNull();
        assertThat(response.getAdaptiveStrategySignal().getStrategy()).isEqualTo("TRANSFER_REFLECTION");
        assertThat(response.getEvidenceRefs()).contains("coach-strategy:TRANSFER_REFLECTION", "coach-adaptive:verdict:ACCEPTED");
    }

    @Test
    void includesPersistedEvidencePackageInContextSummary() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(13L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(13L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]", """
                {
                  "schemaVersion": "evidence-v1",
                  "submission": {
                    "id": 13,
                    "language": "Python 3",
                    "verdict": "WRONG_ANSWER",
                    "sourceCodeLineCount": 4
                  },
                  "problem": {
                    "id": 101,
                    "title": "边界练习"
                  },
                  "judgeFacts": {
                    "passedCount": 1,
                    "totalCount": 2,
                    "hiddenFailureObserved": true,
                    "firstFailedCase": {
                      "testCaseNumber": 2,
                      "hidden": true
                    }
                  },
                  "history": {
                    "repeatedFineGrainedTag": "OFF_BY_ONE"
                  },
                  "policy": {
                    "hintPolicy": "L2",
                    "allowedHintLevels": ["L1", "L2"]
                  }
                }
                """);

        CoachPromptResponse response = service.generateNextQuestion(13L);

        assertThat(response.getEvidenceRefs()).contains(
                "evidence:evidence-v1",
                "judge:cases:1/2",
                "judge:first_failed_case:2",
                "judge:hidden_failure",
                "history:repeated_fine_tag:OFF_BY_ONE",
                "policy:L2"
        );
        assertThat(response.getContextSummary()).contains("证据包摘要");
        assertThat(response.getContextSummary()).contains("已通过 1/2 个测试点");
        assertThat(response.getContextSummary()).contains("隐藏测试点");
    }

    @Test
    void coachPromptCarriesFineGrainedStudentProfileContext() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(21L, 7L, Submission.Verdict.WRONG_ANSWER);
        createSubmission(20L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(21L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:range_stop\"]", null, """
                "abilityPoints": ["循环与边界"],
                "focusPoints": ["range 右端不包含"]
                """);
        createAnalysis(20L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:old_range_stop\"]", null, """
                "abilityPoints": ["循环与边界"],
                "focusPoints": ["range 右端不包含"]
                """);

        CoachPromptResponse response = service.generateNextQuestion(21L);

        assertThat(response.getContextSummary()).contains("学生画像");
        assertThat(response.getContextSummary()).contains("循环与边界");
        assertThat(response.getContextSummary()).contains("range 右端不包含");
        assertThat(response.getContextSummary()).contains("最近复盘题");
        assertThat(response.getEvidenceRefs()).contains(
                "student-profile:fine-tag:OFF_BY_ONE",
                "student-profile:ability:循环与边界",
                "student-profile:review-submission:21"
        );
        assertThat(promptRepository.saved.get(0).getContextSummary()).contains("学生画像");
    }

    @Test
    void storesStudentAnswerAndGeneratesFollowUpTurn() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(14L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(14L, "[\"TIME_COMPLEXITY\"]", "[\"BRUTE_FORCE_LIMIT\"]", "[\"judge:cases:1/3\"]");
        CoachPromptResponse first = service.generateNextQuestion(14L);
        CoachReplyRequest request = new CoachReplyRequest();
        request.setAnswer("最大 n=100000 时双层循环次数太多，我要先构造最大输入估算复杂度。");

        CoachPromptResponse next = service.replyAndGenerateNext(14L, request);

        assertThat(first.getTurnIndex()).isEqualTo(1);
        assertThat(next.getTurnIndex()).isEqualTo(2);
        assertThat(next.getPromptType()).isEqualTo("SOCRATIC_FOLLOW_UP_MODEL");
        assertThat(next.getQuestion()).contains("最小修改").doesNotContain("完整代码");
        assertThat(next.getAdaptiveStrategySignal()).isNotNull();
        assertThat(next.getAdaptiveStrategySignal().getStrategy()).isEqualTo("VERIFY_MINIMAL_CHANGE");
        assertThat(next.getEvidenceRefs()).contains(
                "coach-strategy:VERIFY_MINIMAL_CHANGE",
                "coach-adaptive:answer_quality:EVIDENCE_GROUNDED"
        );
        assertThat(next.getTurns()).hasSize(2);
        assertThat(next.getTurns().get(0).getStudentAnswer()).contains("最大 n=100000");
        assertThat(next.getTurns().get(0).getCoachFeedback()).contains("证据意识");
        assertThat(next.getTurns().get(0).getAnsweredAt()).isNotNull();
        assertThat(promptRepository.saved.get(0).getAnsweredAt()).isNotNull();
        assertThat(next.getTurns().get(1).getQuestion()).isEqualTo(next.getQuestion());
        assertThat(next.getTurns().get(1).getAdaptiveStrategySignal().getStrategy()).isEqualTo("VERIFY_MINIMAL_CHANGE");
    }

    @Test
    void asksForEvidenceWhenStudentAnswerIsVague() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(15L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(15L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]");
        service.generateNextQuestion(15L);
        CoachReplyRequest request = new CoachReplyRequest();
        request.setAnswer("我觉得循环那里可能不太对。");

        CoachPromptResponse next = service.replyAndGenerateNext(15L, request);

        assertThat(next.getQuestion()).contains("最小 n 值");
        assertThat(next.getAdaptiveStrategySignal()).isNotNull();
        assertThat(next.getAdaptiveStrategySignal().getStrategy()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(next.getEvidenceRefs()).contains(
                "coach-strategy:COLLECT_EVIDENCE",
                "coach-adaptive:answer_quality:NEEDS_EVIDENCE"
        );
        assertThat(next.getTurns().get(0).getCoachFeedback()).contains("证据还不够");
    }

    @Test
    void safetyResetWhenStudentAnswerContainsAnswerLikeContent() {
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(17L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(17L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]");
        service.generateNextQuestion(17L);
        CoachReplyRequest request = new CoachReplyRequest();
        request.setAnswer("答案就是直接改成完整代码。");

        CoachPromptResponse next = service.replyAndGenerateNext(17L, request);

        assertThat(next.getAdaptiveStrategySignal()).isNotNull();
        assertThat(next.getAdaptiveStrategySignal().getStrategy()).isEqualTo("SAFETY_RESET");
        assertThat(next.getAdaptiveStrategySignal().isNeedsTeacherAttention()).isTrue();
        assertThat(next.getEvidenceRefs()).contains(
                "coach-strategy:SAFETY_RESET",
                "coach-adaptive:answer_quality:SAFETY_RISK"
        );
        assertThat(next.getQuestion()).contains("输入特征").doesNotContain("完整代码即可");
        assertThat(next.getTurns().get(0).getCoachFeedback()).contains("证据层");
    }

    @Test
    void persistsCoachModelSafetyRejectionMetadata() {
        CoachPromptService service = serviceWith(new SafetyRejectedCoachAgentService(objectMapper, taxonomy));
        createAssignment(7L, Assignment.HintPolicy.L2);
        createSubmission(18L, 7L, Submission.Verdict.WRONG_ANSWER);
        createAnalysis(18L, "[\"LOOP_BOUNDARY\"]", "[\"OFF_BY_ONE\"]", "[\"code:plus_minus_one\"]");

        CoachPromptResponse response = service.generateNextQuestion(18L);

        assertThat(response.getQuestion()).contains("AI 追问暂不可用");
        assertThat(response.getPromptType()).isEqualTo("SOCRATIC_NEXT_STEP_AI_UNAVAILABLE");
        assertThat(response.getModelFailureReason()).isEqualTo("SAFETY_REJECTED");
        assertThat(response.getModelAnswerLeakRisk()).isEqualTo("HIGH");
        assertThat(promptRepository.saved).hasSize(1);
        assertThat(promptRepository.saved.get(0).getModelFailureReason()).isEqualTo("SAFETY_REJECTED");
        assertThat(promptRepository.saved.get(0).getModelAnswerLeakRisk()).isEqualTo("HIGH");
    }

    private void createAssignment(Long id, Assignment.HintPolicy policy) {
        assignmentRepository.items.put(id, Assignment.builder()
                .id(id)
                .title("课堂作业")
                .hintPolicy(policy)
                .build());
    }

    private void createSubmission(Long id, Long assignmentId, Submission.Verdict verdict) {
        submissionRepository.items.put(id, Submission.builder()
                .id(id)
                .assignmentId(assignmentId)
                .studentProfileId(3L)
                .problemId(101L)
                .languageId(71)
                .languageName("Python 3")
                .sourceCode("print(1)")
                .verdict(verdict)
                .submittedAt(LocalDateTime.of(2026, 5, 18, 12, 0))
                .build());
    }

    private void createAnalysis(Long submissionId, String issueTags, String fineTags, String evidenceRefs) {
        createAnalysis(submissionId, issueTags, fineTags, evidenceRefs, null);
    }

    private void createAnalysis(Long submissionId, String issueTags, String fineTags, String evidenceRefs, String evidenceJson) {
        createAnalysis(submissionId, issueTags, fineTags, evidenceRefs, evidenceJson, null);
    }

    private void createAnalysis(Long submissionId,
                                String issueTags,
                                String fineTags,
                                String evidenceRefs,
                                String evidenceJson,
                                String extraReportJsonFields) {
        String extraFields = extraReportJsonFields == null || extraReportJsonFields.isBlank()
                ? ""
                : ",\n" + extraReportJsonFields;
        analysisRepository.items.put(submissionId, SubmissionAnalysis.builder()
                .submissionId(submissionId)
                .analysisSource("RULE_BASED_V1")
                .scenario("WA")
                .headline("规则诊断")
                .summary("规则诊断")
                .reportMarkdown("规则诊断")
                .reportJson("""
                        {
                          "issueTags": %s,
                          "fineGrainedTags": %s,
                          "evidenceRefs": %s%s
                        }
                        """.formatted(issueTags, fineTags, evidenceRefs, extraFields))
                .evidenceJson(evidenceJson)
                .build());
    }

    private static class SafetyRejectedCoachAgentService extends CoachAgentService {
        private SafetyRejectedCoachAgentService(ObjectMapper objectMapper, DiagnosisTaxonomy diagnosisTaxonomy) {
            super(objectMapper, diagnosisTaxonomy);
        }

        @Override
        public CoachDraft generateInitialQuestion(Submission submission,
                                                  SubmissionAnalysis analysis,
                                                  String primaryTag,
                                                  Assignment.HintPolicy hintPolicy,
                                                  String contextSummary,
                                                  List<String> evidenceRefs) {
            CoachDraft draft = CoachDraft.unavailable("SAFETY_REJECTED");
            draft.setModelAnswerLeakRisk("HIGH");
            return draft;
        }
    }

    private static class ModelStubCoachAgentService extends CoachAgentService {
        private ModelStubCoachAgentService(ObjectMapper objectMapper, DiagnosisTaxonomy diagnosisTaxonomy) {
            super(objectMapper, diagnosisTaxonomy);
        }

        @Override
        public CoachDraft generateInitialQuestion(Submission submission,
                                                  SubmissionAnalysis analysis,
                                                  String primaryTag,
                                                  Assignment.HintPolicy hintPolicy,
                                                  String contextSummary,
                                                  List<String> evidenceRefs) {
            String question = submission != null && submission.getVerdict() == Submission.Verdict.ACCEPTED
                    ? "请说明复杂度，并补一个和样例不同的测试来验证泛化能力。"
                    : "请先构造最小失败样例，并手推 n=1 的循环变量变化。";
            return modelDraft(question, contextSummary, evidenceRefs);
        }

        @Override
        public CoachDraft generateFollowUpQuestion(Submission submission,
                                                   SubmissionAnalysis analysis,
                                                   String primaryTag,
                                                   Assignment.HintPolicy hintPolicy,
                                                   String contextSummary,
                                                   List<String> evidenceRefs,
                                                   String studentAnswer,
                                                   Integer currentTurnIndex) {
            String normalized = studentAnswer == null ? "" : studentAnswer;
            String question;
            if (normalized.contains("完整代码") || normalized.contains("直接改成")) {
                question = "请只描述一个会暴露问题的输入特征，不要直接说完整代码。";
            } else if (normalized.contains("最大") || normalized.contains("复杂度")) {
                question = "请只做一个最小修改，并预测下一次评测现象。";
            } else {
                question = "请写出一个最小 n 值，并列出循环第一次和最后一次取值。";
            }
            return modelDraft(question, contextSummary, evidenceRefs);
        }

        private CoachDraft modelDraft(String question, String contextSummary, List<String> evidenceRefs) {
            return CoachDraft.builder()
                    .question(question)
                    .rationale(contextSummary != null && contextSummary.contains("降低提示粒度")
                            ? "降低提示粒度，要求学生补充证据。"
                            : "模型基于上下文生成追问。")
                    .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                    .confidence(0.82)
                    .answerLeakRisk("LOW")
                    .modelAnswerLeakRisk("LOW")
                    .source("MODEL")
                    .build();
        }
    }

    private static class FakeSubmissionRepository extends UnsupportedJpaRepository<Submission, Long> implements SubmissionRepository {
        private final Map<Long, Submission> items = new LinkedHashMap<>();

        @Override
        public Optional<Submission> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtDesc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByProblemIdOrderBySubmittedAtAsc(Long problemId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdIn(Collection<Long> assignmentIds) {
            return items.values()
                    .stream()
                    .filter(item -> assignmentIds.contains(item.getAssignmentId()))
                    .toList();
        }

        @Override
        public List<Submission> findByStudentProfileIdInOrderBySubmittedAtDesc(Collection<Long> studentProfileIds) {
            return List.of();
        }

        @Override
        public List<Submission> findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long studentProfileId) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .filter(item -> Objects.equals(item.getStudentProfileId(), studentProfileId))
                    .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<Submission> findByAssignmentIdAndProblemIdAndStudentProfileIdOrderBySubmittedAtDesc(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<Submission> findTop10ByOrderBySubmittedAtDesc() {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.submission.persistence.SubmissionHistoryProjection> findHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.submission.persistence.SubmissionHistoryProjection> findHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.submission.persistence.SubmissionHistoryProjection> findHistorySummariesByAssignmentIdAndProblemIdAndStudentProfileId(Long assignmentId, Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.submission.persistence.SubmissionHistoryProjection> findPublicHistorySummariesByProblemIdAndStudentProfileId(Long problemId, Long studentProfileId) {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.submission.persistence.SubmissionHistoryProjection> findAnonymousHistorySummariesByProblemId(Long problemId) {
            return List.of();
        }

        @Override
        public List<com.onlinejudge.leaderboard.persistence.ProblemSubmissionStatsProjection> summarizeByProblem() {
            return List.of();
        }

        @Override
        public long deleteByProblemId(Long problemId) {
            return 0;
        }
    }

    private static class FakeSubmissionAnalysisRepository extends UnsupportedJpaRepository<SubmissionAnalysis, Long> implements SubmissionAnalysisRepository {
        private final Map<Long, SubmissionAnalysis> items = new LinkedHashMap<>();

        @Override
        public Optional<SubmissionAnalysis> findBySubmissionId(Long submissionId) {
            return Optional.ofNullable(items.get(submissionId));
        }

        @Override
        public List<SubmissionAnalysis> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return items.entrySet()
                    .stream()
                    .filter(entry -> submissionIds.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        @Override
        public long deleteBySubmissionId(Long submissionId) {
            return items.remove(submissionId) == null ? 0 : 1;
        }

        @Override
        public long deleteBySubmissionIdIn(Collection<Long> submissionIds) {
            long before = items.size();
            submissionIds.forEach(items::remove);
            return before - items.size();
        }
    }

    private static class FakeAssignmentRepository extends UnsupportedJpaRepository<Assignment, Long> implements AssignmentRepository {
        private final Map<Long, Assignment> items = new LinkedHashMap<>();

        @Override
        public Optional<Assignment> findById(Long id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<Assignment> findAllByOrderByCreatedAtDesc() {
            return List.copyOf(items.values());
        }

        @Override
        public List<Assignment> findByClassGroupIdOrderByCreatedAtDesc(Long classGroupId) {
            return items.values()
                    .stream()
                    .filter(item -> Objects.equals(item.getClassGroupId(), classGroupId))
                    .toList();
        }
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long> implements CoachPromptRepository {
        private final List<CoachPrompt> saved = new ArrayList<>();
        private long nextId = 1;

        @Override
        public CoachPrompt save(CoachPrompt prompt) {
            if (prompt.getId() == null) {
                prompt.setId(nextId++);
                prompt.setCreatedAt(LocalDateTime.of(2026, 5, 18, 12, 20));
                saved.add(prompt);
            } else {
                for (int index = 0; index < saved.size(); index++) {
                    if (Objects.equals(saved.get(index).getId(), prompt.getId())) {
                        saved.set(index, prompt);
                        break;
                    }
                }
            }
            return prompt;
        }

        @Override
        public Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .sorted(Comparator
                            .comparing(CoachPrompt::getTurnIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(CoachPrompt::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
        }
    }

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() {
            throw unsupported();
        }

        public List<T> findAllById(Iterable<ID> ids) {
            throw unsupported();
        }

        public <S extends T> S save(S entity) {
            throw unsupported();
        }

        public <S extends T> List<S> saveAll(Iterable<S> entities) {
            throw unsupported();
        }

        public Optional<T> findById(ID id) {
            throw unsupported();
        }

        public boolean existsById(ID id) {
            throw unsupported();
        }

        public long count() {
            throw unsupported();
        }

        public void deleteById(ID id) {
            throw unsupported();
        }

        public void delete(T entity) {
            throw unsupported();
        }

        public void deleteAllById(Iterable<? extends ID> ids) {
            throw unsupported();
        }

        public void deleteAll(Iterable<? extends T> entities) {
            throw unsupported();
        }

        public void deleteAll() {
            throw unsupported();
        }

        public void flush() {
            throw unsupported();
        }

        public <S extends T> S saveAndFlush(S entity) {
            throw unsupported();
        }

        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw unsupported();
        }

        public void deleteAllInBatch(Iterable<T> entities) {
            throw unsupported();
        }

        public void deleteAllByIdInBatch(Iterable<ID> ids) {
            throw unsupported();
        }

        public void deleteAllInBatch() {
            throw unsupported();
        }

        public T getOne(ID id) {
            throw unsupported();
        }

        public T getById(ID id) {
            throw unsupported();
        }

        public T getReferenceById(ID id) {
            throw unsupported();
        }

        public List<T> findAll(Sort sort) {
            throw unsupported();
        }

        public Page<T> findAll(Pageable pageable) {
            throw unsupported();
        }

        public <S extends T> Optional<S> findOne(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> List<S> findAll(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
            throw unsupported();
        }

        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw unsupported();
        }

        public <S extends T> long count(Example<S> example) {
            throw unsupported();
        }

        public <S extends T> boolean exists(Example<S> example) {
            throw unsupported();
        }

        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }
}
