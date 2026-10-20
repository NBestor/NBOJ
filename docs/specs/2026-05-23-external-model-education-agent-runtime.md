# External Model Education Agent Runtime 阶段说明

## 背景

前一阶段的 AI 能力主要在本地规则 agent、证据包、标准库、学生提示计划、学习轨迹和离线评测上取得稳定结果。但 live model eval 暴露出一个关键问题：外部大模型调用不能继续依赖单次长 prompt。模型超时或回退时，必须能看见原因，不能把规则兜底误认为模型成功。

## 本阶段目标

本阶段使用 OpenSpec change `external-model-education-agent-runtime` 推进，目标是把外部大模型调用升级为 workflow-first 的教育 agent runtime：

```text
评测事实
-> ModelDiagnosisBrief
-> StandardLibraryPack
-> diagnosis-judge-v1
-> 程序校验
-> teaching-hint-v1
-> 安全校验
-> live eval report
```

## 新增底座

- `ModelDiagnosisBrief`：模型压缩输入，只保留题目摘要、关键代码、评测事实、候选信号、证据引用、学习轨迹和隐藏数据边界。
- `StandardLibraryPack`：按当前样本裁剪标准库，只注入候选错因、细粒度标签、教学动作、安全约束和不确定性出口。
- `PromptTemplateRegistry`：集中管理 `diagnosis-judge-v1` 和 `teaching-hint-v1`。
- `ModelOutputValidator`：校验标签、证据引用、字段完整性和泄题风险。
- `ExternalModelAgentRuntime`：负责组装 brief、标准库包和 prompt，并提供阶段输出校验入口。
- `LiveModelEvalReport`：逐条记录模型调用状态、耗时、fallback、标签命中、证据校验、安全结果和失败原因。

## 当前验证

离线回归已通过：

```text
ModelDiagnosisEvalTest
StudentHintEvalFixtureTest
ModelDiagnosisBriefBuilderTest
PromptTemplateRegistryTest
ModelOutputValidatorTest
```

关键结果：

- 100 条学生提示 blind eval 仍保持 issue/fine/teaching/evidence 全部命中。
- 负例评测没有新增误报。
- 学习轨迹公开链路评测继续通过。
- 新增 brief/standard library/prompt/validator 测试通过。

live smoke eval 已重新验证 1 条样本：

```text
model=Qwen/Qwen3.5-35B-A3B
total=1
completed=1
fallback=0
timeout=0
issueHits=1
fineHits=1
safetyPassed=1
promptVersion=diagnosis-judge-v1+teaching-hint-v1
```

报告保存于 `online-judge/target/ai-eval-reports/live-model-eval-20260523-144103.json`。该目录属于测试生成产物，不应提交。

DeepSeek-V4-Pro 切回默认模型后，已用流式协议重新跑真实 small live eval：

```text
model=deepseek-ai/DeepSeek-V4-Pro
total=3
completed=0
fallback=3
timeout=0
issueHits=3
fineHits=3
safetyPassed=3
failureReason=MODEL_RUNTIME_FALLBACK:RATE_LIMITED
report=online-judge/target/ai-eval-reports/live-model-eval-20260523-151518.json
```

随后新增 `INSUFFICIENT_QUOTA` 失败原因后，又跑 1 条样本验证失败归因：

```text
model=deepseek-ai/DeepSeek-V4-Pro
total=1
completed=0
fallback=1
timeout=0
issueHits=1
fineHits=1
safetyPassed=1
failureReason=MODEL_RUNTIME_FALLBACK:INSUFFICIENT_QUOTA
latencyMs=10623
report=online-judge/target/ai-eval-reports/live-model-eval-20260523-151709.json
```

这次 1 条样本显示：阶段 A `diagnosis-judge-v1` 已通过流式调用返回并通过校验，阶段 B `teaching-hint-v1` 调用时触发 429 `insufficient_quota`。因此当前瓶颈不是“agent 完全没有接入外部模型”，而是两阶段调用在当前账号额度下容易被第二阶段消耗卡住。

本轮真实在线评测还暴露了两个配置/协议风险：

- `deepseek-ai/DeepSeek-V4-Pro`：非流式调用返回 `choices=null`，更适合按 ModelScope 示例使用 `stream=true` 调用；已将默认模型切回该模型，并将 `ai.stream-enabled` 默认值调整为 `true`。真实 smoke 已确认阶段 A 能通过流式协议完成，但阶段 B 在当前账号下触发 `INSUFFICIENT_QUOTA`。如果账号额度或限流导致流式调用失败，runtime 必须显式记录 fallback，而不能把规则兜底算作模型成功。
- `MiniMax/MiniMax-M2.7`：ModelScope 返回 400 `has no provider supported`，说明这个 Model-Id 在当前 API-Inference 环境不可用，不能作为默认配置。

因此当前默认模型遵循产品选择，配置为 `deepseek-ai/DeepSeek-V4-Pro`，默认使用流式响应提取最终 `content`，并忽略 `reasoning_content`。`Qwen/Qwen3.5-35B-A3B` 是本轮在 ModelScope OpenAI-compatible API 上真实 smoke 成功的备选模型 ID，可作为后续稳定性、延迟和成本对比基线。

## 限制

- 当前 runtime 已接入提交诊断的默认外部模型路径；旧长 prompt 路径仅作为 `ai.external-runtime-enabled=false` 的兼容回滚路径。
- live smoke 只跑 1 条样本，不能证明模型在完整评测集上稳定。
- `deepseek-ai/DeepSeek-V4-Pro` 依赖账号额度和流式协议稳定性；上线前必须重新跑 small smoke live eval 和扩展样本 live eval。
- `Qwen/Qwen3.5-35B-A3B` 单条两阶段耗时约 57 秒，后续可作为备选基线继续对比。
- 后续必须继续压缩 prompt、比较更快模型，并考虑阶段 B 条件触发或缓存策略。

## 后续方向

1. 对比不同模型在 live report 中的完成率、回退率、延迟和标签命中率，优先寻找更快且稳定支持非流式或流式调用的模型。
2. 将 `failureReason` 继续细分为 `EMPTY_CHOICES` 等；当前已支持 `INSUFFICIENT_QUOTA` 和 `MODEL_UNSUPPORTED`，方便教师端和运维端判断问题来源。
3. 将教师校正样本纳入 small live eval，优先验证真实高质量样本。
4. 让教师端质量面板读取模型来源、promptVersion、fallback 状态和失败原因。
