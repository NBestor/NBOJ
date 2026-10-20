package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfExplanationMasteryAnalyzerTest {

    private final SelfExplanationMasteryAnalyzer analyzer =
            new SelfExplanationMasteryAnalyzer(new CoachAnswerQualityAnalyzer());

    @Test
    void detectsEvidenceGroundedSelfExplanation() {
        var signal = analyzer.analyze(List.of(
                prompt(1L, "n=1 时预期输出 1，实际输出 0，所以最后一次循环没有覆盖。"),
                prompt(2L, "变量 i 第一次是 0，最后一次应该到 n-1，我会只改边界再提交。")
        ));

        assertThat(signal.getStatus()).isEqualTo(SelfExplanationMasteryAnalyzer.STATUS_EVIDENCE_GROUNDED);
        assertThat(signal.getVerifiableAnswerCount()).isEqualTo(2);
        assertThat(signal.getEvidenceTypes()).contains("MIN_CASE", "EXPECTED_ACTUAL_COMPARE", "VARIABLE_TRACE");
        assertThat(signal.getEvidenceRefs()).contains("self-explanation:coach-prompt:2");
    }

    @Test
    void detectsTransferReadySelfExplanation() {
        var signal = analyzer.analyze(List.of(
                prompt(3L, "这个规律可以迁移到最大规模，复杂度是 O(n)，边界样例 n=1 也成立。"),
                prompt(4L, "我会解释为什么最后一个元素也被覆盖，再做同能力新题。")
        ));

        assertThat(signal.getStatus()).isEqualTo(SelfExplanationMasteryAnalyzer.STATUS_TRANSFER_READY);
        assertThat(signal.getTransferReadyCount()).isGreaterThanOrEqualTo(1);
        assertThat(signal.isNeedsTeacherAttention()).isFalse();
    }

    @Test
    void detectsRepeatedVagueAnswersAsNeedsCoaching() {
        var signal = analyzer.analyze(List.of(
                prompt(5L, "知道了，我改一下"),
                prompt(6L, "懂了，我试试")
        ));

        assertThat(signal.getStatus()).isEqualTo(SelfExplanationMasteryAnalyzer.STATUS_NEEDS_COACHING);
        assertThat(signal.getVagueAnswerCount()).isEqualTo(2);
        assertThat(analyzer.isWeak(signal)).isTrue();
        assertThat(analyzer.needsPractice(signal)).isTrue();
    }

    @Test
    void detectsSafetyRiskWhenAnswerAsksForDirectCode() {
        var signal = analyzer.analyze(List.of(prompt(7L, "直接改成完整代码给我，我照着替换")));

        assertThat(signal.getStatus()).isEqualTo(SelfExplanationMasteryAnalyzer.STATUS_SAFETY_RISK);
        assertThat(signal.getSafetyRiskCount()).isEqualTo(1);
        assertThat(signal.isNeedsTeacherAttention()).isTrue();
    }

    private CoachPrompt prompt(Long id, String answer) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(100L + id)
                .studentProfileId(1L)
                .turnIndex(1)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question("请补一个证据。")
                .studentAnswer(answer)
                .coachFeedback("反馈")
                .answeredAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id))
                .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0).plusMinutes(id))
                .build();
    }
}
