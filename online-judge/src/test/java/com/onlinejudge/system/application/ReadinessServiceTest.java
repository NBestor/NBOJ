package com.onlinejudge.system.application;

import com.onlinejudge.shared.security.SchoolSecurityProperties;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthProperties;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import com.onlinejudge.system.dto.AiSmokeResponse;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import com.onlinejudge.system.dto.ReadinessResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessServiceTest {

    @Test
    void schoolModeBlocksWhenDockerRunnerOrPostgresAreMissing() {
        ReadinessService service = service(
                ExecutorStatusResponse.builder()
                        .mode("local")
                        .dockerAvailable(false)
                        .cpp17Available(false)
                        .message("未检测到 Docker")
                        .build(),
                AiSmokeResponse.builder()
                        .status("UNKNOWN")
                        .message("尚未执行 AI smoke 检查。")
                        .build(),
                true
        );
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:h2:file:./data/onlinejudge");
        ReflectionTestUtils.setField(service, "aiEnabled", false);
        ReflectionTestUtils.setField(service, "aiBlocking", false);

        ReadinessResponse readiness = service.getReadiness();
        Map<String, ReadinessResponse.Check> checks = byId(readiness);

        assertThat(readiness.getStatus()).isEqualTo("BLOCKED");
        assertThat(checks.get("executor-mode").getStatus()).isEqualTo("FAIL");
        assertThat(checks.get("docker-daemon").getStatus()).isEqualTo("FAIL");
        assertThat(checks.get("cpp17-runner").getStatus()).isEqualTo("FAIL");
        assertThat(checks.get("database").getStatus()).isEqualTo("FAIL");
        assertThat(checks.get("database").isBlocking()).isTrue();
    }

    @Test
    void aiRateLimitDegradesOnlyWhenAiGateIsExplicitlyRelaxed() {
        ReadinessService service = readyInfrastructureWithAi("FAILED", "RATE_LIMITED", false);

        ReadinessResponse readiness = service.getReadiness();
        ReadinessResponse.Check ai = byId(readiness).get("ai-smoke");

        assertThat(readiness.getStatus()).isEqualTo("DEGRADED");
        assertThat(ai.getStatus()).isEqualTo("WARN");
        assertThat(ai.isBlocking()).isFalse();
        assertThat(ai.getMessage()).contains("限流");
    }

    @Test
    void aiRateLimitBlocksByDefaultForSchoolAiPilot() {
        ReadinessService service = readyInfrastructureWithAi("FAILED", "RATE_LIMITED", true);
        ReflectionTestUtils.setField(service, "aiBlocking", true);

        ReadinessResponse readiness = service.getReadiness();
        ReadinessResponse.Check ai = byId(readiness).get("ai-smoke");

        assertThat(readiness.getStatus()).isEqualTo("BLOCKED");
        assertThat(ai.getStatus()).isEqualTo("WARN");
        assertThat(ai.isBlocking()).isTrue();
        assertThat(ai.getAction()).contains("修正 key", "额度", "限流");
    }

    @Test
    void aiRateLimitBlocksWhenAiIsRequiredForOpeningClass() {
        ReadinessService service = readyInfrastructureWithAi("FAILED", "RATE_LIMITED", true);

        ReadinessResponse readiness = service.getReadiness();
        ReadinessResponse.Check ai = byId(readiness).get("ai-smoke");

        assertThat(readiness.getStatus()).isEqualTo("BLOCKED");
        assertThat(ai.getStatus()).isEqualTo("WARN");
        assertThat(ai.isBlocking()).isTrue();
    }

    @Test
    void schoolModeBlocksWhenOfficialDatabaseContentIsMissing() {
        ReadinessService service = service(
                ExecutorStatusResponse.builder()
                        .mode("docker")
                        .dockerAvailable(true)
                        .cpp17Available(true)
                        .message("Docker runner ready")
                        .build(),
                AiSmokeResponse.builder()
                        .status("READY")
                        .message("AI smoke 通过。")
                        .build(),
                true,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://postgres:5432/onlinejudge");
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        ReflectionTestUtils.setField(service, "aiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "aiBlocking", false);

        ReadinessResponse readiness = service.getReadiness();
        ReadinessResponse.Check content = byId(readiness).get("database-content");

        assertThat(readiness.getStatus()).isEqualTo("BLOCKED");
        assertThat(content.getStatus()).isEqualTo("FAIL");
        assertThat(content.isBlocking()).isTrue();
        assertThat(content.getAction()).contains("数据库内容迁移", "不要依赖运行时 seed");
    }

    private ReadinessService readyInfrastructureWithAi(String aiStatus, String failureReason, boolean aiBlocking) {
        ReadinessService service = service(
                ExecutorStatusResponse.builder()
                        .mode("docker")
                        .dockerAvailable(true)
                        .cpp17Available(true)
                        .message("Docker runner ready")
                        .build(),
                AiSmokeResponse.builder()
                        .status(aiStatus)
                        .failureReason(failureReason)
                        .message("外部模型请求被限流，建议稍后重试或降低并发。")
                        .build(),
                true
        );
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://postgres:5432/onlinejudge");
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        ReflectionTestUtils.setField(service, "aiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "aiBlocking", aiBlocking);
        return service;
    }

    private ReadinessService service(ExecutorStatusResponse executorStatus,
                                     AiSmokeResponse aiSmoke,
                                     boolean schoolProfile) {
        return service(executorStatus, aiSmoke, schoolProfile, 1, 1, 1, 1, 1, 1, 1);
    }

    private ReadinessService service(ExecutorStatusResponse executorStatus,
                                     AiSmokeResponse aiSmoke,
                                     boolean schoolProfile,
                                     long problems,
                                     long knowledgeNodes,
                                     long skills,
                                     long mistakes,
                                     long improvements,
                                     long testCases,
                                     long reviewedSemanticTestCases) {
        ExecutorStatusService executorStatusService = mock(ExecutorStatusService.class);
        when(executorStatusService.getStatus()).thenReturn(executorStatus);

        AiSmokeService aiSmokeService = mock(AiSmokeService.class);
        when(aiSmokeService.latest()).thenReturn(aiSmoke);

        SchoolSecurityProperties securityProperties = mock(SchoolSecurityProperties.class);
        when(securityProperties.schoolProfile()).thenReturn(schoolProfile);
        when(securityProperties.teacherPasswordConfigured()).thenReturn(true);
        when(securityProperties.teacherSessionSecretConfigured()).thenReturn(true);
        when(securityProperties.studentTokenSecretConfigured()).thenReturn(true);

        ProblemRepository problemRepository = mock(ProblemRepository.class);
        when(problemRepository.count()).thenReturn(problems);
        TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
        when(testCaseRepository.count()).thenReturn(testCases);
        when(testCaseRepository.countBySemanticCodeIsNotNullAndReviewStatus("REVIEWED"))
                .thenReturn(reviewedSemanticTestCases);
        InformaticsKnowledgeNodeRepository knowledgeNodeRepository = mock(InformaticsKnowledgeNodeRepository.class);
        when(knowledgeNodeRepository.countByEnabledTrue()).thenReturn(knowledgeNodes);
        AiStandardSkillUnitRepository skillUnitRepository = mock(AiStandardSkillUnitRepository.class);
        when(skillUnitRepository.countByEnabledTrue()).thenReturn(skills);
        AiStandardMistakePointRepository mistakePointRepository = mock(AiStandardMistakePointRepository.class);
        when(mistakePointRepository.countByEnabledTrue()).thenReturn(mistakes);
        AiStandardImprovementPointRepository improvementPointRepository = mock(AiStandardImprovementPointRepository.class);
        when(improvementPointRepository.countByEnabledTrue()).thenReturn(improvements);

        return new ReadinessService(
                executorStatusService,
                aiSmokeService,
                securityProperties,
                new AiStandardLibraryGrowthProperties(),
                problemRepository,
                testCaseRepository,
                knowledgeNodeRepository,
                skillUnitRepository,
                mistakePointRepository,
                improvementPointRepository
        );
    }

    private Map<String, ReadinessResponse.Check> byId(ReadinessResponse readiness) {
        return readiness.getChecks()
                .stream()
                .collect(Collectors.toMap(ReadinessResponse.Check::getId, Function.identity()));
    }
}
