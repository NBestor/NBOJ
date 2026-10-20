import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from "react";
import enLocale from "../../public/locales/en/translation.json";
import zhLocale from "../../public/locales/zh/translation.json";

type Locale = "zh" | "en";
type TranslationValue = string | { [key: string]: TranslationValue };
type TranslationDictionary = Record<string, TranslationValue>;

const STORAGE_KEY = "wzai:locale";

const localeFiles: Record<Locale, TranslationDictionary> = {
  zh: zhLocale as TranslationDictionary,
  en: enLocale as TranslationDictionary
};

const dictionaries: Record<Locale, TranslationDictionary> = {
  zh: {
    common: {
      language: "语言",
      chinese: "中文",
      english: "English",
      appName: "温中编程学习平台",
      mainNavigation: "主导航",
      openNavigation: "展开导航",
      closeNavigation: "收起导航",
      skipToMain: "跳到主要内容",
      login: "登录",
      logout: "退出",
      studentSide: "学生端",
      teacherSide: "教师端",
      teacherWorkbench: "教师工作台",
      assignmentCenter: "作业中心",
      resourceManagement: "资源管理",
      loadingPage: "正在加载页面",
      theme: {
        toggle: "切换主题",
        toLight: "切换到白天模式",
        toDark: "切换到夜间模式",
        light: "白天",
        dark: "夜间"
      }
    },
    feedbackMeta: {
      knowledgePath: "知识路径",
      knowledgePathAria: "建议对应的知识路径",
      noKnowledgePath: "标准库暂未找到可归属的知识路径。",
      codeEvidence: "代码证据",
      codeEvidenceAria: "建议对应的代码证据",
      evidenceBasis: "证据依据",
      jumpToCode: "回到编辑器并高亮对应代码行",
      viewCode: "查看对应代码位置",
      line: "第 {{line}} 行",
      lineRange: "第 {{start}}-{{end}} 行",
      noJumpableLine: "{{evidence}}，暂无可跳转代码行。",
      noCodeEvidence: "本条建议暂无可定位代码证据。",
      listSeparator: "、",
      pathStatus: {
        formal: "正式标准库",
        provisional: "临时知识点",
        inferred: "历史推断",
        unclassified: "暂未归类"
      },
      evidenceKinds: {
        judge: "评测结果",
        problem: "题面信息",
        verdict: "运行结果",
        source: "提交代码",
        analysis: "分析证据"
      }
    },
    problemHistory: {
      title: "最近提交",
      aria: "本题历史提交",
      submission: "提交 #{{id}}",
      submittedCode: "本次提交代码",
      loadFailed: "历史提交加载失败。",
      feedback: {
        ready: "AI 建议已生成",
        generating: "AI 建议生成中",
        failed: "AI 建议未完成",
        pending: "等待 AI 建议"
      }
    },
    routeHub: {
      eyebrow: "入口",
      title: "学习平台",
      description: "学生学习和教师工作台各走自己的路径，入口清楚，任务直接。",
      studentTitle: "学生学习",
      studentDescription: "进入课堂作业、公共题库和个人提交记录。",
      teacherTitle: "教师工作台",
      teacherDescription: "查看班级、作业、题目和学生，也管理名单、题库与 AI 标准库。",
      welcome: "欢迎回来",
      headlineStart: "从一道题开始，",
      headlineEnd: "看见真正的进步",
      summary: "学生获得即时反馈，教师掌握真实学情。",
      roleAria: "选择使用身份",
      studentCta: "我是学生",
      teacherCta: "我是教师",
      schoolAdminCta: "学校管理员",
      preview: {
        badge: "界面截图",
        alt: "学生端编程与评测界面预览截图，展示两数之和代码和全部通过的评测结果"
      },
      features: {
        languagesTitle: "支持多语言编程练习",
        languagesDetail: "Python / C++ / Java / C",
        feedbackTitle: "即时评测与详细反馈",
        feedbackDetail: "自动化评测，定位问题",
        dataTitle: "学习数据可视化",
        dataDetail: "进步看得见，教学更精准"
      },
      demo: {
        aria: "编程练习与即时评测示例",
        back: "返回题目",
        problem: "两数之和",
        difficulty: "简单",
        problemId: "题目 ID: 1001",
        submit: "提交代码",
        codeAria: "Python 两数之和示例代码",
        console: "控制台",
        caseOne: "测试用例 1 通过：输出 [0, 1]",
        caseTwo: "测试用例 2 通过：输出 [1, 2]",
        result: "评测结果",
        quality: "代码质量",
        passed: "全部通过",
        testCount: "通过 12 / 12 个测试用例",
        runtime: "运行时间",
        scoreTitle: "得分详情",
        correctness: "功能正确性",
        efficiency: "代码效率",
        style: "代码规范",
        feedbackTitle: "太棒了！所有测试均已通过。",
        feedbackDetail: "时间和空间效率都很优秀，继续保持。"
      },
      loop: {
        title: "学习闭环：练习 → 评测 → 复盘",
        practice: "练习",
        practiceDetail: "选择题目，编写并提交代码",
        judge: "评测",
        judgeDetail: "自动评测，获得即时反馈",
        judgeEvidence: "全部通过 12 / 12",
        review: "复盘",
        reviewDetail: "查看解析与建议，巩固提升",
        reviewEvidence: "时间复杂度"
      }
    },
    portals: {
      checking: "正在核验账号",
      reconnecting: "正在恢复连接",
      temporarilyUnavailable: "暂时无法核验登录状态",
      reconnectHint: "不会清除已有登录信息。请确认本地服务已启动，然后重试。",
      retry: "重新连接",
      secureWorkspace: "独立安全工作台",
      loginFailed: "登录失败",
      loadFailed: "数据加载失败",
      actionFailed: "操作未完成",
      registerFailed: "注册申请未提交",
      registrationPending: "申请已提交，请等待本校管理员审核。",
      passwordChanged: "密码已修改，请重新登录。",
      passwordFailed: "密码修改失败",
      changePassword: "首次登录请修改密码",
      changeHint: "临时密码只使用一次，新密码至少 10 位并包含字母和数字。",
      roleMismatch: "当前已登录账号不属于这个工作台，请先退出。",
      oneTimeSecret: "以下信息仅显示一次，请立即安全保存。",
      temporaryPassword: "临时密码",
      schoolCode: "学校注册码",
      quotaLine: "总额度 {{total}} · 已分配 {{allocated}} · 已消耗 {{used}}",
      teacher: { eyebrow: "教师入口", title: "教师工作台登录", description: "使用学校注册码申请账号，审核通过后管理自己的教学数据。" },
      platform: {
        eyebrow: "平台管理员", title: "平台管理登录", description: "仅用于学校、学校管理员、学校额度池及全平台题目治理。",
        workspaceTitle: "平台管理工作台", workspaceDescription: "管理学校边界与平台内容，不进入普通教师和学生明细。",
        createSchool: "创建学校与校管", schoolGovernance: "学校治理", schools: "学校列表", schoolSummary: "学校级汇总",
        problemReviews: "共建与公共题审核", contentGovernance: "全平台内容治理", noSchools: "还没有学校", noReviews: "暂无待审题目", created: "学校已创建"
      },
      school: {
        eyebrow: "学校管理员", title: "学校管理登录", description: "审核本校教师、分配学校额度并只读查看校内教学数据。",
        workspaceTitle: "学校管理工作台", workspaceDescription: "教师账号与额度由学校自主管理；教学内容仅可查看和导出。",
        overview: "学校概览", quotaPool: "本月 AI 额度池", applications: "教师申请", pending: "项待审核", noApplications: "暂无待审申请",
        teachers: "本校教师", quotaAllocation: "教师额度分配", noTeachers: "暂无教师", teachingData: "校内教学数据", readOnly: "只读",
        students: "名学生", assignmentsCount: "份作业", noTeachingData: "暂无教学数据", teacherUsage: "已分配 {{allocated}} · 已消耗 {{used}} · 已预留 {{reserved}}",
        studentsTitle: "学生名单", assignmentsTitle: "作业", submissionsTitle: "提交明细", noStudents: "暂无学生", noAssignments: "暂无作业", noSubmissions: "暂无提交"
      },
      fields: {
        schoolName: "学校名称", adminUsername: "校管用户名", adminDisplayName: "校管姓名", monthlyQuota: "学校月额度",
        totalQuota: "总额度", allocatedQuota: "已分配", usedQuota: "已消耗", availableQuota: "可分配", teacherQuota: "教师本月额度", replacementUsername: "新校管用户名", replacementDisplayName: "新校管姓名"
      },
      actions: {
        create: "创建学校", refresh: "刷新", resetPassword: "重置密码", suspend: "停用", restore: "恢复", saveQuota: "保存额度",
        approve: "批准", reject: "拒绝", publishPublic: "发布为公共题", rotateCode: "轮换注册码", allocate: "分配额度", replaceAdmin: "更换校管", inspect: "查看明细", viewSubmissions: "查看提交", exportCsv: "导出 CSV"
      }
    },
    studentHome: {
      eyebrow: "学生端",
      title: "开始练习",
      subtitleGuest: "无需登录，可先做公共题库。",
      subtitleSignedIn: "{{name}}，选择今天要继续的练习。",
      taskCount: "{{count}} 题",
      dashboard: {
        greeting: "{{name}}，今天先完成课堂任务。",
        headerSummary: "共 {{total}} 项课堂作业，{{active}} 项待继续",
        entryAria: "学生学习入口",
        learningTasks: "学习任务",
        pinnedPublic: "置顶 · 公开练习",
        classroom: "课堂作业",
        assignmentSummary: "{{total}} 项作业 · {{active}} 项进行中 · {{notStarted}} 项待开始 · {{completed}} 项已完成",
        assignmentName: "作业名称",
        assignmentMeta: "{{count}} 题 · {{description}}",
        className: "所属班级",
        status: "截止 / 状态",
        problemCount: "题目数",
        progress: "完成进度",
        progressAria: "已完成 {{completed}} 题，共 {{total}} 题",
        active: "进行中",
        notStarted: "待开始",
        completed: "已完成",
        closed: "已结束",
        deadline: "截止 {{value}}",
        unassignedClass: "暂未分班",
        continue: "继续作业",
        selectAssignment: "选择作业：{{title}}",
        continueAssignment: "继续作业：{{title}}",
        selfPractice: "自主练习",
        publicCount: "{{count}} 题",
        publicHint: "按题号或标题搜索",
        browsePublic: "浏览题库",
        recentReview: "最近复盘",
        viewReview: "查看复盘",
        emptyReview: "提交后可复盘错题。"
      },
      nextLearning: {
        title: "继续学习",
        loading: "正在同步学习进度",
        loadingHint: "马上为你接回最近的学习任务。",
        fallbackTitle: "继续完成当前任务",
        fallbackHint: "学习建议暂未更新，不影响进入课堂作业或自主练习。",
        empty: "从一个明确任务开始",
        emptyHint: "完成一次提交后，这里会接回最适合继续的内容。",
        keepLearning: "继续学习",
        goal: "目标：{{goal}}",
        open: "开始行动",
        openAria: "开始学习行动：{{title}}"
      },
      selfPractice: {
        title: "自主练习",
        meta: "{{count}} 道题，可按难度选择",
        action: "查看题库"
      },
      errors: {
        publicBank: "公共题库暂时不可用。",
        assignments: "老师作业加载失败。"
      },
      loading: {
        publicBank: "正在读取",
        assignments: "正在读取课堂作业",
        assignmentHint: "公共题库可先进入练习"
      },
      public: {
        title: "公共题库",
        meta: "{{count}} 题",
        description: "按难度或题名选择题目。",
        cta: "开始练习"
      },
      login: {
        title: "课堂学习",
        meta: "课堂作业、错题复盘、学习记录",
        description: "登录后查看课堂作业、错题复盘和学习记录。",
        cta: "登录查看",
        shortcut: "登录查看课堂学习"
      },
      guestPreview: {
        today: "公开练习",
        todayHint: "选择一道题开始练习。",
        difficulty: "难度",
        difficultyAria: "公共题库难度分布",
        easy: "基础 {{count}} 题",
        medium: "提高 {{count}} 题",
        hard: "挑战 {{count}} 题",
        viewAll: "查看全部",
        starterTitle: "推荐起步",
        starterAria: "推荐起步题目",
        starterHint: {
          easy: "适合热身",
          medium: "练习边界",
          hard: "综合应用",
          unknown: "打开练习"
        }
      },
      assignment: {
        description: "进入当前作业的下一题。",
        cta: "进入作业"
      },
      emptyAssignments: {
        title: "暂无课堂作业",
        meta: "等待老师发布作业。"
      },
      review: {
        aria: "我的错题复盘",
        eyebrow: "我的复盘",
        title: "最近该回看的题",
        loading: "正在读取画像",
        waiting: "等待更多提交",
        failedCount: "{{count}} 次未通过记录",
        profileBuilding: "画像仍在形成",
        focusHint: "先围绕 {{focus}} 做一次错题复盘。",
        organizing: "正在整理最近错题。",
        empty: "提交后会整理可复盘错题。"
      }
    },
    studentPublic: {
      eyebrow: "学生端",
      title: "公共题库",
      subtitle: "{{count}} 道题，按难度筛选或直接搜索题号、题名。",
      back: "返回",
      loading: "正在读取公共题库",
      enteringAssignment: "正在进入作业",
      assignmentTitle: "课堂作业",
      noProblems: "暂无题目",
      searchAria: "搜索公共题目",
      searchPlaceholder: "搜索题号、题目或关键词",
      clearSearch: "清空搜索",
      filterAria: "按难度和草稿筛选",
      status: "显示 {{visible}}/{{total}} 题",
      draftCount: "{{count}} 题有草稿",
      clearFilters: "清空筛选",
      resume: "继续上次：{{title}}",
      listAria: "公共题目",
      problemFallback: "打开题目查看完整题面。",
      timeLimit: "{{value}} ms",
      memoryLimit: "{{value}} MB",
      emptyFiltered: "没有匹配的题目",
      empty: "暂无公共题目",
      errors: {
        load: "公共题库加载失败。",
        assignmentLoad: "作业加载失败。"
      },
      filters: {
        all: "全部",
        draft: "有草稿"
      },
      difficulty: {
        easy: "基础",
        medium: "提高",
        hard: "挑战",
        unknown: "未分级"
      }
    },
    teacherShell: {
      aria: "教师工作台导航",
      kicker: "教师端",
      title: "教学分析",
      managementTitle: "管理工具",
      footnote: "按班级、作业、题目递进查看客观结果。",
      managementFootnote: "维护班级、题库和标准库。",
      groups: {
        results: "结果查看",
        management: "管理工具"
      },
      nav: {
        analytics: "教学分析",
        analyticsDescription: "班级、作业、题目结果",
        roster: "班级名单",
        rosterDescription: "学生导入与班级维护",
        problemBank: "题库管理",
        problemBankDescription: "题目列表与编辑工作台",
        aiLibrary: "AI 标准库",
        aiLibraryDescription: "正式库与候选治理",
        system: "系统状态",
        systemDescription: "AI 检测与运行状态"
      }
    },
    classOverview: {
      title: "班级学情",
      breadcrumb: "作业",
      backAssignments: "返回作业",
      newAssignment: "新建作业"
    },
    teacherAnalytics: {
      kicker: "教学结果分析",
      landing: {
        title: "选择班级",
        description: "先进入班级，再查看这个班级下的作业和题目结果。",
        classList: "班级列表",
        classFallback: "班级",
        noTeacherName: "未设置教师"
      },
      breadcrumb: {
        classes: "班级"
      },
      scope: {
        class: "班级",
        assignment: "作业",
        problem: "题目"
      },
      class: {
        description: "查看这个班级跨作业的提交、正确率、错误分布和知识路径。"
      },
      assignment: {
        description: "查看这次作业的题目结果、错误分布和 AI 知识归因。"
      },
      problem: {
        description: "查看这道题的提交情况、错因分布、知识路径和证据样本。"
      },
      actions: {
        newAssignment: "新建作业",
        backToAnalytics: "返回教学分析"
      },
      loading: {
        classes: "正在读取班级",
        class: "正在读取班级分析",
        assignment: "正在读取作业分析",
        problem: "正在读取题目分析"
      },
      errors: {
        load: "教学分析数据读取失败。"
      },
      granularity: {
        chapter: "按章节",
        knowledgePoint: "按知识点",
        skillUnit: "按能力点",
        mistakePoint: "按易错点"
      },
      metrics: {
        assignments: "作业数",
        students: "学生数",
        rosterStudents: "在册人数",
        submissions: "提交数",
        accuracy: "正确率",
        studentAccuracy: "学生通过率",
        attemptAccuracy: "尝试通过率",
        dataCompleteness: "数据完整率",
        recoveryEvidence: "恢复样本",
        errorCount: "错误次数",
        affectedStudents: "涉及学生",
        submittedStudents: "提交人数",
        unsubmittedStudents: "未提交人数",
        averageAttempts: "平均提交",
        lowPassProblems: "低通过题",
        passedStudents: "通过人数",
        failedStudents: "未通过人数"
      },
      sections: {
        metrics: "基础指标",
        visualization: "数据可视化",
        ranking: "错误分布",
        share: "占比",
        currentPath: "当前知识路径",
        assignments: "作业",
        problems: "题目"
      },
      scopeTitle: "{{scope}}结果分布",
      pathMeta: "{{count}} 次 · {{students}} 名学生 · {{repeated}} 名重复 · {{problems}} 道题",
      pathLabel: "路径",
      pathStatus: {
        formal: "正式标准库",
        provisional: "教师校正或临时路径",
        inferred: "历史推断",
        unclassified: "未归类"
      },
      completeness: {
        note: "身份缺失 {{identityMissing}} · 未诊断 {{analysisMissing}}"
      },
      recovery: {
        note: "可比较后续样本 {{denominator}}"
      },
      status: {
        active: "进行中",
        draft: "草稿",
        closed: "已结束"
      },
      defaultLabels: {
        defaultClass: "默认班级",
        assignmentFallbackWithId: "课堂作业 #{{id}}",
        pilotAssignment: "课堂编程作业",
        arraySequence: "数组与序列",
        ioString: "输入输出与字符串",
        loopRecursion: "循环与递归",
        dataStructure: "数据结构",
        dynamicProgramming: "动态规划",
        general: "综合应用",
        boundary: "边界处理",
        ioFormat: "输入输出规范",
        stateMaintenance: "状态维护",
        problem: "题目",
        submissionRecord: "提交记录",
        studentWithId: "学生 #{{id}}"
      },
      tables: {
        assignmentTitle: "作业列表",
        problemTitle: "题目列表"
      },
      tableLabels: {
        status: "状态",
        problemCount: "题目",
        submissions: "提交",
        accuracy: "正确率",
        passed: "通过",
        issue: "错因"
      },
      units: {
        count: "次",
        problem: "题",
        passed: "通过"
      },
      ai: {
        title: "AI 知识归因",
        class: "班级路径",
        assignment: "作业路径",
        problem: "题目路径"
      },
      fit: {
        HIT: "标准库命中",
        PARTIAL: "标准库半命中",
        MISS: "库外候选",
        UNKNOWN: "归属未确认"
      },
      evidence: {
        title: "证据样本",
        submissionWithId: "提交 #{{id}}"
      },
      recentState: {
        summary: "{{status}} · 近 30 天 {{submissions}} 次提交 / {{problems}} 道题",
        status: {
          recovered: "近期有恢复证据",
          repeated: "近期重复出现",
          changing: "近期问题发生变化",
          single: "单次观察，证据不足",
          observing: "近期观察中"
        }
      },
      aiLoop: {
        title: "查看反馈后的表现",
        noObservation: "暂无可判断样本",
        noObservationDescription: "学生查看反馈并完成同题后续提交后，这里会显示变化。结果只表示前后相关，不直接证明因果。",
        followupEvidence: "后续提交 #{{id}}",
        status: {
          improved: "查看反馈后改善",
          shifted: "查看反馈后问题转移",
          sameIssue: "查看反馈后仍卡在同类问题",
          regressed: "查看反馈后出现回退",
          verdictChanged: "查看反馈后评测阶段变化",
          noClearChange: "查看反馈后暂未见明确变化",
          awaiting: "等待查看反馈后的提交"
        },
        summary: {
          improved: "同题下一次提交已通过；这是观察到改善的相关证据，但不能单独证明由反馈造成。",
          shifted: "同题后续提交进入了新的问题阶段，当前证据显示原问题已变化。",
          sameIssue: "同题后续提交仍出现同类问题，当前证据尚未显示该问题消失。",
          regressed: "同题后续提交的评测阶段出现回退，但现有数据不能单独解释原因。",
          verdictChanged: "同题后续提交的评测阶段发生变化，是否属于推进仍需更多证据。",
          noClearChange: "已有同题后续提交，但目前没有观察到明确变化。",
          awaiting: "学生已查看反馈，但还没有同题后续提交，暂不能判断效果。"
        }
      },
      correction: {
        title: "校正归因",
        issue: "错因",
        fineIssue: "细分错因",
        type: "校正类型",
        types: {
          diagnosis: "错因判断",
          knowledgePath: "知识路径",
          evidence: "证据引用",
          advice: "反馈内容"
        },
        knowledgePath: "修正后的知识路径",
        knowledgePathPlaceholder: "例如 基础语法 / 输入输出 / 多组数据读取",
        evidenceRef: "应关联的证据",
        evidenceRefPlaceholder: "例如 代码第 4 行或首个失败样例",
        note: "记录",
        submit: "保存校正",
        unavailable: "暂无可校正提交。"
      },
      empty: {
        noClasses: "暂无班级",
        noClassesDescription: "创建班级后，这里会进入班级分析。",
        classNotFound: "班级未找到",
        classNotFoundDescription: "返回班级列表重新选择。",
        assignmentNotFound: "作业未找到",
        assignmentNotFoundDescription: "返回班级页面重新选择作业。",
        problemNotFound: "题目未找到",
        problemNotFoundDescription: "返回作业页面重新选择题目。",
        noAssignments: "当前班级暂无作业数据。",
        noSubmissions: "暂无提交数据。",
        noInsight: "暂无可归因数据。",
        noEvidence: "暂无提交证据样本。",
        noKnowledgePath: "暂无知识路径。",
        noIssue: "暂无集中错因"
      }
    },
    teacherHome: {
      classOverview: "班级概览",
      classLabel: "班级",
      defaultClass: "默认班级",
      unboundClass: "未绑定班级",
      pilotTitle: "课堂编程作业",
      assignmentFallbackWithId: "课堂作业 #{{id}}",
      classProgress: "班级进度",
      newAssignment: "新建作业",
      assignmentListAria: "教师作业入口",
      assignmentListTitle: "作业",
      assignmentCount: "{{count}} 个",
      loadingAssignments: "正在读取作业",
      emptyAssignmentsTitle: "暂无作业",
      emptyAssignmentsDescription: "先新建一项作业。",
      viewDetails: "查看详情",
      needsAttentionWithCount: "需看 {{count}}",
      metrics: {
        assignments: "作业",
        students: "学生",
        needsAttention: "需看",
        recentSubmissions: "新增",
        problems: "题目",
        status: "状态"
      },
      console: {
        title: "作业中心",
        description: "先看有哪些作业，再进入作业里的题目、正确率和学生证据。",
        tableTitle: "作业列表",
        tableHint: "按老师真实批阅路径组织：作业 → 题目 → 学生。",
        classColumn: "班级",
        submitted: "提交",
        passRate: "通过率",
        attention: "需关注",
        recent: "最近",
        action: "进入",
        queueTitle: "待处理队列",
        queueHint: "只放需要老师优先看的学生或题目。",
        queueEmptyTitle: "暂时没有待处理",
        queueEmptyDescription: "暂无需处理对象。",
        stable: "稳定",
        waiting: "等待提交",
        noSharedIssue: "暂无集中错因"
      },
      errors: {
        loadFailed: "教师端数据读取失败。",
        serviceUnavailable: "教师端数据读取失败，服务暂时不可用。",
        notFound: "教师端数据读取失败，未找到课堂资源。",
        separator: "，"
      }
    },
    assignmentDetail: {
      fallbackTitle: "课堂作业",
      pilotTitle: "课堂编程作业",
      defaultClass: "默认班级",
      unknownStudent: "学生 #{{id}}",
      loading: "正在加载作业详情",
      notFound: "作业未找到",
      notFoundDescription: "返回作业中心重新选择作业。",
      backToAssignmentCenter: "返回作业中心",
      header: {
        problemCount: "{{count}} 题",
        submitted: "{{count}} 人已提交",
        passRate: "{{rate}}% 通过",
        startReview: "开始批阅",
        viewProblem: "看题目"
      },
      status: {
        active: "进行中",
        draft: "草稿",
        closed: "已结束",
        unset: "未设置",
        pending: "待提交",
        mastered: "已掌握",
        needsLecture: "需讲评",
        progressing: "推进中"
      },
      difficulty: {
        easy: "基础",
        medium: "提高",
        hard: "挑战",
        unknown: "-"
      },
      focus: {
        aria: "作业待处理",
        eyebrow: "待处理",
        title: "下一步",
        noStudentTitle: "暂无需关注学生",
        noStudentDescription: "目前没有学生被标记为需关注，先看题目推进即可。",
        studentLabel: "先看学生",
        studentAction: "查看学生",
        defaultStudentReason: "有新的卡点需要确认。",
        repeatedIssue: "{{issue}}反复出现",
        problemLabel: "先讲题目",
        noProblemTitle: "等待提交",
        noProblemDescription: "暂无需要讲评的题目。",
        problemWithIssue: "{{count}} 名需关注，主要错因是 {{issue}}。",
        problemWithoutIssue: "{{count}} 名需关注，先看学生明细再决定讲评。",
        stable: "稳定"
      },
      metrics: {
        aria: "作业关键指标",
        submittedStudents: "提交人数",
        passedStudents: "通过人数",
        totalSubmissions: "总提交",
        needsAttention: "需关注",
        recentSubmissions: "最近提交",
        submittedPeople: "{{count}} 人"
      },
      trend: {
        eyebrow: "提交推进",
        title: "作业增长情况",
        chartAria: "提交推进曲线",
        attempts: "提交次数",
        submitted: "提交人数",
        passed: "通过人数",
        onePoint: "已有 {{attempts}} 次提交，等待形成趋势。{{submitted}} 人提交，{{passed}} 人通过。",
        empty: "等待第一次提交。"
      },
      problems: {
        aria: "题目推进列表",
        eyebrow: "题目",
        title: "题目推进",
        problem: "题目",
        difficulty: "难度",
        submitted: "提交",
        passed: "通过",
        attention: "需看",
        issue: "集中问题",
        action: "操作",
        view: "看题目",
        noIssue: "暂无集中错因"
      },
      console: {
        stageAssignment: "作业题目",
        stageProblem: "题目分析",
        stageStudent: "学生诊断",
        problemListHint: "先看每道题的提交、正确率和集中错因，再进入具体题目。",
        aiPanelTitle: "AI 辅助摘要",
        aiPanelHint: "AI 只解释证据，不替代老师判断。",
        priorityStudent: "优先学生",
        priorityProblem: "优先讲评题",
        lectureSuggestion: "讲评建议",
        noEvidenceYet: "暂无提交证据。",
        submittedRatio: "提交人数"
      }
    },
    taskEditor: {
      eyebrow: "题库编辑",
      newProblem: "新建题目",
      status: {
        checked: "检查完成",
        basic: "基本完整",
        incomplete: "未完成"
      },
      actions: {
        saveProblem: "保存题目",
        save: "保存",
        reset: "重置",
        addPublic: "添加公开点",
        addHidden: "添加隐藏点",
        delete: "删除"
      },
      badges: {
        publicTests: "{{count}} 个公开测试点",
        publicCompact: "{{count}} 个公开",
        hiddenCompact: "{{count}} 个隐藏",
        checks: "{{count}}/5 检查完成",
        problemCount: "{{count}} 个"
      },
      sections: {
        problemInfo: "题目信息",
        tests: "测试点",
        teaching: "教学增强信息",
        saveChecks: "保存检查",
        problemList: "题目列表"
      },
      fields: {
        title: "题目标题",
        difficulty: "难度",
        timeLimit: "时限 ms",
        statement: "题面",
        starterCode: "默认代码",
        memoryLimit: "内存 KB",
        feedbackScope: "反馈范围",
        knowledge: "知识点",
        strategy: "算法策略",
        mistakes: "常见误区",
        boundaries: "边界类型"
      },
      placeholders: {
        title: "两数求和",
        feedbackScope: "空输入、循环边界、复杂度"
      },
      quality: {
        title: "题目标题",
        statement: "题面",
        publicTests: "公开测试点",
        hiddenTests: "隐藏测试点",
        knowledge: "知识点",
        filled: "已填写",
        empty: "未填写",
        incomplete: "未完成",
        visibleCount: "{{count}} 个可见",
        hiddenCount: "{{count}} 个隐藏",
        needPublic: "至少保留 1 个样例",
        notSet: "未设置",
        optional: "选填"
      },
      difficulty: {
        easy: "基础",
        medium: "提高",
        hard: "挑战"
      },
      statement: {
        emptyPreview: "暂无题面内容",
        fallbackText: "文本",
        toolbarAria: "题面 Markdown 工具栏",
        bold: "加粗",
        boldFallback: "加粗文本",
        italic: "斜体",
        italicFallback: "斜体文本",
        inlineCode: "行内代码",
        quote: "引用",
        quoteFallback: "提示",
        unorderedList: "无序列表",
        orderedList: "有序列表",
        listFallback: "列表项",
        link: "链接",
        linkFallback: "链接文字",
        edit: "编辑",
        preview: "预览"
      },
      code: {
        toolbarAria: "默认代码工具栏",
        languageAria: "默认代码语言"
      },
      tests: {
        index: "编号",
        status: "状态",
        input: "输入",
        expected: "期望输出",
        score: "分数",
        hidden: "隐藏",
        actions: "操作",
        publicShort: "公开",
        hiddenShort: "隐藏",
        publicTest: "公开测试点",
        hiddenTest: "隐藏测试点",
        inputAria: "测试点 {{index}} 输入",
        expectedAria: "测试点 {{index}} 期望输出",
        hiddenAria: "隐藏测试点 {{index}}",
        deleteAria: "删除测试点 {{index}}"
      },
      empty: {
        noProblems: "暂无题目"
      },
      errors: {
        catalog: "题目列表加载失败。",
        load: "题目加载失败。",
        required: "请填写标题和题面。",
        needPublic: "至少需要 1 个公开测试点。",
        save: "保存失败。"
      },
      success: {
        saved: "题目已保存，可在教师工作台绑定到作业。"
      }
    },
    teacherManagement: {
      sections: {
        classes: {
          eyebrow: "管理 / 班级名单",
          title: "班级名单",
          description: "创建默认班级，导入或更新学生名单。"
        },
        problems: {
          eyebrow: "管理 / 题库",
          title: "题库",
          description: "导入题目、维护题面、测试点和教学增强信息。"
        },
        aiLibrary: {
          eyebrow: "管理 / AI 标准库",
          title: "AI 标准库",
          description: "维护能力点、易错点和 AI 教学解释标准。"
        },
        system: {
          eyebrow: "管理 / 系统状态",
          title: "系统状态",
          description: "检查 AI 服务、模型配置和关键运行状态。"
        }
      },
      classManage: {
        listAria: "班级列表",
        listTitle: "班级",
        classCount: "{{count}} 个班级",
        emptyTitle: "暂无班级",
        emptyDescription: "先创建一个默认班级。",
        defaultClass: "默认班级",
        summaryAria: "当前班级摘要",
        importAction: "导入或更新名单",
        rosterCount: "{{count}} 名学生",
        tabs: {
          aria: "班级名单工作区",
          roster: "学生名单",
          import: "导入记录",
          settings: "班级设置"
        },
        rosterColumns: {
          student: "学生",
          number: "学号",
          status: "状态",
          action: "操作"
        },
        create: {
          title: "创建班级",
          name: "班级名称",
          grade: "年级",
          teacher: "任课老师",
          submit: "创建"
        },
        import: {
          eyebrow: "名单维护",
          currentTarget: "导入到当前班级",
          waiting: "等待班级",
          sourceTitle: "选择名单来源",
          sourceDescription: "上传 CSV/XLSX，或直接在下一步粘贴名单。",
          fileLabel: "名单文件",
          fileNote: "CSV 或 XLSX",
          targetLabel: "目标班级",
          pasteTitle: "粘贴或校对名单",
          pasteDescription: "格式保持为班级、姓名、学号，预览后再导入。",
          pasteLabel: "名单内容",
          pastePlaceholder: "班级,姓名,学号\n高一1班,张三,01",
          confirmTitle: "预览并导入",
          confirmDescription: "先预览，确认无误后再写入名单。",
          preview: "预览名单",
          commit: "导入名单"
        }
      },
      problemManage: {
        problemCount: "{{count}} 个题目",
        listAria: "题目列表",
        editorTitle: "题库编辑",
        visibleCount: "{{visible}} / {{total}} 个题目",
        newProblem: "新建题目",
        currentSort: "当前排序：{{sort}}",
        switchSort: "切换排序，当前{{sort}}",
        searchPlaceholder: "搜索题目标题",
        noSummary: "题目描述待完善",
        emptyTitle: "暂无题目",
        emptyFilteredTitle: "没有匹配题目",
        emptyDescription: "导入题目或新建题目。",
        emptyEditorDescription: "先从左侧导入或新建一道题。",
        emptyEditorFilteredDescription: "调整搜索条件，或点击新建题目。",
        paginationAria: "题目分页",
        previousPage: "上一页",
        nextPage: "下一页",
        pageAria: "第 {{page}} 页",
        sort: {
          id: "按编号",
          difficulty: "按难度",
          timeLimit: "按时限"
        },
        difficulty: {
          easy: "基础",
          medium: "提高",
          hard: "挑战"
        },
        readOnly: {
          factsAria: "题目运行限制",
          difficulty: "难度",
          timeLimit: "时间限制",
          memoryLimit: "内存限制",
          publicSamples: "公开样例",
          statementTitle: "题目内容",
          statementDescription: "学生实际看到的完整题面。",
          emptyStatement: "这道题暂未填写题目描述。",
          samplesTitle: "公开样例",
          samplesDescription: "用于讲解题意和核对输入输出格式。",
          sampleNumber: "样例 {{number}}",
          sampleInput: "输入",
          sampleOutput: "输出",
          emptyValue: "（空）",
          noSamples: "这道题暂未设置公开样例。",
          hiddenTests: "另有 {{count}} 个隐藏评测点，内容不在只读视图中展示。",
          teachingTitle: "教学配置",
          teachingDescription: "查看题目的知识、策略与讲解方向。",
          knowledgePoints: "知识点",
          algorithmStrategies: "算法策略",
          commonMistakes: "常见错误",
          boundaryTypes: "边界类型",
          aiDirection: "AI 引导方向",
          starterCode: "查看起始代码"
        },
        import: {
          title: "导入题目",
          fileLabel: "题目文件",
          fileNote: "Markdown、JSON、CSV 或 XLSX",
          pasteLabel: "粘贴题目",
          pastePlaceholder: "# 两数求和\n\n## 题目描述\n...\n\n## 样例输入\n1 2\n\n## 样例输出\n3",
          preview: "预览",
          commit: "导入"
        }
      },
      readiness: {
        aria: "系统状态",
        title: "开课状态",
        summaryLabel: "状态结论",
        aiScan: "AI 配置扫描",
        scanDescription: "检查模型、密钥和调用链是否可用。",
        ready: "可开课",
        blocked: "需处理",
        degraded: "可试用",
        unknown: "读取中",
        canStart: "可以开课",
        cannotStart: "暂不能正式开课",
        trialStart: "可试用",
        issueSummary: " · {{blocking}} 阻断 · {{warnings}} 提醒",
        readyDescription: "关键服务已通过检查。",
        blockedDescription: "{{count}} 个阻断需要处理。",
        degradedDescription: "{{count}} 个提醒，先试用再完善。",
        loadingDescription: "正在读取系统状态。",
        classes: "班级",
        problems: "题目",
        library: "标准库",
        statsAria: "管理数据",
        refresh: "刷新",
        runAi: "检测 AI",
        running: "检测中",
        detailSummary: "{{blocking}} 个阻断 · {{warnings}} 个提醒",
        checkDetails: "检查详情",
        checkFail: "失败",
        checkWarn: "提醒",
        checkPass: "通过",
        allClear: "全部关键检查已通过。",
        hiddenMore: "还有 {{count}} 项",
        moreCompact: "+{{count}}"
      },
      aiLibrary: {
        tabs: {
          library: "正式库",
          governance: "人工治理"
        },
        governance: {
          eyebrow: "标准库人工治理",
          title: "候选审核工作台",
          description: "审核 AI 建议的新能力点和易错点，确认后再纳入正式库。",
          pendingCount: "待处理 {{count}}",
          refresh: "刷新候选",
          summaryAria: "人工治理摘要",
          highFrequencyPaths: "高频路径",
          weakPaths: "待补强路径",
          noHighFrequencyPaths: "暂无高频候选路径。",
          noWeakPaths: "暂无集中薄弱路径。",
          emptyTitle: "暂无成长候选",
          emptyDescription: "暂无待审核候选。",
          emptyFilteredTitle: "没有匹配候选",
          emptyFilteredDescription: "调整状态、路径或关键词筛选。",
          metrics: {
            total: "候选总数",
            pending: "待审核",
            duplicates: "重复聚合",
            merged: "已入库",
            closed: "拒绝/忽略"
          },
          filters: {
            aria: "筛选成长候选",
            searchAria: "搜索候选名称、ID、路径或证据",
            searchPlaceholder: "名称、ID、路径、证据",
            statusAria: "候选状态",
            allStatus: "全部状态",
            pendingOnly: "只看待处理",
            clear: "清除筛选"
          },
          status: {
            proposed: "待处理",
            needsReview: "待审核",
            blocked: "需修正",
            mergedSimilar: "重复聚合",
            teacherApproved: "教师批准",
            merged: "已入库",
            rejected: "已拒绝",
            ignored: "已忽略"
          },
          evidence: {
            supported: "有证据",
            noDirectCodeEvidence: "无直接代码证据",
            unsupported: "证据无效",
            unknown: "证据待确认"
          }
        }
      }
    }
  },
  en: {
    common: {
      language: "Language",
      chinese: "中文",
      english: "English",
      appName: "Wenzhong Coding Learning Platform",
      mainNavigation: "Main navigation",
      openNavigation: "Open navigation",
      closeNavigation: "Close navigation",
      skipToMain: "Skip to main content",
      login: "Log in",
      logout: "Log out",
      studentSide: "Student",
      teacherSide: "Teacher",
      teacherWorkbench: "Teacher Workbench",
      assignmentCenter: "Assignments",
      resourceManagement: "Resources",
      loadingPage: "Loading page",
      theme: {
        toggle: "Toggle theme",
        toLight: "Switch to light mode",
        toDark: "Switch to dark mode",
        light: "Light",
        dark: "Dark"
      }
    },
    feedbackMeta: {
      knowledgePath: "Knowledge path",
      knowledgePathAria: "Knowledge path for this suggestion",
      noKnowledgePath: "No suitable standard-library path has been found yet.",
      codeEvidence: "Code evidence",
      codeEvidenceAria: "Code evidence for this suggestion",
      evidenceBasis: "Evidence basis",
      jumpToCode: "Return to the editor and highlight this code line",
      viewCode: "View the related code",
      line: "Line {{line}}",
      lineRange: "Lines {{start}}-{{end}}",
      noJumpableLine: "{{evidence}}; no jumpable code line is available.",
      noCodeEvidence: "This suggestion has no locatable code evidence yet.",
      listSeparator: ", ",
      pathStatus: {
        formal: "Formal library",
        provisional: "Provisional node",
        inferred: "Legacy inference",
        unclassified: "Unclassified"
      },
      evidenceKinds: {
        judge: "Judge result",
        problem: "Problem statement",
        verdict: "Run result",
        source: "Submitted code",
        analysis: "Analysis evidence"
      }
    },
    problemHistory: {
      title: "Recent submissions",
      aria: "Submission history for this problem",
      submission: "Submission #{{id}}",
      submittedCode: "Submitted code",
      loadFailed: "Failed to load this submission.",
      feedback: {
        ready: "AI suggestions ready",
        generating: "Generating AI suggestions",
        failed: "AI suggestions unavailable",
        pending: "Waiting for AI suggestions"
      }
    },
    routeHub: {
      eyebrow: "Entry",
      title: "Learning Platform",
      description: "Students and teachers each get a clear path, with direct access to the work that matters.",
      studentTitle: "Student Learning",
      studentDescription: "Open class assignments, public practice, and personal submission records.",
      teacherTitle: "Teacher Workbench",
      teacherDescription: "Review classes, assignments, problems, and students, plus manage rosters, problems, and the AI library.",
      welcome: "Welcome back",
      headlineStart: "Start with one problem,",
      headlineEnd: "see real progress",
      summary: "Students get instant feedback. Teachers see authentic learning progress.",
      roleAria: "Choose your role",
      studentCta: "I'm a student",
      teacherCta: "I'm a teacher",
      schoolAdminCta: "School admin",
      preview: {
        badge: "Interface screenshot",
        alt: "Student coding and judge interface screenshot showing Two Sum code and an all-passed result"
      },
      features: {
        languagesTitle: "Practice in multiple languages",
        languagesDetail: "Python / C++ / Java / C",
        feedbackTitle: "Instant judge and detailed feedback",
        feedbackDetail: "Automated evaluation pinpoints issues",
        dataTitle: "Learning data visualization",
        dataDetail: "Visible progress and more precise teaching"
      },
      demo: {
        aria: "Programming practice and instant judge example",
        back: "Back to problems",
        problem: "Two Sum",
        difficulty: "Easy",
        problemId: "Problem ID: 1001",
        submit: "Submit code",
        codeAria: "Python Two Sum sample code",
        console: "Console",
        caseOne: "Test case 1 passed: output [0, 1]",
        caseTwo: "Test case 2 passed: output [1, 2]",
        result: "Judge result",
        quality: "Code quality",
        passed: "All passed",
        testCount: "Passed 12 / 12 test cases",
        runtime: "Runtime",
        scoreTitle: "Score details",
        correctness: "Correctness",
        efficiency: "Efficiency",
        style: "Code style",
        feedbackTitle: "Great work! All tests passed.",
        feedbackDetail: "Time and space efficiency are both excellent. Keep it up."
      },
      loop: {
        title: "Learning loop: Practice → Judge → Review",
        practice: "Practice",
        practiceDetail: "Choose a problem, write and submit code",
        judge: "Judge",
        judgeDetail: "Run automated checks and get feedback",
        judgeEvidence: "All passed 12 / 12",
        review: "Review",
        reviewDetail: "Study explanations and reinforce learning",
        reviewEvidence: "Time complexity"
      }
    },
    portals: {
      checking: "Checking account",
      reconnecting: "Reconnecting",
      temporarilyUnavailable: "Unable to verify your session right now",
      reconnectHint: "Your existing sign-in is preserved. Make sure the local service is running, then try again.",
      retry: "Try again",
      secureWorkspace: "Independent secure workspace",
      loginFailed: "Sign-in failed",
      loadFailed: "Failed to load data",
      actionFailed: "Action not completed",
      registerFailed: "Registration was not submitted",
      registrationPending: "Application submitted. Your school administrator will review it.",
      passwordChanged: "Password changed. Please sign in again.",
      passwordFailed: "Could not change password",
      changePassword: "Change your temporary password",
      changeHint: "The temporary password works once. Use at least 10 characters with letters and numbers.",
      roleMismatch: "The signed-in account belongs to another portal. Sign out first.",
      oneTimeSecret: "These credentials are shown once. Store them securely now.",
      temporaryPassword: "Temporary password",
      schoolCode: "School registration code",
      quotaLine: "Total {{total}} · Allocated {{allocated}} · Used {{used}}",
      teacher: { eyebrow: "Teacher portal", title: "Teacher workbench sign-in", description: "Apply with a school registration code, then manage only your own teaching data after approval." },
      platform: {
        eyebrow: "Platform admin", title: "Platform administration sign-in", description: "For schools, school administrators, school quota pools, and platform problem governance only.",
        workspaceTitle: "Platform Administration", workspaceDescription: "Manage school boundaries and platform content without opening teacher or student details.",
        createSchool: "Create school and admin", schoolGovernance: "School governance", schools: "Schools", schoolSummary: "School-level summary",
        problemReviews: "Shared and public problem reviews", contentGovernance: "Platform content governance", noSchools: "No schools yet", noReviews: "No pending problem reviews", created: "School created"
      },
      school: {
        eyebrow: "School admin", title: "School administration sign-in", description: "Review your teachers, allocate school quota, and inspect school teaching data in read-only mode.",
        workspaceTitle: "School Administration", workspaceDescription: "The school manages teacher accounts and quota; teaching content remains read-only and exportable.",
        overview: "School overview", quotaPool: "Monthly AI quota pool", applications: "Teacher applications", pending: "pending", noApplications: "No pending applications",
        teachers: "School teachers", quotaAllocation: "Teacher quota allocation", noTeachers: "No teachers", teachingData: "School teaching data", readOnly: "Read only",
        students: "students", assignmentsCount: "assignments", noTeachingData: "No teaching data", teacherUsage: "Allocated {{allocated}} · Used {{used}} · Reserved {{reserved}}",
        studentsTitle: "Student roster", assignmentsTitle: "Assignments", submissionsTitle: "Submission details", noStudents: "No students", noAssignments: "No assignments", noSubmissions: "No submissions"
      },
      fields: {
        schoolName: "School name", adminUsername: "Admin username", adminDisplayName: "Admin name", monthlyQuota: "Monthly school quota",
        totalQuota: "Total", allocatedQuota: "Allocated", usedQuota: "Used", availableQuota: "Available", teacherQuota: "Teacher monthly quota", replacementUsername: "New admin username", replacementDisplayName: "New admin name"
      },
      actions: {
        create: "Create school", refresh: "Refresh", resetPassword: "Reset password", suspend: "Suspend", restore: "Restore", saveQuota: "Save quota",
        approve: "Approve", reject: "Reject", publishPublic: "Publish public", rotateCode: "Rotate registration code", allocate: "Allocate", replaceAdmin: "Replace admin", inspect: "Inspect", viewSubmissions: "View submissions", exportCsv: "Export CSV"
      }
    },
    studentHome: {
      eyebrow: "Student",
      title: "Start Practice",
      subtitleGuest: "Start with the public problem bank. No login needed.",
      subtitleSignedIn: "{{name}}, choose what to continue today.",
      taskCount: "{{count}} problems",
      dashboard: {
        greeting: "{{name}}, finish your class work first today.",
        headerSummary: "{{total}} class assignments · {{active}} to continue",
        entryAria: "Student learning entry points",
        learningTasks: "Learning Tasks",
        pinnedPublic: "Pinned · Public Practice",
        classroom: "Class Assignments",
        assignmentSummary: "{{total}} assignments · {{active}} in progress · {{notStarted}} not started · {{completed}} completed",
        assignmentName: "Assignment",
        assignmentMeta: "{{count}} problems · {{description}}",
        className: "Class",
        status: "Deadline / status",
        problemCount: "Problems",
        progress: "Progress",
        progressAria: "{{completed}} of {{total}} problems completed",
        active: "Active",
        notStarted: "Not started",
        completed: "Completed",
        closed: "Closed",
        deadline: "Due {{value}}",
        unassignedClass: "No class",
        continue: "Continue",
        selectAssignment: "Select assignment: {{title}}",
        continueAssignment: "Continue assignment: {{title}}",
        selfPractice: "Self Practice",
        publicCount: "{{count}} problems",
        publicHint: "Search by number or title",
        browsePublic: "Browse problems",
        recentReview: "Recent Review",
        viewReview: "Open review",
        emptyReview: "Submit solutions to review recent mistakes."
      },
      nextLearning: {
        title: "Continue Learning",
        loading: "Syncing your progress",
        loadingHint: "Your most recent learning task will be ready in a moment.",
        fallbackTitle: "Continue your current task",
        fallbackHint: "Guidance has not refreshed, but class work and self practice are still available.",
        empty: "Start with one clear task",
        emptyHint: "After your next submission, this area will bring back the best task to continue.",
        keepLearning: "Continue learning",
        goal: "Goal: {{goal}}",
        open: "Start action",
        openAria: "Start learning action: {{title}}"
      },
      selfPractice: {
        title: "Self Practice",
        meta: "{{count}} problems, choose by difficulty",
        action: "Browse problems"
      },
      errors: {
        publicBank: "The public problem bank is unavailable.",
        assignments: "Failed to load teacher assignments."
      },
      loading: {
        publicBank: "Loading",
        assignments: "Loading class assignments",
        assignmentHint: "You can practice in the public bank first"
      },
      public: {
        title: "Public Problem Bank",
        meta: "{{count}} problems",
        description: "Choose by difficulty or problem title.",
        cta: "Start practice"
      },
      login: {
        title: "Class Learning",
        meta: "Class assignments, mistake review, and learning records",
        description: "Log in to see class assignments, mistake review, and learning records.",
        cta: "Log in to view",
        shortcut: "Log in for class learning"
      },
      guestPreview: {
        today: "Public Practice",
        todayHint: "Choose a problem and start practicing.",
        difficulty: "Difficulty",
        difficultyAria: "Public problem bank difficulty distribution",
        easy: "{{count}} basic",
        medium: "{{count}} intermediate",
        hard: "{{count}} challenge",
        viewAll: "View all",
        starterTitle: "Recommended start",
        starterAria: "Recommended starter problems",
        starterHint: {
          easy: "Good warm-up",
          medium: "Practice edges",
          hard: "Mixed challenge",
          unknown: "Open practice"
        }
      },
      assignment: {
        description: "Open the next problem in this assignment.",
        cta: "Open assignment"
      },
      emptyAssignments: {
        title: "No class assignments",
        meta: "Waiting for your teacher to publish assignments."
      },
      review: {
        aria: "My mistake review",
        eyebrow: "My Review",
        title: "Problems to revisit",
        loading: "Loading profile",
        waiting: "Waiting for more submissions",
        failedCount: "{{count}} failed submissions",
        profileBuilding: "Profile is still forming",
        focusHint: "Review one problem around {{focus}} first.",
        organizing: "Organizing recent mistakes.",
        empty: "Mistake review appears after submissions."
      }
    },
    studentPublic: {
      eyebrow: "Student",
      title: "Public Problem Bank",
      subtitle: "{{count}} problems. Filter by difficulty or search by number and title.",
      back: "Back",
      loading: "Loading public problem bank",
      enteringAssignment: "Opening assignment",
      assignmentTitle: "Class Assignment",
      noProblems: "No problems",
      searchAria: "Search public problems",
      searchPlaceholder: "Search number, title, or keyword",
      clearSearch: "Clear search",
      filterAria: "Filter by difficulty and drafts",
      status: "Showing {{visible}}/{{total}} problems",
      draftCount: "{{count}} with drafts",
      clearFilters: "Clear filters",
      resume: "Continue: {{title}}",
      listAria: "Public problems",
      problemFallback: "Open the problem to read the full statement.",
      timeLimit: "{{value}} ms",
      memoryLimit: "{{value}} MB",
      emptyFiltered: "No matching problems",
      empty: "No public problems",
      errors: {
        load: "Failed to load the public problem bank.",
        assignmentLoad: "Failed to load the assignment."
      },
      filters: {
        all: "All",
        draft: "Draft"
      },
      difficulty: {
        easy: "Basic",
        medium: "Intermediate",
        hard: "Challenge",
        unknown: "Unrated"
      }
    },
    taskEditor: {
      eyebrow: "Problem editor",
      newProblem: "New problem",
      status: {
        checked: "Checked",
        basic: "Mostly ready",
        incomplete: "Incomplete"
      },
      actions: {
        saveProblem: "Save problem",
        save: "Save",
        reset: "Reset",
        addPublic: "Add public case",
        addHidden: "Add hidden case",
        delete: "Delete"
      },
      badges: {
        publicTests: "{{count}} public cases",
        publicCompact: "{{count}} public",
        hiddenCompact: "{{count}} hidden",
        checks: "{{count}}/5 checks done",
        problemCount: "{{count}} items"
      },
      sections: {
        problemInfo: "Problem info",
        tests: "Test cases",
        teaching: "Teaching notes",
        saveChecks: "Save checks",
        problemList: "Problem list"
      },
      fields: {
        title: "Problem title",
        difficulty: "Difficulty",
        timeLimit: "Time limit ms",
        statement: "Statement",
        starterCode: "Starter code",
        memoryLimit: "Memory KB",
        feedbackScope: "Feedback scope",
        knowledge: "Knowledge points",
        strategy: "Algorithm strategies",
        mistakes: "Common mistakes",
        boundaries: "Boundary types"
      },
      placeholders: {
        title: "Two Sum",
        feedbackScope: "empty input, loop boundary, complexity"
      },
      quality: {
        title: "Problem title",
        statement: "Statement",
        publicTests: "Public cases",
        hiddenTests: "Hidden cases",
        knowledge: "Knowledge",
        filled: "Filled",
        empty: "Empty",
        incomplete: "Incomplete",
        visibleCount: "{{count}} visible",
        hiddenCount: "{{count}} hidden",
        needPublic: "Keep at least 1 sample",
        notSet: "Not set",
        optional: "Optional"
      },
      difficulty: {
        easy: "Basic",
        medium: "Intermediate",
        hard: "Challenge"
      },
      statement: {
        emptyPreview: "No statement content",
        fallbackText: "text",
        toolbarAria: "Statement Markdown toolbar",
        bold: "Bold",
        boldFallback: "bold text",
        italic: "Italic",
        italicFallback: "italic text",
        inlineCode: "Inline code",
        quote: "Quote",
        quoteFallback: "hint",
        unorderedList: "Bulleted list",
        orderedList: "Numbered list",
        listFallback: "list item",
        link: "Link",
        linkFallback: "link text",
        edit: "Edit",
        preview: "Preview"
      },
      code: {
        toolbarAria: "Starter code toolbar",
        languageAria: "Starter code language"
      },
      tests: {
        index: "No.",
        status: "Status",
        input: "Input",
        expected: "Expected output",
        score: "Score",
        hidden: "Hidden",
        actions: "Actions",
        publicShort: "Public",
        hiddenShort: "Hidden",
        publicTest: "Public case",
        hiddenTest: "Hidden case",
        inputAria: "Test case {{index}} input",
        expectedAria: "Test case {{index}} expected output",
        hiddenAria: "Hide test case {{index}}",
        deleteAria: "Delete test case {{index}}"
      },
      empty: {
        noProblems: "No problems yet"
      },
      errors: {
        catalog: "Failed to load problem list.",
        load: "Failed to load problem.",
        required: "Fill in the title and statement.",
        needPublic: "At least 1 public test case is required.",
        save: "Save failed."
      },
      success: {
        saved: "Problem saved. You can attach it to an assignment in the teacher workbench."
      }
    },
    teacherShell: {
      aria: "Teacher workbench navigation",
      kicker: "Teacher",
      title: "Teaching Analytics",
      managementTitle: "Management Tools",
      footnote: "Drill from class to assignment to problem for objective results.",
      managementFootnote: "Maintain classes, problems, and the standard library.",
      groups: {
        results: "Results",
        management: "Management tools"
      },
      nav: {
        analytics: "Analytics",
        analyticsDescription: "Class, assignment, and problem results",
        roster: "Roster",
        rosterDescription: "Student import and class setup",
        problemBank: "Problems",
        problemBankDescription: "Problem list and editor",
        aiLibrary: "AI Library",
        aiLibraryDescription: "Approved library and governance",
        system: "System",
        systemDescription: "AI checks and runtime state"
      }
    },
    classOverview: {
      title: "Class Insights",
      breadcrumb: "Assignments",
      backAssignments: "Back to assignments",
      newAssignment: "New assignment"
    },
    teacherAnalytics: {
      kicker: "Teaching Results",
      landing: {
        title: "Choose a class",
        description: "Start with a class, then drill into its assignments and problem results.",
        classList: "Class list",
        classFallback: "Class",
        noTeacherName: "No teacher name"
      },
      breadcrumb: {
        classes: "Classes"
      },
      scope: {
        class: "Class",
        assignment: "Assignment",
        problem: "Problem"
      },
      class: {
        description: "Review cross-assignment submissions, accuracy, error distribution, and knowledge paths."
      },
      assignment: {
        description: "Review problem results, error distribution, and AI knowledge attribution for this assignment."
      },
      problem: {
        description: "Review submissions, error distribution, knowledge path, and evidence samples for this problem."
      },
      actions: {
        newAssignment: "New assignment",
        backToAnalytics: "Back to analytics"
      },
      loading: {
        classes: "Loading classes",
        class: "Loading class analytics",
        assignment: "Loading assignment analytics",
        problem: "Loading problem analytics"
      },
      errors: {
        load: "Failed to load teaching analytics."
      },
      granularity: {
        chapter: "By chapter",
        knowledgePoint: "By knowledge point",
        skillUnit: "By skill unit",
        mistakePoint: "By mistake point"
      },
      metrics: {
        assignments: "Assignments",
        students: "Students",
        rosterStudents: "Roster students",
        submissions: "Submissions",
        accuracy: "Accuracy",
        studentAccuracy: "Student pass rate",
        attemptAccuracy: "Attempt pass rate",
        dataCompleteness: "Data completeness",
        recoveryEvidence: "Recovery samples",
        errorCount: "Errors",
        affectedStudents: "Students affected",
        submittedStudents: "Submitted",
        unsubmittedStudents: "Not submitted",
        averageAttempts: "Avg attempts",
        lowPassProblems: "Low-pass problems",
        passedStudents: "Passed",
        failedStudents: "Failed"
      },
      sections: {
        metrics: "Core metrics",
        visualization: "Data visualization",
        ranking: "Error distribution",
        share: "Share",
        currentPath: "Current knowledge path",
        assignments: "Assignments",
        problems: "Problems"
      },
      scopeTitle: "{{scope}} result distribution",
      pathMeta: "{{count}} times · {{students}} students · {{repeated}} repeated · {{problems}} problems",
      pathLabel: "Path",
      pathStatus: {
        formal: "Formal library",
        provisional: "Teacher-corrected or provisional",
        inferred: "Historical inference",
        unclassified: "Unclassified"
      },
      completeness: {
        note: "{{identityMissing}} identity missing · {{analysisMissing}} undiagnosed"
      },
      recovery: {
        note: "{{denominator}} comparable follow-up samples"
      },
      status: {
        active: "Active",
        draft: "Draft",
        closed: "Closed"
      },
      defaultLabels: {
        defaultClass: "Default class",
        assignmentFallbackWithId: "Class assignment #{{id}}",
        pilotAssignment: "Class coding assignment",
        arraySequence: "Arrays and sequences",
        ioString: "Input, output, and strings",
        loopRecursion: "Loops and recursion",
        dataStructure: "Data structures",
        dynamicProgramming: "Dynamic programming",
        general: "General application",
        boundary: "Boundary handling",
        ioFormat: "Input/output format",
        stateMaintenance: "State maintenance",
        problem: "Problem",
        submissionRecord: "Submission record",
        studentWithId: "Student #{{id}}"
      },
      tables: {
        assignmentTitle: "Assignments",
        problemTitle: "Problems"
      },
      tableLabels: {
        status: "Status",
        problemCount: "Problems",
        submissions: "Submitted",
        accuracy: "Accuracy",
        passed: "Passed",
        issue: "Issue"
      },
      units: {
        count: "times",
        problem: "problems",
        passed: "passed"
      },
      ai: {
        title: "AI Knowledge Attribution",
        class: "Class paths",
        assignment: "Assignment paths",
        problem: "Problem paths"
      },
      fit: {
        HIT: "Library hit",
        PARTIAL: "Partial library match",
        MISS: "Out-of-library candidate",
        UNKNOWN: "Unconfirmed attribution"
      },
      evidence: {
        title: "Evidence samples",
        submissionWithId: "Submission #{{id}}"
      },
      recentState: {
        summary: "{{status}} · {{submissions}} submissions across {{problems}} problems in 30 days",
        status: {
          recovered: "Recent recovery evidence",
          repeated: "Recently repeated",
          changing: "Issue recently changed",
          single: "Single observation; insufficient evidence",
          observing: "Under recent observation"
        }
      },
      aiLoop: {
        title: "Performance after viewing feedback",
        noObservation: "No comparable sample yet",
        noObservationDescription: "Changes appear after the student views the feedback and submits the same problem again. This is observational evidence, not proof of causation.",
        followupEvidence: "Follow-up submission #{{id}}",
        status: {
          improved: "Improved after viewing feedback",
          shifted: "Issue shifted after viewing feedback",
          sameIssue: "Same issue after viewing feedback",
          regressed: "Regressed after viewing feedback",
          verdictChanged: "Evaluation stage changed",
          noClearChange: "No clear change yet",
          awaiting: "Waiting for a follow-up submission"
        },
        summary: {
          improved: "The next submission for the same problem passed. This is correlated evidence of improvement, not proof that the feedback caused it.",
          shifted: "The follow-up submission moved to a new issue stage; current evidence shows that the original issue changed.",
          sameIssue: "The same issue remains in the follow-up submission; current evidence does not show that it disappeared.",
          regressed: "The follow-up moved backward in the evaluation stages, but current data does not explain the cause.",
          verdictChanged: "The evaluation stage changed; more evidence is needed to determine whether this is progress.",
          noClearChange: "A follow-up submission exists, but no clear change is observable yet.",
          awaiting: "The student viewed the feedback but has not submitted the same problem again, so impact cannot be judged yet."
        }
      },
      correction: {
        title: "Correct attribution",
        issue: "Issue",
        fineIssue: "Detailed issue",
        type: "Correction type",
        types: {
          diagnosis: "Issue diagnosis",
          knowledgePath: "Knowledge path",
          evidence: "Evidence reference",
          advice: "Feedback content"
        },
        knowledgePath: "Corrected knowledge path",
        knowledgePathPlaceholder: "For example Basics / Input and output / Multiple cases",
        evidenceRef: "Expected evidence reference",
        evidenceRefPlaceholder: "For example code line 4 or the first failed case",
        note: "Note",
        submit: "Save correction",
        unavailable: "No correctable submission yet."
      },
      empty: {
        noClasses: "No classes yet",
        noClassesDescription: "Create a class to open class analytics.",
        classNotFound: "Class not found",
        classNotFoundDescription: "Return to the class list and choose again.",
        assignmentNotFound: "Assignment not found",
        assignmentNotFoundDescription: "Return to the class page and choose another assignment.",
        problemNotFound: "Problem not found",
        problemNotFoundDescription: "Return to the assignment page and choose another problem.",
        noAssignments: "No assignment data for this class yet.",
        noSubmissions: "No submission data yet.",
        noInsight: "No attribution data yet.",
        noEvidence: "No evidence samples yet.",
        noKnowledgePath: "No knowledge path yet.",
        noIssue: "No shared issue yet"
      }
    },
    teacherHome: {
      classOverview: "Class overview",
      classLabel: "Class",
      defaultClass: "Default class",
      unboundClass: "Unassigned class",
      pilotTitle: "Class Coding Assignment",
      assignmentFallbackWithId: "Class Assignment #{{id}}",
      classProgress: "Class progress",
      newAssignment: "New assignment",
      assignmentListAria: "Teacher assignment entries",
      assignmentListTitle: "Assignments",
      assignmentCount: "{{count}} items",
      loadingAssignments: "Loading assignments",
      emptyAssignmentsTitle: "No assignments yet",
      emptyAssignmentsDescription: "Create an assignment first.",
      viewDetails: "View details",
      needsAttentionWithCount: "Review {{count}}",
      metrics: {
        assignments: "Assignments",
        students: "Students",
        needsAttention: "Review",
        recentSubmissions: "New",
        problems: "Problems",
        status: "Status"
      },
      console: {
        title: "Assignments",
        description: "Start with the assignment list, then drill into problems, accuracy, and student evidence.",
        tableTitle: "Assignment List",
        tableHint: "Organized by the teacher workflow: assignment → problem → student.",
        classColumn: "Class",
        submitted: "Submitted",
        passRate: "Pass rate",
        attention: "Attention",
        recent: "Recent",
        action: "Open",
        queueTitle: "Priority Queue",
        queueHint: "Only students or problems that need teacher action appear here.",
        queueEmptyTitle: "Nothing urgent",
        queueEmptyDescription: "No items need attention.",
        stable: "Stable",
        waiting: "Waiting",
        noSharedIssue: "No shared issue yet"
      },
      errors: {
        loadFailed: "Failed to load teacher data.",
        serviceUnavailable: "Failed to load teacher data. The service is temporarily unavailable.",
        notFound: "Failed to load teacher data. Classroom resources were not found.",
        separator: ": "
      }
    },
    assignmentDetail: {
      fallbackTitle: "Class Assignment",
      pilotTitle: "Class Coding Assignment",
      defaultClass: "Default class",
      unknownStudent: "Student #{{id}}",
      loading: "Loading assignment details",
      notFound: "Assignment not found",
      notFoundDescription: "Go back to assignments and choose another item.",
      backToAssignmentCenter: "Back to assignments",
      header: {
        problemCount: "{{count}} problems",
        submitted: "{{count}} submitted",
        passRate: "{{rate}}% pass",
        startReview: "Start review",
        viewProblem: "View problem"
      },
      status: {
        active: "Active",
        draft: "Draft",
        closed: "Closed",
        unset: "Unset",
        pending: "Waiting",
        mastered: "Mastered",
        needsLecture: "Needs review",
        progressing: "In progress"
      },
      difficulty: {
        easy: "Basic",
        medium: "Intermediate",
        hard: "Challenge",
        unknown: "-"
      },
      focus: {
        aria: "Assignment triage",
        eyebrow: "Triage",
        title: "Next step",
        noStudentTitle: "No priority student",
        noStudentDescription: "No student is flagged right now. Start from problem progress.",
        studentLabel: "Student first",
        studentAction: "View student",
        defaultStudentReason: "A new blocker needs a quick check.",
        repeatedIssue: "{{issue}} appears repeatedly",
        problemLabel: "Problem first",
        noProblemTitle: "Waiting for submissions",
        noProblemDescription: "No problems need review yet.",
        problemWithIssue: "{{count}} need attention; main issue: {{issue}}.",
        problemWithoutIssue: "{{count}} need attention; inspect student details before deciding the review.",
        stable: "Stable"
      },
      metrics: {
        aria: "Assignment key metrics",
        submittedStudents: "Submitted",
        passedStudents: "Passed",
        totalSubmissions: "Attempts",
        needsAttention: "Attention",
        recentSubmissions: "Recent",
        submittedPeople: "{{count}} students"
      },
      trend: {
        eyebrow: "Submission pace",
        title: "Submission trend",
        chartAria: "Submission progress chart",
        attempts: "Attempts",
        submitted: "Students submitted",
        passed: "Students passed",
        onePoint: "{{attempts}} attempts so far. Waiting for one more data point. {{submitted}} submitted, {{passed}} passed.",
        empty: "Waiting for the first submission."
      },
      problems: {
        aria: "Problem progress list",
        eyebrow: "Problems",
        title: "Problem Progress",
        problem: "Problem",
        difficulty: "Level",
        submitted: "Submitted",
        passed: "Passed",
        attention: "Review",
        issue: "Main issue",
        action: "Action",
        view: "Open",
        noIssue: "No shared issue yet"
      },
      console: {
        stageAssignment: "Assignment Problems",
        stageProblem: "Problem Analysis",
        stageStudent: "Student Diagnosis",
        problemListHint: "Review submissions, accuracy, and shared issues before opening a problem.",
        aiPanelTitle: "AI Teaching Summary",
        aiPanelHint: "AI explains evidence; teachers still make the call.",
        priorityStudent: "Priority Student",
        priorityProblem: "Priority Problem",
        lectureSuggestion: "Review Suggestion",
        noEvidenceYet: "No submission evidence yet.",
        submittedRatio: "Submitted"
      }
    },
    teacherManagement: {
      sections: {
        classes: {
          eyebrow: "Management / Class Roster",
          title: "Class Roster",
          description: "Create default classes, and import or update student rosters."
        },
        problems: {
          eyebrow: "Management / Problem Bank",
          title: "Problem Bank",
          description: "Import problems, and maintain statements, test cases, and teaching enrichments."
        },
        aiLibrary: {
          eyebrow: "Management / AI Library",
          title: "AI Library",
          description: "Maintain skills, mistake points, and AI teaching explanation standards."
        },
        system: {
          eyebrow: "Management / System Status",
          title: "System Status",
          description: "Check AI service, model configuration, and key runtime status."
        }
      },
      classManage: {
        listAria: "Class list",
        listTitle: "Classes",
        classCount: "{{count}} classes",
        emptyTitle: "No classes yet",
        emptyDescription: "Create a default class first.",
        defaultClass: "Default class",
        summaryAria: "Current class summary",
        importAction: "Import or update roster",
        rosterCount: "{{count}} students",
        tabs: {
          aria: "Class roster workspace",
          roster: "Students",
          import: "Import records",
          settings: "Class settings"
        },
        rosterColumns: {
          student: "Student",
          number: "Student ID",
          status: "Status",
          action: "Action"
        },
        create: {
          title: "Create class",
          name: "Class name",
          grade: "Grade",
          teacher: "Teacher",
          submit: "Create"
        },
        import: {
          eyebrow: "Roster maintenance",
          currentTarget: "Import to current class",
          waiting: "Waiting for class",
          sourceTitle: "Choose roster source",
          sourceDescription: "Upload CSV/XLSX, or paste the roster in the next step.",
          fileLabel: "Roster file",
          fileNote: "CSV or XLSX",
          targetLabel: "Target class",
          pasteTitle: "Paste or review roster",
          pasteDescription: "Keep the format as class, name, and student number; preview before importing.",
          pasteLabel: "Roster content",
          pastePlaceholder: "Class,Name,Student ID\nClass 1,Alice,01",
          confirmTitle: "Preview and import",
          confirmDescription: "Preview first, then import after the roster looks right.",
          preview: "Preview roster",
          commit: "Import roster"
        }
      },
      problemManage: {
        problemCount: "{{count}} problems",
        listAria: "Problem list",
        editorTitle: "Problem editor",
        visibleCount: "{{visible}} / {{total}} problems",
        newProblem: "New problem",
        currentSort: "Current sort: {{sort}}",
        switchSort: "Switch sort, current {{sort}}",
        searchPlaceholder: "Search problem title",
        noSummary: "Problem description not completed",
        emptyTitle: "No problems yet",
        emptyFilteredTitle: "No matching problems",
        emptyDescription: "Import or create a problem.",
        emptyEditorDescription: "Import or create a problem from the left panel first.",
        emptyEditorFilteredDescription: "Adjust the search, or create a new problem.",
        paginationAria: "Problem pagination",
        previousPage: "Previous page",
        nextPage: "Next page",
        pageAria: "Page {{page}}",
        sort: {
          id: "By ID",
          difficulty: "By difficulty",
          timeLimit: "By time limit"
        },
        difficulty: {
          easy: "Basic",
          medium: "Intermediate",
          hard: "Challenge"
        },
        readOnly: {
          factsAria: "Problem limits",
          difficulty: "Difficulty",
          timeLimit: "Time limit",
          memoryLimit: "Memory limit",
          publicSamples: "Public samples",
          statementTitle: "Problem statement",
          statementDescription: "The complete statement shown to students.",
          emptyStatement: "This problem does not have a statement yet.",
          samplesTitle: "Public samples",
          samplesDescription: "Use these examples to explain the format and expected result.",
          sampleNumber: "Sample {{number}}",
          sampleInput: "Input",
          sampleOutput: "Output",
          emptyValue: "(empty)",
          noSamples: "This problem does not have public samples yet.",
          hiddenTests: "{{count}} hidden test(s) are kept out of this read-only view.",
          teachingTitle: "Teaching configuration",
          teachingDescription: "Review the knowledge, strategies, and guidance attached to this version.",
          knowledgePoints: "Knowledge points",
          algorithmStrategies: "Strategies",
          commonMistakes: "Common mistakes",
          boundaryTypes: "Boundary cases",
          aiDirection: "AI guidance",
          starterCode: "View starter code"
        },
        import: {
          title: "Import problems",
          fileLabel: "Problem file",
          fileNote: "Markdown, JSON, CSV, or XLSX",
          pasteLabel: "Paste problem",
          pastePlaceholder: "# Two Sum\n\n## Statement\n...\n\n## Sample Input\n1 2\n\n## Sample Output\n3",
          preview: "Preview",
          commit: "Import"
        }
      },
      readiness: {
        aria: "System status",
        title: "Class readiness",
        summaryLabel: "Status summary",
        aiScan: "AI configuration scan",
        scanDescription: "Check model, key, and invocation health.",
        ready: "Ready",
        blocked: "Needs action",
        degraded: "Trial ready",
        unknown: "Loading",
        canStart: "Ready to start",
        cannotStart: "Not ready for class",
        trialStart: "Trial ready",
        issueSummary: " · {{blocking}} blockers · {{warnings}} warnings",
        readyDescription: "Key services passed the checks.",
        blockedDescription: "{{count}} blocker(s) need attention.",
        degradedDescription: "{{count}} warning(s); safe to trial before polishing.",
        loadingDescription: "Loading system status.",
        classes: "Classes",
        problems: "Problems",
        library: "Library",
        statsAria: "Management data",
        refresh: "Refresh",
        runAi: "Check AI",
        running: "Checking",
        detailSummary: "{{blocking}} blockers · {{warnings}} warnings",
        checkDetails: "Check details",
        checkFail: "Fail",
        checkWarn: "Warning",
        checkPass: "Pass",
        allClear: "All key checks passed.",
        hiddenMore: "{{count}} more",
        moreCompact: "+{{count}}"
      },
      aiLibrary: {
        tabs: {
          library: "Library",
          governance: "Governance"
        },
        governance: {
          eyebrow: "Standard Library Governance",
          title: "Candidate Review Workbench",
          description: "Review AI-suggested skills and mistake points before adding them to the approved library.",
          pendingCount: "Pending {{count}}",
          refresh: "Refresh candidates",
          summaryAria: "Governance summary",
          highFrequencyPaths: "Frequent paths",
          weakPaths: "Paths to strengthen",
          noHighFrequencyPaths: "No frequent candidate paths yet.",
          noWeakPaths: "No concentrated weak paths yet.",
          emptyTitle: "No growth candidates",
          emptyDescription: "No candidates to review.",
          emptyFilteredTitle: "No matching candidates",
          emptyFilteredDescription: "Adjust status, path, or keyword filters.",
          metrics: {
            total: "Candidates",
            pending: "Pending",
            duplicates: "Duplicates",
            merged: "Merged",
            closed: "Rejected/Ignored"
          },
          filters: {
            aria: "Filter growth candidates",
            searchAria: "Search candidate name, ID, path, or evidence",
            searchPlaceholder: "Name, ID, path, evidence",
            statusAria: "Candidate status",
            allStatus: "All statuses",
            pendingOnly: "Pending only",
            clear: "Clear filters"
          },
          status: {
            proposed: "Proposed",
            needsReview: "Needs review",
            blocked: "Needs fixes",
            mergedSimilar: "Duplicate group",
            teacherApproved: "Approved",
            merged: "Merged",
            rejected: "Rejected",
            ignored: "Ignored"
          },
          evidence: {
            supported: "Evidence backed",
            noDirectCodeEvidence: "No direct code evidence",
            unsupported: "Invalid evidence",
            unknown: "Evidence pending"
          }
        }
      }
    }
  }
};

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
};

const I18nContext = createContext<I18nContextValue | null>(null);

function readLocale(): Locale {
  try {
    return localStorage.getItem(STORAGE_KEY) === "en" ? "en" : "zh";
  } catch {
    return "zh";
  }
}

function saveLocale(locale: Locale): void {
  try {
    localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // The chosen language still applies in memory when storage is blocked.
  }
}

function lookup(dictionary: TranslationDictionary, key: string): string | undefined {
  const value = key.split(".").reduce<TranslationValue | undefined>((current, part) => {
    if (!current || typeof current === "string") {
      return undefined;
    }
    return current[part];
  }, dictionary);
  return typeof value === "string" ? value : undefined;
}

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{\{(\w+)}}/g, (_, key: string) => String(params[key] ?? ""));
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(readLocale);

  useEffect(() => {
    saveLocale(locale);
    document.documentElement.lang = locale === "en" ? "en" : "zh-CN";
    document.documentElement.dataset.locale = locale;
  }, [locale]);

  const value = useMemo<I18nContextValue>(() => {
    function t(key: string, params?: Record<string, string | number>) {
      const template = lookup(localeFiles[locale], key)
        ?? lookup(localeFiles.zh, key)
        ?? lookup(dictionaries[locale], key)
        ?? lookup(dictionaries.zh, key)
        ?? key;
      return interpolate(template, params);
    }

    return { locale, setLocale: setLocaleState, t };
  }, [locale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useTranslation() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useTranslation must be used inside I18nProvider");
  }
  return context;
}
