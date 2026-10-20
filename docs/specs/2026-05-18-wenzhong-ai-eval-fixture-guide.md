# Guide: 教师校正样例进入模型 Eval

## 目的

教师校正样例是最有价值的 AI 质量数据。它代表“AI 原本判断错了，老师给出了更可信的教学判断”。这些样例应该进入可复跑 eval，而不是只留在业务数据库里。

## 当前 fixture 位置

```text
online-judge/src/test/resources/diagnosis-eval-fixtures/teacher-corrections.json
```

## fixture 字段

- `problem`: 题目信息。
- `submission`: 学生源码、语言、评测结果。
- `analysis`: AI 原始诊断标签和标题。
- `teacherCorrection`: 老师修正后的粗/细错因和备注。
- `expectedIssueTags`: 模型/agent 应命中的粗粒度标签。
- `expectedFineTags`: 模型/agent 应命中的细粒度标签。
- `mustMention`: 期望解释里应该出现的方向。
- `mustNotMention`: 不应出现的泄题或不安全内容。

## 如何运行

无模型 key 时，运行：

```powershell
mvn -q -Dtest=ModelDiagnosisEvalTest test
```

这会验证 fixture 结构，不调用真实模型。

有模型 key 时：

```powershell
$env:AI_EVAL_API_KEY="..."
$env:AI_EVAL_MODEL="deepseek-ai/DeepSeek-V4-Pro"
$env:AI_STREAM_ENABLED="true"
mvn -q -Dtest=ModelDiagnosisEvalTest test
```

可选：

```powershell
$env:AI_EVAL_BASE_URL="https://api-inference.modelscope.cn/v1"
```

## 从真实教师校正补充 fixture

先调用：

```text
GET /api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates
```

然后把候选样例整理成 `teacher-corrections.json` 中相同结构。整理时注意：

也可以先保存候选 JSON，再用脚本生成 fixture 草稿：

```powershell
curl http://localhost:8081/api/teacher/assignments/7/diagnosis-eval-candidates -o candidates.json
node scripts/export-diagnosis-eval-fixture.mjs --input candidates.json --output src/test/resources/diagnosis-eval-fixtures/teacher-corrections.generated.json
```

脚本产物仍建议人工快速复核后，再合并进长期维护的 `teacher-corrections.json`。

- 不要保留学生隐私信息。
- 源码只保留复现诊断所需部分。
- `expectedIssueTags` 和 `expectedFineTags` 应以老师修正为准。
- `mustNotMention` 默认包含 `完整代码`、`参考答案`、`隐藏测试点` 等安全约束。
