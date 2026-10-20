package com.onlinejudge.submission.application;

import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiagnosticAgentService {

    private static final String AGENT_VERSION = "diagnostic-agent-v2";
    private static final String BASELINE_PROMPT_VERSION = "diagnostic-baseline-v1";
    private static final String AI_SOURCE = "MODEL_SCOPE_EXTERNAL_MODEL";
    private static final int MAX_CONTEXT_EVIDENCE_REFS = 16;

    private final DiagnosisEvidencePackageBuilder evidencePackageBuilder;
    private final AiReportService aiReportService;
    private final HintSafetyService hintSafetyService;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final StudentFeedbackViewAssembler studentFeedbackViewAssembler;

    @Autowired
    public DiagnosticAgentService(DiagnosisEvidencePackageBuilder evidencePackageBuilder,
                                  AiReportService aiReportService,
                                  HintSafetyService hintSafetyService,
                                  DiagnosisTaxonomy diagnosisTaxonomy,
                                  StudentFeedbackViewAssembler studentFeedbackViewAssembler) {
        this.evidencePackageBuilder = evidencePackageBuilder;
        this.aiReportService = aiReportService;
        this.hintSafetyService = hintSafetyService;
        this.diagnosisTaxonomy = diagnosisTaxonomy;
        this.studentFeedbackViewAssembler = studentFeedbackViewAssembler;
    }

    public DiagnosticAgentService(DiagnosisEvidencePackageBuilder evidencePackageBuilder,
                                  AiReportService aiReportService,
                                  HintSafetyService hintSafetyService,
                                  DiagnosisTaxonomy diagnosisTaxonomy) {
        this(
                evidencePackageBuilder,
                aiReportService,
                hintSafetyService,
                diagnosisTaxonomy,
                new StudentFeedbackViewAssembler()
        );
    }

    public AgentResult diagnose(Problem problem,
                                Submission submission,
                                List<SubmissionCaseResult> caseResults,
                                SubmissionAnalysisResponse baseline,
                                Assignment.HintPolicy hintPolicy) {
        return diagnose(problem, submission, caseResults, baseline, hintPolicy, null);
    }

    public AgentResult diagnose(Problem problem,
                                Submission submission,
                                List<SubmissionCaseResult> caseResults,
                                SubmissionAnalysisResponse baseline,
                                Assignment.HintPolicy hintPolicy,
                                DiagnosisEvidencePackage.HistoryEvidence historyEvidence) {
        return diagnose(problem, submission, caseResults, baseline, hintPolicy, historyEvidence, null);
    }

    public AgentResult diagnose(Problem problem,
                                Submission submission,
                                List<SubmissionCaseResult> caseResults,
                                SubmissionAnalysisResponse baseline,
                                Assignment.HintPolicy hintPolicy,
                                DiagnosisEvidencePackage.HistoryEvidence historyEvidence,
                                DiagnosisEvidencePackage.StudentLearningMemorySnapshot learningMemory) {
        Assignment.HintPolicy effectivePolicy = hintPolicy == null ? Assignment.HintPolicy.L2 : hintPolicy;
        DiagnosisEvidencePackage evidencePackage = evidencePackageBuilder.build(
                problem,
                submission,
                caseResults,
                baseline,
                effectivePolicy,
                historyEvidence,
                learningMemory
        );
        ModelStageResult modelStage = enhanceWithModel(problem, submission, baseline, evidencePackage);
        SubmissionAnalysisResponse enhanced = modelStage.analysis();
        if (isModelFailure(enhanced)) {
            enhanced.setIssueTags(List.of());
            enhanced.setFineGrainedTags(List.of());
            enhanced.setAbilityPoints(List.of());
            enhanced.setFocusPoints(List.of());
            enhanced.setFixDirections(List.of());
            enhanced = preserveContextEvidenceRefs(enhanced, evidencePackage);
            String traceSummary = buildTraceSummary(enhanced);
            enhanced.setDiagnosticTrace(traceSummary);
            enhanced.setAiInvocation(resolveInvocation(enhanced));
            enhanced.setStudentFeedbackView(studentFeedbackViewAssembler.assemble(enhanced));
            return new AgentResult(enhanced, evidencePackage, traceSummary);
        }
        enhanced.setIssueTags(diagnosisTaxonomy.normalizeIssueTags(enhanced.getIssueTags()));
        enhanced.setFineGrainedTags(diagnosisTaxonomy.normalizeFineGrainedTags(enhanced.getFineGrainedTags()));
        enhanced = preserveContextEvidenceRefs(enhanced, evidencePackage);
        enhanced = applyLowConfidenceGuard(enhanced);
        enhanced = applyTeacherCalibration(enhanced, evidencePackage);
        SubmissionAnalysisResponse.LearningTrajectorySignal trajectorySignal =
                resolveLearningTrajectory(enhanced, evidencePackage);
        enhanced.setLearningTrajectorySignal(trajectorySignal);
        if (trajectorySignal != null) {
            enhanced.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(
                    enhanced.getEvidenceRefs(),
                    singletonIfPresent(trajectorySignal.getEvidenceRef())
            )));
            enhanced.setProgressSignal(defaultIfBlank(enhanced.getProgressSignal(), trajectorySignal.getSummary()));
        }
        enhanced.setStudentHintPlan(resolveHintPlan(enhanced, effectivePolicy));
        enhanced.setLearningInterventionPlan(resolveInterventionPlan(enhanced, effectivePolicy, evidencePackage));
        enhanced.setLearningActionEvidence(resolveInitialActionEvidence(enhanced));
        enhanced = applyPreviousActionEvidence(enhanced, evidencePackage);
        enhanced.setLearningInterventionPlan(resolveInterventionPlan(enhanced, effectivePolicy, evidencePackage));
        enhanced = hintSafetyService.verifyAndRecord(enhanced, effectivePolicy);
        enhanced.setStudentFeedback(new StudentFeedbackAssembler(diagnosisTaxonomy)
                .assemble(enhanced, evidencePackage, false));
        String traceSummary = buildTraceSummary(enhanced);
        enhanced.setDiagnosticTrace(traceSummary);
        enhanced.setAiInvocation(resolveInvocation(enhanced));
        enhanced.setStudentFeedbackView(studentFeedbackViewAssembler.assemble(enhanced));
        return new AgentResult(enhanced, evidencePackage, traceSummary);
    }

    private SubmissionAnalysisResponse.StudentHintPlan resolveHintPlan(SubmissionAnalysisResponse analysis,
                                                                       Assignment.HintPolicy hintPolicy) {
        if (analysis == null) {
            return null;
        }
        SubmissionAnalysisResponse.StudentHintPlan existing = analysis.getStudentHintPlan();
        String primaryTag = primaryTag(analysis);
        String problemType = diagnosisTaxonomy.label(primaryTag);
        String teachingAction = diagnosisTaxonomy.teachingAction(primaryTag);
        List<String> evidenceRefs = DiagnosisListSupport.deduplicate(mergeLists(
                existing == null ? List.of() : existing.getEvidenceRefs(),
                analysis.getEvidenceRefs()
        ));
        String safeHint = defaultIfBlank(analysis.getStudentHint(), defaultHintForTag(primaryTag));
        boolean actionMismatch = existing != null
                && !defaultIfBlank(existing.getTeachingAction(), teachingAction).equals(teachingAction);
        return SubmissionAnalysisResponse.StudentHintPlan.builder()
                .hintLevel(defaultIfBlank(existing == null ? null : existing.getHintLevel(), effectiveHintPolicy(hintPolicy).name()))
                .problemType(defaultIfBlank(existing == null ? null : existing.getProblemType(), problemType))
                .evidenceAnchor(defaultIfBlank(existing == null ? null : existing.getEvidenceAnchor(), buildEvidenceAnchor(primaryTag, evidenceRefs)))
                .nextAction(actionMismatch ? defaultHintForTag(primaryTag)
                        : defaultIfBlank(existing == null ? null : existing.getNextAction(), safeHint))
                .coachQuestion(actionMismatch ? defaultCoachQuestion(primaryTag)
                        : defaultIfBlank(existing == null ? null : existing.getCoachQuestion(), defaultCoachQuestion(primaryTag)))
                .teachingAction(teachingAction)
                .evidenceRefs(evidenceRefs)
                .answerLeakRisk(defaultIfBlank(existing == null ? null : existing.getAnswerLeakRisk(), analysis.getAnswerLeakRisk()))
                .build();
    }

    private SubmissionAnalysisResponse.LearningInterventionPlan resolveInterventionPlan(SubmissionAnalysisResponse analysis,
                                                                                        Assignment.HintPolicy hintPolicy,
                                                                                        DiagnosisEvidencePackage evidencePackage) {
        if (analysis == null) {
            return null;
        }
        SubmissionAnalysisResponse.LearningInterventionPlan existing = analysis.getLearningInterventionPlan();
        String primaryTag = primaryTag(analysis);
        String teachingAction = analysis.getStudentHintPlan() == null
                ? diagnosisTaxonomy.teachingAction(primaryTag)
                : defaultIfBlank(analysis.getStudentHintPlan().getTeachingAction(), diagnosisTaxonomy.teachingAction(primaryTag));
        boolean actionContradicted = analysis.getLearningActionEvidence() != null
                && "CONTRADICTED".equals(analysis.getLearningActionEvidence().getExecutionStatus());
        if (!actionContradicted && hasUsableIntervention(existing) && interventionMatchesTeachingAction(existing, teachingAction)) {
            return normalizeIntervention(existing, analysis);
        }
        SubmissionAnalysisResponse.LearningTrajectorySignal trajectory = analysis.getLearningTrajectorySignal();
        String phase = trajectory == null ? "" : defaultIfBlank(trajectory.getPhase(), "");
        phase = actionEvidencePhase(analysis, phase);
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory = evidencePackage == null
                ? null
                : evidencePackage.getLearningMemory();
        List<String> evidenceRefs = DiagnosisListSupport.deduplicate(mergeLists(
                analysis.getEvidenceRefs(),
                trajectory == null ? List.of() : singletonIfPresent(trajectory.getEvidenceRef())
        ));
        InterventionTemplate template = interventionTemplate(primaryTag, teachingAction, phase);
        String goal = template.goal();
        if (trajectory != null && trajectory.getSummary() != null && !trajectory.getSummary().isBlank()
                && ("REPEATED_STUCK".equals(phase) || "REGRESSION".equals(phase))) {
            goal = trajectory.getSummary();
        }
        String studentTask = defaultIfBlank(trajectory == null ? null : trajectory.getNextFocus(), template.studentTask());
        if (memorySuggestsSmallerTask(memory) && !"REPEATED_STUCK".equals(phase) && !"REGRESSION".equals(phase)) {
            goal = "学生历史中存在重复卡点，本轮先缩小任务粒度，验证当前提交的一个最小证据。";
            studentTask = "先选一个最小失败样例或最短变量轨迹，只验证当前诊断证据，不要整体重写。";
            evidenceRefs = DiagnosisListSupport.deduplicate(mergeLists(
                    evidenceRefs,
                    memory == null ? List.of() : memory.getEvidenceRefs()
            ));
        }
        return SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                .interventionType(template.interventionType())
                .goal(goal)
                .studentTask(studentTask)
                .checkQuestion(template.checkQuestion())
                .completionSignal(template.completionSignal())
                .evidenceRefs(evidenceRefs)
                .estimatedMinutes(template.estimatedMinutes())
                .answerLeakRisk(defaultIfBlank(analysis.getAnswerLeakRisk(), "LOW"))
                .build();
    }

    private String actionEvidencePhase(SubmissionAnalysisResponse analysis, String currentPhase) {
        SubmissionAnalysisResponse.LearningActionEvidence evidence =
                analysis == null ? null : analysis.getLearningActionEvidence();
        if (evidence == null || evidence.getExecutionStatus() == null) {
            return currentPhase;
        }
        if ("CONTRADICTED".equals(evidence.getExecutionStatus())) {
            return "PREVIOUS_ACTION_CONTRADICTED";
        }
        if ("OBSERVED".equals(evidence.getExecutionStatus())
                && ("ACCEPTED_AFTER_FIX".equals(currentPhase) || "ACCEPTED_REVIEW".equals(currentPhase))) {
            return "PREVIOUS_ACTION_OBSERVED";
        }
        return currentPhase;
    }

    private boolean memorySuggestsSmallerTask(DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory) {
        if (memory == null) {
            return false;
        }
        boolean repeatedIssue = memory.getRecurringIssueTags() != null && memory.getRecurringIssueTags().stream()
                .anyMatch(stat -> stat.getCount() != null && stat.getCount() >= 3);
        boolean repeatedFine = memory.getRecurringFineGrainedTags() != null && memory.getRecurringFineGrainedTags().stream()
                .anyMatch(stat -> stat.getCount() != null && stat.getCount() >= 3);
        String trend = memory.getRecentTrend() == null ? "" : memory.getRecentTrend();
        String effect = memory.getInterventionEffect() == null ? "" : memory.getInterventionEffect();
        return repeatedIssue || repeatedFine || trend.contains("最近 3 次") || effect.contains("CONTRADICTED");
    }

    private boolean hasUsableIntervention(SubmissionAnalysisResponse.LearningInterventionPlan plan) {
        return plan != null
                && plan.getInterventionType() != null && !plan.getInterventionType().isBlank()
                && plan.getStudentTask() != null && !plan.getStudentTask().isBlank()
                && plan.getCheckQuestion() != null && !plan.getCheckQuestion().isBlank();
    }

    private boolean interventionMatchesTeachingAction(SubmissionAnalysisResponse.LearningInterventionPlan plan,
                                                      String teachingAction) {
        if (plan == null) {
            return false;
        }
        return interventionTemplate("", teachingAction, "").interventionType()
                .equals(defaultIfBlank(plan.getInterventionType(), ""));
    }

    private SubmissionAnalysisResponse.LearningActionEvidence resolveInitialActionEvidence(SubmissionAnalysisResponse analysis) {
        if (analysis == null) {
            return null;
        }
        SubmissionAnalysisResponse.LearningActionEvidence existing = analysis.getLearningActionEvidence();
        if (existing != null && existing.getExpectedActionType() != null && !existing.getExpectedActionType().isBlank()) {
            return existing;
        }
        SubmissionAnalysisResponse.LearningInterventionPlan plan = analysis.getLearningInterventionPlan();
        String expectedActionType = plan == null ? null : plan.getInterventionType();
        if (expectedActionType == null || expectedActionType.isBlank()) {
            return null;
        }
        return SubmissionAnalysisResponse.LearningActionEvidence.builder()
                .expectedActionType(expectedActionType)
                .executionStatus("NOT_OBSERVED")
                .observedEvidence("已生成学习动作，但还没有后续同题提交或学生回答可以验证是否执行。")
                .confidence(0.5)
                .evidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(
                        plan.getEvidenceRefs(),
                        analysis.getEvidenceRefs()
                )))
                .nextAdjustment("等待学生按学习动作完成一次可观察产出，再判断是否提高、降低或更换提示粒度。")
                .build();
    }

    private SubmissionAnalysisResponse applyPreviousActionEvidence(SubmissionAnalysisResponse analysis,
                                                                   DiagnosisEvidencePackage evidencePackage) {
        if (analysis == null || evidencePackage == null || evidencePackage.getHistory() == null) {
            return analysis;
        }
        DiagnosisEvidencePackage.HistoryEvidence history = evidencePackage.getHistory();
        if (history.getPreviousLearningActionStatus() == null || history.getPreviousLearningActionStatus().isBlank()) {
            return analysis;
        }
        List<String> actionRefs = DiagnosisListSupport.deduplicate(mergeLists(
                analysis.getEvidenceRefs(),
                history.getPreviousLearningActionEvidenceRefs()
        ));
        analysis.setEvidenceRefs(actionRefs);
        SubmissionAnalysisResponse.LearningActionEvidence current = analysis.getLearningActionEvidence();
        analysis.setLearningActionEvidence(SubmissionAnalysisResponse.LearningActionEvidence.builder()
                .expectedActionType(defaultIfBlank(history.getPreviousInterventionType(),
                        current == null ? null : current.getExpectedActionType()))
                .executionStatus(history.getPreviousLearningActionStatus())
                .observedEvidence(defaultIfBlank(history.getPreviousLearningActionSummary(),
                        current == null ? null : current.getObservedEvidence()))
                .confidence(history.getPreviousLearningActionConfidence() == null
                        ? (current == null ? 0.5 : current.getConfidence())
                        : history.getPreviousLearningActionConfidence())
                .evidenceRefs(actionRefs)
                .nextAdjustment(defaultIfBlank(history.getPreviousLearningActionNextAdjustment(),
                        current == null ? null : current.getNextAdjustment()))
                .build());
        if ("CONTRADICTED".equals(history.getPreviousLearningActionStatus())) {
            analysis.setProgressSignal(defaultIfBlank(
                    analysis.getProgressSignal(),
                    "Previous learning action was not observed; shrink the next action and consider teacher attention."
            ));
        }
        return analysis;
    }

    private SubmissionAnalysisResponse.LearningInterventionPlan normalizeIntervention(
            SubmissionAnalysisResponse.LearningInterventionPlan plan,
            SubmissionAnalysisResponse analysis) {
        return SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                .interventionType(defaultIfBlank(plan.getInterventionType(), "COLLECT_EVIDENCE"))
                .goal(defaultIfBlank(plan.getGoal(), "把当前判断变成一个可验证的小动作。"))
                .studentTask(defaultIfBlank(plan.getStudentTask(), "先写出一个最小样例和预期中间状态，再改代码。"))
                .checkQuestion(defaultIfBlank(plan.getCheckQuestion(), "这个动作完成后，你能看到哪一个具体证据变化？"))
                .completionSignal(defaultIfBlank(plan.getCompletionSignal(), "学生能给出样例、预期状态和实际状态的对比。"))
                .evidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(plan.getEvidenceRefs(), analysis.getEvidenceRefs())))
                .estimatedMinutes(plan.getEstimatedMinutes() == null || plan.getEstimatedMinutes() <= 0 ? 5 : plan.getEstimatedMinutes())
                .answerLeakRisk(defaultIfBlank(plan.getAnswerLeakRisk(), analysis.getAnswerLeakRisk()))
                .build();
    }

    private InterventionTemplate interventionTemplate(String primaryTag, String teachingAction, String phase) {
        if ("PREVIOUS_ACTION_CONTRADICTED".equals(phase)) {
            return new InterventionTemplate(
                    "MIN_CASE_TRACE",
                    "Previous learning action was not observed in the follow-up; shrink the task to one observable artifact.",
                    "Keep one minimal failing input and write the expected and actual key-variable changes before editing again.",
                    "Which variable, output, or state first diverges from the expectation in this minimal case?",
                    "The student can provide one minimal case and identify the first divergence between expected and actual state.",
                    8
            );
        }
        if ("PREVIOUS_ACTION_OBSERVED".equals(phase)) {
            return new InterventionTemplate(
                    "EXPLAIN_GENERALITY",
                    "Previous learning action has observable evidence, so move into review and transfer.",
                    "Review the action, the evidence that changed, and one boundary where the same idea should transfer.",
                    "If the boundary input changes, does your reason still hold?",
                    "The student can explain the action, evidence change, and one transferable boundary case.",
                    6
            );
        }
        if ("REPEATED_STUCK".equals(phase)) {
            return new InterventionTemplate(
                    "MIN_CASE_TRACE",
                    "停止连续试错，把问题缩小到一个能手推的失败样例。",
                    "只保留一个最小失败输入，写出关键变量的预期变化和实际变化，再决定改哪一行。",
                    "这个样例里，第一个偏离预期的变量是哪一个？",
                    "学生能提交一个最小样例，并说明预期状态与实际状态第一次分叉的位置。",
                    8
            );
        }
        if ("REGRESSION".equals(phase)) {
            return new InterventionTemplate(
                    "COMPARE_SUBMISSIONS",
                    "先定位哪次修改引入了回退，而不是继续叠加修复。",
                    "对比最近两次提交，只记录一个行为变化和它对应的代码差异。",
                    "哪一处改动让原本更好的行为变差了？",
                    "学生能指出一个最小 diff、一个受影响样例和变化前后的输出差异。",
                    10
            );
        }
        if ("ACCEPTED_AFTER_FIX".equals(phase) || "ACCEPTED_REVIEW".equals(phase)) {
            return new InterventionTemplate(
                    "EXPLAIN_GENERALITY",
                    "把通过结果沉淀成可迁移的解题经验。",
                    "用三句话复盘：关键修复是什么、复杂度是什么、哪个边界样例现在能解释清楚。",
                    "如果题目换一个边界输入，你的理由还成立吗？",
                    "学生能解释关键不变量、复杂度和一个边界样例。",
                    6
            );
        }
        return switch (teachingAction == null ? "" : teachingAction) {
            case "ASK_MIN_CASE" -> new InterventionTemplate(
                    "MIN_CASE",
                    "先构造最小可验证样例，避免凭感觉改代码。",
                    "写出一个最小输入、期望输出和你代码当前输出的对比。",
                    "这个最小样例为什么足以暴露当前问题？",
                    "学生能提供最小输入、期望输出、实际输出三项对照。",
                    5
            );
            case "TRACE_VARIABLES" -> new InterventionTemplate(
                    "VARIABLE_TRACE",
                    "把边界判断转成可观察的变量变化。",
                    "选一个最小样例，列出循环第一轮和最后一轮的关键变量值。",
                    "循环第一次和最后一次分别处理了哪个位置？",
                    "学生能列出关键变量表，并指出第一次偏离预期的位置。",
                    7
            );
            case "COMPARE_OUTPUT", "COMPARE_INPUT_SPEC" -> new InterventionTemplate(
                    "IO_COMPARE",
                    "把输入输出格式问题定位到具体字符或读取步骤。",
                    "逐字对比题面格式、实际输出和期望输出，只圈出第一处不同。",
                    "多了、少了或读错的是哪一个字符或字段？",
                    "学生能指出第一处格式差异或第一个读错的字段。",
                    5
            );
            case "COUNT_COMPLEXITY" -> new InterventionTemplate(
                    "COMPLEXITY_ESTIMATE",
                    "先估算最大规模下的操作次数，再讨论优化方向。",
                    "写出核心循环在最大输入下大约执行多少次，并和时限做比较。",
                    "当 n 取最大值时，最内层操作会发生多少次？",
                    "学生能给出量级估算，并判断当前做法是否可能通过。",
                    6
            );
            case "DEFINE_STATE", "TRACE_STATE" -> new InterventionTemplate(
                    "STATE_EXPLANATION",
                    "先说清楚状态含义，再检查转移或重置。",
                    "用一句话定义关键状态，并写出它在一个小样例中的初值和更新后值。",
                    "这个状态是否包含判断答案所需的全部信息？",
                    "学生能说明状态含义、初值和一次更新后的含义。",
                    8
            );
            case "CHECK_INVARIANT", "BUILD_COUNTEREXAMPLE" -> new InterventionTemplate(
                    "COUNTEREXAMPLE",
                    "验证当前策略是否真的能泛化。",
                    "构造一个最小反例，说明当前局部选择或样例规律在哪里失效。",
                    "什么输入会挑战你当前的选择依据？",
                    "学生能给出一个反例，并解释它破坏了哪条假设。",
                    8
            );
            case "CHECK_RUNTIME_GUARDS" -> new InterventionTemplate(
                    "RUNTIME_GUARD_CHECK",
                    "先确认崩溃风险是否已经被具体 guard 覆盖。",
                    "列出最可能触发越界、空值或除零的输入，并说明当前代码如何处理。",
                    "哪个极端输入最可能触发运行错误？",
                    "学生能指出风险输入、保护条件和保护后的行为。",
                    6
            );
            case "FIX_FIRST_COMPILER_ERROR" -> new InterventionTemplate(
                    "FIX_FIRST_COMPILER_ERROR",
                    "先处理第一条编译错误，避免同时大改逻辑。",
                    "只读第一条编译错误，定位对应行和符号，再做一次最小修改。",
                    "第一条错误信息指向的是哪个符号或结构？",
                    "学生能定位第一条编译错误并完成一次最小修复。",
                    4
            );
            default -> new InterventionTemplate(
                    "COLLECT_EVIDENCE",
                    "把模糊判断转成下一条可验证证据。",
                    "补充一个最小样例或一段运行证据，再判断主要错因。",
                    "你下一步准备用哪条证据确认这个判断？",
                    "学生能补充一条新的样例、输出对比或错误信息。",
                    5
            );
        };
    }

    private Assignment.HintPolicy effectiveHintPolicy(Assignment.HintPolicy hintPolicy) {
        return hintPolicy == null ? Assignment.HintPolicy.L2 : hintPolicy;
    }

    private String primaryTag(SubmissionAnalysisResponse analysis) {
        List<String> fineTags = analysis.getFineGrainedTags();
        List<String> issueTags = analysis.getIssueTags();
        if (fineTags != null && !fineTags.isEmpty()) {
            String contextual = contextualPrimaryTag(issueTags, fineTags, analysis.getEvidenceRefs());
            if (contextual != null) {
                return contextual;
            }
            return preferredFineTag(fineTags);
        }
        if (issueTags != null && !issueTags.isEmpty()) {
            return issueTags.get(0);
        }
        return "NEEDS_MORE_EVIDENCE";
    }

    private String contextualPrimaryTag(List<String> issueTags, List<String> fineTags, List<String> evidenceRefs) {
        List<String> safeIssues = issueTags == null ? List.of() : issueTags;
        List<String> safeRefs = evidenceRefs == null ? List.of() : evidenceRefs;
        if (safeIssues.contains("NEEDS_MORE_EVIDENCE") && fineTags.contains("PARTIAL_FIX_REGRESSION")) {
            return "PARTIAL_FIX_REGRESSION";
        }
        if (fineTags.contains("EMPTY_INPUT")) {
            return "EMPTY_INPUT";
        }
        if (fineTags.contains("STATE_RESET") && safeRefs.stream()
                .anyMatch(ref -> ref != null && ref.equals("problem:multi_case_state_before_loop"))) {
            return "STATE_RESET";
        }
        if (fineTags.contains("MAX_BOUNDARY") && safeRefs.stream()
                .anyMatch(ref -> ref != null && ref.equals("problem:max_bound_linear_loop"))) {
            return "MAX_BOUNDARY";
        }
        if (safeIssues.contains("RUNTIME_STABILITY")) {
            return "RUNTIME_STABILITY";
        }
        if (safeIssues.contains("SPACE_COMPLEXITY")
                && safeRefs.stream().anyMatch(ref -> ref != null && ref.equals("code:matrix_allocation"))) {
            return "SPACE_COMPLEXITY";
        }
        if (safeIssues.contains("IO_FORMAT") && hasStrongIoEvidence(safeRefs)) {
            if (fineTags.contains("OUTPUT_FORMAT_DETAIL")) {
                return "OUTPUT_FORMAT_DETAIL";
            }
            if (fineTags.contains("INPUT_PARSING")) {
                return "INPUT_PARSING";
            }
        }
        if (safeIssues.contains("TIME_COMPLEXITY")) {
            if (fineTags.contains("OVER_SIMULATION")) {
                return "OVER_SIMULATION";
            }
            if (fineTags.contains("BRUTE_FORCE_LIMIT")) {
                return "BRUTE_FORCE_LIMIT";
            }
            if (fineTags.contains("MAX_BOUNDARY")) {
                return "MAX_BOUNDARY";
            }
        }
        if (safeIssues.contains("ALGORITHM_STRATEGY")) {
            if (fineTags.contains("DP_STATE_DESIGN")) {
                return "DP_STATE_DESIGN";
            }
            if (fineTags.contains("IN_PLACE_STATE_PROGRESS")) {
                return "IN_PLACE_STATE_PROGRESS";
            }
            if (fineTags.contains("GREEDY_ASSUMPTION")) {
                return "GREEDY_ASSUMPTION";
            }
        }
        if (safeIssues.contains("STATE_TRANSITION") && fineTags.contains("IN_PLACE_STATE_PROGRESS")) {
            return "IN_PLACE_STATE_PROGRESS";
        }
        if (fineTags.contains("OFF_BY_ONE") && safeRefs.stream()
                .anyMatch(ref -> ref != null && (ref.equals("code:plus_minus_one")
                        || ref.equals("problem:inclusive_range_source_excludes_n")))) {
            return "OFF_BY_ONE";
        }
        return null;
    }

    private boolean hasStrongIoEvidence(List<String> evidenceRefs) {
        return evidenceRefs.stream().anyMatch(ref -> ref != null && (
                ref.equals("judge:whitespace_mismatch")
                        || ref.equals("problem:repeated_input_source_single_read")
        ));
    }

    private String preferredFineTag(List<String> fineTags) {
        if (fineTags == null || fineTags.isEmpty()) {
            return "NEEDS_MORE_EVIDENCE";
        }
        List<String> priority = List.of(
                "PARTIAL_FIX_REGRESSION",
                "IN_PLACE_STATE_PROGRESS",
                "DP_STATE_DESIGN",
                "GREEDY_ASSUMPTION",
                "STATE_RESET",
                "OVER_SIMULATION",
                "MAX_BOUNDARY",
                "EMPTY_INPUT",
                "DUPLICATE_CASE",
                "OUTPUT_FORMAT_DETAIL",
                "BRUTE_FORCE_LIMIT",
                "OFF_BY_ONE",
                "INITIAL_STATE",
                "SAMPLE_OVERFIT",
                "INPUT_PARSING"
        );
        for (String preferred : priority) {
            if (fineTags.contains(preferred)) {
                return preferred;
            }
        }
        return fineTags.get(0);
    }

    private String buildEvidenceAnchor(String primaryTag, List<String> evidenceRefs) {
        String label = diagnosisTaxonomy.label(primaryTag);
        if (evidenceRefs == null || evidenceRefs.isEmpty()) {
            return "当前判断主要来自评测结果和代码形态，先围绕“" + label + "”补充一个可验证证据。";
        }
        return "当前判断锚定在“" + label + "”，可先核对证据：" + evidenceRefs.get(0) + "。";
    }

    private String defaultHintForTag(String primaryTag) {
        return switch (primaryTag == null ? "NEEDS_MORE_EVIDENCE" : primaryTag) {
            case "SYNTAX_ERROR" -> "先只修第一条编译错误，不要同时大改逻辑。";
            case "IO_FORMAT", "OUTPUT_FORMAT_DETAIL", "INPUT_PARSING" -> "先逐字核对题面输入输出格式和你的读写顺序。";
            case "TIME_COMPLEXITY", "BRUTE_FORCE_LIMIT", "OVER_SIMULATION", "MAX_BOUNDARY" -> "先估算最大输入下核心操作会执行多少次。";
            case "STATE_TRANSITION", "DP_STATE_DESIGN" -> "先用一句话写清楚状态含义，再检查它依赖哪些上一状态。";
            case "IN_PLACE_STATE_PROGRESS" -> "先手推一次原地更新后，当前位置的新状态是否还需要继续处理。";
            case "GREEDY_ASSUMPTION", "ALGORITHM_STRATEGY" -> "先尝试构造一个能挑战当前策略的最小反例。";
            case "RUNTIME_STABILITY" -> "先检查数组下标、空值、除零和递归出口这些稳定性风险。";
            case "PARTIAL_FIX_REGRESSION" -> "先对比最近两次提交，找出是哪一处修改改变了失败现象。";
            default -> "先构造一个最小样例，手推你的关键变量变化。";
        };
    }

    private String defaultCoachQuestion(String primaryTag) {
        return switch (primaryTag == null ? "NEEDS_MORE_EVIDENCE" : primaryTag) {
            case "OFF_BY_ONE", "LOOP_BOUNDARY" -> "循环第一次和最后一次分别处理了哪个位置？";
            case "EMPTY_INPUT", "BOUNDARY_CONDITION" -> "最小规模输入下，答案变量最初来自哪里？";
            case "OUTPUT_FORMAT_DETAIL", "IO_FORMAT" -> "你的输出和题面要求相比，多了或少了哪个字符？";
            case "TIME_COMPLEXITY", "BRUTE_FORCE_LIMIT" -> "当输入取最大值时，核心循环大约执行多少次？";
            case "OVER_SIMULATION", "MAX_BOUNDARY" -> "如果输入达到题面最大规模，当前循环或模拟会执行多少次？";
            case "DP_STATE_DESIGN", "STATE_TRANSITION" -> "这个状态是否包含了题目判断所需的全部信息？";
            case "IN_PLACE_STATE_PROGRESS" -> "一次交换后，新换到当前位置的值是否已经满足你期望的不变量？";
            case "GREEDY_ASSUMPTION", "ALGORITHM_STRATEGY" -> "有没有一个很小的输入会让当前局部选择失效？";
            case "RUNTIME_STABILITY" -> "哪一种极端输入最可能触发越界、空值或除零？";
            case "PARTIAL_FIX_REGRESSION" -> "最近一次修改修好了什么，又可能引入了什么新问题？";
            default -> "你准备用哪个最小样例验证这个判断？";
        };
    }

    private ModelStageResult enhanceWithModel(Problem problem,
                                              Submission submission,
                                              SubmissionAnalysisResponse baseline,
                                              DiagnosisEvidencePackage evidencePackage) {
        try {
            SubmissionAnalysisResponse enhanced = aiReportService.enhanceSubmissionAnalysis(
                    problem,
                    submission,
                    baseline,
                    evidencePackage
            );
            boolean modelReturnedBaseline = enhanced == null
                    || enhanced == baseline
                    || (enhanced.getSourceType() != null && baseline != null
                    && enhanced.getSourceType().equals(baseline.getSourceType()));
            if (modelReturnedBaseline) {
                return new ModelStageResult(modelFailureFromBaseline(baseline, "MODEL_NOT_COMPLETED"));
            }
            return new ModelStageResult(enhanced);
        } catch (RuntimeException exception) {
            return new ModelStageResult(modelFailureFromBaseline(baseline, exception.getClass().getSimpleName()));
        }
    }

    private SubmissionAnalysisResponse modelFailureFromBaseline(SubmissionAnalysisResponse baseline, String reason) {
        if (baseline == null) {
            return SubmissionAnalysisResponse.builder()
                    .sourceType(AI_SOURCE)
                    .headline("AI 诊断暂不可用")
                    .summary("本次没有生成 AI 诊断，请稍后重试或先查看原始评测结果。")
                    .issueTags(List.of())
                    .fineGrainedTags(List.of())
                    .evidenceRefs(List.of())
                    .aiInvocation(modelFailureInvocation(null, reason))
                    .build();
        }
        return SubmissionAnalysisResponse.builder()
                .submissionId(baseline.getSubmissionId())
                .analysisSchemaVersion(baseline.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(baseline.getEvidenceSchemaVersion())
                .taxonomyVersion(baseline.getTaxonomyVersion())
                .sourceType(AI_SOURCE)
                .scenario(baseline.getScenario())
                .headline("AI 诊断暂不可用")
                .summary("本次没有生成 AI 诊断，请稍后重试或先查看原始评测结果。")
                .issueTags(List.of())
                .fineGrainedTags(List.of())
                .abilityPoints(List.of())
                .focusPoints(List.of())
                .fixDirections(List.of())
                .evidenceRefs(List.of())
                .studentHint("AI 诊断未生成，请稍后重试。")
                .teacherNote("外部模型未完成，本次未使用本地规则兜底。")
                .progressSignal("")
                .confidence(null)
                .uncertainty("外部模型未完成，本次未使用本地规则兜底。")
                .diagnosticTrace("model=failed reason=" + defaultIfBlank(reason, "MODEL_NOT_COMPLETED"))
                .basicLayerAdvice(List.of())
                .improvementLayerAdvice(List.of())
                .aiInvocation(modelFailureInvocation(baseline, reason))
                .answerLeakRisk(defaultIfBlank(baseline.getAnswerLeakRisk(), "LOW"))
                .lineIssues(List.of())
                .firstFailedCase(baseline.getFirstFailedCase())
                .reportMarkdown("")
                .build();
    }

    private SubmissionAnalysisResponse.AiInvocation modelFailureInvocation(SubmissionAnalysisResponse baseline,
                                                                           String reason) {
        return SubmissionAnalysisResponse.AiInvocation.builder()
                .provider("AI_PROVIDER")
                .model("unknown-model")
                .modelVersion("unknown-model")
                .promptVersion(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4)
                .agentVersion(AGENT_VERSION)
                .analysisSchemaVersion(baseline == null ? "diagnosis-v1" : baseline.getAnalysisSchemaVersion())
                .evidenceSchemaVersion(baseline == null ? DiagnosisEvidencePackage.SCHEMA_VERSION : baseline.getEvidenceSchemaVersion())
                .taxonomyVersion(baseline == null ? DiagnosisTaxonomy.TAXONOMY_VERSION : baseline.getTaxonomyVersion())
                .status("MODEL_FAILED")
                .fallbackUsed(false)
                .runtimeMode("diagnosis-report")
                .runtimeProfile(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD)
                .requestCompact(false)
                .failureStage("DIAGNOSIS_AND_ADVICE")
                .failureReason(defaultIfBlank(reason, "MODEL_NOT_COMPLETED"))
                .build();
    }

    private SubmissionAnalysisResponse applyLowConfidenceGuard(SubmissionAnalysisResponse analysis) {
        if (analysis == null || analysis.getConfidence() == null || analysis.getConfidence() >= 0.6) {
            return analysis;
        }
        analysis.setIssueTags(diagnosisTaxonomy.normalizeIssueTags(mergeLists(
                analysis.getIssueTags(),
                List.of("NEEDS_MORE_EVIDENCE")
        )));
        analysis.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(
                analysis.getEvidenceRefs(),
                List.of("agent:low_confidence")
        )));
        String lowConfidenceNote = "当前置信度较低，需要更多提交、样例或教师复核来确认错因。";
        if (analysis.getUncertainty() == null || analysis.getUncertainty().isBlank()) {
            analysis.setUncertainty(lowConfidenceNote);
        } else if (!analysis.getUncertainty().contains("置信度")) {
            analysis.setUncertainty(analysis.getUncertainty() + " " + lowConfidenceNote);
        }
        return analysis;
    }

    private SubmissionAnalysisResponse preserveContextEvidenceRefs(SubmissionAnalysisResponse analysis,
                                                                   DiagnosisEvidencePackage evidencePackage) {
        if (analysis == null) {
            return null;
        }
        analysis.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(
                analysis.getEvidenceRefs(),
                contextEvidenceRefs(evidencePackage)
        )).stream().limit(MAX_CONTEXT_EVIDENCE_REFS).toList());
        return analysis;
    }

    private List<String> contextEvidenceRefs(DiagnosisEvidencePackage evidencePackage) {
        if (evidencePackage == null) {
            return List.of();
        }
        List<String> refs = List.of();
        DiagnosisEvidencePackage.SubmissionEvidence submission = evidencePackage.getSubmission();
        if (submission != null) {
            if (submission.getId() != null) {
                refs = mergeLists(refs, List.of("submission:" + submission.getId()));
            }
            String verdict = normalizeVerdict(submission.getVerdict()).toLowerCase();
            if (!"unknown".equals(verdict)) {
                refs = mergeLists(refs, List.of("verdict:" + verdict));
            }
            if (submission.getSourceCodeLineCount() != null) {
                refs = mergeLists(refs, List.of("source:lines:" + submission.getSourceCodeLineCount()));
            }
        }
        DiagnosisEvidencePackage.ProblemEvidence problem = evidencePackage.getProblem();
        if (problem != null && problem.getId() != null) {
            refs = mergeLists(refs, List.of("problem:" + problem.getId()));
        }
        DiagnosisEvidencePackage.JudgeFacts judgeFacts = evidencePackage.getJudgeFacts();
        if (judgeFacts != null) {
            if (judgeFacts.getPassedCount() != null && judgeFacts.getTotalCount() != null) {
                refs = mergeLists(refs, List.of("judge:cases:" + judgeFacts.getPassedCount() + "/" + judgeFacts.getTotalCount()));
            }
            if (judgeFacts.getFirstFailedCase() != null) {
                refs = mergeLists(refs, List.of("judge:first_failed_case:" + judgeFacts.getFirstFailedCase().getTestCaseNumber()));
            } else if (judgeFacts.getCaseResultsSummary() != null) {
                Integer firstFailedCaseNumber = judgeFacts.getCaseResultsSummary().stream()
                        .filter(summary -> summary != null && !Boolean.TRUE.equals(summary.getPassed()))
                        .map(DiagnosisEvidencePackage.CaseSummary::getTestCaseNumber)
                        .filter(testCaseNumber -> testCaseNumber != null)
                        .findFirst()
                        .orElse(null);
                if (firstFailedCaseNumber != null) {
                    refs = mergeLists(refs, List.of("judge:first_failed_case:" + firstFailedCaseNumber));
                }
            }
            if (Boolean.TRUE.equals(judgeFacts.getHiddenFailureObserved())) {
                refs = mergeLists(refs, List.of("judge:hidden_failure"));
            }
            if (judgeFacts.getCompileOutput() != null && !judgeFacts.getCompileOutput().isBlank()) {
                refs = mergeLists(refs, List.of("judge:compile_output"));
            }
            if (judgeFacts.getRuntimeErrorMessage() != null && !judgeFacts.getRuntimeErrorMessage().isBlank()) {
                refs = mergeLists(refs, List.of("judge:runtime_error"));
            }
        }
        DiagnosisEvidencePackage.HistoryEvidence history = evidencePackage.getHistory();
        if (history != null) {
            refs = mergeLists(refs, singletonIfPresent(history.getRepeatedFineGrainedTag() == null
                    ? null
                    : "history:repeated_fine_tag:" + history.getRepeatedFineGrainedTag()));
            refs = mergeLists(refs, singletonIfPresent(history.getRepeatedIssueTag() == null
                    ? null
                    : "history:repeated_issue_tag:" + history.getRepeatedIssueTag()));
            if (history.getTransitionSignal() != null && !history.getTransitionSignal().isBlank()) {
                refs = mergeLists(refs, List.of("history:transition"));
            }
            refs = mergeLists(refs, history.getPreviousLearningActionEvidenceRefs());
        }
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory = evidencePackage.getLearningMemory();
        if (memory != null) {
            refs = mergeLists(refs, memory.getEvidenceRefs());
        }
        DiagnosisEvidencePackage.PolicyEvidence policy = evidencePackage.getPolicy();
        if (policy != null && policy.getHintPolicy() != null && !policy.getHintPolicy().isBlank()) {
            refs = mergeLists(refs, List.of("policy:" + policy.getHintPolicy()));
        }
        return DiagnosisListSupport.deduplicate(refs);
    }

    private SubmissionAnalysisResponse applyTeacherCalibration(SubmissionAnalysisResponse analysis,
                                                               DiagnosisEvidencePackage evidencePackage) {
        if (analysis == null || evidencePackage == null || evidencePackage.getLearningMemory() == null) {
            return analysis;
        }
        DiagnosisEvidencePackage.TeacherCalibrationPattern pattern =
                selectTeacherCalibrationPattern(evidencePackage.getLearningMemory().getTeacherCalibrationPatterns(), analysis);
        if (pattern == null) {
            return analysis;
        }
        List<String> calibrationRefs = pattern.getEvidenceRefs() == null ? List.of() : pattern.getEvidenceRefs();
        boolean correctedPresent = containsTag(analysis, pattern.getCorrectedIssueTag(), pattern.getCorrectedFineGrainedTag());
        boolean originalPresent = containsTag(analysis, pattern.getOriginalIssueTag(), pattern.getOriginalFineGrainedTag());
        String status;
        double confidenceAdjustment = 0.0;
        boolean needsTeacherReview = false;
        String recommendedAction;
        if (correctedPresent) {
            status = "SUPPORTED";
            recommendedAction = "当前诊断已参考教师校正方向，继续用当前提交证据验证即可。";
            analysis.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(analysis.getEvidenceRefs(), calibrationRefs)));
        } else if (originalPresent && hasCorrectedTag(pattern)) {
            status = "CONFLICT_NEEDS_REVIEW";
            confidenceAdjustment = -0.15;
            needsTeacherReview = true;
            recommendedAction = "这次诊断命中了教师曾修正过的原始错因，请教师优先复核当前证据是否应改判为教师修正标签。";
            analysis.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(analysis.getEvidenceRefs(), calibrationRefs)));
            analysis.setConfidence(adjustConfidence(analysis.getConfidence(), confidenceAdjustment));
            analysis.setUncertainty(appendSentence(
                    analysis.getUncertainty(),
                    "教师校准提示：历史中该类诊断曾被教师修正，当前结论需要复核后再作为稳定错因。"
            ));
            analysis.setTeacherNote(appendSentence(
                    analysis.getTeacherNote(),
                    "教师校准冲突：系统仍命中曾被修正的原始标签，建议核对当前证据是否更符合 "
                            + firstNonBlank(pattern.getCorrectedFineGrainedTag(), pattern.getCorrectedIssueTag()) + "。"
            ));
        } else {
            status = "APPLIED";
            recommendedAction = "存在相关教师校正记忆，当前诊断应把教师修正标签作为辅助观察方向。";
            analysis.setEvidenceRefs(DiagnosisListSupport.deduplicate(mergeLists(analysis.getEvidenceRefs(), calibrationRefs)));
        }
        analysis.setTeacherCalibrationSignal(SubmissionAnalysisResponse.TeacherCalibrationSignal.builder()
                .status(status)
                .summary(teacherCalibrationSummary(status, pattern))
                .originalIssueTag(blankToNull(pattern.getOriginalIssueTag()))
                .originalFineGrainedTag(blankToNull(pattern.getOriginalFineGrainedTag()))
                .correctedIssueTag(blankToNull(pattern.getCorrectedIssueTag()))
                .correctedFineGrainedTag(blankToNull(pattern.getCorrectedFineGrainedTag()))
                .correctionCount(pattern.getCorrectionCount())
                .confidenceAdjustment(confidenceAdjustment)
                .evidenceRefs(calibrationRefs)
                .recommendedAction(recommendedAction)
                .needsTeacherReview(needsTeacherReview)
                .build());
        return analysis;
    }

    private DiagnosisEvidencePackage.TeacherCalibrationPattern selectTeacherCalibrationPattern(
            List<DiagnosisEvidencePackage.TeacherCalibrationPattern> patterns,
            SubmissionAnalysisResponse analysis) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        return patterns.stream()
                .filter(pattern -> pattern != null && hasCorrectedTag(pattern))
                .sorted((left, right) -> Integer.compare(
                        teacherCalibrationScore(right, analysis),
                        teacherCalibrationScore(left, analysis)))
                .findFirst()
                .orElse(null);
    }

    private int teacherCalibrationScore(DiagnosisEvidencePackage.TeacherCalibrationPattern pattern,
                                        SubmissionAnalysisResponse analysis) {
        int score = 0;
        if (containsTag(analysis, pattern.getCorrectedIssueTag(), pattern.getCorrectedFineGrainedTag())) {
            score += 80;
        }
        if (containsTag(analysis, pattern.getOriginalIssueTag(), pattern.getOriginalFineGrainedTag())) {
            score += 70;
        }
        long count = pattern.getCorrectionCount() == null ? 0L : pattern.getCorrectionCount();
        score += (int) Math.min(20L, count * 5L);
        return score;
    }

    private boolean containsTag(SubmissionAnalysisResponse analysis, String issueTag, String fineTag) {
        return tagListContains(analysis == null ? null : analysis.getIssueTags(), issueTag)
                || tagListContains(analysis == null ? null : analysis.getFineGrainedTags(), fineTag);
    }

    private boolean tagListContains(List<String> tags, String tag) {
        if (tags == null || tag == null || tag.isBlank()) {
            return false;
        }
        return tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.equalsIgnoreCase(tag));
    }

    private boolean hasCorrectedTag(DiagnosisEvidencePackage.TeacherCalibrationPattern pattern) {
        return pattern != null
                && ((pattern.getCorrectedIssueTag() != null && !pattern.getCorrectedIssueTag().isBlank())
                || (pattern.getCorrectedFineGrainedTag() != null && !pattern.getCorrectedFineGrainedTag().isBlank()));
    }

    private Double adjustConfidence(Double confidence, double adjustment) {
        double base = confidence == null ? 0.62 : confidence;
        return Math.max(0.35, Math.min(0.95, base + adjustment));
    }

    private String teacherCalibrationSummary(String status, DiagnosisEvidencePackage.TeacherCalibrationPattern pattern) {
        String corrected = firstNonBlank(pattern.getCorrectedFineGrainedTag(), pattern.getCorrectedIssueTag());
        String original = firstNonBlank(pattern.getOriginalFineGrainedTag(), pattern.getOriginalIssueTag());
        long count = pattern.getCorrectionCount() == null ? 0L : pattern.getCorrectionCount();
        return switch (status) {
            case "SUPPORTED" -> "教师校准支持当前诊断：历史中 " + count + " 次校正指向 " + corrected + "。";
            case "CONFLICT_NEEDS_REVIEW" -> "教师校准与当前诊断存在冲突：历史曾将 " + original + " 修正为 " + corrected + "。";
            default -> "教师校准已作为辅助观察方向：优先关注 " + corrected + "。";
        };
    }

    private String appendSentence(String current, String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return sentence;
        }
        if (current.contains(sentence)) {
            return current;
        }
        return current + " " + sentence;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private SubmissionAnalysisResponse.LearningTrajectorySignal resolveLearningTrajectory(
            SubmissionAnalysisResponse analysis,
            DiagnosisEvidencePackage evidencePackage) {
        String currentVerdict = normalizeVerdict(evidencePackage == null || evidencePackage.getSubmission() == null
                ? null
                : evidencePackage.getSubmission().getVerdict());
        DiagnosisEvidencePackage.HistoryEvidence history = evidencePackage == null ? null : evidencePackage.getHistory();
        String previousVerdict = normalizeVerdict(history == null ? null : history.getPreviousVerdict());

        if (isUnknown(previousVerdict)) {
            if (isAccepted(currentVerdict)) {
                return trajectory(
                        "ACCEPTED_REVIEW",
                        "accepted review",
                        "history:first_attempt",
                        "This accepted submission should move into review and generalization.",
                        "Ask the student to explain the invariant, complexity, and one boundary case.",
                        false
                );
            }
            return trajectory(
                    "FIRST_ATTEMPT",
                    "first observable failure",
                    "history:first_attempt",
                    "No reliable learning trajectory is available yet; diagnose from the current judge facts first.",
                    "Collect a minimal example or the first compiler/runtime evidence before judging deeper causes.",
                    false
            );
        }

        long repeatedCount = maxRepeatedCount(history);
        if (isAccepted(currentVerdict) && !isAccepted(previousVerdict)) {
            return trajectory(
                    "ACCEPTED_AFTER_FIX",
                    "accepted after fix",
                    "history:accepted_after_fix",
                    "The latest submission moved from a failing state to accepted; this is a good moment for reflection.",
                    "Ask the student which condition, boundary, or guard the fix changed.",
                    false
            );
        }
        if (isAccepted(previousVerdict) && !isAccepted(currentVerdict)) {
            return trajectory(
                    "REGRESSION",
                    "regression after accepted",
                    "history:verdict_regression",
                    "A previously accepted solution now fails, so the next step is to compare the latest edits.",
                    "Review the smallest diff and identify which changed assumption broke the passing behavior.",
                    true
            );
        }
        if ("COMPILATION_ERROR".equals(previousVerdict) && !"COMPILATION_ERROR".equals(currentVerdict)) {
            return trajectory(
                    "FIXED_COMPILATION",
                    "compilation fixed",
                    "history:fixed_compilation",
                    "The student has moved past compilation; focus should shift from syntax to runtime or correctness evidence.",
                    "Acknowledge the compile fix, then build a minimal check for the current verdict.",
                    false
            );
        }
        if ("RUNTIME_ERROR".equals(previousVerdict)
                && ("WRONG_ANSWER".equals(currentVerdict)
                || "TIME_LIMIT_EXCEEDED".equals(currentVerdict)
                || "MEMORY_LIMIT_EXCEEDED".equals(currentVerdict))) {
            return trajectory(
                    "RUNTIME_FIXED_CORRECTNESS_REMAINS",
                    "runtime fixed, correctness remains",
                    "history:runtime_fixed_correctness_remains",
                    "The runtime crash may be fixed, but correctness or complexity still needs a separate check.",
                    "Review which runtime guard worked, then switch to evidence for the current verdict.",
                    false
            );
        }
        if (currentVerdict.equals(previousVerdict) && !isAccepted(currentVerdict)) {
            boolean repeatedStuck = repeatedCount >= 3;
            return trajectory(
                    repeatedStuck ? "REPEATED_STUCK" : "STILL_STUCK",
                    repeatedStuck ? "repeated stuck state" : "same failure remains",
                    repeatedStuck ? "history:repeated_stuck" : "history:still_stuck",
                    repeatedStuck
                            ? "The student has stayed in the same failure category several times; reduce the task size before more edits."
                            : "The failure category is unchanged, so the recent fix has not reached the core evidence yet.",
                    repeatedStuck
                            ? "Ask for one minimal trace and consider teacher intervention on the mistaken assumption."
                            : "Compare the two latest submissions on one failing example and watch the key variable.",
                    repeatedStuck
            );
        }
        if (isRegression(previousVerdict, currentVerdict)) {
            return trajectory(
                    "REGRESSION",
                    "partial fix regression",
                    "history:verdict_regression",
                    "The current verdict is less stable than the previous one, suggesting a partial fix introduced a new issue.",
                    "Compare the latest edits and identify which changed behavior created the new failure.",
                    true
            );
        }
        if (isProgress(previousVerdict, currentVerdict)) {
            return trajectory(
                    "PROGRESSING",
                    "failure stage progressing",
                    "history:verdict_progress",
                    "The failure stage has moved forward, but correctness is not closed yet.",
                    "Keep the fixed part stable and continue with evidence for the current verdict.",
                    false
            );
        }
        return trajectory(
                "CURRENT_ATTEMPT",
                "current attempt",
                "history:current_attempt",
                "History exists, but it is not enough to classify clear progress or regression.",
                "Collect one piece of evidence that explains the current verdict.",
                false
        );
    }

    private SubmissionAnalysisResponse.LearningTrajectorySignal trajectory(String phase,
                                                                           String label,
                                                                           String evidenceRef,
                                                                           String summary,
                                                                           String nextFocus,
                                                                           boolean needsTeacherAttention) {
        return SubmissionAnalysisResponse.LearningTrajectorySignal.builder()
                .phase(phase)
                .label(label)
                .evidenceRef(evidenceRef)
                .summary(summary)
                .nextFocus(nextFocus)
                .needsTeacherAttention(needsTeacherAttention)
                .build();
    }

    private List<String> mergeLists(List<String> left, List<String> right) {
        return DiagnosisListSupport.merge(left, right);
    }

    private List<String> singletonIfPresent(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }

    private String buildTraceSummary(SubmissionAnalysisResponse analysis) {
        int evidenceRefCount = analysis == null || analysis.getEvidenceRefs() == null ? 0 : analysis.getEvidenceRefs().size();
        String source = analysis == null ? "UNKNOWN" : analysis.getSourceType();
        String modelStage = isModelFailure(analysis)
                ? "model=failed"
                : "model=completed";
        String trajectory = analysis == null || analysis.getLearningTrajectorySignal() == null
                ? ""
                : " trajectory=" + analysis.getLearningTrajectorySignal().getPhase();
        return AGENT_VERSION + " evidenceRefs=" + evidenceRefCount
                + " source=" + source
                + " " + modelStage
                + trajectory;
    }

    private SubmissionAnalysisResponse.AiInvocation resolveInvocation(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.AiInvocation existing = analysis == null ? null : analysis.getAiInvocation();
        String status = defaultIfBlank(
                existing == null ? null : existing.getStatus(),
                "MODEL_COMPLETED"
        );
        return SubmissionAnalysisResponse.AiInvocation.builder()
                .provider(defaultIfBlank(existing == null ? null : existing.getProvider(), "AI_PROVIDER"))
                .model(defaultIfBlank(existing == null ? null : existing.getModel(), "unknown-model"))
                .modelVersion(defaultIfBlank(existing == null ? null : existing.getModelVersion(), "unknown-model"))
                .promptVersion(defaultIfBlank(existing == null ? null : existing.getPromptVersion(), BASELINE_PROMPT_VERSION))
                .agentVersion(AGENT_VERSION)
                .analysisSchemaVersion(defaultIfBlank(
                        existing == null ? null : existing.getAnalysisSchemaVersion(),
                        analysis == null ? "diagnosis-v1" : analysis.getAnalysisSchemaVersion()
                ))
                .evidenceSchemaVersion(defaultIfBlank(
                        existing == null ? null : existing.getEvidenceSchemaVersion(),
                        analysis == null ? DiagnosisEvidencePackage.SCHEMA_VERSION : analysis.getEvidenceSchemaVersion()
                ))
                .taxonomyVersion(defaultIfBlank(
                        existing == null ? null : existing.getTaxonomyVersion(),
                        analysis == null ? DiagnosisTaxonomy.TAXONOMY_VERSION : analysis.getTaxonomyVersion()
                ))
                .status(status)
                .fallbackUsed(false)
                .runtimeMode(defaultIfBlank(
                        existing == null ? null : existing.getRuntimeMode(),
                        ""
                ))
                .runtimeProfile(defaultIfBlank(existing == null ? null : existing.getRuntimeProfile(), "standard"))
                .requestBytes(existing == null || existing.getRequestBytes() == null ? 0 : existing.getRequestBytes())
                .requestCompact(existing != null && Boolean.TRUE.equals(existing.getRequestCompact()))
                .failureStage(defaultIfBlank(existing == null ? null : existing.getFailureStage(), ""))
                .failureReason(defaultIfBlank(existing == null ? null : existing.getFailureReason(), ""))
                .transportMode(defaultIfBlank(existing == null ? null : existing.getTransportMode(), ""))
                .streamChunkCount(existing == null ? null : existing.getStreamChunkCount())
                .streamContentChunkCount(existing == null ? null : existing.getStreamContentChunkCount())
                .streamReasoningChunkCount(existing == null ? null : existing.getStreamReasoningChunkCount())
                .streamInvalidChunkCount(existing == null ? null : existing.getStreamInvalidChunkCount())
                .streamFinishReason(defaultIfBlank(existing == null ? null : existing.getStreamFinishReason(), ""))
                .streamFallbackRetryUsed(existing == null ? null : existing.getStreamFallbackRetryUsed())
                .standardLibraryNavigationEnabled(existing == null ? null : existing.getStandardLibraryNavigationEnabled())
                .standardLibraryNavigationStatus(existing == null ? null : existing.getStandardLibraryNavigationStatus())
                .standardLibraryNavigationSelectedCount(existing == null ? null : existing.getStandardLibraryNavigationSelectedCount())
                .standardLibraryNavigationFailureReason(existing == null ? null : existing.getStandardLibraryNavigationFailureReason())
                .adviceGenerationStatus(existing == null ? null : existing.getAdviceGenerationStatus())
                .adviceGenerationFailureReason(existing == null ? null : existing.getAdviceGenerationFailureReason())
                .basicAdviceCount(existing == null ? null : existing.getBasicAdviceCount())
                .improvementAdviceCount(existing == null ? null : existing.getImprovementAdviceCount())
                .advicePromptVersion(existing == null ? null : existing.getAdvicePromptVersion())
                .build();
    }

    private boolean isModelFailure(SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.AiInvocation invocation = analysis == null ? null : analysis.getAiInvocation();
        String status = invocation == null ? "" : invocation.getStatus();
        return "MODEL_FAILED".equalsIgnoreCase(status);
    }

    private String defaultIfBlank(String candidate, String defaultValue) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return defaultValue == null ? "" : defaultValue;
    }

    private String normalizeVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = verdict.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "AC", "ACCEPTED" -> "ACCEPTED";
            case "WA", "WRONG_ANSWER", "PRESENTATION_ERROR" -> "WRONG_ANSWER";
            case "CE", "COMPILE_ERROR", "COMPILATION_ERROR" -> "COMPILATION_ERROR";
            case "RE", "RUNTIME_ERROR" -> "RUNTIME_ERROR";
            case "TLE", "TIME_LIMIT_EXCEEDED" -> "TIME_LIMIT_EXCEEDED";
            case "MLE", "MEMORY_LIMIT_EXCEEDED" -> "MEMORY_LIMIT_EXCEEDED";
            default -> normalized;
        };
    }

    private boolean isUnknown(String verdict) {
        return verdict == null || verdict.isBlank() || "UNKNOWN".equals(verdict);
    }

    private boolean isAccepted(String verdict) {
        return "ACCEPTED".equals(normalizeVerdict(verdict));
    }

    private boolean isProgress(String previousVerdict, String currentVerdict) {
        String previous = normalizeVerdict(previousVerdict);
        String current = normalizeVerdict(currentVerdict);
        if (isUnknown(previous) || isUnknown(current) || previous.equals(current)) {
            return false;
        }
        return verdictStage(current) > verdictStage(previous);
    }

    private boolean isRegression(String previousVerdict, String currentVerdict) {
        String previous = normalizeVerdict(previousVerdict);
        String current = normalizeVerdict(currentVerdict);
        if (isUnknown(previous) || isUnknown(current) || previous.equals(current)) {
            return false;
        }
        if (isAccepted(previous) && !isAccepted(current)) {
            return true;
        }
        return verdictStage(current) < verdictStage(previous);
    }

    private int verdictStage(String verdict) {
        return switch (normalizeVerdict(verdict)) {
            case "COMPILATION_ERROR" -> 1;
            case "RUNTIME_ERROR" -> 2;
            case "WRONG_ANSWER", "TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED" -> 3;
            case "ACCEPTED" -> 4;
            default -> 0;
        };
    }

    private long maxRepeatedCount(DiagnosisEvidencePackage.HistoryEvidence history) {
        if (history == null) {
            return 0L;
        }
        long issueCount = history.getRepeatedIssueCount() == null ? 0L : history.getRepeatedIssueCount();
        long fineCount = history.getRepeatedFineGrainedIssueCount() == null ? 0L : history.getRepeatedFineGrainedIssueCount();
        return Math.max(issueCount, fineCount);
    }

    public record AgentResult(SubmissionAnalysisResponse analysis,
                              DiagnosisEvidencePackage evidencePackage,
                              String traceSummary) {
    }

    private record ModelStageResult(SubmissionAnalysisResponse analysis) {
    }

    private record InterventionTemplate(String interventionType,
                                        String goal,
                                        String studentTask,
                                        String checkQuestion,
                                        String completionSignal,
                                        int estimatedMinutes) {
    }
}
