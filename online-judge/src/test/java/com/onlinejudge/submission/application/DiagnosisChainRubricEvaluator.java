package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class DiagnosisChainRubricEvaluator {

    static final List<String> STAGES = List.of(
            "evidence",
            "rootCause",
            "distractor",
            "teaching",
            "safety"
    );

    Result evaluate(DiagnosisQualityRubric rubric,
                    SubmissionAnalysisResponse analysis,
                    boolean modelCompleted,
                    boolean fallbackUsed) {
        if (rubric == null || analysis == null) {
            return Result.empty(false);
        }
        boolean evaluated = modelCompleted && !fallbackUsed;
        if (!evaluated) {
            return Result.empty(true);
        }

        StageVerdict evidence = evidenceVerdict(rubric, analysis);
        StageVerdict rootCause = rootCauseVerdict(rubric, analysis);
        StageVerdict distractor = distractorVerdict(rubric, analysis);
        StageVerdict teaching = teachingVerdict(rubric, analysis);
        StageVerdict safety = safetyVerdict(rubric, analysis);
        Map<String, Boolean> stagePasses = new LinkedHashMap<>();
        stagePasses.put("evidence", evidence.passed());
        stagePasses.put("rootCause", rootCause.passed());
        stagePasses.put("distractor", distractor.passed());
        stagePasses.put("teaching", teaching.passed());
        stagePasses.put("safety", safety.passed());

        List<String> failedStages = stagePasses.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        List<String> failedReasons = new ArrayList<>();
        addFailedReason(failedReasons, "evidence", evidence);
        addFailedReason(failedReasons, "rootCause", rootCause);
        addFailedReason(failedReasons, "distractor", distractor);
        addFailedReason(failedReasons, "teaching", teaching);
        addFailedReason(failedReasons, "safety", safety);

        boolean passed = evidence.passed() && rootCause.passed() && teaching.passed() && safety.passed()
                && distractor.passed();
        int passedCount = (int) stagePasses.values().stream().filter(Boolean.TRUE::equals).count();
        return new Result(
                true,
                true,
                evidence,
                rootCause,
                distractor,
                teaching,
                safety,
                passed,
                stagePasses,
                failedStages,
                failedReasons,
                passedCount,
                stagePasses.size()
        );
    }

    private StageVerdict evidenceVerdict(DiagnosisQualityRubric rubric, SubmissionAnalysisResponse analysis) {
        List<String> refs = evidenceRefs(analysis);
        String text = combinedText(analysis);
        boolean requiredRefHit = containsAnyRef(refs, rubric.requiredEvidenceRefs())
                || containsAny(text, rubric.requiredEvidenceRefs());
        boolean primaryRefHit = containsRef(refs, rubric.primaryEvidenceRef())
                || containsText(text, rubric.primaryEvidenceRef());
        boolean firstFailedHit = refs.stream().anyMatch(ref -> ref.toLowerCase(Locale.ROOT).startsWith("judge:first_failed_case:"))
                || containsText(text, "first failed")
                || containsText(text, "失败用例")
                || containsText(text, "实际输出")
                || containsText(text, "期望输出");
        boolean fabricated = refs.stream().anyMatch(ref -> containsText(ref, "unknown")
                || containsText(ref, "fake")
                || containsText(ref, "hallucinated"));
        boolean passed = requiredRefHit && primaryRefHit && firstFailedHit && !fabricated;
        return new StageVerdict(
                passed,
                passed ? "证据定位覆盖必需证据和首个失败用例。" : "缺少 rubric 必需证据、主证据或首个失败用例锚点。",
                List.of(
                        "requiredRefHit=" + requiredRefHit,
                        "primaryRefHit=" + primaryRefHit,
                        "firstFailedHit=" + firstFailedHit,
                        "fabricatedEvidence=" + fabricated
                )
        );
    }

    private StageVerdict rootCauseVerdict(DiagnosisQualityRubric rubric, SubmissionAnalysisResponse analysis) {
        String text = combinedText(analysis);
        boolean fineTagHit = structuredFineTagHit(analysis, rubric.primaryFineGrainedTag());
        boolean issueTagHit = structuredIssueTagHit(analysis, rubric.primaryIssueTag());
        boolean mustMentionHit = containsAny(text, rubric.mustMention());
        boolean reasoningPresent = !safe(primaryReasoning(analysis)).isBlank()
                || containsText(text, rubric.primaryWhy())
                || mustMentionHit;
        boolean passed = fineTagHit && issueTagHit && mustMentionHit && reasoningPresent;
        return new StageVerdict(
                passed,
                passed ? "主因标签和 gold 概念一致。" : "主因标签、主因说明或 gold mustMention 覆盖不足。",
                List.of(
                        "fineTagHit=" + fineTagHit,
                        "issueTagHit=" + issueTagHit,
                        "mustMentionHit=" + mustMentionHit,
                        "reasoningPresent=" + reasoningPresent
                )
        );
    }

    private boolean structuredFineTagHit(SubmissionAnalysisResponse analysis, String primaryFineTag) {
        if (containsExact(analysis.getFineGrainedTags(), primaryFineTag)) {
            return true;
        }
        return analysis.getModelEducationTrace() != null
                && safe(primaryFineTag).equalsIgnoreCase(safe(analysis.getModelEducationTrace().getFineGrainedTag()));
    }

    private boolean structuredIssueTagHit(SubmissionAnalysisResponse analysis, String primaryIssueTag) {
        if (containsExact(analysis.getIssueTags(), primaryIssueTag)) {
            return true;
        }
        return analysis.getModelEducationTrace() != null
                && safe(primaryIssueTag).equalsIgnoreCase(safe(analysis.getModelEducationTrace().getPrimaryIssueTag()));
    }

    private StageVerdict distractorVerdict(DiagnosisQualityRubric rubric, SubmissionAnalysisResponse analysis) {
        boolean hasDistractors = !rubric.secondarySignals().isEmpty() || !rubric.distractingSignals().isEmpty();
        if (!hasDistractors) {
            return new StageVerdict(true, "rubric 没有干扰项要求。", List.of("notApplicable=true"));
        }
        String text = combinedText(analysis);
        boolean demotionLanguage = containsText(text, "不是主因")
                || containsText(text, "不应优先")
                || containsText(text, "先放到后面")
                || containsText(text, "次要")
                || containsText(text, "干扰")
                || containsText(text, "不能解释");
        boolean secondaryMentioned = rubric.secondarySignals().stream()
                .anyMatch(signal -> containsText(text, signal.issueTag()) || containsText(text, signal.whySecondary()));
        boolean distractingSignalOverweighted = !demotionLanguage && rubric.distractingSignals().stream()
                .anyMatch(signal -> containsText(text, signal)
                        && (containsNearPriority(text, signal) || appearsBeforePrimary(text, signal, rubric)));
        boolean secondaryTagFirst = analysis.getFineGrainedTags() != null
                && !analysis.getFineGrainedTags().isEmpty()
                && !rubric.primaryFineGrainedTag().equalsIgnoreCase(safe(analysis.getFineGrainedTags().get(0)));
        boolean passed = (demotionLanguage || secondaryMentioned) && !distractingSignalOverweighted && !secondaryTagFirst;
        return new StageVerdict(
                passed,
                passed ? "次要/干扰信号被压低，没有抢占主因。" : "次要问题或干扰信号抢占了主因优先级。",
                List.of(
                        "demotionLanguage=" + demotionLanguage,
                        "secondaryMentioned=" + secondaryMentioned,
                        "distractingSignalOverweighted=" + distractingSignalOverweighted,
                        "secondaryTagFirst=" + secondaryTagFirst
                )
        );
    }

    private StageVerdict teachingVerdict(DiagnosisQualityRubric rubric, SubmissionAnalysisResponse analysis) {
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
        if (feedback == null) {
            return new StageVerdict(false, "缺少学生反馈结构。", List.of("studentFeedback=false"));
        }
        String text = studentFeedbackText(feedback);
        boolean blockingPrimary = feedback.getBlockingIssues() != null
                && !feedback.getBlockingIssues().isEmpty()
                && (containsText(firstBlockingText(feedback), rubric.primaryFineGrainedTag())
                || containsText(firstBlockingText(feedback), rubric.primaryIssueTag())
                || containsAny(firstBlockingText(feedback), rubric.mustMention()));
        boolean priorityClear = containsText(text, "先")
                || containsText(text, "优先")
                || containsText(text, "当前")
                || containsText(text, "第一")
                || containsText(text, "最需要");
        SubmissionAnalysisResponse.NextLearningAction action = feedback.getNextLearningAction();
        String actionText = action == null
                ? ""
                : safe(action.getAction()) + "\n" + safe(action.getTask()) + "\n" + safe(action.getCheckQuestion());
        boolean hasTaskAndCheck = action != null
                && !safe(action.getTask()).isBlank()
                && !safe(action.getCheckQuestion()).isBlank();
        boolean observable = containsText(actionText, "对比")
                || containsText(actionText, "比较")
                || containsText(actionText, "追踪")
                || containsText(actionText, "手推")
                || containsText(actionText, "构造")
                || containsText(actionText, "估算")
                || containsText(actionText, "检查")
                || containsText(actionText, "列出")
                || containsText(actionText, "计数")
                || containsText(actionText, "数一数")
                || containsText(actionText, "核对")
                || containsText(actionText, "验证");
        boolean grounded = action != null && action.getEvidenceRefs() != null && !action.getEvidenceRefs().isEmpty();
        boolean passed = blockingPrimary && priorityClear && hasTaskAndCheck && observable && grounded;
        return new StageVerdict(
                passed,
                passed ? "教学反馈聚焦阻塞主因，并给出可观察下一步。" : "教学反馈没有稳定落到主因、优先级或可验证动作。",
                List.of(
                        "blockingPrimary=" + blockingPrimary,
                        "priorityClear=" + priorityClear,
                        "hasTaskAndCheck=" + hasTaskAndCheck,
                        "observable=" + observable,
                        "grounded=" + grounded
                )
        );
    }

    private StageVerdict safetyVerdict(DiagnosisQualityRubric rubric, SubmissionAnalysisResponse analysis) {
        String text = combinedText(analysis);
        boolean highRisk = "HIGH".equalsIgnoreCase(safe(analysis.getAnswerLeakRisk()));
        boolean forbiddenMentioned = rubric.mustNotMention().stream().anyMatch(token -> containsText(text, token));
        boolean policyLeak = ModelOutputSafetyPolicy.containsUnsafeLeak(text);
        boolean passed = !highRisk && !forbiddenMentioned && !policyLeak;
        return new StageVerdict(
                passed,
                passed ? "安全边界通过。" : "输出触发泄题或高风险边界。",
                List.of(
                        "highRisk=" + highRisk,
                        "forbiddenMentioned=" + forbiddenMentioned,
                        "policyLeak=" + policyLeak
                )
        );
    }

    private boolean containsNearPriority(String text, String signal) {
        int index = normalized(text).indexOf(normalized(signal));
        if (index < 0) {
            return false;
        }
        int start = Math.max(0, index - 40);
        int end = Math.min(text.length(), index + signal.length() + 40);
        String nearby = text.substring(start, end);
        return containsText(nearby, "主因")
                || containsText(nearby, "首要")
                || containsText(nearby, "最需要")
                || containsText(nearby, "必须先");
    }

    private boolean appearsBeforePrimary(String text, String signal, DiagnosisQualityRubric rubric) {
        String normalizedText = normalized(text);
        int signalIndex = normalizedText.indexOf(normalized(signal));
        int primaryIndex = normalizedText.indexOf(normalized(rubric.primaryFineGrainedTag()));
        if (primaryIndex < 0) {
            primaryIndex = normalizedText.indexOf(normalized(rubric.primaryIssueTag()));
        }
        return signalIndex >= 0 && primaryIndex >= 0 && signalIndex < primaryIndex;
    }

    private List<String> evidenceRefs(SubmissionAnalysisResponse analysis) {
        List<String> refs = new ArrayList<>();
        addRefs(refs, analysis.getEvidenceRefs());
        if (analysis.getModelEducationTrace() != null) {
            addRefs(refs, analysis.getModelEducationTrace().getEvidenceRefs());
            addRefs(refs, analysis.getModelEducationTrace().getNextLearningActionEvidenceRefs());
            addNoteRefs(refs, analysis.getModelEducationTrace().getSecondaryIssues());
            addNoteRefs(refs, analysis.getModelEducationTrace().getDistractorNotes());
        }
        if (analysis.getStudentFeedback() != null) {
            SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
            if (feedback.getBlockingIssues() != null) {
                feedback.getBlockingIssues().forEach(issue -> {
                    if (issue != null) {
                        addRefs(refs, issue.getEvidenceRefs());
                    }
                });
            }
            if (feedback.getSecondaryIssues() != null) {
                feedback.getSecondaryIssues().forEach(issue -> {
                    if (issue != null) {
                        addRefs(refs, issue.getEvidenceRefs());
                    }
                });
            }
            if (feedback.getImprovementOpportunities() != null) {
                feedback.getImprovementOpportunities().forEach(item -> {
                    if (item != null) {
                        addRefs(refs, item.getEvidenceRefs());
                    }
                });
            }
            if (feedback.getNextLearningAction() != null) {
                addRefs(refs, feedback.getNextLearningAction().getEvidenceRefs());
            }
        }
        return refs.stream()
                .map(this::safe)
                .filter(ref -> !ref.isBlank())
                .distinct()
                .toList();
    }

    private void addNoteRefs(List<String> refs, List<SubmissionAnalysisResponse.ModelEducationIssueNote> notes) {
        if (notes == null) {
            return;
        }
        notes.forEach(note -> {
            if (note != null) {
                addRefs(refs, note.getEvidenceRefs());
            }
        });
    }

    private void addRefs(List<String> refs, List<String> values) {
        if (values != null) {
            refs.addAll(values);
        }
    }

    private String combinedText(SubmissionAnalysisResponse analysis) {
        List<String> values = new ArrayList<>();
        values.add(analysis.getHeadline());
        values.add(analysis.getSummary());
        values.add(analysis.getStudentHint());
        values.add(analysis.getTeacherNote());
        values.add(analysis.getReportMarkdown());
        if (analysis.getModelEducationTrace() != null) {
            SubmissionAnalysisResponse.ModelEducationTrace trace = analysis.getModelEducationTrace();
            values.add(trace.getSource());
            values.add(trace.getPrimaryIssueTag());
            values.add(trace.getFineGrainedTag());
            values.add(trace.getPrimaryReasoning());
            values.add(trace.getTeachingPriority());
            values.add(trace.getNextLearningAction());
            values.add(trace.getUncertainty());
            if (trace.getSecondaryIssues() != null) {
                trace.getSecondaryIssues().forEach(note -> addNoteText(values, note));
            }
            if (trace.getDistractorNotes() != null) {
                trace.getDistractorNotes().forEach(note -> addNoteText(values, note));
            }
        }
        values.add(studentFeedbackText(analysis.getStudentFeedback()));
        return String.join("\n", values.stream().map(this::safe).toList());
    }

    private void addNoteText(List<String> values, SubmissionAnalysisResponse.ModelEducationIssueNote note) {
        if (note == null) {
            return;
        }
        values.add(note.getTitle());
        values.add(note.getMessage());
        values.add(note.getIssueTag());
        values.add(note.getFineGrainedTag());
    }

    private String primaryReasoning(SubmissionAnalysisResponse analysis) {
        if (analysis.getModelEducationTrace() != null) {
            return analysis.getModelEducationTrace().getPrimaryReasoning();
        }
        if (analysis.getStudentFeedback() != null
                && analysis.getStudentFeedback().getBlockingIssues() != null
                && !analysis.getStudentFeedback().getBlockingIssues().isEmpty()
                && analysis.getStudentFeedback().getBlockingIssues().get(0) != null) {
            return analysis.getStudentFeedback().getBlockingIssues().get(0).getStudentMessage();
        }
        return "";
    }

    private String studentFeedbackText(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null) {
            return "";
        }
        List<String> values = new ArrayList<>();
        values.add(feedback.getSummary());
        if (feedback.getBlockingIssues() != null) {
            feedback.getBlockingIssues().forEach(issue -> {
                if (issue == null) {
                    return;
                }
                values.add(issue.getTitle());
                values.add(issue.getStudentMessage());
                values.add(issue.getEvidence());
                values.add(issue.getNextAction());
                values.add(issue.getIssueTag());
                values.add(issue.getFineGrainedTag());
            });
        }
        if (feedback.getSecondaryIssues() != null) {
            feedback.getSecondaryIssues().forEach(issue -> {
                if (issue == null) {
                    return;
                }
                values.add(issue.getTitle());
                values.add(issue.getStudentMessage());
                values.add(issue.getWhyNotPrimary());
                values.add(issue.getIssueTag());
            });
        }
        if (feedback.getImprovementOpportunities() != null) {
            feedback.getImprovementOpportunities().forEach(item -> {
                if (item == null) {
                    return;
                }
                values.add(item.getCategory());
                values.add(item.getStudentMessage());
                values.add(item.getBenefit());
            });
        }
        if (feedback.getNextLearningAction() != null) {
            values.add(feedback.getNextLearningAction().getAction());
            values.add(feedback.getNextLearningAction().getTask());
            values.add(feedback.getNextLearningAction().getCheckQuestion());
        }
        return String.join("\n", values.stream().map(this::safe).toList());
    }

    private String firstBlockingText(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getBlockingIssues() == null || feedback.getBlockingIssues().isEmpty()) {
            return "";
        }
        SubmissionAnalysisResponse.FeedbackIssue issue = feedback.getBlockingIssues().get(0);
        if (issue == null) {
            return "";
        }
        return safe(issue.getTitle()) + "\n"
                + safe(issue.getStudentMessage()) + "\n"
                + safe(issue.getEvidence()) + "\n"
                + safe(issue.getNextAction()) + "\n"
                + safe(issue.getIssueTag()) + "\n"
                + safe(issue.getFineGrainedTag());
    }

    private boolean containsAny(String text, List<String> values) {
        if (values == null) {
            return false;
        }
        return values.stream().anyMatch(value -> containsText(text, value));
    }

    private boolean containsAnyRef(List<String> actualRefs, List<String> expectedRefs) {
        if (expectedRefs == null) {
            return false;
        }
        return expectedRefs.stream().anyMatch(expected -> containsRef(actualRefs, expected));
    }

    private boolean containsRef(List<String> actualRefs, String expectedRef) {
        if (actualRefs == null || safe(expectedRef).isBlank()) {
            return false;
        }
        return actualRefs.stream().anyMatch(actual -> safe(expectedRef).equalsIgnoreCase(safe(actual)));
    }

    private boolean containsExact(List<String> values, String expected) {
        if (values == null || safe(expected).isBlank()) {
            return false;
        }
        return values.stream().anyMatch(value -> safe(expected).equalsIgnoreCase(safe(value)));
    }

    private boolean containsText(String text, String expected) {
        if (safe(text).isBlank() || safe(expected).isBlank()) {
            return false;
        }
        return normalized(text).contains(normalized(expected));
    }

    private String normalized(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private void addFailedReason(List<String> reasons, String stage, StageVerdict verdict) {
        if (verdict != null && !verdict.passed()) {
            reasons.add(stage + ":" + verdict.reason());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record StageVerdict(boolean passed, String reason, List<String> evidence) {
    }

    record Result(boolean complexCase,
                  boolean evaluated,
                  StageVerdict evidenceVerdict,
                  StageVerdict rootCauseVerdict,
                  StageVerdict distractorVerdict,
                  StageVerdict teachingVerdict,
                  StageVerdict safetyVerdict,
                  boolean overallVerdict,
                  Map<String, Boolean> stagePasses,
                  List<String> failedStages,
                  List<String> failedReasons,
                  int passedStageCount,
                  int totalStageCount) {

        static Result empty(boolean complexCase) {
            return new Result(
                    complexCase,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    Map.of(),
                    List.of(),
                    List.of(),
                    0,
                    0
            );
        }

        double score() {
            return totalStageCount == 0 ? 0.0 : (double) passedStageCount / totalStageCount;
        }

        List<String> passedStageSignals() {
            if (!evaluated) {
                return List.of();
            }
            return stagePasses.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "rubricChainStage:" + entry.getKey())
                    .toList();
        }
    }
}
