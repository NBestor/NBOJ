package com.onlinejudge.submission.application;

import java.util.List;

record DiagnosisQualityRubric(String caseId,
                              String primaryIssueTag,
                              String primaryFineGrainedTag,
                              String primaryWhy,
                              String primaryEvidenceRef,
                              List<String> requiredEvidenceRefs,
                              List<String> mustMention,
                              List<String> mustNotMention,
                              List<SecondarySignal> secondarySignals,
                              List<String> distractingSignals,
                              String expectedTeachingPriority) {

    static DiagnosisQualityRubric from(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture) {
        if (fixture == null || fixture.primaryRootCause() == null) {
            return null;
        }
        ComplexStudentSubmissionEvalFixtureLoader.RootCauseFixture primary = fixture.primaryRootCause();
        List<SecondarySignal> secondarySignals = fixture.secondaryIssues() == null
                ? List.of()
                : fixture.secondaryIssues().stream()
                .filter(signal -> signal != null)
                .map(signal -> new SecondarySignal(
                        safe(signal.issueTag()),
                        safe(signal.role()),
                        safe(signal.whySecondary())
                ))
                .toList();
        return new DiagnosisQualityRubric(
                safe(fixture.caseId()),
                safe(primary.issueTag()),
                safe(primary.fineGrainedTag()),
                safe(primary.whyPrimary()),
                safe(primary.evidenceRef()),
                safeList(fixture.requiredEvidenceRefs()),
                safeList(fixture.mustMention()),
                safeList(fixture.mustNotMention()),
                secondarySignals,
                safeList(fixture.distractingSignals()),
                safe(fixture.expectedTeachingPriority())
        );
    }

    boolean complete() {
        return !primaryIssueTag.isBlank()
                && !primaryFineGrainedTag.isBlank()
                && !primaryWhy.isBlank()
                && !primaryEvidenceRef.isBlank()
                && !requiredEvidenceRefs.isEmpty()
                && !mustMention.isEmpty()
                && !mustNotMention.isEmpty()
                && (!secondarySignals.isEmpty() || !distractingSignals.isEmpty())
                && !expectedTeachingPriority.isBlank();
    }

    private static List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(DiagnosisQualityRubric::safe)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record SecondarySignal(String issueTag, String role, String whySecondary) {
    }
}
