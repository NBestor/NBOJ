package com.onlinejudge.system.application;

import com.onlinejudge.shared.security.SchoolSecurityProperties;
import com.onlinejudge.learning.standardlibrary.application.AiStandardLibraryGrowthProperties;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardImprovementPointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardMistakePointRepository;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardSkillUnitRepository;
import com.onlinejudge.problem.persistence.ProblemRepository;
import com.onlinejudge.identity.domain.TeacherAccount;
import com.onlinejudge.identity.persistence.TeacherAccountRepository;
import com.onlinejudge.classroom.domain.StudentProfile;
import com.onlinejudge.classroom.persistence.AssignmentRepository;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.classroom.persistence.StudentProfileRepository;
import com.onlinejudge.aiquota.application.AiProviderGateway;
import com.onlinejudge.aiquota.persistence.TeacherAiQuotaRepository;
import com.onlinejudge.aiquota.persistence.SchoolAiQuotaRepository;
import com.onlinejudge.organization.domain.School;
import com.onlinejudge.organization.persistence.SchoolRepository;
import com.onlinejudge.problem.persistence.TestCaseRepository;
import com.onlinejudge.system.dto.AiSmokeResponse;
import com.onlinejudge.system.dto.ExecutorStatusResponse;
import com.onlinejudge.system.dto.ReadinessResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.flywaydb.core.Flyway;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class ReadinessService {

    private final ExecutorStatusService executorStatusService;
    private final AiSmokeService aiSmokeService;
    private final SchoolSecurityProperties securityProperties;
    private final AiStandardLibraryGrowthProperties growthProperties;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final InformaticsKnowledgeNodeRepository knowledgeNodeRepository;
    private final AiStandardSkillUnitRepository skillUnitRepository;
    private final AiStandardMistakePointRepository mistakePointRepository;
    private final AiStandardImprovementPointRepository improvementPointRepository;
    private final TeacherAccountRepository teacherAccountRepository;
    private final ClassGroupRepository classGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherAiQuotaRepository teacherAiQuotaRepository;
    private final AiProviderGateway aiProviderGateway;
    private final Flyway flyway;
    @Autowired(required = false)
    private SchoolRepository schoolRepository;
    @Autowired(required = false)
    private SchoolAiQuotaRepository schoolAiQuotaRepository;

    public ReadinessService(ExecutorStatusService executorStatusService,
                            AiSmokeService aiSmokeService,
                            SchoolSecurityProperties securityProperties,
                            AiStandardLibraryGrowthProperties growthProperties,
                            ProblemRepository problemRepository,
                            InformaticsKnowledgeNodeRepository knowledgeNodeRepository,
                            AiStandardSkillUnitRepository skillUnitRepository,
                            AiStandardMistakePointRepository mistakePointRepository,
                            AiStandardImprovementPointRepository improvementPointRepository) {
        this(executorStatusService, aiSmokeService, securityProperties, growthProperties, problemRepository,
                null, knowledgeNodeRepository, skillUnitRepository, mistakePointRepository, improvementPointRepository,
                null, null, null, null, null, null, null);
    }

    public ReadinessService(ExecutorStatusService executorStatusService,
                            AiSmokeService aiSmokeService,
                            SchoolSecurityProperties securityProperties,
                            AiStandardLibraryGrowthProperties growthProperties,
                            ProblemRepository problemRepository,
                            TestCaseRepository testCaseRepository,
                            InformaticsKnowledgeNodeRepository knowledgeNodeRepository,
                            AiStandardSkillUnitRepository skillUnitRepository,
                            AiStandardMistakePointRepository mistakePointRepository,
                            AiStandardImprovementPointRepository improvementPointRepository) {
        this(executorStatusService, aiSmokeService, securityProperties, growthProperties, problemRepository,
                testCaseRepository, knowledgeNodeRepository, skillUnitRepository, mistakePointRepository,
                improvementPointRepository, null, null, null, null, null, null, null);
    }

    @Autowired
    public ReadinessService(ExecutorStatusService executorStatusService,
                            AiSmokeService aiSmokeService,
                            SchoolSecurityProperties securityProperties,
                            AiStandardLibraryGrowthProperties growthProperties,
                            ProblemRepository problemRepository,
                            TestCaseRepository testCaseRepository,
                            InformaticsKnowledgeNodeRepository knowledgeNodeRepository,
                            AiStandardSkillUnitRepository skillUnitRepository,
                            AiStandardMistakePointRepository mistakePointRepository,
                            AiStandardImprovementPointRepository improvementPointRepository,
                            TeacherAccountRepository teacherAccountRepository,
                            ClassGroupRepository classGroupRepository,
                            AssignmentRepository assignmentRepository,
                            StudentProfileRepository studentProfileRepository,
                            TeacherAiQuotaRepository teacherAiQuotaRepository,
                            AiProviderGateway aiProviderGateway,
                            @Nullable Flyway flyway) {
        this.executorStatusService = executorStatusService;
        this.aiSmokeService = aiSmokeService;
        this.securityProperties = securityProperties;
        this.growthProperties = growthProperties;
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.skillUnitRepository = skillUnitRepository;
        this.mistakePointRepository = mistakePointRepository;
        this.improvementPointRepository = improvementPointRepository;
        this.teacherAccountRepository = teacherAccountRepository;
        this.classGroupRepository = classGroupRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherAiQuotaRepository = teacherAiQuotaRepository;
        this.aiProviderGateway = aiProviderGateway;
        this.flyway = flyway;
    }

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ai.api-key:}")
    private String aiApiKey;

    @Value("${readiness.ai-blocking:${AI_READINESS_BLOCKING:true}}")
    private boolean aiBlocking;

    @Value("${ai.diagnosis-report-v3-enabled:${AI_DIAGNOSIS_REPORT_V3_ENABLED:true}}")
    private boolean diagnosisReportV3Enabled = true;

    public ReadinessResponse getReadiness() {
        List<ReadinessResponse.Check> checks = new ArrayList<>();
        ExecutorStatusResponse executor = executorStatusService.getStatus();

        checks.add(check(
                "deployment-mode",
                "部署模式",
                securityProperties.schoolProfile() ? "PASS" : "WARN",
                false,
                securityProperties.schoolProfile()
                        ? "当前为学校部署模式。"
                        : "当前不是 school 模式，适合开发或演示，不建议直接开课。",
                "学校部署请设置 APP_PROFILE=school。"
        ));

        checks.add(check(
                "executor-mode",
                "判题执行模式",
                "docker".equalsIgnoreCase(executor.getMode()) ? "PASS" : "FAIL",
                true,
                "docker".equalsIgnoreCase(executor.getMode())
                        ? "判题执行模式为 Docker。"
                        : "当前判题执行模式不是 Docker，不能作为正式课堂沙箱。",
                "设置 EXECUTOR_MODE=docker，并通过学校预检。"
        ));

        checks.add(check(
                "docker-daemon",
                "Docker 服务",
                executor.isDockerAvailable() ? "PASS" : "FAIL",
                true,
                executor.isDockerAvailable() ? "Docker daemon 可用。" : "未检测到 Docker daemon。",
                "启动 Docker Desktop、OrbStack 或 Docker Engine。"
        ));

        checks.add(check(
                "cpp17-runner",
                "C++17 runner",
                executor.isCpp17Available() ? "PASS" : "FAIL",
                true,
                executor.isCpp17Available() ? "C++17 runner 可用。" : executor.getMessage(),
                "运行 scripts/build-cpp17-runner.sh 或使用 docker compose 构建。"
        ));

        boolean h2 = datasourceUrl.toLowerCase().contains(":h2:");
        checks.add(check(
                "database",
                "数据库",
                securityProperties.schoolProfile() && h2 ? "FAIL" : h2 ? "WARN" : "PASS",
                securityProperties.schoolProfile() && h2,
                h2 ? "当前使用 H2 文件数据库，仅适合开发或极小范围试点。" : "当前未使用 H2，适合学校部署。",
                "学校模式请使用 Postgres，并配置备份。"
        ));

        checks.add(databaseContentCheck());

        if (flyway != null) {
            checks.add(flywayCheck());
            checks.add(activeAdminCheck());
            checks.add(schoolTenantCheck());
            checks.add(ownershipCheck());
            checks.add(rosterCheck());
            checks.add(publicProblemCheck());
            checks.add(quotaCheck());
        }

        AiSmokeResponse smoke = aiSmokeService.latest();
        boolean aiConfigured = aiEnabled && (aiProviderGateway == null
                ? aiApiKey != null && !aiApiKey.isBlank() : aiProviderGateway.available());
        String aiStatus = !aiEnabled ? "WARN" : !aiConfigured ? "WARN" : "READY".equals(smoke.getStatus()) ? "PASS" : "FAILED".equals(smoke.getStatus()) ? "WARN" : "WARN";
        checks.add(check(
                "ai-smoke",
                "外部 AI",
                aiStatus,
                aiBlocking && !"PASS".equals(aiStatus),
                aiStatusMessage(aiEnabled, aiConfigured, smoke),
                aiAction(aiEnabled && aiConfigured, smoke)
        ));

        checks.add(check(
                "ai-diagnosis-report-v3",
                "AI 诊断报告 v3",
                diagnosisReportV3Enabled ? "PASS" : "FAIL",
                true,
                diagnosisReportV3Enabled
                        ? "诊断报告 v3 已开启。"
                        : "诊断报告 v3 已关闭。",
                "设置 AI_DIAGNOSIS_REPORT_V3_ENABLED=true。"
        ));

        checks.add(check(
                "ai-standard-library-growth",
                "标准库成长 Agent",
                growthProperties.isEnabled() ? growthProperties.isAutoMergeEnabled() ? "WARN" : "PASS" : "WARN",
                false,
                growthProperties.isEnabled()
                        ? growthProperties.isAutoMergeEnabled()
                        ? "成长 Agent 已开启，允许自动入库。"
                        : "成长 Agent 已开启，只写入候选池。"
                        : "成长 Agent 已关闭。",
                "自动入库前请先小范围审核。"
        ));

        String overall = overallStatus(checks);
        return ReadinessResponse.builder()
                .status(overall)
                .updatedAt(LocalDateTime.now())
                .checks(checks)
                .build();
    }

    private String aiStatusMessage(boolean enabled, boolean configured, AiSmokeResponse smoke) {
        if (!enabled) {
            return "AI 已关闭。";
        }
        if (!configured) {
            return "API key 未配置。";
        }
        if ("READY".equals(smoke.getStatus())) {
            return "AI smoke 通过。";
        }
        return smoke.getMessage();
    }

    private String aiAction(boolean enabledAndConfigured, AiSmokeResponse smoke) {
        if (!enabledAndConfigured) {
            return "开启 AI 并配置有效 API key。";
        }
        if (smoke != null && "RATE_LIMITED".equalsIgnoreCase(smoke.getFailureReason())) {
            return "修正 key、检查额度与限流策略；必要时切换可用模型后重新执行 AI smoke。";
        }
        if (smoke != null && "UNAUTHORIZED".equalsIgnoreCase(smoke.getFailureReason())) {
            return "修正 key 或重新生成 ModelScope token 后执行 AI smoke。";
        }
        return "执行 AI smoke；失败时检查 key、额度、限流和网络。";
    }

    private String overallStatus(List<ReadinessResponse.Check> checks) {
        boolean blocked = checks.stream().anyMatch(check -> check.isBlocking() && !"PASS".equals(check.getStatus()));
        if (blocked) {
            return "BLOCKED";
        }
        boolean degraded = checks.stream().anyMatch(check -> !"PASS".equals(check.getStatus()));
        return degraded ? "DEGRADED" : "READY";
    }

    private ReadinessResponse.Check databaseContentCheck() {
        try {
            long problems = problemRepository.count();
            long testCases = testCaseRepository.count();
            long reviewedSemanticTestCases =
                    testCaseRepository.countBySemanticCodeIsNotNullAndReviewStatus("REVIEWED");
            long knowledgeNodes = knowledgeNodeRepository.countByEnabledTrue();
            long skills = skillUnitRepository.countByEnabledTrue();
            long mistakes = mistakePointRepository.countByEnabledTrue();
            long improvements = improvementPointRepository.countByEnabledTrue();
            boolean ready = problems > 0
                    && testCases > 0
                    && reviewedSemanticTestCases == testCases
                    && knowledgeNodes > 0
                    && skills > 0
                    && mistakes > 0
                    && improvements > 0;
            return check(
                    "database-content",
                    "正式内容库",
                    ready ? "PASS" : securityProperties.schoolProfile() ? "FAIL" : "WARN",
                    securityProperties.schoolProfile() && !ready,
                    ready
                            ? "正式数据库已有题库、测试点语义、知识树和 AI 标准库内容。"
                            : "正式数据库内容不完整：problems=%d, testCases=%d, reviewedSemanticTestCases=%d, knowledgeNodes=%d, skills=%d, mistakes=%d, improvements=%d。"
                            .formatted(problems, testCases, reviewedSemanticTestCases,
                                    knowledgeNodes, skills, mistakes, improvements),
                    ready
                            ? "继续使用数据库作为正式内容主库。"
                            : "先执行数据库内容迁移和验证，不要依赖运行时 seed 自动补齐。"
            );
        } catch (RuntimeException ex) {
            return check(
                    "database-content",
                    "正式内容库",
                    securityProperties.schoolProfile() ? "FAIL" : "WARN",
                    securityProperties.schoolProfile(),
                    "无法读取正式内容表：" + ex.getMessage(),
                    "检查数据库连接、表结构迁移和内容迁移状态。"
            );
        }
    }

    private ReadinessResponse.Check flywayCheck() {
        try {
            var current = flyway.info().current();
            String version = current == null ? "none" : current.getVersion().getVersion();
            boolean ready = current != null && current.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("8")) >= 0;
            return check("flyway-version", "数据库迁移版本", ready ? "PASS" : "FAIL", true,
                    "当前 Flyway 版本：" + version + "。", "部署前必须完成 V1-V8 迁移并验证校验和。");
        } catch (RuntimeException failure) {
            return check("flyway-version", "数据库迁移版本", "FAIL", true,
                    "无法读取 Flyway 版本：" + failure.getMessage(), "检查迁移历史表和数据库权限。");
        }
    }

    private ReadinessResponse.Check activeAdminCheck() {
        long count = teacherAccountRepository.countByRoleAndStatus(TeacherAccount.Role.PLATFORM_ADMIN, TeacherAccount.Status.ACTIVE);
        return check("active-admin", "有效平台管理员", count > 0 ? "PASS" : "FAIL", true,
                "当前有效管理员数量：" + count + "。", "使用 BOOTSTRAP_ADMIN_* 环境变量完成首次管理员激活。");
    }

    private ReadinessResponse.Check schoolTenantCheck() {
        if (schoolRepository == null || schoolAiQuotaRepository == null) {
            return check("school-tenants", "学校租户", "FAIL", true, "学校租户组件未加载。", "检查 V6-V8 与学校额度组件。");
        }
        long invalid = schoolRepository.findAll().stream().filter(school ->
                teacherAccountRepository.findById(school.getAdminAccountId())
                        .filter(admin -> admin.getRole() == TeacherAccount.Role.SCHOOL_ADMIN)
                        .filter(admin -> school.getId().equals(admin.getSchoolId()))
                        .filter(admin -> admin.getStatus() == TeacherAccount.Status.ACTIVE || school.getStatus() == School.Status.SUSPENDED)
                        .isEmpty()).count();
        try { schoolAiQuotaRepository.count(); }
        catch (RuntimeException failure) { invalid++; }
        return check("school-tenants", "学校租户与额度池", invalid == 0 ? "PASS" : "FAIL", true,
                "无效学校管理员或额度结构数量：" + invalid + "。", "修复学校管理员关联并确认 school_ai_quotas 可用。");
    }

    private ReadinessResponse.Check ownershipCheck() {
        Set<java.util.UUID> teacherIds = teacherAccountRepository.findAll().stream()
                .map(TeacherAccount::getId).collect(java.util.stream.Collectors.toSet());
        var classes = classGroupRepository.findAll();
        Set<Long> classIds = classes.stream().map(item -> item.getId()).collect(java.util.stream.Collectors.toSet());
        long orphanClasses = classes.stream().filter(item -> item.getOwnerTeacherId() == null || !teacherIds.contains(item.getOwnerTeacherId())).count();
        long orphanAssignments = assignmentRepository.findAll().stream().filter(item -> item.getOwnerTeacherId() == null
                || !teacherIds.contains(item.getOwnerTeacherId()) || item.getClassGroupId() == null || !classIds.contains(item.getClassGroupId())).count();
        long orphanStudents = studentProfileRepository.findAll().stream()
                .filter(item -> item.getClassGroupId() == null || !classIds.contains(item.getClassGroupId())).count();
        long total = orphanClasses + orphanAssignments + orphanStudents;
        return check("orphan-data", "孤立教学数据", total == 0 ? "PASS" : "FAIL", true,
                "孤立班级=%d，孤立作业=%d，孤立学生=%d。".formatted(orphanClasses, orphanAssignments, orphanStudents),
                "推广前将孤立数据转交给有效教师并重新运行检查。");
    }

    private ReadinessResponse.Check rosterCheck() {
        long needsReview = studentProfileRepository.findAll().stream()
                .filter(item -> item.getStatus() == StudentProfile.RosterStatus.NEEDS_REVIEW
                        || item.getStudentNo() == null || item.getStudentNo().isBlank()).count();
        Set<String> seen = new HashSet<>();
        long duplicates = studentProfileRepository.findAll().stream()
                .filter(item -> item.getClassGroupId() != null && item.getStudentNo() != null && !item.getStudentNo().isBlank())
                .map(item -> item.getClassGroupId() + "|" + item.getStudentNo().trim().toLowerCase(Locale.ROOT))
                .filter(key -> !seen.add(key)).count();
        boolean ready = needsReview == 0 && duplicates == 0;
        return check("roster-anomalies", "名单异常", ready ? "PASS" : "WARN", false,
                "待修复名单=%d，重复学号=%d。".formatted(needsReview, duplicates),
                "修复学号和重复身份后再允许对应学生登录。");
    }

    private ReadinessResponse.Check publicProblemCheck() {
        long count = problemRepository.findCatalogItems().size();
        return check("public-problems", "公共免费题", count > 0 ? "PASS" : "FAIL", true,
                "当前公共已发布题目数量：" + count + "。", "确认现有正式题目已经迁移为 PUBLIC/PUBLISHED。");
    }

    private ReadinessResponse.Check quotaCheck() {
        boolean tableReady;
        try {
            teacherAiQuotaRepository.count();
            tableReady = true;
        } catch (RuntimeException ignored) {
            tableReady = false;
        }
        boolean providerReady = !aiEnabled || aiProviderGateway.available();
        return check("ai-quota-system", "AI Provider 与额度", tableReady && providerReady ? "PASS" : "WARN",
                aiBlocking && (!tableReady || !providerReady),
                "额度表%s，AI Provider%s。".formatted(tableReady ? "可用" : "不可用", providerReady ? "可用" : "未配置"),
                "检查 V5 迁移与平台统一模型密钥；不要向教师发放供应商密钥。");
    }

    private ReadinessResponse.Check check(String id,
                                          String label,
                                          String status,
                                          boolean blocking,
                                          String message,
                                          String action) {
        return ReadinessResponse.Check.builder()
                .id(id)
                .label(label)
                .status(status)
                .blocking(blocking)
                .message(message)
                .action(action)
                .build();
    }
}
