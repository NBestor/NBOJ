package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationActionEvidenceFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationActionEvidenceAnalyzer analyzer = new RecommendationActionEvidenceAnalyzer();

    @Test
    void verifiesStableActionEvidenceCases() throws Exception {
        List<Fixture> fixtures;
        try (InputStream input = getClass().getResourceAsStream(
                "/recommendation-eval-fixtures/action-evidence-cases.json")) {
            fixtures = objectMapper.readValue(input, new TypeReference<>() {
            });
        }

        assertThat(fixtures).hasSize(9);
        for (Fixture fixture : fixtures) {
            var signal = analyzer.analyze(events(fixture)).get(0);
            assertThat(signal.getOutcome())
                    .as("outcome for %s", fixture.id)
                    .isEqualTo(fixture.expectedOutcome);
            assertThat(signal.getMatchBasis())
                    .as("match basis for %s", fixture.id)
                    .isEqualTo(fixture.expectedMatchBasis);
        }
    }

    private List<StudentRecommendationEvent> events(Fixture fixture) throws Exception {
        List<StudentRecommendationEvent> events = new ArrayList<>();
        StudentRecommendationEvent exposure = base(fixture, StudentRecommendationEventService.EVENT_EXPOSED);
        events.add(exposure);
        events.add(base(fixture, StudentRecommendationEventService.EVENT_ENTERED_PROBLEM));
        if (fixture.hasSubmission == null || fixture.hasSubmission) {
            StudentRecommendationEvent submitted = base(fixture, StudentRecommendationEventService.EVENT_SUBMITTED);
            submitted.setFollowupSubmissionId(9001L);
            submitted.setFollowupVerdict(fixture.verdict);
            submitted.setFollowupIssueTag(fixture.followupIssueTag);
            submitted.setFollowupFineGrainedTag(fixture.followupIssueTag);
            submitted.setFollowupIssueIds(json(fixture.followupIssueIds));
            submitted.setFollowupPointKeys(json(fixture.followupPointKeys));
            submitted.setFollowupMistakePointCodes(json(fixture.followupMistakePointCodes));
            submitted.setFollowupFailedTestSemanticCodes(json(fixture.followupFailedTestSemanticCodes));
            events.add(submitted);
        }
        return events;
    }

    private StudentRecommendationEvent base(Fixture fixture, String eventType) throws Exception {
        return StudentRecommendationEvent.builder()
                .recommendationToken("fixture:" + fixture.id)
                .studentProfileId(41L)
                .assignmentId(7L)
                .problemId(101L)
                .type("REVIEW")
                .focusTags(json(fixture.focusTags))
                .focusIssueIds(json(fixture.focusIssueIds))
                .focusPointKeys(json(fixture.focusPointKeys))
                .focusMistakePointCodes(json(fixture.focusMistakePointCodes))
                .focusTestSemanticCodes(json(fixture.focusTestSemanticCodes))
                .riskLevel(fixture.riskLevel)
                .eventType(eventType)
                .createdAt(LocalDateTime.of(2026, 7, 23, 10, 0)
                        .plusMinutes(StudentRecommendationEventService.EVENT_EXPOSED.equals(eventType) ? 0 : 1))
                .build();
    }

    private String json(List<String> values) throws Exception {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    }

    private static class Fixture {
        public String id;
        public List<String> focusTags;
        public List<String> focusIssueIds;
        public List<String> focusPointKeys;
        public List<String> focusMistakePointCodes;
        public List<String> focusTestSemanticCodes;
        public List<String> followupIssueIds;
        public List<String> followupPointKeys;
        public List<String> followupMistakePointCodes;
        public List<String> followupFailedTestSemanticCodes;
        public String followupIssueTag;
        public String verdict;
        public String riskLevel;
        public Boolean hasSubmission;
        public String expectedOutcome;
        public String expectedMatchBasis;
    }
}
