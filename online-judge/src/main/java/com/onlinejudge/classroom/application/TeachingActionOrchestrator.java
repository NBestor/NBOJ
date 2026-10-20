package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class TeachingActionOrchestrator {

    public static final String ACTION_TEACHER_REVIEW = "TEACHER_REVIEW";
    public static final String ACTION_SPIRAL_REVIEW = "SPIRAL_REVIEW";
    public static final String ACTION_REGRESSION_REPAIR = "REGRESSION_REPAIR";
    public static final String ACTION_INDEPENDENT_ATTEMPT = "INDEPENDENT_ATTEMPT";
    public static final String ACTION_SELF_EXPLANATION_PRACTICE = "SELF_EXPLANATION_PRACTICE";
    public static final String ACTION_POST_AC_REFLECTION = "POST_AC_REFLECTION";
    public static final String ACTION_TRANSFER_PRACTICE = "TRANSFER_PRACTICE";
    public static final String ACTION_CONTINUE_DIAGNOSIS = "CONTINUE_DIAGNOSIS";

    public static final String ACTOR_TEACHER = "TEACHER";
    public static final String ACTOR_STUDENT = "STUDENT";
    public static final String ACTOR_AI_COACH = "AI_COACH";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    public StudentAbilityProfileResponse.TeachingActionDecision decide(
            StudentTrajectoryResponse.LearningTrajectorySignal learningTrajectorySignal,
            StudentTrajectoryResponse.LearningActionEvidence learningActionEvidence,
            StudentTrajectoryResponse.PostAcTransferSignal postAcTransferSignal,
            StudentAbilityProfileResponse.RecurringMisconceptionSignal recurringMisconceptionSignal,
            StudentAbilityProfileResponse.SelfExplanationMasterySignal selfExplanationMasterySignal,
            StudentAbilityProfileResponse.AiDependencySignal aiDependencySignal,
            StudentAbilityProfileResponse.MasteryGrowthSignal masteryGrowthSignal,
            String fallbackAction) {
        List<StudentAbilityProfileResponse.TeachingActionDecision> candidates = new ArrayList<>();
        addRecurringMisconception(candidates, recurringMisconceptionSignal);
        addSelfExplanationTeacherReview(candidates, selfExplanationMasterySignal);
        addAiDependencyTeacherReview(candidates, aiDependencySignal);
        addMasterySpiralReview(candidates, masteryGrowthSignal);
        addMasteryRegressionOrPlateau(candidates, masteryGrowthSignal);
        addAiDependencyIndependentAttempt(candidates, aiDependencySignal);
        addSelfExplanationPractice(candidates, selfExplanationMasterySignal);
        addLearningActionAdjustment(candidates, learningActionEvidence);
        addPostAcTransfer(candidates, postAcTransferSignal);
        addLearningTrajectory(candidates, learningTrajectorySignal);

        StudentAbilityProfileResponse.TeachingActionDecision selected = candidates.stream()
                .min(Comparator
                        .comparingInt(this::priority)
                        .thenComparing(StudentAbilityProfileResponse.TeachingActionDecision::getActionType,
                                Comparator.nullsLast(String::compareTo)))
                .orElseGet(() -> fallback(fallbackAction));
        selected.setCandidateCount(candidates.isEmpty() ? 1 : candidates.size());
        return selected;
    }

    public boolean isRisk(StudentAbilityProfileResponse.TeachingActionDecision decision) {
        return decision != null
                && (decision.isNeedsTeacherAttention() || RISK_HIGH.equals(decision.getRiskLevel()));
    }

    public boolean isActionable(StudentAbilityProfileResponse.TeachingActionDecision decision) {
        return decision != null
                && decision.getActionType() != null
                && !ACTION_CONTINUE_DIAGNOSIS.equals(decision.getActionType());
    }

    private void addRecurringMisconception(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                           StudentAbilityProfileResponse.RecurringMisconceptionSignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return;
        }
        if (RecurringMisconceptionAnalyzer.STATUS_ESCALATE.equals(signal.getStatus())) {
            candidates.add(decision(
                    ACTION_TEACHER_REVIEW,
                    ACTOR_TEACHER,
                    10,
                    RISK_HIGH,
                    "先做教师复盘",
                    signal.getSummary(),
                    "复发误区已经跨题或跨作业升级，继续加新题会稀释关键卡点。",
                    signal.getRecommendedAction(),
                    "如果学生仍无法说出共同失败条件，请教师只用两道证据题做示范复盘。",
                    signal.getEvidenceRefs(),
                    List.of("recurring_misconception:" + signal.getStatus()),
                    true
            ));
        } else if (RecurringMisconceptionAnalyzer.STATUS_RECURRING.equals(signal.getStatus())) {
            candidates.add(decision(
                    ACTION_SPIRAL_REVIEW,
                    ACTOR_STUDENT,
                    28,
                    RISK_MEDIUM,
                    "修复复发误区",
                    signal.getSummary(),
                    "同类误区已经跨题或跨作业重复，需要先把共同条件显性化。",
                    signal.getRecommendedAction(),
                    "如果复盘后仍命中同类误区，升级为教师复盘。",
                    signal.getEvidenceRefs(),
                    List.of("recurring_misconception:" + signal.getStatus()),
                    signal.isNeedsTeacherAttention()
            ));
        }
    }

    private void addSelfExplanationTeacherReview(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                                 StudentAbilityProfileResponse.SelfExplanationMasterySignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return;
        }
        boolean teacherReview = SelfExplanationMasteryAnalyzer.STATUS_SAFETY_RISK.equals(signal.getStatus())
                || SelfExplanationMasteryAnalyzer.STATUS_NO_EVIDENCE.equals(signal.getStatus()) && signal.isNeedsTeacherAttention();
        if (!teacherReview) {
            return;
        }
        candidates.add(decision(
                ACTION_TEACHER_REVIEW,
                ACTOR_TEACHER,
                11,
                RISK_HIGH,
                "先看解释示范",
                signal.getSummary(),
                "学生解释没有落在可验证证据层，继续给提示可能扩大泄题或依赖风险。",
                signal.getRecommendedAction(),
                "如果学生继续索要完整改法，请只要求描述输入特征、输出差异或变量现象。",
                signal.getEvidenceRefs(),
                List.of("self_explanation:" + signal.getStatus()),
                true
        ));
    }

    private void addAiDependencyTeacherReview(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                              StudentAbilityProfileResponse.AiDependencySignal signal) {
        if (signal == null || !AiDependencyAnalyzer.STATUS_TEACHER_FADE_REVIEW.equals(signal.getStatus())) {
            return;
        }
        candidates.add(decision(
                ACTION_TEACHER_REVIEW,
                ACTOR_TEACHER,
                12,
                RISK_HIGH,
                "先做撤支架复盘",
                signal.getSummary(),
                "AI 支架长期高密度且缺少独立推进，需要由教师示范如何拆提示。",
                signal.getRecommendedAction(),
                "如果仍需要逐步提示，请教师只给问题拆解边界，不给下一步改法。",
                signal.getDependencyEvidenceRefs(),
                List.of("ai_dependency:" + signal.getStatus()),
                true
        ));
    }

    private void addMasterySpiralReview(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                        StudentAbilityProfileResponse.MasteryGrowthSignal signal) {
        if (signal == null || !MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED.equals(signal.getStatus())) {
            return;
        }
        candidates.add(decision(
                ACTION_SPIRAL_REVIEW,
                ACTOR_TEACHER,
                13,
                RISK_HIGH,
                "做跨题螺旋复习",
                signal.getSummary(),
                "长期成长信号显示同一能力点跨题停滞，优先做结构化复盘而不是继续局部提示。",
                signal.getRecommendedAction(),
                "如果学生无法说出共同失败条件，请教师用两道证据题示范一轮。",
                signal.getEvidenceRefs(),
                List.of("mastery_growth:" + signal.getStatus()),
                true
        ));
    }

    private void addMasteryRegressionOrPlateau(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                               StudentAbilityProfileResponse.MasteryGrowthSignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return;
        }
        if (MasteryGrowthAnalyzer.STATUS_REGRESSION.equals(signal.getStatus())) {
            candidates.add(decision(
                    ACTION_REGRESSION_REPAIR,
                    ACTOR_STUDENT,
                    20,
                    RISK_HIGH,
                    "对比回退差异",
                    signal.getSummary(),
                    "学生已经出现通过后回退，下一步应先修复掌握稳定性。",
                    signal.getRecommendedAction(),
                    "如果无法定位差异，先回看最近一次 AC 的关键边界样例。",
                    signal.getEvidenceRefs(),
                    List.of("mastery_growth:" + signal.getStatus()),
                    signal.isNeedsTeacherAttention()
            ));
        } else if (MasteryGrowthAnalyzer.STATUS_PLATEAU.equals(signal.getStatus())) {
            candidates.add(decision(
                    ACTION_REGRESSION_REPAIR,
                    ACTOR_STUDENT,
                    24,
                    RISK_MEDIUM,
                    "修复成长停滞",
                    signal.getSummary(),
                    "近期连续失败或同能力停滞，需要先把问题降到最小失败样例。",
                    signal.getRecommendedAction(),
                    "如果复盘后仍连续失败，升级为螺旋复习或教师介入。",
                    signal.getEvidenceRefs(),
                    List.of("mastery_growth:" + signal.getStatus()),
                    signal.isNeedsTeacherAttention()
            ));
        }
    }

    private void addAiDependencyIndependentAttempt(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                                   StudentAbilityProfileResponse.AiDependencySignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return;
        }
        if (!AiDependencyAnalyzer.STATUS_SCAFFOLD_DENSE.equals(signal.getStatus())
                && !AiDependencyAnalyzer.STATUS_DEPENDENCY_RISK.equals(signal.getStatus())) {
            return;
        }
        candidates.add(decision(
                ACTION_INDEPENDENT_ATTEMPT,
                ACTOR_STUDENT,
                30,
                RISK_MEDIUM,
                "先独立尝试一次",
                signal.getSummary(),
                "AI 支架使用偏密，下一步要验证学生不新增提示时能否形成最小推进。",
                signal.getRecommendedAction(),
                "如果独立尝试仍无法推进，再回到 Coach，但只问一个最小验证问题。",
                signal.getDependencyEvidenceRefs(),
                List.of("ai_dependency:" + signal.getStatus()),
                signal.isNeedsTeacherAttention()
        ));
    }

    private void addSelfExplanationPractice(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                            StudentAbilityProfileResponse.SelfExplanationMasterySignal signal) {
        if (signal == null || signal.getStatus() == null) {
            return;
        }
        if (!SelfExplanationMasteryAnalyzer.STATUS_EMERGING.equals(signal.getStatus())
                && !SelfExplanationMasteryAnalyzer.STATUS_NEEDS_COACHING.equals(signal.getStatus())) {
            return;
        }
        candidates.add(decision(
                ACTION_SELF_EXPLANATION_PRACTICE,
                ACTOR_STUDENT,
                40,
                RISK_MEDIUM,
                "补一条证据解释",
                signal.getSummary(),
                "学生已有回答迹象，但还没稳定转成可验证样例、变量轨迹或输出对比。",
                signal.getRecommendedAction(),
                "如果仍只有方向或空泛确认，请降级为教师示范一个最小样例解释。",
                signal.getEvidenceRefs(),
                List.of("self_explanation:" + signal.getStatus()),
                signal.isNeedsTeacherAttention()
        ));
    }

    private void addLearningActionAdjustment(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                             StudentTrajectoryResponse.LearningActionEvidence evidence) {
        if (evidence == null || evidence.getExecutionStatus() == null) {
            return;
        }
        if (!"CONTRADICTED".equals(evidence.getExecutionStatus())
                && !"NOT_OBSERVED".equals(evidence.getExecutionStatus())) {
            return;
        }
        String risk = "CONTRADICTED".equals(evidence.getExecutionStatus()) ? RISK_MEDIUM : RISK_LOW;
        candidates.add(decision(
                ACTION_CONTINUE_DIAGNOSIS,
                ACTOR_AI_COACH,
                45,
                risk,
                "调整学习动作",
                evidence.getObservedEvidence(),
                "上一轮学习动作尚未被后续证据支持，需要把动作改成更可观察的产出。",
                firstNonBlank(evidence.getNextAdjustment(), "继续根据最新诊断缩小失败样例。"),
                "如果后续提交继续反驳动作效果，请降低到最小样例或教师介入。",
                evidence.getEvidenceRefs(),
                List.of("learning_action:" + evidence.getExecutionStatus()),
                false
        ));
    }

    private void addPostAcTransfer(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                   StudentTrajectoryResponse.PostAcTransferSignal signal) {
        if (signal == null || signal.getPhase() == null) {
            return;
        }
        if (PostAcTransferAnalyzer.PHASE_TRANSFER_READY.equals(signal.getPhase())) {
            candidates.add(decision(
                    ACTION_TRANSFER_PRACTICE,
                    ACTOR_STUDENT,
                    48,
                    RISK_LOW,
                    "进入迁移练习",
                    signal.getSummary(),
                    "通过后的复盘证据已经形成，可以用同能力新题检验迁移。",
                    signal.getRecommendedAction(),
                    "如果新题继续命中同类错因，回到通过题复盘共同条件。",
                    signal.getEvidenceRefs(),
                    List.of("post_ac_transfer:" + signal.getPhase()),
                    signal.isNeedsTeacherAttention()
            ));
            return;
        }
        if (!PostAcTransferAnalyzer.PHASE_JUST_ACCEPTED.equals(signal.getPhase())
                && !PostAcTransferAnalyzer.PHASE_REFLECTION_NEEDED.equals(signal.getPhase())) {
            return;
        }
        candidates.add(decision(
                ACTION_POST_AC_REFLECTION,
                ACTOR_STUDENT,
                50,
                RISK_MEDIUM,
                "补一条通过后复盘",
                signal.getSummary(),
                "题目已经通过，但还缺少可迁移的边界、复杂度或修复解释。",
                signal.getRecommendedAction(),
                "如果复盘只能复述答案，请先回答一个 Coach 追问补足证据。",
                signal.getEvidenceRefs(),
                List.of("post_ac_transfer:" + signal.getPhase()),
                signal.isNeedsTeacherAttention()
        ));
    }

    private void addLearningTrajectory(List<StudentAbilityProfileResponse.TeachingActionDecision> candidates,
                                       StudentTrajectoryResponse.LearningTrajectorySignal signal) {
        if (signal == null || signal.getPhase() == null || !signal.isNeedsTeacherAttention()) {
            return;
        }
        candidates.add(decision(
                ACTION_CONTINUE_DIAGNOSIS,
                ACTOR_AI_COACH,
                55,
                RISK_MEDIUM,
                "继续收窄诊断",
                signal.getSummary(),
                firstNonBlank(signal.getNextFocus(), "学习轨迹提示当前需要更小粒度证据。"),
                firstNonBlank(signal.getNextFocus(), "继续根据最新诊断缩小失败样例。"),
                "如果下一次提交仍无推进，请升级为教师查看。",
                signal.getEvidenceRef() == null || signal.getEvidenceRef().isBlank() ? List.of() : List.of(signal.getEvidenceRef()),
                List.of("learning_trajectory:" + signal.getPhase()),
                signal.isNeedsTeacherAttention()
        ));
    }

    private StudentAbilityProfileResponse.TeachingActionDecision fallback(String fallbackAction) {
        return decision(
                ACTION_CONTINUE_DIAGNOSIS,
                ACTOR_AI_COACH,
                90,
                RISK_LOW,
                "继续观察诊断",
                "当前没有明显高风险教育信号，继续按最新提交和诊断证据推进。",
                "暂未观察到需要教师优先介入或暂停练习的结构化风险。",
                firstNonBlank(fallbackAction, "继续根据最新诊断缩小失败样例。"),
                "如果后续出现复发误区、支架依赖或成长回退，再升级教学动作。",
                List.of(),
                List.of("fallback"),
                false
        );
    }

    private StudentAbilityProfileResponse.TeachingActionDecision decision(String actionType,
                                                                          String actor,
                                                                          int priority,
                                                                          String riskLevel,
                                                                          String title,
                                                                          String summary,
                                                                          String primaryReason,
                                                                          String recommendedAction,
                                                                          String fallbackAction,
                                                                          List<String> evidenceRefs,
                                                                          List<String> sourceSignals,
                                                                          boolean needsTeacherAttention) {
        return StudentAbilityProfileResponse.TeachingActionDecision.builder()
                .actionType(actionType)
                .actor(actor)
                .priority(priority)
                .riskLevel(riskLevel)
                .title(title)
                .summary(firstNonBlank(summary, primaryReason, title))
                .primaryReason(firstNonBlank(primaryReason, summary, title))
                .recommendedAction(firstNonBlank(recommendedAction, fallbackAction, summary))
                .fallbackAction(firstNonBlank(fallbackAction, "继续收集下一次提交或解释证据后再调整动作。"))
                .evidenceRefs(distinct(evidenceRefs))
                .sourceSignals(distinct(sourceSignals))
                .candidateCount(1)
                .needsTeacherAttention(needsTeacherAttention || ACTOR_TEACHER.equals(actor))
                .build();
    }

    private int priority(StudentAbilityProfileResponse.TeachingActionDecision decision) {
        return decision == null || decision.getPriority() == null ? Integer.MAX_VALUE : decision.getPriority();
    }

    private List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(set::add);
        return set.stream().toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
