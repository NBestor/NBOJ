package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticAgentServiceTest {

    @Test
    void diagnoseDoesNotInjectLegacyRuleSignals() {
        DiagnosticAgentService service = newService();

        Submission submission = Submission.builder()
                .id(10L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        total = 0
                        for i in range(n - 1):
                            total += i
                        print(total)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(1L)
                .title("求和")
                .description("输入 n，输出 1 到 n 的和")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(10L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2
        );

        assertThat(result.analysis().getIssueTags()).contains("BOUNDARY_CONDITION");
        assertThat(result.analysis().getIssueTags()).doesNotContain("LOOP_BOUNDARY");
        assertThat(result.analysis().getFineGrainedTags()).doesNotContain("OFF_BY_ONE");
        assertThat(result.analysis().getEvidenceRefs()).doesNotContain("code:plus_minus_one");
        assertThat(result.traceSummary()).contains("diagnostic-agent-v2");
        assertThat(result.analysis().getDiagnosticTrace()).contains("model=completed");
        assertThat(result.analysis().getAiInvocation()).isNotNull();
        assertThat(result.analysis().getAiInvocation().getAgentVersion()).isEqualTo("diagnostic-agent-v2");
        assertThat(result.analysis().getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(result.analysis().getStudentHintPlan()).isNotNull();
        assertThat(result.analysis().getStudentHintPlan().getHintLevel()).isEqualTo("L2");
        assertThat(result.analysis().getStudentHintPlan().getProblemType()).isNotBlank();
        assertThat(result.analysis().getStudentHintPlan().getEvidenceRefs()).doesNotContain("code:plus_minus_one");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isNotBlank();
        assertThat(result.analysis().getLearningInterventionPlan().getStudentTask()).isNotBlank();
        assertThat(result.analysis().getLearningInterventionPlan().getCompletionSignal()).isNotBlank();
        assertThat(result.analysis().getLearningInterventionPlan().getEvidenceRefs()).doesNotContain("code:plus_minus_one");
    }

    @Test
    void teacherCalibrationConflictLowersConfidenceAndRequestsTeacherReview() {
        DiagnosticAgentService service = newService();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(10L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of("LOOP_BOUNDARY"))
                .fineGrainedTags(List.of("OFF_BY_ONE"))
                .evidenceRefs(List.of("code:plus_minus_one"))
                .confidence(0.82)
                .uncertainty("当前代码存在边界迹象。")
                .teacherNote("先看边界。")
                .build();
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                        .teacherCalibrationPatterns(List.of(DiagnosisEvidencePackage.TeacherCalibrationPattern.builder()
                                .originalIssueTag("LOOP_BOUNDARY")
                                .originalFineGrainedTag("OFF_BY_ONE")
                                .correctedIssueTag("TIME_COMPLEXITY")
                                .correctedFineGrainedTag("BRUTE_FORCE_LIMIT")
                                .correctionCount(2L)
                                .evidenceSubmissionIds(List.of(21L, 22L))
                                .evidenceRefs(List.of("memory:teacher_calibration:brute_force_limit"))
                                .build()))
                        .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                Problem.builder().id(1L).title("多组输入").description("需要读到 EOF。").build(),
                Submission.builder()
                        .id(10L)
                        .languageName("Python 3")
                        .verdict(Submission.Verdict.WRONG_ANSWER)
                        .sourceCode("n=int(input())\nprint(n)")
                        .build(),
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                DiagnosisEvidencePackage.HistoryEvidence.builder().build(),
                memory
        );

        assertThat(result.analysis().getTeacherCalibrationSignal()).isNotNull();
        assertThat(result.analysis().getTeacherCalibrationSignal().getStatus()).isEqualTo("CONFLICT_NEEDS_REVIEW");
        assertThat(result.analysis().getTeacherCalibrationSignal().isNeedsTeacherReview()).isTrue();
        assertThat(result.analysis().getConfidence()).isLessThan(0.82);
        assertThat(result.analysis().getEvidenceRefs()).contains("memory:teacher_calibration:brute_force_limit");
        assertThat(result.analysis().getUncertainty()).contains("教师校准提示");
        assertThat(result.analysis().getTeacherNote()).contains("教师校准冲突");
        assertThat(result.analysis().getIssueTags()).contains("LOOP_BOUNDARY");
        assertThat(result.analysis().getFineGrainedTags()).contains("OFF_BY_ONE");
    }

    @Test
    void diagnoseAlignsGenericModelTeachingActionToConcreteFineGrainedTag() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        DiagnosticAgentService service = new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new GenericComplexityAiReportService(),
                new PassThroughHintSafetyService(taxonomy),
                taxonomy
        );
        Submission submission = Submission.builder()
                .id(12L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.TIME_LIMIT_EXCEEDED)
                .sourceCode("""
                        start, steps = map(int, input().split())
                        used = 0
                        while used < steps:
                            start += 1
                            used += 1
                        print(start)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(2L)
                .title("机器人向右走")
                .description("输入起点 start 和步数 k，k 最大可达到 10^9，不能按每一步逐次模拟。")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(12L)
                .sourceType("RULE_BASED_V1")
                .scenario("TLE")
                .issueTags(List.of("TIME_COMPLEXITY"))
                .fineGrainedTags(List.of("OVER_SIMULATION"))
                .evidenceRefs(List.of("problem:large_bound_step_simulation"))
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2
        );

        assertThat(result.analysis().getFineGrainedTags()).contains("OVER_SIMULATION");
        assertThat(result.analysis().getStudentHintPlan().getTeachingAction()).isEqualTo("COUNT_COMPLEXITY");
        assertThat(result.analysis().getStudentHintPlan().getNextAction()).contains("最大输入");
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("COMPLEXITY_ESTIMATE");
    }

    @Test
    void modelSuccessKeepsStableContextEvidenceRefs() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        DiagnosticAgentService service = new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new ModelOnlyEvidenceAiReportService(),
                new PassThroughHintSafetyService(taxonomy),
                taxonomy
        );
        Submission submission = Submission.builder()
                .id(20L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        dp = [0] * n
                        for i in range(1, n):
                            dp[i] = max(dp[i - 1], i)
                        print(dp[-1])
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(3L)
                .title("不相邻选择最大和")
                .description("需要设计状态表达选择约束和不同来源。")
                .difficulty(Problem.Difficulty.MEDIUM)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(20L)
                .sourceType("FIXTURE_BASELINE")
                .scenario("WA")
                .issueTags(List.of("ALGORITHM_STRATEGY"))
                .fineGrainedTags(List.of("DP_STATE_DESIGN"))
                .evidenceRefs(List.of("problem:dp_state_needs_design"))
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(SubmissionCaseResult.builder()
                        .testCaseNumber(1)
                        .passed(false)
                        .hidden(false)
                        .inputSnapshot("5\n2 7 9 3 1")
                        .actualOutput("9")
                        .expectedOutput("12")
                        .build()),
                baseline,
                Assignment.HintPolicy.L2
        );

        assertThat(result.analysis().getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(result.analysis().getAiInvocation().getStandardLibraryNavigationStatus()).isEqualTo("SUCCESS");
        assertThat(result.analysis().getAiInvocation().getStandardLibraryNavigationSelectedCount()).isEqualTo(2);
        assertThat(result.analysis().getAiInvocation().getAdviceGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(result.analysis().getAiInvocation().getBasicAdviceCount()).isEqualTo(1);
        assertThat(result.analysis().getAiInvocation().getImprovementAdviceCount()).isEqualTo(1);
        assertThat(result.analysis().getAiInvocation().getAdvicePromptVersion()).isEqualTo("diagnosis-and-advice-v1");
        assertThat(result.analysis().getIssueTags()).contains("ALGORITHM_STRATEGY");
        assertThat(result.analysis().getFineGrainedTags()).contains("DP_STATE_DESIGN");
        assertThat(result.analysis().getEvidenceRefs()).contains(
                "model:dp_state_decision",
                "verdict:wrong_answer",
                "judge:first_failed_case:1"
        );
        assertThat(result.analysis().getEvidenceRefs()).doesNotContain("problem:dp_state_needs_design");
        assertThat(result.analysis().getStudentHintPlan().getEvidenceRefs()).contains("verdict:wrong_answer");
    }

    @Test
    void diagnoseAddsPartialFixRegressionFromHistoryTransition() {
        DiagnosticAgentService service = newService();

        Submission submission = Submission.builder()
                .id(11L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.RUNTIME_ERROR)
                .sourceCode("print(1 / 0)")
                .build();
        Problem problem = Problem.builder()
                .id(1L)
                .title("调试题")
                .description("调试")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(11L)
                .sourceType("RULE_BASED_V1")
                .scenario("RE")
                .issueTags(List.of("RUNTIME_STABILITY"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("WRONG_ANSWER")
                .transitionSignal("评测阶段从 WRONG_ANSWER 变化为 RUNTIME_ERROR")
                .recentIssueTags(List.of("BOUNDARY_CONDITION"))
                .recentFineGrainedTags(List.of("OFF_BY_ONE"))
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                history
        );

        assertThat(result.analysis().getFineGrainedTags()).doesNotContain("PARTIAL_FIX_REGRESSION");
        assertThat(result.analysis().getEvidenceRefs()).doesNotContain("history:verdict_transition");
        assertThat(result.analysis().getLearningTrajectorySignal()).isNotNull();
        assertThat(result.analysis().getLearningTrajectorySignal().getPhase()).isEqualTo("REGRESSION");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("COMPARE_SUBMISSIONS");
    }

    @Test
    void diagnoseBuildsFixedCompilationTrajectorySignal() {
        DiagnosticAgentService service = newService();

        Submission submission = Submission.builder()
                .id(14L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        print(n + 1)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(4L)
                .title("trajectory")
                .description("Read an integer and output the expected value.")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(14L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("COMPILATION_ERROR")
                .transitionSignal("compilation fixed, now wrong answer")
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                history
        );

        assertThat(result.analysis().getLearningTrajectorySignal()).isNotNull();
        assertThat(result.analysis().getLearningTrajectorySignal().getPhase()).isEqualTo("FIXED_COMPILATION");
        assertThat(result.analysis().getLearningTrajectorySignal().isNeedsTeacherAttention()).isFalse();
        assertThat(result.analysis().getEvidenceRefs()).contains("history:fixed_compilation");
        assertThat(result.traceSummary()).contains("trajectory=FIXED_COMPILATION");
    }

    @Test
    void diagnoseBuildsRepeatedStuckTrajectorySignal() {
        DiagnosticAgentService service = newService();

        Submission submission = Submission.builder()
                .id(15L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        print(n - 1)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(5L)
                .title("repeated")
                .description("Read an integer and output the expected value.")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(15L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("WRONG_ANSWER")
                .repeatedIssueCount(4L)
                .transitionSignal("same verdict remains")
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                history
        );

        assertThat(result.analysis().getLearningTrajectorySignal()).isNotNull();
        assertThat(result.analysis().getLearningTrajectorySignal().getPhase()).isEqualTo("REPEATED_STUCK");
        assertThat(result.analysis().getLearningTrajectorySignal().isNeedsTeacherAttention()).isTrue();
        assertThat(result.analysis().getEvidenceRefs()).contains("history:repeated_stuck");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("MIN_CASE_TRACE");
        assertThat(result.analysis().getLearningInterventionPlan().getStudentTask()).isNotBlank();
        assertThat(result.analysis().getLearningInterventionPlan().getCompletionSignal()).isNotBlank();
    }

    @Test
    void diagnoseBuildsAcceptedAfterFixTrajectorySignal() {
        DiagnosticAgentService service = newService();

        Submission submission = Submission.builder()
                .id(16L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.ACCEPTED)
                .sourceCode("print(int(input()))")
                .build();
        Problem problem = Problem.builder()
                .id(6L)
                .title("accepted")
                .description("Read an integer and output the expected value.")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(16L)
                .sourceType("RULE_BASED_V1")
                .scenario("AC")
                .issueTags(List.of("GENERALIZATION_CHECK"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("WRONG_ANSWER")
                .transitionSignal("wrong answer fixed to accepted")
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                history
        );

        assertThat(result.analysis().getLearningTrajectorySignal()).isNotNull();
        assertThat(result.analysis().getLearningTrajectorySignal().getPhase()).isEqualTo("ACCEPTED_AFTER_FIX");
        assertThat(result.analysis().getLearningTrajectorySignal().isNeedsTeacherAttention()).isFalse();
        assertThat(result.analysis().getEvidenceRefs()).contains("history:accepted_after_fix");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("EXPLAIN_GENERALITY");
    }

    @Test
    void diagnoseReturnsExplicitFailureWhenModelStageFails() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        DiagnosticAgentService service = new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new FailingAiReportService(),
                new PassThroughHintSafetyService(taxonomy),
                taxonomy
        );
        Submission submission = Submission.builder()
                .id(12L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        for i in range(n - 1):
                            print(i)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(2L)
                .title("循环边界")
                .description("输出 1 到 n")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(12L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2
        );

        assertThat(result.analysis().getAiInvocation().getStatus()).isEqualTo("MODEL_FAILED");
        assertThat(result.analysis().getAiInvocation().isFallbackUsed()).isFalse();
        assertThat(result.analysis().getHeadline()).contains("AI 诊断暂不可用");
        assertThat(result.analysis().getIssueTags()).isEmpty();
        assertThat(result.analysis().getIssueTags()).doesNotContain("LOOP_BOUNDARY");
        assertThat(result.analysis().getFineGrainedTags()).doesNotContain("OFF_BY_ONE");
        assertThat(result.analysis().getDiagnosticTrace()).contains("model=failed");
        assertThat(result.traceSummary()).contains("model=failed");
    }

    @Test
    void diagnoseMarksLowConfidenceAsNeedsMoreEvidence() {
        DiagnosticAgentService service = newService();
        Submission submission = Submission.builder()
                .id(13L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.PENDING)
                .sourceCode("print(1)")
                .build();
        Problem problem = Problem.builder()
                .id(3L)
                .title("待观察")
                .description("待观察")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(13L)
                .sourceType("RULE_BASED_V1")
                .scenario("UNKNOWN")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of())
                .confidence(0.45)
                .uncertainty("")
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L1
        );

        assertThat(result.analysis().getIssueTags()).contains("NEEDS_MORE_EVIDENCE");
        assertThat(result.analysis().getEvidenceRefs()).contains("agent:low_confidence");
        assertThat(result.analysis().getUncertainty()).contains("置信度较低");
        assertThat(result.analysis().getStudentHintPlan().getProblemType()).isEqualTo("证据不足");
        assertThat(result.analysis().getStudentHintPlan().getTeachingAction()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("COLLECT_EVIDENCE");
    }

    @Test
    void diagnoseShrinksInterventionWhenPreviousLearningActionWasContradicted() {
        DiagnosticAgentService service = newService();
        Submission submission = Submission.builder()
                .id(17L)
                .languageName("Python 3")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .sourceCode("""
                        n = int(input())
                        total = 0
                        for i in range(n - 1):
                            total += i
                        print(total)
                        """)
                .build();
        Problem problem = Problem.builder()
                .id(7L)
                .title("action feedback")
                .description("Input n and output the expected value.")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .submissionId(17L)
                .sourceType("RULE_BASED_V1")
                .scenario("WA")
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .fineGrainedTags(List.of())
                .evidenceRefs(List.of("code:plus_minus_one"))
                .build();
        DiagnosisEvidencePackage.HistoryEvidence history = DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousVerdict("WRONG_ANSWER")
                .previousInterventionType("MIN_CASE_TRACE")
                .previousLearningActionStatus("CONTRADICTED")
                .previousLearningActionConfidence(0.74)
                .previousLearningActionEvidenceRefs(List.of("eval:intervention", "followup:submission:17", "action:CONTRADICTED"))
                .previousLearningActionSummary("The same issue remained after the previous action.")
                .previousLearningActionNextAdjustment("Shrink the next task.")
                .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                problem,
                submission,
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                history
        );

        assertThat(result.analysis().getLearningActionEvidence()).isNotNull();
        assertThat(result.analysis().getLearningActionEvidence().getExecutionStatus()).isEqualTo("CONTRADICTED");
        assertThat(result.analysis().getLearningActionEvidence().getEvidenceRefs()).contains("action:CONTRADICTED");
        assertThat(result.analysis().getEvidenceRefs()).contains("eval:intervention", "followup:submission:17");
        assertThat(result.analysis().getLearningInterventionPlan()).isNotNull();
        assertThat(result.analysis().getLearningInterventionPlan().getInterventionType()).isEqualTo("MIN_CASE_TRACE");
        assertThat(result.analysis().getLearningInterventionPlan().getEvidenceRefs()).contains("action:CONTRADICTED");
    }

    private DiagnosticAgentService newService() {
        DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();
        return new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new PassThroughAiReportService(),
                new PassThroughHintSafetyService(taxonomy),
                taxonomy
        );
    }

    private static class PassThroughAiReportService extends AiReportService {
        protected PassThroughAiReportService() {
            super(new ObjectMapper(), new AiCodeAssistSupport());
        }

        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            return asModelResponse(fallback);
        }
    }

    private static class PassThroughHintSafetyService extends HintSafetyService {
        private PassThroughHintSafetyService(DiagnosisTaxonomy taxonomy) {
            super(null, new ObjectMapper(), taxonomy);
        }

        @Override
        public SubmissionAnalysisResponse verifyAndRecord(SubmissionAnalysisResponse analysis,
                                                          Assignment.HintPolicy hintPolicy) {
            return analysis;
        }
    }

    private static class FailingAiReportService extends PassThroughAiReportService {
        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            throw new IllegalStateException("model stage unavailable");
        }
    }

    private static class GenericComplexityAiReportService extends PassThroughAiReportService {
        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            fallback.setIssueTags(List.of("TIME_COMPLEXITY"));
            fallback.setFineGrainedTags(List.of("OVER_SIMULATION"));
            fallback.setStudentHint("请根据诊断标签补充一个最小验证。");
            fallback.setStudentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                    .hintLevel("L2")
                    .problemType("复杂度")
                    .evidenceAnchor("problem:large_bound_step_simulation")
                    .nextAction("构造一个最小样例。")
                    .coachQuestion("你准备验证什么？")
                    .teachingAction("COLLECT_EVIDENCE")
                    .evidenceRefs(List.of("problem:large_bound_step_simulation"))
                    .answerLeakRisk("LOW")
                    .build());
            fallback.setLearningInterventionPlan(SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                    .interventionType("COLLECT_EVIDENCE")
                    .goal("确认问题。")
                    .studentTask("补充证据。")
                    .checkQuestion("证据是什么？")
                    .completionSignal("能说出证据。")
                    .evidenceRefs(List.of("problem:large_bound_step_simulation"))
                    .estimatedMinutes(5)
                    .answerLeakRisk("LOW")
                    .build());
            return asModelResponse(fallback);
        }
    }

    private static SubmissionAnalysisResponse asModelResponse(SubmissionAnalysisResponse source) {
        if (source == null) {
            return null;
        }
        return SubmissionAnalysisResponse.builder()
                .analysisSchemaVersion(source.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(source.getEvidenceSchemaVersion())
                .taxonomyVersion(source.getTaxonomyVersion())
                .submissionId(source.getSubmissionId())
                .sourceType("AI_MODEL")
                .scenario(source.getScenario())
                .headline(source.getHeadline())
                .summary(source.getSummary())
                .issueTags(source.getIssueTags())
                .fineGrainedTags(source.getFineGrainedTags())
                .abilityPoints(source.getAbilityPoints())
                .focusPoints(source.getFocusPoints())
                .fixDirections(source.getFixDirections())
                .evidenceRefs(source.getEvidenceRefs())
                .studentHint(source.getStudentHint())
                .studentHintPlan(source.getStudentHintPlan())
                .studentFeedback(source.getStudentFeedback())
                .studentFeedbackView(source.getStudentFeedbackView())
                .learningInterventionPlan(source.getLearningInterventionPlan())
                .teacherNote(source.getTeacherNote())
                .progressSignal(source.getProgressSignal())
                .learningTrajectorySignal(source.getLearningTrajectorySignal())
                .confidence(source.getConfidence())
                .learningActionEvidence(source.getLearningActionEvidence())
                .teacherCalibrationSignal(source.getTeacherCalibrationSignal())
                .uncertainty(source.getUncertainty())
                .diagnosticTrace(source.getDiagnosticTrace())
                .modelEducationTrace(source.getModelEducationTrace())
                .caseUnderstanding(source.getCaseUnderstanding())
                .basicLayerAdvice(source.getBasicLayerAdvice())
                .improvementLayerAdvice(source.getImprovementLayerAdvice())
                .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                        .provider("MODEL_SCOPE")
                        .model("deepseek-ai/DeepSeek-V4-Pro")
                        .promptVersion(PromptTemplateRegistry.DIAGNOSIS_REPORT_V2)
                        .status("MODEL_COMPLETED")
                        .fallbackUsed(false)
                        .runtimeMode("diagnosis-report")
                        .runtimeProfile(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD)
                        .requestCompact(false)
                        .build())
                .answerLeakRisk(source.getAnswerLeakRisk())
                .wrongSolution(source.getWrongSolution())
                .correctSolution(source.getCorrectSolution())
                .lineIssues(source.getLineIssues())
                .firstFailedCase(source.getFirstFailedCase())
                .reportMarkdown(source.getReportMarkdown())
                .generatedAt(source.getGeneratedAt())
                .build();
    }

    private static class ModelOnlyEvidenceAiReportService extends PassThroughAiReportService {
        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            return SubmissionAnalysisResponse.builder()
                    .analysisSchemaVersion("diagnosis-v1")
                    .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                    .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                    .submissionId(submission.getId())
                    .sourceType("AI_MODEL")
                    .scenario("WA")
                    .headline("DP 状态来源不足")
                    .summary("模型选择了 DP 状态设计问题。")
                    .issueTags(List.of("ALGORITHM_STRATEGY"))
                    .fineGrainedTags(List.of("DP_STATE_DESIGN"))
                    .evidenceRefs(List.of("model:dp_state_decision"))
                    .studentHint("先说明 dp[i] 表示什么。")
                    .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                            .hintLevel("L2")
                            .problemType("动态规划状态设计")
                            .evidenceAnchor("model:dp_state_decision")
                            .nextAction("手推一个最小样例。")
                            .coachQuestion("这个状态包含选择当前值的来源吗？")
                            .teachingAction("DEFINE_STATE")
                            .evidenceRefs(List.of("model:dp_state_decision"))
                            .answerLeakRisk("LOW")
                            .build())
                    .learningInterventionPlan(SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                            .interventionType("STATE_DEFINITION")
                            .goal("确认状态语义。")
                            .studentTask("写出 dp[i] 的含义。")
                            .checkQuestion("状态是否覆盖不相邻约束？")
                            .completionSignal("能说清状态来源。")
                            .evidenceRefs(List.of("model:dp_state_decision"))
                            .estimatedMinutes(6)
                            .answerLeakRisk("LOW")
                            .build())
                    .confidence(0.83)
                    .uncertainty("")
                    .aiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                            .provider("MODEL_SCOPE")
                            .model("deepseek-ai/DeepSeek-V4-Pro")
                            .promptVersion("diagnosis-and-advice-v1")
                            .status("MODEL_COMPLETED")
                            .fallbackUsed(false)
                            .runtimeMode("single-call")
                            .standardLibraryNavigationEnabled(true)
                            .standardLibraryNavigationStatus("SUCCESS")
                            .standardLibraryNavigationSelectedCount(2)
                            .adviceGenerationStatus("SUCCESS")
                            .basicAdviceCount(1)
                            .improvementAdviceCount(1)
                            .advicePromptVersion("diagnosis-and-advice-v1")
                            .build())
                    .answerLeakRisk("LOW")
                    .build();
        }
    }
}
