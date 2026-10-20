package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.classroom.application.HintSafetyService;
import com.onlinejudge.classroom.domain.Assignment;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.submission.domain.Submission;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticAgentServiceLearningMemoryTest {

    @Test
    void repeatedLearningMemoryShrinksInterventionWithoutOverridingCurrentDiagnosis() {
        DiagnosticAgentService service = new DiagnosticAgentService(
                new DiagnosisEvidencePackageBuilder(),
                new PassThroughAiReportService(),
                new PassThroughHintSafetyService(),
                new DiagnosisTaxonomy()
        );
        SubmissionAnalysisResponse baseline = SubmissionAnalysisResponse.builder()
                .analysisSchemaVersion("diagnosis-v1")
                .evidenceSchemaVersion(DiagnosisEvidencePackage.SCHEMA_VERSION)
                .taxonomyVersion(DiagnosisTaxonomy.TAXONOMY_VERSION)
                .submissionId(11L)
                .sourceType("RULE_BASELINE")
                .scenario("CE")
                .headline("编译失败")
                .summary("当前提交有明确编译错误。")
                .issueTags(List.of("SYNTAX_ERROR"))
                .fineGrainedTags(List.of())
                .abilityPoints(List.of("语法与运行环境"))
                .focusPoints(List.of())
                .fixDirections(List.of())
                .evidenceRefs(List.of("judge:compile_output"))
                .studentHint("先只看第一条编译错误。")
                .teacherNote("当前直接证据是编译错误。")
                .progressSignal("语法或编译环境需要先过关")
                .confidence(0.88)
                .uncertainty("当前编译输出是直接证据。")
                .answerLeakRisk("LOW")
                .lineIssues(List.of())
                .build();

        DiagnosisEvidencePackage.StudentLearningMemorySnapshot memory =
                DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                        .studentProfileId(9L)
                        .observedSubmissionCount(6)
                        .recurringIssueTags(List.of(DiagnosisEvidencePackage.MemoryTagStat.builder()
                                .tag("IO_FORMAT")
                                .count(4L)
                                .evidenceSubmissionIds(List.of(1L, 2L, 3L, 4L))
                                .build()))
                        .recentTrend("最近 3 次历史提交仍未通过，诊断应收窄到最小证据，不要重复泛泛提示。")
                        .evidenceRefs(List.of("memory:student:9", "memory:recurring_issue:IO_FORMAT"))
                        .build();

        DiagnosticAgentService.AgentResult result = service.diagnose(
                Problem.builder().id(1L).title("题目").description("描述").build(),
                Submission.builder()
                        .id(11L)
                        .problemId(1L)
                        .studentProfileId(9L)
                        .assignmentId(3L)
                        .languageName("Python 3")
                        .verdict(Submission.Verdict.COMPILATION_ERROR)
                        .sourceCode("print(")
                        .compileOutput("SyntaxError: '(' was never closed")
                        .build(),
                List.of(),
                baseline,
                Assignment.HintPolicy.L2,
                DiagnosisEvidencePackage.HistoryEvidence.builder()
                        .transitionSignal("本题暂无历史提交")
                        .build(),
                memory
        );

        assertThat(result.evidencePackage().getLearningMemory()).isNotNull();
        assertThat(result.analysis().getIssueTags()).contains("SYNTAX_ERROR");
        assertThat(result.analysis().getIssueTags()).doesNotContain("IO_FORMAT");
        assertThat(result.analysis().getLearningInterventionPlan().getStudentTask())
                .contains("最小失败样例")
                .doesNotContain("输入格式");
        assertThat(result.analysis().getLearningInterventionPlan().getEvidenceRefs())
                .contains("memory:student:9", "judge:compile_output");
    }

    private static class PassThroughAiReportService extends AiReportService {
        PassThroughAiReportService() {
            super(new ObjectMapper(), new AiCodeAssistSupport());
        }

        @Override
        public SubmissionAnalysisResponse enhanceSubmissionAnalysis(Problem problem,
                                                                    Submission submission,
                                                                    SubmissionAnalysisResponse fallback,
                                                                    DiagnosisEvidencePackage evidencePackage) {
            SubmissionAnalysisResponse response = new ObjectMapper().convertValue(fallback, SubmissionAnalysisResponse.class);
            response.setSourceType("AI_MODEL");
            response.setAiInvocation(SubmissionAnalysisResponse.AiInvocation.builder()
                    .provider("MODEL_SCOPE")
                    .model("deepseek-ai/DeepSeek-V4-Pro")
                    .promptVersion(PromptTemplateRegistry.DIAGNOSIS_REPORT_V2)
                    .status("MODEL_COMPLETED")
                    .fallbackUsed(false)
                    .runtimeMode("diagnosis-report")
                    .runtimeProfile(ExternalModelAgentRuntime.RUNTIME_PROFILE_STANDARD)
                    .requestCompact(false)
                    .build());
            return response;
        }
    }

    private static class PassThroughHintSafetyService extends HintSafetyService {
        PassThroughHintSafetyService() {
            super(null, new ObjectMapper(), new DiagnosisTaxonomy());
        }

        @Override
        public SubmissionAnalysisResponse verifyAndRecord(SubmissionAnalysisResponse analysis,
                                                          Assignment.HintPolicy hintPolicy) {
            return analysis;
        }
    }
}
