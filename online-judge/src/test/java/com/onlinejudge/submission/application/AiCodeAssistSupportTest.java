package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCodeAssistSupportTest {

    private final AiCodeAssistSupport support = new AiCodeAssistSupport();

    @Test
    void resolveLineIssuesParsesLooseLineBlocks() {
        String sourceCode = """
                a
                b
                c
                d
                e
                """;
        String rawContent = """
                行 3
                错误：数组越界
                建议：先判断索引范围

                第5行：变量未定义
                建议：先声明变量再使用
                """;

        List<SubmissionAnalysisResponse.LineIssue> issues = support.resolveLineIssues(
                List.of(),
                null,
                rawContent,
                sourceCode,
                List.of()
        );

        assertThat(issues).hasSize(2);
        assertThat(issues.get(0).getLineNumber()).isEqualTo(3);
        assertThat(issues.get(0).getError()).isEqualTo("数组越界");
        assertThat(issues.get(0).getSuggestion()).isEqualTo("先判断索引范围");
        assertThat(issues.get(1).getLineNumber()).isEqualTo(5);
        assertThat(issues.get(1).getError()).isEqualTo("变量未定义");
        assertThat(issues.get(1).getSuggestion()).isEqualTo("先声明变量再使用");
    }

    @Test
    void resolveLineIssuesBackfillsSuggestionForStructuredPayload() {
        String sourceCode = """
                first
                second
                third
                """;

        List<SubmissionAnalysisResponse.LineIssue> issues = support.resolveLineIssues(
                List.of(new AiCodeAssistSupport.LineIssueCandidate(2, "IndexError: list index out of range", "")),
                null,
                null,
                sourceCode,
                List.of()
        );

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getLineNumber()).isEqualTo(2);
        assertThat(issues.get(0).getError()).isEqualTo("IndexError: list index out of range");
        assertThat(issues.get(0).getSuggestion()).isEqualTo("检查下标或索引边界，避免访问越界。");
    }
}
