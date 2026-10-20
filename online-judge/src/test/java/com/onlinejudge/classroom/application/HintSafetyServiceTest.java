package com.onlinejudge.classroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HintSafetyServiceTest {

    private final HintSafetyService service = new HintSafetyService(null, new ObjectMapper(), new DiagnosisTaxonomy());

    @Test
    void downgradesStructuredHintAndInterventionPlanWhenTheyLeakAnswerLikeContent() {
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .submissionId(99L)
                .issueTags(List.of("BOUNDARY_CONDITION"))
                .studentHint("Check the boundary first.")
                .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                        .hintLevel("L2")
                        .problemType("loop boundary")
                        .evidenceAnchor("line 3")
                        .nextAction("Use this complete code: int main() { return 0; }")
                        .coachQuestion("Copy the answer below.")
                        .teachingAction("TRACE_VARIABLES")
                        .evidenceRefs(List.of("code:loop_present"))
                        .answerLeakRisk("LOW")
                        .build())
                .learningInterventionPlan(SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                        .interventionType("MIN_CASE")
                        .goal("Find the boundary issue.")
                        .studentTask("Paste this full code block: #include <stdio.h>")
                        .checkQuestion("What does the answer code do?")
                        .completionSignal("Student copies the full code.")
                        .evidenceRefs(List.of("code:loop_present"))
                        .estimatedMinutes(5)
                        .answerLeakRisk("LOW")
                        .build())
                .reportMarkdown("ordinary report")
                .answerLeakRisk("LOW")
                .build();

        SubmissionAnalysisResponse result = service.verifyAndRecord(analysis, Assignment.HintPolicy.L2);

        assertThat(result.getAnswerLeakRisk()).isEqualTo("HIGH");
        assertThat(result.getStudentHintPlan()).isNotNull();
        assertThat(result.getStudentHintPlan().getTeachingAction()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(result.getLearningInterventionPlan()).isNotNull();
        assertThat(result.getLearningInterventionPlan().getInterventionType()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(result.getLearningInterventionPlan().getAnswerLeakRisk()).isEqualTo("HIGH");
        assertThat(result.getLearningInterventionPlan().getStudentTask()).doesNotContain("int main", "#include");
        assertThat(result.getReportMarkdown()).doesNotContain("int main", "#include");
    }

    @Test
    void doesNotDowngradeSafeHintBecauseReportContainsSubmittedCodeLocation() {
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .submissionId(100L)
                .issueTags(List.of("IO_FORMAT"))
                .studentHint("请对比题目要求的输入行数与代码中实际调用读取函数的次数。")
                .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                        .hintLevel("L2")
                        .problemType("输入输出格式")
                        .evidenceAnchor("problem:repeated_input_source_single_read")
                        .nextAction("统计当前代码处理了几次查询，并和题面 q 次查询对齐。")
                        .coachQuestion("题目说要进行 q 次查询，但你的代码目前只执行了一次输出，这说明了什么？")
                        .teachingAction("COMPARE_INPUT_SPEC")
                        .evidenceRefs(List.of("problem:repeated_input_source_single_read"))
                        .answerLeakRisk("LOW")
                        .build())
                .learningInterventionPlan(SubmissionAnalysisResponse.LearningInterventionPlan.builder()
                        .interventionType("COMPARE_INPUT_SPEC")
                        .goal("把题面输入结构和实际读取结构对齐。")
                        .studentTask("画出 q 次查询对应的读取次数。")
                        .checkQuestion("从题面第几行开始，读取次数和要求不一致？")
                        .completionSignal("学生能指出读取次数不一致的位置。")
                        .evidenceRefs(List.of("problem:repeated_input_source_single_read"))
                        .estimatedMinutes(5)
                        .answerLeakRisk("LOW")
                        .build())
                .reportMarkdown("""
                        ## AI 阶段化诊断

                        - 错因阶段：IO_FORMAT / INPUT_PARSING

                        ## 给学生的下一步

                        请对比题目要求的输入行数与代码中实际调用读取函数的次数。

                        ## 代码定位

                        def read_numbers():
                            return list(map(int, input().split()))
                        n, q = read_numbers()
                        l, r = read_numbers()
                        """)
                .answerLeakRisk("LOW")
                .build();

        SubmissionAnalysisResponse result = service.verifyAndRecord(analysis, Assignment.HintPolicy.L2);

        assertThat(result.getAnswerLeakRisk()).isEqualTo("LOW");
        assertThat(result.getStudentHintPlan()).isNotNull();
        assertThat(result.getStudentHintPlan().getTeachingAction()).isEqualTo("COMPARE_INPUT_SPEC");
        assertThat(result.getReportMarkdown()).doesNotContain("提示已安全降级");
    }

    @Test
    void downgradesEnglishDirectFixAndHiddenTestPhrases() {
        SubmissionAnalysisResponse analysis = SubmissionAnalysisResponse.builder()
                .submissionId(101L)
                .issueTags(List.of("IO_FORMAT"))
                .studentHint("The final answer is to directly change the parser and use the hidden test input.")
                .studentHintPlan(SubmissionAnalysisResponse.StudentHintPlan.builder()
                        .hintLevel("L2")
                        .problemType("input format")
                        .evidenceAnchor("problem:input-shape")
                        .nextAction("Apply this exact fix now.")
                        .coachQuestion("What is the hidden test output?")
                        .teachingAction("COMPARE_INPUT_SPEC")
                        .evidenceRefs(List.of("problem:input-shape"))
                        .answerLeakRisk("LOW")
                        .build())
                .reportMarkdown("Hidden test input and output are shown here.")
                .answerLeakRisk("LOW")
                .build();

        SubmissionAnalysisResponse result = service.verifyAndRecord(analysis, Assignment.HintPolicy.L2);

        assertThat(result.getAnswerLeakRisk()).isEqualTo("HIGH");
        assertThat(result.getStudentHint()).doesNotContain("directly change", "hidden test");
        assertThat(result.getStudentHintPlan().getTeachingAction()).isEqualTo("COLLECT_EVIDENCE");
        assertThat(result.getReportMarkdown()).contains("提示已安全降级");
    }
}
