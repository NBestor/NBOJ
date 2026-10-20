package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassTeachingStrategyAnalyzerTest {

    private final ClassTeachingStrategyAnalyzer analyzer = new ClassTeachingStrategyAnalyzer();

    @Test
    void wholeClassMiniLessonWinsWhenManyStudentsShareIssue() {
        var signal = analyzer.analyze(
                List.of(
                        student(1L, "一号", "OFF_BY_ONE", "循环与边界"),
                        student(2L, "二号", "OFF_BY_ONE", "循环与边界"),
                        student(3L, "三号", "INPUT_PARSING", "输入解析")
                ),
                List.of(AssignmentOverviewResponse.IssueStat.builder()
                        .label("OFF_BY_ONE")
                        .abilityPoint("循环与边界")
                        .count(3)
                        .affectedStudentCount(2)
                        .interventionSuggestion("画循环变量表。")
                        .build()),
                List.of(AssignmentOverviewResponse.AbilityStat.builder()
                        .abilityPoint("循环与边界")
                        .submissionCount(3)
                        .taskCount(1)
                        .evidenceTags(List.of("OFF_BY_ONE"))
                        .build()),
                List.of()
        );

        assertThat(signal.getStatus()).isEqualTo(ClassTeachingStrategyAnalyzer.STATUS_WHOLE_CLASS_MINI_LESSON);
        assertThat(signal.getStrategyType()).isEqualTo(ClassTeachingStrategyAnalyzer.TYPE_MINI_LESSON);
        assertThat(signal.getTeacherAction()).contains("循环变量表");
        assertThat(signal.getExitTicket()).contains("最小反例");
        assertThat(signal.getEvidenceRefs()).contains("submission:101");
        assertThat(signal.getGroups()).first()
                .satisfies(group -> {
                    assertThat(group.getGroupType()).isEqualTo("MINI_LESSON");
                    assertThat(group.getStudentProfileIds()).contains(1L, 2L);
                });
    }

    @Test
    void smallGroupReviewUsesStudentLevelTeachingActions() {
        var signal = analyzer.analyze(
                List.of(
                        studentWithDecision(1L, "一号", "循环与边界", TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW),
                        studentWithDecision(2L, "二号", "循环与边界", TeachingActionOrchestrator.ACTION_TEACHER_REVIEW)
                ),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(signal.getStatus()).isEqualTo(ClassTeachingStrategyAnalyzer.STATUS_SMALL_GROUP_REVIEW);
        assertThat(signal.getGroups()).first()
                .satisfies(group -> {
                    assertThat(group.getGroupType()).isEqualTo("SMALL_GROUP");
                    assertThat(group.getStudentNames()).contains("一号", "二号");
                });
        assertThat(signal.getSourceSignals()).contains("teaching_action:SPIRAL_REVIEW", "teaching_action:TEACHER_REVIEW");
    }

    @Test
    void differentiatedSupportHandlesAiDependencyAndSelfExplanationGap() {
        var signal = analyzer.analyze(
                List.of(
                        studentWithAiDependency(1L, "一号"),
                        studentWithSelfExplanationGap(2L, "二号")
                ),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(signal.getStatus()).isEqualTo(ClassTeachingStrategyAnalyzer.STATUS_DIFFERENTIATED_SUPPORT);
        assertThat(signal.getTeacherAction()).contains("独立尝试组");
        assertThat(signal.getExitTicket()).contains("无新增提示");
        assertThat(signal.getSourceSignals()).contains("ai_dependency:DEPENDENCY_RISK", "self_explanation:NEEDS_COACHING");
    }

    @Test
    void noSignalAvoidsMisleadingClassStrategy() {
        var signal = analyzer.analyze(List.of(), List.of(), List.of(), List.of());

        assertThat(signal.getStatus()).isEqualTo(ClassTeachingStrategyAnalyzer.STATUS_NO_SIGNAL);
        assertThat(signal.getAffectedStudentCount()).isZero();
        assertThat(signal.getGroups()).isEmpty();
        assertThat(analyzer.isActionable(signal)).isFalse();
    }

    @Test
    void strategyKeyIsStableForAssignmentStatusAndFocus() {
        var signal = analyzer.analyze(
                117L,
                List.of(
                        student(1L, "一号", "OFF_BY_ONE", "循环与边界"),
                        student(2L, "二号", "OFF_BY_ONE", "循环与边界")
                ),
                List.of(AssignmentOverviewResponse.IssueStat.builder()
                        .label("OFF_BY_ONE")
                        .abilityPoint("循环与边界")
                        .count(2)
                        .affectedStudentCount(2)
                        .build()),
                List.of(),
                List.of()
        );

        assertThat(signal.getStrategyKey()).isEqualTo("strategy:117:whole-class-mini-lesson:off-by-one");
        assertThat(signal.getSourceSignals()).contains("class_issue:OFF_BY_ONE");
    }

    private AssignmentOverviewResponse.StudentProgressSummary student(Long id,
                                                                       String name,
                                                                       String fineTag,
                                                                       String ability) {
        return AssignmentOverviewResponse.StudentProgressSummary.builder()
                .studentProfileId(id)
                .displayName(name)
                .latestSubmissionId(100L + id)
                .latestFineGrainedIssue(fineTag)
                .primaryAbilityFocus(ability)
                .abilitySummary(List.of(AssignmentOverviewResponse.AbilityStat.builder()
                        .abilityPoint(ability)
                        .submissionCount(1)
                        .taskCount(1)
                        .evidenceTags(List.of(fineTag))
                        .build()))
                .attentionEvidence(List.of())
                .needsAttention(false)
                .build();
    }

    private AssignmentOverviewResponse.StudentProgressSummary studentWithDecision(Long id,
                                                                                   String name,
                                                                                   String ability,
                                                                                   String actionType) {
        return AssignmentOverviewResponse.StudentProgressSummary.builder()
                .studentProfileId(id)
                .displayName(name)
                .latestSubmissionId(200L + id)
                .primaryAbilityFocus(ability)
                .teachingActionDecision(StudentAbilityProfileResponse.TeachingActionDecision.builder()
                        .actionType(actionType)
                        .riskLevel(TeachingActionOrchestrator.RISK_HIGH)
                        .needsTeacherAttention(true)
                        .evidenceRefs(List.of("teaching_action:" + id))
                        .sourceSignals(List.of("teaching_action:" + actionType))
                        .build())
                .needsAttention(true)
                .build();
    }

    private AssignmentOverviewResponse.StudentProgressSummary studentWithAiDependency(Long id, String name) {
        return AssignmentOverviewResponse.StudentProgressSummary.builder()
                .studentProfileId(id)
                .displayName(name)
                .latestSubmissionId(300L + id)
                .aiDependencySignal(StudentAbilityProfileResponse.AiDependencySignal.builder()
                        .status(AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK)
                        .dependencyEvidenceRefs(List.of("ai_dependency:" + id))
                        .build())
                .needsAttention(false)
                .build();
    }

    private AssignmentOverviewResponse.StudentProgressSummary studentWithSelfExplanationGap(Long id, String name) {
        return AssignmentOverviewResponse.StudentProgressSummary.builder()
                .studentProfileId(id)
                .displayName(name)
                .latestSubmissionId(400L + id)
                .selfExplanationMasterySignal(StudentAbilityProfileResponse.SelfExplanationMasterySignal.builder()
                        .status(SelfExplanationMasteryAnalyzer.STATUS_NEEDS_COACHING)
                        .evidenceRefs(List.of("self_explanation:" + id))
                        .build())
                .needsAttention(false)
                .build();
    }
}
