package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.dto.AssignmentReadinessResponse;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.domain.Problem;
import com.onlinejudge.problem.domain.TestCase;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssignmentReadinessService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final InformaticsKnowledgeNodeRepository knowledgeNodeRepository;
    private final AiStandardSkillUnitRepository skillUnitRepository;

    @Transactional(readOnly = true)
    public AssignmentReadinessResponse inspect(List<Long> problemIds) {
        List<Long> ids = problemIds == null ? List.of() : problemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<AssignmentReadinessResponse.ProblemReadiness> results = ids.stream()
                .map(this::inspectProblem)
                .toList();
        int blockerCount = results.stream().mapToInt(item -> item.getBlockers().size()).sum();
        int warningCount = results.stream().mapToInt(item -> item.getWarnings().size()).sum();
        return AssignmentReadinessResponse.builder()
                .publishable(!ids.isEmpty() && blockerCount == 0)
                .blockerCount(blockerCount)
                .warningCount(warningCount)
                .problems(results)
                .build();
    }

    public void requirePublishable(List<Long> problemIds) {
        AssignmentReadinessResponse readiness = inspect(problemIds);
        if (!readiness.isPublishable()) {
            String details = readiness.getProblems().stream()
                    .flatMap(problem -> problem.getBlockers().stream()
                            .map(issue -> problem.getProblemTitle() + "：" + issue.getMessage()))
                    .limit(4)
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("所选题目尚未达到发布条件");
            throw new IllegalArgumentException("作业发布前质检未通过：" + details);
        }
    }

    private AssignmentReadinessResponse.ProblemReadiness inspectProblem(Long problemId) {
        Problem problem = problemRepository.findById(problemId).orElse(null);
        List<AssignmentReadinessResponse.Issue> blockers = new ArrayList<>();
        List<AssignmentReadinessResponse.Issue> warnings = new ArrayList<>();
        if (problem == null) {
            blockers.add(issue("PROBLEM_NOT_FOUND", "BLOCKER", "题目不存在或已被删除"));
            return result(problemId, "题目 #" + problemId, 0, 0, blockers, warnings);
        }

        List<TestCase> cases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId);
        if (cases.isEmpty()) {
            blockers.add(issue("NO_TEST_CASES", "BLOCKER", "缺少测试用例，无法可靠判题"));
        }
        Set<String> semanticCodes = new LinkedHashSet<>();
        Set<String> intentTypes = new LinkedHashSet<>();
        int reviewedCount = 0;
        for (TestCase testCase : cases) {
            if (!completeReviewedSemantic(testCase)) {
                blockers.add(issue(
                        "SEMANTIC_PROFILE_INCOMPLETE",
                        "BLOCKER",
                        "测试点 " + displayOrder(testCase) + " 缺少完整且已审核的语义档案"
                ));
                continue;
            }
            reviewedCount++;
            if (!semanticCodes.add(testCase.getSemanticCode().trim())) {
                blockers.add(issue("SEMANTIC_CODE_DUPLICATE", "BLOCKER", "测试点语义 code 重复"));
            }
            intentTypes.add(testCase.getIntentType().trim());
            boolean knowledgeValid = knowledgeNodeRepository.findByCode(testCase.getKnowledgeNodeCode())
                    .map(node -> node.isEnabled())
                    .orElse(false);
            boolean skillValid = skillUnitRepository.findByCode(testCase.getSkillUnitCode())
                    .map(skill -> skill.isEnabled()
                            && Objects.equals(skill.getPrimaryKnowledgeNodeCode(), testCase.getKnowledgeNodeCode()))
                    .orElse(false);
            if (!knowledgeValid || !skillValid) {
                blockers.add(issue(
                        "STANDARD_LIBRARY_PATH_INVALID",
                        "BLOCKER",
                        "测试点 " + displayOrder(testCase) + " 的知识点与能力路径无效或不一致"
                ));
            }
            String expectedReveal = Boolean.TRUE.equals(testCase.getIsHidden())
                    ? "AI_GENERALIZED"
                    : "PUBLIC_EXAMPLE";
            if (!expectedReveal.equals(testCase.getRevealPolicy())) {
                blockers.add(issue(
                        "REVEAL_POLICY_MISMATCH",
                        "BLOCKER",
                        "测试点 " + displayOrder(testCase) + " 的隐藏属性与揭示策略冲突"
                ));
            }
        }
        if (cases.size() >= 2 && intentTypes.size() < 2) {
            warnings.add(issue("LOW_INTENT_DIVERSITY", "WARNING", "多个测试点只覆盖一种评测意图"));
        }
        if (isBlank(problem.getStarterCode())) {
            warnings.add(issue("STARTER_CODE_MISSING", "WARNING", "未提供起步代码，初学者进入任务时缺少结构支架"));
        }
        return result(problemId, problem.getTitle(), cases.size(), reviewedCount, blockers, warnings);
    }

    private AssignmentReadinessResponse.ProblemReadiness result(
            Long problemId,
            String title,
            int testCaseCount,
            int reviewedCount,
            List<AssignmentReadinessResponse.Issue> blockers,
            List<AssignmentReadinessResponse.Issue> warnings
    ) {
        return AssignmentReadinessResponse.ProblemReadiness.builder()
                .problemId(problemId)
                .problemTitle(title)
                .ready(blockers.isEmpty())
                .testCaseCount(testCaseCount)
                .reviewedSemanticCount(reviewedCount)
                .blockers(List.copyOf(blockers))
                .warnings(List.copyOf(warnings))
                .build();
    }

    private boolean completeReviewedSemantic(TestCase testCase) {
        return testCase != null
                && !isBlank(testCase.getSemanticCode())
                && !isBlank(testCase.getIntentType())
                && !isBlank(testCase.getIntentTitle())
                && !isBlank(testCase.getIntentSummary())
                && !isBlank(testCase.getLearningObjective())
                && !isBlank(testCase.getContestRole())
                && !isBlank(testCase.getRevealPolicy())
                && !isBlank(testCase.getKnowledgeNodeCode())
                && !isBlank(testCase.getSkillUnitCode())
                && "REVIEWED".equals(testCase.getReviewStatus())
                && testCase.getReviewedAt() != null;
    }

    private int displayOrder(TestCase testCase) {
        return (testCase.getOrderIndex() == null ? 0 : testCase.getOrderIndex()) + 1;
    }

    private AssignmentReadinessResponse.Issue issue(String code, String severity, String message) {
        return AssignmentReadinessResponse.Issue.builder()
                .code(code)
                .severity(severity)
                .message(message)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
