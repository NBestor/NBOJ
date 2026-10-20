package com.onlinejudge.classroom.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.domain.AiStandardSkillUnit;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignmentReadinessServiceTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final InformaticsKnowledgeNodeRepository knowledgeNodeRepository =
            mock(InformaticsKnowledgeNodeRepository.class);
    private final AiStandardSkillUnitRepository skillUnitRepository =
            mock(AiStandardSkillUnitRepository.class);
    private final AssignmentReadinessService service = new AssignmentReadinessService(
            problemRepository,
            testCaseRepository,
            knowledgeNodeRepository,
            skillUnitRepository
    );

    @Test
    void blocksActivePublishWhenProblemHasNoTestCasesButDraftCanStillInspect() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem(1L, "空测试题")));
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        var readiness = service.inspect(List.of(1L));

        assertThat(readiness.isPublishable()).isFalse();
        assertThat(readiness.getBlockerCount()).isEqualTo(1);
        assertThat(readiness.getProblems().get(0).getBlockers())
                .extracting("code")
                .containsExactly("NO_TEST_CASES");
        assertThatThrownBy(() -> service.requirePublishable(List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发布前质检未通过");
    }

    @Test
    void acceptsReviewedSemanticPathAndReturnsNonBlockingStarterWarning() {
        when(problemRepository.findById(2L)).thenReturn(Optional.of(problem(2L, "边界练习")));
        when(testCaseRepository.findByProblemIdOrderByOrderIndexAsc(2L)).thenReturn(List.of(
                reviewedCase(2L, 0, "TC_BOUNDARY", "BOUNDARY"),
                reviewedCase(2L, 1, "TC_STRUCTURAL", "STRUCTURAL")
        ));
        when(knowledgeNodeRepository.findByCode("BASIC.LOOP.BOUNDARY"))
                .thenReturn(Optional.of(InformaticsKnowledgeNode.builder()
                        .code("BASIC.LOOP.BOUNDARY")
                        .enabled(true)
                        .build()));
        when(skillUnitRepository.findByCode("SK_LOOP_BOUNDARY"))
                .thenReturn(Optional.of(AiStandardSkillUnit.builder()
                        .code("SK_LOOP_BOUNDARY")
                        .primaryKnowledgeNodeCode("BASIC.LOOP.BOUNDARY")
                        .enabled(true)
                        .build()));

        var readiness = service.inspect(List.of(2L));

        assertThat(readiness.isPublishable()).isTrue();
        assertThat(readiness.getBlockerCount()).isZero();
        assertThat(readiness.getWarningCount()).isEqualTo(1);
        assertThat(readiness.getProblems().get(0).getWarnings())
                .extracting("code")
                .containsExactly("STARTER_CODE_MISSING");
    }

    private Problem problem(Long id, String title) {
        return Problem.builder()
                .id(id)
                .title(title)
                .description("用于质检测试")
                .difficulty(Problem.Difficulty.EASY)
                .timeLimit(1000)
                .memoryLimit(65536)
                .build();
    }

    private TestCase reviewedCase(Long problemId, int order, String code, String intent) {
        return TestCase.builder()
                .problemId(problemId)
                .input("1")
                .expectedOutput("1")
                .isHidden(false)
                .orderIndex(order)
                .semanticCode(code)
                .intentType(intent)
                .intentTitle("测试意图")
                .intentSummary("描述本测试点覆盖的泛化行为。")
                .learningObjective("学生能解释边界行为并完成验证。")
                .contestRole("CORRECTNESS_GUARD")
                .revealPolicy("PUBLIC_EXAMPLE")
                .knowledgeNodeCode("BASIC.LOOP.BOUNDARY")
                .skillUnitCode("SK_LOOP_BOUNDARY")
                .reviewStatus("REVIEWED")
                .sourceReference("developer-fixture")
                .libraryVersion("test-semantic-v1")
                .reviewedAt(LocalDateTime.of(2026, 7, 23, 10, 0))
                .build();
    }
}
