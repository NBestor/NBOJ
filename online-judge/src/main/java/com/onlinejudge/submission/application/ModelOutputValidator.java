package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ModelOutputValidator {

    public ExternalModelStagePayloads.StageValidationResult validateStudentFeedback(
            SubmissionAnalysisResponse.StudentFeedback feedback,
            ModelDiagnosisBrief brief,
            StandardLibraryPack standardLibraryPack) {
        if (feedback == null) {
            return invalid(ModelStageFailureReason.EMPTY_RESPONSE, "Student feedback output is empty.");
        }
        if (feedback.getSummary() == null || feedback.getSummary().isBlank()
                || feedback.getBlockingIssues() == null || feedback.getBlockingIssues().isEmpty()
                || feedback.getNextLearningAction() == null) {
            return invalid(ModelStageFailureReason.INVALID_JSON, "Student feedback is missing required fields.");
        }
        Set<String> validRefs = validEvidenceRefs(brief);
        for (SubmissionAnalysisResponse.FeedbackIssue issue : feedback.getBlockingIssues()) {
            if (issue == null || issue.getStudentMessage() == null || issue.getStudentMessage().isBlank()
                    || issue.getNextAction() == null || issue.getNextAction().isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_JSON,
                        "Blocking issue is missing student message or next action.");
            }
            String invalidIssueRef = invalidRefSummary(
                    "studentFeedback.blockingIssues[" + indexOf(feedback.getBlockingIssues(), issue) + "].evidenceRefs",
                    issue.getEvidenceRefs(),
                    validRefs,
                    brief
            );
            if (!invalidIssueRef.isBlank()) {
                return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidIssueRef);
            }
        }
        String invalidNextActionRef = invalidRefSummary(
                "studentFeedback.nextLearningAction.evidenceRefs",
                feedback.getNextLearningAction().getEvidenceRefs(),
                validRefs,
                brief
        );
        if (!invalidNextActionRef.isBlank()) {
            return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidNextActionRef);
        }
        Set<String> allowedImprovementTags = improvementTagIds(
                standardLibraryPack == null ? null : standardLibraryPack.getImprovementTags());
        if (feedback.getImprovementOpportunities() != null) {
            for (SubmissionAnalysisResponse.ImprovementOpportunity item : feedback.getImprovementOpportunities()) {
                if (item == null) {
                    continue;
                }
                if (!allowedImprovementTags.isEmpty()
                        && !allowedImprovementTags.contains(normalize(item.getCategory()))) {
                    return invalid(ModelStageFailureReason.INVALID_TAG,
                            "Improvement category is not in standard library pack.");
                }
                if (item.getStudentMessage() == null || item.getStudentMessage().isBlank()
                        || item.getBenefit() == null || item.getBenefit().isBlank()) {
                    return invalid(ModelStageFailureReason.INVALID_JSON,
                            "Improvement opportunity is missing message or benefit.");
                }
                String invalidImprovementRef = item.getEvidenceRefs() == null || item.getEvidenceRefs().isEmpty()
                        ? ""
                        : invalidRefSummary(
                        "studentFeedback.improvementOpportunities[" + indexOf(feedback.getImprovementOpportunities(), item) + "].evidenceRefs",
                        item.getEvidenceRefs(),
                        validRefs,
                        brief
                );
                if (!invalidImprovementRef.isBlank()) {
                    return invalid(ModelStageFailureReason.INVALID_EVIDENCE_REF, invalidImprovementRef);
                }
            }
        }
        return valid();
    }

    private Set<String> validEvidenceRefs(ModelDiagnosisBrief brief) {
        return EvidenceRefSupport.validEvidenceRefs(brief);
    }

    private String invalidRefSummary(String fieldName,
                                     List<String> refs,
                                     Set<String> validRefs,
                                     ModelDiagnosisBrief brief) {
        if (refs == null || refs.isEmpty()) {
            return fieldName + " missing evidenceRefs.";
        }
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                return fieldName + " contains blank evidenceRef.";
            }
            String normalized = EvidenceRefSupport.normalizeEvidenceRef(ref, validRefs,
                    EvidenceRefSupport.orderedEvidenceRefs(brief), brief);
            if (validRefs == null
                    || (!validRefs.contains(normalized) && !EvidenceRefSupport.isValidEvidenceRef(normalized, brief))) {
                return fieldName + " invalid evidenceRef=" + ref;
            }
        }
        return "";
    }

    private int indexOf(List<?> values, Object value) {
        return values == null ? -1 : values.indexOf(value);
    }

    private Set<String> improvementTagIds(List<StandardLibraryPack.ImprovementTagOption> tags) {
        Set<String> ids = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                    .map(StandardLibraryPack.ImprovementTagOption::getId)
                    .map(this::normalize)
                    .forEach(ids::add);
        }
        return ids;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private ExternalModelStagePayloads.StageValidationResult valid() {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(true)
                .failureReason(ModelStageFailureReason.NONE)
                .message("")
                .build();
    }

    private ExternalModelStagePayloads.StageValidationResult invalid(ModelStageFailureReason reason, String message) {
        return ExternalModelStagePayloads.StageValidationResult.builder()
                .valid(false)
                .failureReason(reason)
                .message(message)
                .build();
    }
}
