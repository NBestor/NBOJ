package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoachAnswerQualityAnalyzerTest {

    private final CoachAnswerQualityAnalyzer analyzer = new CoachAnswerQualityAnalyzer();

    @Test
    void classifiesEmptyAnswerAsNoAnswer() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze("");

        assertThat(signal.getQualityLevel()).isEqualTo("NO_ANSWER");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("NONE");
        assertThat(signal.getActionStatus()).isEqualTo("NOT_ANSWERED");
        assertThat(signal.getEvidenceCompleteness()).isEqualTo(0.0);
        assertThat(signal.getVerifiable()).isFalse();
        assertThat(signal.getMissingEvidence()).isNotEmpty();
    }

    @Test
    void classifiesVagueAcknowledgement() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze("我知道了，我改一下");

        assertThat(signal.getQualityLevel()).isEqualTo("VAGUE_ACK");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("NONE");
        assertThat(signal.getActionStatus()).isEqualTo("NEEDS_EVIDENCE");
        assertThat(signal.getVerifiable()).isFalse();
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
        assertThat(signal.getNextCoachMove()).contains("最小证据");
    }

    @Test
    void classifiesDirectionWithoutEvidence() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze("应该是循环边界的问题");

        assertThat(signal.getQualityLevel()).isEqualTo("DIRECTION_ONLY");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("DIRECTION");
        assertThat(signal.getActionStatus()).isEqualTo("NEEDS_EVIDENCE");
        assertThat(signal.getMissingEvidence()).contains("方向还没有落到可检查证据。");
    }

    @Test
    void detectsMinimalCaseAndVariableTraceEvidence() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze(
                "n=3 时循环变量第一次是 1，最后一次只到 2，实际输出比预期少了 3"
        );

        assertThat(signal.getQualityLevel()).isEqualTo("VERIFICATION_READY");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("VERIFICATION");
        assertThat(signal.getActionStatus()).isEqualTo("READY_TO_VERIFY");
        assertThat(signal.getVerifiable()).isTrue();
        assertThat(signal.getEvidenceCompleteness()).isEqualTo(1.0);
        assertThat(signal.getEvidenceTypes()).contains("MIN_CASE", "VARIABLE_TRACE", "EXPECTED_ACTUAL_COMPARE");
    }

    @Test
    void detectsComplexityEstimateEvidence() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze(
                "最大 n=200000 时双重循环次数大约是 n*n，会超时"
        );

        assertThat(signal.getQualityLevel()).isEqualTo("VERIFICATION_READY");
        assertThat(signal.getRecommendedTeachingAction()).isEqualTo("COUNT_COMPLEXITY");
        assertThat(signal.getEvidenceTypes()).contains("MIN_CASE", "COMPLEXITY_ESTIMATE");
    }

    @Test
    void detectsCounterexampleEvidence() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze(
                "我构造了一个反例，重复元素会破坏这个贪心规则"
        );

        assertThat(signal.getQualityLevel()).isEqualTo("EVIDENCE_GROUNDED");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("EVIDENCE");
        assertThat(signal.getEvidenceCompleteness()).isEqualTo(0.45);
        assertThat(signal.getEvidenceTypes()).contains("COUNTEREXAMPLE");
    }

    @Test
    void classifiesTransferReadyAnswer() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze(
                "这题通过后我能解释不变量，也能说明边界样例为什么能处理，复杂度是 O(n)"
        );

        assertThat(signal.getQualityLevel()).isEqualTo("TRANSFER_READY");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("TRANSFER");
        assertThat(signal.getActionStatus()).isEqualTo("READY_TO_TRANSFER");
        assertThat(signal.getRecommendedTeachingAction()).isEqualTo("EXPLAIN_GENERALITY");
        assertThat(signal.getEvidenceTypes()).contains("GENERALIZATION", "COMPLEXITY_ESTIMATE");
    }

    @Test
    void flagsAnswerLikeContentAsSafetyRisk() {
        CoachInteractionSummaryResponse.CoachAnswerQualitySignal signal = analyzer.analyze(
                "答案如下：直接改成 for _ in range(q) 就可以"
        );

        assertThat(signal.getQualityLevel()).isEqualTo("SAFETY_RISK");
        assertThat(signal.getUnderstandingLevel()).isEqualTo("RISKY");
        assertThat(signal.getActionStatus()).isEqualTo("SAFETY_RISK");
        assertThat(signal.getRecommendedTeachingAction()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(signal.getVerifiable()).isFalse();
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
    }
}
