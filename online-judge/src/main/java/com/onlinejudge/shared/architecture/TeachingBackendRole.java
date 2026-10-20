package com.onlinejudge.shared.architecture;

public enum TeachingBackendRole {
    KEEP_MAIN("keep-main", "直接服务学生下一步动作、教师课堂判断或判题稳定性"),
    SIMPLIFY("simplify", "有教学价值但职责过大或表达过重，需要继续收敛"),
    INTERNAL_ONLY("internal-only", "服务 AI、评测、运行诊断、研发排障或系统详情，不进入主体验"),
    REMOVE_LATER("remove-later", "历史兼容或旧链路遗留，确认依赖后删除或归档");

    private final String code;
    private final String description;

    TeachingBackendRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public boolean canDriveStudentOrTeacherMainFlow() {
        return this == KEEP_MAIN || this == SIMPLIFY;
    }
}
