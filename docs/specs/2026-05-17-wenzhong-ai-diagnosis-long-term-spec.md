# Spec: 温中 AI 诊断与学习分析长期升级路线

## 1. 定位

本 spec 是温中 AI 编程学习辅助平台的长期 AI 能力升级路线图。它不只描述某一次 prompt 优化，而是定义一套可持续演进的 AI 诊断体系，让平台从“提交后生成 AI 评语”升级为“能基于证据分析学生错因、追踪学习变化、辅助教师干预的教学智能体系统”。

一句话目标：

> 用结构化证据、标准错因库、agent 诊断链、评测集和学习轨迹，把 AI 能力从一次性点评升级为可验证、可治理、可持续增强的编程学习诊断系统。

当前对话后续关于 AI 能力增强、错因分析、学习情况分析、agent 教学流程、诊断标准库、评测集、安全提示、教师洞察等工作，默认以本 spec 为长期依据。每完成一个阶段，应回看并更新本 spec 或新增阶段性执行文档。

## 2. 背景

项目当前已经具备 AI 诊断雏形：

- `JudgeService` 负责代码执行和评测结果。
- `SubmissionAnalysisService` 负责规则诊断和基础报告。
- `AiReportService` 负责调用模型增强提交分析和成长报告。
- `DiagnosisTaxonomy` 已有基础错因标签库。
- `HintSafetyService` 已有答案泄露和提示层级保护。
- `StudentTrajectoryService` 已能基于多次提交生成学习轨迹摘要。

这些能力说明项目已经不是“裸调大模型”，而是有了 AI 教练的骨架。但它目前更接近“一次性诊断报告生成器”，距离真正的教学 agent 还有差距：

- 错因颗粒度不够细，难以区分小 bug、边界误差、算法策略错误、输出格式细节等。
- 缺少统一的诊断证据包，后续功能容易各自拼上下文。
- 规则诊断、模型诊断、标准库校验、安全校验之间边界还不够清晰。
- 缺少 AI 诊断评测集，无法客观判断改动是否真的提升。
- 学习轨迹已有摘要，但还没有形成稳定的“进步/卡点/干预建议”模型。
- 教师端可以看到统计，但还不能充分解释“为什么这个学生需要关注”。

因此，AI 能力升级不能只靠改 prompt，而要同时升级证据、标准、流程、评测和教学表达。

## 3. 长期原则

### 3.1 先证据，后判断

AI 诊断必须基于提交证据，而不是只看代码自由发挥。每次诊断应先构建证据包，再进入规则分析和模型分析。

### 3.2 程序能确定的，不交给模型猜

编译错误行号、运行栈、评测结果、首个失败测试点、代码 diff、提交次数等确定性事实，应由程序提取。模型负责归纳、教学表达和不确定性解释。

### 3.3 标签标准化，表达可变化

错因标签、能力点、提示等级、安全风险必须标准化。学生和教师看到的中文表达可以优化，但底层 ID 要稳定，方便统计和长期追踪。

### 3.4 Agent 是流程，不是单次调用

本项目的 agent 不应理解为“聊天机器人”，而应理解为一条教学诊断链：

```text
收集证据 -> 规则分析 -> 生成错因候选 -> 模型选择/解释
-> 标准库校验 -> 安全校验 -> 学生提示/教师摘要
-> 写入学习轨迹 -> 后续提交对比
```

### 3.5 评测集先行

每次改 prompt、换模型、加规则、改标签，都需要用 AI 诊断评测集验证。没有 eval 的 AI 优化只能算主观调参。

### 3.6 教育价值优先于“像 AI”

平台追求的不是 AI 说得炫，而是能帮助学生继续思考、帮助教师快速定位教学问题。默认不追求完整答案生成，不鼓励替代学生完成代码。

### 3.7 可演化，不写死

第一期不能做成后续救不回来的补丁。证据包、标签、规则、prompt、输出结构都要可扩展、可版本化、可测试。

## 4. 核心概念

### 4.1 诊断证据包 Evidence Package

一次提交进入 AI 诊断前，应整理为标准证据包。建议字段：

```text
submission:
  id
  language
  verdict
  sourceCode
  sourceCodeWithLineNumbers
  submittedAt

problem:
  id
  title
  description
  constraints
  aiPromptDirection
  expectedKnowledgeTags

judgeFacts:
  compileOutput
  runtimeErrorMessage
  caseResultsSummary
  firstFailedCase
  passedCount
  totalCount
  hiddenFailureObserved

staticSignals:
  lineIssueCandidates
  loopCount
  nestedLoopLevel
  memoryAllocationSignals
  ioSignals
  suspiciousPatterns

history:
  previousVerdict
  previousIssueTags
  recentIssueDistribution
  repeatedIssueTag
  diffSummary

policy:
  hintPolicy
  answerLeakRules
  allowedHintLevel
```

教学解释：证据包就是 AI 的“病历”。没有病历，AI 只能凭感觉开药。

### 4.2 错因标准库 Diagnosis Taxonomy

标准库用于统一 AI、教师端、学生端、统计报表和学习轨迹的语言。每个标签至少包含：

```text
id
label
studentExplanation
teacherExplanation
abilityPoint
recommendedHintPolicy
commonSignals
examples
parentTag
severity
isRuleDetectable
```

第一阶段保留现有粗粒度标签，并新增细粒度标签。旧标签用于兼容，新标签用于更精细分析。

建议扩展标签：

```text
OFF_BY_ONE：差一位错误
EMPTY_INPUT：空输入/极小输入
MAX_BOUNDARY：最大规模边界
DUPLICATE_CASE：重复元素/重复状态
OUTPUT_FORMAT_DETAIL：输出格式细节
INPUT_PARSING：输入读取理解错误
INITIAL_STATE：初始状态设置错误
STATE_RESET：多组数据或循环中的状态重置遗漏
OVER_SIMULATION：过度模拟导致复杂度过高
BRUTE_FORCE_LIMIT：暴力解法无法通过规模
GREEDY_ASSUMPTION：贪心依据不足
DP_STATE_DESIGN：状态定义不清
SAMPLE_OVERFIT：只适配样例
PARTIAL_FIX_REGRESSION：局部修复引入新问题
```

注意：标签不宜无限细分。只有满足以下至少一个条件才新增标签：

- 教师看板需要统计。
- 学生提示需要明显不同。
- 规则或模型能较稳定识别。
- 学习轨迹中有长期分析价值。

### 4.3 提示层级 Hint Policy

提示不是越多越好。建议长期稳定为四级：

```text
L1：指出问题类型，不给定位细节。
L2：给定位方向和自测建议，不给具体改法。
L3：指出局部可疑逻辑，可解释原因，但不给完整答案。
L4：给参考改法方向，仍禁止完整代码和隐藏测试点。
```

默认学生使用 L1-L2。教师可查看更完整诊断，但教师端也应标注泄题风险。

### 4.4 AI 诊断评测集 Eval Set

评测集用于判断 AI 是否真的变强。每条样例包含：

```text
problem
sourceCode
language
judgeResult
expectedTags
acceptableTags
expectedHintLevel
mustMention
mustNotMention
expectedLineIssue
teacherDiagnosisExpectation
```

评分维度：

- 错因标签是否命中。
- 是否给出合理依据。
- 是否避免泄题。
- 是否没有猜测隐藏测试点。
- 是否能区分小 bug 和算法策略错误。
- 是否能给出学生可执行的下一步。

### 4.5 学习轨迹 Learning Trajectory

学习轨迹不只是提交历史，而是对学生变化的解释：

```text
是否从语法错误进入逻辑调试
是否长期卡在同一类错因
是否从 WA 转为 TLE，说明正确性接近但复杂度不足
是否通过后仍不能解释复杂度或边界
是否频繁大改但错因不变
是否局部修复后引入新错误
```

教学解释：单次诊断告诉学生“这次错在哪里”；学习轨迹告诉老师“这个学生正在怎样学习”。

## 5. 长期架构

建议目标架构：

```text
Judge Core
  -> EvidencePackageBuilder
  -> RuleSignalAnalyzer
  -> DiagnosisCandidateGenerator
  -> ModelDiagnosisService
  -> TaxonomyValidator
  -> HintSafetyService
  -> TrajectoryAnalyzer
  -> TeacherInsightBuilder
  -> EvalRunner
```

### 5.1 EvidencePackageBuilder

职责：

- 从题目、提交、测试点、分析结果、历史提交中整理标准证据。
- 不做复杂判断，只负责收集和规整。
- 为规则诊断、模型诊断、评测集、教师看板提供统一输入。

验收：

- 同一提交在不同 AI 功能中使用同一证据结构。
- 新增字段不破坏旧消费者。
- 可以序列化为 JSON 供调试和 eval 使用。

### 5.2 RuleSignalAnalyzer

职责：

- 提取确定性或半确定性信号。
- 例如编译错误行号、运行错误行号、循环层级、疑似输出格式问题、暴力复杂度风险、状态重置风险。

验收：

- 规则结果可单独测试。
- 输出的是信号和置信度，不直接生成长篇评语。
- 规则误判时不会覆盖模型和教师判断。

### 5.3 DiagnosisCandidateGenerator

职责：

- 基于规则信号和评测事实，生成候选错因标签。
- 限制模型在候选标签和标准库内选择，减少自由发挥。

验收：

- 模型输入中包含候选标签及依据。
- 模型输出的标签必须经过标准库校验。
- 未知标签不能入库。

### 5.4 ModelDiagnosisService

职责：

- 调用大模型进行归纳、解释、教学表达。
- 输出严格结构化 JSON。
- 给出学生版本和教师版本。

验收：

- 失败时可回退到规则诊断。
- 输出包含 `confidence`、`evidenceRefs`、`uncertainty`。
- 不直接输出完整代码、隐藏测试点或最终答案。

### 5.5 TaxonomyValidator

职责：

- 校验模型输出标签是否存在。
- 合并粗粒度和细粒度标签。
- 处理低置信度和证据不足。

验收：

- 未知标签被拒绝或映射为 `NEEDS_MORE_EVIDENCE`。
- 标签解释来自标准库，而不是模型临时生成。

### 5.6 HintSafetyService

职责：

- 检查提示是否超过作业允许层级。
- 检查是否泄露完整代码、隐藏测试点、最终答案。
- 生成安全降级版本。

验收：

- 高风险原始内容不展示给学生。
- 安全检查结果记录原因。
- 教师端可看到风险等级和降级原因。

### 5.7 TrajectoryAnalyzer

职责：

- 基于多次提交分析学生学习状态。
- 输出学生下一步建议和教师干预建议。

验收：

- 能识别重复错因。
- 能识别阶段变化，如 CE -> WA -> AC。
- 能识别“进步但未完成”和“试错但未理解”的差异信号。

### 5.8 TeacherInsightBuilder

职责：

- 汇总班级、作业、题目的高频错因。
- 标出需要关注的学生和原因。
- 给出教师可行动建议。

验收：

- 教师看到的不只是标签数量，而是教学解释。
- 每个“需关注”学生都有理由。
- 低置信度 AI 诊断不会伪装成确定结论。

### 5.9 EvalRunner

职责：

- 运行 AI 诊断评测集。
- 输出命中率、安全失败、低置信情况和回归问题。

验收：

- 能在本地无真实模型时运行规则层测试。
- 有模型 key 时可运行端到端 AI eval。
- 每期 AI 改动前后有对比。

## 6. 分期路线

### 第 0 期：现状体检与 AI 评测集

目标：

- 先建立 AI 诊断考试卷，再优化 AI。
- 找出当前系统在错因命中、提示颗粒度、安全性、学习轨迹上的真实短板。

任务：

- 建立 10-20 条典型错误样例。
- 每条样例标注期望标签、禁止内容、提示等级。
- 增加规则层 eval，不依赖外部模型。
- 记录当前系统基线表现。

验收：

- 有可复跑的 eval 数据。
- 能说明当前 AI 在哪些错因上弱。
- 后续每次优化都能和基线对比。

风险：

- 样例过少会导致误判。第一版可以少，但要覆盖 CE、RE、WA、TLE、AC 复盘、隐藏测试失败。

### 第 1 期：诊断底座增强

目标：

- 建立可演化地基，提升错因颗粒度。
- 不追求完整 agent，但要避免 prompt 补丁化。

任务：

- 新增 EvidencePackageBuilder。
- 扩展 DiagnosisTaxonomy，保留旧标签并新增细粒度标签。
- 新增 RuleSignalAnalyzer 初版。
- 调整 AI prompt：模型从候选错因中选择，而不是自由发明。
- 输出结构增加 `evidenceRefs`、`fineGrainedTags`、`uncertainty`。
- 更新安全校验以适配细粒度提示。

验收：

- 对循环边界、输出格式、初始化、复杂度、样例过拟合等常见问题能给更细标签。
- 旧接口不破坏。
- 无模型 key 时仍有规则诊断可用。
- eval 分数比第 0 期基线提升。

### 第 2 期：Agent 错因推理链

目标：

- 从“一次性生成报告”升级为“按流程诊断”。

任务：

- 新增 DiagnosticAgentService 编排诊断流程。
- 明确每一步输入输出。
- 支持候选错因排序和依据记录。
- 支持模型低置信时回退到规则诊断。
- 在分析结果中保存诊断路径摘要。

验收：

- 每条 AI 诊断能解释“为什么选这个错因”。
- 低置信度结果会显示不确定性。
- 模型输出无法绕过标准库和安全校验。

### 第 3 期：学习轨迹与个性化辅导

目标：

- 从“这次提交错在哪里”升级为“这个学生正在怎样学习”。

任务：

- 增强 StudentTrajectoryService。
- 识别反复错因、阶段迁移、修复回退、疑似盲目试错。
- 增加学生下一步建议。
- 增加教师干预建议。
- 支持同一作业内跨题分析。

验收：

- 学生能看到“这次比上次改善了什么”。
- 教师能看到“为什么这个学生需要关注”。
- 轨迹结论基于提交事实和标准标签，而不是泛泛鼓励。

### 第 4 期：教师教学洞察与人工校正

目标：

- 让 AI 诊断服务教师决策，并允许教师纠偏。

任务：

- 教师端展示班级高频错因、题目卡点、学生关注原因。
- 增加 AI 诊断置信度和不确定性展示。
- 允许教师标记“诊断准确/不准确/需要调整”。
- 把教师校正沉淀为后续 eval 或规则优化素材。

验收：

- 教师能在 1-2 分钟内理解班级主要卡点。
- 教师可追溯某个统计来自哪些提交。
- 人工校正不会直接污染标准库，需要审核或规则化。

### 第 5 期：多轮 AI 教练

目标：

- 在已有诊断底座上，引入学生和 AI 的多轮引导。

任务：

- 学生可围绕某次诊断继续追问。
- AI 默认采用苏格拉底式追问，而不是直接给答案。
- 对话受 hintPolicy 控制。
- 对话可引用证据包和学习轨迹。
- 对话结果可沉淀为学习记录。

验收：

- AI 能追问学生手推样例、解释循环次数、复述题意。
- AI 不输出完整代码和隐藏测试点。
- 教师可查看学生是否通过对话完成理解推进。

### 第 6 期：题目知识建模与跨题能力画像

目标：

- 从单题诊断升级到知识点和能力点画像。

任务：

- 为题目维护知识点、算法策略、常见误区、边界类型。
- 将学生错因映射到能力点。
- 生成跨题能力画像。
- 推荐下一道练习或复盘任务。

验收：

- 平台能回答“这个学生最近主要卡在哪类能力”。
- 教师能看到班级在某个能力点上的整体薄弱情况。
- 推荐建议有依据，不只是随机推荐。

## 7. 数据与兼容策略

### 7.1 不破坏旧数据

短期内不做破坏性迁移。新增字段优先放入现有 `reportJson` 或新增兼容结构。需要表结构迁移时，先写 dry-run 和回滚方案。

### 7.2 输出版本化

AI 分析结果建议增加版本字段：

```text
analysisSchemaVersion: "diagnosis-v1"
evidenceSchemaVersion: "evidence-v1"
taxonomyVersion: "taxonomy-v1"
```

这样后续结构变化不会让旧报告无法解释。

### 7.3 粗粒度与细粒度共存

建议长期同时保存：

```text
issueTags: 粗粒度标签，兼容旧统计
fineGrainedTags: 细粒度标签，服务精细诊断
abilityPoints: 能力点
```

## 8. Prompt 策略

Prompt 不再承担全部智能，只承担模型任务说明和教学表达控制。

推荐结构：

```text
system:
  角色、输出格式、安全边界、教学原则

developer/context:
  标准标签库摘要、候选标签、提示等级

user:
  标准证据包 JSON
```

模型必须输出：

```json
{
  "headline": "...",
  "summary": "...",
  "issueTags": ["..."],
  "fineGrainedTags": ["..."],
  "evidenceRefs": ["..."],
  "studentHint": "...",
  "teacherNote": "...",
  "progressSignal": "...",
  "confidence": 0.0,
  "uncertainty": "...",
  "answerLeakRisk": "LOW",
  "lineIssues": [],
  "reportMarkdown": "..."
}
```

模型不允许：

- 发明未知标签。
- 猜测隐藏测试点具体数据。
- 输出完整代码。
- 给最终答案。
- 把不确定推断说成事实。

## 9. 教育诊断原则

### 9.1 区分事实、推断、建议

学生报告和教师报告都应尽量区分：

```text
事实：评测结果、错误日志、失败样例。
推断：可能错因、可疑代码区域。
建议：下一步手推、自测、修改方向。
```

### 9.2 鼓励最小验证

学生下一步建议应尽量落到可操作动作：

```text
手推一个 1 个元素的样例
列出循环变量变化表
检查输出末尾是否多空格
估算最大输入下核心循环次数
```

### 9.3 不替代学生思考

默认不直接给完整解法。即使教师允许更高提示等级，也要优先给“验证方法”和“局部解释”，而不是整段答案。

### 9.4 教师看到更完整，但仍需风险标记

教师端可以看到更完整的诊断依据，但要标明 AI 置信度、风险等级、不确定性和是否需要人工复核。

## 10. 风险与护栏

### 风险 A：只改 prompt，后续难维护

护栏：

- prompt 只引用标准证据包和标准库。
- 标签、规则、评测集都在代码/数据层维护。

### 风险 B：标签无限膨胀

护栏：

- 新标签必须有教学价值、统计价值或可识别信号。
- 粗粒度标签长期保留。

### 风险 C：AI 过度提示导致泄题

护栏：

- HintSafetyService 独立运行。
- 高风险内容安全降级。
- 原始风险记录仅供审计，不展示给学生。

### 风险 D：模型幻觉

护栏：

- 要求 evidenceRefs。
- 对隐藏测试点只能说“可能”，不能说具体数据。
- 低置信度标记 `NEEDS_MORE_EVIDENCE`。

### 风险 E：学习轨迹误判学生

护栏：

- 教师端表述用“建议关注”，避免给学生贴死标签。
- 轨迹结论必须显示依据，如最近几次提交、重复错因次数。

### 风险 F：第一期补丁化

护栏：

- 第一期必须先建 eval 和证据结构。
- 不把规则、标签、prompt 混在一个大方法里。
- 不做破坏性数据迁移。

## 11. 长期验收指标

### AI 诊断质量

- 常见错因标签命中率提升。
- 细粒度标签覆盖主要错误类型。
- 低置信度时能正确表达不确定性。
- 行号定位准确率提升。

### 教育有效性

- 学生下一步建议更具体。
- 学生多次提交后能看到阶段变化。
- 教师能基于看板快速找到班级卡点。
- 教师能理解某个学生为什么被建议关注。

### 安全性

- 不输出完整代码。
- 不泄露隐藏测试点。
- 不超过作业设置的提示等级。
- 高风险输出被降级并记录。

### 工程可维护性

- AI 输出结构稳定。
- 标准库可扩展。
- eval 可复跑。
- 规则、模型、安全、轨迹模块边界清晰。

## 12. 当前优先级建议

下一次真正执行代码改造时，建议从第 0 期和第 1 期合并启动，但保持两个目标清楚：

```text
先有 eval，知道当前 AI 的真实短板。
再做底座增强，确保后续 agent 能接上。
```

建议第一批落地任务：

- 新建 AI 诊断 eval 样例数据。
- 扩展 `DiagnosisTaxonomy` 的细粒度标签。
- 新建 `EvidencePackageBuilder`。
- 新建 `RuleSignalAnalyzer` 初版。
- 调整 `AiReportService` 的上下文和 JSON 输出结构。
- 增加后端测试，验证标签归一化、安全降级和规则信号。

## 13. 迭代记录

### 2026-05-17：第 0/1 期地基启动

已完成：

- 扩展 `SubmissionAnalysisResponse`，新增 schema 版本、细粒度标签、证据引用和不确定性说明字段。
- 新增 `DiagnosisEvidencePackage` 与 `DiagnosisEvidencePackageBuilder`，开始统一提交诊断证据结构。
- 新增 `RuleSignalAnalyzer`，初步识别输出格式、差一位、隐藏测试/样例过拟合、复杂度和初始化相关规则信号。
- 扩展 `DiagnosisTaxonomy`，保留旧粗粒度标签，新增 `OFF_BY_ONE`、`OUTPUT_FORMAT_DETAIL`、`SAMPLE_OVERFIT` 等细粒度标签。
- 调整 `SubmissionAnalysisService`，在模型增强前先构建证据包并合并规则信号。
- 调整 `AiReportService`，允许模型基于证据包和规则信号输出 `fineGrainedTags`、`evidenceRefs`、`uncertainty`。
- 增加规则层 eval 测试，覆盖输出格式、差一位和隐藏测试泛化不足。
- 修复中文“第5行”格式的行号解析，增强逐行纠错基础能力。

验证：

- 已运行 `./mvnw.cmd -q test`，后端测试和前端构建通过。

下一步：

- 扩充 eval 样例到 10-20 条。
- 将学习轨迹服务接入细粒度标签。
- 在教师端逐步展示“需关注原因”和细粒度错因解释。
- 后续再引入 `DiagnosticAgentService`，避免继续把流程堆在现有 service 中。

### 2026-05-17：细粒度错因进入学习分析

已完成：

- 扩展学生轨迹 DTO，增加 `repeatedFineGrainedTag`、`attentionReason` 和细粒度错因分布。
- 扩展教师作业概览 DTO，增加高频问题解释、学生最新细分卡点、重复细分卡点和关注原因。
- `StudentTrajectoryService` 开始统计 `fineGrainedTags`，并把重复细分错因转成学生下一步建议。
- `ClassroomService` 的教师概览优先展示细粒度高频错因，并给出“需关注”的原因。
- 前端类型和页面已接入新字段，学生端显示更具体卡点，教师端显示关注原因。
- 规则层 eval 扩展到输出格式、差一位、隐藏测试泛化不足、暴力复杂度、运行稳定性、通过后复盘 6 类。

验证：

- 已运行 `./mvnw.cmd -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 继续扩充 eval 到 10-20 条，覆盖状态重置、输入读取、重复元素、最大规模边界、局部修复回退。
- 抽出 `DiagnosticAgentService`，把证据构建、规则信号、模型增强、标准库校验和安全校验从现有服务中编排起来。
- 增加对旧 `reportJson` 的兼容测试，避免历史诊断在新轨迹中退化。

### 2026-05-17：任务拆解与 agent 编排启动

已完成：

- 新增长期执行清单 `docs/specs/2026-05-17-wenzhong-ai-diagnosis-tasks.md`，把长期 spec 拆成 phase/task。
- 规则层 eval 扩展到 10 条，新增输入读取、状态重置、重复元素、最大规模边界。
- 新增 `DiagnosticAgentService`，开始把证据包、规则信号、模型增强、标准库校验、安全校验集中编排。
- `SubmissionAnalysisService` 已改为调用 agent 编排层，减少核心流程继续堆在提交分析服务中。
- 新增 `DiagnosisReportReader`，统一读取新旧 `reportJson` 中的粗粒度标签、细粒度标签、学生提示和进度信号。
- 学生轨迹和教师概览改为复用 `DiagnosisReportReader`，降低旧报告兼容风险。
- 新增 `DiagnosisReportReaderTest`，覆盖新版 report、旧版 report、坏 JSON 兼容读取。

验证：

- 已运行 `./mvnw.cmd -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 为 `DiagnosticAgentService` 增加编排测试。
- 继续覆盖局部修复回退 `PARTIAL_FIX_REGRESSION`。
- 将历史提交摘要接入证据包，为“这次比上次改善了什么”做准备。

### 2026-05-17：历史证据、回退保护与改善信号

已完成：

- `DiagnosticAgentService` 增加编排测试，覆盖规则信号合并、证据引用保留、历史变更触发 `PARTIAL_FIX_REGRESSION`。
- 历史提交摘要已接入证据包，包含上次 verdict、近期粗/细错因分布、重复错因和阶段变化信号。
- agent 输出 `diagnosticTrace`，记录本次诊断链路的规则信号数量、证据引用数量、来源和模型阶段状态。
- 模型增强阶段异常或空返回时，agent 会回退到规则诊断结果，避免 AI 服务异常导致诊断不可用。
- 学生学习轨迹开始输出“这次比上次改善了什么”，并在单题任务和提交点中展示阶段变化。
- 前端类型同步 `fineGrainedTags`、`evidenceRefs`、`uncertainty`、`diagnosticTrace` 等诊断字段。
- 修复测试对 Mockito inline mock 的依赖，改为测试替身，避免 Java 24 与 Byte Buddy 兼容问题影响业务测试。

验证：

- 已运行 `./mvnw.cmd -q -Dtest=DiagnosticAgentServiceTest test`，agent 编排测试通过。
- 已运行 `./mvnw.cmd -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 为 `DiagnosisEvidencePackageBuilder` 增加单元测试，确保证据包结构继续可演进。
- 支持低置信度时自动标记 `NEEDS_MORE_EVIDENCE`。
- 开始识别“频繁大改但错因不变”。
- 输出当前 AI 诊断基线报告，把 eval 命中情况沉淀为后续对比基准。

### 2026-05-17：证据包测试、低置信度治理与试错识别

已完成：

- 新增 `DiagnosisEvidencePackageBuilderTest`，覆盖证据包 schema、提交源码行号、评测事实、隐藏用例脱敏、提示层级和历史证据保留。
- `DiagnosticAgentService` 增加低置信度治理：当 `confidence < 0.6` 时自动追加 `NEEDS_MORE_EVIDENCE` 和 `agent:low_confidence` 证据引用，并补充不确定性说明。
- 新增 `TrajectorySignalAnalyzer`，识别“连续多次大幅改代码，但主要错因不变”的学习轨迹信号。
- `StudentTrajectoryService` 接入试错识别信号，用于覆盖下一步建议、关注原因和改善信号，提醒学生停止整体重写，改用最小样例验证单个假设。
- 新增 `TrajectorySignalAnalyzerTest`，覆盖大改不变、小改不触发、错因变化不触发三种情况。

验证：

- 已运行 `./mvnw.cmd -q "-Dtest=DiagnosisEvidencePackageBuilderTest,DiagnosticAgentServiceTest,TrajectorySignalAnalyzerTest" test`，定向测试通过。
- 已运行 `./mvnw.cmd -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 扩展 eval 到 15-20 条可复跑 AI 诊断样例。
- 输出当前 AI 诊断基线报告，把规则层和 agent 层能力量化。
- 教师端展示 AI 置信度、不确定性、泄题风险。
- 设计教师校正入口的最小闭环，为后续 eval 样例沉淀做准备。

### 2026-05-18：规则层 eval 扩容与基线报告

已完成：

- `RuleSignalAnalyzerTest` 从 10 条扩展到 16 条可复跑 eval，新增覆盖编译错误、未知/等待评测、单纯 TLE verdict、单纯 MLE verdict、显式初始值风险，以及“可见输出内容不同但非空白差异”的负例。
- 新增基线报告 `docs/specs/2026-05-18-wenzhong-ai-diagnosis-baseline-report.md`，记录当前诊断链路、16 条 eval 覆盖、已具备能力、已知短板和后续指标。
- Phase 0 的“扩展到 15-20 条样例”和“输出当前 AI 诊断基线报告”已完成，后续可以把这份报告作为 AI 诊断质量对比基准。

验证：

- 已运行 `mvn -q -Dtest=RuleSignalAnalyzerTest test`，规则层 eval 通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 教师端展示 AI 置信度、不确定性、泄题风险，让老师知道哪些诊断可以直接参考，哪些需要人工复核。
- 设计教师校正入口的最小闭环，把老师改过的错因沉淀为后续 eval 候选样例。
- 支持同一作业内跨题能力摘要，从单次提交诊断逐步升级到“这个学生近期卡在哪类能力点”。
- 规划模型层 eval：有模型 key 时，对 prompt / agent 输出做回归对比，而不是只测规则层。

### 2026-05-18：教师端 AI 透明度展示

已完成：

- `DiagnosisReportReader` 增加 `confidence`、`uncertainty`、`answerLeakRisk` 读取方法，统一从结构化 `reportJson` 中读取 AI 元信息。
- `AssignmentOverviewResponse.StudentProgressSummary` 增加最新诊断置信度、不确定性、泄题风险字段。
- `ClassroomService` 在教师作业概览中带出每个学生最新一次 AI 诊断透明度。
- 教师端学生过程列表展示“置信度”和“泄题风险”标签，并在有内容时展示不确定性说明。
- 前端类型和格式化工具同步 `confidenceLabel`、`answerLeakRiskLabel`。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 设计教师校正入口的最小闭环：老师能把 AI 错因改成正确标签，并记录证据。
- 将教师校正沉淀为 eval 候选样例，形成“真实教学反馈 -> 诊断集 -> 回归验证”的闭环。
- 支持同一作业内跨题能力摘要，进入学生能力画像层。
- 规划模型层 eval，有模型 key 时对 prompt / agent 输出做回归对比。

### 2026-05-18：教师校正入口最小闭环

已完成：

- 新增 `TeacherDiagnosisCorrection`，以追加记录方式保存教师校正，不覆盖原始 AI 诊断。
- 新增教师校正 API：`POST /api/teacher/assignments/{assignmentId}/diagnosis-corrections`。
- 新增诊断标签 API：`GET /api/teacher/diagnosis-tags`，教师端从标准库读取可选错因标签。
- 教师作业概览带出最新提交 ID、最新标准错因标签和最新教师校正记录。
- 教师端学生过程列表新增“校正”入口，可选择主要错因、细分错因并填写校正理由。
- 校正默认标记为 eval 候选样例，为后续“教师反馈 -> eval 回归”做准备。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=ClassroomServiceCorrectionTest test`，教师校正边界测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 支持同一作业内跨题能力摘要，把单个学生多题错因聚合成能力画像。
- 将教师校正样例导出/汇总为可复跑 eval 数据，进一步闭合人工反馈链路。
- 规划模型层 eval，有模型 key 时对 prompt / agent 输出做回归对比。

### 2026-05-18：同一作业内跨题能力摘要

已完成：

- 新增 `AbilitySignalAnalyzer`，将已有诊断标签映射到标准库中的能力点，并按最近提交聚合作业内能力信号。
- 能力摘要优先分析未通过提交；如果没有失败证据，则回退到通过后复盘信号，避免把 AC 后建议误判为失败能力缺口。
- 能力信号输出 `abilityPoint`、涉及题目数、涉及提交数和证据标签，保证教师和学生看到的摘要可追溯。
- `StudentTrajectoryResponse` 增加 `primaryAbilityFocus`、`crossProblemSummary`、`abilitySummary`。
- `AssignmentOverviewResponse.StudentProgressSummary` 增加同样的能力摘要字段，并在多题集中同一能力点时纳入需关注判断。
- 学生端“提交记录”展示能力摘要；教师端学生过程列表展示能力焦点和跨题说明。
- 新增 `AbilitySignalAnalyzerTest`，覆盖跨题聚合、通过后复盘回退、未知标签降级为证据不足/问题定位。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=AbilitySignalAnalyzerTest,ClassroomServiceCorrectionTest test`，能力摘要与教师校正定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 教师端展示每个高频问题的解释和建议干预方式，让“看见问题”进一步变成“知道怎么教”。
- 教师端展示需关注学生的证据链，把能力摘要背后的题目、提交、标签串起来。
- 将教师校正样例导出/汇总为可复跑 eval 数据，进入真实教学反馈闭环。

### 2026-05-18：教师端高频问题解释与干预建议

已完成：

- `AssignmentOverviewResponse.IssueStat` 增加 `abilityPoint`、`recommendedHintPolicy`、`interventionSuggestion`。
- 教师作业概览的高频问题不再只返回标签名称，而是从 `DiagnosisTaxonomy` 带出教师解释、能力点和建议提示层级。
- 为常见错因补充稳定干预建议，例如循环边界引导学生列循环变量表，输入读取错误引导学生圈出题面输入结构，复杂度问题先估算循环次数。
- 教师端“高频问题”列表展示解释、能力点、提示层级和干预建议，让老师从“看见问题”进入“知道怎么教”。
- `ClassroomServiceCorrectionTest` 增加概览测试，确认高频问题字段完整输出。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=ClassroomServiceCorrectionTest,AbilitySignalAnalyzerTest test`，定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 教师端展示需关注学生的证据链，把“为什么关注这个学生”展示到题目、提交、错因证据层。
- 将教师校正样例导出/汇总为 eval 数据，进入真实教学反馈闭环。
- 规划模型层 eval，有模型 key 时对 prompt / agent 输出做回归对比。

### 2026-05-18：需关注学生证据链

已完成：

- `AssignmentOverviewResponse.StudentProgressSummary` 增加 `attentionEvidence`。
- 证据链限制为最近关键提交，包含提交 ID、题目 ID、评测结果、提交时间、粗/细错因、能力点、诊断标题和关注理由。
- 证据链生成逻辑优先保留最近未通过提交，同时关联重复错因和跨题能力点，避免只给“需关注”标签而没有依据。
- 教师端学生过程列表展示最多 3 条证据，老师能直接看到“什么时间、什么错因、为什么关注”。
- 现有概览测试同步校验证据链字段，确保教师洞察可追溯。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=ClassroomServiceCorrectionTest,AbilitySignalAnalyzerTest test`，定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 将教师校正样例导出/汇总为 eval 数据，让真实教学反馈能进入回归测试。
- 规划模型层 eval，有模型 key 时对 prompt / agent 输出做回归对比。
- 后续进入多轮 AI 教练设计，让 AI 基于证据链追问，而不是直接给答案。

### 2026-05-18：教师校正 eval 候选汇总 API

已完成：

- 新增 `DiagnosisEvalCandidateResponse`，用于承载教师校正沉淀出的 eval 候选样例。
- `TeacherDiagnosisCorrectionRepository` 增加按作业查询 `evalCandidate=true` 校正记录。
- 新增教师端 API：`GET /api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates`。
- eval 候选样例包含校正 ID、提交 ID、学生 ID、题目 ID、评测结果、AI 原始错因、教师修正错因、教师备注、原诊断标题、诊断来源、场景和源码预览。
- 源码只返回预览，避免概览接口携带过大内容；后续如需完整离线 eval 文件，可在此 API 基础上增加受控导出。
- `ClassroomServiceCorrectionTest` 增加 eval 候选汇总测试，确认真实教学反馈能被聚合为可复跑样例材料。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=ClassroomServiceCorrectionTest test`，教师校正与 eval 候选定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 规划模型层 eval：有模型 key 时对 prompt / agent 输出做回归对比。
- 设计诊断后追问流程，让多轮 AI 教练引用证据包、学习轨迹和教师校正经验。

### 2026-05-18：模型层 eval 最小框架

已完成：

- 新增 `ModelDiagnosisEvalTest`，建立可选 live model eval 入口。
- 固化 3 条模型层诊断样例：差一位循环边界、输出格式细节、暴力复杂度瓶颈。
- 每条样例包含题目、提交、评测事实、baseline、期望粗/细错因标签。
- live eval 通过 `AI_EVAL_API_KEY` 显式开启；未设置 key 时自动跳过真实模型调用，不影响日常本地测试。
- 支持通过 `AI_EVAL_BASE_URL`、`AI_EVAL_MODEL` 覆盖模型地址和模型名。
- live eval 断言模型增强后的 agent 输出仍命中预期错因、保留证据引用、泄题风险不为高，并带有 agent trace。
- 增加本地结构测试，确保 eval 样例本身稳定可复用。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=ModelDiagnosisEvalTest test`，本地结构测试通过，live 模型测试因未设置 `AI_EVAL_API_KEY` 按设计跳过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

下一步：

- 扩展模型层 eval 样例来源：从教师校正 eval 候选 API 中挑选真实样例。
- 设计诊断后追问流程，让 AI 教练基于证据包、学习轨迹和 hintPolicy 追问学生。

### 2026-05-18：诊断后追问最小闭环

已完成：

- 新增 `CoachPrompt`，保存每次诊断后追问，不让追问只停留在一次性前端文案。
- 新增 `CoachPromptRepository`、`CoachPromptService`、`CoachPromptResponse`。
- 新增学生提交追问 API：`GET /api/submissions/{id}/coach-prompt` 和 `POST /api/submissions/{id}/coach-prompt`。
- 追问生成基于当前提交、结构化诊断标签和作业 `hintPolicy`，默认采用苏格拉底式下一问。
- 不同错因生成不同定位问题：循环边界要求手推最小样例，输入读取要求逐行对题面，复杂度问题要求估算循环次数，通过后要求复盘边界与复杂度。
- 追问记录保存提交 ID、作业 ID、学生 ID、hintPolicy、追问类型、问题、生成理由和证据引用。
- 学生端“本次反馈”区域新增“下一问”卡片，可生成或再次生成追问。
- 新增 `CoachPromptServiceTest`，覆盖失败提交定位追问和通过提交复盘追问。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=CoachPromptServiceTest test`，追问服务定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

剩余风险：

- 当前追问为规则/模板生成，尚未真正引入模型多轮对话。
- 当前追问引用的是当前提交和诊断标签，尚未完整引用学生轨迹、证据包详情和教师校正经验。

下一步：

- 扩展追问上下文，让对话引用证据包和学习轨迹。
- 设计学生回答后的二次追问与记录结构，形成真正多轮教练。

### 2026-05-18：追问引用诊断证据与学习轨迹摘要

已完成：

- `CoachPrompt` 增加 `contextSummary`，单独保存追问上下文摘要，避免和证据引用 JSON 混在一起。
- `DiagnosisReportReader` 增加 `evidenceRefs` 读取方法，从结构化诊断结果中取出模型/规则引用的证据。
- `CoachPromptService` 生成追问时会合并提交 ID、诊断场景、当前标签和诊断 `evidenceRefs`。
- 追问服务会查询同一作业、同一学生最近 5 次提交，提取重复粗/细错因和最近评测阶段变化。
- 追问的 `rationale` 和 `contextSummary` 会说明“为什么现在问这个问题”，例如最近多次出现同一细分卡点、最近两次仍停留在同一评测阶段。
- 学生端“下一问”卡片展示上下文摘要，让学生知道追问不是凭空出现的。
- `CoachPromptServiceTest` 增加证据引用和重复卡点断言。

验证：

- 已运行 `npm run typecheck`，前端类型检查通过。
- 已运行 `mvn -q -Dtest=CoachPromptServiceTest,DiagnosisReportReaderTest test`，追问与诊断读取定向测试通过。
- 已运行 `mvn -q test`，后端测试、前端类型检查和前端构建通过。

剩余风险：

- 当前引用的是诊断证据引用 ID 和学习轨迹摘要，尚未把完整 `DiagnosisEvidencePackage` 内容持久化后供追问直接读取。
- 仍然是一问式教练，学生回答后的二次追问还未落库。

下一步：

- 设计学生回答结构和二次追问 API。
- 规划完整证据包持久化或可重建机制，让多轮教练能引用更细的评测事实。

## 14. 长期协作约定

后续本对话继续做 AI 能力升级时，默认遵守：

- 每次动核心 AI 前先说明影响范围和验证方式。
- 小步迭代，不一次性重写。
- 每期结束后更新验收结果和剩余风险。
- 用户不需要先懂所有 agent 和教育诊断术语；实现过程中持续解释关键概念。
- 如果发现当前分期不合理，可以调整路线，但不能牺牲证据包、标准库、安全校验和 eval 这四个地基。

## 15. 一句话总结

温中平台的 AI 能力升级，不是把 prompt 写长，也不是换一个更强模型，而是建立一套可验证的教学诊断系统：

```text
证据包让 AI 有事实，
标准库让 AI 不乱说，
agent 链让 AI 会诊断，
安全层让 AI 不泄题，
评测集让 AI 能持续变强，
学习轨迹让 AI 真正服务教育。
```
