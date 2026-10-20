package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import com.onlinejudge.submission.domain.SubmissionDiagnosisFact;
import com.onlinejudge.submission.dto.SubmissionGrowthSummaryResponse;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultStatsProjection;
import com.onlinejudge.submission.persistence.SubmissionDiagnosisFactRepository;
import com.onlinejudge.submission.persistence.SubmissionIssueTransitionRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionGrowthSummaryServiceTest {

    @Test
    void reportsMixedProgressWhenTenIssuesBecomeFiveWithOneNewIssue() {
        Fixture fixture = fixture();
        Submission first = submission(1L, "v1", Submission.Verdict.WRONG_ANSWER, 0);
        Submission current = submission(2L, "v2", Submission.Verdict.WRONG_ANSWER, 1);
        List<SubmissionDiagnosisFact> facts = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            facts.add(fact(100L + index, 1L, "point-" + index, "知识点 " + index));
        }
        for (int index = 1; index <= 4; index++) {
            facts.add(fact(200L + index, 2L, "point-" + index, "知识点 " + index));
        }
        facts.add(fact(299L, 2L, "point-new", "新增边界问题"));
        fixture.stub(List.of(first, current), facts, List.of(stats(1L, 4, 8), stats(2L, 7, 8)), List.of(1L, 2L));

        SubmissionGrowthSummaryResponse summary = fixture.service.summarize(List.of(first, current)).get(2L);

        assertThat(summary.getGrowthState()).isEqualTo("MIXED_PROGRESS");
        assertThat(summary.getComparisonSubmissionId()).isEqualTo(1L);
        assertThat(summary.getPassedTestCaseDelta()).isEqualTo(3);
        assertThat(summary.getPersistedCount()).isEqualTo(4);
        assertThat(summary.getNewCount()).isEqualTo(1);
        assertThat(summary.getNotObservedCount()).isEqualTo(6);
        assertThat(summary.getUnresolvedCount()).isEqualTo(5);
        assertThat(summary.getPriorityIssueTitle()).isEqualTo("新增边界问题");
    }

    @Test
    void skipsDuplicateAndIncompleteRecordsWhenSelectingComparisonBaseline() {
        Fixture fixture = fixture();
        Submission first = submission(1L, "same", Submission.Verdict.WRONG_ANSWER, 0);
        Submission duplicate = submission(2L, "same", Submission.Verdict.WRONG_ANSWER, 1);
        Submission incomplete = submission(3L, "unknown", Submission.Verdict.WRONG_ANSWER, 2);
        Submission current = submission(4L, "changed", Submission.Verdict.WRONG_ANSWER, 3);
        fixture.stub(
                List.of(first, duplicate, incomplete, current),
                List.of(
                        fact(101L, 1L, "point-a", "边界条件"),
                        fact(102L, 2L, "point-a", "边界条件"),
                        fact(104L, 4L, "point-a", "边界条件")
                ),
                List.of(stats(1L, 4, 8), stats(2L, 4, 8), stats(3L, 4, 8), stats(4L, 6, 8)),
                List.of(1L, 2L, 4L)
        );

        var summaries = fixture.service.summarize(List.of(first, duplicate, incomplete, current));

        assertThat(summaries.get(2L).getGrowthState()).isEqualTo("DUPLICATE_NO_CHANGE");
        assertThat(summaries.get(2L).getDuplicateOfSubmissionId()).isEqualTo(1L);
        assertThat(summaries.get(3L).getGrowthState()).isEqualTo("UNCOMPARABLE");
        assertThat(summaries.get(4L).getComparisonSubmissionId()).isEqualTo(1L);
        assertThat(summaries.get(4L).getGrowthState()).isEqualTo("CLEAR_PROGRESS");
    }

    @Test
    void keepsOptionalImprovementsOutOfUnresolvedIssueCount() {
        Fixture fixture = fixture();
        Submission accepted = submission(1L, "accepted", Submission.Verdict.ACCEPTED, 0);
        SubmissionDiagnosisFact improvement = fact(101L, 1L, "improvement-a", "代码表达优化");
        improvement.setFactType("IMPROVEMENT");
        improvement.setDisplayCategory("IMPROVEMENT");
        fixture.stub(List.of(accepted), List.of(improvement), List.of(stats(1L, 8, 8)), List.of(1L));

        SubmissionGrowthSummaryResponse summary = fixture.service.summarize(List.of(accepted)).get(1L);

        assertThat(summary.getGrowthState()).isEqualTo("COMPLETED");
        assertThat(summary.getImprovementCount()).isEqualTo(1);
        assertThat(summary.getUnresolvedCount()).isZero();
    }

    @Test
    void batchSummaryNeverUsesAnotherStudentAsComparisonBaseline() {
        Fixture fixture = fixture();
        Submission firstStudent = submission(1L, "student-a", Submission.Verdict.WRONG_ANSWER, 0);
        Submission secondStudent = submission(2L, "student-b", Submission.Verdict.WRONG_ANSWER, 1);
        secondStudent.setStudentProfileId(12L);
        fixture.stub(
                List.of(firstStudent, secondStudent),
                List.of(
                        fact(101L, 1L, "point-a", "边界条件"),
                        fact(201L, 2L, "point-b", "输入解析")
                ),
                List.of(stats(1L, 2, 8), stats(2L, 5, 8)),
                List.of(1L, 2L)
        );

        var summaries = fixture.service.summarize(List.of(firstStudent, secondStudent));

        assertThat(summaries.get(1L).getGrowthState()).isEqualTo("FIRST_RECORD");
        assertThat(summaries.get(2L).getGrowthState()).isEqualTo("FIRST_RECORD");
        assertThat(summaries.get(2L).getComparisonSubmissionId()).isNull();
    }

    @Test
    void classifiesFirstShiftedStalledAndRegressedAttemptsDeterministically() {
        Fixture fixture = fixture();
        Submission first = submission(1L, "first", Submission.Verdict.WRONG_ANSWER, 0);
        Submission shifted = submission(2L, "shifted", Submission.Verdict.WRONG_ANSWER, 1);
        Submission stalled = submission(3L, "stalled", Submission.Verdict.WRONG_ANSWER, 2);
        Submission regressed = submission(4L, "regressed", Submission.Verdict.WRONG_ANSWER, 3);
        fixture.stub(
                List.of(first, shifted, stalled, regressed),
                List.of(
                        fact(101L, 1L, "point-a", "边界条件"),
                        fact(102L, 1L, "point-b", "循环控制"),
                        fact(201L, 2L, "point-a", "边界条件"),
                        fact(202L, 2L, "point-c", "数组索引"),
                        fact(301L, 3L, "point-a", "边界条件"),
                        fact(302L, 3L, "point-c", "数组索引"),
                        fact(401L, 4L, "point-a", "边界条件"),
                        fact(402L, 4L, "point-c", "数组索引"),
                        fact(403L, 4L, "point-d", "输入解析")
                ),
                List.of(stats(1L, 4, 8), stats(2L, 4, 8), stats(3L, 4, 8), stats(4L, 3, 8)),
                List.of(1L, 2L, 3L, 4L)
        );

        var summaries = fixture.service.summarize(List.of(first, shifted, stalled, regressed));

        assertThat(summaries.get(1L).getGrowthState()).isEqualTo("FIRST_RECORD");
        assertThat(summaries.get(1L).getSubmissionId()).isEqualTo(1L);
        assertThat(summaries.get(2L).getGrowthState()).isEqualTo("ISSUE_SHIFTED");
        assertThat(summaries.get(3L).getGrowthState()).isEqualTo("STALLED");
        assertThat(summaries.get(4L).getGrowthState()).isEqualTo("REGRESSED");
    }

    private static Fixture fixture() {
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionAnalysisRepository analysisRepository = mock(SubmissionAnalysisRepository.class);
        SubmissionCaseResultRepository caseResultRepository = mock(SubmissionCaseResultRepository.class);
        SubmissionDiagnosisFactRepository factRepository = mock(SubmissionDiagnosisFactRepository.class);
        SubmissionIssueTransitionRepository transitionRepository = mock(SubmissionIssueTransitionRepository.class);
        SubmissionGrowthSummaryService service = new SubmissionGrowthSummaryService(
                submissionRepository,
                analysisRepository,
                caseResultRepository,
                factRepository,
                transitionRepository,
                new IssuePointKeyFactory(),
                new ObjectMapper()
        );
        return new Fixture(service, analysisRepository, caseResultRepository, factRepository, transitionRepository);
    }

    private static Submission submission(Long id, String source, Submission.Verdict verdict, int minute) {
        return Submission.builder()
                .id(id)
                .assignmentId(7L)
                .problemId(21L)
                .studentProfileId(11L)
                .sourceCode(source)
                .verdict(verdict)
                .submittedAt(LocalDateTime.now().minusHours(1).plusMinutes(minute))
                .build();
    }

    private static SubmissionDiagnosisFact fact(Long id, Long submissionId, String key, String title) {
        return SubmissionDiagnosisFact.builder()
                .id(id)
                .submissionId(submissionId)
                .analysisId(1000L + submissionId)
                .factKey("fact-" + id)
                .factType("REPAIR")
                .displayCategory("REPAIR")
                .normalizedPointKey(key)
                .pointKeySource("FORMAL_ID")
                .pointKeyVersion(IssuePointKeyFactory.VERSION)
                .title(title)
                .knowledgePathJson("[\"第一章\",\"第二小节\",\"" + title + "\"]")
                .knowledgePathStatus("FORMAL")
                .projectionStatus("READY")
                .build();
    }

    private static SubmissionCaseResultStatsProjection stats(Long submissionId, long passed, long total) {
        SubmissionCaseResultStatsProjection projection = mock(SubmissionCaseResultStatsProjection.class);
        when(projection.getSubmissionId()).thenReturn(submissionId);
        when(projection.getPassedTestCases()).thenReturn(passed);
        when(projection.getTotalTestCases()).thenReturn(total);
        return projection;
    }

    private record Fixture(
            SubmissionGrowthSummaryService service,
            SubmissionAnalysisRepository analysisRepository,
            SubmissionCaseResultRepository caseResultRepository,
            SubmissionDiagnosisFactRepository factRepository,
            SubmissionIssueTransitionRepository transitionRepository
    ) {
        private void stub(
                List<Submission> submissions,
                List<SubmissionDiagnosisFact> facts,
                List<SubmissionCaseResultStatsProjection> stats,
                List<Long> analyzedIds
        ) {
            when(analysisRepository.findBySubmissionIdIn(anyList())).thenReturn(analyzedIds.stream()
                    .map(id -> SubmissionAnalysis.builder().id(1000L + id).submissionId(id).build())
                    .toList());
            when(caseResultRepository.summarizeBySubmissionIdIn(anyList())).thenReturn(stats);
            when(factRepository.findBySubmissionIdIn(anyList())).thenReturn(facts);
            when(transitionRepository.findByCurrentSubmissionIdIn(anyList())).thenReturn(List.of());
        }
    }
}
