# AI 标准库基础层与提高层重做

## 目标

把当前 AI 诊断标准库从“标签 + 提示协议 + 安全规则”的混合包，重做为面向教育诊断的知识库。标准库只沉淀典型错因、提升点、证据信号、分级提示和能力点；模型执行规则、提示安全、教师审批和运行时策略不再放入标准库。

## 范围

- 新增基础层错因库，用于定位学生代码为什么没过。
- 新增提高层提升库，用于指出学生通过当前问题后还能强化什么。
- 保留现有标签字段作为兼容索引，避免一次性改动外部模型输出校验链路。
- 调整模型提示词，不再要求模型读取 `standardLibrary.decisionProtocol`、`educationAgentProtocol` 或 `studentFeedbackRules`。
- 更新标准库测试，验证标准库不再混入流程规则。

## 标准库边界

标准库包含：

- 典型错因与提升点。
- 适用证据，例如编译错误、运行时错误、首个失败样例、代码形态、题面约束。
- L1/L2/L3 分级提示内容。
- 学生解释、教师解释和能力点。
- 适用语言与相关条目关系。

标准库不包含：

- AI 决策流程。
- 提示词执行协议。
- 安全策略。
- 教师审批流程。
- readiness、AI smoke 或部署门禁。

## 数据结构

```text
StandardLibraryPack
├── basicCauses: BasicCauseOption[]
├── improvementPoints: ImprovementPointOption[]
├── issueTags: TagOption[]                  # 兼容旧校验
├── fineGrainedTags: TagOption[]            # 兼容旧校验
├── improvementTags: ImprovementTagOption[] # 兼容旧校验
└── teachingActions: TeachingActionOption[] # 兼容旧校验
```

基础层条目包含：`id / category / name / description / studentExplanation / teacherExplanation / evidenceSignals / commonCodePatterns / judgeSignals / hintL1 / hintL2 / hintL3 / abilityPoint / severity / applicableLanguages / relatedFineTags / teachingAction`。

提高层条目包含：`id / category / name / description / whenToUse / studentBenefit / teacherExplanation / requiredEvidence / hintL1 / hintL2 / hintL3 / abilityPoint / relatedBasicCauses`。

## 验收标准

- `StandardLibraryPackBuilder` 能生成基础层与提高层条目。
- 标准库 JSON 中不再携带流程协议、安全规则、校准样例或不确定性选项。
- 基础层至少覆盖语法/编译、运行时、输入输出、变量状态、条件分支、循环边界、边界值、类型溢出、多组数据、调试残留等高频问题。
- 提高层至少覆盖复杂度、算法策略、数据结构、二分、DP、贪心、搜索、数学规律、自测反例、代码组织、AC 复盘迁移等方向。
- 现有模型输出校验仍可使用兼容标签字段。
- 相关后端测试通过。

## 风险边界

本次只重做标准库知识结构和模型输入描述，不直接实现 L4 教师审批、完整教程申请、外部模型能力评测集或前端展示改版。这些属于后续链路改造。
