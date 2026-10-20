# Coach 回答质量分析升级

## 目标

本轮 AI 革新的目标是把系统从“学生是否回答了 AI 追问”升级为“学生回答是否形成了可验证学习证据”。

当前系统已经能生成错因诊断、学习轨迹、干预计划、coach 追问和教师优先级信号，但对学生回答质量的判断仍偏粗。仅统计“已回答”不足以判断学生是否真正理解了提示、是否能把提示转化为可验证动作。

## 新增教育状态变量

新增 `coachAnswerQualitySignal`，用于描述学生最近一次 coach 回答的质量。

字段建议：

- `qualityLevel`：回答质量等级。
- `qualityLabel`：面向教师和学生的中文标签。
- `evidenceTypes`：学生回答中出现的证据类型。
- `missingEvidence`：仍缺少的关键证据。
- `summary`：对回答质量的解释。
- `nextCoachMove`：下一轮 coach 应该采取的动作。
- `needsTeacherAttention`：是否需要教师关注。

## 质量等级

- `NO_ANSWER`：没有回答。
- `VAGUE_ACK`：只是表示知道、会改、试试，没有证据。
- `DIRECTION_ONLY`：有方向，但没有样例、变量、复杂度、反例或提交对比。
- `EVIDENCE_GROUNDED`：给出了可验证证据，例如最小样例、输入输出对照、变量轨迹、复杂度估算、反例或提交差异。
- `TRANSFER_READY`：能解释边界、规律、不变量、复杂度或迁移条件，适合进入通过后复盘。

## 证据类型

- `MIN_CASE`：最小样例或边界输入。
- `EXPECTED_ACTUAL_COMPARE`：预期与实际输出对照。
- `VARIABLE_TRACE`：变量、循环或状态变化追踪。
- `COMPLEXITY_ESTIMATE`：复杂度或操作次数估算。
- `COUNTEREXAMPLE`：反例或样例外场景。
- `SUBMISSION_DIFF`：提交差异或改动对比。
- `GENERALIZATION`：规律、不变量、复杂度或迁移解释。

## 落地范围

1. 新增 `CoachAnswerQualityAnalyzer`，从学生回答文本中提取回答质量与证据类型。
2. 扩展 `CoachInteractionSummaryResponse`，输出最近回答质量信号。
3. 在 `CoachInteractionAnalyzer` 中汇总每个提交的最新回答质量。
4. 在 `TeacherActionPriorityAnalyzer` 中把低质量回答纳入教师优先级判断。
5. 增加单元测试，覆盖空回答、空泛回答、有方向无证据、最小样例、变量轨迹、复杂度估算、反例、迁移复盘。

## 验收标准

- 系统能区分“回答了”和“回答有证据”。
- `VAGUE_ACK`、`DIRECTION_ONLY` 这类低质量回答能触发后续补证据或教师关注。
- `EVIDENCE_GROUNDED` 能引导下一轮进入最小修改或验证。
- `TRANSFER_READY` 能用于通过后复盘和迁移。
- 不修改数据库结构，优先通过 DTO 和分析器增量输出。
- 原有 AI 诊断、轨迹、coach、教师优先级相关测试不下降。

## 风险边界

- 本轮不改变模型 API 调用方式。
- 本轮不迁移数据库。
- 本轮不改前端页面，只让后端 DTO 先具备结构化信号。
- 本轮不把回答质量作为唯一教学判断，只作为观察性信号参与教师优先级。
