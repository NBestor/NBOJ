package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AdviceGenerationFeedbackMapper {

    public SubmissionAnalysisResponse.StudentFeedback toStudentFeedback(AdviceGenerationOutput output,
                                                                        StandardLibraryPack standardLibraryPack) {
        if (output == null) {
            return null;
        }
        if (hasStudentReport(output)) {
            return toStudentFeedbackFromReportV2(output, standardLibraryPack);
        }
        List<SubmissionAnalysisResponse.FeedbackIssue> blockingIssues = toBlockingIssues(output);
        List<SubmissionAnalysisResponse.ImprovementOpportunity> improvementOpportunities =
                toImprovementOpportunities(output, standardLibraryPack);
        AdviceGenerationOutput.NextStepAdvice firstStep = safe(output.getNextStepPlan()).stream()
                .filter(item -> item != null)
                .findFirst()
                .orElse(null);
        return SubmissionAnalysisResponse.StudentFeedback.builder()
                .summary(defaultIfBlank(output.getStudentSummary(), "先处理当前最明确的基础层问题。"))
                .blockingIssues(blockingIssues)
                .secondaryIssues(List.of())
                .improvementOpportunities(improvementOpportunities)
                .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                        .hintLevel("L2")
                        .action(firstTeachingAction(standardLibraryPack))
                        .task(firstStep == null ? firstBasicAction(output) : firstStep.getTarget())
                        .checkQuestion(firstBasicQuestion(output))
                        .evidenceRefs(firstStep == null || firstStep.getEvidenceRef() == null || firstStep.getEvidenceRef().isBlank()
                                ? firstBasicEvidenceRefs(output)
                                : List.of(firstStep.getEvidenceRef()))
                        .answerLeakRisk("LOW")
                        .build())
                .build();
    }

    private SubmissionAnalysisResponse.StudentFeedback toStudentFeedbackFromReportV2(
            AdviceGenerationOutput output,
            StandardLibraryPack standardLibraryPack
    ) {
        AdviceGenerationOutput.StudentReport report = output.getStudentReport();
        AdviceGenerationOutput.DiagnosisAnchor anchor = firstAnchor(output);
        List<String> evidenceRefs = anchor == null || anchor.getEvidenceRefs() == null
                ? List.of()
                : anchor.getEvidenceRefs();
        String nextAction = defaultIfBlank(report.getNextActionText(), "先复核模型指出的证据。");

        List<SubmissionAnalysisResponse.FeedbackIssue> blockingIssues = toBlockingIssues(output);
        if (blockingIssues.isEmpty()) {
            blockingIssues = toBlockingIssuesFromReport(output, anchor, evidenceRefs);
        }

        List<SubmissionAnalysisResponse.ImprovementOpportunity> improvementOpportunities =
                toImprovementOpportunities(output, standardLibraryPack);
        if (improvementOpportunities.isEmpty()) {
            improvementOpportunities = toImprovementOpportunitiesFromReport(output);
        }

        List<String> nextEvidenceRefs = evidenceRefs.isEmpty() ? firstBasicEvidenceRefs(output) : evidenceRefs;

        return SubmissionAnalysisResponse.StudentFeedback.builder()
                .summary(defaultIfBlank(output.getStudentSummary(), report.getBasicLayerText()))
                .blockingIssues(blockingIssues)
                .secondaryIssues(List.of())
                .improvementOpportunities(improvementOpportunities)
                .nextLearningAction(SubmissionAnalysisResponse.NextLearningAction.builder()
                        .hintLevel(defaultIfBlank(report.getHintLevel(), "L3"))
                        .action(firstTeachingAction(standardLibraryPack))
                        .task(nextAction)
                        .checkQuestion(nextAction)
                        .evidenceRefs(nextEvidenceRefs)
                        .answerLeakRisk("LOW")
                        .build())
                .build();
    }

    private List<SubmissionAnalysisResponse.FeedbackIssue> toBlockingIssuesFromReport(
            AdviceGenerationOutput output,
            AdviceGenerationOutput.DiagnosisAnchor anchor,
            List<String> evidenceRefs
    ) {
        if (output == null || output.getStudentReport() == null
                || blank(output.getStudentReport().getBasicLayerText())) {
            return List.of();
        }
        return List.of(SubmissionAnalysisResponse.FeedbackIssue.builder()
                .priority(1)
                .title(defaultIfBlank(anchor == null ? "" : anchor.getReason(), "基础层问题"))
                .studentMessage(output.getStudentReport().getBasicLayerText())
                .evidence(firstOrDefault(evidenceRefs, "模型引用了当前提交证据。"))
                .nextAction(output.getStudentReport().getNextActionText())
                .issueTag(anchor == null ? "" : defaultIfBlank(anchor.getType(), anchor.getId()))
                .fineGrainedTag(anchor == null ? "" : anchor.getId())
                .evidenceRefs(evidenceRefs == null ? List.of() : evidenceRefs)
                .build());
    }

    private List<SubmissionAnalysisResponse.ImprovementOpportunity> toImprovementOpportunitiesFromReport(
            AdviceGenerationOutput output
    ) {
        if (output == null || output.getStudentReport() == null
                || blank(output.getStudentReport().getImprovementLayerText())) {
            return List.of();
        }
        String text = output.getStudentReport().getImprovementLayerText();
        return List.of(SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                .title("提高层")
                .category("AI_FEEDBACK")
                .studentMessage(text)
                .benefit(text)
                .evidenceRefs(List.of())
                .build());
    }

    private List<SubmissionAnalysisResponse.FeedbackIssue> toBlockingIssues(AdviceGenerationOutput output) {
        return safe(output == null ? null : output.getBasicLayerAdvice()).stream()
                .filter(item -> item != null)
                .map(item -> SubmissionAnalysisResponse.FeedbackIssue.builder()
                        .priority(priorityOf(output.getBasicLayerAdvice(), item))
                        .title(defaultIfBlank(item.getTitle(), "基础层问题"))
                        .studentMessage(joinNonBlank(item.getWhatHappened(), item.getWhyItMatters()))
                        .evidence(firstOrDefault(item.getEvidenceRefs(), "模型引用了当前提交证据。"))
                        .nextAction(item.getStudentAction())
                        .issueTag(defaultIfBlank(item.getSkillUnitId(), item.getMistakePointId()))
                        .fineGrainedTag(item.getMistakePointId())
                        .evidenceRefs(item.getEvidenceRefs())
                        .build())
                .toList();
    }

    private List<SubmissionAnalysisResponse.ImprovementOpportunity> toImprovementOpportunities(
            AdviceGenerationOutput output,
            StandardLibraryPack standardLibraryPack
    ) {
        return safe(output == null ? null : output.getImprovementLayerAdvice()).stream()
                .filter(item -> item != null)
                .map(item -> SubmissionAnalysisResponse.ImprovementOpportunity.builder()
                        .title(defaultIfBlank(item.getTitle(), "提高层"))
                        .category(toImprovementCategory(item, standardLibraryPack))
                        .studentMessage(defaultIfBlank(item.getSuggestion(), item.getCurrentLimit()))
                        .benefit(defaultIfBlank(item.getStudentBenefit(), "帮助你把同类问题迁移到下一题。"))
                        .evidenceRefs(item.getEvidenceRefs() == null ? List.of() : item.getEvidenceRefs())
                        .build())
                .toList();
    }

    private boolean hasStudentReport(AdviceGenerationOutput output) {
        AdviceGenerationOutput.StudentReport report = output.getStudentReport();
        return report != null && (!blank(report.getBasicLayerText())
                || !blank(report.getImprovementLayerText())
                || !blank(report.getNextActionText()));
    }

    private AdviceGenerationOutput.DiagnosisAnchor firstAnchor(AdviceGenerationOutput output) {
        if (output == null || output.getDiagnosisDecision() == null) {
            return null;
        }
        return safe(output.getDiagnosisDecision().getAnchors()).stream()
                .filter(item -> item != null)
                .findFirst()
                .orElse(null);
    }

    private AdviceGenerationOutput.BasicLayerAdvice firstBasic(AdviceGenerationOutput output) {
        return safe(output == null ? null : output.getBasicLayerAdvice()).stream()
                .filter(item -> item != null)
                .findFirst()
                .orElse(null);
    }

    private String firstBasicAction(AdviceGenerationOutput output) {
        AdviceGenerationOutput.BasicLayerAdvice first = firstBasic(output);
        return first == null ? "先复核模型指出的证据。" : first.getStudentAction();
    }

    private String firstBasicQuestion(AdviceGenerationOutput output) {
        AdviceGenerationOutput.BasicLayerAdvice first = firstBasic(output);
        return first == null ? "这条证据说明了什么代码行为？" : first.getCheckQuestion();
    }

    private List<String> firstBasicEvidenceRefs(AdviceGenerationOutput output) {
        AdviceGenerationOutput.BasicLayerAdvice first = firstBasic(output);
        if (first != null && first.getEvidenceRefs() != null && !first.getEvidenceRefs().isEmpty()) {
            return first.getEvidenceRefs();
        }
        if (output != null && output.getCaseUnderstanding() != null
                && output.getCaseUnderstanding().getPrimaryEvidenceRef() != null
                && !output.getCaseUnderstanding().getPrimaryEvidenceRef().isBlank()) {
            return List.of(output.getCaseUnderstanding().getPrimaryEvidenceRef());
        }
        return List.of();
    }

    private String toImprovementCategory(AdviceGenerationOutput.ImprovementLayerAdvice item,
                                         StandardLibraryPack standardLibraryPack) {
        String mappedCategory = categoryFromImprovementPoint(item, standardLibraryPack);
        if (!mappedCategory.isBlank()) {
            return mappedCategory;
        }
        String normalizedSkillCategory = normalizeToAllowedImprovementTag(
                item == null ? "" : item.getSkillUnitId(),
                standardLibraryPack
        );
        if (!normalizedSkillCategory.isBlank()) {
            return normalizedSkillCategory;
        }
        if (standardLibraryPack != null && standardLibraryPack.getImprovementTags() != null
                && !standardLibraryPack.getImprovementTags().isEmpty()) {
            return standardLibraryPack.getImprovementTags().get(0).getId();
        }
        return "TRANSFER_REVIEW";
    }

    private String categoryFromImprovementPoint(AdviceGenerationOutput.ImprovementLayerAdvice item,
                                                StandardLibraryPack standardLibraryPack) {
        if (item == null || item.getImprovementPointId() == null || item.getImprovementPointId().isBlank()
                || standardLibraryPack == null || standardLibraryPack.getImprovementPoints() == null) {
            return "";
        }
        Map<String, StandardLibraryPack.ImprovementPointOption> pointsById =
                standardLibraryPack.getImprovementPoints().stream()
                        .filter(point -> point != null && point.getId() != null && !point.getId().isBlank())
                        .collect(Collectors.toMap(
                                point -> normalizeKey(point.getId()),
                                Function.identity(),
                                (left, right) -> left
                        ));
        StandardLibraryPack.ImprovementPointOption point = pointsById.get(normalizeKey(item.getImprovementPointId()));
        if (point == null) {
            return "";
        }
        String category = normalizeToAllowedImprovementTag(point.getCategory(), standardLibraryPack);
        if (!category.isBlank()) {
            return category;
        }
        return normalizeToAllowedImprovementTag(point.getId(), standardLibraryPack);
    }

    private String normalizeToAllowedImprovementTag(String rawValue, StandardLibraryPack standardLibraryPack) {
        if (rawValue == null || rawValue.isBlank()
                || standardLibraryPack == null || standardLibraryPack.getImprovementTags() == null) {
            return "";
        }
        String key = normalizeKey(rawValue);
        return standardLibraryPack.getImprovementTags().stream()
                .filter(tag -> tag != null && tag.getId() != null && !tag.getId().isBlank())
                .map(StandardLibraryPack.ImprovementTagOption::getId)
                .filter(id -> normalizeKey(id).equals(key))
                .findFirst()
                .orElse("");
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int priorityOf(List<AdviceGenerationOutput.BasicLayerAdvice> values,
                           AdviceGenerationOutput.BasicLayerAdvice item) {
        int index = values == null ? -1 : values.indexOf(item);
        return index < 0 ? 1 : index + 1;
    }

    private String firstTeachingAction(StandardLibraryPack standardLibraryPack) {
        if (standardLibraryPack == null || standardLibraryPack.getTeachingActions() == null) {
            return "COLLECT_EVIDENCE";
        }
        return standardLibraryPack.getTeachingActions().stream()
                .filter(item -> item != null && item.getId() != null && !item.getId().isBlank())
                .map(StandardLibraryPack.TeachingActionOption::getId)
                .findFirst()
                .orElse("COLLECT_EVIDENCE");
    }

    private String firstImprovementCategory(StandardLibraryPack standardLibraryPack) {
        if (standardLibraryPack == null || standardLibraryPack.getImprovementTags() == null) {
            return "TRANSFER_REVIEW";
        }
        return standardLibraryPack.getImprovementTags().stream()
                .filter(item -> item != null && item.getId() != null && !item.getId().isBlank())
                .map(StandardLibraryPack.ImprovementTagOption::getId)
                .findFirst()
                .orElse("TRANSFER_REVIEW");
    }

    private String joinNonBlank(String first, String second) {
        if (first == null || first.isBlank()) {
            return defaultIfBlank(second, "");
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    private String firstOrDefault(List<String> values, String fallback) {
        if (values == null) {
            return fallback;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
