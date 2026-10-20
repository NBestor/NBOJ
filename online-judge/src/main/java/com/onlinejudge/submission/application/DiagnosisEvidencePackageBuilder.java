package com.onlinejudge.submission.application;

import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DiagnosisEvidencePackageBuilder {

    public DiagnosisEvidencePackage build(Problem problem,
                                          Submission submission,
                                          List<SubmissionCaseResult> caseResults,
                                          SubmissionAnalysisResponse fallback,
                                          Assignment.HintPolicy hintPolicy) {
        return build(problem, submission, caseResults, fallback, hintPolicy, null);
    }

    public DiagnosisEvidencePackage build(Problem problem,
                                          Submission submission,
                                          List<SubmissionCaseResult> caseResults,
                                          SubmissionAnalysisResponse fallback,
                                          Assignment.HintPolicy hintPolicy,
                                          DiagnosisEvidencePackage.HistoryEvidence historyEvidence) {
        return build(problem, submission, caseResults, fallback, hintPolicy, historyEvidence, null);
    }

    public DiagnosisEvidencePackage build(Problem problem,
                                          Submission submission,
                                          List<SubmissionCaseResult> caseResults,
                                          SubmissionAnalysisResponse fallback,
                                          Assignment.HintPolicy hintPolicy,
                                          DiagnosisEvidencePackage.HistoryEvidence historyEvidence,
                                          DiagnosisEvidencePackage.StudentLearningMemorySnapshot learningMemory) {
        String sourceCode = Optional.ofNullable(submission.getSourceCode()).orElse("");
        return DiagnosisEvidencePackage.builder()
                .schemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .submission(buildSubmissionEvidence(submission, sourceCode))
                .problem(buildProblemEvidence(problem))
                .judgeFacts(buildJudgeFacts(submission, caseResults, fallback))
                .history(historyEvidence == null ? emptyHistoryEvidence() : historyEvidence)
                .learningMemory(learningMemory)
                .policy(buildPolicyEvidence(hintPolicy))
                .build();
    }

    private DiagnosisEvidencePackage.HistoryEvidence emptyHistoryEvidence() {
        return DiagnosisEvidencePackage.HistoryEvidence.builder()
                .previousIssueTags(List.of())
                .previousFineGrainedTags(List.of())
                .recentIssueTags(List.of())
                .recentFineGrainedTags(List.of())
                .build();
    }

    private DiagnosisEvidencePackage.SubmissionEvidence buildSubmissionEvidence(Submission submission, String sourceCode) {
        return DiagnosisEvidencePackage.SubmissionEvidence.builder()
                .id(submission.getId())
                .language(submission.getLanguageName())
                .verdict(submission.getVerdict() == null ? "UNKNOWN" : submission.getVerdict().name())
                .sourceCode(Optional.ofNullable(sourceCode).orElse(""))
                .sourceCodeWithLineNumbers(addLineNumbers(sourceCode))
                .sourceCodeLineCount(countLines(sourceCode))
                .build();
    }

    private DiagnosisEvidencePackage.ProblemEvidence buildProblemEvidence(Problem problem) {
        return DiagnosisEvidencePackage.ProblemEvidence.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty() == null ? "" : problem.getDifficulty().name())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .aiPromptDirection(Optional.ofNullable(problem.getAiPromptDirection()).orElse(""))
                .knowledgePoints(safeList(problem.getKnowledgePoints()))
                .algorithmStrategies(safeList(problem.getAlgorithmStrategies()))
                .commonMistakes(safeList(problem.getCommonMistakes()))
                .boundaryTypes(safeList(problem.getBoundaryTypes()))
                .build();
    }

    private DiagnosisEvidencePackage.JudgeFacts buildJudgeFacts(Submission submission,
                                                                List<SubmissionCaseResult> caseResults,
                                                                SubmissionAnalysisResponse fallback) {
        List<SubmissionCaseResult> safeCaseResults = caseResults == null ? List.of() : caseResults;
        int passedCount = (int) safeCaseResults.stream().filter(result -> Boolean.TRUE.equals(result.getPassed())).count();
        boolean hiddenFailureObserved = safeCaseResults.stream()
                .anyMatch(result -> !Boolean.TRUE.equals(result.getPassed()) && Boolean.TRUE.equals(result.getHidden()));
        return DiagnosisEvidencePackage.JudgeFacts.builder()
                .compileOutput(Optional.ofNullable(submission.getCompileOutput()).orElse(""))
                .runtimeErrorMessage(Optional.ofNullable(submission.getErrorMessage()).orElse(""))
                .passedCount(passedCount)
                .totalCount(safeCaseResults.size())
                .hiddenFailureObserved(hiddenFailureObserved)
                .firstFailedCase(fallback == null ? null : fallback.getFirstFailedCase())
                .caseResultsSummary(safeCaseResults.stream()
                        .map(this::toCaseSummary)
                        .toList())
                .build();
    }

    private DiagnosisEvidencePackage.CaseSummary toCaseSummary(SubmissionCaseResult result) {
        boolean hidden = Boolean.TRUE.equals(result.getHidden());
        boolean semanticVisible = !hidden || "AI_GENERALIZED".equals(result.getTestRevealPolicy());
        return DiagnosisEvidencePackage.CaseSummary.builder()
                .testCaseNumber(result.getTestCaseNumber())
                .testCaseId(result.getTestCaseId())
                .passed(Boolean.TRUE.equals(result.getPassed()))
                .hidden(hidden)
                .executionTime(result.getExecutionTime())
                .memoryUsed(result.getMemoryUsed())
                .inputPreview(hidden ? "[隐藏测试点]" : Optional.ofNullable(result.getInputSnapshot()).orElse(""))
                .actualOutputPreview(hidden ? "[隐藏测试点]" : Optional.ofNullable(result.getActualOutput()).orElse(""))
                .expectedOutputPreview(hidden ? "[隐藏测试点]" : Optional.ofNullable(result.getExpectedOutput()).orElse(""))
                .semanticCode(semanticVisible ? result.getTestSemanticCode() : null)
                .intentType(semanticVisible ? result.getTestIntentType() : null)
                .intentTitle(semanticVisible ? result.getTestIntentTitle() : null)
                .intentSummary(semanticVisible ? result.getTestIntentSummary() : null)
                .learningObjective(semanticVisible ? result.getTestLearningObjective() : null)
                .contestRole(semanticVisible ? result.getTestContestRole() : null)
                .revealPolicy(semanticVisible ? result.getTestRevealPolicy() : null)
                .build();
    }

    private DiagnosisEvidencePackage.PolicyEvidence buildPolicyEvidence(Assignment.HintPolicy hintPolicy) {
        Assignment.HintPolicy effective = hintPolicy == null ? Assignment.HintPolicy.L2 : hintPolicy;
        return DiagnosisEvidencePackage.PolicyEvidence.builder()
                .hintPolicy(effective.name())
                .allowedHintLevels(switch (effective) {
                    case L1 -> List.of("L1");
                    case L2 -> List.of("L1", "L2");
                    case L3 -> List.of("L1", "L2", "L3");
                    case L4 -> List.of("L1", "L2", "L3", "L4");
                })
                .build();
    }

    private String addLineNumbers(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            String numberedLine = (index + 1) + ": " + lines[index];
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(numberedLine);
        }
        return builder.toString();
    }

    private int countLines(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }
        return sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).length;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
