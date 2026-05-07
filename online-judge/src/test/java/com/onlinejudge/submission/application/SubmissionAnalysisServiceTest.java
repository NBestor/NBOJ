package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionCaseResult;
import com.onlinejudge.submission.dto.SubmissionResponse;
import com.onlinejudge.submission.persistence.SubmissionAnalysisRepository;
import com.onlinejudge.submission.persistence.SubmissionCaseResultRepository;
import com.onlinejudge.submission.persistence.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SubmissionAnalysisServiceTest {

    @Test
    void buildSubmissionResponseAddsCaseSummaryAndMasksHiddenFailure() {
        SubmissionAnalysisService service = new SubmissionAnalysisService(
                mock(SubmissionRepository.class),
                mock(ProblemRepository.class),
                mock(SubmissionCaseResultRepository.class),
                mock(SubmissionAnalysisRepository.class),
                new ObjectMapper(),
                new AiCodeAssistSupport(),
                mock(AiReportService.class)
        );
        Problem problem = Problem.builder()
                .id(1L)
                .title("Two Sum")
                .build();
        Submission submission = Submission.builder()
                .id(10L)
                .problemId(1L)
                .languageId(71)
                .languageName("Python 3")
                .sourceCode("print(1)")
                .verdict(Submission.Verdict.WRONG_ANSWER)
                .build();

        SubmissionResponse response = service.buildSubmissionResponse(
                submission,
                problem,
                List.of(
                        SubmissionCaseResult.builder()
                                .testCaseNumber(1)
                                .passed(true)
                                .actualOutput("3")
                                .expectedOutput("3")
                                .hidden(false)
                                .build(),
                        SubmissionCaseResult.builder()
                                .testCaseNumber(2)
                                .passed(false)
                                .actualOutput("secret actual")
                                .expectedOutput("secret expected")
                                .hidden(true)
                                .build()
                ),
                null
        );

        assertThat(response.getPassedTestCases()).isEqualTo(1);
        assertThat(response.getTotalTestCases()).isEqualTo(2);
        assertThat(response.getFirstFailedCase()).isNotNull();
        assertThat(response.getFirstFailedCase().getTestCaseNumber()).isEqualTo(2);
        assertThat(response.getFirstFailedCase().isHidden()).isTrue();
        assertThat(response.getFirstFailedCase().getActualOutput()).doesNotContain("secret");
        assertThat(response.getFirstFailedCase().getExpectedOutput()).doesNotContain("secret");
    }
}
