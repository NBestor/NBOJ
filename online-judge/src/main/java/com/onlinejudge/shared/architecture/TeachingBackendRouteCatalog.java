package com.onlinejudge.shared.architecture;

import java.util.List;

public final class TeachingBackendRouteCatalog {

    private TeachingBackendRouteCatalog() {
    }

    public static List<RouteEntry> routes() {
        return List.of(
                route("POST", "/api/code-runs", TeachingBackendRole.KEEP_MAIN,
                        "学生使用自定义输入临时运行代码，不产生提交或学习证据"),
                route("POST", "/api/submissions", TeachingBackendRole.KEEP_MAIN,
                        "学生提交代码，启动判题事实链路"),
                route("GET", "/api/submissions/{id}", TeachingBackendRole.KEEP_MAIN,
                        "读取提交和测试点事实"),
                route("GET", "/api/submissions/{id}/student-ai-feedback", TeachingBackendRole.KEEP_MAIN,
                        "学生查看模型结构化修正和提升建议"),
                route("POST", "/api/submissions/{id}/student-ai-feedback/view", TeachingBackendRole.KEEP_MAIN,
                        "记录学生查看反馈，服务学习轨迹"),
                route("GET", "/api/submissions/{id}/analysis", TeachingBackendRole.SIMPLIFY,
                        "旧诊断兼容层，不再驱动学生主体验"),
                route("GET", "/api/teacher/assignments/{assignmentId}/overview", TeachingBackendRole.KEEP_MAIN,
                        "教师课堂判断主摘要"),
                route("GET", "/api/student/profile/{studentProfileId}/assignments", TeachingBackendRole.KEEP_MAIN,
                        "学生登录后读取作业"),
                route("POST", "/api/student/login", TeachingBackendRole.KEEP_MAIN,
                        "学生身份入口"),
                route("GET", "/api/student/assignments/{assignmentId}/profile/{studentProfileId}/trajectory", TeachingBackendRole.SIMPLIFY,
                        "学习轨迹摘要，后续继续降噪"),
                route("GET", "/api/teacher/assignments/{assignmentId}/student-ai-feedback-observability",
                        TeachingBackendRole.INTERNAL_ONLY,
                        "学生 AI 反馈可用性观测，只用于系统详情"),
                route("GET", "/api/teacher/assignments/{assignmentId}/ai-quality", TeachingBackendRole.INTERNAL_ONLY,
                        "AI 质量和运行证据，只用于系统详情或研发判断"),
                route("GET", "/api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates",
                        TeachingBackendRole.INTERNAL_ONLY,
                        "诊断评测样本候选，只用于质量闭环"),
                route("GET", "/api/teacher/assignments/{assignmentId}/diagnosis-eval-fixture-draft",
                        TeachingBackendRole.INTERNAL_ONLY,
                        "诊断评测草稿，只用于质量闭环")
        );
    }

    private static RouteEntry route(String method, String path, TeachingBackendRole role, String reason) {
        return new RouteEntry(method, path, role, reason);
    }

    public record RouteEntry(String method, String path, TeachingBackendRole role, String reason) {
    }
}
