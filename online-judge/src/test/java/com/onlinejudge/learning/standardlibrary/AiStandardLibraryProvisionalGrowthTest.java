package com.onlinejudge.learning.standardlibrary;

import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthAgentService;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryService;
import com.onlinejudge.learning.standardlibrary.application.StandardLibraryGrowthProposal;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryGrowthCandidateStatus;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardLibraryLayer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-standard-library-provisional-growth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false",
        "app.content-migration.enabled=true",
        "ai.standard-library-growth.auto-merge-enabled=true",
        "ai.standard-library-growth.auto-merge-min-confidence=0.90",
        "ai.standard-library-growth.auto-merge-min-occurrences=2"
})
class AiStandardLibraryProvisionalGrowthTest {

    private static final String PARENT = "BASIC.LOOP.BOUNDARY.左闭右开";
    private static final String CODE = "MP_PROVISIONAL_AUTO_PROMOTION_TEST";

    @Autowired
    AiStandardLibraryGrowthAgentService growthService;

    @Autowired
    AiStandardLibraryService standardLibraryService;

    @Test
    void provisionalCandidatePromotesOnlyAfterIndependentSubmissionEvidence() {
        var first = growthService.propose(proposal(101L, "code:line:3"));

        assertThat(first.getStatus()).isEqualTo(AiStandardLibraryGrowthCandidateStatus.PROPOSED);
        assertThat(first.getParentKnowledgeNodeCode()).isEqualTo(PARENT);
        assertThat(first.getOccurrenceCount()).isEqualTo(1);
        assertThat(first.getObservedSubmissionIds()).containsExactly(101L);
        assertThat(standardLibraryService.expandDiagnosticLayer(PARENT).getProvisionalCandidates())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.getCode()).isEqualTo(CODE);
                    assertThat(candidate.getLayer()).isEqualTo("MISTAKE_POINT");
                    assertThat(candidate.getParentKnowledgeNodeCode()).isEqualTo(PARENT);
                    assertThat(candidate.isProvisional()).isTrue();
                });

        var repeatedSameSubmission = growthService.propose(proposal(101L, "judge:first_failed_case"));

        assertThat(repeatedSameSubmission.getId()).isEqualTo(first.getId());
        assertThat(repeatedSameSubmission.getOccurrenceCount()).isEqualTo(1);
        assertThat(repeatedSameSubmission.getObservedSubmissionIds()).containsExactly(101L);
        assertThat(repeatedSameSubmission.getStatus()).isEqualTo(AiStandardLibraryGrowthCandidateStatus.PROPOSED);
        assertThat(repeatedSameSubmission.getEvidenceRefs())
                .contains("code:line:3", "judge:first_failed_case");

        var second = growthService.propose(proposal(102L, "code:line:5"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getOccurrenceCount()).isEqualTo(2);
        assertThat(second.getObservedSubmissionIds()).containsExactly(101L, 102L);
        assertThat(second.getStatus()).isEqualTo(AiStandardLibraryGrowthCandidateStatus.MERGED);
        assertThat(standardLibraryService.formalItemExists(AiStandardLibraryLayer.MISTAKE_POINT, CODE)).isTrue();
        assertThat(standardLibraryService.expandDiagnosticLayer(PARENT).getProvisionalCandidates())
                .noneMatch(candidate -> CODE.equals(candidate.getCode()));
    }

    @Test
    void candidateWithoutSubmissionSourceCannotAutoPromote() {
        String code = "MP_PROVISIONAL_WITHOUT_SOURCE";
        var first = growthService.propose(proposal(code, null, "code:line:7"));
        var repeated = growthService.propose(proposal(code, null, "judge:first_failed_case"));

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(repeated.getObservedSubmissionIds()).isEmpty();
        assertThat(repeated.getOccurrenceCount()).isEqualTo(1);
        assertThat(repeated.getStatus()).isNotEqualTo(AiStandardLibraryGrowthCandidateStatus.MERGED);
        assertThat(standardLibraryService.formalItemExists(AiStandardLibraryLayer.MISTAKE_POINT, code)).isFalse();
    }

    private StandardLibraryGrowthProposal proposal(Long submissionId, String evidenceRef) {
        return proposal(CODE, submissionId, evidenceRef);
    }

    private StandardLibraryGrowthProposal proposal(String code, Long submissionId, String evidenceRef) {
        return StandardLibraryGrowthProposal.builder()
                .suggestedCode(code)
                .suggestedName("半开区间误当闭区间")
                .layer(AiStandardLibraryLayer.MISTAKE_POINT)
                .suggestedPath(List.of("信息学基础", "循环结构", "循环边界", "左闭右开", "半开区间误当闭区间"))
                .parentKnowledgeNodeCode(PARENT)
                .sourceProblemId(9L)
                .sourceSubmissionId(submissionId)
                .changeReason("同一细颗粒错因在不同提交中反复出现。")
                .evidenceRefs(List.of(evidenceRef))
                .evidenceStatus("SUPPORTED")
                .confidence(0.96)
                .build();
    }
}
