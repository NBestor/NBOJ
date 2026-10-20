# AI 产品主链路收束说明

## 北极星

学生每次失败提交后，都能在不泄题的前提下，明确知道当前最该处理的错误点，以及下一步可验证学习动作。

这意味着本项目不是“带 AI 的传统 OJ”，而是把每次代码提交转化成一次可验证、可行动、可解释的学习反馈。

## 三层架构

### 1. 学生主链路

学生主链路只回答三个问题：

- 当前错误点：这次为什么没过，最该先处理什么。
- 继续提升点：修完当前错误后，在哪些学习习惯或代码质量上还能进步。
- 下一步：现在立刻做一个什么观察、对照、追踪、估算或自测动作。

学生端主展示契约是 `SubmissionAnalysisResponse.studentFeedback`：

- `summary`
- `blockingIssues`
- `improvementOpportunities`
- `nextLearningAction`

旧字段如 `studentHint`、`fixDirections`、`issueTags` 保留兼容，但只作为兜底或折叠详情。

### 2. 教师洞察层

教师端服务老师的判断，不服务学生的第一眼反馈。它可以展示：

- 班级共性错因。
- 重复卡点和学习轨迹。
- 需要老师介入的学生。
- 模型是否可靠、是否 fallback、是否存在安全风险。

教师端可以使用 taxonomy、evidenceRefs、model status、fallback status，但要把这些转化成教学决策语言。

### 3. 研发评测层

研发评测层服务模型和系统迭代。它可以复杂，但不进入学生主界面：

- complex/live eval。
- baseline regression。
- runtime telemetry。
- provider error / latency / token budget。
- fallback attribution。
- promptVersion / model comparability。

这些信息用于判断外接模型能力是否真实提升，不用于学生端主体验。

## 后端六步主链路

```text
学生提交
-> 本地判题结果
-> DiagnosisEvidencePackage 证据摘要
-> RuleSignalAnalyzer 本地候选信号
-> 外接模型尝试生成结构化诊断与 studentFeedback
-> ModelOutputValidator 安全与结构校验
-> StudentFeedbackAssembler 生成最终学生双通道反馈
```

关键边界：

- 外接模型完整通过校验时，可以提供学生反馈。
- 外接模型 partial/fallback 时，仍返回本地安全反馈。
- 本地安全反馈不能冒充外接模型智能能力。
- 学生端主区域不展示 provider、promptVersion、fallback、latency、baseline 或模型指标。

## 学生反馈质量核心指标

产品层只保留四个核心指标：

- 主错因命中：是否找对当前最该先处理的问题。
- 证据扎根：是否绑定判题结果、失败样例或代码证据。
- 下一步可执行：学生是否知道现在要做什么。
- 不泄题：不提供完整代码、直接替换式修法或隐藏测试猜测。

更多细粒度指标继续保留在研发评测报告中。

## 后续开发判断规则

新增 AI 能力时，先判断它属于哪一层：

- 学生看完能不能行动？能，才进入学生主链路。
- 老师能不能据此做教学决策？能，进入教师洞察层。
- 只是为了验证模型或链路质量？进入研发评测层。

任何 runtime、baseline、provider 或 telemetry 信息，默认不得进入学生端第一视觉层。
