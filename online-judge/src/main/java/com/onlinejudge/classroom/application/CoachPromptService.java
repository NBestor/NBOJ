package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachPromptResponse;
import com.onlinejudge.classroom.dto.CoachReplyRequest;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import com.onlinejudge.learning.diagnosis.DiagnosisReportReader;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.application.DiagnosisEvidencePackageReader;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachPromptService {

    private static final String STRATEGY_REDUCE_GRANULARITY = "REDUCE_GRANULARITY";
    private static final String STRATEGY_COLLECT_EVIDENCE = "COLLECT_EVIDENCE";
    private static final String STRATEGY_VERIFY_MINIMAL_CHANGE = "VERIFY_MINIMAL_CHANGE";
    private static final String STRATEGY_TRANSFER_REFLECTION = "TRANSFER_REFLECTION";
    private static final String STRATEGY_SAFETY_RESET = "SAFETY_RESET";

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnalysisRepository submissionAnalysisRepository;
    private final AssignmentRepository assignmentRepository;
    private final CoachPromptRepository coachPromptRepository;
    private final DiagnosisReportReader diagnosisReportReader;
    private final DiagnosisTaxonomy diagnosisTaxonomy;
    private final DiagnosisEvidencePackageReader diagnosisEvidencePackageReader;
    private final CoachAgentService coachAgentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CoachPromptResponse generateNextQuestion(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        SubmissionAnalysis analysis = submissionAnalysisRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new IllegalStateException("请先等待本次 AI 诊断生成后再追问。"));
        Assignment.HintPolicy hintPolicy = resolveHintPolicy(submission);
        List<String> issueTags = diagnosisReportReader.issueTags(analysis);
        List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
        String primaryTag = fineTags.isEmpty()
                ? issueTags.stream().findFirst().orElse("NEEDS_MORE_EVIDENCE")
                : fineTags.get(0);
        LearningContext learningContext = buildLearningContext(submission, analysis, primaryTag, null, null, hintPolicy);
        List<String> evidenceRefs = learningContext.evidenceRefs();
        CoachAgentService.CoachDraft draft = coachAgentService.generateInitialQuestion(
                submission,
                analysis,
                primaryTag,
                hintPolicy,
                learningContext.summary(),
                evidenceRefs
        );
        CoachPrompt prompt = coachPromptRepository.save(CoachPrompt.builder()
                .assignmentId(submission.getAssignmentId())
                .studentProfileId(submission.getStudentProfileId())
                .submissionId(submission.getId())
                .turnIndex(nextTurnIndex(submission.getId()))
                .hintPolicy(hintPolicy.name())
                .promptType(promptType("SOCRATIC_NEXT_STEP", draft))
                .modelFailureReason(blankToNull(draft.getFailureReason()))
                .modelAnswerLeakRisk(blankToNull(firstText(draft.getModelAnswerLeakRisk(), draft.getAnswerLeakRisk())))
                .question(draft.getQuestion())
                .rationale(buildDraftRationale(draft, learningContext))
                .contextSummary(learningContext.summary())
                .evidenceRefs(toJson(draftRefs(draft, evidenceRefs)))
                .build());
        return responseWithTurns(prompt);
    }

    public CoachPromptResponse getLatestPrompt(Long submissionId) {
        return latestNonReflectionPrompt(submissionId)
                .map(this::responseWithTurns)
                .orElse(null);
    }

    @Transactional
    public CoachPromptResponse replyAndGenerateNext(Long submissionId, CoachReplyRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在: " + submissionId));
        SubmissionAnalysis analysis = submissionAnalysisRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new IllegalStateException("请先等待本次 AI 诊断生成后再回答追问。"));
        CoachPrompt current = latestNonReflectionPrompt(submissionId)
                .orElseThrow(() -> new IllegalStateException("请先生成一个 AI 追问。"));
        if (hasText(current.getStudentAnswer())) {
            return responseWithTurns(current);
        }

        String answer = sanitizeAnswer(request == null ? "" : request.getAnswer());
        current.setStudentAnswer(answer);
        current.setCoachFeedback(buildCoachFeedback(current, answer));
        current.setAnsweredAt(LocalDateTime.now());
        coachPromptRepository.save(current);

        Assignment.HintPolicy hintPolicy = resolveHintPolicy(submission);
        List<String> issueTags = diagnosisReportReader.issueTags(analysis);
        List<String> fineTags = diagnosisReportReader.fineGrainedTags(analysis);
        String primaryTag = fineTags.isEmpty()
                ? issueTags.stream().findFirst().orElse("NEEDS_MORE_EVIDENCE")
                : fineTags.get(0);
        LearningContext learningContext = buildLearningContext(submission, analysis, primaryTag, answer, current.getTurnIndex(), hintPolicy);
        List<String> evidenceRefs = learningContext.evidenceRefs();
        CoachAgentService.CoachDraft draft = coachAgentService.generateFollowUpQuestion(
                submission,
                analysis,
                primaryTag,
                hintPolicy,
                learningContext.summary(),
                evidenceRefs,
                answer,
                current.getTurnIndex()
        );
        CoachPrompt next = coachPromptRepository.save(CoachPrompt.builder()
                .assignmentId(submission.getAssignmentId())
                .studentProfileId(submission.getStudentProfileId())
                .submissionId(submission.getId())
                .parentPromptId(current.getId())
                .turnIndex(nextTurnIndex(submission.getId()))
                .hintPolicy(hintPolicy.name())
                .promptType(promptType("SOCRATIC_FOLLOW_UP", draft))
                .modelFailureReason(blankToNull(draft.getFailureReason()))
                .modelAnswerLeakRisk(blankToNull(firstText(draft.getModelAnswerLeakRisk(), draft.getAnswerLeakRisk())))
                .question(draft.getQuestion())
                .rationale(buildDraftRationale(draft, learningContext))
                .contextSummary(learningContext.summary())
                .evidenceRefs(toJson(draftRefs(draft, evidenceRefs)))
                .build());
        return responseWithTurns(next);
    }

    private String promptType(String baseType, CoachAgentService.CoachDraft draft) {
        if ("MODEL".equals(draft.getSource())) {
            return baseType + "_MODEL";
        }
        if ("AI_UNAVAILABLE".equals(draft.getSource())) {
            return baseType + "_AI_UNAVAILABLE";
        }
        return baseType;
    }

    private String buildDraftRationale(CoachAgentService.CoachDraft draft, LearningContext learningContext) {
        String rationale = draft.getRationale() == null || draft.getRationale().isBlank()
                ? "基于证据生成追问。"
                : draft.getRationale();
        String source = "MODEL".equals(draft.getSource())
                ? "模型追问"
                : "AI_UNAVAILABLE".equals(draft.getSource()) ? "AI 追问不可用" : "追问";
        String confidence = draft.getConfidence() == null ? "" : " 置信度：" + draft.getConfidence();
        String risk = draft.getAnswerLeakRisk() == null ? "" : " 泄题风险：" + draft.getAnswerLeakRisk();
        return source + "。" + rationale + confidence + risk
                + (learningContext.summary().isBlank() ? "" : " " + learningContext.summary());
    }

    private List<String> draftRefs(CoachAgentService.CoachDraft draft, List<String> contextRefs) {
        if ("AI_UNAVAILABLE".equals(draft.getSource())) {
            return List.of();
        }
        if (draft.getEvidenceRefs() == null || draft.getEvidenceRefs().isEmpty()) {
            return contextRefs == null ? List.of() : contextRefs;
        }
        return mergeRefs(draft.getEvidenceRefs(), contextRefs);
    }

    private Assignment.HintPolicy resolveHintPolicy(Submission submission) {
        if (submission.getAssignmentId() == null) {
            return Assignment.HintPolicy.L2;
        }
        return assignmentRepository.findById(submission.getAssignmentId())
                .map(Assignment::getHintPolicy)
                .orElse(Assignment.HintPolicy.L2);
    }

    private LearningContext buildLearningContext(Submission submission,
                                                 SubmissionAnalysis analysis,
                                                 String primaryTag,
                                                 String studentAnswer,
                                                 Integer currentTurnIndex,
                                                 Assignment.HintPolicy hintPolicy) {
        List<String> refs = diagnosisReportReader.evidenceRefs(analysis);
        DiagnosisEvidencePackageReader.EvidenceSummary evidenceSummary = diagnosisEvidencePackageReader.summarize(analysis, submission);
        List<String> baseRefs = mergeRefs(List.of(
                "submission:" + submission.getId(),
                "analysis:" + analysis.getScenario(),
                "tag:" + primaryTag
        ), refs);
        List<String> evidenceRefs = mergeRefs(baseRefs, evidenceSummary.evidenceRefs());
        List<Submission> recentSubmissions = loadRecentSubmissions(submission);
        Map<Long, SubmissionAnalysis> analyses = recentSubmissions.isEmpty()
                ? Map.of()
                : submissionAnalysisRepository.findBySubmissionIdIn(recentSubmissions.stream().map(Submission::getId).toList())
                .stream()
                .collect(Collectors.toMap(SubmissionAnalysis::getSubmissionId, Function.identity()));
        String repeatedFineTag = repeatedTag(recentSubmissions, analyses, true);
        String repeatedIssueTag = repeatedTag(recentSubmissions, analyses, false);
        String transition = buildTransition(recentSubmissions);
        StudentProfileContext studentProfileContext = buildStudentProfileContext(recentSubmissions, analyses);
        evidenceRefs = mergeRefs(evidenceRefs, studentProfileContext.evidenceRefs());
        DiagnosisReportReader.LearningActionEvidenceSnapshot actionEvidence = diagnosisReportReader.learningActionEvidence(analysis);
        CoachPromptResponse.CoachAdaptiveStrategySignal adaptiveStrategySignal = selectAdaptiveStrategy(
                submission,
                primaryTag,
                repeatedFineTag,
                repeatedIssueTag,
                actionEvidence,
                studentAnswer,
                currentTurnIndex,
                hintPolicy,
                evidenceRefs
        );
        evidenceRefs = mergeRefs(evidenceRefs, adaptiveStrategySignal.getEvidenceRefs());
        String summary = buildContextSummary(
                primaryTag,
                repeatedFineTag,
                repeatedIssueTag,
                transition,
                evidenceRefs,
                evidenceSummary,
                adaptiveStrategySignal,
                studentProfileContext
        );
        return new LearningContext(summary, evidenceRefs, adaptiveStrategySignal);
    }

    private CoachPromptResponse.CoachAdaptiveStrategySignal selectAdaptiveStrategy(Submission submission,
                                                                                  String primaryTag,
                                                                                  String repeatedFineTag,
                                                                                  String repeatedIssueTag,
                                                                                  DiagnosisReportReader.LearningActionEvidenceSnapshot actionEvidence,
                                                                                  String studentAnswer,
                                                                                  Integer currentTurnIndex,
                                                                                  Assignment.HintPolicy hintPolicy,
                                                                                  List<String> baseEvidenceRefs) {
        LinkedHashSet<String> refs = new LinkedHashSet<>(baseEvidenceRefs == null ? List.of() : baseEvidenceRefs);
        StrategyDecision decision;
        if (studentAnswer != null) {
            decision = selectFollowUpStrategy(studentAnswer, currentTurnIndex, hintPolicy);
        } else {
            decision = selectInitialStrategy(submission, primaryTag, repeatedFineTag, repeatedIssueTag, actionEvidence);
        }
        refs.add("coach-strategy:" + decision.strategy());
        refs.addAll(decision.evidenceRefs());
        return CoachPromptResponse.CoachAdaptiveStrategySignal.builder()
                .strategy(decision.strategy())
                .reason(decision.reason())
                .recommendedCoachMove(decision.recommendedCoachMove())
                .needsTeacherAttention(decision.needsTeacherAttention())
                .evidenceRefs(List.copyOf(refs))
                .build();
    }

    private StrategyDecision selectInitialStrategy(Submission submission,
                                                   String primaryTag,
                                                   String repeatedFineTag,
                                                   String repeatedIssueTag,
                                                   DiagnosisReportReader.LearningActionEvidenceSnapshot actionEvidence) {
        if (submission != null && submission.getVerdict() == Submission.Verdict.ACCEPTED) {
            return strategy(
                    STRATEGY_TRANSFER_REFLECTION,
                    "当前提交已经通过，需要把通过经验迁移到边界、复杂度或新样例复盘。",
                    "请学生解释复杂度、补一个非样例测试或说明最容易漏掉的边界。",
                    false,
                    List.of("coach-adaptive:verdict:ACCEPTED")
            );
        }
        if (actionEvidence != null && "CONTRADICTED".equals(actionEvidence.executionStatus())) {
            return strategy(
                    STRATEGY_REDUCE_GRANULARITY,
                    firstText(actionEvidence.nextAdjustment(), "前次学习动作被后续证据反驳，需要降低提示颗粒度。"),
                    "把问题缩到一个最小失败样例或一条关键变量轨迹，先验证学生是否真正看见失败条件。",
                    true,
                    mergeRefs(
                            List.of("coach-adaptive:previous_action:CONTRADICTED"),
                            actionEvidence.evidenceRefs()
                    )
            );
        }
        if (hasText(repeatedFineTag)) {
            return strategy(
                    STRATEGY_REDUCE_GRANULARITY,
                    "最近多次出现细分卡点“" + diagnosisTaxonomy.label(repeatedFineTag) + "”，需要缩小追问颗粒度。",
                    "要求学生写出最小失败样例、变量首次和末次取值，避免继续泛泛讨论。",
                    true,
                    List.of("coach-adaptive:repeated_fine_tag:" + repeatedFineTag)
            );
        }
        if (hasText(repeatedIssueTag)) {
            return strategy(
                    STRATEGY_REDUCE_GRANULARITY,
                    "最近多次出现同类问题“" + diagnosisTaxonomy.label(repeatedIssueTag) + "”，需要缩小追问颗粒度。",
                    "要求学生把同类问题落到一个可手推样例或输入输出对照。",
                    true,
                    List.of("coach-adaptive:repeated_issue_tag:" + repeatedIssueTag)
            );
        }
        return strategy(
                STRATEGY_COLLECT_EVIDENCE,
                "当前还缺少学生可自证的样例、变量或复杂度证据。",
                "先让学生补一个最小样例、关键变量变化或输入输出对照。",
                false,
                List.of("coach-adaptive:primary_tag:" + (primaryTag == null ? "NEEDS_MORE_EVIDENCE" : primaryTag))
        );
    }

    private StrategyDecision selectFollowUpStrategy(String studentAnswer,
                                                    Integer currentTurnIndex,
                                                    Assignment.HintPolicy hintPolicy) {
        String normalized = studentAnswer == null ? "" : studentAnswer.toLowerCase(Locale.ROOT);
        if (!hasText(studentAnswer)) {
            return strategy(
                    STRATEGY_COLLECT_EVIDENCE,
                    "学生上一轮还没有形成可验证回答。",
                    "请学生补一个最小样例或关键变量变化。",
                    false,
                    List.of("coach-adaptive:answer_quality:NOT_ANSWERED")
            );
        }
        if (mentionsAnswerLikeContent(normalized)) {
            return strategy(
                    STRATEGY_SAFETY_RESET,
                    "学生回答出现答案或完整代码倾向，需要把对话拉回证据层。",
                    "避免确认直接改法，改问能暴露问题的输入特征或测试现象。",
                    true,
                    List.of("coach-adaptive:answer_quality:SAFETY_RISK")
            );
        }
        if (!mentionsEvidence(normalized)) {
            return strategy(
                    STRATEGY_COLLECT_EVIDENCE,
                    "学生上一轮只有方向但缺少样例、变量、输入输出或复杂度证据。",
                    "继续要求补证据，先不要推进到改代码。",
                    false,
                    List.of("coach-adaptive:answer_quality:NEEDS_EVIDENCE")
            );
        }
        return strategy(
                STRATEGY_VERIFY_MINIMAL_CHANGE,
                "学生回答已经包含可验证证据，可以推进到最小修改验证。",
                currentTurnIndex != null && currentTurnIndex >= 3
                        ? "请学生先提交一次最小修改，用评测结果验证判断。"
                        : "请学生只做一个最小修改，并预测下一次提交会改变哪个测试现象。",
                false,
                List.of(
                        "coach-adaptive:answer_quality:EVIDENCE_GROUNDED",
                        "coach-adaptive:hint_policy:" + (hintPolicy == null ? Assignment.HintPolicy.L2.name() : hintPolicy.name())
                )
        );
    }

    private StrategyDecision strategy(String strategy,
                                      String reason,
                                      String recommendedCoachMove,
                                      boolean needsTeacherAttention,
                                      List<String> evidenceRefs) {
        return new StrategyDecision(
                strategy,
                reason == null ? "" : reason,
                recommendedCoachMove == null ? "" : recommendedCoachMove,
                needsTeacherAttention,
                evidenceRefs == null ? List.of() : evidenceRefs
        );
    }

    private List<Submission> loadRecentSubmissions(Submission submission) {
        if (submission.getAssignmentId() == null || submission.getStudentProfileId() == null) {
            return List.of(submission);
        }
        return submissionRepository.findByAssignmentIdAndStudentProfileIdOrderBySubmittedAtDesc(
                        submission.getAssignmentId(),
                        submission.getStudentProfileId()
                )
                .stream()
                .filter(item -> item.getId() != null)
                .limit(5)
                .toList();
    }

    private String repeatedTag(List<Submission> submissions,
                               Map<Long, SubmissionAnalysis> analyses,
                               boolean fineGrained) {
        Map<String, Long> counts = submissions.stream()
                .map(submission -> analyses.get(submission.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> (fineGrained
                        ? diagnosisReportReader.fineGrainedTags(analysis)
                        : diagnosisReportReader.issueTags(analysis)).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() >= 2)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private String buildTransition(List<Submission> submissions) {
        if (submissions.size() < 2) {
            return "";
        }
        String latest = verdictName(submissions.get(0));
        String previous = verdictName(submissions.get(1));
        if (latest.equals(previous)) {
            return "最近两次提交仍停留在“" + readableVerdict(latest) + "”。";
        }
        return "最近评测阶段从“" + readableVerdict(previous) + "”变为“" + readableVerdict(latest) + "”。";
    }

    private StudentProfileContext buildStudentProfileContext(List<Submission> submissions,
                                                             Map<Long, SubmissionAnalysis> analyses) {
        if (submissions == null || submissions.isEmpty() || analyses == null || analyses.isEmpty()) {
            return StudentProfileContext.empty();
        }
        List<Submission> source = submissions.stream()
                .filter(submission -> submission.getVerdict() != Submission.Verdict.ACCEPTED)
                .toList();
        if (source.isEmpty()) {
            source = submissions;
        }
        List<String> fineTags = topAnalysisValues(source, analyses, diagnosisReportReader::fineGrainedTags, 3);
        List<String> issueTags = topAnalysisValues(source, analyses, diagnosisReportReader::issueTags, 2);
        List<String> abilityPoints = topAnalysisValues(source, analyses, diagnosisReportReader::abilityPoints, 2);
        List<String> focusPoints = topAnalysisValues(source, analyses, diagnosisReportReader::focusPoints, 2);
        if (fineTags.isEmpty() && issueTags.isEmpty() && abilityPoints.isEmpty() && focusPoints.isEmpty()) {
            return StudentProfileContext.empty();
        }
        StringBuilder summary = new StringBuilder("学生画像：");
        if (!fineTags.isEmpty()) {
            summary.append("近期高频细颗粒错因“")
                    .append(fineTags.stream().map(diagnosisTaxonomy::label).collect(Collectors.joining("、")))
                    .append("”；");
        } else if (!issueTags.isEmpty()) {
            summary.append("近期高频问题“")
                    .append(issueTags.stream().map(diagnosisTaxonomy::label).collect(Collectors.joining("、")))
                    .append("”；");
        }
        if (!abilityPoints.isEmpty()) {
            summary.append("能力点“").append(String.join("、", abilityPoints)).append("”；");
        }
        if (!focusPoints.isEmpty()) {
            summary.append("学习焦点“").append(String.join("、", focusPoints)).append("”；");
        }
        List<String> reviewHints = source.stream()
                .filter(submission -> submission.getId() != null)
                .limit(2)
                .map(submission -> "题目#" + submission.getProblemId() + " " + readableVerdict(verdictName(submission)))
                .toList();
        if (!reviewHints.isEmpty()) {
            summary.append("最近复盘题：").append(String.join("，", reviewHints)).append("。");
        }
        List<String> evidenceRefs = mergeRefs(
                mergeRefs(
                        fineTags.stream().map(tag -> "student-profile:fine-tag:" + tag).toList(),
                        abilityPoints.stream().map(point -> "student-profile:ability:" + point).toList()
                ),
                source.stream()
                        .filter(submission -> submission.getId() != null)
                        .limit(2)
                        .map(submission -> "student-profile:review-submission:" + submission.getId())
                        .toList()
        );
        return new StudentProfileContext(summary.toString(), evidenceRefs);
    }

    private List<String> topAnalysisValues(List<Submission> submissions,
                                           Map<Long, SubmissionAnalysis> analyses,
                                           Function<SubmissionAnalysis, List<String>> extractor,
                                           int limit) {
        return submissions.stream()
                .map(submission -> analyses.get(submission.getId()))
                .filter(Objects::nonNull)
                .flatMap(analysis -> extractor.apply(analysis).stream())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String buildContextSummary(String primaryTag,
                                       String repeatedFineTag,
                                       String repeatedIssueTag,
                                       String transition,
                                       List<String> evidenceRefs,
                                       DiagnosisEvidencePackageReader.EvidenceSummary evidenceSummary,
                                       CoachPromptResponse.CoachAdaptiveStrategySignal adaptiveStrategySignal,
                                       StudentProfileContext studentProfileContext) {
        StringBuilder summary = new StringBuilder("本次追问基于“")
                .append(diagnosisTaxonomy.label(primaryTag))
                .append("”。");
        if (repeatedFineTag != null && !repeatedFineTag.isBlank()) {
            summary.append(" 最近多次出现细分卡点“").append(diagnosisTaxonomy.label(repeatedFineTag)).append("”。");
        } else if (repeatedIssueTag != null && !repeatedIssueTag.isBlank()) {
            summary.append(" 最近多次出现同类问题“").append(diagnosisTaxonomy.label(repeatedIssueTag)).append("”。");
        }
        if (transition != null && !transition.isBlank()) {
            summary.append(" ").append(transition);
        }
        if (evidenceRefs != null && evidenceRefs.size() > 3) {
            summary.append(" 已引用 ").append(evidenceRefs.size()).append(" 条诊断证据。");
        }
        if (studentProfileContext != null && hasText(studentProfileContext.summary())) {
            summary.append(" ").append(studentProfileContext.summary());
        }
        if (evidenceSummary != null && evidenceSummary.detailLines() != null && !evidenceSummary.detailLines().isEmpty()) {
            summary.append(" 证据包摘要：")
                    .append(evidenceSummary.detailLines().stream().limit(3).collect(Collectors.joining("；")))
                    .append("。");
        }
        if (adaptiveStrategySignal != null && hasText(adaptiveStrategySignal.getStrategy())) {
            summary.append(" Coach 自适应策略：")
                    .append(firstText(adaptiveStrategySignal.getReason(), adaptiveStrategyLabel(adaptiveStrategySignal.getStrategy())))
                    .append(" 下一问动作：")
                    .append(firstText(adaptiveStrategySignal.getRecommendedCoachMove(), adaptiveStrategyLabel(adaptiveStrategySignal.getStrategy())))
                    .append("。");
        }
        return summary.toString();
    }

    private String verdictName(Submission submission) {
        return submission == null || submission.getVerdict() == null ? "UNKNOWN" : submission.getVerdict().name();
    }

    private String readableVerdict(String verdict) {
        return switch (verdict) {
            case "ACCEPTED" -> "已通过";
            case "WRONG_ANSWER" -> "答案需修正";
            case "TIME_LIMIT_EXCEEDED" -> "时间超限";
            case "MEMORY_LIMIT_EXCEEDED" -> "内存超限";
            case "RUNTIME_ERROR" -> "运行错误";
            case "COMPILATION_ERROR" -> "编译错误";
            default -> "待观察";
        };
    }

    private String buildCoachFeedback(CoachPrompt prompt, String answer) {
        String normalized = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (!hasText(answer)) {
            return "这次回答还没有形成可验证想法，下一步先补一个最小样例。";
        }
        if (mentionsAnswerLikeContent(normalized)) {
            return "你已经开始想到改法了，但这里先收回到证据层：先确认哪个输入特征会触发问题。";
        }
        if (mentionsEvidence(normalized)) {
            return "这次回答有证据意识，可以进入最小修改或反例验证。";
        }
        return "这次回答有方向，但证据还不够。下一步要把方向落到样例、变量变化或复杂度数量级上。";
    }

    private boolean mentionsEvidence(String normalizedAnswer) {
        if (normalizedAnswer == null) {
            return false;
        }
        return normalizedAnswer.contains("n=")
                || normalizedAnswer.contains("n =")
                || normalizedAnswer.contains("样例")
                || normalizedAnswer.contains("输入")
                || normalizedAnswer.contains("输出")
                || normalizedAnswer.contains("边界")
                || normalizedAnswer.contains("变量")
                || normalizedAnswer.contains("复杂度")
                || normalizedAnswer.contains("次数")
                || normalizedAnswer.contains("最大")
                || normalizedAnswer.matches(".*\\d+.*");
    }

    private boolean mentionsAnswerLikeContent(String normalizedAnswer) {
        if (normalizedAnswer == null) {
            return false;
        }
        return normalizedAnswer.contains("完整代码")
                || normalizedAnswer.contains("答案")
                || normalizedAnswer.contains("#include")
                || normalizedAnswer.contains("def ")
                || normalizedAnswer.contains("class ");
    }

    private String toJson(List<String> refs) {
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private List<String> mergeRefs(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(
                        left == null ? java.util.stream.Stream.empty() : left.stream(),
                        right == null ? java.util.stream.Stream.empty() : right.stream()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> parseRefs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private CoachPromptResponse responseWithTurns(CoachPrompt prompt) {
        List<CoachPrompt> turns = coachPromptRepository.findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(prompt.getSubmissionId())
                .stream()
                .filter(turn -> !LearningProofService.REFLECTION_PROMPT_TYPE.equals(turn.getPromptType()))
                .toList();
        List<CoachPromptResponse> turnResponses = turns.stream()
                .map(turn -> {
                    List<String> refs = parseRefs(turn.getEvidenceRefs());
                    return CoachPromptResponse.from(turn, refs, adaptiveStrategySignalFromPrompt(turn, refs));
                })
                .toList();
        List<String> refs = parseRefs(prompt.getEvidenceRefs());
        return CoachPromptResponse.from(prompt, refs, turnResponses, adaptiveStrategySignalFromPrompt(prompt, refs));
    }

    private CoachPromptResponse.CoachAdaptiveStrategySignal adaptiveStrategySignalFromPrompt(CoachPrompt prompt, List<String> evidenceRefs) {
        String strategy = (evidenceRefs == null ? List.<String>of() : evidenceRefs)
                .stream()
                .filter(ref -> ref != null && ref.startsWith("coach-strategy:"))
                .map(ref -> ref.substring("coach-strategy:".length()).trim())
                .filter(ref -> !ref.isBlank())
                .findFirst()
                .orElse("");
        if (strategy.isBlank()) {
            return null;
        }
        List<String> strategyRefs = (evidenceRefs == null ? List.<String>of() : evidenceRefs)
                .stream()
                .filter(ref -> ref != null && (ref.startsWith("coach-strategy:") || ref.startsWith("coach-adaptive:")))
                .distinct()
                .toList();
        return CoachPromptResponse.CoachAdaptiveStrategySignal.builder()
                .strategy(strategy)
                .reason(extractAdaptiveReason(prompt == null ? "" : prompt.getContextSummary(), strategy))
                .recommendedCoachMove(extractAdaptiveMove(prompt == null ? "" : prompt.getContextSummary(), strategy))
                .needsTeacherAttention(needsTeacherAttention(strategy, strategyRefs))
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                .build();
    }

    private String extractAdaptiveReason(String contextSummary, String strategy) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return adaptiveStrategyLabel(strategy);
        }
        String marker = "Coach 自适应策略：";
        int start = contextSummary.indexOf(marker);
        if (start < 0) {
            return adaptiveStrategyLabel(strategy);
        }
        int valueStart = start + marker.length();
        int end = contextSummary.indexOf(" 下一问动作：", valueStart);
        if (end < 0) {
            end = contextSummary.indexOf('。', valueStart);
        }
        if (end < 0) {
            end = contextSummary.length();
        }
        String value = contextSummary.substring(valueStart, end).trim();
        return value.isBlank() ? adaptiveStrategyLabel(strategy) : value;
    }

    private String extractAdaptiveMove(String contextSummary, String strategy) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return adaptiveStrategyMove(strategy);
        }
        String marker = " 下一问动作：";
        int start = contextSummary.indexOf(marker);
        if (start < 0) {
            return adaptiveStrategyMove(strategy);
        }
        int valueStart = start + marker.length();
        int end = contextSummary.indexOf('。', valueStart);
        if (end < 0) {
            end = contextSummary.length();
        }
        String value = contextSummary.substring(valueStart, end).trim();
        return value.isBlank() ? adaptiveStrategyMove(strategy) : value;
    }

    private boolean needsTeacherAttention(String strategy, List<String> evidenceRefs) {
        return STRATEGY_SAFETY_RESET.equals(strategy)
                || (STRATEGY_REDUCE_GRANULARITY.equals(strategy)
                && evidenceRefs != null
                && evidenceRefs.stream().anyMatch(ref -> ref.contains("previous_action:CONTRADICTED") || ref.contains("repeated_")));
    }

    private String adaptiveStrategy(String strategy) {
        return strategy == null ? "" : strategy.trim().toUpperCase(Locale.ROOT);
    }

    private String adaptiveStrategy(CoachPromptResponse.CoachAdaptiveStrategySignal adaptiveStrategySignal) {
        return adaptiveStrategy(adaptiveStrategySignal == null ? "" : adaptiveStrategySignal.getStrategy());
    }

    private String adaptiveStrategyLabel(String strategy) {
        return switch (adaptiveStrategy(strategy)) {
            case STRATEGY_REDUCE_GRANULARITY -> "降低提示颗粒度";
            case STRATEGY_COLLECT_EVIDENCE -> "收集可验证证据";
            case STRATEGY_VERIFY_MINIMAL_CHANGE -> "验证一个最小修改";
            case STRATEGY_TRANSFER_REFLECTION -> "通过后迁移复盘";
            case STRATEGY_SAFETY_RESET -> "回到证据层避免泄题";
            default -> "继续苏格拉底式追问";
        };
    }

    private String adaptiveStrategyMove(String strategy) {
        return switch (adaptiveStrategy(strategy)) {
            case STRATEGY_REDUCE_GRANULARITY -> "缩到最小失败样例、关键变量轨迹或单行输入输出对照。";
            case STRATEGY_COLLECT_EVIDENCE -> "要求学生补一个样例、变量变化、输入输出对照或复杂度数量级。";
            case STRATEGY_VERIFY_MINIMAL_CHANGE -> "让学生只做一个最小修改，并预测下一次评测现象。";
            case STRATEGY_TRANSFER_REFLECTION -> "让学生解释复杂度、边界样例或迁移到新测试。";
            case STRATEGY_SAFETY_RESET -> "避免确认改法，把问题拉回输入特征和测试现象。";
            default -> "继续提出短、具体、可验证的下一问。";
        };
    }

    private String firstText(String first, String defaultValue) {
        return first == null || first.isBlank() ? defaultValue : first;
    }

    private Optional<CoachPrompt> latestNonReflectionPrompt(Long submissionId) {
        return coachPromptRepository.findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(submissionId)
                .stream()
                .filter(prompt -> !LearningProofService.REFLECTION_PROMPT_TYPE.equals(prompt.getPromptType()))
                .reduce((left, right) -> right);
    }

    private int nextTurnIndex(Long submissionId) {
        return coachPromptRepository.findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(submissionId)
                .stream()
                .filter(prompt -> !LearningProofService.REFLECTION_PROMPT_TYPE.equals(prompt.getPromptType()))
                .map(CoachPrompt::getTurnIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String sanitizeAnswer(String answer) {
        return answer == null ? "" : answer.trim();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record LearningContext(String summary,
                                   List<String> evidenceRefs,
                                   CoachPromptResponse.CoachAdaptiveStrategySignal adaptiveStrategySignal) {
    }

    private record StudentProfileContext(String summary, List<String> evidenceRefs) {
        private static StudentProfileContext empty() {
            return new StudentProfileContext("", List.of());
        }
    }

    private record StrategyDecision(String strategy,
                                    String reason,
                                    String recommendedCoachMove,
                                    boolean needsTeacherAttention,
                                    List<String> evidenceRefs) {
    }
}
