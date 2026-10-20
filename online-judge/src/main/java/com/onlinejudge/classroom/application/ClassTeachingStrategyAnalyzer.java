package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.AssignmentOverviewResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class ClassTeachingStrategyAnalyzer {

    public static final String STATUS_NO_SIGNAL = "NO_SIGNAL";
    public static final String STATUS_WATCH = "WATCH";
    public static final String STATUS_SMALL_GROUP_REVIEW = "SMALL_GROUP_REVIEW";
    public static final String STATUS_WHOLE_CLASS_MINI_LESSON = "WHOLE_CLASS_MINI_LESSON";
    public static final String STATUS_DIFFERENTIATED_SUPPORT = "DIFFERENTIATED_SUPPORT";
    public static final String STATUS_CALIBRATION_REVIEW = "CALIBRATION_REVIEW";

    public static final String TYPE_MINI_LESSON = "MINI_LESSON";
    public static final String TYPE_SMALL_GROUP = "SMALL_GROUP";
    public static final String TYPE_DIFFERENTIATED = "DIFFERENTIATED_SUPPORT";
    public static final String TYPE_CALIBRATION = "CALIBRATION_REVIEW";
    public static final String TYPE_WATCH = "WATCH";

    public AssignmentOverviewResponse.ClassTeachingStrategySignal analyze(
            List<AssignmentOverviewResponse.StudentProgressSummary> students,
            List<AssignmentOverviewResponse.IssueStat> topIssues,
            List<AssignmentOverviewResponse.AbilityStat> classAbilityWeaknesses,
            List<AssignmentOverviewResponse.ClassReviewSuggestion> classReviewSuggestions) {
        return analyze(null, students, topIssues, classAbilityWeaknesses, classReviewSuggestions);
    }

    public AssignmentOverviewResponse.ClassTeachingStrategySignal analyze(
            Long assignmentId,
            List<AssignmentOverviewResponse.StudentProgressSummary> students,
            List<AssignmentOverviewResponse.IssueStat> topIssues,
            List<AssignmentOverviewResponse.AbilityStat> classAbilityWeaknesses,
            List<AssignmentOverviewResponse.ClassReviewSuggestion> classReviewSuggestions) {
        List<AssignmentOverviewResponse.StudentProgressSummary> safeStudents = safeList(students);
        List<AssignmentOverviewResponse.IssueStat> safeIssues = safeList(topIssues);
        List<AssignmentOverviewResponse.AbilityStat> safeAbilities = safeList(classAbilityWeaknesses);
        List<AssignmentOverviewResponse.ClassReviewSuggestion> safeSuggestions = safeList(classReviewSuggestions);
        if (safeStudents.isEmpty() && safeIssues.isEmpty() && safeAbilities.isEmpty()) {
            return signal(
                    assignmentId,
                    STATUS_NO_SIGNAL,
                    TYPE_WATCH,
                    "暂不生成课堂策略",
                    "还没有足够学生提交或诊断证据，暂不建议安排全班讲评。",
                    null,
                    null,
                    "证据不足",
                    0,
                    0,
                    90,
                    TeachingActionOrchestrator.RISK_LOW,
                    "继续收集至少一次可诊断提交。",
                    "等待学生形成一次提交或 Coach 回答后再判断。",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates = new ArrayList<>();
        addCalibrationReview(assignmentId, candidates, safeStudents);
        addWholeClassMiniLesson(assignmentId, candidates, safeStudents, safeIssues, safeAbilities, safeSuggestions);
        addSmallGroupReview(assignmentId, candidates, safeStudents);
        addDifferentiatedSupport(assignmentId, candidates, safeStudents);
        addWatch(assignmentId, candidates, safeStudents, safeIssues);
        return candidates.stream()
                .min(Comparator
                        .comparingInt((AssignmentOverviewResponse.ClassTeachingStrategySignal signal) ->
                                signal.getPriority() == null ? 99 : signal.getPriority())
                        .thenComparing(AssignmentOverviewResponse.ClassTeachingStrategySignal::getStatus,
                                Comparator.nullsLast(String::compareTo)))
                .orElseGet(() -> signal(
                        assignmentId,
                        STATUS_WATCH,
                        TYPE_WATCH,
                        "继续观察课堂证据",
                        "当前有零散风险，但还不足以安排明确的全班或小组策略。",
                        null,
                        null,
                        "观察",
                        Math.max(0, safeStudents.stream().filter(AssignmentOverviewResponse.StudentProgressSummary::isNeedsAttention).count()),
                        safeStudents.size(),
                        80,
                        TeachingActionOrchestrator.RISK_LOW,
                        "继续观察高频错因和学生下一次提交。",
                        "选 1 名学生口头说明最小失败样例，作为下一轮证据。",
                        List.of(),
                        List.of("class_strategy:watch"),
                        List.of()
                ));
    }

    public boolean isActionable(AssignmentOverviewResponse.ClassTeachingStrategySignal signal) {
        return signal != null
                && signal.getStatus() != null
                && !STATUS_NO_SIGNAL.equals(signal.getStatus())
                && !STATUS_WATCH.equals(signal.getStatus());
    }

    public boolean hasEvidence(AssignmentOverviewResponse.ClassTeachingStrategySignal signal) {
        return signal != null && signal.getEvidenceRefs() != null && !signal.getEvidenceRefs().isEmpty();
    }

    public boolean hasExitTicket(AssignmentOverviewResponse.ClassTeachingStrategySignal signal) {
        return signal != null && signal.getExitTicket() != null && !signal.getExitTicket().isBlank();
    }

    public boolean hasGroupPlan(AssignmentOverviewResponse.ClassTeachingStrategySignal signal) {
        return signal != null && signal.getGroups() != null && !signal.getGroups().isEmpty();
    }

    private void addCalibrationReview(Long assignmentId,
                                      List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates,
                                      List<AssignmentOverviewResponse.StudentProgressSummary> students) {
        List<AssignmentOverviewResponse.StudentProgressSummary> calibrationStudents = students.stream()
                .filter(student -> student.getLatestCorrection() != null || isLowConfidence(student))
                .limit(6)
                .toList();
        if (calibrationStudents.size() < 2) {
            return;
        }
        candidates.add(signal(
                assignmentId,
                STATUS_CALIBRATION_REVIEW,
                TYPE_CALIBRATION,
                "先做诊断校准复核",
                "有 " + calibrationStudents.size() + " 名学生存在教师校正或低置信诊断，建议先抽查证据再安排讲评。",
                firstNonBlank(calibrationStudents.get(0).getPrimaryAbilityFocus(), "诊断证据"),
                firstNonBlank(calibrationStudents.get(0).getLatestFineGrainedIssue(), calibrationStudents.get(0).getLatestIssueTag(), "NEEDS_MORE_EVIDENCE"),
                "诊断校准",
                calibrationStudents.size(),
                students.size(),
                10,
                TeachingActionOrchestrator.RISK_HIGH,
                "抽查这些学生的提交证据和教师校正记录，只确认错因边界，不直接给改法。",
                "让学生写出一个最小失败样例和对应变量现象，用于校准当前错因。",
                evidenceRefs(calibrationStudents),
                List.of("teacher_calibration:class_review", "diagnosis_confidence:low"),
                List.of(group(
                        "CALIBRATION",
                        "需复核诊断证据",
                        calibrationStudents,
                        "教师校正或低置信样本",
                        "先抽查证据包，再决定是否全班讲评。"
                ))
        ));
    }

    private void addWholeClassMiniLesson(Long assignmentId,
                                         List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates,
                                         List<AssignmentOverviewResponse.StudentProgressSummary> students,
                                         List<AssignmentOverviewResponse.IssueStat> topIssues,
                                         List<AssignmentOverviewResponse.AbilityStat> classAbilityWeaknesses,
                                         List<AssignmentOverviewResponse.ClassReviewSuggestion> classReviewSuggestions) {
        AssignmentOverviewResponse.IssueStat issue = topIssues.stream()
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparingLong((AssignmentOverviewResponse.IssueStat item) -> Math.max(item.getAffectedStudentCount(), item.getCount()))
                        .thenComparing(AssignmentOverviewResponse.IssueStat::getCount))
                .orElse(null);
        AssignmentOverviewResponse.AbilityStat ability = classAbilityWeaknesses.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        long affected = issue == null ? 0 : Math.max(issue.getAffectedStudentCount(), issue.getCount());
        if (!students.isEmpty()) {
            long matchedStudents = students.stream()
                    .filter(student -> matchesIssueOrAbility(student, issue, ability))
                    .count();
            affected = Math.max(affected, matchedStudents);
        }
        long threshold = students.size() >= 4 ? Math.max(2, Math.round(students.size() * 0.4)) : 2;
        if (affected < threshold && (issue == null || issue.getCount() < 2)) {
            return;
        }
        String focusTag = issue == null ? null : issue.getLabel();
        String focusAbility = firstNonBlank(
                issue == null ? null : issue.getAbilityPoint(),
                ability == null ? null : ability.getAbilityPoint(),
                "共性能力点"
        );
        AssignmentOverviewResponse.ClassReviewSuggestion suggestion = classReviewSuggestions.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        List<AssignmentOverviewResponse.StudentProgressSummary> matched = students.stream()
                .filter(student -> matchesIssueOrAbility(student, issue, ability))
                .limit(8)
                .toList();
        candidates.add(signal(
                assignmentId,
                STATUS_WHOLE_CLASS_MINI_LESSON,
                TYPE_MINI_LESSON,
                "全班小讲评：" + focusLabel(focusTag, focusAbility),
                "班级集中暴露「" + focusLabel(focusTag, focusAbility) + "」，建议用 8-10 分钟做证据讲评。",
                focusAbility,
                focusTag,
                focusLabel(focusTag, focusAbility),
                affected,
                students.size(),
                20,
                TeachingActionOrchestrator.RISK_MEDIUM,
                firstNonBlank(
                        suggestion == null ? null : suggestion.getAction(),
                        issue == null ? null : issue.getInterventionSuggestion(),
                        "选一个最小失败样例，让学生先说循环、边界或输入结构，再对照代码定位。"
                ),
                "课末让学生独立写出一个不同于样例的最小反例，并说明它验证了哪个判断步骤。",
                mergeEvidence(evidenceRefs(matched), evidenceRefs(issue, suggestion)),
                List.of("class_issue:" + firstNonBlank(focusTag, "UNKNOWN"), "class_ability:" + focusAbility),
                List.of(group(
                        "MINI_LESSON",
                        "全班共性讲评",
                        matched,
                        focusLabel(focusTag, focusAbility),
                        "先讲失败现象和判断步骤，再让学生补一个同类最小样例。"
                ))
        ));
    }

    private void addSmallGroupReview(Long assignmentId,
                                     List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates,
                                     List<AssignmentOverviewResponse.StudentProgressSummary> students) {
        List<AssignmentOverviewResponse.StudentProgressSummary> riskStudents = students.stream()
                .filter(this::needsSmallGroupReview)
                .limit(8)
                .toList();
        if (riskStudents.isEmpty()) {
            return;
        }
        String focus = firstNonBlank(
                riskStudents.get(0).getPrimaryAbilityFocus(),
                riskStudents.get(0).getRepeatedFineGrainedTag(),
                riskStudents.get(0).getLatestFineGrainedIssue(),
                "复发误区"
        );
        candidates.add(signal(
                assignmentId,
                STATUS_SMALL_GROUP_REVIEW,
                TYPE_SMALL_GROUP,
                "小组复盘：" + focus,
                "有 " + riskStudents.size() + " 名学生需要小组复盘，优先处理反复卡住、成长停滞或教师关注动作。",
                focus,
                firstNonBlank(riskStudents.get(0).getRepeatedFineGrainedTag(), riskStudents.get(0).getLatestFineGrainedIssue(), null),
                focus,
                riskStudents.size(),
                students.size(),
                30,
                riskStudents.size() >= 2 ? TeachingActionOrchestrator.RISK_MEDIUM : TeachingActionOrchestrator.RISK_LOW,
                "把这些学生拉成小组，对比两次失败提交，只要求说出共同失败条件和一个最小修复验证。",
                "小组每人提交一条变量轨迹或最小失败样例，证明自己找到了同类错误的触发条件。",
                evidenceRefs(riskStudents),
                sourceSignals(riskStudents),
                List.of(group(
                        "SMALL_GROUP",
                        "需要小组复盘",
                        riskStudents,
                        focus,
                        "对比失败提交，写出共同失败条件。"
                ))
        ));
    }

    private void addDifferentiatedSupport(Long assignmentId,
                                          List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates,
                                          List<AssignmentOverviewResponse.StudentProgressSummary> students) {
        List<AssignmentOverviewResponse.StudentProgressSummary> scaffoldStudents = students.stream()
                .filter(this::needsDifferentiatedSupport)
                .limit(8)
                .toList();
        if (scaffoldStudents.size() < 2) {
            return;
        }
        candidates.add(signal(
                assignmentId,
                STATUS_DIFFERENTIATED_SUPPORT,
                TYPE_DIFFERENTIATED,
                "分层支持：支架退场与解释补证",
                "有 " + scaffoldStudents.size() + " 名学生存在 AI 支架过密或自解释证据不足，建议分层处理。",
                "自主解释与支架退场",
                null,
                "支架退场",
                scaffoldStudents.size(),
                students.size(),
                40,
                TeachingActionOrchestrator.RISK_MEDIUM,
                "把学生分成独立尝试组和解释补证组，前者不新增提示完成一次提交，后者补最小样例解释。",
                "独立尝试组提交一次无新增提示的修改；解释补证组写出输入、变量、输出三段证据。",
                evidenceRefs(scaffoldStudents),
                sourceSignals(scaffoldStudents),
                List.of(group(
                        "INDEPENDENT_PRACTICE",
                        "支架退场练习",
                        scaffoldStudents,
                        "AI 支架自主性",
                        "先不问新提示，完成一次最小独立推进。"
                ))
        ));
    }

    private void addWatch(Long assignmentId,
                          List<AssignmentOverviewResponse.ClassTeachingStrategySignal> candidates,
                          List<AssignmentOverviewResponse.StudentProgressSummary> students,
                          List<AssignmentOverviewResponse.IssueStat> issues) {
        long attentionCount = students.stream()
                .filter(AssignmentOverviewResponse.StudentProgressSummary::isNeedsAttention)
                .count();
        if (attentionCount <= 0 && issues.isEmpty()) {
            return;
        }
        candidates.add(signal(
                assignmentId,
                STATUS_WATCH,
                TYPE_WATCH,
                "继续观察课堂证据",
                "当前有零散风险或错因分布，但还不足以形成明确班级教学策略。",
                issues.isEmpty() ? null : issues.get(0).getAbilityPoint(),
                issues.isEmpty() ? null : issues.get(0).getLabel(),
                issues.isEmpty() ? "课堂观察" : focusLabel(issues.get(0).getLabel(), issues.get(0).getAbilityPoint()),
                attentionCount,
                students.size(),
                80,
                TeachingActionOrchestrator.RISK_LOW,
                "继续观察下一次提交，优先看是否集中到同一错因或能力点。",
                "抽 1 个代表学生写出最小失败样例，作为是否讲评的证据。",
                evidenceRefs(students.stream().filter(AssignmentOverviewResponse.StudentProgressSummary::isNeedsAttention).limit(3).toList()),
                List.of("class_strategy:watch"),
                List.of()
        ));
    }

    private boolean needsSmallGroupReview(AssignmentOverviewResponse.StudentProgressSummary student) {
        if (student == null) {
            return false;
        }
        StudentAbilityProfileResponse.TeachingActionDecision decision = student.getTeachingActionDecision();
        return student.isNeedsAttention()
                || decision != null && (decision.isNeedsTeacherAttention()
                || TeachingActionOrchestrator.RISK_HIGH.equals(decision.getRiskLevel()))
                || student.getRecurringMisconceptionSignal() != null
                && student.getRecurringMisconceptionSignal().isNeedsTeacherAttention()
                || student.getMasteryGrowthSignal() != null
                && student.getMasteryGrowthSignal().isNeedsTeacherAttention();
    }

    private boolean needsDifferentiatedSupport(AssignmentOverviewResponse.StudentProgressSummary student) {
        if (student == null) {
            return false;
        }
        return student.getAiDependencySignal() != null
                && (AiDependencyAnalyzer.STATUS_SCAFFOLD_DENSE.equals(student.getAiDependencySignal().getStatus())
                || AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK.equals(student.getAiDependencySignal().getStatus())
                || AiDependencyAnalyzer.STATUS_TEACHER_FADE_REVIEW.equals(student.getAiDependencySignal().getStatus()))
                || student.getSelfExplanationMasterySignal() != null
                && (SelfExplanationMasteryAnalyzer.STATUS_NEEDS_COACHING.equals(student.getSelfExplanationMasterySignal().getStatus())
                || SelfExplanationMasteryAnalyzer.STATUS_SAFETY_RISK.equals(student.getSelfExplanationMasterySignal().getStatus()));
    }

    private boolean matchesIssueOrAbility(AssignmentOverviewResponse.StudentProgressSummary student,
                                          AssignmentOverviewResponse.IssueStat issue,
                                          AssignmentOverviewResponse.AbilityStat ability) {
        if (student == null) {
            return false;
        }
        String issueLabel = issue == null ? null : issue.getLabel();
        String abilityPoint = firstNonBlank(issue == null ? null : issue.getAbilityPoint(),
                ability == null ? null : ability.getAbilityPoint(), null);
        return matches(issueLabel, student.getLatestFineGrainedIssue(), student.getLatestIssueTag(), student.getRepeatedFineGrainedTag(), student.getRepeatedIssueTag())
                || abilityPoint != null && !abilityPoint.isBlank()
                && (abilityPoint.equals(student.getPrimaryAbilityFocus())
                || student.getAbilitySummary() != null && student.getAbilitySummary().stream()
                .anyMatch(item -> abilityPoint.equals(item.getAbilityPoint())));
    }

    private boolean matches(String target, String... values) {
        if (target == null || target.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (target.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLowConfidence(AssignmentOverviewResponse.StudentProgressSummary student) {
        return student != null
                && student.getLatestConfidence() != null
                && student.getLatestConfidence() < AiQualityMetrics.LOW_CONFIDENCE_THRESHOLD;
    }

    private AssignmentOverviewResponse.ClassTeachingStrategySignal signal(Long assignmentId,
                                                                          String status,
                                                                          String strategyType,
                                                                          String title,
                                                                          String summary,
                                                                          String focusAbility,
                                                                          String focusTag,
                                                                          String focusLabel,
                                                                          long affectedStudentCount,
                                                                          long totalStudentCount,
                                                                          int priority,
                                                                          String riskLevel,
                                                                          String teacherAction,
                                                                          String exitTicket,
                                                                          List<String> evidenceRefs,
                                                                          List<String> sourceSignals,
                                                                          List<AssignmentOverviewResponse.ClassTeachingStrategyGroup> groups) {
        List<String> safeEvidenceRefs = evidenceRefs == null ? List.of() : evidenceRefs.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(8)
                .toList();
        List<String> safeSourceSignals = sourceSignals == null ? List.of() : sourceSignals.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .limit(8)
                .toList();
        return AssignmentOverviewResponse.ClassTeachingStrategySignal.builder()
                .strategyKey(strategyKey(assignmentId, status, focusAbility, focusTag))
                .status(status)
                .statusLabel(label(status))
                .strategyType(strategyType)
                .title(title)
                .summary(summary)
                .focusAbility(focusAbility)
                .focusTag(focusTag)
                .focusLabel(focusLabel)
                .affectedStudentCount(affectedStudentCount)
                .affectedStudentRatio(totalStudentCount <= 0 ? 0.0 : round(affectedStudentCount / (double) totalStudentCount))
                .priority(priority)
                .riskLevel(riskLevel)
                .teacherAction(teacherAction)
                .exitTicket(exitTicket)
                .groups(groups == null ? List.of() : groups)
                .evidenceRefs(safeEvidenceRefs)
                .sourceSignals(safeSourceSignals)
                .build();
    }

    private String strategyKey(Long assignmentId, String status, String focusAbility, String focusTag) {
        return "strategy:"
                + (assignmentId == null ? "unscoped" : assignmentId)
                + ":"
                + slug(status)
                + ":"
                + slug(firstNonBlank(focusTag, focusAbility, "no-focus"));
    }

    private String slug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private AssignmentOverviewResponse.ClassTeachingStrategyGroup group(String groupType,
                                                                        String title,
                                                                        List<AssignmentOverviewResponse.StudentProgressSummary> students,
                                                                        String focus,
                                                                        String action) {
        List<AssignmentOverviewResponse.StudentProgressSummary> safeStudents = safeList(students);
        return AssignmentOverviewResponse.ClassTeachingStrategyGroup.builder()
                .groupType(groupType)
                .title(title)
                .studentProfileIds(safeStudents.stream()
                        .map(AssignmentOverviewResponse.StudentProgressSummary::getStudentProfileId)
                        .filter(Objects::nonNull)
                        .limit(8)
                        .toList())
                .studentNames(safeStudents.stream()
                        .map(AssignmentOverviewResponse.StudentProgressSummary::getDisplayName)
                        .filter(name -> name != null && !name.isBlank())
                        .limit(8)
                        .toList())
                .focus(focus)
                .action(action)
                .evidenceRefs(evidenceRefs(safeStudents))
                .build();
    }

    private List<String> sourceSignals(List<AssignmentOverviewResponse.StudentProgressSummary> students) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (AssignmentOverviewResponse.StudentProgressSummary student : safeList(students)) {
            if (student.getTeachingActionDecision() != null && student.getTeachingActionDecision().getActionType() != null) {
                refs.add("teaching_action:" + student.getTeachingActionDecision().getActionType());
            }
            if (student.getRecurringMisconceptionSignal() != null && student.getRecurringMisconceptionSignal().getStatus() != null) {
                refs.add("recurring_misconception:" + student.getRecurringMisconceptionSignal().getStatus());
            }
            if (student.getAiDependencySignal() != null && student.getAiDependencySignal().getStatus() != null) {
                refs.add("ai_dependency:" + student.getAiDependencySignal().getStatus());
            }
            if (student.getSelfExplanationMasterySignal() != null && student.getSelfExplanationMasterySignal().getStatus() != null) {
                refs.add("self_explanation:" + student.getSelfExplanationMasterySignal().getStatus());
            }
            if (student.getMasteryGrowthSignal() != null && student.getMasteryGrowthSignal().getStatus() != null) {
                refs.add("mastery_growth:" + student.getMasteryGrowthSignal().getStatus());
            }
        }
        return refs.stream().limit(8).toList();
    }

    private List<String> evidenceRefs(List<AssignmentOverviewResponse.StudentProgressSummary> students) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (AssignmentOverviewResponse.StudentProgressSummary student : safeList(students)) {
            if (student.getLatestSubmissionId() != null) {
                refs.add("submission:" + student.getLatestSubmissionId());
            }
            addAll(refs, student.getTeachingActionDecision() == null ? null : student.getTeachingActionDecision().getEvidenceRefs());
            addAll(refs, student.getRecurringMisconceptionSignal() == null ? null : student.getRecurringMisconceptionSignal().getEvidenceRefs());
            addAll(refs, student.getSelfExplanationMasterySignal() == null ? null : student.getSelfExplanationMasterySignal().getEvidenceRefs());
            addAll(refs, student.getAiDependencySignal() == null ? null : student.getAiDependencySignal().getDependencyEvidenceRefs());
            addAll(refs, student.getMasteryGrowthSignal() == null ? null : student.getMasteryGrowthSignal().getEvidenceRefs());
        }
        return refs.stream().limit(8).toList();
    }

    private List<String> evidenceRefs(AssignmentOverviewResponse.IssueStat issue,
                                      AssignmentOverviewResponse.ClassReviewSuggestion suggestion) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (issue != null && issue.getLabel() != null) {
            refs.add("class_issue:" + issue.getLabel());
        }
        if (suggestion != null && suggestion.getEvidenceSubmissionIds() != null) {
            suggestion.getEvidenceSubmissionIds().stream()
                    .filter(Objects::nonNull)
                    .map(id -> "submission:" + id)
                    .forEach(refs::add);
        }
        return refs.stream().limit(5).toList();
    }

    private List<String> mergeEvidence(List<String> left, List<String> right) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        addAll(refs, left);
        addAll(refs, right);
        return refs.stream().limit(8).toList();
    }

    private void addAll(LinkedHashSet<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(target::add);
    }

    private String focusLabel(String focusTag, String focusAbility) {
        return firstNonBlank(focusAbility, focusTag, "共性问题");
    }

    private String label(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_CALIBRATION_REVIEW -> "诊断校准复核";
            case STATUS_WHOLE_CLASS_MINI_LESSON -> "全班小讲评";
            case STATUS_SMALL_GROUP_REVIEW -> "小组复盘";
            case STATUS_DIFFERENTIATED_SUPPORT -> "分层支持";
            case STATUS_WATCH -> "继续观察";
            default -> "暂无策略";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
