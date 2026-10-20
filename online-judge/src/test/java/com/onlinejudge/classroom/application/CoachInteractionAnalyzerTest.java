package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.CoachPrompt;
import com.onlinejudge.classroom.dto.CoachInteractionSummaryResponse;
import com.onlinejudge.classroom.persistence.CoachPromptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CoachInteractionAnalyzerTest {

    private final FakeCoachPromptRepository repository = new FakeCoachPromptRepository();
    private final CoachInteractionAnalyzer analyzer = new CoachInteractionAnalyzer(repository, new CoachAnswerQualityAnalyzer());

    @Test
    void summarizesPromptedAndAnsweredTurns() {
        repository.saved.add(prompt(1L, 21L, 1, "先估算复杂度", "n=100000 会超时", "这次回答有证据意识"));
        repository.saved.add(prompt(2L, 21L, 2, "请做最小修改", null, null));

        CoachInteractionSummaryResponse summary = analyzer.summarize(List.of(21L)).get(21L);

        assertThat(summary.getStatus()).isEqualTo("CONTINUED");
        assertThat(summary.getTurnCount()).isEqualTo(2);
        assertThat(summary.getAnsweredTurnCount()).isEqualTo(1);
        assertThat(summary.isPrompted()).isTrue();
        assertThat(summary.isAnswered()).isTrue();
        assertThat(summary.getSummary()).contains("继续追问中");
        assertThat(summary.getLatestFeedback()).contains("证据意识");
        assertThat(summary.getAnswerQualitySignal().getQualityLevel()).isEqualTo("VERIFICATION_READY");
        assertThat(summary.getAnswerQualitySignal().getUnderstandingLevel()).isEqualTo("VERIFICATION");
        assertThat(summary.getAnswerQualitySignal().getVerifiable()).isTrue();
        assertThat(summary.getAnswerQualitySignal().getEvidenceTypes()).contains("MIN_CASE", "COMPLEXITY_ESTIMATE");
        assertThat(summary.getCoachSafetyRejectionSignal()).isNotNull();
        assertThat(summary.getCoachSafetyRejectionSignal().getStatus()).isEqualTo("HEALTHY");
        assertThat(summary.getCoachSafetyRejectionSignal().getRejectionCount()).isZero();
    }

    @Test
    void picksLatestSummaryBySubmissionOrder() {
        repository.saved.add(prompt(1L, 11L, 1, "第一题追问", null, null));
        repository.saved.add(prompt(2L, 12L, 1, "第二题追问", "我补了样例", "可以进入验证"));
        Map<Long, CoachInteractionSummaryResponse> summaries = analyzer.summarize(List.of(11L, 12L));

        CoachInteractionSummaryResponse latest = analyzer.latestForOrderedSubmissions(List.of(12L, 11L), summaries);

        assertThat(latest.getSubmissionId()).isEqualTo(12L);
        assertThat(latest.getStatus()).isEqualTo("ANSWERED");
    }

    @Test
    void summarizesCoachSafetyRejectionSignalSeparatelyFromStudentAnswerQuality() {
        repository.saved.add(prompt(1L, 31L, 1, "请先构造最小样例", null, null));
        repository.saved.get(0).setModelFailureReason("SAFETY_REJECTED");
        repository.saved.get(0).setModelAnswerLeakRisk("HIGH");

        CoachInteractionSummaryResponse summary = analyzer.summarize(List.of(31L)).get(31L);

        assertThat(summary.getStatus()).isEqualTo("PROMPTED");
        assertThat(summary.getAnswerQualitySignal().getQualityLevel()).isEqualTo("NO_ANSWER");
        assertThat(summary.getCoachSafetyRejectionSignal()).isNotNull();
        assertThat(summary.getCoachSafetyRejectionSignal().getStatus()).isEqualTo("SAFETY_REJECTED");
        assertThat(summary.getCoachSafetyRejectionSignal().getRejectionCount()).isEqualTo(1);
        assertThat(summary.getCoachSafetyRejectionSignal().getLatestReason()).isEqualTo("SAFETY_REJECTED");
        assertThat(summary.getCoachSafetyRejectionSignal().getLatestAnswerLeakRisk()).isEqualTo("HIGH");
        assertThat(summary.getCoachSafetyRejectionSignal().isNeedsTeacherAttention()).isTrue();
        assertThat(summary.getCoachSafetyRejectionSignal().getEvidenceRefs())
                .contains("coach_prompt:1", "coach_safety_rejection:submission:31");
    }

    private CoachPrompt prompt(Long id, Long submissionId, Integer turnIndex, String question, String answer, String feedback) {
        return CoachPrompt.builder()
                .id(id)
                .submissionId(submissionId)
                .turnIndex(turnIndex)
                .hintPolicy("L2")
                .promptType("SOCRATIC_NEXT_STEP")
                .question(question)
                .studentAnswer(answer)
                .coachFeedback(feedback)
                .createdAt(LocalDateTime.of(2026, 5, 18, 12, turnIndex))
                .build();
    }

    private static class FakeCoachPromptRepository extends UnsupportedJpaRepository<CoachPrompt, Long> implements CoachPromptRepository {
        private final List<CoachPrompt> saved = new ArrayList<>();

        @Override
        public Optional<CoachPrompt> findTopBySubmissionIdOrderByCreatedAtDesc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .reduce((left, right) -> right);
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdOrderByTurnIndexAscCreatedAtAsc(Long submissionId) {
            return saved.stream()
                    .filter(item -> Objects.equals(item.getSubmissionId(), submissionId))
                    .toList();
        }

        @Override
        public List<CoachPrompt> findBySubmissionIdIn(Collection<Long> submissionIds) {
            return saved.stream()
                    .filter(item -> submissionIds.contains(item.getSubmissionId()))
                    .toList();
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
