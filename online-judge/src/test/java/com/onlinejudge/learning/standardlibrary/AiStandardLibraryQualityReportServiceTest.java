package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryQualityReportService;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeedCatalog;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryQualityReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiStandardLibraryQualityReportServiceTest {

    private final AiStandardLibraryQualityReportService service = new AiStandardLibraryQualityReportService();

    @Test
    void generatesStandardLibraryQualityReport() {
        AiStandardLibraryQualityReport report = service.generate();

        assertThat(report.summary().domainCount()).isEqualTo(6);
        assertThat(report.summary().chapterCount()).isGreaterThanOrEqualTo(34);
        assertThat(report.summary().topicCount()).isGreaterThanOrEqualTo(110);
        assertThat(report.summary().knowledgePointCount()).isGreaterThanOrEqualTo(550);
        assertThat(report.summary().totalItemCount()).isGreaterThanOrEqualTo(460);
        assertThat(report.summary().handwrittenSkillUnitCount()).isGreaterThanOrEqualTo(65);
        assertThat(report.summary().handwrittenMistakePointCount()).isGreaterThanOrEqualTo(164);
        assertThat(AiStandardLibrarySeedCatalog.seeds())
                .noneMatch(AiStandardLibrarySeedCatalog::isGeneratedFallbackSeed);
        assertThat(AiStandardLibrarySeedCatalog.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("archivedGeneratedFallbackSeeds"));

        assertThat(report.domainCoverage())
                .anySatisfy(domain -> {
                    assertThat(domain.domainCode()).isEqualTo("BASIC");
                    assertThat(domain.knowledgePointCount()).isGreaterThanOrEqualTo(170);
                    assertThat(domain.handwrittenMistakeLinkCount()).isGreaterThanOrEqualTo(48);
                })
                .anySatisfy(domain -> {
                    assertThat(domain.domainCode()).isEqualTo("ALGO");
                    assertThat(domain.knowledgePointCount()).isGreaterThanOrEqualTo(140);
                    assertThat(domain.handwrittenMistakeLinkCount()).isGreaterThanOrEqualTo(70);
                });

        assertThat(report.mistakeTypeDistribution())
                .containsKeys("BOUNDARY", "STATE", "MODELING");
        assertThat(report.recommendations())
                .anyMatch(recommendation -> recommendation.contains("字符串"))
                .anyMatch(recommendation -> recommendation.contains("模拟"));
    }

    @Test
    void reportsWeakTopicsForPurposefulExpansion() {
        AiStandardLibraryQualityReport report = service.generate();

        assertThat(report.weakTopics())
                .anySatisfy(topic -> {
                    assertThat(topic.topicCode()).isEqualTo("ALGO.SIM.PROCESS");
                    assertThat(topic.recommendation()).contains("状态变量").contains("事件顺序");
                })
                .anySatisfy(topic -> {
                    assertThat(topic.topicCode()).isEqualTo("BASIC.STRING.MATCH");
                    assertThat(topic.recommendation()).contains("查找失败");
                })
                .anySatisfy(topic -> {
                    assertThat(topic.topicCode()).isEqualTo("BASIC.ARRAY.UPDATE");
                    assertThat(topic.recommendation()).contains("原地覆盖");
                });
    }
}
