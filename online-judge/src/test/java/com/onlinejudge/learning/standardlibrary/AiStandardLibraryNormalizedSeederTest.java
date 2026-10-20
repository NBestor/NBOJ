package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeed;
import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeedCatalog;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryNormalizedSeeder;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibrarySeedCatalog;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardImprovementPoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLegacyMapping;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryRelationType;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryTargetType;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardMistakePoint;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryLegacyMappingRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryRelationRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-standard-library-normalized-seeder;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false",
        "app.content-migration.enabled=true"
})
class AiStandardLibraryNormalizedSeederTest {

    @Autowired
    AiStandardSkillUnitRepository skillUnitRepository;

    @Autowired
    AiStandardMistakePointRepository mistakePointRepository;

    @Autowired
    AiStandardImprovementPointRepository improvementPointRepository;

    @Autowired
    AiStandardLibraryRelationRepository relationRepository;

    @Autowired
    AiStandardLibraryLegacyMappingRepository legacyMappingRepository;

    @Autowired
    AiStandardLibraryNormalizedSeeder normalizedSeeder;

    @Test
    void normalizesSeedCatalogIntoSkillMistakeImprovementStructureIdempotently() {
        long skillCount = skillUnitRepository.count();
        long mistakeCount = mistakePointRepository.count();
        long improvementCount = improvementPointRepository.count();
        long mappingCount = legacyMappingRepository.count();
        long relationCount = relationRepository.count();

        assertThat(skillCount).isGreaterThanOrEqualTo(112);
        assertThat(mistakeCount).isGreaterThanOrEqualTo(295);
        assertThat(improvementCount).isGreaterThanOrEqualTo(50);
        assertThat(mappingCount).isEqualTo(AiStandardLibrarySeedCatalog.seeds().size());
        assertThat(relationCount).isGreaterThan(0);
        assertThat(skillUnitRepository.findAll())
                .noneMatch(skill -> AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(
                        AiStandardLibraryLayer.SKILL_UNIT, skill.getCode()));
        assertThat(mistakePointRepository.findAll())
                .noneMatch(mistake -> AiStandardLibrarySeedCatalog.isGeneratedFallbackCode(
                        AiStandardLibraryLayer.MISTAKE_POINT, mistake.getCode()));
        assertPrimaryAnchorsAreKnowledgePoints();

        var skill = skillUnitRepository.findByCode("SK_BINARY_ANSWER_CHECK").orElseThrow();
        assertThat(skill.getPrimaryKnowledgeNodeCode()).isEqualTo("ALGO.BINARY.ANSWER.check_函数");
        assertThat(skill.getKnowledgeNodeCodes()).contains("ALGO.BINARY.ANSWER.check_函数");
        assertThat(skill.getLearningGoal()).contains("答案候选值");

        var mistake = mistakePointRepository.findByCode("MP_BINARY_CHECK_EQUAL_CASE_REJECTED").orElseThrow();
        assertThat(mistake.getSkillUnitCode()).isEqualTo("SK_BINARY_ANSWER_CHECK");
        assertThat(mistake.getPrimaryKnowledgeNodeCode()).isEqualTo("ALGO.BINARY.ANSWER.check_函数");
        assertThat(mistake.getMisconception()).contains("刚好卡边界");
        assertThat(mistake.getKnowledgeNodeCodes()).contains("ALGO.BINARY.ANSWER.check_函数");

        AiStandardLibraryLegacyMapping mapping = legacyMappingRepository
                .findByLegacyLayerAndLegacyCode(AiStandardLibraryLayer.MISTAKE_POINT,
                        "MP_BINARY_CHECK_EQUAL_CASE_REJECTED")
                .orElseThrow();
        assertThat(mapping.getTargetType()).isEqualTo(AiStandardLibraryTargetType.MISTAKE_POINT);
        assertThat(mapping.getTargetCode()).isEqualTo("MP_BINARY_CHECK_EQUAL_CASE_REJECTED");

        var improvement = improvementPointRepository.findByCode("IP_V8_ARRAY_UPDATE_INVARIANT_TESTING").orElseThrow();
        assertThat(improvement.getSkillUnitCode()).isEqualTo("SK_V8_ARRAY_UPDATE_OLD_NEW_CONTRACT");
        assertThat(improvement.getKnowledgeNodeCodes()).contains("BASIC.ARRAY.UPDATE.读旧写新分离");
        assertThat(improvement.getImprovementGoal()).contains("适用于");
        assertThat(improvement.getStudentBenefit()).contains("旧值").contains("累计量");
        assertThat(improvement.getRelatedMistakeCodes())
                .contains("MP_V8_ARRAY_UPDATE_OVERWRITES_SOURCE_BEFORE_READ");

        AiStandardLibraryLegacyMapping improvementMapping = legacyMappingRepository
                .findByLegacyLayerAndLegacyCode(AiStandardLibraryLayer.IMPROVEMENT_POINT,
                        "IP_V8_ARRAY_UPDATE_INVARIANT_TESTING")
                .orElseThrow();
        assertThat(improvementMapping.getTargetType()).isEqualTo(AiStandardLibraryTargetType.IMPROVEMENT_POINT);
        assertThat(improvementMapping.getTargetCode()).isEqualTo("IP_V8_ARRAY_UPDATE_INVARIANT_TESTING");
        assertThat(relationRepository.findBySourceTypeAndSourceCodeAndRelationTypeAndTargetTypeAndTargetCode(
                AiStandardLibraryTargetType.IMPROVEMENT_POINT,
                "IP_V8_ARRAY_UPDATE_INVARIANT_TESTING",
                AiStandardLibraryRelationType.EXTENDS,
                AiStandardLibraryTargetType.SKILL_UNIT,
                "SK_V8_ARRAY_UPDATE_OLD_NEW_CONTRACT")).isPresent();

        normalizedSeeder.run();

        assertThat(skillUnitRepository.count()).isEqualTo(skillCount);
        assertThat(mistakePointRepository.count()).isEqualTo(mistakeCount);
        assertThat(improvementPointRepository.count()).isEqualTo(improvementCount);
        assertThat(legacyMappingRepository.count()).isEqualTo(mappingCount);
        assertThat(relationRepository.count()).isEqualTo(relationCount);
    }

    @Test
    void normalizedMistakePointDoesNotCarryHardCodedEvidencePatternFields() {
        assertThat(Arrays.stream(AiStandardMistakePoint.class.getDeclaredFields())
                .map(Field::getName)
                .toList())
                .doesNotContain("evidenceSignals", "commonCodePatterns", "judgeSignals", "requiredEvidence");
    }

    private void assertPrimaryAnchorsAreKnowledgePoints() {
        Set<String> knowledgePointCodes = InformaticsKnowledgeSeedCatalog.seeds().stream()
                .filter(seed -> seed.type() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .map(InformaticsKnowledgeSeed::code)
                .collect(Collectors.toSet());

        assertThat(skillUnitRepository.findAll().stream().filter(AiStandardSkillUnit::isEnabled))
                .allSatisfy(skill -> assertThat(skill.getPrimaryKnowledgeNodeCode())
                        .as(skill.getCode() + " 的能力点主锚点应指向知识点")
                        .isIn(knowledgePointCodes));
        assertThat(mistakePointRepository.findAll().stream().filter(AiStandardMistakePoint::isEnabled))
                .allSatisfy(mistake -> assertThat(mistake.getPrimaryKnowledgeNodeCode())
                        .as(mistake.getCode() + " 的易错点主锚点应指向知识点")
                        .isIn(knowledgePointCodes));
        assertThat(improvementPointRepository.findAll().stream().filter(AiStandardImprovementPoint::isEnabled))
                .allSatisfy(improvement -> assertThat(improvement.getPrimaryKnowledgeNodeCode())
                        .as(improvement.getCode() + " 的提升点主锚点应指向知识点")
                        .isIn(knowledgePointCodes));
    }
}
