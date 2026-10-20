# Spec: AI 学生提示第一轮稳态优化

## Why

当前项目已经具备证据包、错因标签、模型增强、提示安全检查、多轮教练和教师质量闭环，但学生端提示仍主要依赖单个 `studentHint` 字符串。这个形态容易出现两个问题：提示表达不稳定，以及信息堆叠后学生不知道下一步先做什么。

本轮优化的目标不是重写 AI，也不是大改学生端界面，而是在不破坏现有链路的前提下，为每次诊断补充一个结构化“学生提示计划”。它把提示拆成提示层级、问题类型、证据锚点、下一步动作、教练追问和教学动作，作为后续前端展示、评测集和教师分析的统一底座。

## Goals

- 保留旧 `studentHint`，新增兼容式结构化提示对象。
- 让规则回退和模型增强都能产出同一种学生提示骨架。
- 把错因标准库扩展为“错因标签 -> 教学动作”的映射。
- 让安全检查覆盖结构化提示对象，避免新字段绕过防泄题规则。
- 补充定向测试，验证旧数据兼容、低风险回退和结构化字段读取。

## Non-goals

- 不在本轮重做学生端 UI。
- 不删除或重命名旧 `studentHint` 字段。
- 不改变数据库表结构；结构化提示继续保存在 `reportJson`。
- 不直接扩到 100 条评测集；先把输出协议稳定下来。
- 不声称当前追问效果具备严格因果证明。

## Impact

- Frontend: 暂不强制改动；前端可继续读旧字段。
- Backend: 扩展诊断 DTO、标准库、模型 prompt、agent 补全逻辑和安全检查。
- Database: 无迁移；旧记录缺少新字段时降级读取。
- AI / standard data: 新增教学动作标准，并要求模型输出结构化提示计划。
- Deployment: 无额外部署依赖。

## Risks

- 风险：模型输出的新结构可能包含过直提示。
  缓解：`HintSafetyService` 同时检查 `studentHintPlan`，触发风险时降级为安全提示。

- 风险：过早改 UI 让学生端更混乱。
  缓解：本轮只补后端结构，不改变当前展示节奏。

- 风险：新增字段让旧数据读取失败。
  缓解：新增字段可空，reader 对缺失字段返回空快照。

- 风险：标准库变复杂后无人理解。
  缓解：教学动作只保留少量稳定枚举，先服务提示生成和 eval。

## Tasks

- [x] 扩展 `SubmissionAnalysisResponse`，新增 `StudentHintPlan`。
- [x] 扩展 `DiagnosisTaxonomy`，给标签增加 `teachingAction`。
- [x] 调整 `AiReportService` prompt 和 payload，要求模型输出 `studentHintPlan`。
- [x] 在 `DiagnosticAgentService` 中补全缺失的提示计划。
- [x] 让 `HintSafetyService` 检查并降级结构化提示。
- [x] 扩展 `DiagnosisReportReader` 读取结构化提示快照。
- [x] 补充单元测试。

## Acceptance

- [x] 旧 `studentHint` 仍可读，旧 `reportJson` 不报错。
- [x] 规则回退诊断能产出非空 `studentHintPlan`。
- [x] 模型 payload 中的 `studentHintPlan` 能进入最终诊断。
- [x] 泄题风险触发时，结构化提示和 Markdown 一起降级。
- [x] 定向测试通过，且不触碰当前未提交的身份/签名相关改动。

## 2026-05-19 Execution Notes

- `SubmissionAnalysisResponse` 增加 `studentHintPlan`，包含提示层级、问题类型、证据锚点、下一步动作、教练追问、教学动作、证据引用和泄题风险。
- `DiagnosisTaxonomy` 为现有错因标签补充 `teachingAction`，例如 `OFF_BY_ONE -> TRACE_VARIABLES`、`BRUTE_FORCE_LIMIT -> COUNT_COMPLEXITY`、`GREEDY_ASSUMPTION -> CHECK_INVARIANT`。
- `AiReportService` 的模型输出协议要求返回 `studentHintPlan`；如果模型缺失或不完整，`DiagnosticAgentService` 会基于标准标签和旧 `studentHint` 自动补齐。
- `HintSafetyService` 同时检查旧提示、结构化提示计划和 Markdown 报告；如果命中泄题风险，三者一起降级。
- `DiagnosisReportReader` 增加 `studentHintPlan` 快照读取，后续教师端、eval 或前端接入时不需要各自解析 JSON。
- 本轮没有重做学生端 UI，没有删除旧字段，也没有引入数据库迁移。
