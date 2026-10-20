package com.onlinejudge.submission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.learning.diagnosis.DiagnosisTaxonomy;
import com.onlinejudge.submission.dto.SubmissionAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineRuntimeProfileEvalReportFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsActionableFailureReasonsWhenCompactProfileLosesAnchors() {
        OfflineRuntimeProfileEvalReport report = new OfflineRuntimeProfileEvalReportFactory(
                objectMapper,
                new NoAnchorRuntime()
        ).fromCases(List.of(new OfflineRuntimeProfileEvalReportFactory.OfflineEvalCase(
                "missing-anchors",
                DiagnosisEvidencePackage.builder().build(),
                SubmissionAnalysisResponse.builder().submissionId(1L).build()
        )));

        assertThat(report.getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getRequestBytesReduced()).isFalse();
                    assertThat(entry.getAutoRequestBytesReduced()).isFalse();
                    assertThat(entry.getAutoRequestCompact()).isFalse();
                    assertThat(entry.getQualityPreserved()).isFalse();
                    assertThat(entry.getAutoQualityPreserved()).isFalse();
                    assertThat(entry.getFailureReasons()).contains(
                            "LOW_LATENCY_REQUEST_NOT_SMALLER",
                            "MISSING_EVIDENCE_REFS",
                            "MISSING_ISSUE_TAGS",
                            "MISSING_TEACHING_ACTIONS",
                            "MISSING_HIDDEN_BOUNDARY"
                    );
                    assertThat(entry.getFailureReasons()).doesNotContain("MISSING_CANDIDATE_SIGNALS");
                    assertThat(entry.getAutoFailureReasons()).contains(
                            "AUTO_MISSING_EVIDENCE_REFS",
                            "AUTO_MISSING_ISSUE_TAGS",
                            "AUTO_MISSING_TEACHING_ACTIONS",
                            "AUTO_MISSING_HIDDEN_BOUNDARY"
                    );
                    assertThat(entry.getAutoFailureReasons()).doesNotContain("AUTO_MISSING_CANDIDATE_SIGNALS");
                });
    }

    private static class NoAnchorRuntime extends ExternalModelAgentRuntime {

        NoAnchorRuntime() {
            super(
                    new ModelDiagnosisBriefBuilder(),
                    new StandardLibraryPackBuilder(new DiagnosisTaxonomy()),
                    new PromptTemplateRegistry(),
                    new ModelOutputValidator()
            );
        }

        @Override
        public RuntimePlan prepare(DiagnosisEvidencePackage evidencePackage,
                                   SubmissionAnalysisResponse fallback,
                                   String runtimeProfile) {
            ModelDiagnosisBrief brief = ModelDiagnosisBrief.builder()
                    .schemaVersion(ModelDiagnosisBrief.SCHEMA_VERSION)
                    .evidenceRefs(List.of())
                    .build();
            StandardLibraryPack pack = StandardLibraryPack.builder()
                    .schemaVersion(StandardLibraryPack.SCHEMA_VERSION)
                    .issueTags(List.of())
                    .fineGrainedTags(List.of())
                    .teachingActions(List.of())
                    .build();
            return RuntimePlan.builder()
                    .brief(brief)
                    .standardLibraryPack(pack)
                    .advicePrompt(new PromptTemplateRegistry().get(PromptTemplateRegistry.DIAGNOSIS_AND_ADVICE_V1))
                    .runtimeProfile(runtimeProfile)
                    .requestCompact(false)
                    .build();
        }
    }
}
