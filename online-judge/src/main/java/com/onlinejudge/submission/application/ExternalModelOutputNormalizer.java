package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExternalModelOutputNormalizer {

    public SubmissionAnalysisResponse.StudentFeedback normalizeStudentFeedback(
            SubmissionAnalysisResponse.StudentFeedback feedback,
            ExternalModelAgentRuntime.RuntimePlan runtimePlan) {
        if (feedback == null) {
            return null;
        }
        ModelDiagnosisBrief brief = runtimePlan == null ? null : runtimePlan.getBrief();
        StandardLibraryPack standardLibraryPack = runtimePlan == null ? null : runtimePlan.getStandardLibraryPack();
        Set<String> improvementTags = improvementTagLookup(
                standardLibraryPack == null ? null : standardLibraryPack.getImprovementTags());
        if (feedback.getBlockingIssues() != null) {
            feedback.getBlockingIssues().forEach(issue -> {
                if (issue == null) {
                    return;
                }
                issue.setEvidenceRefs(normalizeEvidenceRefs(issue.getEvidenceRefs(), brief));
            });
        }
        if (feedback.getSecondaryIssues() != null) {
            feedback.getSecondaryIssues().forEach(issue -> {
                if (issue == null) {
                    return;
                }
                issue.setEvidenceRefs(normalizeEvidenceRefs(issue.getEvidenceRefs(), brief));
            });
        }
        if (feedback.getImprovementOpportunities() != null) {
            feedback.getImprovementOpportunities().forEach(item -> {
                if (item == null) {
                    return;
                }
                item.setCategory(resolveImprovementTag(item.getCategory(), improvementTags));
                item.setEvidenceRefs(normalizeEvidenceRefs(item.getEvidenceRefs(), brief));
            });
        }
        if (feedback.getNextLearningAction() != null) {
            feedback.getNextLearningAction().setEvidenceRefs(
                    normalizeEvidenceRefs(feedback.getNextLearningAction().getEvidenceRefs(), brief));
            feedback.getNextLearningAction().setAnswerLeakRisk(
                    ModelOutputSafetyPolicy.calibrateModelReportedRisk(
                            feedback.getNextLearningAction().getAnswerLeakRisk(),
                            feedback.getNextLearningAction().getAction(),
                            feedback.getNextLearningAction().getTask(),
                            feedback.getNextLearningAction().getCheckQuestion()
                    ));
        }
        return feedback;
    }

    private List<String> normalizeEvidenceRefs(List<String> refs, ModelDiagnosisBrief brief) {
        if (refs == null || refs.isEmpty()) {
            return refs;
        }
        Map<String, String> lookup = evidenceLookup(brief);
        return refs.stream()
                .map(ref -> normalizeEvidenceRef(ref, lookup, brief))
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeEvidenceRef(String rawValue, Map<String, String> lookup, ModelDiagnosisBrief brief) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        String trimmed = rawValue.trim();
        String exact = lookup.get(normalizeKey(trimmed));
        if (exact != null) {
            return exact;
        }
        return EvidenceRefSupport.normalizeEvidenceRef(trimmed, brief);
    }

    private Map<String, String> evidenceLookup(ModelDiagnosisBrief brief) {
        Map<String, String> refs = new LinkedHashMap<>();
        if (brief == null) {
            return refs;
        }
        addEvidenceRefs(refs, brief.getEvidenceRefs());
        return refs;
    }

    private void addEvidenceRefs(Map<String, String> refs, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .forEach(ref -> refs.putIfAbsent(normalizeKey(ref), ref));
    }

    private Set<String> improvementTagLookup(List<StandardLibraryPack.ImprovementTagOption> tags) {
        Set<String> ids = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                    .map(StandardLibraryPack.ImprovementTagOption::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(id -> ids.add(id.trim()));
        }
        return ids;
    }

    private String resolveImprovementTag(String rawValue, Set<String> allowedTags) {
        if (rawValue == null || rawValue.isBlank() || allowedTags == null || allowedTags.isEmpty()) {
            return rawValue;
        }
        String key = normalizeKey(rawValue);
        return allowedTags.stream()
                .filter(tag -> normalizeKey(tag).equals(key))
                .findFirst()
                .orElse(rawValue);
    }

    private String normalizeKey(String value) {
        return normalizeText(value).toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }
}
