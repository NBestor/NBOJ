package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.dto.RecommendationEffectivenessResponse;
import com.onlinejudge.classroom.dto.StudentAbilityProfileResponse;
import com.onlinejudge.classroom.dto.StudentRecommendationResponse;
import com.onlinejudge.classroom.dto.StudentTrajectoryResponse;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentRecommendationService {

    private static final String STRATEGY_REPAIR_SAME_PROBLEM = "REPAIR_SAME_PROBLEM";
    private static final String STRATEGY_TRANSFER_TO_NEW_PROBLEM = "TRANSFER_TO_NEW_PROBLEM";
    private static final String STRATEGY_POST_AC_REFLECTION = "POST_AC_REFLECTION";
    private static final String STRATEGY_MISCONCEPTION_REPAIR = "MISCONCEPTION_REPAIR";
    private static final String STRATEGY_TEACHER_REVIEW_RECOMMENDED = "TEACHER_REVIEW_RECOMMENDED";
    private static final String STRATEGY_SELF_EXPLANATION_PRACTICE = "SELF_EXPLANATION_PRACTICE";
    private static final String STRATEGY_TEACHER_EXPLANATION_REVIEW = "TEACHER_EXPLANATION_REVIEW";
    private static final String STRATEGY_INDEPENDENT_ATTEMPT = "INDEPENDENT_ATTEMPT";
    private static final String STRATEGY_TEACHER_SCAFFOLD_FADE_REVIEW = "TEACHER_SCAFFOLD_FADE_REVIEW";
    private static final String STRATEGY_MASTERY_PLATEAU_REPAIR = "MASTERY_PLATEAU_REPAIR";
    private static final String STRATEGY_MASTERY_REGRESSION_REPAIR = "MASTERY_REGRESSION_REPAIR";
    private static final String STRATEGY_MASTERY_SPIRAL_REVIEW = "MASTERY_SPIRAL_REVIEW";
    private static final String STRATEGY_TEACHING_ACTION_TEACHER_REVIEW = "TEACHING_ACTION_TEACHER_REVIEW";
    private static final String STRATEGY_TEACHING_ACTION_SPIRAL_REVIEW = "TEACHING_ACTION_SPIRAL_REVIEW";
    private static final String STRATEGY_TEACHING_ACTION_REGRESSION_REPAIR = "TEACHING_ACTION_REGRESSION_REPAIR";
    private static final String STRATEGY_TEACHING_ACTION_INDEPENDENT_ATTEMPT = "TEACHING_ACTION_INDEPENDENT_ATTEMPT";
    private static final String STRATEGY_TEACHING_ACTION_SELF_EXPLANATION_PRACTICE = "TEACHING_ACTION_SELF_EXPLANATION_PRACTICE";
    private static final String STRATEGY_TEACHING_ACTION_POST_AC_REFLECTION = "TEACHING_ACTION_POST_AC_REFLECTION";
    private static final String STRATEGY_REFLECTION_EVIDENCE = "REFLECTION_EVIDENCE";
    private static final String STRATEGY_STEP_DOWN_REVIEW = "STEP_DOWN_REVIEW";

    private final StudentAbilityProfileService abilityProfileService;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final CoachInteractionAnalyzer coachInteractionAnalyzer;
    private final StudentRecommendationEventService recommendationEventService;
    private final PostAcTransferAnalyzer postAcTransferAnalyzer;
    private final RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer;
    private final SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer;
    private final AiDependencyAnalyzer aiDependencyAnalyzer;
    private final MasteryGrowthAnalyzer masteryGrowthAnalyzer;
    private final TeachingActionOrchestrator teachingActionOrchestrator;
    private final RecommendationActionEvidenceAnalyzer recommendationActionEvidenceAnalyzer;

    @Autowired
    public StudentRecommendationService(StudentAbilityProfileService abilityProfileService,
                                        ProblemRepository problemRepository,
                                        SubmissionRepository submissionRepository,
                                        SubmissionAnalysisRepository submissionAnalysisRepository,
                                        CoachInteractionAnalyzer coachInteractionAnalyzer,
                                        StudentRecommendationEventService recommendationEventService,
                                        PostAcTransferAnalyzer postAcTransferAnalyzer,
                                        RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer,
                                        SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer,
                                        AiDependencyAnalyzer aiDependencyAnalyzer,
                                        MasteryGrowthAnalyzer masteryGrowthAnalyzer,
                                        TeachingActionOrchestrator teachingActionOrchestrator) {
        this(abilityProfileService,
                problemRepository,
                submissionRepository,
                submissionAnalysisRepository,
                coachInteractionAnalyzer,
                recommendationEventService,
                postAcTransferAnalyzer,
                recurringMisconceptionAnalyzer,
                selfExplanationMasteryAnalyzer,
                aiDependencyAnalyzer,
                masteryGrowthAnalyzer,
                teachingActionOrchestrator,
                new RecommendationActionEvidenceAnalyzer());
    }

    public StudentRecommendationService(StudentAbilityProfileService abilityProfileService,
                                        ProblemRepository problemRepository,
                                        SubmissionRepository submissionRepository,
                                        SubmissionAnalysisRepository submissionAnalysisRepository,
                                        CoachInteractionAnalyzer coachInteractionAnalyzer,
                                        StudentRecommendationEventService recommendationEventService,
                                        PostAcTransferAnalyzer postAcTransferAnalyzer,
                                        RecurringMisconceptionAnalyzer recurringMisconceptionAnalyzer,
                                        SelfExplanationMasteryAnalyzer selfExplanationMasteryAnalyzer,
                                        AiDependencyAnalyzer aiDependencyAnalyzer,
                                        MasteryGrowthAnalyzer masteryGrowthAnalyzer,
                                        TeachingActionOrchestrator teachingActionOrchestrator,
                                        RecommendationActionEvidenceAnalyzer recommendationActionEvidenceAnalyzer) {
        this.abilityProfileService = abilityProfileService;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.submissionAnalysisRepository = submissionAnalysisRepository;
        this.coachInteractionAnalyzer = coachInteractionAnalyzer;
        this.recommendationEventService = recommendationEventService;
        this.postAcTransferAnalyzer = postAcTransferAnalyzer;
        this.recurringMisconceptionAnalyzer = recurringMisconceptionAnalyzer;
        this.selfExplanationMasteryAnalyzer = selfExplanationMasteryAnalyzer;
        this.aiDependencyAnalyzer = aiDependencyAnalyzer;
        this.masteryGrowthAnalyzer = masteryGrowthAnalyzer;
        this.teachingActionOrchestrator = teachingActionOrchestrator;
        this.recommendationActionEvidenceAnalyzer = recommendationActionEvidenceAnalyzer == null
                ? new RecommendationActionEvidenceAnalyzer()
                : recommendationActionEvidenceAnalyzer;
    }

    public StudentRecommendationResponse recommend(Long studentProfileId) {
        StudentAbilityProfileService.StudentScope scope = abilityProfileService.resolveStudentScope(studentProfileId);
        List<Long> profileIds = scope.profileIds();
        List<Submission> submissions = profileIds.isEmpty()
                ? List.of()
                : submissionRepository.findByStudentProfileIdInOrderBySubmittedAtDesc(profileIds).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(java.time.LocalDateTime::compareTo)).reversed()
                        .thenComparing(Submission::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Long> submissionIds = submissions.stream()
                .map(Submission::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SubmissionAnalysis> analyses = submissionIds.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(submissionIds)
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity()));
        List<StudentRecommendationEvent> recommendationEvents = recommendationEvents(profileIds);
        List<RecommendationEffectivenessResponse.ActionEvidenceSignal> actionEvidenceSignals =
                recommendationActionEvidenceAnalyzer.analyze(recommendationEvents);
        Set<Long> attemptedProblemIds = new LinkedHashSet<>();
        Map<Long, Submission> latestByProblem = new LinkedHashMap<>();
        submissions.stream()
                .filter(Objects::nonNull)
                .forEach(submission -> {
                    if (submission.getProblemId() != null) {
                        attemptedProblemIds.add(submission.getProblemId());
                        latestByProblem.putIfAbsent(submission.getProblemId(), submission);
                    }
                });
        Submission latestFailed = latestByProblem.values()
                .stream()
                .filter(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .findFirst()
                .orElse(null);
        List<Problem> problems = problemRepository.findAllByOrderByIdAsc();
        Map<Long, Problem> problemMap = problems.stream()
                .filter(problem -> problem.getId() != null)
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        StudentRecommendationResponse.RecommendationItem primary = latestFailed != null
                ? redoCurrentProblem(latestFailed, problemMap.get(latestFailed.getProblemId()), analyses.get(latestFailed.getId()))
                : nextUnattemptedProblem(problems, attemptedProblemIds);
        if (primary == null && !submissions.isEmpty()) {
            Submission latest = submissions.get(0);
            primary = reviewLatestSubmission(latest, problemMap.get(latest.getProblemId()));
        }
        List<StudentRecommendationResponse.RecommendationItem> recommendations = primary == null
                ? List.of()
                : List.of(primary);
        recommendations.forEach(item -> item.setRecommendationToken(recommendationEventService.tokenFor(studentProfileId, item)));
        Map<String, RecommendationEffectivenessResponse.ActionEvidenceSignal> actionEvidenceByToken =
                actionEvidenceSignals.stream()
                        .filter(signal -> signal.getRecommendationToken() != null)
                        .collect(Collectors.toMap(
                                RecommendationEffectivenessResponse.ActionEvidenceSignal::getRecommendationToken,
                                Function.identity(),
                                (left, right) -> left
                        ));
        recommendations.forEach(item -> {
            RecommendationEffectivenessResponse.ActionEvidenceSignal signal =
                    actionEvidenceByToken.get(item.getRecommendationToken());
            if (signal != null) {
                item.setActionOutcome(signal.getOutcome());
                item.setActionOutcomeSummary(signal.getSummary());
                item.setActionMatchBasis(signal.getMatchBasis());
                item.setActionEvidenceRefs(signal.getEvidenceRefs());
            }
        });
        recommendations.forEach(item -> recommendationEventService.recordExposure(studentProfileId, item));

        return StudentRecommendationResponse.builder()
                .student(scope.student())
                .summary(simpleSummary(recommendations))
                .recommendations(recommendations)
                .build();
    }

    private StudentRecommendationResponse.RecommendationItem redoCurrentProblem(
            Submission submission,
            Problem problem,
            SubmissionAnalysis analysis
    ) {
        String problemTitle = problem == null ? null : problem.getTitle();
        String diagnosis = analysis == null ? null : analysis.getHeadline();
        return StudentRecommendationResponse.RecommendationItem.builder()
                .type("REDO")
                .title("继续处理最近未通过的题")
                .reason(diagnosis == null || diagnosis.isBlank()
                        ? "最近一次提交还没有通过，先根据判题结果继续修改。"
                        : "最近一次提交还没有通过，当前需要处理：" + diagnosis)
                .actionLabel("继续修改")
                .assignmentId(submission.getAssignmentId())
                .problemId(submission.getProblemId())
                .problemTitle(problemTitle)
                .focusTags(List.of())
                .evidenceProblemIds(submission.getProblemId() == null ? List.of() : List.of(submission.getProblemId()))
                .expectedCompletionSignal("再次提交后通过，或当前问题不再出现。")
                .strategy(STRATEGY_REPAIR_SAME_PROBLEM)
                .priority(1)
                .build();
    }

    private StudentRecommendationResponse.RecommendationItem nextUnattemptedProblem(
            List<Problem> problems,
            Set<Long> attemptedProblemIds
    ) {
        Problem problem = problems.stream()
                .filter(item -> item.getId() != null && !attemptedProblemIds.contains(item.getId()))
                .findFirst()
                .orElse(null);
        if (problem == null) {
            return null;
        }
        return StudentRecommendationResponse.RecommendationItem.builder()
                .type("NEXT_PROBLEM")
                .title("开始一题新题")
                .reason("当前没有未完成的题，可以继续下一题。")
                .actionLabel("开始做题")
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .focusTags(List.of())
                .evidenceProblemIds(List.of())
                .expectedCompletionSignal("完成一次独立提交。")
                .strategy("START_NEW_PROBLEM")
                .priority(1)
                .build();
    }

    private StudentRecommendationResponse.RecommendationItem reviewLatestSubmission(
            Submission submission,
            Problem problem
    ) {
        return StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title("回看最近一次提交")
                .reason("当前已尝试的题目都已通过，可以简单回看关键修改。")
                .actionLabel("查看提交")
                .assignmentId(submission.getAssignmentId())
                .problemId(submission.getProblemId())
                .problemTitle(problem == null ? null : problem.getTitle())
                .focusTags(List.of())
                .evidenceProblemIds(submission.getProblemId() == null ? List.of() : List.of(submission.getProblemId()))
                .expectedCompletionSignal("能说清这次通过前改动了什么。")
                .strategy("REVIEW_LAST_SUBMISSION")
                .priority(1)
                .build();
    }

    private String simpleSummary(List<StudentRecommendationResponse.RecommendationItem> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "当前没有需要处理的学习任务。";
        }
        return switch (recommendations.get(0).getType()) {
            case "REDO" -> "先完成最近未通过的题。";
            case "NEXT_PROBLEM" -> "当前题目已处理完，可以开始下一题。";
            default -> "回看最近一次提交。";
        };
    }

    private void addTeachingActionRecommendation(StudentAbilityProfileResponse profile,
                                                 StudentAbilityProfileResponse.TeachingActionDecision decision,
                                                 Long latestAssignmentId,
                                                 List<StudentRecommendationResponse.RecommendationItem> items) {
        if (!teachingActionOrchestrator.isActionable(decision)) {
            return;
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(decision.getTitle())
                .reason(firstNonBlank(decision.getSummary(), decision.getPrimaryReason()))
                .actionLabel(actionLabel(decision.getActionType(), decision.getActor()))
                .assignmentId(latestAssignmentId)
                .focusAbility(profile == null ? null : profile.getPrimaryAbilityFocus())
                .focusTags(teachingActionFocusTags(decision, profile))
                .evidenceProblemIds(collectEvidenceProblemIdsFromRefs(decision.getEvidenceRefs()))
                .learningHypothesis("系统已把多个教育信号编排为同一个下一步教学动作。")
                .expectedCompletionSignal(teachingActionCompletionSignal(decision))
                .strategy(teachingActionStrategy(decision.getActionType()))
                .riskLevel(decision.getRiskLevel())
                .fallbackAction(decision.getFallbackAction())
                .priority(Math.max(1, decision.getPriority() == null ? 1 : decision.getPriority() / 10))
                .build());
    }

    private void addRecurringMisconceptionRecommendation(StudentAbilityProfileResponse.RecurringMisconceptionSignal signal,
                                                         Long latestAssignmentId,
                                                         List<StudentRecommendationResponse.RecommendationItem> items) {
        if (!recurringMisconceptionAnalyzer.isActionable(signal)) {
            return;
        }
        boolean escalate = RecurringMisconceptionAnalyzer.STATUS_ESCALATE.equals(signal.getStatus());
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(escalate ? "先做教师复盘" : "修复反复误区")
                .reason(signal.getSummary())
                .actionLabel(escalate ? "带着证据找老师" : "先复盘")
                .assignmentId(latestAssignmentId)
                .problemId(signal.getEvidenceProblemIds() == null || signal.getEvidenceProblemIds().isEmpty()
                        ? null
                        : signal.getEvidenceProblemIds().get(0))
                .focusAbility(signal.getAbilityPoint())
                .focusTags(focusTags(signal))
                .evidenceProblemIds(signal.getEvidenceProblemIds())
                .learningHypothesis("学生可能不是只卡在当前题，而是在跨题重复命中同一误区。")
                .expectedCompletionSignal("能对比两道证据题，写出共同失败条件，并在下一次同能力题不再命中该细分错因。")
                .strategy(escalate ? STRATEGY_TEACHER_REVIEW_RECOMMENDED : STRATEGY_MISCONCEPTION_REPAIR)
                .riskLevel(escalate ? "HIGH" : "MEDIUM")
                .fallbackAction(escalate
                        ? "如果仍无法解释共同条件，请教师用更小样例点对点介入。"
                        : "如果复盘后仍命中同类误区，升级为教师复盘。")
                .priority(escalate ? 3 : 6)
                .build());
    }

    private boolean coversSource(StudentAbilityProfileResponse.TeachingActionDecision decision, String sourcePrefix) {
        if (!teachingActionOrchestrator.isActionable(decision) || decision.getSourceSignals() == null) {
            return false;
        }
        return decision.getSourceSignals().stream()
                .filter(Objects::nonNull)
                .anyMatch(source -> source.startsWith(sourcePrefix + ":"));
    }

    private String teachingActionStrategy(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case TeachingActionOrchestrator.ACTION_TEACHER_REVIEW -> STRATEGY_TEACHING_ACTION_TEACHER_REVIEW;
            case TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW -> STRATEGY_TEACHING_ACTION_SPIRAL_REVIEW;
            case TeachingActionOrchestrator.ACTION_REGRESSION_REPAIR -> STRATEGY_TEACHING_ACTION_REGRESSION_REPAIR;
            case TeachingActionOrchestrator.ACTION_INDEPENDENT_ATTEMPT -> STRATEGY_TEACHING_ACTION_INDEPENDENT_ATTEMPT;
            case TeachingActionOrchestrator.ACTION_SELF_EXPLANATION_PRACTICE -> STRATEGY_TEACHING_ACTION_SELF_EXPLANATION_PRACTICE;
            case TeachingActionOrchestrator.ACTION_POST_AC_REFLECTION,
                 TeachingActionOrchestrator.ACTION_TRANSFER_PRACTICE -> STRATEGY_TEACHING_ACTION_POST_AC_REFLECTION;
            default -> "TEACHING_ACTION_CONTINUE_DIAGNOSIS";
        };
    }

    private String actionLabel(String actionType, String actor) {
        if (TeachingActionOrchestrator.ACTOR_TEACHER.equals(actor)) {
            return "找老师复盘";
        }
        return switch (actionType == null ? "" : actionType) {
            case TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW -> "螺旋复盘";
            case TeachingActionOrchestrator.ACTION_REGRESSION_REPAIR -> "对比修复";
            case TeachingActionOrchestrator.ACTION_INDEPENDENT_ATTEMPT -> "先独立试";
            case TeachingActionOrchestrator.ACTION_SELF_EXPLANATION_PRACTICE -> "补证据";
            case TeachingActionOrchestrator.ACTION_POST_AC_REFLECTION -> "去复盘";
            case TeachingActionOrchestrator.ACTION_TRANSFER_PRACTICE -> "做迁移题";
            default -> "按建议做";
        };
    }

    private String teachingActionCompletionSignal(StudentAbilityProfileResponse.TeachingActionDecision decision) {
        String actionType = decision == null ? "" : decision.getActionType();
        return switch (actionType == null ? "" : actionType) {
            case TeachingActionOrchestrator.ACTION_SPIRAL_REVIEW ->
                    "能对比两道证据题，说出共同失败条件，并完成一次同能力最小练习或教师复盘记录。";
            case TeachingActionOrchestrator.ACTION_REGRESSION_REPAIR ->
                    "能指出最近通过和当前失败之间的一个关键差异，并用一次提交验证该差异。";
            case TeachingActionOrchestrator.ACTION_INDEPENDENT_ATTEMPT ->
                    "先不新增 Coach 追问或推荐，独立写出一个最小样例、一个修改假设，并完成一次提交或解释。";
            case TeachingActionOrchestrator.ACTION_SELF_EXPLANATION_PRACTICE ->
                    "能写出一个最小样例、预期/实际输出对比或变量轨迹，并说明下一次提交要验证什么。";
            case TeachingActionOrchestrator.ACTION_POST_AC_REFLECTION ->
                    "能写出关键修复、一个边界样例和复杂度判断，或完成同能力新题验证。";
            case TeachingActionOrchestrator.ACTION_TRANSFER_PRACTICE ->
                    "能完成同能力新题或说明新旧题共同边界、复杂度和错因差异。";
            case TeachingActionOrchestrator.ACTION_TEACHER_REVIEW ->
                    "教师或学生能留下复盘记录，说明来源信号、证据题和下一次验证动作。";
            default -> "完成该动作后，后续提交、解释或教师记录不再命中当前来源信号。";
        };
    }

    private List<String> teachingActionFocusTags(StudentAbilityProfileResponse.TeachingActionDecision decision,
                                                 StudentAbilityProfileResponse profile) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (profile != null && profile.getPrimaryAbilityFocus() != null && !profile.getPrimaryAbilityFocus().isBlank()) {
            tags.add(profile.getPrimaryAbilityFocus());
        }
        if (decision != null && decision.getSourceSignals() != null) {
            decision.getSourceSignals().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(tags::add);
        }
        if (tags.isEmpty()) {
            tags.add("教学动作");
        }
        return tags.stream().limit(6).toList();
    }

    private void addSelfExplanationRecommendation(StudentAbilityProfileResponse.SelfExplanationMasterySignal signal,
                                                  Long latestAssignmentId,
                                                  List<StudentRecommendationResponse.RecommendationItem> items) {
        if (signal == null) {
            return;
        }
        boolean teacherReview = SelfExplanationMasteryAnalyzer.STATUS_SAFETY_RISK.equals(signal.getStatus())
                || SelfExplanationMasteryAnalyzer.STATUS_NO_EVIDENCE.equals(signal.getStatus()) && signal.isNeedsTeacherAttention();
        if (!teacherReview && !selfExplanationMasteryAnalyzer.needsPractice(signal)) {
            return;
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(teacherReview ? "先看解释示范" : "补一条证据解释")
                .reason(signal.getSummary())
                .actionLabel(teacherReview ? "找老师示范" : "补证据")
                .assignmentId(latestAssignmentId)
                .focusAbility("自解释与验证")
                .focusTags(focusTags(signal))
                .evidenceProblemIds(List.of())
                .learningHypothesis(teacherReview
                        ? "学生可能把追问理解成索要答案或直接改法，需要教师示范证据层表达。"
                        : "学生可能有方向感，但还没有把提示转成自己的可验证证据。")
                .expectedCompletionSignal("能写出一个最小样例、预期/实际输出对比或变量轨迹，并说明下一次提交要验证什么。")
                .strategy(teacherReview ? STRATEGY_TEACHER_EXPLANATION_REVIEW : STRATEGY_SELF_EXPLANATION_PRACTICE)
                .riskLevel(teacherReview ? "HIGH" : "MEDIUM")
                .fallbackAction(teacherReview
                        ? "如果学生继续索要完整改法，请只让其描述输入特征和输出差异。"
                        : "如果仍只有方向或空泛确认，请降级为教师示范一个最小样例解释。")
                .priority(teacherReview ? 2 : 7)
                .build());
    }

    private void addAiDependencyRecommendation(StudentAbilityProfileResponse.AiDependencySignal signal,
                                               Long latestAssignmentId,
                                               List<StudentRecommendationResponse.RecommendationItem> items) {
        if (signal == null || !aiDependencyAnalyzer.isRisk(signal)) {
            return;
        }
        boolean teacherReview = AiDependencyAnalyzer.STATUS_TEACHER_FADE_REVIEW.equals(signal.getStatus());
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(teacherReview ? "先做撤支架复盘" : "先独立尝试一次")
                .reason(signal.getSummary())
                .actionLabel(teacherReview ? "找老师拆提示" : "先独立试")
                .assignmentId(latestAssignmentId)
                .focusAbility("自主解题与支架退场")
                .focusTags(focusTags(signal))
                .evidenceProblemIds(List.of())
                .learningHypothesis(teacherReview
                        ? "学生可能长期把 AI 支架当成下一步指令，需要教师示范如何拆提示并设置独立尝试边界。"
                        : "学生可能能跟着提示推进，但还缺少不新增提示时的最小独立尝试证据。")
                .expectedCompletionSignal("先不新增 Coach 追问或推荐，独立写出一个最小样例、一个修改假设，并完成一次提交或解释为什么暂不提交。")
                .strategy(teacherReview ? STRATEGY_TEACHER_SCAFFOLD_FADE_REVIEW : STRATEGY_INDEPENDENT_ATTEMPT)
                .riskLevel(teacherReview ? "HIGH" : "MEDIUM")
                .fallbackAction(teacherReview
                        ? "如果仍需要逐步提示，请教师只给问题拆解边界，不给下一步改法。"
                        : "如果独立尝试后仍无法推进，再回到 Coach，但只问一个最小验证问题。")
                .priority(teacherReview ? 1 : 4)
                .build());
    }

    private void addMasteryGrowthRecommendation(StudentAbilityProfileResponse.MasteryGrowthSignal signal,
                                                Long latestAssignmentId,
                                                List<StudentRecommendationResponse.RecommendationItem> items) {
        if (signal == null || !masteryGrowthAnalyzer.isRisk(signal)) {
            return;
        }
        boolean spiral = MasteryGrowthAnalyzer.STATUS_SPIRAL_REVIEW_NEEDED.equals(signal.getStatus());
        boolean regression = MasteryGrowthAnalyzer.STATUS_REGRESSION.equals(signal.getStatus());
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(spiral ? "做跨题螺旋复习" : regression ? "对比回退差异" : "修复成长停滞")
                .reason(signal.getSummary())
                .actionLabel(spiral ? "螺旋复盘" : regression ? "对比提交" : "最小复盘")
                .assignmentId(latestAssignmentId)
                .focusAbility(signal.getFocusAbility())
                .focusTags(focusTags(signal))
                .evidenceProblemIds(collectEvidenceProblemIdsFromRefs(signal.getEvidenceRefs()))
                .learningHypothesis(spiral
                        ? "学生可能不是卡在单题，而是在跨题重复命中同一能力缺口，需要把共同失败条件显性化。"
                        : regression
                                ? "学生可能一次局部修改破坏了原本已掌握的条件，需要对比通过提交和当前失败提交。"
                                : "学生可能持续试错但没有缩小假设，需要先把问题降到一个最小失败样例。")
                .expectedCompletionSignal(spiral
                        ? "能对比两道证据题，说出共同失败条件，并完成一次同能力最小练习或教师复盘记录。"
                        : regression
                                ? "能指出上次通过和当前失败之间的一个关键差异，并用一次提交验证该差异。"
                                : "能写出最小失败样例、预期/实际输出和一个只改一处的验证假设。")
                .strategy(spiral
                        ? STRATEGY_MASTERY_SPIRAL_REVIEW
                        : regression ? STRATEGY_MASTERY_REGRESSION_REPAIR : STRATEGY_MASTERY_PLATEAU_REPAIR)
                .riskLevel(spiral || regression ? "HIGH" : "MEDIUM")
                .fallbackAction(spiral
                        ? "如果仍无法说出共同失败条件，请教师用两道证据题做示范复盘。"
                        : regression
                                ? "如果无法定位差异，先回看最近一次 AC 的关键边界样例。"
                                : "如果复盘后仍连续失败，升级为跨题螺旋复习或教师介入。")
                .priority(spiral || regression ? 2 : 6)
                .build());
    }

    private void addPostAcTransferRecommendation(StudentTrajectoryResponse.PostAcTransferSignal signal,
                                                 List<StudentTrajectoryResponse.PostAcTransferSignal> allSignals,
                                                 Long latestAssignmentId,
                                                 List<StudentRecommendationResponse.RecommendationItem> items) {
        if (!postAcTransferAnalyzer.isPending(signal)) {
            return;
        }
        if (hasVerifiedSameFocus(signal, allSignals)) {
            return;
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("POST_AC_REFLECTION")
                .title("补一条通过后复盘")
                .reason(signal.getSummary())
                .actionLabel("去复盘")
                .assignmentId(latestAssignmentId)
                .problemId(signal.getProblemId())
                .problemTitle(signal.getProblemTitle())
                .focusAbility(signal.getTargetAbility())
                .focusTags(signal.getTargetTags() == null ? List.of() : signal.getTargetTags())
                .evidenceProblemIds(signal.getProblemId() == null ? List.of() : List.of(signal.getProblemId()))
                .learningHypothesis("学生已经把本题推进到 AC，但还缺少可迁移复盘证据。")
                .expectedCompletionSignal("能写出关键修复、一个边界样例和复杂度判断，或完成同能力新题验证。")
                .strategy(STRATEGY_POST_AC_REFLECTION)
                .riskLevel("MEDIUM")
                .fallbackAction("如果复盘只能复述答案，请先回答一个 Coach 追问，补足样例或变量证据。")
                .priority(4)
                .build());
    }

    private boolean hasVerifiedSameFocus(StudentTrajectoryResponse.PostAcTransferSignal pending,
                                         List<StudentTrajectoryResponse.PostAcTransferSignal> allSignals) {
        if (pending == null || allSignals == null || allSignals.isEmpty()) {
            return false;
        }
        return allSignals.stream()
                .filter(signal -> signal != null && PostAcTransferAnalyzer.PHASE_TRANSFER_VERIFIED.equals(signal.getPhase()))
                .anyMatch(signal -> sameTransferFocus(pending, signal));
    }

    private boolean sameTransferFocus(StudentTrajectoryResponse.PostAcTransferSignal left,
                                      StudentTrajectoryResponse.PostAcTransferSignal right) {
        if (left.getTargetAbility() != null && !left.getTargetAbility().isBlank()
                && Objects.equals(left.getTargetAbility(), right.getTargetAbility())) {
            return true;
        }
        Set<String> leftTags = new LinkedHashSet<>(left.getTargetTags() == null ? List.of() : left.getTargetTags());
        Set<String> rightTags = new LinkedHashSet<>(right.getTargetTags() == null ? List.of() : right.getTargetTags());
        leftTags.retainAll(rightTags);
        return !leftTags.isEmpty();
    }

    private List<String> focusTags(StudentAbilityProfileResponse.RecurringMisconceptionSignal signal) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (signal == null) {
            return List.of();
        }
        if (signal.getFineGrainedTag() != null && !signal.getFineGrainedTag().isBlank()) {
            tags.add(signal.getFineGrainedTag());
        }
        if (signal.getMisconceptionTag() != null && !signal.getMisconceptionTag().isBlank()) {
            tags.add(signal.getMisconceptionTag());
        }
        if (signal.getAbilityPoint() != null && !signal.getAbilityPoint().isBlank()) {
            tags.add(signal.getAbilityPoint());
        }
        return tags.stream().toList();
    }

    private List<String> focusTags(StudentAbilityProfileResponse.SelfExplanationMasterySignal signal) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("自解释与验证");
        if (signal != null && signal.getEvidenceTypes() != null) {
            signal.getEvidenceTypes().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(tags::add);
        }
        return tags.stream().toList();
    }

    private List<String> focusTags(StudentAbilityProfileResponse.AiDependencySignal signal) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("自主解题");
        tags.add("支架退场");
        if (signal != null && signal.getStatus() != null && !signal.getStatus().isBlank()) {
            tags.add(signal.getStatus());
        }
        return tags.stream().toList();
    }

    private List<String> focusTags(StudentAbilityProfileResponse.MasteryGrowthSignal signal) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("能力成长");
        if (signal == null) {
            return tags.stream().toList();
        }
        if (signal.getFineGrainedTag() != null && !signal.getFineGrainedTag().isBlank()) {
            tags.add(signal.getFineGrainedTag());
        }
        if (signal.getFocusTag() != null && !signal.getFocusTag().isBlank()) {
            tags.add(signal.getFocusTag());
        }
        if (signal.getFocusAbility() != null && !signal.getFocusAbility().isBlank()) {
            tags.add(signal.getFocusAbility());
        }
        if (signal.getStatus() != null && !signal.getStatus().isBlank()) {
            tags.add(signal.getStatus());
        }
        return tags.stream().toList();
    }

    private void addRedoRecommendation(StudentAbilityProfileResponse profile,
                                       List<Problem> problems,
                                       Set<Long> failedProblemIds,
                                       Long latestAssignmentId,
                                       List<StudentRecommendationResponse.RecommendationItem> items) {
        if (failedProblemIds.isEmpty()) {
            return;
        }
        Problem problem = problems.stream()
                .filter(item -> failedProblemIds.contains(item.getId()))
                .filter(item -> matchesProfileFocus(item, profile))
                .findFirst()
                .orElseGet(() -> problems.stream()
                        .filter(item -> failedProblemIds.contains(item.getId()))
                        .findFirst()
                        .orElse(null));
        if (problem == null) {
            return;
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REDO")
                .title("先重做最近卡住的题")
                .reason("这道题和当前能力画像最接近，适合用来验证错因是否真正被解决。")
                .actionLabel("去重做")
                .assignmentId(latestAssignmentId)
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .focusAbility(profile.getPrimaryAbilityFocus())
                .focusTags(focusTags(problem, profile))
                .evidenceProblemIds(List.of(problem.getId()))
                .learningHypothesis("学生可能已经定位到原题错因，需要用同题重做验证修复是否真的生效。")
                .expectedCompletionSignal("同题后续提交通过，或后续诊断不再命中当前关注错因。")
                .strategy(STRATEGY_REPAIR_SAME_PROBLEM)
                .riskLevel(failedProblemIds.size() >= 2 ? "MEDIUM" : "LOW")
                .fallbackAction("如果重做后仍命中同类错因，先降级到最小失败样例复盘，再继续改代码。")
                .priority(10)
                .build());
    }

    private void addNewPracticeRecommendation(StudentAbilityProfileResponse profile,
                                              List<Problem> problems,
                                              Set<Long> attemptedProblemIds,
                                              Long latestAssignmentId,
                                              List<StudentRecommendationResponse.RecommendationItem> items) {
        Problem problem = problems.stream()
                .filter(item -> !attemptedProblemIds.contains(item.getId()))
                .filter(item -> matchesProfileFocus(item, profile))
                .findFirst()
                .orElseGet(() -> problems.stream()
                        .filter(item -> !attemptedProblemIds.contains(item.getId()))
                        .findFirst()
                        .orElse(null));
        if (problem == null) {
            return;
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("NEXT_PROBLEM")
                .title("做一题同类新题")
                .reason("换一道题可以检查你是不是只修好了原题，而不是掌握了这类能力点。")
                .actionLabel("去练习")
                .assignmentId(latestAssignmentId)
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .focusAbility(profile.getPrimaryAbilityFocus())
                .focusTags(focusTags(problem, profile))
                .evidenceProblemIds(collectEvidenceProblemIds(profile))
                .learningHypothesis("学生可能已经修复原题，需要换同类新题验证能力是否能迁移。")
                .expectedCompletionSignal("新题提交通过，或能解释新题与旧题共同的边界、复杂度和错因差异。")
                .strategy(STRATEGY_TRANSFER_TO_NEW_PROBLEM)
                .riskLevel("MEDIUM")
                .fallbackAction("如果新题继续命中同类错因，先回到复盘任务，写出旧题到新题的迁移条件。")
                .priority(20)
                .build());
    }

    private void addReviewRecommendation(StudentAbilityProfileResponse profile,
                                         Long latestAssignmentId,
                                         List<StudentRecommendationResponse.RecommendationItem> items,
                                         boolean hasUnresolvedLearningSignal) {
        List<StudentAbilityProfileResponse.ReviewCard> reviewCards = safeReviewCards(profile.getReviewCards());
        StudentAbilityProfileResponse.ReviewCard primaryReviewCard = reviewCards.isEmpty() ? null : reviewCards.get(0);
        List<String> tags = new ArrayList<>();
        if (profile.getPrimaryAbilityFocus() != null && !profile.getPrimaryAbilityFocus().isBlank()) {
            tags.add(profile.getPrimaryAbilityFocus());
        }
        if (primaryReviewCard != null) {
            addIfPresent(tags, primaryReviewCard.getPrimaryFineGrainedTag());
            addIfPresent(tags, primaryReviewCard.getPrimaryIssueTag());
            addIfPresent(tags, primaryReviewCard.getAbilityPoint());
        }
        safeStats(profile.getKnowledgeFocus()).stream()
                .limit(2)
                .map(StudentAbilityProfileResponse.ProfileStat::getLabel)
                .forEach(tags::add);
        if (tags.isEmpty()) {
            tags.add("最小样例");
            tags.add("边界与复杂度");
        }
        items.add(StudentRecommendationResponse.RecommendationItem.builder()
                .type("REVIEW")
                .title(hasUnresolvedLearningSignal ? "先降级做证据复盘" : "做一次短复盘")
                .reason(buildReviewReason(profile, hasUnresolvedLearningSignal))
                .actionLabel(hasUnresolvedLearningSignal ? "先复盘" : "按提示复盘")
                .assignmentId(latestAssignmentId)
                .problemId(primaryReviewCard == null ? null : primaryReviewCard.getProblemId())
                .problemTitle(primaryReviewCard == null ? null : primaryReviewCard.getProblemTitle())
                .focusAbility(profile.getPrimaryAbilityFocus())
                .focusTags(tags.stream().distinct().toList())
                .evidenceProblemIds(collectEvidenceProblemIds(profile))
                .learningHypothesis(hasUnresolvedLearningSignal
                        ? "学生收到推荐后仍卡在同类错因，当前更需要补齐证据解释，而不是继续加题。"
                        : "学生需要把最近错因转化为可迁移的样例、边界和复杂度解释。")
                .expectedCompletionSignal("能写出一个最小样例，说明预期输出、当前错误原因和下一次提交要验证的假设。")
                .strategy(hasUnresolvedLearningSignal ? STRATEGY_STEP_DOWN_REVIEW : STRATEGY_REFLECTION_EVIDENCE)
                .riskLevel(hasUnresolvedLearningSignal ? "HIGH" : "MEDIUM")
                .fallbackAction(hasUnresolvedLearningSignal
                        ? "如果复盘后仍无法解释同类错因，建议带着最小样例请求教师介入。"
                        : "如果复盘缺少样例或解释，先回答 AI 教练追问再进入下一题。")
                .priority(hasUnresolvedLearningSignal ? 5 : 30)
                .build());
    }

    private boolean matchesProfileFocus(Problem problem, StudentAbilityProfileResponse profile) {
        if (problem == null || profile == null) {
            return false;
        }
        List<String> problemTags = problemTags(problem);
        if (problemTags.isEmpty()) {
            return false;
        }
        if (containsAny(problemTags, profile.getKnowledgeFocus())) {
            return true;
        }
        if (containsAny(problemTags, profile.getCommonMistakeFocus())) {
            return true;
        }
        if (containsAny(problemTags, profile.getBoundaryFocus())) {
            return true;
        }
        String ability = profile.getPrimaryAbilityFocus();
        return ability != null && problemTags.stream().anyMatch(tag -> ability.contains(tag) || tag.contains(ability));
    }

    private boolean containsAny(List<String> problemTags, List<StudentAbilityProfileResponse.ProfileStat> profileStats) {
        if (profileStats == null || profileStats.isEmpty()) {
            return false;
        }
        return profileStats.stream()
                .map(StudentAbilityProfileResponse.ProfileStat::getLabel)
                .filter(Objects::nonNull)
                .anyMatch(label -> problemTags.stream().anyMatch(tag -> tag.equalsIgnoreCase(label)));
    }

    private List<String> focusTags(Problem problem, StudentAbilityProfileResponse profile) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (profile.getPrimaryAbilityFocus() != null && !profile.getPrimaryAbilityFocus().isBlank()) {
            tags.add(profile.getPrimaryAbilityFocus());
        }
        problemTags(problem).stream().limit(3).forEach(tags::add);
        return List.copyOf(tags);
    }

    private List<String> problemTags(Problem problem) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addAll(tags, problem.getKnowledgePoints());
        addAll(tags, problem.getCommonMistakes());
        addAll(tags, problem.getBoundaryTypes());
        return List.copyOf(tags);
    }

    private void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(target::add);
    }

    private List<Long> collectEvidenceProblemIds(StudentAbilityProfileResponse profile) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        safeReviewCards(profile.getReviewCards()).stream()
                .map(StudentAbilityProfileResponse.ReviewCard::getProblemId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        collectEvidenceIds(ids, profile.getKnowledgeFocus());
        collectEvidenceIds(ids, profile.getCommonMistakeFocus());
        collectEvidenceIds(ids, profile.getBoundaryFocus());
        return ids.stream().limit(5).toList();
    }

    private List<StudentAbilityProfileResponse.ReviewCard> safeReviewCards(List<StudentAbilityProfileResponse.ReviewCard> reviewCards) {
        return reviewCards == null ? List.of() : reviewCards;
    }

    private List<Long> collectEvidenceProblemIdsFromRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        return refs.stream()
                .filter(Objects::nonNull)
                .map(ref -> ref.startsWith("problem:") ? ref.substring("problem:".length()) : "")
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();
    }

    private void collectEvidenceIds(Set<Long> ids, List<StudentAbilityProfileResponse.ProfileStat> stats) {
        safeStats(stats).stream()
                .flatMap(stat -> stat.getEvidenceProblemIds() == null ? List.<Long>of().stream() : stat.getEvidenceProblemIds().stream())
                .filter(Objects::nonNull)
                .forEach(ids::add);
    }

    private List<StudentAbilityProfileResponse.ProfileStat> safeStats(List<StudentAbilityProfileResponse.ProfileStat> stats) {
        return stats == null ? List.of() : stats;
    }

    private List<StudentRecommendationEvent> recommendationEvents(List<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return List.of();
        }
        return profileIds.stream()
                .filter(Objects::nonNull)
                .flatMap(profileId -> recommendationEventService.findEvents(profileId).stream())
                .filter(Objects::nonNull)
                .toList();
    }

    private Long latestAssignmentId(List<Submission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return null;
        }
        return submissions.stream()
                .filter(Objects::nonNull)
                .map(Submission::getAssignmentId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean hasUnresolvedLearningSignal(List<RecommendationEffectivenessResponse.ActionEvidenceSignal> actionEvidenceSignals,
                                                List<StudentRecommendationEvent> events) {
        if (actionEvidenceSignals != null && !actionEvidenceSignals.isEmpty()) {
            return actionEvidenceSignals.stream().anyMatch(recommendationActionEvidenceAnalyzer::isUnresolved);
        }
        if (events == null || events.isEmpty()) {
            return false;
        }
        return events.stream()
                .filter(event -> StudentRecommendationEventService.EVENT_SUBMITTED.equals(event.getEventType()))
                .filter(event -> !Submission.Verdict.ACCEPTED.name().equals(event.getFollowupVerdict()))
                .anyMatch(this::sameFocusIssue);
    }

    private boolean sameFocusIssue(StudentRecommendationEvent event) {
        if (event == null || event.getFocusTags() == null || event.getFocusTags().isBlank()) {
            return false;
        }
        String tags = event.getFocusTags();
        return containsFocusTag(tags, event.getFollowupFineGrainedTag())
                || containsFocusTag(tags, event.getFollowupIssueTag());
    }

    private boolean containsFocusTag(String focusTags, String tag) {
        return focusTags != null && tag != null && !tag.isBlank()
                && focusTags.toUpperCase().contains(tag.trim().toUpperCase());
    }

    private String buildReviewReason(StudentAbilityProfileResponse profile, boolean hasUnresolvedLearningSignal) {
        StudentAbilityProfileResponse.ReviewCard reviewCard = safeReviewCards(profile.getReviewCards()).stream()
                .findFirst()
                .orElse(null);
        if (hasUnresolvedLearningSignal) {
            return "最近推荐后的提交仍命中同类错因，先用最小样例和自己的解释补齐证据，再继续练新题。";
        }
        if (reviewCard != null) {
            String title = firstNonBlank(reviewCard.getProblemTitle(), "最近错题");
            String issue = firstNonBlank(reviewCard.getPrimaryFineGrainedTag(), reviewCard.getPrimaryIssueTag(), reviewCard.getAbilityPoint());
            String action = firstNonBlank(reviewCard.getNextAction(), "先写出最小失败样例和下一次自查点。");
            if (!issue.isBlank()) {
                return "最近在《" + title + "》暴露出“" + issue + "”，" + action;
            }
            return "最近在《" + title + "》还有未完成复盘，" + action;
        }
        if (profile.getLatestCoachInteraction() != null && profile.getLatestCoachInteraction().isPrompted()
                && !profile.getLatestCoachInteraction().isAnswered()) {
            return "你还有 AI 教练追问没有回答，先把自己的样例或判断依据补完整。";
        }
        if (profile.getTrendSignal() != null && !profile.getTrendSignal().isBlank()) {
            return profile.getTrendSignal();
        }
        return "把最近一次错误写成一个最小样例，再说明输入、预期输出和当前代码为什么不一致。";
    }

    private String buildSummary(StudentAbilityProfileResponse profile,
                                List<StudentRecommendationResponse.RecommendationItem> items) {
        if (items == null || items.isEmpty()) {
            return "暂时没有足够证据生成推荐，先完成一次提交。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_STEP_DOWN_REVIEW.equals(item.getStrategy()))) {
            return "检测到推荐后仍有同类错因，先降级到复盘和最小样例，再决定是否继续新题。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_TEACHER_REVIEW.equals(item.getStrategy()))) {
            return "系统已将多个风险信号编排为教师复盘动作，先带着证据处理最高优先级卡点。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_SPIRAL_REVIEW.equals(item.getStrategy()))) {
            return "系统已将长期停滞信号编排为螺旋复习，先复盘共同失败条件再继续加题。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_REGRESSION_REPAIR.equals(item.getStrategy()))) {
            return "系统已将成长回退或停滞编排为修复动作，先对比差异或最小样例。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_INDEPENDENT_ATTEMPT.equals(item.getStrategy()))) {
            return "系统已将支架风险编排为独立尝试，先不新增提示完成一次最小推进。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_SELF_EXPLANATION_PRACTICE.equals(item.getStrategy()))) {
            return "系统已将解释证据缺口编排为自解释练习，先补最小样例或变量轨迹。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHING_ACTION_POST_AC_REFLECTION.equals(item.getStrategy()))) {
            return "系统已将通过后证据缺口编排为复盘或迁移动作，先把 AC 转成可迁移解释。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHER_REVIEW_RECOMMENDED.equals(item.getStrategy()))) {
            return "检测到跨题或跨作业复发误区，先带着证据做教师复盘，再继续加新题。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHER_EXPLANATION_REVIEW.equals(item.getStrategy()))) {
            return "检测到自解释越界或长期无回答证据，先看教师示范，再继续改代码。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_TEACHER_SCAFFOLD_FADE_REVIEW.equals(item.getStrategy()))) {
            return "检测到长期高密度 AI 支架且缺少独立推进，先做教师撤支架复盘。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_MASTERY_SPIRAL_REVIEW.equals(item.getStrategy()))) {
            return "检测到同一能力点跨题反复停滞，先做螺旋复习，再继续新题。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_MASTERY_REGRESSION_REPAIR.equals(item.getStrategy()))) {
            return "检测到通过后回退，先对比上次通过和当前失败的差异。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_INDEPENDENT_ATTEMPT.equals(item.getStrategy()))) {
            return "检测到 AI 支架使用偏密，先完成一次不新增提示的最小独立尝试。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_MASTERY_PLATEAU_REPAIR.equals(item.getStrategy()))) {
            return "检测到近期成长停滞，先降到最小失败样例复盘。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_MISCONCEPTION_REPAIR.equals(item.getStrategy()))) {
            return "检测到跨题复发误区，先修复共同失败条件，再安排迁移练习。";
        }
        if (items.stream().anyMatch(item -> STRATEGY_SELF_EXPLANATION_PRACTICE.equals(item.getStrategy()))) {
            return "检测到解释证据还不稳定，先补最小样例或变量轨迹，再进入下一题。";
        }
        if (profile.getPrimaryAbilityFocus() != null && !profile.getPrimaryAbilityFocus().isBlank()) {
            return "推荐围绕“" + profile.getPrimaryAbilityFocus() + "”安排，先验证旧问题，再迁移到新题。";
        }
        return "推荐先用复盘任务补齐证据，再进入下一题。";
    }

    private void addIfPresent(List<String> target, String value) {
        if (target != null && value != null && !value.isBlank()) {
            target.add(value.trim());
        }
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
