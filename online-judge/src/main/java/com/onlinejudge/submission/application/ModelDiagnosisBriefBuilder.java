package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ModelDiagnosisBriefBuilder {

    private static final int MAX_CASE_FACTS = 5;

    public ModelDiagnosisBrief build(DiagnosisEvidencePackage evidencePackage,
                                     SubmissionAnalysisResponse baseline) {
        DiagnosisEvidencePackage.ProblemEvidence problem = evidencePackage == null ? null : evidencePackage.getProblem();
        DiagnosisEvidencePackage.SubmissionEvidence submission = evidencePackage == null ? null : evidencePackage.getSubmission();
        DiagnosisEvidencePackage.JudgeFacts judgeFacts = evidencePackage == null ? null : evidencePackage.getJudgeFacts();

        List<String> evidenceRefs = collectEvidenceRefs(judgeFacts, evidencePackage, baseline);

        return ModelDiagnosisBrief.builder()
                .schemaVersion(ModelDiagnosisBrief.SCHEMA_VERSION)
                .problemBrief(problemBrief(problem))
                .problemConstraints(problemConstraints(problem))
                .verdict(submission == null ? "UNKNOWN" : defaultIfBlank(submission.getVerdict(), "UNKNOWN"))
                .language(submission == null ? "" : defaultIfBlank(submission.getLanguage(), ""))
                .keyCodeExcerpt(codeExcerpt(submission))
                .sourceCodeLineCount(submission == null ? null : submission.getSourceCodeLineCount())
                .firstFailedCase(sanitizedFirstFailedCase(judgeFacts))
                .visibleCaseFacts(visibleCaseFacts(judgeFacts))
                .testIntentFacts(testIntentFacts(judgeFacts))
                .evidenceRefs(evidenceRefs)
                .allowedIssueTags(allowedIssueTags(evidencePackage, baseline))
                .allowedFineGrainedTags(allowedFineTags(evidencePackage, baseline))
                .learningTrajectorySummary(learningTrajectorySummary(evidencePackage, baseline))
                .learningMemorySummary(learningMemorySummary(evidencePackage))
                .teacherCalibrationSummary(teacherCalibrationSummary(evidencePackage))
                .hiddenDataBoundary(hiddenDataBoundary(judgeFacts))
                .uncertainty(uncertainty(judgeFacts))
                .build();
    }

    private String problemBrief(DiagnosisEvidencePackage.ProblemEvidence problem) {
        if (problem == null) {
            return "";
        }
        return String.join("\n",
                defaultIfBlank(problem.getTitle(), ""),
                defaultIfBlank(problem.getAiPromptDirection(), ""),
                defaultIfBlank(problem.getDescription(), ""));
    }

    private String problemConstraints(DiagnosisEvidencePackage.ProblemEvidence problem) {
        if (problem == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (problem.getDifficulty() != null && !problem.getDifficulty().isBlank()) {
            parts.add("difficulty=" + problem.getDifficulty());
        }
        if (problem.getTimeLimit() != null) {
            parts.add("timeLimitMs=" + problem.getTimeLimit());
        }
        if (problem.getMemoryLimit() != null) {
            parts.add("memoryLimitKb=" + problem.getMemoryLimit());
        }
        addNamedList(parts, "knowledgePoints", problem.getKnowledgePoints());
        addNamedList(parts, "algorithmStrategies", problem.getAlgorithmStrategies());
        addNamedList(parts, "commonMistakes", problem.getCommonMistakes());
        addNamedList(parts, "boundaryTypes", problem.getBoundaryTypes());
        return String.join("; ", parts);
    }

    private String codeExcerpt(DiagnosisEvidencePackage.SubmissionEvidence submission) {
        if (submission == null) {
            return "";
        }
        String numbered = defaultIfBlank(submission.getSourceCodeWithLineNumbers(), "");
        String source = defaultIfBlank(submission.getSourceCode(), "");
        return numbered.isBlank() ? source : numbered;
    }

    private SubmissionAnalysisResponse.FailedCaseSnapshot sanitizedFirstFailedCase(DiagnosisEvidencePackage.JudgeFacts judgeFacts) {
        if (judgeFacts == null || judgeFacts.getFirstFailedCase() == null) {
            return null;
        }
        SubmissionAnalysisResponse.FailedCaseSnapshot source = judgeFacts.getFirstFailedCase();
        if (source.isHidden()) {
            return SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                    .testCaseNumber(source.getTestCaseNumber())
                    .hidden(true)
                    .input(null)
                    .expectedOutput(null)
                    .actualOutput(null)
                    .build();
        }
        return source;
    }

    private List<ModelDiagnosisBrief.VisibleCaseFact> visibleCaseFacts(DiagnosisEvidencePackage.JudgeFacts judgeFacts) {
        if (judgeFacts == null || judgeFacts.getCaseResultsSummary() == null) {
            return List.of();
        }
        return judgeFacts.getCaseResultsSummary().stream()
                .limit(MAX_CASE_FACTS)
                .map(item -> ModelDiagnosisBrief.VisibleCaseFact.builder()
                        .testCaseNumber(item.getTestCaseNumber())
                        .passed(item.getPassed())
                        .hidden(item.getHidden())
                        .executionTime(item.getExecutionTime())
                        .memoryUsed(item.getMemoryUsed())
                        .inputPreview(Boolean.TRUE.equals(item.getHidden()) ? null : item.getInputPreview())
                        .actualOutputPreview(Boolean.TRUE.equals(item.getHidden()) ? null : item.getActualOutputPreview())
                        .expectedOutputPreview(Boolean.TRUE.equals(item.getHidden()) ? null : item.getExpectedOutputPreview())
                        .build())
                .toList();
    }

    private List<ModelDiagnosisBrief.TestIntentFact> testIntentFacts(
            DiagnosisEvidencePackage.JudgeFacts judgeFacts) {
        if (judgeFacts == null || judgeFacts.getCaseResultsSummary() == null) {
            return List.of();
        }
        return judgeFacts.getCaseResultsSummary().stream()
                .filter(item -> !isBlank(item.getSemanticCode()))
                .limit(MAX_CASE_FACTS)
                .map(item -> ModelDiagnosisBrief.TestIntentFact.builder()
                        .testCaseNumber(item.getTestCaseNumber())
                        .passed(item.getPassed())
                        .hidden(item.getHidden())
                        .evidenceRef(testIntentEvidenceRef(item.getSemanticCode()))
                        .semanticCode(item.getSemanticCode())
                        .intentType(item.getIntentType())
                        .intentTitle(item.getIntentTitle())
                        .intentSummary(item.getIntentSummary())
                        .learningObjective(item.getLearningObjective())
                        .contestRole(item.getContestRole())
                        .revealPolicy(item.getRevealPolicy())
                        .build())
                .toList();
    }

    private List<String> collectEvidenceRefs(DiagnosisEvidencePackage.JudgeFacts judgeFacts,
                                             DiagnosisEvidencePackage evidencePackage,
                                             SubmissionAnalysisResponse baseline) {
        Set<String> refs = new LinkedHashSet<>();
        if (judgeFacts != null && judgeFacts.getFirstFailedCase() != null) {
            refs.add("judge:first_failed_case");
        }
        if (judgeFacts != null && Boolean.TRUE.equals(judgeFacts.getHiddenFailureObserved())) {
            refs.add("judge:hidden_failure");
        }
        if (judgeFacts != null && judgeFacts.getCaseResultsSummary() != null) {
            judgeFacts.getCaseResultsSummary().stream()
                    .map(DiagnosisEvidencePackage.CaseSummary::getSemanticCode)
                    .filter(code -> !isBlank(code))
                    .limit(MAX_CASE_FACTS)
                    .map(this::testIntentEvidenceRef)
                    .forEach(refs::add);
        }
        if (baseline != null && baseline.getEvidenceRefs() != null) {
            refs.addAll(baseline.getEvidenceRefs());
        }
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                evidencePackage == null ? null : evidencePackage.getLearningMemory();
        if (memory != null && memory.getEvidenceRefs() != null) {
            refs.addAll(memory.getEvidenceRefs());
        }
        return List.copyOf(refs);
    }

    private String testIntentEvidenceRef(String semanticCode) {
        return "judge:test-intent:" + semanticCode;
    }

    private List<String> allowedIssueTags(DiagnosisEvidencePackage evidencePackage,
                                          SubmissionAnalysisResponse baseline) {
        Set<String> tags = new LinkedHashSet<>();
        if (baseline != null && baseline.getIssueTags() != null) {
            tags.addAll(baseline.getIssueTags());
        }
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                evidencePackage == null ? null : evidencePackage.getLearningMemory();
        if (memory != null && memory.getRecurringIssueTags() != null) {
            memory.getRecurringIssueTags().stream()
                    .map(DiagnosisEvidencePackage.MemoryTagStat::getTag)
                    .filter(tag -> tag != null && !tag.isBlank())
                    .forEach(tags::add);
        }
        addTeacherCalibrationIssueTags(tags, memory);
        tags.add("NEEDS_MORE_EVIDENCE");
        return List.copyOf(tags);
    }

    private List<String> allowedFineTags(DiagnosisEvidencePackage evidencePackage,
                                         SubmissionAnalysisResponse baseline) {
        Set<String> tags = new LinkedHashSet<>();
        if (baseline != null && baseline.getFineGrainedTags() != null) {
            tags.addAll(baseline.getFineGrainedTags());
        }
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                evidencePackage == null ? null : evidencePackage.getLearningMemory();
        if (memory != null && memory.getRecurringFineGrainedTags() != null) {
            memory.getRecurringFineGrainedTags().stream()
                    .map(DiagnosisEvidencePackage.MemoryTagStat::getTag)
                    .filter(tag -> tag != null && !tag.isBlank())
                    .forEach(tags::add);
        }
        addTeacherCalibrationFineTags(tags, memory);
        return List.copyOf(tags);
    }

    private String learningTrajectorySummary(DiagnosisEvidencePackage evidencePackage,
                                             SubmissionAnalysisResponse baseline) {
        if (baseline != null && baseline.getLearningTrajectorySignal() != null) {
            SubmissionAnalysisResponse.LearningTrajectorySignal signal = baseline.getLearningTrajectorySignal();
            return String.join(" | ",
                    defaultIfBlank(signal.getPhase(), ""),
                    defaultIfBlank(signal.getSummary(), ""),
                    defaultIfBlank(signal.getNextFocus(), ""));
        }
        DiagnosisEvidencePackage.HistoryEvidence history = evidencePackage == null ? null : evidencePackage.getHistory();
        if (history == null) {
            return "";
        }
        return String.join(" | ",
                "previousVerdict=" + defaultIfBlank(history.getPreviousVerdict(), "UNKNOWN"),
                "repeatedIssueTag=" + defaultIfBlank(history.getRepeatedIssueTag(), ""),
                "transition=" + defaultIfBlank(history.getTransitionSignal(), ""),
                learningActionFeedbackSummary(history));
    }

    private String learningActionFeedbackSummary(DiagnosisEvidencePackage.HistoryEvidence history) {
        if (history == null || isBlank(history.getPreviousInterventionType())) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add("previousIntervention=" + defaultIfBlank(history.getPreviousInterventionType(), ""));
        if (!isBlank(history.getPreviousLearningActionStatus())) {
            parts.add("actionStatus=" + history.getPreviousLearningActionStatus());
        }
        if (history.getPreviousLearningActionConfidence() != null) {
            parts.add("actionConfidence=" + String.format("%.2f", history.getPreviousLearningActionConfidence()));
        }
        if (!isBlank(history.getPreviousLearningActionSummary())) {
            parts.add("actionEvidence=" + history.getPreviousLearningActionSummary());
        }
        if (!isBlank(history.getPreviousLearningActionNextAdjustment())) {
            parts.add("nextAdjustment=" + history.getPreviousLearningActionNextAdjustment());
        }
        return String.join(" | ", parts);
    }

    private String learningMemorySummary(DiagnosisEvidencePackage evidencePackage) {
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                evidencePackage == null ? null : evidencePackage.getLearningMemory();
        if (memory == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (memory.getObservedSubmissionCount() != null) {
            parts.add("observedSubmissions=" + memory.getObservedSubmissionCount());
        }
        if (memory.getObservedProblemCount() != null) {
            parts.add("observedProblems=" + memory.getObservedProblemCount());
        }
        addMemoryStats(parts, "recurringIssueTags", memory.getRecurringIssueTags());
        addMemoryStats(parts, "recurringFineTags", memory.getRecurringFineGrainedTags());
        addAbilityFocus(parts, memory.getAbilityFocus());
        if (memory.getRecentTrend() != null && !memory.getRecentTrend().isBlank()) {
            parts.add("recentTrend=" + memory.getRecentTrend());
        }
        if (memory.getInterventionEffect() != null && !memory.getInterventionEffect().isBlank()) {
            parts.add("interventionEffect=" + memory.getInterventionEffect());
        }
        if (memory.getTeacherCorrectionSummary() != null && !memory.getTeacherCorrectionSummary().isBlank()) {
            parts.add("teacherCorrection=" + memory.getTeacherCorrectionSummary());
        }
        String teacherCalibration = teacherCalibrationSummary(evidencePackage);
        if (!teacherCalibration.isBlank()) {
            parts.add("teacherCalibration=" + teacherCalibration);
        }
        if (memory.getEvidenceRefs() != null && !memory.getEvidenceRefs().isEmpty()) {
            parts.add("memoryEvidenceRefs=" + String.join(",", memory.getEvidenceRefs()));
        }
        return String.join(" | ", parts);
    }

    private String teacherCalibrationSummary(DiagnosisEvidencePackage evidencePackage) {
        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                evidencePackage == null ? null : evidencePackage.getLearningMemory();
        if (memory == null || memory.getTeacherCalibrationPatterns() == null
                || memory.getTeacherCalibrationPatterns().isEmpty()) {
            return "";
        }
        return memory.getTeacherCalibrationPatterns().stream()
                .limit(3)
                .map(pattern -> "original="
                        + defaultIfBlank(pattern.getOriginalFineGrainedTag(), pattern.getOriginalIssueTag())
                        + " corrected="
                        + defaultIfBlank(pattern.getCorrectedFineGrainedTag(), pattern.getCorrectedIssueTag())
                        + " count=" + (pattern.getCorrectionCount() == null ? 0L : pattern.getCorrectionCount())
                        + " refs=" + String.join(",", pattern.getEvidenceRefs() == null ? List.of() : pattern.getEvidenceRefs())
                        + (isBlank(pattern.getLatestTeacherNote()) ? "" : " note=" + pattern.getLatestTeacherNote()))
                .toList()
                .toString();
    }

    private void addTeacherCalibrationIssueTags(Set<String> tags,
                                                DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory) {
        if (memory == null || memory.getTeacherCalibrationPatterns() == null) {
            return;
        }
        memory.getTeacherCalibrationPatterns().stream()
                .map(DiagnosisEvidencePackage.TeacherCalibrationPattern::getCorrectedIssueTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .forEach(tags::add);
    }

    private void addTeacherCalibrationFineTags(Set<String> tags,
                                               DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory) {
        if (memory == null || memory.getTeacherCalibrationPatterns() == null) {
            return;
        }
        memory.getTeacherCalibrationPatterns().stream()
                .map(DiagnosisEvidencePackage.TeacherCalibrationPattern::getCorrectedFineGrainedTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .forEach(tags::add);
    }

    private void addMemoryStats(List<String> parts,
                                String name,
                                List<DiagnosisEvidencePackage.MemoryTagStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        parts.add(name + "=" + stats.stream()
                .limit(3)
                .map(stat -> defaultIfBlank(stat.getTag(), "") + ":" + (stat.getCount() == null ? 0L : stat.getCount()))
                .toList());
    }

    private void addAbilityFocus(List<String> parts, List<DiagnosisEvidencePackage.AbilityFocus> abilityFocus) {
        if (abilityFocus == null || abilityFocus.isEmpty()) {
            return;
        }
        parts.add("abilityFocus=" + abilityFocus.stream()
                .limit(3)
                .map(focus -> defaultIfBlank(focus.getAbilityPoint(), "")
                        + ":submissions=" + (focus.getSubmissionCount() == null ? 0L : focus.getSubmissionCount())
                        + ",problems=" + (focus.getProblemCount() == null ? 0L : focus.getProblemCount()))
                .toList());
    }

    private ModelDiagnosisBrief.HiddenDataBoundary hiddenDataBoundary(DiagnosisEvidencePackage.JudgeFacts judgeFacts) {
        boolean hiddenFailureObserved = judgeFacts != null && Boolean.TRUE.equals(judgeFacts.getHiddenFailureObserved());
        return ModelDiagnosisBrief.HiddenDataBoundary.builder()
                .hiddenFailureObserved(hiddenFailureObserved)
                .hiddenInputVisible(false)
                .policy(hiddenFailureObserved
                        ? "Hidden judge data is not visible. Do not infer exact hidden inputs or outputs."
                        : "Use only visible judge facts and provided code evidence.")
                .build();
    }

    private String uncertainty(DiagnosisEvidencePackage.JudgeFacts judgeFacts) {
        if (judgeFacts != null && Boolean.TRUE.equals(judgeFacts.getHiddenFailureObserved())) {
            return "Hidden failure is observed, but hidden test data is unavailable; do not guess hidden data.";
        }
        return "Use judge facts, source code, and evidenceRefs only; do not invent facts.";
    }

    private void addNamedList(List<String> parts, String name, List<String> values) {
        if (values != null && !values.isEmpty()) {
            parts.add(name + "=" + String.join(",", values));
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
