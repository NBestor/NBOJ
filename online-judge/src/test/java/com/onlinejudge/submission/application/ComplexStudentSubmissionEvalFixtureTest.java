package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexStudentSubmissionEvalFixtureTest {

    private static final int EXPECTED_COMPLEX_FIXTURE_COUNT = 210;
    private static final int EXPECTED_LIVE_CANDIDATE_COUNT = 42;
    private static final int MIN_SOURCE_LINE_COUNT = 50;
    private static final int LARGE_SOURCE_LINE_COUNT = 100;
    private static final int MIN_LARGE_SOURCE_CASE_COUNT = 80;

    private static final List<String> EXPECTED_METRICS = List.of(
            "primaryRootCauseHit",
            "teachingPriorityCorrect",
            "secondaryIssuesNotOverweighted",
            "distractingSignalsIgnored",
            "evidenceGrounded",
            "noFullSolutionLeak"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DiagnosisTaxonomy taxonomy = new DiagnosisTaxonomy();

    @Test
    void generatedComplexFixturesAreHighQualityAndExecutableEvidenceBacked() throws IOException {
        List<ComplexStudentSubmissionEvalFixtureLoader.Fixture> fixtures = loadFixtures();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(EXPECTED_COMPLEX_FIXTURE_COUNT);
        assertThat(fixtures).extracting(ComplexStudentSubmissionEvalFixtureLoader.Fixture::caseId)
                .doesNotHaveDuplicates();
        assertThat(fixtures).extracting(ComplexStudentSubmissionEvalFixtureLoader.Fixture::generatorSpecId)
                .doesNotHaveDuplicates();

        Map<String, Integer> patternCounts = new LinkedHashMap<>();
        Set<String> templateSlugs = new LinkedHashSet<>();
        Set<String> primaryFineTags = new LinkedHashSet<>();
        Set<String> semanticSourceGroups = new LinkedHashSet<>();
        for (ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture : fixtures) {
            patternCounts.merge(fixture.quality().bugPattern(), 1, Integer::sum);
            templateSlugs.add(templateSlug(fixture.generatorSpecId()));
            primaryFineTags.add(fixture.primaryRootCause().fineGrainedTag());
            semanticSourceGroups.add(semanticSourceKey(fixture));
            assertThat(fixture.generatorSpecId()).startsWith("complex-generator-v1::");
            assertThat(fixture.sourceLineCount()).as(fixture.caseId() + " source line count")
                    .isGreaterThanOrEqualTo(MIN_SOURCE_LINE_COUNT);
            assertThat(fixture.quality().lineCount()).as(fixture.caseId() + " quality line count")
                    .isEqualTo(fixture.sourceLineCount());
            assertThat(fixture.quality().injectedBugCount()).as(fixture.caseId() + " injected bug count")
                    .isGreaterThanOrEqualTo(3);
            assertThat(fixture.quality().semanticVariant()).as(fixture.caseId() + " semantic variant")
                    .isNotBlank();
            assertThat(fixture.quality().verifiedByExecution()).isTrue();
            assertThat(fixture.quality().correctSolutionVerified()).isTrue();
            assertThat(fixture.quality().expectedMetrics()).containsExactlyElementsOf(EXPECTED_METRICS);
            assertThat(fixture.teacherExpectation()).as(fixture.caseId() + " teacher expectation").isNotBlank();
            assertThat(fixture.primaryRootCause()).as(fixture.caseId() + " root cause").isNotNull();
            assertThat(fixture.primaryRootCause().whyPrimary()).as(fixture.caseId() + " why primary").isNotBlank();
            assertThat(fixture.primaryRootCause().issueTag()).isIn(fixture.expectedIssueTags());
            assertThat(fixture.primaryRootCause().fineGrainedTag()).isEqualTo(fixture.expectedFineTags().get(0));
            assertThat(taxonomy.get(fixture.primaryRootCause().issueTag())).as(fixture.caseId() + " issue taxonomy").isNotNull();
            assertThat(taxonomy.get(fixture.primaryRootCause().fineGrainedTag())).as(fixture.caseId() + " fine taxonomy").isNotNull();
            assertThat(fixture.secondaryIssues()).as(fixture.caseId() + " secondary issues").hasSizeGreaterThanOrEqualTo(2);
            assertThat(fixture.secondaryIssues()).as(fixture.caseId() + " secondary reasons")
                    .allSatisfy(issue -> assertThat(issue.whySecondary()).isNotBlank());
            assertThat(fixture.distractingSignals()).as(fixture.caseId() + " distracting signals").hasSizeGreaterThanOrEqualTo(2);
            assertThat(fixture.expectedTeachingPriority()).isNotBlank();
            assertThat(fixture.requiredEvidenceRefs()).contains(fixture.primaryRootCause().evidenceRef());
            assertThat(fixture.requiredEvidenceRefs()).anyMatch(ref -> ref.startsWith("judge:first_failed_case:"));
            assertThat(fixture.mustMention()).isNotEmpty();
            assertThat(fixture.mustNotMention()).contains("完整代码", "参考答案", "隐藏测试点");
            assertThat(fixture.toProblem().getDescription()).hasSizeGreaterThanOrEqualTo(40);
            assertThat(fixture.toSubmission().getSourceCode()).contains("def ");
            assertThat(fixture.toCaseResults()).isNotEmpty();
            assertThat(fixture.toCaseResults().get(0).getPassed()).isFalse();
            assertThat(fixture.toBaseline().getEvidenceRefs()).containsAll(fixture.requiredEvidenceRefs());
        }

        long largeSourceCaseCount = fixtures.stream()
                .filter(fixture -> fixture.sourceLineCount() >= LARGE_SOURCE_LINE_COUNT)
                .count();
        assertThat(largeSourceCaseCount).isGreaterThanOrEqualTo(MIN_LARGE_SOURCE_CASE_COUNT);
        assertThat(patternCounts).hasSizeGreaterThanOrEqualTo(14);
        assertThat(templateSlugs).hasSizeGreaterThanOrEqualTo(14);
        assertThat(primaryFineTags).hasSizeGreaterThanOrEqualTo(14);
        assertThat(semanticSourceGroups).hasSizeGreaterThanOrEqualTo(28);
    }

    @Test
    void generatedComplexFixturesExposeExpandedLiveCandidates() throws IOException {
        List<ComplexStudentSubmissionEvalFixtureLoader.Fixture> liveCandidates = loadFixtures().stream()
                .filter(ComplexStudentSubmissionEvalFixtureLoader.Fixture::liveCandidate)
                .toList();

        assertThat(liveCandidates).hasSizeGreaterThanOrEqualTo(EXPECTED_LIVE_CANDIDATE_COUNT);
        assertThat(liveCandidates).extracting(ComplexStudentSubmissionEvalFixtureLoader.Fixture::caseId)
                .allSatisfy(caseId -> assertThat(caseId).startsWith("complex-live-"));
        assertThat(liveCandidates.stream().map(fixture -> fixture.quality().bugPattern()).collect(java.util.stream.Collectors.toSet()))
                .hasSizeGreaterThanOrEqualTo(14);
        assertThat(liveCandidates.stream().limit(6).map(ComplexStudentSubmissionEvalFixtureLoader.Fixture::caseId))
                .containsExactly(
                        "complex-live-01-multi-query-prefix",
                        "complex-live-02-multi-case-run-reset",
                        "complex-live-03-house-dp-state",
                        "complex-live-04-coin-greedy-trap",
                        "complex-live-05-large-range-simulation",
                        "complex-live-06-output-format-extra"
                );
    }

    @Test
    void generatorOutputMatchesCommittedFixture() throws IOException, InterruptedException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("COMPLEX_FIXTURE_REGENERATE_CHECK")),
                "Set COMPLEX_FIXTURE_REGENERATE_CHECK=true to regenerate and compare complex fixtures.");
        Path generatedPath = Path.of("target", "generated-test-fixtures", "complex-student-submission-cases.json");
        Files.deleteIfExists(generatedPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "python3",
                "src/test/resources/diagnosis-eval-fixtures/generate_complex_student_submission_cases.py")
                .directory(Path.of(".").toAbsolutePath().normalize().toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("COMPLEX_FIXTURE_OUTPUT", generatedPath.toString());
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        assertThat(exitCode).as(output).isZero();
        assertThat(output).contains("Wrote 210 complex fixtures");
        assertThat(Files.readString(generatedPath))
                .isEqualTo(Files.readString(Path.of("src/test/resources/diagnosis-eval-fixtures/complex-student-submission-cases.json")));
    }

    private List<ComplexStudentSubmissionEvalFixtureLoader.Fixture> loadFixtures() throws IOException {
        return new ComplexStudentSubmissionEvalFixtureLoader(objectMapper).loadDefault();
    }

    private String templateSlug(String generatorSpecId) {
        String[] parts = generatorSpecId.split("::");
        return parts.length >= 2 ? parts[1] : generatorSpecId;
    }

    private String semanticSourceKey(ComplexStudentSubmissionEvalFixtureLoader.Fixture fixture) {
        return templateSlug(fixture.generatorSpecId()) + "::" + fixture.quality().semanticVariant();
    }
}
