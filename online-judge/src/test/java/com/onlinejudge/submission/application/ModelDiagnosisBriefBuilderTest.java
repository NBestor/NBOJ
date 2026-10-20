package com.onlinejudge.submission.application;

import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDiagnosisBriefBuilderTest {

    private final ModelDiagnosisBriefBuilder briefBuilder = new ModelDiagnosisBriefBuilder();

    @Test
    void briefRedactsHiddenFailedCaseData() {
        DiagnosisEvidencePackage evidencePackage = DiagnosisEvidencePackage.builder()
                .problem(problem())
                .submission(submission())
                .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                        .hiddenFailureObserved(true)
                        .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                .testCaseNumber(7)
                                .hidden(true)
                                .input("secret input")
                                .expectedOutput("secret expected")
                                .actualOutput("secret actual")
                                .build())
                        .caseResultsSummary(List.of(DiagnosisEvidencePackage.CaseSummary.builder()
                                .testCaseNumber(7)
                                .hidden(true)
                                .passed(false)
                                .inputPreview("secret input")
                                .actualOutputPreview("secret actual")
                                .expectedOutputPreview("secret expected")
                                .semanticCode("TCI_HIDDEN_BOUNDARY")
                                .intentType("BOUNDARY")
                                .intentTitle("最小规模边界")
                                .intentSummary("覆盖输入规模处在最小合法边界时的程序行为。")
                                .learningObjective("能检查初始化、空循环和边界返回是否保持定义一致。")
                                .contestRole("CORRECTNESS_GUARD")
                                .revealPolicy("AI_GENERALIZED")
                                .build()))
                        .build())
                .build();

        ModelDiagnosisBrief brief = briefBuilder.build(evidencePackage, null);

        assertThat(brief.getFirstFailedCase()).isNotNull();
        assertThat(brief.getFirstFailedCase().isHidden()).isTrue();
        assertThat(brief.getFirstFailedCase().getInput()).isNull();
        assertThat(brief.getFirstFailedCase().getExpectedOutput()).isNull();
        assertThat(brief.getFirstFailedCase().getActualOutput()).isNull();
        assertThat(brief.getVisibleCaseFacts()).singleElement()
                .satisfies(fact -> {
                    assertThat(fact.getHidden()).isTrue();
                    assertThat(fact.getInputPreview()).isNull();
                    assertThat(fact.getActualOutputPreview()).isNull();
                    assertThat(fact.getExpectedOutputPreview()).isNull();
                });
        assertThat(brief.getTestIntentFacts()).singleElement()
                .satisfies(fact -> {
                    assertThat(fact.getHidden()).isTrue();
                    assertThat(fact.getEvidenceRef()).isEqualTo("judge:test-intent:TCI_HIDDEN_BOUNDARY");
                    assertThat(fact.getIntentSummary()).contains("最小合法边界");
                    assertThat(fact.toString()).doesNotContain("secret input", "secret expected", "secret actual");
                });
        assertThat(brief.getEvidenceRefs()).contains("judge:test-intent:TCI_HIDDEN_BOUNDARY");
        assertThat(brief.getHiddenDataBoundary().getHiddenFailureObserved()).isTrue();
        assertThat(brief.getHiddenDataBoundary().getHiddenInputVisible()).isFalse();
        assertThat(brief.getUncertainty()).contains("Hidden failure");
    }

    @Test
    void briefUsesOnlyJudgeBaselineAndMemoryEvidenceRefs() {
        ModelDiagnosisBrief brief = briefBuilder.build(
                DiagnosisEvidencePackage.builder()
                        .problem(problem())
                        .submission(submission())
                        .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                                .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                        .testCaseNumber(1)
                                        .hidden(false)
                                        .input("3")
                                        .expectedOutput("6")
                                        .actualOutput("3")
                                        .build())
                                .build())
                        .build(),
                SubmissionAnalysisResponse.builder()
                        .issueTags(List.of("BOUNDARY_CONDITION"))
                        .fineGrainedTags(List.of("OFF_BY_ONE"))
                        .evidenceRefs(List.of("baseline:teacher"))
                        .build()
        );

        assertThat(brief.getAllowedIssueTags()).contains("BOUNDARY_CONDITION", "NEEDS_MORE_EVIDENCE");
        assertThat(brief.getAllowedIssueTags()).doesNotContain("LOOP_BOUNDARY");
        assertThat(brief.getAllowedFineGrainedTags()).contains("OFF_BY_ONE");
        assertThat(brief.getEvidenceRefs()).contains("judge:first_failed_case", "baseline:teacher");
        assertThat(brief.getEvidenceRefs()).doesNotContain("code:range_excludes_n");
        assertThat(brief.getKeyCodeExcerpt()).contains("1:");
    }

    @Test
    void briefCarriesVisibleInputPreviewForFailedCases() {
        ModelDiagnosisBrief brief = briefBuilder.build(
                DiagnosisEvidencePackage.builder()
                        .problem(problem())
                        .submission(submission())
                        .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                                .caseResultsSummary(List.of(DiagnosisEvidencePackage.CaseSummary.builder()
                                        .testCaseNumber(1)
                                        .hidden(false)
                                        .passed(false)
                                        .inputPreview("5\n2 7 9 3 1")
                                        .actualOutputPreview("9")
                                        .expectedOutputPreview("12")
                                        .build()))
                                .build())
                        .build(),
                null
        );

        assertThat(brief.getVisibleCaseFacts()).singleElement()
                .satisfies(fact -> assertThat(fact.getInputPreview()).isEqualTo("5\n2 7 9 3 1"));
    }

    @Test
    void standardLibraryPackUsesOnlyBriefAllowedTags() {
        ModelDiagnosisBrief brief = briefBuilder.build(
                DiagnosisEvidencePackage.builder()
                        .problem(problem())
                        .submission(submission())
                        .build(),
                null
        );

        StandardLibraryPack pack = new StandardLibraryPackBuilder(new DiagnosisTaxonomy()).build(brief);

        assertThat(pack.getIssueTags()).extracting(StandardLibraryPack.TagOption::getId)
                .containsExactly("NEEDS_MORE_EVIDENCE");
        assertThat(pack.getFineGrainedTags()).extracting(StandardLibraryPack.TagOption::getId)
                .isEmpty();
        assertThat(pack.getTeachingActions()).extracting(StandardLibraryPack.TeachingActionOption::getId)
                .containsExactly("COLLECT_EVIDENCE");
        assertThat(pack.getBasicCauses()).extracting(StandardLibraryPack.BasicCauseOption::getId)
                .containsExactly("NEEDS_MORE_EVIDENCE");
        assertThat(pack.getImprovementPoints()).extracting(StandardLibraryPack.ImprovementPointOption::getId)
                .contains("TESTING_HABIT", "TRANSFER_REVIEW");
    }

    @Test
    void briefCarriesPreviousLearningActionFeedback() {
        ModelDiagnosisBrief brief = briefBuilder.build(
                DiagnosisEvidencePackage.builder()
                        .problem(problem())
                        .submission(submission())
                        .history(DiagnosisEvidencePackage.HistoryEvidence.builder()
                                .previousVerdict("WRONG_ANSWER")
                                .transitionSignal("same verdict remains")
                                .previousInterventionType("MIN_CASE_TRACE")
                                .previousLearningActionStatus("CONTRADICTED")
                                .previousLearningActionConfidence(0.74)
                                .previousLearningActionSummary("The same issue remained after the previous action.")
                                .previousLearningActionNextAdjustment("Shrink the next task.")
                                .build())
                        .build(),
                null
        );

        assertThat(brief.getLearningTrajectorySummary())
                .contains("previousIntervention=MIN_CASE_TRACE")
                .contains("actionStatus=CONTRADICTED")
                .contains("actionConfidence=0.74")
                .contains("Shrink the next task");
    }

    @Test
    void briefCarriesLearningMemoryWithoutTurningItIntoCurrentCodeEvidence() {
        DiagnosisEvidencePackage evidencePackage = DiagnosisEvidencePackage.builder()
                .problem(problem())
                .submission(submission())
                .judgeFacts(DiagnosisEvidencePackage.JudgeFacts.builder()
                        .firstFailedCase(SubmissionAnalysisResponse.FailedCaseSnapshot.builder()
                                .testCaseNumber(1)
                                .hidden(false)
                                .input("3")
                                .expectedOutput("6")
                                .actualOutput("3")
                                .build())
                        .build())
                .learningMemory(DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                        .studentProfileId(9L)
                        .observedSubmissionCount(5)
                        .recurringIssueTags(List.of(DiagnosisEvidencePackage.MemoryTagStat.builder()
                                .tag("IO_FORMAT")
                                .count(3L)
                                .evidenceSubmissionIds(List.of(101L, 102L, 103L))
                                .build()))
                        .recurringFineGrainedTags(List.of(DiagnosisEvidencePackage.MemoryTagStat.builder()
                                .tag("INPUT_PARSING")
                                .count(3L)
                                .evidenceSubmissionIds(List.of(101L, 102L, 103L))
                                .build()))
                        .abilityFocus(List.of(DiagnosisEvidencePackage.AbilityFocus.builder()
                                .abilityPoint("题意读取")
                                .submissionCount(3L)
                                .problemCount(2L)
                                .evidenceTags(List.of("IO_FORMAT", "INPUT_PARSING"))
                                .build()))
                        .recentTrend("历史中多次出现输入读取问题")
                        .evidenceRefs(List.of("memory:student:9", "memory:recurring_issue:IO_FORMAT"))
                        .build())
                .build();

        ModelDiagnosisBrief brief = briefBuilder.build(evidencePackage, null);

        assertThat(brief.getLearningMemorySummary())
                .contains("observedSubmissions=5")
                .contains("recurringIssueTags")
                .contains("abilityFocus");
        assertThat(brief.getEvidenceRefs())
                .contains("judge:first_failed_case", "memory:student:9");
        assertThat(brief.getEvidenceRefs()).doesNotContain("code:range_excludes_n");
        assertThat(brief.getAllowedIssueTags()).contains("IO_FORMAT", "NEEDS_MORE_EVIDENCE");
        assertThat(brief.getAllowedIssueTags()).doesNotContain("LOOP_BOUNDARY");
        assertThat(brief.getAllowedFineGrainedTags()).contains("INPUT_PARSING");
        assertThat(brief.getAllowedFineGrainedTags()).doesNotContain("OFF_BY_ONE");
    }

    @Test
    void briefCarriesTeacherCalibrationAsAuxiliaryConstraint() {
        DiagnosisEvidencePackage evidencePackage = DiagnosisEvidencePackage.builder()
                .problem(problem())
                .submission(submission())
                .learningMemory(DiagnosisEvidencePackage.StudentLearningMemorySnapshot.builder()
                        .teacherCalibrationPatterns(List.of(DiagnosisEvidencePackage.TeacherCalibrationPattern.builder()
                                .originalIssueTag("LOOP_BOUNDARY")
                                .originalFineGrainedTag("OFF_BY_ONE")
                                .correctedIssueTag("IO_FORMAT")
                                .correctedFineGrainedTag("INPUT_PARSING")
                                .correctionCount(2L)
                                .latestTeacherNote("学生实际是读入多组数据时漏读。")
                                .evidenceSubmissionIds(List.of(31L, 32L))
                                .evidenceRefs(List.of("memory:teacher_calibration:input_parsing", "teacher_correction:submission:31"))
                                .build()))
                        .evidenceRefs(List.of("memory:teacher_corrections:2"))
                        .build())
                .build();

        ModelDiagnosisBrief brief = briefBuilder.build(evidencePackage, null);

        assertThat(brief.getTeacherCalibrationSummary())
                .contains("corrected=INPUT_PARSING")
                .contains("count=2");
        assertThat(brief.getLearningMemorySummary()).contains("teacherCalibration");
        assertThat(brief.getAllowedIssueTags()).contains("IO_FORMAT", "NEEDS_MORE_EVIDENCE");
        assertThat(brief.getAllowedIssueTags()).doesNotContain("LOOP_BOUNDARY");
        assertThat(brief.getAllowedFineGrainedTags()).contains("INPUT_PARSING");
        assertThat(brief.getAllowedFineGrainedTags()).doesNotContain("OFF_BY_ONE");
        assertThat(brief.getEvidenceRefs()).contains("memory:teacher_corrections:2");
        assertThat(brief.getEvidenceRefs()).doesNotContain("memory:teacher_calibration:input_parsing");
    }

    private DiagnosisEvidencePackage.ProblemEvidence problem() {
        return DiagnosisEvidencePackage.ProblemEvidence.builder()
                .id(1L)
                .title("Sum 1 to n")
                .description("Input n and output the sum from 1 to n.")
                .timeLimit(1000)
                .memoryLimit(65536)
                .knowledgePoints(List.of("loop"))
                .commonMistakes(List.of("off by one"))
                .build();
    }

    private DiagnosisEvidencePackage.SubmissionEvidence submission() {
        return DiagnosisEvidencePackage.SubmissionEvidence.builder()
                .id(11L)
                .language("Python 3")
                .verdict("WRONG_ANSWER")
                .sourceCode("""
                        n = int(input())
                        s = 0
                        for i in range(1, n):
                            s += i
                        print(s)
                        """)
                .sourceCodeWithLineNumbers("""
                        1: n = int(input())
                        2: s = 0
                        3: for i in range(1, n):
                        4:     s += i
                        5: print(s)
                        """)
                .sourceCodeLineCount(5)
                .build();
    }

}
