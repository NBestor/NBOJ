# 温中 AI 诊断基线报告

日期：2026-05-18

## 1. 目的

本报告记录当前 AI 诊断底座的可复跑基线。它不是最终质量结论，而是后续优化 prompt、规则、agent、学习轨迹和教师洞察时的对照点。

当前基线以规则层 eval 为主，覆盖无需真实模型 key 的确定性诊断能力。模型端能力、真实提交数据表现和教师校正反馈将在后续阶段纳入。

## 2. 当前结构

已形成的诊断链路：

```text
Submission / Problem / Judge Facts
-> DiagnosisEvidencePackageBuilder
-> RuleSignalAnalyzer
-> DiagnosticAgentService
-> AiReportService
-> DiagnosisTaxonomy
-> HintSafetyService
-> StudentTrajectoryService / Teacher Overview
```

当前已具备：

- 统一证据包 `DiagnosisEvidencePackage`
- 粗粒度错因 `issueTags`
- 细粒度错因 `fineGrainedTags`
- 证据引用 `evidenceRefs`
- 不确定性说明 `uncertainty`
- 诊断路径摘要 `diagnosticTrace`
- 低置信度降级为 `NEEDS_MORE_EVIDENCE`
- 学习轨迹中的阶段变化、修复回退、频繁大改但错因不变识别

## 3. Eval 覆盖

当前规则层 eval 共 16 条。

| 编号 | 场景 | 期望命中 |
|---|---|---|
| R01 | 可见输出仅空白差异 | `IO_FORMAT`, `OUTPUT_FORMAT_DETAIL` |
| R02 | Python `range(n - 1)` 差一位 | `LOOP_BOUNDARY`, `OFF_BY_ONE` |
| R03 | 样例通过但隐藏测试失败 | `SAMPLE_ONLY`, `SAMPLE_OVERFIT` |
| R04 | 多层循环导致复杂度风险 | `TIME_COMPLEXITY`, `BRUTE_FORCE_LIMIT` |
| R05 | 运行错误 | `RUNTIME_STABILITY` |
| R06 | 已通过后的复盘检查 | `GENERALIZATION_CHECK` |
| R07 | 输入读取结构可疑 | `IO_FORMAT`, `INPUT_PARSING` |
| R08 | 循环内状态重置 | `VARIABLE_INITIALIZATION`, `STATE_RESET` |
| R09 | 去重结构导致重复元素风险 | `DUPLICATE_CASE` |
| R10 | 最大规模内存边界 | `SPACE_COMPLEXITY`, `MAX_BOUNDARY` |
| R11 | 编译错误 | `SYNTAX_ERROR` |
| R12 | 未知/等待评测 | `NEEDS_MORE_EVIDENCE` |
| R13 | 单纯超时 verdict | `TIME_COMPLEXITY`, `BRUTE_FORCE_LIMIT` |
| R14 | 单纯超内存 verdict | `SPACE_COMPLEXITY` |
| R15 | 显式初始值风险 | `VARIABLE_INITIALIZATION`, `INITIAL_STATE` |
| R16 | 可见输出内容不同但非空白差异 | 不应误判为 `IO_FORMAT` |

## 4. 当前基线结果

本轮验证命令：

```text
.\mvnw.cmd -q "-Dtest=RuleSignalAnalyzerTest" test
.\mvnw.cmd -q test
```

验收口径：

- 规则层 eval 必须全部通过。
- 正例必须命中期望粗粒度或细粒度标签。
- 负例必须避免明显误判。
- 全量测试必须通过，包含前端类型检查和前端构建。

当前状态：

- 规则层 eval：16 条，全部纳入自动化测试。
- Agent 编排测试：覆盖规则合并、局部修复回退、模型失败回退、低置信度标记。
- 证据包测试：覆盖证据结构、隐藏用例脱敏、历史证据保留。
- 学习轨迹测试：覆盖频繁大改但错因不变。

## 5. 已知短板

当前基线仍有明显边界：

- 规则层只能提供候选信号，不能完全还原学生错误思路。
- 还没有真实模型输出的端到端 eval，因此不能量化 prompt 和模型诊断质量。
- 还没有教师人工校正数据，无法评估错因标签在真实课堂中的准确率。
- `RuleSignalAnalyzer` 当前偏启发式，对复杂算法策略错误、DP 状态设计、贪心反例等识别仍较弱。
- 学习轨迹已有阶段变化和试错识别，但还没有跨题能力画像。
- 教师端还未展示完整的置信度、不确定性和泄题风险。

## 6. 下一步指标

下一阶段建议把 eval 扩展为两层：

```text
规则层 eval：继续保证确定性信号不退化。
模型层 eval：在有模型 key 时验证结构化输出、错因命中、泄题风险和教学表达。
```

建议新增指标：

- `issueTagHitRate`：粗粒度错因命中率
- `fineTagHitRate`：细粒度错因命中率
- `evidenceRefCoverage`：输出是否引用真实证据
- `safetyPassRate`：是否避免完整代码、隐藏测试点和最终答案
- `uncertaintyQuality`：低证据场景是否表达不确定性
- `teacherActionability`：教师能否据此决定干预动作

## 7. 结论

当前 AI 诊断能力已经从“一次性 AI 点评”进入“证据包 + 标准库 + agent 编排 + 学习轨迹”的阶段。

最重要的变化是：系统开始知道自己为什么判断、依据是什么、什么时候不确定、学生是否在反复试错。下一阶段重点应从继续堆规则，转向建立模型层 eval 和教师校正闭环。
