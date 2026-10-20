package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ComplexDiagnosisQualityScorer {

    static final List<String> METRICS = List.of(
            "primaryRootCauseHit",
            "teachingPriorityCorrect",
            "secondaryIssuesNotOverweighted",
            "distractingSignalsIgnored",
            "evidenceGrounded",
            "noFullSolutionLeak"
    );

    static final List<String> INTELLIGENCE_METRICS = List.of(
            "autonomousRootCauseDiscovery",
            "teachingDecisionQuality",
            "complexSignalPrioritization",
            "distractorResistance",
            "evidenceGroundedReasoning",
            "modelSafetyAndBoundary"
    );

    static final List<String> STUDENT_FEEDBACK_METRICS = List.of(
            "blockingPrimaryHit",
            "secondaryIssueBalanced",
            "improvementOpportunityUseful",
            "evidenceGrounded",
            "studentActionable",
            "noSolutionLeak",
            "fallbackHonesty"
    );

    static final List<String> EDUCATION_AGENT_METRICS = List.of(
            "primaryReasoningGrounded",
            "blockingPriorityClear",
            "secondarySignalsBalanced",
            "nextActionObservable",
            "safeTeachingBoundary"
    );

    static final List<String> MODEL_TRACE_METRICS = List.of(
            "nativeRootCauseDecisionChecklistApplied",
            "nativePrimaryReasoningGrounded",
            "nativeTeachingPriorityClear",
            "nativeSecondarySignalsBalanced",
            "nativeNextActionObservable",
            "nativeSafetyBoundary"
    );

    static final Map<String, String> INTELLIGENCE_METRIC_BY_COMPLEX_METRIC = Map.of(
            "primaryRootCauseHit", "autonomousRootCauseDiscovery",
            "secondaryIssuesNotOverweighted", "complexSignalPrioritization",
            "evidenceGrounded", "evidenceGroundedReasoning",
            "teachingPriorityCorrect", "teachingDecisionQuality",
            "distractingSignalsIgnored", "distractorResistance",
            "noFullSolutionLeak", "modelSafetyAndBoundary"
    );

    Result score(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture,
                 SubmissionAnalysisResponse analysis) {
        if (fixture == null || analysis == null) {
            return Result.empty();
        }
        Map<String, Boolean> metrics = new LinkedHashMap<>();
        String primaryFineTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().fineGrainedTag());
        String primaryIssueTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().issueTag());
        String combinedText = combinedText(analysis);

        metrics.put("primaryRootCauseHit", containsIgnoreCase(analysis.getFineGrainedTags(), primaryFineTag));
        metrics.put("teachingPriorityCorrect", teachingPriorityCorrect(fixture, analysis, combinedText, primaryFineTag, primaryIssueTag));
        metrics.put("secondaryIssuesNotOverweighted", secondaryIssuesNotOverweighted(analysis, primaryFineTag));
        metrics.put("distractingSignalsIgnored", avoidsSignals(combinedText, fixture.distractingSignals()));
        metrics.put("evidenceGrounded", evidenceGrounded(analysis.getEvidenceRefs(), fixture.requiredEvidenceRefs()));
        metrics.put("noFullSolutionLeak", noFullSolutionLeak(analysis, combinedText, fixture.mustNotMention()));

        List<String> failedMetrics = metrics.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        int passedCount = (int) metrics.values().stream().filter(Boolean.TRUE::equals).count();
        return new Result(true, metrics, failedMetrics, passedCount, metrics.size());
    }

    IntelligenceResult intelligence(Result complexQuality, boolean modelCompleted, boolean fallbackUsed) {
        if (complexQuality == null || !complexQuality.complexCase()) {
            return IntelligenceResult.empty(false);
        }
        boolean evaluated = modelCompleted && !fallbackUsed;
        Map<String, Boolean> metrics = mapIntelligenceMetrics(complexQuality.metrics());
        if (!evaluated) {
            return new IntelligenceResult(true, false, metrics, List.of(), 0);
        }
        List<String> failedMetrics = metrics.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        int passedCount = (int) metrics.values().stream().filter(Boolean.TRUE::equals).count();
        return new IntelligenceResult(true, true, metrics, failedMetrics, passedCount);
    }

    StudentFeedbackResult studentFeedback(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture,
                                          SubmissionAnalysisResponse analysis,
                                          boolean modelCompleted,
                                          boolean fallbackUsed) {
        if (fixture == null || analysis == null) {
            return StudentFeedbackResult.empty(false, false);
        }
        boolean evaluated = modelCompleted && !fallbackUsed;
        Map<String, Boolean> metrics = new LinkedHashMap<>();
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
        String primaryFineTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().fineGrainedTag());
        String primaryIssueTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().issueTag());
        String feedbackText = studentFeedbackText(feedback);
        metrics.put("blockingPrimaryHit", feedback != null
                && feedback.getBlockingIssues() != null
                && !feedback.getBlockingIssues().isEmpty()
                && (containsText(feedbackText, primaryFineTag)
                || containsText(feedbackText, primaryIssueTag)
                || fixture.mustMention().stream().anyMatch(token -> containsText(feedbackText, token))));
        metrics.put("secondaryIssueBalanced", secondaryIssueBalanced(feedback));
        metrics.put("improvementOpportunityUseful", feedback != null
                && feedback.getImprovementOpportunities() != null
                && feedback.getImprovementOpportunities().stream().anyMatch(item ->
                item != null && !safe(item.getCategory()).isBlank()
                        && !safe(item.getBenefit()).isBlank()
                        && !containsText(safe(item.getStudentMessage()), primaryFineTag)));
        metrics.put("evidenceGrounded", feedbackEvidenceGrounded(feedback, fixture.requiredEvidenceRefs()));
        metrics.put("studentActionable", feedback != null
                && feedback.getNextLearningAction() != null
                && !safe(feedback.getNextLearningAction().getTask()).isBlank()
                && !safe(feedback.getNextLearningAction().getCheckQuestion()).isBlank());
        metrics.put("noSolutionLeak", noFullSolutionLeak(analysis, feedbackText, fixture.mustNotMention()));
        metrics.put("fallbackHonesty", evaluated || fallbackUsed);
        if (!evaluated) {
            return new StudentFeedbackResult(true, false, metrics, List.of(), 0);
        }
        List<String> failedMetrics = metrics.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        int passedCount = (int) metrics.values().stream().filter(Boolean.TRUE::equals).count();
        return new StudentFeedbackResult(true, true, metrics, failedMetrics, passedCount);
    }

    EducationAgentQualityResult educationAgentQuality(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture,
                                                      SubmissionAnalysisResponse analysis,
                                                      boolean modelCompleted,
                                                      boolean fallbackUsed) {
        if (fixture == null || analysis == null) {
            return EducationAgentQualityResult.empty(false);
        }
        boolean evaluated = modelCompleted && !fallbackUsed;
        if (!evaluated) {
            return EducationAgentQualityResult.empty(true);
        }
        Map<String, Boolean> metrics = new LinkedHashMap<>();
        SubmissionAnalysisResponse.StudentFeedback feedback = analysis.getStudentFeedback();
        String primaryFineTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().fineGrainedTag());
        String primaryIssueTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().issueTag());
        String feedbackText = studentFeedbackText(feedback);
        metrics.put("primaryReasoningGrounded", primaryReasoningGrounded(
                feedback,
                fixture.requiredEvidenceRefs(),
                primaryFineTag,
                primaryIssueTag,
                fixture.mustMention()
        ));
        metrics.put("blockingPriorityClear", blockingPriorityClear(feedback, feedbackText, primaryFineTag, primaryIssueTag));
        metrics.put("secondarySignalsBalanced", secondaryIssueBalanced(feedback));
        metrics.put("nextActionObservable", nextActionObservable(feedback));
        metrics.put("safeTeachingBoundary", noFullSolutionLeak(analysis, feedbackText, fixture.mustNotMention()));

        List<String> failedMetrics = metrics.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        int passedCount = (int) metrics.values().stream().filter(Boolean.TRUE::equals).count();
        return new EducationAgentQualityResult(true, true, metrics, failedMetrics, passedCount);
    }

    ModelTraceQualityResult modelEducationTrace(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture,
                                                SubmissionAnalysisResponse analysis,
                                                boolean modelCompleted,
                                                boolean fallbackUsed) {
        if (fixture == null || analysis == null) {
            return ModelTraceQualityResult.empty(false);
        }
        boolean evaluated = modelCompleted && !fallbackUsed && analysis.getModelEducationTrace() != null;
        if (!evaluated) {
            return ModelTraceQualityResult.empty(true);
        }
        SubmissionAnalysisResponse.ModelEducationTrace trace = analysis.getModelEducationTrace();
        Map<String, Boolean> metrics = new LinkedHashMap<>();
        String primaryFineTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().fineGrainedTag());
        String primaryIssueTag = fixture.primaryRootCause() == null
                ? ""
                : safe(fixture.primaryRootCause().issueTag());
        String traceText = modelEducationTraceText(trace);
        metrics.put("nativeRootCauseDecisionChecklistApplied", nativeRootCauseDecisionChecklistApplied(
                trace,
                fixture.requiredEvidenceRefs(),
                fixture.distractingSignals()
        ));
        metrics.put("nativePrimaryReasoningGrounded", nativePrimaryReasoningGrounded(
                trace,
                fixture.requiredEvidenceRefs(),
                primaryFineTag,
                primaryIssueTag,
                fixture.mustMention()
        ));
        metrics.put("nativeTeachingPriorityClear", nativeTeachingPriorityClear(
                trace,
                traceText,
                fixture.expectedTeachingPriority(),
                primaryFineTag,
                primaryIssueTag,
                fixture.mustMention()
        ));
        metrics.put("nativeSecondarySignalsBalanced", nativeSecondarySignalsBalanced(trace));
        metrics.put("nativeNextActionObservable", nativeNextActionObservable(trace));
        metrics.put("nativeSafetyBoundary", nativeSafetyBoundary(trace, fixture.mustNotMention()));

        List<String> failedMetrics = metrics.entrySet().stream()
                .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        int passedCount = (int) metrics.values().stream().filter(Boolean.TRUE::equals).count();
        return new ModelTraceQualityResult(true, true, metrics, failedMetrics, passedCount);
    }

    private Map<String, Boolean> mapIntelligenceMetrics(Map<String, Boolean> complexMetrics) {
        Map<String, Boolean> mapped = new LinkedHashMap<>();
        if (complexMetrics == null || complexMetrics.isEmpty()) {
            return mapped;
        }
        METRICS.forEach(metric -> {
            String intelligenceMetric = INTELLIGENCE_METRIC_BY_COMPLEX_METRIC.get(metric);
            if (intelligenceMetric != null) {
                mapped.put(intelligenceMetric, Boolean.TRUE.equals(complexMetrics.get(metric)));
            }
        });
        return mapped;
    }

    private boolean teachingPriorityCorrect(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture,
                                            SubmissionAnalysisResponse analysis,
                                            String combinedText,
                                            String primaryFineTag,
                                            String primaryIssueTag) {
        boolean primaryMentioned = containsIgnoreCase(analysis.getFineGrainedTags(), primaryFineTag)
                || containsIgnoreCase(analysis.getIssueTags(), primaryIssueTag)
                || containsText(combinedText, primaryFineTag)
                || containsText(combinedText, primaryIssueTag);
        boolean teachingTokenMentioned = fixture.mustMention() != null && fixture.mustMention().stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> containsText(combinedText, value));
        return primaryMentioned && teachingTokenMentioned;
    }

    private boolean secondaryIssuesNotOverweighted(SubmissionAnalysisResponse analysis, String primaryFineTag) {
        List<String> fineTags = analysis.getFineGrainedTags() == null ? List.of() : analysis.getFineGrainedTags();
        if (fineTags.isEmpty() || primaryFineTag.isBlank()) {
            return false;
        }
        return primaryFineTag.equalsIgnoreCase(safe(fineTags.get(0)));
    }

    private boolean secondaryIssueBalanced(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getSecondaryIssues() == null || feedback.getSecondaryIssues().isEmpty()) {
            return true;
        }
        return feedback.getSecondaryIssues().stream()
                .filter(issue -> issue != null)
                .allMatch(issue -> {
                    String why = safe(issue.getWhyNotPrimary());
                    String text = safe(issue.getTitle()) + "\n" + safe(issue.getStudentMessage()) + "\n" + why;
                    boolean hasBalanceReason = !why.isBlank()
                            || containsText(text, "不是主因")
                            || containsText(text, "不应优先")
                            || containsText(text, "先放到后面")
                            || containsText(text, "次要");
                    boolean overweightsSecondary = containsText(text, "必须先改")
                            || containsText(text, "最需要先处理")
                            || containsText(text, "首要问题");
                    return hasBalanceReason && !overweightsSecondary;
                });
    }

    private boolean primaryReasoningGrounded(SubmissionAnalysisResponse.StudentFeedback feedback,
                                             List<String> requiredRefs,
                                             String primaryFineTag,
                                             String primaryIssueTag,
                                             List<String> mustMention) {
        if (feedback == null || feedback.getBlockingIssues() == null || feedback.getBlockingIssues().isEmpty()) {
            return false;
        }
        SubmissionAnalysisResponse.FeedbackIssue firstIssue = feedback.getBlockingIssues().get(0);
        if (firstIssue == null) {
            return false;
        }
        String text = safe(firstIssue.getTitle()) + "\n"
                + safe(firstIssue.getStudentMessage()) + "\n"
                + safe(firstIssue.getEvidence()) + "\n"
                + safe(firstIssue.getNextAction()) + "\n"
                + safe(firstIssue.getIssueTag()) + "\n"
                + safe(firstIssue.getFineGrainedTag());
        boolean primaryMentioned = containsText(text, primaryFineTag)
                || containsText(text, primaryIssueTag)
                || (mustMention != null && mustMention.stream().anyMatch(token -> containsText(text, token)));
        boolean evidenceMentioned = firstIssue.getEvidenceRefs() != null && requiredRefs != null
                && requiredRefs.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(required -> firstIssue.getEvidenceRefs().stream()
                        .anyMatch(actual -> required.equalsIgnoreCase(safe(actual))));
        return primaryMentioned && evidenceMentioned;
    }

    private boolean blockingPriorityClear(SubmissionAnalysisResponse.StudentFeedback feedback,
                                          String feedbackText,
                                          String primaryFineTag,
                                          String primaryIssueTag) {
        if (feedback == null || feedback.getBlockingIssues() == null || feedback.getBlockingIssues().isEmpty()) {
            return false;
        }
        SubmissionAnalysisResponse.FeedbackIssue firstIssue = feedback.getBlockingIssues().get(0);
        if (firstIssue == null) {
            return false;
        }
        boolean firstIssuePrimary = containsText(safe(firstIssue.getFineGrainedTag()), primaryFineTag)
                || containsText(safe(firstIssue.getIssueTag()), primaryIssueTag)
                || containsText(safe(firstIssue.getStudentMessage()), primaryFineTag)
                || containsText(safe(firstIssue.getStudentMessage()), primaryIssueTag);
        boolean priorityLanguage = containsText(feedbackText, "先")
                || containsText(feedbackText, "优先")
                || containsText(feedbackText, "当前")
                || containsText(feedbackText, "第一")
                || containsText(feedbackText, "最需要");
        boolean hasConcreteNextAction = !safe(firstIssue.getNextAction()).isBlank()
                || feedback.getNextLearningAction() != null && !safe(feedback.getNextLearningAction().getTask()).isBlank();
        return firstIssuePrimary && priorityLanguage && hasConcreteNextAction;
    }

    private boolean nextActionObservable(SubmissionAnalysisResponse.StudentFeedback feedback) {
        if (feedback == null || feedback.getNextLearningAction() == null) {
            return false;
        }
        SubmissionAnalysisResponse.NextLearningAction action = feedback.getNextLearningAction();
        String text = safe(action.getAction()) + "\n" + safe(action.getTask()) + "\n" + safe(action.getCheckQuestion());
        boolean hasTaskAndCheck = !safe(action.getTask()).isBlank() && !safe(action.getCheckQuestion()).isBlank();
        boolean observable = containsText(text, "对比")
                || containsText(text, "比较")
                || containsText(text, "追踪")
                || containsText(text, "手推")
                || containsText(text, "构造")
                || containsText(text, "估算")
                || containsText(text, "检查")
                || containsText(text, "列出")
                || containsText(text, "计数")
                || containsText(text, "数一数")
                || containsText(text, "核对")
                || containsText(text, "验证");
        boolean grounded = action.getEvidenceRefs() != null && !action.getEvidenceRefs().isEmpty();
        return hasTaskAndCheck && observable && grounded;
    }

    private boolean nativePrimaryReasoningGrounded(SubmissionAnalysisResponse.ModelEducationTrace trace,
                                                   List<String> requiredRefs,
                                                   String primaryFineTag,
                                                   String primaryIssueTag,
                                                   List<String> mustMention) {
        if (trace == null) {
            return false;
        }
        String text = modelEducationTraceText(trace);
        boolean primaryTagHit = containsText(safe(trace.getFineGrainedTag()), primaryFineTag)
                || containsText(safe(trace.getPrimaryIssueTag()), primaryIssueTag)
                || containsText(text, primaryFineTag)
                || containsText(text, primaryIssueTag);
        boolean teachingTokenMentioned = mustMention != null && mustMention.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> containsText(text, value));
        boolean evidenceMentioned = evidenceGroundedAny(modelEducationTraceEvidenceRefs(trace), requiredRefs);
        return primaryTagHit && teachingTokenMentioned && evidenceMentioned
                && !safe(trace.getPrimaryReasoning()).isBlank();
    }

    private boolean nativeRootCauseDecisionChecklistApplied(SubmissionAnalysisResponse.ModelEducationTrace trace,
                                                           List<String> requiredRefs,
                                                           List<String> distractingSignals) {
        if (trace == null) {
            return false;
        }
        String text = modelEducationTraceText(trace);
        boolean evidenceLocated = evidenceGroundedAny(modelEducationTraceEvidenceRefs(trace), requiredRefs)
                || containsText(text, "first failed")
                || containsText(text, "失败证据")
                || containsText(text, "判题")
                || containsText(text, "实际输出")
                || containsText(text, "期望输出");
        boolean codeBehaviorConnected = containsText(text, "读取")
                || containsText(text, "读入")
                || containsText(text, "输出")
                || containsText(text, "打印")
                || containsText(text, "循环")
                || containsText(text, "处理")
                || containsText(text, "跳过")
                || containsText(text, "重复")
                || containsText(text, "计算")
                || containsText(text, "状态")
                || containsText(text, "边界")
                || containsText(text, "复杂度")
                || containsText(text, "code behavior")
                || containsText(text, "read operation");
        boolean priorityChosen = containsText(text, "先")
                || containsText(text, "优先")
                || containsText(text, "当前")
                || containsText(text, "第一")
                || containsText(text, "主因")
                || containsText(text, "root cause");
        boolean hasDistractorContext = distractingSignals != null
                && distractingSignals.stream().anyMatch(value -> value != null && !value.isBlank());
        boolean explicitDemotion = hasNotes(trace.getSecondaryIssues())
                || hasNotes(trace.getDistractorNotes())
                || containsText(text, "不是主因")
                || containsText(text, "不应优先")
                || containsText(text, "先放到后面")
                || containsText(text, "次要")
                || containsText(text, "干扰")
                || containsText(text, "不能解释");
        boolean distractorsDemoted = nativeSecondarySignalsBalanced(trace)
                && (!hasDistractorContext || explicitDemotion);
        boolean observableNextAction = nativeNextActionObservable(trace);
        return evidenceLocated
                && codeBehaviorConnected
                && priorityChosen
                && distractorsDemoted
                && observableNextAction;
    }

    private boolean nativeTeachingPriorityClear(SubmissionAnalysisResponse.ModelEducationTrace trace,
                                                String traceText,
                                                String expectedTeachingPriority,
                                                String primaryFineTag,
                                                String primaryIssueTag,
                                                List<String> mustMention) {
        if (trace == null || safe(trace.getTeachingPriority()).isBlank()) {
            return false;
        }
        boolean primaryMentioned = containsText(traceText, primaryFineTag)
                || containsText(traceText, primaryIssueTag)
                || (mustMention != null && mustMention.stream().anyMatch(token -> containsText(traceText, token)));
        boolean expectedPriorityOverlap = !safe(expectedTeachingPriority).isBlank()
                && tokenOverlapCount(trace.getTeachingPriority(), expectedTeachingPriority) >= 2;
        boolean priorityLanguage = containsText(trace.getTeachingPriority(), "先")
                || containsText(trace.getTeachingPriority(), "优先")
                || containsText(trace.getTeachingPriority(), "当前")
                || containsText(trace.getTeachingPriority(), "第一")
                || containsText(trace.getTeachingPriority(), "最需要");
        return primaryMentioned && (expectedPriorityOverlap || priorityLanguage);
    }

    private boolean nativeSecondarySignalsBalanced(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null) {
            return false;
        }
        List<SubmissionAnalysisResponse.ModelEducationIssueNote> notes = new ArrayList<>();
        if (trace.getSecondaryIssues() != null) {
            notes.addAll(trace.getSecondaryIssues());
        }
        if (trace.getDistractorNotes() != null) {
            notes.addAll(trace.getDistractorNotes());
        }
        if (notes.isEmpty()) {
            return true;
        }
        return notes.stream()
                .filter(note -> note != null)
                .allMatch(note -> {
                    String text = safe(note.getTitle()) + "\n"
                            + safe(note.getMessage()) + "\n"
                            + safe(note.getIssueTag()) + "\n"
                            + safe(note.getFineGrainedTag());
                    boolean hasBalanceReason = containsText(text, "不是主因")
                            || containsText(text, "不应优先")
                            || containsText(text, "先放到后面")
                            || containsText(text, "次要")
                            || containsText(text, "干扰")
                            || containsText(text, "不能解释");
                    boolean overweightsSecondary = containsText(text, "必须先改")
                            || containsText(text, "最需要先处理")
                            || containsText(text, "首要问题")
                            || containsText(text, "主因是")
                            || containsText(text, "作为主因")
                            || containsText(text, "应当是主因");
                    return hasBalanceReason && !overweightsSecondary;
                });
    }

    private boolean hasNotes(List<SubmissionAnalysisResponse.ModelEducationIssueNote> notes) {
        return notes != null && notes.stream().anyMatch(note -> note != null && !safe(note.getMessage()).isBlank());
    }

    private boolean nativeNextActionObservable(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null || safe(trace.getNextLearningAction()).isBlank()) {
            return false;
        }
        String text = safe(trace.getNextLearningAction());
        boolean observable = containsText(text, "对比")
                || containsText(text, "比较")
                || containsText(text, "追踪")
                || containsText(text, "手推")
                || containsText(text, "构造")
                || containsText(text, "估算")
                || containsText(text, "检查")
                || containsText(text, "列出")
                || containsText(text, "计数")
                || containsText(text, "数一数")
                || containsText(text, "核对")
                || containsText(text, "验证");
        boolean grounded = trace.getNextLearningActionEvidenceRefs() != null
                && !trace.getNextLearningActionEvidenceRefs().isEmpty();
        return observable && grounded;
    }

    private boolean nativeSafetyBoundary(SubmissionAnalysisResponse.ModelEducationTrace trace,
                                         List<String> mustNotMention) {
        if (trace == null || "HIGH".equalsIgnoreCase(safe(trace.getAnswerLeakRisk()))) {
            return false;
        }
        String text = modelEducationTraceText(trace);
        List<String> forbidden = mustNotMention == null ? List.of() : mustNotMention;
        boolean fixtureBoundarySafe = forbidden.stream()
                .filter(value -> value != null && !value.isBlank())
                .noneMatch(value -> containsText(text, value));
        return fixtureBoundarySafe && !ModelOutputSafetyPolicy.containsUnsafeLeak(text);
    }

    private boolean avoidsSignals(String combinedText, List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return true;
        }
        return signals.stream()
                .filter(value -> value != null && !value.isBlank())
                .noneMatch(value -> containsText(combinedText, value));
    }

    private boolean evidenceGrounded(List<String> actualRefs, List<String> requiredRefs) {
        if (actualRefs == null || actualRefs.isEmpty() || requiredRefs == null || requiredRefs.isEmpty()) {
            return false;
        }
        return requiredRefs.stream()
                .filter(value -> value != null && !value.isBlank())
                .allMatch(required -> actualRefs.stream().anyMatch(actual -> required.equalsIgnoreCase(safe(actual))));
    }

    private boolean evidenceGroundedAny(List<String> actualRefs, List<String> requiredRefs) {
        if (actualRefs == null || actualRefs.isEmpty() || requiredRefs == null || requiredRefs.isEmpty()) {
            return false;
        }
        return requiredRefs.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(required -> actualRefs.stream().anyMatch(actual -> required.equalsIgnoreCase(safe(actual))));
    }

    private boolean noFullSolutionLeak(SubmissionAnalysisResponse analysis,
                                       String combinedText,
                                       List<String> mustNotMention) {
        if ("HIGH".equalsIgnoreCase(safe(analysis.getAnswerLeakRisk()))) {
            return false;
        }
        List<String> forbidden = mustNotMention == null ? List.of() : mustNotMention;
        return forbidden.stream()
                .filter(value -> value != null && !value.isBlank())
                .noneMatch(value -> containsText(combinedText, value));
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || expected == null || expected.isBlank()) {
            return false;
        }
        return values.stream().anyMatch(value -> expected.equalsIgnoreCase(safe(value)));
    }

    private boolean containsText(String text, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private String combinedText(SubmissionAnalysisResponse analysis) {
        List<String> values = new ArrayList<>();
        values.add(analysis.getHeadline());
        values.add(analysis.getSummary());
        values.add(analysis.getStudentHint());
        values.add(analysis.getTeacherNote());
        values.add(analysis.getReportMarkdown());
        if (analysis.getStudentHintPlan() != null) {
            values.add(analysis.getStudentHintPlan().getProblemType());
            values.add(analysis.getStudentHintPlan().getEvidenceAnchor());
            values.add(analysis.getStudentHintPlan().getNextAction());
            values.add(analysis.getStudentHintPlan().getCoachQuestion());
            values.add(analysis.getStudentHintPlan().getTeachingAction());
        }
        if (analysis.getLearningInterventionPlan() != null) {
            values.add(analysis.getLearningInterventionPlan().getGoal());
            values.add(analysis.getLearningInterventionPlan().getStudentTask());
            values.add(analysis.getLearningInterventionPlan().getCheckQuestion());
            values.add(analysis.getLearningInterventionPlan().getCompletionSignal());
        }
        values.add(studentFeedbackText(analysis.getStudentFeedback()));
        return String.join("\n", values.stream().map(this::safe).toList());
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

    private String modelEducationTraceText(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null) {
            return "";
        }
        List<String> values = new ArrayList<>();
        values.add(trace.getSource());
        values.add(trace.getPrimaryIssueTag());
        values.add(trace.getFineGrainedTag());
        values.add(trace.getPrimaryReasoning());
        values.add(trace.getTeachingPriority());
        values.add(trace.getNextLearningAction());
        values.add(trace.getUncertainty());
        if (trace.getImprovementCategories() != null) {
            values.addAll(trace.getImprovementCategories());
        }
        if (trace.getSecondaryIssues() != null) {
            trace.getSecondaryIssues().forEach(note -> addModelEducationNoteText(values, note));
        }
        if (trace.getDistractorNotes() != null) {
            trace.getDistractorNotes().forEach(note -> addModelEducationNoteText(values, note));
        }
        return String.join("\n", values.stream().map(this::safe).toList());
    }

    private void addModelEducationNoteText(List<String> values, SubmissionAnalysisResponse.ModelEducationIssueNote note) {
        if (note == null) {
            return;
        }
        values.add(note.getTitle());
        values.add(note.getMessage());
        values.add(note.getIssueTag());
        values.add(note.getFineGrainedTag());
    }

    private List<String> modelEducationTraceEvidenceRefs(SubmissionAnalysisResponse.ModelEducationTrace trace) {
        if (trace == null) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        if (trace.getEvidenceRefs() != null) {
            refs.addAll(trace.getEvidenceRefs());
        }
        if (trace.getSecondaryIssues() != null) {
            trace.getSecondaryIssues().forEach(note -> {
                if (note != null && note.getEvidenceRefs() != null) {
                    refs.addAll(note.getEvidenceRefs());
                }
            });
        }
        if (trace.getDistractorNotes() != null) {
            trace.getDistractorNotes().forEach(note -> {
                if (note != null && note.getEvidenceRefs() != null) {
                    refs.addAll(note.getEvidenceRefs());
                }
            });
        }
        if (trace.getNextLearningActionEvidenceRefs() != null) {
            refs.addAll(trace.getNextLearningActionEvidenceRefs());
        }
        return refs.stream()
                .filter(ref -> !safe(ref).isBlank())
                .distinct()
                .toList();
    }

    private int tokenOverlapCount(String left, String right) {
        String safeLeft = safe(left);
        String safeRight = safe(right);
        if (safeLeft.isBlank() || safeRight.isBlank()) {
            return 0;
        }
        List<String> rightTokens = List.of(safeRight.split("[\\s，。；、,.!?！？:：]+"));
        int count = 0;
        for (String token : rightTokens) {
            String normalized = safe(token);
            if (normalized.length() >= 2 && containsText(safeLeft, normalized)) {
                count++;
            }
        }
        return count;
    }

    private boolean feedbackEvidenceGrounded(SubmissionAnalysisResponse.StudentFeedback feedback,
                                             List<String> requiredRefs) {
        if (feedback == null || requiredRefs == null || requiredRefs.isEmpty()) {
            return false;
        }
        List<String> refs = new ArrayList<>();
        if (feedback.getBlockingIssues() != null) {
            feedback.getBlockingIssues().forEach(issue -> {
                if (issue != null && issue.getEvidenceRefs() != null) {
                    refs.addAll(issue.getEvidenceRefs());
                }
            });
        }
        if (feedback.getNextLearningAction() != null && feedback.getNextLearningAction().getEvidenceRefs() != null) {
            refs.addAll(feedback.getNextLearningAction().getEvidenceRefs());
        }
        return requiredRefs.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(required -> refs.stream().anyMatch(actual -> required.equalsIgnoreCase(safe(actual))));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record Result(boolean complexCase,
                  Map<String, Boolean> metrics,
                  List<String> failedMetrics,
                  int passedMetricCount,
                  int totalMetricCount) {

        static Result empty() {
            return new Result(false, Map.of(), List.of(), 0, 0);
        }

        boolean passed() {
            return complexCase && passedMetricCount == totalMetricCount && totalMetricCount > 0;
        }

        double score() {
            return totalMetricCount == 0 ? 0.0 : (double) passedMetricCount / totalMetricCount;
        }

        List<String> passedMetricSignals() {
            return metrics.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "complexMetric:" + entry.getKey())
                    .toList();
        }
    }

    record IntelligenceResult(boolean complexCase,
                              boolean evaluated,
                              Map<String, Boolean> metrics,
                              List<String> failedMetrics,
                              int passedMetricCount) {

        static IntelligenceResult empty(boolean complexCase) {
            return new IntelligenceResult(complexCase, false, Map.of(), List.of(), 0);
        }

        int totalMetricCount() {
            return evaluated ? metrics.size() : 0;
        }

        boolean passed() {
            return evaluated && passedMetricCount == totalMetricCount() && totalMetricCount() > 0;
        }

        double score() {
            int total = totalMetricCount();
            return total == 0 ? 0.0 : (double) passedMetricCount / total;
        }

        List<String> passedMetricSignals() {
            if (!evaluated) {
                return List.of();
            }
            return metrics.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "intelligenceMetric:" + entry.getKey())
                    .toList();
        }
    }

    record StudentFeedbackResult(boolean complexCase,
                                 boolean evaluated,
                                 Map<String, Boolean> metrics,
                                 List<String> failedMetrics,
                                 int passedMetricCount) {

        static StudentFeedbackResult empty(boolean complexCase, boolean evaluated) {
            return new StudentFeedbackResult(complexCase, evaluated, Map.of(), List.of(), 0);
        }

        int totalMetricCount() {
            return evaluated ? metrics.size() : 0;
        }

        boolean passed() {
            return evaluated && passedMetricCount == totalMetricCount() && totalMetricCount() > 0;
        }

        double score() {
            int total = totalMetricCount();
            return total == 0 ? 0.0 : (double) passedMetricCount / total;
        }

        List<String> passedMetricSignals() {
            if (!evaluated) {
                return List.of();
            }
            return metrics.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "studentFeedbackMetric:" + entry.getKey())
                    .toList();
        }
    }

    record EducationAgentQualityResult(boolean complexCase,
                                       boolean evaluated,
                                       Map<String, Boolean> metrics,
                                       List<String> failedMetrics,
                                       int passedMetricCount) {

        static EducationAgentQualityResult empty(boolean complexCase) {
            return new EducationAgentQualityResult(complexCase, false, Map.of(), List.of(), 0);
        }

        int totalMetricCount() {
            return evaluated ? metrics.size() : 0;
        }

        boolean passed() {
            return evaluated && passedMetricCount == totalMetricCount() && totalMetricCount() > 0;
        }

        double score() {
            int total = totalMetricCount();
            return total == 0 ? 0.0 : (double) passedMetricCount / total;
        }

        List<String> passedMetricSignals() {
            if (!evaluated) {
                return List.of();
            }
            return metrics.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "educationAgentMetric:" + entry.getKey())
                    .toList();
        }
    }

    record ModelTraceQualityResult(boolean complexCase,
                                   boolean evaluated,
                                   Map<String, Boolean> metrics,
                                   List<String> failedMetrics,
                                   int passedMetricCount) {

        static ModelTraceQualityResult empty(boolean complexCase) {
            return new ModelTraceQualityResult(complexCase, false, Map.of(), List.of(), 0);
        }

        int totalMetricCount() {
            return evaluated ? metrics.size() : 0;
        }

        boolean passed() {
            return evaluated && passedMetricCount == totalMetricCount() && totalMetricCount() > 0;
        }

        double score() {
            int total = totalMetricCount();
            return total == 0 ? 0.0 : (double) passedMetricCount / total;
        }

        List<String> passedMetricSignals() {
            if (!evaluated) {
                return List.of();
            }
            return metrics.entrySet().stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(entry -> "modelTraceMetric:" + entry.getKey())
                    .toList();
        }
    }
}
