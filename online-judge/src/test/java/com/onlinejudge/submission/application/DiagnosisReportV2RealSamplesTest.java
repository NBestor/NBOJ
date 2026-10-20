package com.onlinejudge.submission.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisReportV2RealSamplesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void realSamplesCoverEightTargetProblemsWithQualityExpectations() throws Exception {
        List<RealSample> samples = loadSamples();

        assertThat(samples).extracting(RealSample::id)
                .containsExactly(
                        "advanced-coupon-shortest-path-state",
                        "advanced-interval-dp-order-sum",
                        "advanced-segment-tree-lazy-range",
                        "advanced-offline-connectivity-rollback",
                        "advanced-tree-rerooting-weighted",
                        "string-window-boundary",
                        "sliding-window-balance-count",
                        "layered-shortest-path-state",
                        "layered-discounted-shortest-path-edge"
                );
        assertThat(samples).extracting(RealSample::expectedLibraryFit)
                .allMatch(fit -> Set.of("HIT", "PARTIAL", "MISS").contains(fit));
        assertThat(samples).anySatisfy(sample -> assertThat(sample.expectedLibraryFit()).isEqualTo("HIT"));
        assertThat(samples).anySatisfy(sample -> assertThat(sample.expectedLibraryFit()).isEqualTo("PARTIAL"));
        assertThat(samples).filteredOn(RealSample::shouldGenerateGrowthCandidate)
                .allSatisfy(sample -> assertThat(sample.expectedLibraryFit()).isIn("PARTIAL", "MISS"));

        samples.forEach(sample -> {
            assertThat(sample.problemSummary()).isNotBlank();
            assertThat(sample.buggyCode()).isNotBlank();
            assertThat(sample.buggyCode().lines().count()).isGreaterThanOrEqualTo(4);
            assertThat(sample.judgeResult()).isNotBlank();
            assertThat(sample.expectedAnchors()).isNotEmpty();
            assertThat(sample.expectedLibraryPath()).isNotEmpty();
            assertThat(sample.contextPackageRequirements())
                    .contains("完整题目", "完整代码", "判题参考信号", "统一知识树诊断层");
            assertThat(sample.expectedStudentFeedbackQuality()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(sample.quickFeedbackKnownLimit()).isNotBlank();
            assertThat(sample.formalMustImproveAtLeastOneOf()).isNotEmpty();
        });
    }

    @Test
    void formalChainComparisonMetadataRequiresAtLeastOneConcreteImprovementOverQuickFeedback() throws Exception {
        List<RealSample> samples = loadSamples();

        samples.forEach(sample -> {
            assertThat(sample.quickFeedbackKnownLimit())
                    .as(sample.id() + " quick feedback limitation")
                    .containsAnyOf("快反馈", "短反馈", "通常", "可能", "常说");
            assertThat(sample.formalMustImproveAtLeastOneOf())
                    .as(sample.id() + " formal chain improvement")
                    .allSatisfy(requirement -> assertThat(requirement).isNotBlank());
            assertThat(String.join("\n", sample.expectedStudentFeedbackQuality()))
                    .as(sample.id() + " student visible quality")
                    .containsAnyOf("基础层", "下一步", "提高层");
        });
    }

    @Test
    void partialSamplesDeclareReviewCandidateFieldsAndHitSamplesDoNotRequireGrowth() throws Exception {
        List<RealSample> samples = loadSamples();

        samples.stream()
                .filter(RealSample::shouldGenerateGrowthCandidate)
                .forEach(sample -> {
                    assertThat(sample.expectedLibraryFit())
                            .as(sample.id() + " fit")
                            .isIn("PARTIAL", "MISS");
                    assertThat(sample.expectedGrowthCandidateFields())
                            .as(sample.id() + " growth candidate fields")
                            .contains("suggestedPath", "errorSymptom", "typicalCodePattern", "studentExplanation", "status");
                });
        samples.stream()
                .filter(sample -> "HIT".equals(sample.expectedLibraryFit()))
                .forEach(sample -> assertThat(sample.expectedGrowthCandidateFields())
                        .as(sample.id() + " should not require growth")
                        .isEmpty());
    }

    @Test
    void layeredDiscountedShortestPathSampleGuardsTheMisdiagnosisCase() throws Exception {
        RealSample sample = loadSamples().stream()
                .filter(item -> "layered-discounted-shortest-path-edge".equals(item.id()))
                .findFirst()
                .orElseThrow();

        assertThat(sample.expectedLibraryFit()).isEqualTo("PARTIAL");
        assertThat(sample.expectedLibraryPath()).containsExactly("图论", "最短路", "Dijkstra", "分层图状态转移");
        assertThat(sample.buggyCode()).contains("discounted = cost + weight");
        assertThat(String.join("\n", sample.expectedStudentFeedbackQuality()))
                .contains("优惠转移仍使用原边权")
                .contains("不得把主因诊断为 dist 初始化、heap 初始化或状态维度缺失");
    }

    private List<RealSample> loadSamples() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/diagnosis-eval-fixtures/diagnosis-report-v2-real-samples.json")) {
            assertThat(input).isNotNull();
            List<RealSample> samples = objectMapper.readValue(input, new TypeReference<>() {
            });
            return resolveSampleCodeResources(samples);
        }
    }

    private List<RealSample> resolveSampleCodeResources(List<RealSample> samples) throws Exception {
        java.util.ArrayList<RealSample> resolved = new java.util.ArrayList<>();
        for (RealSample sample : samples) {
            String code = sample.buggyCode();
            if ((code == null || code.isBlank())
                    && sample.buggyCodeResource() != null
                    && !sample.buggyCodeResource().isBlank()) {
                try (InputStream input = getClass().getResourceAsStream(sample.buggyCodeResource())) {
                    assertThat(input).as(sample.id() + " buggyCodeResource").isNotNull();
                    code = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            resolved.add(sample.withBuggyCode(code));
        }
        return resolved;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RealSample(
            String id,
            String title,
            String problemSummary,
            String language,
            String buggyCode,
            String buggyCodeResource,
            String judgeResult,
            String expectedLibraryFit,
            List<String> expectedAnchors,
            List<String> expectedLibraryPath,
            List<String> expectedGrowthCandidateFields,
            List<String> contextPackageRequirements,
            List<String> expectedStudentFeedbackQuality,
            boolean shouldGenerateGrowthCandidate,
            String quickFeedbackKnownLimit,
            List<String> formalMustImproveAtLeastOneOf
    ) {
        RealSample withBuggyCode(String resolvedCode) {
            return new RealSample(id, title, problemSummary, language, resolvedCode, buggyCodeResource, judgeResult,
                    expectedLibraryFit, expectedAnchors, expectedLibraryPath, expectedGrowthCandidateFields,
                    contextPackageRequirements, expectedStudentFeedbackQuality, shouldGenerateGrowthCandidate,
                    quickFeedbackKnownLimit, formalMustImproveAtLeastOneOf);
        }
    }
}
