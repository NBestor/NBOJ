package com.onlinejudge.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendForwardController {

    @GetMapping({
            OnlineJudgeWebPaths.PUBLIC_PREFIX,
            OnlineJudgeWebPaths.PUBLIC_PATH,
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/login",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/{assignmentId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/{assignmentId:[0-9]+}/ranking",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/{assignmentId:[0-9]+}/submissions",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/{assignmentId:[0-9]+}/problems/{problemId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/public",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/student/assignments/public/problems/{problemId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/classes",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/classes/{classId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/classes/{classId:[0-9]+}/assignments/{assignmentId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/classes/{classId:[0-9]+}/assignments/{assignmentId:[0-9]+}/problems/{problemId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/classes/{classId:[0-9]+}/assignments/{assignmentId:[0-9]+}/problems/{problemId:[0-9]+}/students/{studentProfileId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/manage",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/manage/classes",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/manage/problems",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/manage/ai-library",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/manage/system",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/login",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/assignment/new",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/assignment/{assignmentId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/assignment/{assignmentId:[0-9]+}/problems/{problemId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher/assignment/{assignmentId:[0-9]+}/problems/{problemId:[0-9]+}/students/{studentProfileId:[0-9]+}",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/teacher-management",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/task-editor",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/class-overview",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/platform-admin",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/platform-admin/**",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/school-admin",
            OnlineJudgeWebPaths.PUBLIC_PREFIX + "/school-admin/**"
    })
    public String forwardToApp() {
        return "forward:" + OnlineJudgeWebPaths.PUBLIC_PREFIX + "/index.html";
    }
}
