package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.domain.SubmissionAnalysis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisEvidencePackageReaderTest {

    private final DiagnosisEvidencePackageReader reader = new DiagnosisEvidencePackageReader(new ObjectMapper());

    @Test
    void summarizesPersistedEvidencePackage() {
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .submissionId(31L)
                .scenario("WA")
                .evidenceJson("""
                        {
                          "schemaVersion": "evidence-v1",
                          "submission": {
                            "id": 31,
                            "verdict": "WRONG_ANSWER",
                            "sourceCodeLineCount": 6
                          },
                          "problem": {
                            "id": 501,
                            "title": "边界练习",
                            "knowledgePoints": ["循环边界", "输入读取"],
                            "commonMistakes": ["把查询次数读漏"]
                          },
                          "judgeFacts": {
                            "passedCount": 2,
                            "totalCount": 4,
                            "hiddenFailureObserved": true,
                            "firstFailedCase": {
                              "testCaseNumber": 3,
                              "hidden": true
                            }
                          },
                          "policy": {
                            "hintPolicy": "L2"
                          }
                        }
                        """)
                .build();

        DiagnosisEvidencePackageReader.EvidenceSummary summary = reader.summarize(analysis, null);

        assertThat(summary.source()).isEqualTo("persisted");
        assertThat(summary.evidenceRefs()).contains(
                "evidence:evidence-v1",
                "submission:31",
                "problem:knowledge_points",
                "problem:common_mistakes",
                "judge:cases:2/4",
                "judge:hidden_failure",
                "policy:L2"
        );
        assertThat(summary.detailLines()).contains("已通过 2/4 个测试点");
        assertThat(String.join("；", summary.detailLines())).contains("隐藏测试点");
        assertThat(String.join("；", summary.detailLines())).contains("题目知识点");
        assertThat(String.join("；", summary.detailLines())).contains("把查询次数读漏");
    }

    @Test
    void fallsBackToLegacySummaryWhenEvidenceJsonIsMissingOrBroken() {
        Submission submission = Submission.builder()
                .id(32L)
                .verdict(Submission.Verdict.RUNTIME_ERROR)
                .build();
        SubmissionAnalysis analysis = SubmissionAnalysis.builder()
                .submissionId(32L)
                .scenario("RE")
                .headline("运行异常")
                .evidenceJson("{broken")
                .build();

        DiagnosisEvidencePackageReader.EvidenceSummary summary = reader.summarize(analysis, submission);

        assertThat(summary.source()).isEqualTo("legacy-summary");
        assertThat(summary.evidenceRefs()).contains("analysis:legacy_summary", "analysis:RE", "verdict:runtime_error");
        assertThat(String.join("；", summary.detailLines())).contains("旧诊断缺少完整证据包");
    }
}
