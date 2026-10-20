package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateRegistryTest {

    private final PromptTemplateRegistry registry = new PromptTemplateRegistry();

    @Test
    void registersStandardLibraryNavigationAdviceAndDiagnosisReportPrompts() {
        PromptTemplateRegistry.PromptTemplate freeDiagnosis =
                registry.get(PromptTemplateRegistry.FREE_DIAGNOSIS_V1);
        PromptTemplateRegistry.PromptTemplate freeDiagnosisV2 =
                registry.get(PromptTemplateRegistry.FREE_DIAGNOSIS_V2);
        PromptTemplateRegistry.PromptTemplate navigation =
                registry.get(PromptTemplateRegistry.STANDARD_LIBRARY_NAVIGATION_V1);
        PromptTemplateRegistry.PromptTemplate advice =
                registry.get(PromptTemplateRegistry.DIAGNOSIS_AND_ADVICE_V1);
        PromptTemplateRegistry.PromptTemplate report =
                registry.get(PromptTemplateRegistry.DIAGNOSIS_REPORT_V2);
        PromptTemplateRegistry.PromptTemplate reportV3 =
                registry.get(PromptTemplateRegistry.DIAGNOSIS_REPORT_V3);
        PromptTemplateRegistry.PromptTemplate reportV4 =
                registry.get(PromptTemplateRegistry.DIAGNOSIS_REPORT_V4);

        assertThat(freeDiagnosis.getStage()).isEqualTo("FREE_DIAGNOSIS");
        assertThat(freeDiagnosis.getSystemPrompt())
                .contains("free-diagnosis-v1")
                .contains("不受标准库候选影响")
                .contains("禁止接收或猜测标准库 ID")
                .contains("problemUnderstanding")
                .contains("issues")
                .contains("navigationIntent")
                .contains("不能写数据库 ID");
        assertThat(freeDiagnosisV2.getSystemPrompt())
                .contains("free-diagnosis-v2")
                .contains("brief.testIntentFacts")
                .contains("不能直接证明某个代码错因")
                .contains("judge:test-intent:<semantic-code>")
                .contains("不得猜测、复述或反推隐藏输入")
                .contains("至少还要引用代码行或可见行为差距");

        assertThat(navigation.getStage()).isEqualTo("STANDARD_LIBRARY_NAVIGATION");
        assertThat(navigation.getSystemPrompt())
                .contains("standard-library-navigation-v1")
                .contains("大章节 -> 小章节 -> 知识点")
                .contains("知识点下面是诊断层")
                .contains("currentLayer")
                .contains("allowedActions")
                .contains("\"action\": \"SELECT\"|\"DONE\"|\"NO_MATCH\"")
                .contains("codes")
                .contains("不要自己创造正式标准库 ID")
                .doesNotContain("selectedBranches")
                .doesNotContain("selectedPaths")
                .doesNotContain("unresolvedGaps");

        assertThat(advice.getStage()).isEqualTo("DIAGNOSIS_AND_ADVICE");
        assertThat(advice.getSystemPrompt())
                .contains("complete diagnosis and advice generation stage")
                .contains("standardLibrary.knowledgeGroups")
                .contains("main structure")
                .contains("knowledge point -> skill unit -> mistake point / improvement point")
                .contains("applicationScenarios")
                .contains("not legal diagnosis ids or submission evidence")
                .contains("If a scenario conflicts with the current code or judge facts, ignore the scenario")
                .contains("two parallel libraries")
                .contains("teaching reference pack")
                .contains("Return one advice item per independent evidence-backed issue")
                .contains("caseUnderstanding")
                .contains("basicLayerAdvice")
                .contains("improvementLayerAdvice")
                .contains("nextStepPlan")
                .contains("studentSummary")
                .contains("Do not provide complete code");

        assertThat(report.getStage()).isEqualTo("DIAGNOSIS_REPORT");
        assertThat(report.getSystemPrompt())
                .contains("diagnosis report v2")
                .contains("单诊断 Agent")
                .contains("不要在中文解释里使用英文双引号")
                .contains("标准库的作用")
                .contains("参考信号，用来验证诊断")
                .contains("problem.description 是完整题目描述")
                .contains("submission.sourceCodeWithLineNumbers 是完整学生代码")
                .contains("visibleCaseFacts")
                .contains("runtimeErrorMessage")
                .contains("compileOutput")
                .contains("统一知识树下的诊断层")
                .contains("不是无关全库倾倒")
                .contains("standardLibrary.knowledgeGroups")
                .contains("standardLibrary.applicationScenarios")
                .contains("不是合法诊断 id 列表")
                .contains("与代码或判题事实冲突时必须忽略场景")
                .contains("不得反过来从场景猜测 issue")
                .contains("知识点 → 能力点 → 易错点 / 提升点")
                .contains("不要把知识树和标准库理解成两套平行库")
                .contains("知识树回答")
                .contains("能力点回答")
                .contains("易错点回答")
                .contains("兼容列表")
                .contains("教学参考规范包")
                .contains("课程标准和教案目录")
                .contains("不是强制答案表")
                .contains("结构邻域")
                .contains("不是唯一答案来源")
                .contains("不能限制你对题目、代码和判题结果的整体判断")
                .contains("先像老师一样自由判断真实诊断候选")
                .contains("没有精确匹配时使用 null、PARTIAL、MISS 或 OUT_OF_LIBRARY")
                .contains("diagnosisCandidates")
                .contains("不是思维链")
                .contains("每个 diagnosisCandidates 项都必须带 libraryPath")
                .contains("每个 diagnosisCandidates 项都必须引用 evidenceRefs")
                .contains("libraryGrowth.candidates 应来自 diagnosisCandidates")
                .contains("status 必须是 NEEDS_REVIEW")
                .contains("不要只因输出偏大或偏小就猜初始化")
                .contains("studentReport")
                .contains("basicLayerText")
                .contains("improvementLayerText")
                .contains("nextActionText")
                .contains("学生优先阅读的自然反馈")
                .contains("后端 metadata")
                .contains("只服务审计、质量评测、教师复核和标准库成长")
                .contains("不要把 diagnosisDecision、diagnosisCandidates、teacherTrace 或 libraryGrowth 的字段名写给学生")
                .contains("先讲人话")
                .contains("基础层优先")
                .contains("题目约束和代码策略不一致")
                .contains("不要直接跳到新算法名")
                .contains("错误策略")
                .contains("120-220 个中文字符")
                .contains("80-180 个中文字符")
                .contains("OUT_OF_LIBRARY")
                .contains("HIT")
                .contains("PARTIAL")
                .contains("MISS")
                .contains("visibleCaseFacts[].inputPreview")
                .contains("actualOutputPreview")
                .contains("expectedOutputPreview")
                .contains("不能把输出当输入")
                .contains("禁止出现自己编的具体数组")
                .contains("只描述反例形态")
                .contains("不要写出可见样例的完整最优组合")
                .contains("不要直接给替代 DP 的状态定义或转移教程")
                .contains("隐藏测试失败时")
                .contains("不要直接指出隐藏情况下代码具体漏比较了哪些位置或字符")
                .contains("禁止给完整代码")
                .contains("不要写出替换表达式或精确循环头")
                .contains("C++ 类型与精度问题")
                .contains("不要写出强制转换表达式")
                .contains("不要把学生变量的具体修正值或初始化位置直接写出来")
                .contains("初始化位置")
                .contains("显式清空全局状态")
                .contains("studentReport 三个字段必须是普通单行字符串");

        assertThat(reportV3.getStage()).isEqualTo("DIAGNOSIS_REPORT");
        assertThat(reportV3.getSystemPrompt())
                .contains("diagnosis-report-v3")
                .contains("自由诊断 issues")
                .contains("可选标准库挂接结果")
                .contains("standardLibrary")
                .contains("libraryAnchors")
                .contains("navigationResult")
                .contains("studentReport")
                .contains("libraryGrowth.candidates")
                .contains("NEEDS_REVIEW")
                .contains("每个学生可见判断都要有证据引用")
                .contains("检查什么现象 + 怎么手推验证")
                .contains("必须绑定当前 issue")
                .contains("nextStepPlan 只能返回 1 条")
                .contains("学生可见字段禁止出现“直接改成”“替换为”“完整代码”");
        assertThat(reportV4.getSystemPrompt())
                .contains("diagnosis-report-v4")
                .contains("brief.testIntentFacts")
                .contains("不是预先计算的错因")
                .contains("不得因测试点映射而改写真实诊断")
                .contains("testIntentFacts 不能单独证明错因")
                .contains("hidden=true 的测试语义不得被原样复述给学生");
    }

    @Test
    void oldPromptVersionsAreNotResolvable() {
        assertThatThrownBy(() -> registry.get("diagnosis-and-" + "teaching-v3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown prompt template version");
        assertThatThrownBy(() -> registry.get("diagnosis-" + "judge-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown prompt template version");
        assertThatThrownBy(() -> registry.get("teaching-" + "hint-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown prompt template version");
    }

    @Test
    void advicePromptDoesNotExposeLegacyContracts() {
        String prompt = registry.get(PromptTemplateRegistry.DIAGNOSIS_AND_ADVICE_V1).getSystemPrompt();

        assertThat(prompt)
                .doesNotContain("diagnosisDecision")
                .doesNotContain("teachingHint")
                .doesNotContain("Combined" + "Output")
                .doesNotContain("AiAnalysis" + "Payload");
    }
}
