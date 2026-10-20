package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.StudentRecommendationEvent;
import com.onlinejudge.classroom.persistence.StudentRecommendationEventRepository;
import com.onlinejudge.submission.domain.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEffectivenessServiceTest {

    private final FakeStudentRecommendationEventRepository eventRepository = new FakeStudentRecommendationEventRepository();
    private final RecommendationEffectivenessService service = new RecommendationEffectivenessService(eventRepository, new ObjectMapper());

    @Test
    void summarizesRecommendationFunnelAndFollowupOutcomes() {
        eventRepository.items.add(event("rec-redo", "REDO", StudentRecommendationEventService.EVENT_EXPOSED, "[\"OFF_BY_ONE\"]", null, null));
        eventRepository.items.add(event("rec-redo", "REDO", StudentRecommendationEventService.EVENT_CLICKED, "[\"OFF_BY_ONE\"]", null, null));
        eventRepository.items.add(event("rec-redo", "REDO", StudentRecommendationEventService.EVENT_ENTERED_PROBLEM, "[\"OFF_BY_ONE\"]", null, null));
        eventRepository.items.add(event("rec-redo", "REDO", StudentRecommendationEventService.EVENT_SUBMITTED, "[\"OFF_BY_ONE\"]", Submission.Verdict.WRONG_ANSWER.name(), "OFF_BY_ONE"));

        eventRepository.items.add(event("rec-next", "NEXT_PROBLEM", StudentRecommendationEventService.EVENT_EXPOSED, "[\"INPUT_PARSING\"]", null, null));
        eventRepository.items.add(event("rec-next", "NEXT_PROBLEM", StudentRecommendationEventService.EVENT_CLICKED, "[\"INPUT_PARSING\"]", null, null));
        eventRepository.items.add(event("rec-next", "NEXT_PROBLEM", StudentRecommendationEventService.EVENT_SUBMITTED, "[\"INPUT_PARSING\"]", Submission.Verdict.ACCEPTED.name(), null));

        eventRepository.items.add(event("rec-review", "REVIEW", StudentRecommendationEventService.EVENT_EXPOSED, "[\"COMPLEXITY\"]", null, null));
        eventRepository.items.add(event("rec-review", "REVIEW", StudentRecommendationEventService.EVENT_CLICKED, "[\"COMPLEXITY\"]", null, null));

        var overview = service.buildOverview();

        assertThat(overview.getRecentEventCount()).isEqualTo(9);
        assertThat(overview.getUniqueRecommendationCount()).isEqualTo(3);
        assertThat(overview.getExposureCount()).isEqualTo(3);
        assertThat(overview.getClickCount()).isEqualTo(3);
        assertThat(overview.getEnteredProblemCount()).isEqualTo(1);
        assertThat(overview.getFollowupSubmissionCount()).isEqualTo(2);
        assertThat(overview.getAcceptedFollowupCount()).isEqualTo(1);
        assertThat(overview.getSameFocusIssueCount()).isEqualTo(1);
        assertThat(overview.getClickedWithoutSubmissionCount()).isEqualTo(1);
        assertThat(overview.getUnresolvedLearningSignalCount()).isEqualTo(1);
        assertThat(overview.getTeacherInterventionRecommendedCount()).isZero();
        assertThat(overview.getClickThroughRate()).isEqualTo(100.0);
        assertThat(overview.getFollowupSubmissionRate()).isEqualTo(66.7);
        assertThat(overview.getAcceptedFollowupRate()).isEqualTo(50.0);
        assertThat(overview.getSameFocusIssueRate()).isEqualTo(50.0);
        assertThat(overview.getSummary()).contains("下一轮应降级复盘");
        assertThat(overview.getByType()).extracting("key").contains("REDO", "NEXT_PROBLEM", "REVIEW");
        assertThat(overview.getByStrategy()).extracting("key")
                .contains("REPAIR_SAME_PROBLEM", "TRANSFER_TO_NEW_PROBLEM", "REFLECTION_EVIDENCE");
        assertThat(overview.getFocusTags()).extracting("key").contains("OFF_BY_ONE", "INPUT_PARSING", "COMPLEXITY");
        assertThat(overview.getFocusTags().stream()
                .filter(item -> "OFF_BY_ONE".equals(item.getKey()))
                .findFirst()
                .orElseThrow()
                .getSameFocusIssueCount()).isEqualTo(1);
        assertThat(overview.getFeedbackSignals()).extracting("signal")
                .contains("UNRESOLVED_SAME_FOCUS", "CLICKED_WITHOUT_SUBMISSION");
        assertThat(overview.getActionEvidenceSignals()).extracting("outcome")
                .contains(
                        RecommendationActionEvidenceAnalyzer.OUTCOME_UNRESOLVED_SAME_FOCUS,
                        RecommendationActionEvidenceAnalyzer.OUTCOME_CONTRACT_FULFILLED,
                        RecommendationActionEvidenceAnalyzer.OUTCOME_NO_FOLLOWUP_SUBMISSION
                );
        assertThat(overview.getActionEvidenceSignals()).filteredOn(signal -> "rec-redo".equals(signal.getRecommendationToken()))
                .first()
                .satisfies(signal -> {
                    assertThat(signal.getOutcome()).isEqualTo(RecommendationActionEvidenceAnalyzer.OUTCOME_UNRESOLVED_SAME_FOCUS);
                    assertThat(signal.getRecommendedAdjustment()).contains("最小样例");
                    assertThat(signal.getEvidenceRefs()).contains(
                            "recommendation:rec-redo",
                            "recommendation-outcome:UNRESOLVED_SAME_FOCUS",
                            "submission:9001"
                    );
                });
    }

    @Test
    void parsesLegacyFocusTagsAndReportsClickedWithoutSubmission() {
        eventRepository.items.add(event("rec-legacy", "REVIEW", StudentRecommendationEventService.EVENT_EXPOSED, "OFF_BY_ONE,BOUNDARY", null, null));
        eventRepository.items.add(event("rec-legacy", "REVIEW", StudentRecommendationEventService.EVENT_ENTERED_PROBLEM, "OFF_BY_ONE,BOUNDARY", null, null));

        var overview = service.buildOverview();

        assertThat(overview.getFollowupSubmissionCount()).isZero();
        assertThat(overview.getClickedWithoutSubmissionCount()).isEqualTo(1);
        assertThat(overview.getSummary()).contains("尚未提交");
        assertThat(overview.getFocusTags()).extracting("key").contains("OFF_BY_ONE", "BOUNDARY");
        assertThat(overview.getActionEvidenceSignals()).first()
                .satisfies(signal -> assertThat(signal.getOutcome())
                        .isEqualTo(RecommendationActionEvidenceAnalyzer.OUTCOME_NO_FOLLOWUP_SUBMISSION));
    }

    @Test
    void reportsHighRiskUnresolvedRecommendationAsTeacherInterventionSignal() {
        eventRepository.items.add(event(
                "rec-step-down",
                "REVIEW",
                "STEP_DOWN_REVIEW",
                "HIGH",
                StudentRecommendationEventService.EVENT_EXPOSED,
                "[\"OFF_BY_ONE\"]",
                null,
                null
        ));
        eventRepository.items.add(event(
                "rec-step-down",
                "REVIEW",
                "STEP_DOWN_REVIEW",
                "HIGH",
                StudentRecommendationEventService.EVENT_SUBMITTED,
                "[\"OFF_BY_ONE\"]",
                Submission.Verdict.WRONG_ANSWER.name(),
                "OFF_BY_ONE"
        ));

        var overview = service.buildOverview();

        assertThat(overview.getUnresolvedLearningSignalCount()).isEqualTo(1);
        assertThat(overview.getTeacherInterventionRecommendedCount()).isEqualTo(1);
        assertThat(overview.getByStrategy()).first().satisfies(segment -> {
            assertThat(segment.getKey()).isEqualTo("STEP_DOWN_REVIEW");
            assertThat(segment.getTeacherInterventionRecommendedCount()).isEqualTo(1);
            assertThat(segment.getUnresolvedLearningSignalCount()).isEqualTo(1);
        });
        assertThat(overview.getFeedbackSignals()).first().satisfies(signal -> {
            assertThat(signal.getSignal()).isEqualTo("UNRESOLVED_SAME_FOCUS");
            assertThat(signal.getSeverity()).isEqualTo("HIGH");
            assertThat(signal.getRecommendedAction()).contains("教师介入");
            assertThat(signal.getEvidenceTokens()).contains("rec-step-down");
        });
        assertThat(overview.getActionEvidenceSignals()).first().satisfies(signal -> {
            assertThat(signal.getOutcome()).isEqualTo(RecommendationActionEvidenceAnalyzer.OUTCOME_UNRESOLVED_SAME_FOCUS);
            assertThat(signal.isNeedsTeacherAttention()).isTrue();
            assertThat(signal.getRecommendedAdjustment()).contains("教师介入");
        });
        assertThat(service.buildInterventionQueue()).singleElement().satisfies(signal -> {
            assertThat(signal.getStudentProfileId()).isEqualTo(41L);
            assertThat(signal.getAssignmentId()).isEqualTo(7L);
            assertThat(signal.getProblemId()).isEqualTo(101L);
        });
    }

    @Test
    void actionEvidenceWaitsForDiagnosisWhenFollowupHasNoTagsYet() {
        eventRepository.items.add(event(
                "rec-waiting",
                "NEXT_PROBLEM",
                "TRANSFER_TO_NEW_PROBLEM",
                "MEDIUM",
                StudentRecommendationEventService.EVENT_EXPOSED,
                "[\"OFF_BY_ONE\"]",
                null,
                null
        ));
        eventRepository.items.add(event(
                "rec-waiting",
                "NEXT_PROBLEM",
                "TRANSFER_TO_NEW_PROBLEM",
                "MEDIUM",
                StudentRecommendationEventService.EVENT_SUBMITTED,
                "[\"OFF_BY_ONE\"]",
                Submission.Verdict.WRONG_ANSWER.name(),
                null
        ));

        var overview = service.buildOverview();

        assertThat(overview.getActionEvidenceSignals()).first().satisfies(signal -> {
            assertThat(signal.getOutcome()).isEqualTo(RecommendationActionEvidenceAnalyzer.OUTCOME_WAITING_DIAGNOSIS);
            assertThat(signal.getSummary()).contains("诊断标签尚未回填");
        });
    }

    @Test
    void canScopeRecommendationEffectivenessByAssignment() {
        eventRepository.items.add(event(
                "rec-current",
                "REVIEW",
                "STEP_DOWN_REVIEW",
                "HIGH",
                7L,
                StudentRecommendationEventService.EVENT_EXPOSED,
                "[\"OFF_BY_ONE\"]",
                null,
                null
        ));
        eventRepository.items.add(event(
                "rec-current",
                "REVIEW",
                "STEP_DOWN_REVIEW",
                "HIGH",
                7L,
                StudentRecommendationEventService.EVENT_SUBMITTED,
                "[\"OFF_BY_ONE\"]",
                Submission.Verdict.WRONG_ANSWER.name(),
                "OFF_BY_ONE"
        ));
        eventRepository.items.add(event(
                "rec-other",
                "REVIEW",
                "STEP_DOWN_REVIEW",
                "HIGH",
                8L,
                StudentRecommendationEventService.EVENT_SUBMITTED,
                "[\"OFF_BY_ONE\"]",
                Submission.Verdict.WRONG_ANSWER.name(),
                "OFF_BY_ONE"
        ));

        var overview = service.buildOverview(7L);

        assertThat(overview.getUniqueRecommendationCount()).isEqualTo(1);
        assertThat(overview.getUnresolvedLearningSignalCount()).isEqualTo(1);
        assertThat(overview.getFeedbackSignals()).first()
                .satisfies(signal -> assertThat(signal.getEvidenceTokens()).contains("rec-current").doesNotContain("rec-other"));
    }

    private StudentRecommendationEvent event(String token,
                                             String type,
                                             String eventType,
                                             String focusTags,
                                             String verdict,
                                             String issueTag) {
        return event(token, type, strategyForType(type), "MEDIUM", eventType, focusTags, verdict, issueTag);
    }

    private StudentRecommendationEvent event(String token,
                                             String type,
                                             String strategy,
                                             String riskLevel,
                                             String eventType,
                                             String focusTags,
                                             String verdict,
                                             String issueTag) {
        return StudentRecommendationEvent.builder()
                .recommendationToken(token)
                .studentProfileId(41L)
                .type(type)
                .assignmentId(7L)
                .problemId(101L)
                .focusAbility("Boundary reasoning")
                .focusTags(focusTags)
                .strategy(strategy)
                .learningHypothesis("验证推荐学习假设")
                .expectedCompletionSignal("后续提交不再命中同类错因")
                .riskLevel(riskLevel)
                .fallbackAction("降级复盘")
                .eventType(eventType)
                .followupSubmissionId(StudentRecommendationEventService.EVENT_SUBMITTED.equals(eventType) ? 9001L : null)
                .followupVerdict(verdict)
                .followupIssueTag(issueTag)
                .followupFineGrainedTag(issueTag)
                .createdAt(LocalDateTime.of(2026, 5, 19, 10, 0).plusMinutes(eventRepository.items.size()))
                .build();
    }

    private StudentRecommendationEvent event(String token,
                                             String type,
                                             String strategy,
                                             String riskLevel,
                                             Long assignmentId,
                                             String eventType,
                                             String focusTags,
                                             String verdict,
                                             String issueTag) {
        StudentRecommendationEvent event = event(token, type, strategy, riskLevel, eventType, focusTags, verdict, issueTag);
        event.setAssignmentId(assignmentId);
        return event;
    }

    private String strategyForType(String type) {
        return switch (type) {
            case "REDO" -> "REPAIR_SAME_PROBLEM";
            case "NEXT_PROBLEM" -> "TRANSFER_TO_NEW_PROBLEM";
            case "REVIEW" -> "REFLECTION_EVIDENCE";
            default -> null;
        };
    }

    private static class FakeStudentRecommendationEventRepository extends UnsupportedJpaRepository<StudentRecommendationEvent, Long>
            implements StudentRecommendationEventRepository {
        private final List<StudentRecommendationEvent> items = new ArrayList<>();

        @Override
        public List<StudentRecommendationEvent> findByStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getStudentProfileId(), studentProfileId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByOrderByCreatedAtDesc() {
            return items.stream()
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findTop500ByAssignmentIdOrderByCreatedAtDesc(Long assignmentId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getAssignmentId(), assignmentId))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .limit(500)
                    .toList();
        }

        @Override
        public List<StudentRecommendationEvent> findByFollowupSubmissionIdAndEventTypeOrderByCreatedAtDesc(Long followupSubmissionId, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getFollowupSubmissionId(), followupSubmissionId))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .sorted(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
        }

        @Override
        public Optional<StudentRecommendationEvent> findTopByRecommendationTokenAndEventTypeOrderByCreatedAtDesc(String recommendationToken, String eventType) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getRecommendationToken(), recommendationToken))
                    .filter(item -> Objects.equals(item.getEventType(), eventType))
                    .max(Comparator.comparing(StudentRecommendationEvent::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
    }

    private abstract static class UnsupportedJpaRepository<T, ID> {
        public List<T> findAll() { throw unsupported(); }
        public List<T> findAllById(Iterable<ID> ids) { throw unsupported(); }
        public <S extends T> S save(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAll(Iterable<S> entities) { throw unsupported(); }
        public Optional<T> findById(ID id) { throw unsupported(); }
        public boolean existsById(ID id) { throw unsupported(); }
        public long count() { throw unsupported(); }
        public void deleteById(ID id) { throw unsupported(); }
        public void delete(T entity) { throw unsupported(); }
        public void deleteAllById(Iterable<? extends ID> ids) { throw unsupported(); }
        public void deleteAll(Iterable<? extends T> entities) { throw unsupported(); }
        public void deleteAll() { throw unsupported(); }
        public void flush() { throw unsupported(); }
        public <S extends T> S saveAndFlush(S entity) { throw unsupported(); }
        public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) { throw unsupported(); }
        public void deleteAllInBatch(Iterable<T> entities) { throw unsupported(); }
        public void deleteAllByIdInBatch(Iterable<ID> ids) { throw unsupported(); }
        public void deleteAllInBatch() { throw unsupported(); }
        public T getOne(ID id) { throw unsupported(); }
        public T getById(ID id) { throw unsupported(); }
        public T getReferenceById(ID id) { throw unsupported(); }
        public List<T> findAll(Sort sort) { throw unsupported(); }
        public Page<T> findAll(Pageable pageable) { throw unsupported(); }
        public <S extends T> Optional<S> findOne(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example) { throw unsupported(); }
        public <S extends T> List<S> findAll(Example<S> example, Sort sort) { throw unsupported(); }
        public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) { throw unsupported(); }
        public <S extends T> long count(Example<S> example) { throw unsupported(); }
        public <S extends T> boolean exists(Example<S> example) { throw unsupported(); }
        public <S extends T, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used in this test");
        }
    }
}
