package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingActionOrchestratorTest {

    private final TeachingActionOrchestrator orchestrator = new TeachingActionOrchestrator();

    @Test
    void teacherReviewOutranksDependencyAndSelfExplanationPractice() {
        var decision = orchestrator.decide(
                null,
                null,
                null,
                StudentAbilityProfileResponse.RecurringMisconceptionSignal.builder()
                        .status(RecurringMisconceptionAnalyzer.STATUS_ESCALATE)
                        .summary("跨作业复发")
                        .recommendedAction("找老师对比两题")
                        .evidenceRefs(List.of("recurring-misconception:submission:1"))
                        .build(),
                StudentAbilityProfileResponse.SelfExplanationMasterySignal.builder()
                        .status(SelfExplanationMasteryAnalyzer.STATUS_NEEDS_COACHING)
                        .summary("解释证据不足")
                        .recommendedAction("补最小样例")
                        .evidenceRefs(List.of("self-explanation:coach-prompt:1"))
                        .build(),
                StudentAbilityProfileResponse.AiDependencySignal.builder()
                        .status(AiDependencyAnalyzer.STATUS_SCAFFOLD_DENSE)
                        .summary("支架过密")
                        .recommendedAction("独立尝试")
                        .dependencyEvidenceRefs(List.of("ai_dependency:SCAFFOLD_DENSE"))
                        .build(),
                null,
                "继续诊断"
        );

        assertThat(decision.getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_TEACHER_REVIEW);
        assertThat(decision.getActor()).isEqualTo(TeachingActionOrchestrator.ACTOR_TEACHER);
        assertThat(decision.getPriority()).isEqualTo(10);
        assertThat(decision.getSourceSignals()).contains("recurring_misconception:ESCALATE");
        assertThat(decision.getCandidateCount()).isEqualTo(3);
        assertThat(decision.isNeedsTeacherAttention()).isTrue();
    }

    @Test
    void masterySpiralReviewOutranksIndependentAttempt() {
        var decision = orchestrator.decide(
                null,
                null,
                null,
                null,
                null,
                StudentAbilityProfileResponse.AiDependencySignal.builder()
                        .status(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK)
                        .summary("支架后仍未改善")
                        .recommendedAction("独立尝试")
                        .dependencyEvidenceRefs(List.of("ai_dependency:DEPENDENCY_RISK"))
                        .build(),
                StudentAbilityProfileResponse.MasteryGrowthSignal.builder()
                        .status(MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED)
                        .summary("跨题停滞")
                        .recommendedAction("螺旋复习")
                        .evidenceRefs(List.of("mastery_growth:SPIRAL_REVIEW_NEEDED"))
                        .build(),
                "继续诊断"
        );

        assertThat(decision.getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW);
        assertThat(decision.getActor()).isEqualTo(TeachingActionOrchestrator.ACTOR_TEACHER);
        assertThat(decision.getRiskLevel()).isEqualTo(TeachingActionOrchestrator.RISK_HIGH);
        assertThat(decision.getCandidateCount()).isEqualTo(2);
    }

    @Test
    void dependencyRiskBecomesIndependentAttempt() {
        var decision = orchestrator.decide(
                null,
                null,
                null,
                null,
                null,
                StudentAbilityProfileResponse.AiDependencySignal.builder()
                        .status(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK)
                        .summary("支架后仍未改善")
                        .recommendedAction("独立尝试")
                        .dependencyEvidenceRefs(List.of("recommendation_event:1"))
                        .build(),
                null,
                "继续诊断"
        );

        assertThat(decision.getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_INDEPENDENT_ATTEMPT);
        assertThat(decision.getActor()).isEqualTo(TeachingActionOrchestrator.ACTOR_STUDENT);
        assertThat(decision.getRiskLevel()).isEqualTo(TeachingActionOrchestrator.RISK_MEDIUM);
    }

    @Test
    void postAcPendingBecomesReflectionAction() {
        var decision = orchestrator.decide(
                null,
                null,
                StudentTrajectoryResponse.PostAcTransferSignal.builder()
                        .phase(PostAcTransferAnalyzer.PHASE_REFLECTION_NEEDED)
                        .summary("通过后缺复盘")
                        .recommendedAction("补复盘")
                        .evidenceRefs(List.of("post_ac_transfer:problem:101"))
                        .build(),
                null,
                null,
                null,
                null,
                "继续诊断"
        );

        assertThat(decision.getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_POST_AC_REFLECTION);
        assertThat(decision.getRecommendedAction()).isEqualTo("补复盘");
        assertThat(decision.getSourceSignals()).contains("post_ac_transfer:REFLECTION_NEEDED");
    }

    @Test
    void missingRiskFallsBackToContinueDiagnosis() {
        var decision = orchestrator.decide(null, null, null, null, null, null, null, "继续按最新诊断推进");

        assertThat(decision.getActionType()).isEqualTo(TeachingActionOrchestrator.ACTION_CONTINUE_DIAGNOSIS);
        assertThat(decision.getActor()).isEqualTo(TeachingActionOrchestrator.ACTOR_AI_COACH);
        assertThat(decision.getRiskLevel()).isEqualTo(TeachingActionOrchestrator.RISK_LOW);
        assertThat(decision.isNeedsTeacherAttention()).isFalse();
        assertThat(orchestrator.isActionable(decision)).isFalse();
    }
}
