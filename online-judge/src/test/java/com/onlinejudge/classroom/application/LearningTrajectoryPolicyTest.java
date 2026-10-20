package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningTrajectoryPolicyTest {

    private final LearningTrajectoryPolicy policy = new LearningTrajectoryPolicy();

    @Test
    void repeatedStuckUsesNextFocusAndTeacherAttentionSummary() {
        StudentTrajectoryResponse.LearningTrajectorySignal signal = StudentTrajectoryResponse.LearningTrajectorySignal.builder()
                .phase("REPEATED_STUCK")
                .summary("连续多次停留在同一类失败上，需要老师关注。")
                .nextFocus("只保留一个最小失败样例，写出关键变量再改代码。")
                .needsTeacherAttention(true)
                .build();

        assertThat(policy.nextStep(signal, "fallback"))
                .isEqualTo("只保留一个最小失败样例，写出关键变量再改代码。");
        assertThat(policy.attentionReason(signal, "fallback"))
                .isEqualTo("连续多次停留在同一类失败上，需要老师关注。");
        assertThat(policy.improvementSignal(signal, "fallback"))
                .isEqualTo("连续多次停留在同一类失败上，需要老师关注。");
    }

    @Test
    void regressionFallsBackToPhasePolicyWhenNextFocusIsBlank() {
        StudentTrajectoryResponse.LearningTrajectorySignal signal = StudentTrajectoryResponse.LearningTrajectorySignal.builder()
                .phase("REGRESSION")
                .needsTeacherAttention(true)
                .build();

        assertThat(policy.nextStep(signal, "fallback")).contains("最近两次提交");
        assertThat(policy.attentionReason(signal, "fallback")).contains("需要老师关注");
        assertThat(policy.improvementSignal(signal, "fallback")).isEqualTo("fallback");
    }

    @Test
    void acceptedReviewPromotesReflectionWithoutTeacherEscalation() {
        StudentTrajectoryResponse.LearningTrajectorySignal signal = StudentTrajectoryResponse.LearningTrajectorySignal.builder()
                .phase("ACCEPTED_AFTER_FIX")
                .summary("这次从失败推进到通过，适合复盘。")
                .needsTeacherAttention(false)
                .build();

        assertThat(policy.nextStep(signal, "fallback")).contains("复盘");
        assertThat(policy.attentionReason(signal, "fallback")).isEqualTo("这次从失败推进到通过，适合复盘。");
    }

    @Test
    void contradictedActionEvidenceLowersHintGranularity() {
        StudentTrajectoryResponse.LearningActionEvidence evidence = StudentTrajectoryResponse.LearningActionEvidence.builder()
                .executionStatus("CONTRADICTED")
                .observedEvidence("后续仍停留在相同错因，可能没有真正完成学习动作。")
                .build();

        assertThat(policy.nextStep(evidence, "fallback")).contains("可观察产出");
        assertThat(policy.attentionReason(evidence, "fallback")).contains("相同错因");
        assertThat(policy.improvementSignal(evidence, "fallback")).contains("相同错因");
    }
}
