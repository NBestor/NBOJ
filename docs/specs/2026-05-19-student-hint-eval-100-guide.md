# Guide: 100 条学生提示评测集

## 目的

这批评测集用于检查后端 AI 诊断链路是否能对学生错误代码生成稳定、可教学、低泄题风险的结构化提示。它不替代真实教师校正数据，而是作为长期回归集，帮助我们在改提示词、agent 编排或标准库时确认能力没有退化。

## 文件

```text
online-judge/src/test/resources/diagnosis-eval-fixtures/student-hint-cases.json
online-judge/scripts/generate-student-hint-eval-fixtures.mjs
online-judge/src/test/java/com/onlinejudge/submission/application/StudentHintEvalFixtureLoader.java
online-judge/src/test/java/com/onlinejudge/submission/application/StudentHintEvalFixtureTest.java
```

## 覆盖范围

当前数据包含 100 条样例，按 20 个错误主题平均分布，每个主题 5 条。主题包括：

- 循环终点差一位
- 下标越界差一位
- 查询输入读取错误
- 多组数据读取错误
- 输出空格/换行格式错误
- 循环内重复扫描
- 双重循环规模瓶颈
- 逐步模拟超时
- 最大规模边界忽略
- 重复元素被错误去重
- 初始值不符合数据范围
- 多组数据状态未重置
- DP 状态定义缺信息
- 状态转移来源遗漏
- 贪心假设缺少证明
- 样例过拟合
- 空输入/极小输入崩溃
- 除零等运行稳定性问题
- 不必要的大空间结构
- 局部修复后回退

每条样例包含题目、错误代码、评测结果、可见/隐藏用例摘要、预期错因标签、预期教学动作、必须提到的诊断方向、禁止出现的泄题短语，以及学生误解和期望下一步动作。

部分样例允许多个合理教学动作。例如下标越界既可以用 `TRACE_VARIABLES` 引导学生追踪循环变量，也可以用 `CHECK_RUNTIME_GUARDS` 先排查运行稳定性。评测用 `acceptableTeachingActions` 表示这些等价入口，避免把合理教学路径误判为失败。

## 如何生成

```powershell
node online-judge/scripts/generate-student-hint-eval-fixtures.mjs
```

生成器会自检：

- 必须正好 100 条。
- 名称不可重复。
- 至少 20 个错误主题。
- 每个错误主题正好 5 条。
- 每条必须有题面、源码、预期标签、教学动作、禁泄题短语、学生误解和下一步动作。

## 如何运行

只跑这批评测：

```powershell
mvn -q -Dexec.skip=true -Dtest=StudentHintEvalFixtureTest test
```

跑 AI 相关回归组合：

```powershell
mvn -q -Dexec.skip=true -Dtest=StudentHintEvalFixtureTest,ModelDiagnosisEvalTest,DiagnosticAgentServiceTest,HintSafetyServiceTest test
```

`-Dexec.skip=true` 用于跳过前端构建，只验证后端 AI 诊断能力。

## 当前验收口径

无外部模型 key 时，测试验证的是后端 agent 管线：

- JSON 数据质量和分布。
- fixture 能转成 `Problem`、`Submission`、`SubmissionCaseResult` 和 baseline。
- `DiagnosticAgentService` 能为 100 条样例产出非空 `studentHintPlan`。
- `studentHintPlan` 必须包含提示层级、问题类型、证据锚点、下一步动作、教练追问、教学动作和泄题风险。
- 学生可见文本不得包含 `完整代码`、`参考答案`、`隐藏测试点`。
- 标签、教学动作和证据引用需要达到最低命中率。

有 `AI_EVAL_API_KEY` 时，后续可以继续扩展为真实模型 live eval，检查模型输出是否命中同一批黄金样例。

## 2026-05-20 优化记录

第一版评测只验证“带金标 baseline 时能产出结构化提示”，容易高估能力。现在新增 blind eval：baseline 不再携带预期错因标签，只保留评测事实、源码、题面和历史信号，让后端 agent 自己判断。

本轮同时增强了规则信号：

- 结合题面和源码识别 `range(1, n)` 漏掉 n。
- 识别多组数据/多查询题面与单次读取代码之间的不一致。
- 识别大规模题目中的逐步模拟和线性循环。
- 识别 DP 状态设计、贪心假设、空输入、状态重置和大矩阵空间风险。
- 调整主错因选择，避免泛化的 `INPUT_PARSING` 抢走 DP、贪心、复杂度等更关键教学主线。

当前 blind eval 结果：

```text
issueTags=100/100
fineTags=100/100
teachingActions=100/100
evidenceRefs=100/100
```

这个结果说明本地规则/agent 诊断底座在这 100 条合成样例上已经稳定。它不等同于真实大模型满分；真实模型质量仍需要 `AI_EVAL_API_KEY` 开启 live eval 后验证。
