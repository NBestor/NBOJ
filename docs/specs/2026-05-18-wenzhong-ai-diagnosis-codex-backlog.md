# Codex Backlog: 温中 AI 诊断后续执行任务

本文档把长期 spec 中尚未完成的方向拆成 Codex 可连续执行的任务包。后续本对话说“继续”时，优先按本 backlog 顺序推进；如果实现中发现设计不合理，先小幅修正 spec/backlog，再继续执行。

## 执行原则

- 每个任务包都必须包含实现、测试、文档回写。
- 优先小步闭环，不一次性重写核心 AI 链路。
- 涉及 AI 结论的功能必须保留证据引用、置信度或可追溯来源。
- 涉及学生提示的功能必须受 `hintPolicy` 限制，不能泄题。
- 涉及教师反馈的功能必须能进入 eval 或后续质量分析。
- 当前优化目标优先保障长期质量，而不是最快堆出可见功能；凡是会影响后续诊断、教练、画像、推荐的数据底座，优先补齐。

## Codex 执行协议

后续每次说“继续”时，默认按以下方式推进一个任务包：

1. 读取相关代码和现有测试，确认影响范围。
2. 将任务包拆成当轮最小可交付闭环。
3. 实现后补测试，优先覆盖规则、边界、旧数据兼容和泄题风险。
4. 运行定向测试、前端类型检查或完整回归。
5. 回写本 backlog、长期 spec 或任务清单，记录完成内容、剩余风险和下一步。

如果执行中发现当前任务顺序不合理，可以先修正 backlog/spec，再继续执行；但不能牺牲证据包、标准库、安全校验和 eval 这四个地基。

## Backlog 总览

| 编号 | 任务包 | 目标 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| C1 | 证据包持久化 / 可重建 | 让追问和后续多轮教练能引用完整诊断证据 | 已有 `DiagnosisEvidencePackageBuilder` | 已完成 |
| C2 | 学生回答与二次追问 | 从“一问”升级为真正多轮教练 | C1 可先不依赖，完整质量依赖 C1 | 已完成 |
| C3 | 教练记录进入学习轨迹 | 学生/老师能看到追问、回答和改善关系 | C2 | 已完成 |
| C4 | 题目知识点与误区标准库 | 为题目维护知识点、算法策略、常见误区、边界类型 | 现有题目管理 | 已完成 |
| C5 | 跨题能力画像升级 | 从作业内摘要升级到跨作业学生能力画像和班级薄弱点 | C4 更完整，当前可先做轻量版 | 已完成 |
| C6 | 推荐下一题/复盘任务 | 基于能力画像推荐练习或复盘动作 | C5 | 已完成 |
| C7 | 教师校正样例进入模型 eval | 从真实教师校正中抽样形成可复跑模型 eval | 已有 eval 候选 API | 已完成 |
| C8 | 模型多轮教练增强 | 在安全限制下引入模型生成追问/追问评价 | C1/C2/C7 | 已完成 |
| C9 | 教师端 AI 质量面板 | 汇总校正率、低置信度、模型 eval 命中情况 | C7 | 已完成 |
| C10 | UI/体验二次打磨 | 保证学生端/教师端新增 AI 能力易用、不过载 | C2/C5/C9 后持续执行 | 已完成 |

## C1: 证据包持久化 / 可重建

### Why

当前追问只引用诊断证据 ID 和学习轨迹摘要，尚未直接引用完整 `DiagnosisEvidencePackage`。如果要让 AI 教练基于真实评测事实追问，就需要能读取或重建当次证据包。

### Codex 任务

- [x] 梳理 `SubmissionAnalysisService.generateAndStoreAnalysis` 中证据包生成与存储路径。
- [x] 设计最小字段：在 `SubmissionAnalysis` 中保存 `evidenceJson`，或新增 `submission_diagnosis_evidence` 表。
- [x] 优先选择兼容旧数据的方案：旧记录无 evidence 时可按提交和评测结果重建。
- [x] 新增 DTO/reader，让 `CoachPromptService` 能读取 evidence summary。
- [x] `CoachPrompt.contextSummary` 使用具体证据，如首个失败点、隐藏测试点脱敏状态、规则信号。
- [x] 增加单元测试：新诊断保存 evidence，旧诊断能降级为摘要。
- [x] 跑 `mvn -q -Dtest=DiagnosisEvidencePackageBuilderTest,CoachPromptServiceTest,DiagnosisEvidencePackageReaderTest test`。
- [x] 跑 `mvn -q test`。
- [x] 回写长期 spec/backlog。

### 验收

- 新提交诊断后可以从存储中读到证据包或证据摘要。
- 追问上下文不只展示标签，还能展示具体证据类型。
- 旧数据不报错。

### 2026-05-18 执行记录

- `SubmissionAnalysis` 增加 `evidenceJson`，新诊断会随 `reportJson` 一起保存完整 `DiagnosisEvidencePackage`。
- 新增 `DiagnosisEvidencePackageReader`，负责证据包序列化、反序列化、摘要提取和旧数据降级。
- `CoachPromptService` 生成追问时优先读取持久化证据包，并把测试点数量、首个失败点、隐藏测试点、历史重复卡点、提示层级等事实写入上下文摘要。
- `DiagnosisEvidencePackage` 增加无参构造，保证证据包 JSON 可以稳定读回。
- 新增 `DiagnosisEvidencePackageReaderTest`，并扩展 `CoachPromptServiceTest` 覆盖持久化证据包引用。

## C2: 学生回答与二次追问

### Why

当前教练只是生成“下一问”，学生不能回答，系统也无法根据回答继续追问或记录学习过程。

### Codex 任务

- [x] 新增 `CoachTurn` 或扩展 `CoachPrompt`，支持学生回答、二次追问、轮次编号。
- [x] 新增请求/响应 DTO：`CoachReplyRequest`、`CoachTurnResponse`。
- [x] 新增 API：`POST /api/submissions/{id}/coach-turns`，提交学生回答并生成下一步反馈。
- [x] 先实现规则版二次追问：根据学生回答是否为空、是否提到样例/边界/复杂度，给下一问。
- [x] 所有输出继续受 `hintPolicy` 限制。
- [x] 学生端“下一问”卡片增加回答输入框、提交回答、显示下一步反馈。
- [x] 增加测试：空回答、不含证据的回答、包含最小样例的回答。
- [x] 跑 `npm run typecheck`。
- [x] 跑 `mvn -q -Dtest=CoachPromptServiceTest test`。
- [x] 跑 `mvn -q test`。
- [x] 回写长期 spec 和任务清单。

### 验收

- 学生可以回答 AI 追问。
- 系统能保存回答并生成二次反馈。
- 不出现完整答案或直接改法。

### 2026-05-18 执行记录

- `CoachPrompt` 扩展 `parentPromptId`、`turnIndex`、`studentAnswer`、`coachFeedback`，在不新建表的前提下支持多轮追问。
- 新增 `CoachReplyRequest` 和 `POST /api/submissions/{id}/coach-turns`，学生提交回答后生成下一轮苏格拉底式追问。
- `CoachPromptResponse` 返回完整 `turns` 历史，前端可以展示每一轮问题、学生回答和教练反馈。
- 规则版二次追问会区分“有证据的回答”和“泛泛说方向的回答”，要求学生补样例、边界、变量变化或复杂度数量级。
- 学生端“下一问”卡片新增回答框、提交回答、轮次历史和教练反馈。
- 已通过 `npm run typecheck`、`mvn -q -Dtest=CoachPromptServiceTest,DiagnosisEvidencePackageReaderTest test`、`mvn -q test`。

## C3: 教练记录进入学习轨迹

### Why

多轮教练如果不进入学习轨迹，老师和学生无法看到“追问是否带来改善”。

### Codex 任务

- [x] 学生轨迹 DTO 增加最近教练互动摘要。
- [x] 教师概览/学生轨迹中展示最近追问状态：已追问、已回答、待继续。
- [x] 将教练轮次与提交改善信号关联：回答后下一次提交是否改变错因/通过。
- [x] 增加 `CoachInteractionSummary` 聚合器。
- [x] 增加测试：回答后再次提交，轨迹显示改善/未改善。
- [x] 前端学生端作业记录展示“最近追问”。
- [x] 教师端学生过程展示“已进入教练追问”标记。
- [x] 跑类型检查和完整回归。
- [x] 回写文档。

### 验收

- 学生轨迹能看到最近一次教练追问与回答状态。
- 教师能识别哪些学生已经被 AI 引导过。

### 2026-05-18 执行记录

- 新增 `CoachInteractionAnalyzer`，把追问轮次压缩成统一的教练互动摘要：轮次数、已回答轮数、状态、最近回答、最近反馈。
- 学生轨迹 `StudentTrajectoryResponse` 增加 `latestCoachInteraction`，每题轨迹也增加 `latestCoachInteraction`。
- 教师概览 `AssignmentOverviewResponse.StudentProgressSummary` 增加 `latestCoachInteraction`。
- 学生端作业记录展示最近 AI 教练状态和每题追问轮次。
- 教师端学生过程列表展示“待回答追问 / 已回答追问 / 继续追问中”状态。
- 已通过 `npm run typecheck`、`mvn -q -Dtest=CoachInteractionAnalyzerTest,CoachPromptServiceTest,ClassroomServiceCorrectionTest test`、`mvn -q test`。
- 2026-05-19 追加一轮：新增 `CoachImpactAnalyzer` 和 `CoachImpactResponse`，将已回答的 AI 追问与同题后续提交关联，输出“追问后通过 / 错因变化 / 仍卡同类问题 / 评测阶段变化 / 等待后续提交”等观察性信号。
- 学生轨迹、每题轨迹、提交点、长期能力画像、教师作业概览和最近教练互动摘要均接入 `latestCoachImpact` / `impact`，让学生和老师能看到追问后是否出现同题改善。
- `CoachImpactAnalyzer` 以后续提交时间晚于“原提交时间与学生回答追问时间的较晚者”为准，避免把学生回答前已经发生的提交误算成追问效果；旧追问记录没有回答时间时回退到追问创建时间。
- 学生端提交结果区展示追问后改善摘要，长期能力画像展示 `coachImpactSummary`；教师端学生过程列表显示追问效果状态和摘要。
- 新增 `CoachImpactAnalyzerTest`，扩展 `StudentAbilityProfileServiceTest` 和 `ClassroomServiceCorrectionTest`，覆盖追问后通过、仍卡同类问题、等待后续提交、画像摘要和教师概览接线。
- `browser-smoke.mjs` mock 数据加入追问效果字段，`visual-smoke.mjs` 检查构建产物中的 `latestCoachImpact` 和 `coachImpactSummary`。
- 已通过 `npm run typecheck`、`npm run build`、`npm run smoke:visual`、`npm run smoke:browser`、`./mvnw.cmd -q "-Dtest=CoachImpactAnalyzerTest,StudentAbilityProfileServiceTest,StudentRecommendationServiceTest,ClassroomServiceCorrectionTest" test`。
- 2026-05-19 追加一轮：`CoachPrompt` 增加 `answeredAt`，学生提交追问回答时写入独立回答时间；`CoachPromptResponse` 和前端类型同步暴露该字段。
- `CoachImpactAnalyzer` 改为优先使用 `answeredAt` 作为后续提交锚点，并新增测试覆盖“回答前已有一次提交，不应算作追问后改善”和“旧数据无回答时间时回退 createdAt”。
- 已通过 `./mvnw.cmd -q "-Dtest=CoachImpactAnalyzerTest,CoachPromptServiceTest,CoachInteractionAnalyzerTest" test`。
- 剩余风险：当前追问效果仍是观察性分析，不声称严格因果；`answeredAt` 能提升时间归因精度，但还不能证明改善一定由 AI 追问导致。

## C4: 题目知识点与误区标准库

### Why

当前能力点主要由错因标签映射而来，还缺少题目侧知识点、算法策略、常见误区和边界类型。

### Codex 任务

- [x] 设计题目元数据字段：`knowledgePoints`、`algorithmStrategies`、`commonMistakes`、`boundaryTypes`。
- [x] 决定存储方式：先 JSON 字段或新表；优先小步、兼容旧题目。
- [x] 更新题目管理 DTO/API。
- [x] 题目编辑页增加元数据编辑区。
- [x] 诊断证据包纳入题目元数据。
- [x] `DiagnosisTaxonomy` 与题目元数据建立弱关联，不强制每题必须填。
- [x] 增加后端测试：题目元数据保存、读取、进入证据包。
- [x] 增加前端类型检查。
- [x] 回写文档。

### 验收

- 老师可为题目维护知识点和常见误区。
- 新诊断证据包能读取题目元数据。

### 2026-05-18 执行记录

- `Problem` 增加 `knowledgePoints`、`algorithmStrategies`、`commonMistakes`、`boundaryTypes` 四类结构化元数据，使用 JSON 文本保存，旧题目兼容空列表。
- `CreateProblemRequest`、`ProblemResponse`、`ProblemManageResponse` 同步返回/接收题目知识元数据。
- `ProblemService` 保存题目时清洗列表、去重，并限制单类最多 12 项。
- `DiagnosisEvidencePackage.ProblemEvidence` 纳入题目知识点、算法策略、常见误区、边界类型。
- `DiagnosisEvidencePackageReader` 的追问摘要会引用题目知识点和常见误区。
- 题目编辑页增加知识点、算法策略、常见误区、边界类型编辑区；支持逗号、顿号、分号、换行分隔。
- 已通过 `npm run typecheck`、`mvn -q -Dtest=DiagnosisEvidencePackageBuilderTest,DiagnosisEvidencePackageReaderTest,ModelDiagnosisEvalTest test`、`mvn -q test`。

## C5: 跨题能力画像升级

### Why

当前能力摘要主要在同一作业内聚合。长期目标需要跨作业、跨时间形成学生能力画像和班级薄弱点。

### Codex 任务

- [x] 新增学生能力画像服务：按学生聚合最近 N 次作业/提交。
- [x] 聚合维度：能力点、题目知识点、常见误区、边界类型、近期趋势。
- [x] 新增 API：学生个人能力画像。
- [x] 教师端班级概览增加能力薄弱点模块。
- [x] 学生端增加“长期能力画像”轻量展示。
- [x] 增加测试：多作业多提交聚合、同学生多 profile 合并、教练互动摘要进入画像。
- [x] 跑完整回归。
- [x] 回写文档。

### 验收

- 能看到学生跨作业 top 能力卡点。
- 能看到班级层面的薄弱能力点。

### 2026-05-18 执行记录

- 新增 `StudentAbilityProfileService` 和 `StudentAbilityProfileResponse`，按同一班级的学号优先、姓名兜底合并学生在不同作业下产生的多个 `StudentProfile`。
- 学生画像默认聚合最近 40 次提交，输出提交数、题目数、作业数、失败提交数、主要能力缺口、趋势信号、最近 AI 教练互动。
- 能力缺口继续复用 `AbilitySignalAnalyzer` 和 `DiagnosisTaxonomy`，保证错因标签到能力点的映射不另起炉灶。
- 画像额外聚合题目侧 `knowledgePoints`、`commonMistakes`、`boundaryTypes`，让 C4 的题目元数据进入长期学习分析。
- 新增 `GET /api/student/profile/{studentProfileId}/ability-profile`。
- 教师作业概览 `AssignmentOverviewResponse` 增加 `classAbilityWeaknesses`，教师端“高频问题”下展示班级能力薄弱点。
- 学生端提交结果区域增加“长期能力画像”，展示主要能力点、摘要、趋势和部分知识标签。
- 已通过 `npm run typecheck`、`./mvnw.cmd -q "-Dtest=StudentAbilityProfileServiceTest,ClassroomServiceCorrectionTest,CoachPromptServiceTest" test`、`./mvnw.cmd -q test`。
- 剩余风险：当前跨作业合并依赖班级 + 学号/姓名的启发式匹配；后续如果要严肃追踪多年学习画像，应引入稳定学生唯一身份或导入名单 ID。

## C6: 推荐下一题/复盘任务

### Why

能力画像如果不转化为下一步行动，价值会停留在分析层。

### Codex 任务

- [x] 设计推荐规则：能力缺口 -> 题目知识点/边界类型 -> 推荐题目或复盘动作。
- [x] 新增推荐 DTO/API。
- [x] 学生端展示 1-3 个下一步建议：重做、复盘、下一题。
- [x] 教师端展示班级复盘建议。
- [x] 增加测试：缺口匹配题目、无题目时给复盘任务。
- [x] 跑完整回归。
- [x] 回写文档。

### 验收

- 学生得到明确下一步练习或复盘建议。
- 推荐理由可追溯到能力画像。

### 2026-05-18 执行记录

- 新增 `StudentRecommendationService` 和 `StudentRecommendationResponse`。
- 新增 `GET /api/student/profile/{studentProfileId}/recommendations`，直接复用 C5 的长期能力画像。
- 推荐策略先保持可解释规则版：优先重做最近失败且匹配画像的旧题，其次推荐未做过的同类新题，最后给短复盘任务。
- 推荐理由会引用能力点、题目知识元数据、常见误区、边界类型或最近教练状态。
- 学生端在长期能力画像下方展示“下一步推荐”，有题目时可直接跳转练习；复盘任务只展示行动建议。
- 新增 `StudentRecommendationServiceTest` 覆盖“重做 -> 同类新题 -> 复盘”的推荐顺序。
- 已通过 `npm run typecheck`、`./mvnw.cmd -q "-Dtest=StudentRecommendationServiceTest,StudentAbilityProfileServiceTest" test`、`./mvnw.cmd -q test`。
- 剩余半步：教师端“班级复盘建议”尚未单独实现；当前教师端已有班级能力薄弱点，可作为 C9 质量面板或 C10 体验打磨时合并处理。

## C7: 教师校正样例进入模型 eval

### Why

已有教师校正 eval 候选 API，但模型层 eval 还没有自动使用真实校正样例。

### Codex 任务

- [x] 设计 eval candidate adapter：将 `DiagnosisEvalCandidateResponse` 转成 `ModelDiagnosisEvalTest` 可用样例。
- [x] 增加本地 JSON fixture 支持，避免测试依赖运行中数据库。
- [x] 新增脚本或测试工具：从 API 导出 eval JSON。
- [x] live model eval 支持读取 fixture。
- [x] 增加质量指标：粗标签命中、细标签命中、泄题风险、证据引用保留。
- [x] 文档说明如何设置 `AI_EVAL_API_KEY`、`AI_EVAL_MODEL`、fixture 路径。
- [x] 跑无 key 本地测试。
- [x] 回写文档。

### 验收

- 教师校正样例可以转成可复跑 eval fixture。
- 没有 key 时不阻塞；有 key 时能跑真实模型回归。

### 2026-05-18 执行记录

- 新增 `src/test/resources/diagnosis-eval-fixtures/teacher-corrections.json`，保存教师校正样例 fixture。
- 新增 `TeacherCorrectionEvalFixtureLoader`，把 fixture 转成 `Problem`、`Submission` 和 baseline `SubmissionAnalysisResponse`。
- `ModelDiagnosisEvalTest` 现在会加载教师校正 fixture；无模型 key 时验证 fixture 结构，有 `AI_EVAL_API_KEY` 时纳入 live model eval。
- 当前 fixture 包含输入读取误判、暴力复杂度误判两个教师校正案例。
- 已通过 `mvn -q -Dtest=ModelDiagnosisEvalTest test`。
- 2026-05-19 追加一轮：`DiagnosisEvalCandidateResponse` 补充题目标题、题面、难度、时限、内存、语言和完整源码字段，让候选 API 可以直接支撑 fixture 导出。
- 新增 `scripts/export-diagnosis-eval-fixture.mjs`，将 `GET /api/teacher/assignments/{assignmentId}/diagnosis-eval-candidates` 返回的 JSON 转成 `teacher-corrections.json` 同结构 fixture 草稿，默认输出到 `teacher-corrections.generated.json`。
- `docs/specs/2026-05-18-wenzhong-ai-eval-fixture-guide.md` 增加 curl 导出和脚本转换用法。
- 剩余风险：导出的 `mustMention` 目前基于教师备注自动填充，仍需要老师或维护者做一次人工筛选，避免把过长备注、学生隐私或不稳定措辞直接纳入长期 eval。

## C8: 模型多轮教练增强

### Why

当前追问为规则模板生成。后续可让模型在安全约束下生成更自然、更贴合学生回答的追问。

### Codex 任务

- [x] 设计 `CoachAgentService`，输入证据包、学习轨迹、学生回答、hintPolicy。
- [x] 定义结构化输出：question、rationale、evidenceRefs、confidence、answerLeakRisk。
- [x] 加安全校验：超过 hintPolicy 或泄题则降级到规则追问。
- [x] 模型失败回退规则追问。
- [x] 增加 eval：模型追问不得给答案，必须引用证据。
- [x] 前端不需要大改，继续使用现有 coach turn API。
- [x] 跑定向测试和完整回归。
- [x] 回写文档。

### 验收

- 有模型 key 时追问更自然。
- 无模型或模型失败时仍可用。
- 泄题风险受控。

### 2026-05-18 执行记录

- 新增 `CoachAgentService`，复用现有 `ai.*` 配置调用 chat completions。
- 模型追问输出被限制为结构化 JSON：`question`、`rationale`、`evidenceRefs`、`confidence`、`answerLeakRisk`。
- `CoachPromptService` 生成首轮追问和二次追问时先构造规则 fallback，再尝试模型草稿；模型不可用、解析失败、证据引用缺失或泄题风险过高时自动回退规则追问。
- 安全门复用 `DiagnosisTaxonomy.isBeyondPolicy`，并额外拦截完整代码、答案、直接改法、代码块等泄题文本。
- 模型追问会在 `promptType` 中标记 `_MODEL`，`rationale` 中保留来源、置信度、泄题风险和证据摘要，便于后续 C9 质量面板统计。
- 新增 `CoachAgentServiceTest`，覆盖安全模型草稿采纳、泄题草稿拒绝、无模型回退。
- 已通过 `./mvnw.cmd -q "-Dtest=CoachAgentServiceTest,CoachPromptServiceTest" test`、`npm run typecheck`、`./mvnw.cmd -q test`。
- 剩余风险：当前模型追问质量尚未进入真实 live eval；后续可把教师/学生对追问的反馈纳入 C9 或独立 coach eval fixture。

## C9: 教师端 AI 质量面板

### Why

当 AI 能力越来越多，老师需要知道 AI 整体是否可靠，哪些地方常被校正。

### Codex 任务

- [x] 聚合指标：校正次数、校正率、低置信度次数、高泄题风险次数、常被校正标签。
- [x] 新增教师质量 API。
- [x] 教师端增加 AI 质量/复核面板。
- [x] 与 eval 候选联动：提示可导出的样例数量。
- [x] 增加测试：指标聚合正确。
- [x] 跑前端类型检查和完整回归。
- [x] 回写文档。

### 验收

- 老师能看到 AI 诊断质量趋势。
- 能识别最需要优化的错因类别。

### 2026-05-18 执行记录

- 新增 `AiQualityOverviewService` 和 `AiQualityOverviewResponse`。
- 新增 `GET /api/teacher/assignments/{assignmentId}/ai-quality`。
- 指标包括：AI 诊断样本数、教师校正数、校正率、低置信度数/率、高泄题风险数/率、eval 候选数、常见校正流向。
- 教师端作业工作台新增“AI 质量”面板，显示质量摘要、关键指标和常被校正标签。
- `TeacherDiagnosisCorrectionRepository` 增加按作业查询校正记录的方法。
- 新增 `AiQualityOverviewServiceTest`，覆盖低置信度、高泄题风险、校正率、eval 候选和校正标签统计。
- 已通过 `npm run typecheck`、`./mvnw.cmd -q "-Dtest=AiQualityOverviewServiceTest,ClassroomServiceCorrectionTest" test`、`./mvnw.cmd -q test`。
- 剩余风险：质量指标目前是作业级聚合；跨作业/跨时间模型质量趋势可以在后续管理端报表中继续扩展。

## C10: UI/体验二次打磨

### Why

AI 能力增强后，页面信息密度会升高，需要避免学生和老师被信息压住。

### Codex 任务

- [x] 浏览学生端提交流程：提交 -> 反馈 -> 下一问 -> 回答 -> 再提交。
- [x] 浏览教师端流程：概览 -> 需关注学生 -> 证据链 -> 校正 -> eval 候选。
- [x] 优化信息层级：默认展示最重要信息，细节可折叠。
- [x] 检查移动端布局和文字溢出。
- [x] 检查夜间主题。
- [x] 必要时用浏览器截图验证。
- [x] 跑类型检查。
- [x] 回写文档。

### 验收

- 学生端不会被 AI 信息淹没。
- 教师端能快速定位最需要干预的学生和问题。

### 2026-05-18 执行记录

- 学生端长期能力画像收束为主要能力、摘要和少量标签，不再同时展开趋势全文。
- 学生端下一步推荐限制默认展示信息密度：每条推荐保留题目、理由、最多 2 个焦点标签和行动按钮。
- 推荐理由与画像摘要使用两行截断，避免移动端侧栏被长文本撑开。
- 教师端 AI 质量指标改为自适应网格，窄屏下不再固定 5 列。
- 教师端常见校正标签增加换行保护，避免长标签组合撑破面板。
- 已通过 `npm run typecheck` 和 `./mvnw.cmd -q test`。
- 浏览器截图验证尝试受限：当前环境项目未安装 Playwright 包，`npx --package playwright` 在 PowerShell stdin 脚本中未暴露模块；未为截图检查新增项目依赖。后续若要做更强视觉回归，可补一个项目级 Playwright smoke 脚本或使用 Codex 浏览器插件。

## 第一阶段执行顺序回顾

1. C1 证据包持久化 / 可重建。
2. C2 学生回答与二次追问。
3. C3 教练记录进入学习轨迹。
4. C7 教师校正样例进入模型 eval。
5. C4 题目知识点与误区标准库。
6. C5 跨题能力画像升级。
7. C6 推荐下一题/复盘任务。
8. C8 模型多轮教练增强。
9. C9 教师端 AI 质量面板。
10. C10 UI/体验二次打磨。

第一阶段的重点是把 AI 从“一次性诊断文本”升级为“证据包 -> 多轮教练 -> 学习轨迹 -> 能力画像 -> 推荐 -> 质量监控”的闭环。C1-C10 已完成一轮可运行实现，后续进入第二阶段：增强评测、稳定身份、跨作业质量趋势和真实课堂使用闭环。

## 第二阶段 Backlog

| 编号 | 任务包 | 目标 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| D1 | Coach eval fixture | 把模型追问质量纳入可复跑评测，检查不泄题、引用证据、追问有效性 | C8 | 已完成 |
| D2 | 稳定学生身份 | 避免跨作业画像依赖姓名/学号启发式合并 | C5 | 已完成一轮 |
| D3 | 跨作业 AI 质量趋势 | 从单作业质量面板升级为跨作业/跨时间模型质量趋势 | C9 | 已完成一轮 |
| D4 | 推荐效果闭环 | 记录推荐后是否重做、是否通过、错因是否变化 | C6/C3 | 已完成一轮 |
| D5 | 教师班级复盘建议 | 将班级薄弱点转成可执行课堂复盘动作 | C5/C6/C9 | 已完成一轮 |
| D6 | 浏览器视觉 smoke | 为学生端/教师端关键 AI 面板建立桌面/移动端 smoke 检查 | C10 | 已完成一轮 |

### D1: Coach eval fixture

- [x] 设计 coach eval JSON：输入证据包、学生回答、期望追问特征、禁止内容。
- [x] 为 `CoachAgentService` 增加无 key 本地 fixture 测试。
- [x] 有 `AI_EVAL_API_KEY` 时运行真实模型追问 eval。
- [x] 质量指标：证据引用命中、泄题风险、是否要求学生补证据、是否直接给改法。

#### 2026-05-18 执行记录

- 新增/接入 `src/test/resources/coach-eval-fixtures/coach-turns.json`，当前覆盖差一位首轮追问、暴力复杂度二次追问、输入读取二次追问 3 个教练场景。
- 新增/使用 `CoachEvalFixtureLoader`，把 fixture 转成 `Submission`、`SubmissionAnalysis`、`HintPolicy` 和安全模型响应。
- `CoachAgentServiceTest` 增加 fixture 结构验收，确保每个样例都声明期望追问信号、必需证据引用和禁用泄题短语。
- `CoachAgentServiceTest` 增加本地无 key 回归：用 fixture 中的安全模型响应验证安全门会采纳、证据引用会保留、禁用短语不会出现。
- 新增“缺少证据引用即回退规则追问”的 fixture 化安全测试，防止模型生成看似合理但无证据来源的追问。
- 新增 `liveModelCoachQuestionsStaySafeWhenEnabled`，当设置 `AI_EVAL_API_KEY` 时会调用真实模型，检查模型追问来源、证据引用、泄题风险和追问信号；无 key 时自动跳过，不阻塞本地回归。
- 已通过 `./mvnw.cmd -q "-Dtest=CoachAgentServiceTest" test`。
- 剩余风险：当前 coach eval 只有 3 条代表性样例，后续应继续从真实学生回答和教师反馈中补充样例，尤其补充“模型过度提示”“模型追问太泛”“学生回答跑偏后如何拉回证据”的场景。

### D2: 稳定学生身份

- [x] 设计稳定学生唯一标识：导入名单 ID / class student key / 后台合并工具。
- [x] 兼容旧 `StudentProfile`，提供迁移或合并报告。
- [x] 能人工合并/拆分误合并画像。
- [x] 跨作业画像优先使用稳定 ID，姓名/学号只做兜底。

#### 2026-05-18 执行记录

- 新增 `StudentIdentityService`，统一生成学生身份 key：优先 `class:{classGroupId}:{studentNo/name}`，无班级 ID 时使用 `class-name:{className}:{studentNo/name}`，最后才回退旧的作业级 identity key。
- `ClassroomService.bindStudentIdentity` 改为新绑定优先使用稳定身份 key，同时能识别旧作业级 key 并把再次绑定迁移到稳定 key。
- `ClassroomImportService` 导入名单时使用同一套稳定身份 key，避免名单导入和学生自助绑定产生两套身份规则。
- `StudentAbilityProfileService` 的跨作业画像合并改为稳定 key 优先；同时继续把同班级同学号的旧 assignment-scoped profile 纳入画像，保证旧数据不被切断。
- 新增 `StudentIdentityAuditService` 和 `GET /api/teacher/classes/{classGroupId}/identity-audit`，输出班级画像总数、稳定身份数、旧身份数、缺失学号数和疑似重复身份组；该接口只报告，不自动改数据。
- 新增 `StudentIdentityServiceTest`、`StudentIdentityAuditServiceTest`，并扩展 `StudentAbilityProfileServiceTest` 覆盖稳定 key 优先、同名不同学号不误合并、旧作业级 profile 仍能被兼容纳入画像。
- 已通过 `./mvnw.cmd -q "-Dtest=StudentIdentityServiceTest,StudentIdentityAuditServiceTest,StudentAbilityProfileServiceTest,StudentRecommendationServiceTest,ClassroomServiceCorrectionTest" test` 和 `./mvnw.cmd -q test`。
- 追加一轮：新增 `StudentIdentityAdminService`，提供受保护的人工合并/拆分。合并不会删除画像或迁移提交记录，而是把选中的 profile 写成同一个 `manual-merge:{classGroupId}:{targetProfileId}`；拆分会给单个 profile 写成 `manual-split:{classGroupId}:{profileId}`。
- `StudentIdentityAuditResponse` 增加 `manualIdentityCount`；审计会识别人工身份，避免把已人工处理的组继续报成疑似重复。
- `StudentAbilityProfileService` 会优先尊重 `manual-merge` / `manual-split`。人工合并的 profile 会按同一身份聚合；人工拆分的 profile 不再被同学号/同名启发式兜底重新合并。
- 新增 `POST /api/teacher/classes/{classGroupId}/identity-merge` 和 `POST /api/teacher/classes/{classGroupId}/identity-split`。
- 教师管理页“班级与名单”区域新增身份审计卡片，展示画像数、稳定身份、人工身份、旧版身份、缺学号、疑似重复组，并可对疑似重复组执行“合并这组”或“拆分 #profileId”。
- 新增/扩展 `StudentIdentityAuditServiceTest` 和 `StudentAbilityProfileServiceTest`，覆盖人工合并不删数据、人工拆分独立身份、长期画像尊重人工身份。
- 已通过 `./mvnw.cmd -q "-Dtest=StudentIdentityAuditServiceTest,StudentAbilityProfileServiceTest" test` 和 `npm run typecheck`。
- 剩余风险：当前是低风险“重标识”方案，没有迁移 submissions/coach/corrections 的外键；这能保证历史不被破坏，但列表中仍会保留多个 profile。若后续需要真正物理合并，需要单独做 dry-run 迁移、回滚方案和操作审计日志。

### D3: 跨作业 AI 质量趋势

- [x] 新增跨作业质量 API。
- [x] 统计模型来源、校正率、低置信度、高泄题风险、常错标签随时间变化。
- [x] 教师/管理员端展示趋势和最该补 eval 的标签。

#### 2026-05-18 执行记录

- 新增 `AiQualityMetrics`，把作业级质量面板和跨作业趋势共用的校正率、低置信度率、高泄题风险率计算抽出来，避免指标口径分叉。
- 新增 `AiQualityTrendResponse` 和 `AiQualityTrendService`，按全部作业聚合 AI 质量：作业数、诊断样本数、教师校正数、eval 候选数、低置信度数、高泄题风险数、整体比率、每作业质量点、高频校正标签、尚未进入 eval 的高频标签。
- 新增 `GET /api/teacher/ai-quality/trend`，为后续教师/管理员趋势页提供数据接口。
- `SubmissionRepository` 增加 `findByAssignmentIdIn`，`TeacherDiagnosisCorrectionRepository` 增加 `findByAssignmentIdIn`，用于跨作业聚合。
- `AiQualityOverviewService` 改为复用 `AiQualityMetrics`，保持单作业面板和跨作业趋势的指标口径一致。
- 新增 `AiQualityTrendServiceTest`，覆盖多作业聚合、每作业质量点、校正标签统计和“待补 eval 标签”。
- 已通过 `./mvnw.cmd -q "-Dtest=AiQualityOverviewServiceTest,AiQualityTrendServiceTest" test` 和 `./mvnw.cmd -q test`。
- 追加一轮：教师端作业工作台接入 `api.aiQualityTrend()`，在单作业 AI 质量面板下新增“跨作业趋势”面板，展示作业数、诊断样本、校正率、低置信度率、eval 候选数、每作业校正率条形趋势和最该补 eval 的标签。
- 教师保存诊断校正后会刷新跨作业趋势，避免趋势面板停留在旧数据。
- `visual-smoke.mjs` 增加 `.teacher-ai-trend` 和 `/api/teacher/ai-quality/trend` 检查，防止构建产物丢失趋势入口。
- 已通过 `npm run typecheck`。
- 2026-05-19 追加一轮：`AiQualityTrendResponse` 增加 `sourceSegments`，按 `analysisSource`、`analysisSchemaVersion` 和 `diagnosticTrace` 中的 agent 版本聚合诊断样本、校正数、低置信度数、高泄题风险数及对应比率。
- `DiagnosisReportReader` 增加读取 `analysisSchemaVersion` 与 `diagnosticTrace` 的方法，避免趋势服务直接手写 JSON 解析。
- 教师工作台“跨作业趋势”面板新增“来源版本”区块，展示不同模型/规则来源和 agent 版本的样本量、校正率、低置信度率。
- `visual-smoke.mjs` 和 `browser-smoke.mjs` 增加 `.teacher-ai-source-segments`、`来源版本` 和 mock `sourceSegments` 检查。
- 已通过 `./mvnw.cmd -q "-Dtest=AiQualityTrendServiceTest" test`、`npm run typecheck`、`npm run build`、`npm run smoke:visual`、`npm run smoke:browser` 和 `./mvnw.cmd -q test`。
- 剩余风险：版本维度目前来自已存诊断 JSON 与 trace 字符串；后续如果要做严格 A/B 对比，应把 promptVersion/modelVersion 提升为显式字段并记录调用配置快照。
- 2026-05-19 追加一轮：`SubmissionAnalysisResponse` 增加 `aiInvocation` 结构化调用快照，显式记录 provider、model、modelVersion、promptVersion、agentVersion、schema/taxonomy 版本、调用状态和是否规则回退。
- `AiReportService` 在模型增强成功或文本兜底时写入模型与 prompt 版本；`DiagnosticAgentService` 升级到 `diagnostic-agent-v2`，在最终诊断中补齐 agent 版本与规则回退状态；规则基线和旧报告读取也补充兼容快照。
- `DiagnosisReportReader` 增加 `aiInvocation` 读取器；`AiQualityTrendService` 优先按结构化快照聚合来源版本，输出模型、prompt、agent、状态和 `fallbackCount`，旧数据仍回退到 schema + trace 解析。
- 教师工作台重新接入跨作业 AI 质量趋势，展示来源版本、模型/prompt 信息、校正率和规则回退数，让老师能看见不同 AI 版本的质量表现。
- 新增/扩展 `DiagnosisReportReaderTest` 和 `AiQualityTrendServiceTest`，覆盖结构化调用快照读取、按 prompt/model/agent 分组、规则回退计数。
- 已通过 `npm run typecheck` 和 `./mvnw.cmd -q "-Dtest=DiagnosisReportReaderTest,AiQualityTrendServiceTest" test`。
- 剩余风险：`aiInvocation` 仍存于诊断 JSON 中，不是单独审计表；如果后续要做严格实验、灰度或 A/B 分析，应新增调用日志表，记录完整请求配置、响应状态、耗时、错误类型和样本分桶。

### D4: 推荐效果闭环

- [x] 记录推荐曝光、点击、进入题目、提交结果。
- [x] 判断推荐后是否通过、错因是否变化、是否仍卡同一能力点。
- [x] 学生画像中加入推荐效果信号。
- [x] 教师端展示推荐效果汇总看板。

#### 2026-05-18 执行记录

- 新增 `StudentRecommendationEvent` 和 `StudentRecommendationEventRepository`，记录推荐 token、学生画像、推荐类型、题目、焦点能力/标签、事件类型、后续提交、后续 verdict 和后续错因标签。
- `StudentRecommendationService` 为每条推荐生成稳定 `recommendationToken`，并在推荐生成时记录幂等曝光，避免学生刷新页面导致同一条推荐重复计数。
- 新增 `RecommendationEventRequest`，学生端推荐卡片点击时上报 `CLICKED`，题目页带 token 到达后上报 `ENTERED_PROBLEM`。
- `SubmissionRequest` 和 `JudgeService` 接入 `recommendationToken`，提交完成后记录 `SUBMITTED` 事件；无效/过期 token 只跳过归因，不影响学生提交主流程。
- `StudentAbilityProfileService` 汇总推荐事件，在学生长期能力画像中输出 `recommendationEffectSummary`，区分“只曝光”“已点击”“已提交未过”“仍命中同类错因”“推荐后已通过”等轻量效果信号。
- 学生端推荐跳题链接会携带 `recommendationToken`，提交代码时继续带回后端；长期能力画像区域展示推荐效果信号。
- 新增/扩展 `StudentRecommendationServiceTest`、`StudentAbilityProfileServiceTest` 覆盖推荐 token、曝光/点击/进入题目/提交归因、提交 verdict/错因写入和画像效果信号。
- 已通过 `./mvnw.cmd -q "-Dtest=StudentRecommendationServiceTest,StudentAbilityProfileServiceTest" test` 和 `./mvnw.cmd -q test`。
- 追加一轮：`SubmissionAnalysisService` 在生成或复用提交诊断后，会调用 `StudentRecommendationEventService.backfillSubmissionAnalysis`，把异步生成的最终 verdict、一级错因和细粒度错因回填到推荐提交事件。
- `StudentRecommendationEventRepository` 新增按 `followupSubmissionId + eventType` 查询，保证“提交事件先到、AI 诊断后到”时仍能补齐推荐效果数据。
- 新增 `submittedRecommendationEventCanBackfillAnalysisWhenDiagnosisArrivesLater`，覆盖推荐提交先无标签、诊断生成后回填标签的链路。
- 已通过 `./mvnw.cmd -q "-Dtest=StudentRecommendationServiceTest,StudentAbilityProfileServiceTest" test`。
- 2026-05-19 追加一轮：新增 `RecommendationEffectivenessResponse`、`RecommendationEffectivenessService` 和 `GET /api/teacher/recommendations/effectiveness`，按最近推荐事件汇总曝光、点击、进入题目、后续提交、后续通过、仍命中同类错因、点击未提交、推荐类型和关注标签。
- 教师工作台在跨作业 AI 质量趋势旁新增“推荐效果”面板，展示推荐曝光、点击率、后续提交、后续通过率、点击未提交、按推荐类型/标签拆分的后续效果。
- `visual-smoke.mjs` 增加 `.teacher-recommendation-effect` 和 `/api/teacher/recommendations/effectiveness` 检查；`browser-smoke.mjs` 增加真实浏览器可见性检查和 mock 数据。
- 新增 `RecommendationEffectivenessServiceTest`，覆盖推荐漏斗、后续通过、仍命中同类错因、旧版 focusTags 解析和点击未提交信号。
- 已通过 `./mvnw.cmd -q "-Dtest=RecommendationEffectivenessServiceTest,StudentRecommendationServiceTest,StudentAbilityProfileServiceTest" test`、`npm run typecheck`、`npm run build`、`npm run smoke:visual`、`npm run smoke:browser` 和 `./mvnw.cmd -q test`。
- 剩余风险：当前效果信号仍是观察性分析，不声称严格因果；还缺“点击但未提交”的学生原因收集。老师对课堂复盘建议的采纳/忽略/修改反馈已在 D5 追加一轮中完成，后续可再把推荐卡片本身的教师反馈独立接入。

### D5: 教师班级复盘建议

- [x] 从班级能力薄弱点生成 1-3 个课堂复盘动作。
- [x] 动作必须包含目标能力、示例题、建议提问和证据来源。
- [x] 教师端在 AI 质量/班级薄弱点旁展示。
- [x] 老师可以对复盘建议记录采纳、忽略或调整。

#### 2026-05-18 执行记录

- `AssignmentOverviewResponse` 新增 `classReviewSuggestions`，每条建议包含标题、目标能力、示例题、证据标签、证据提交、课堂提问、复盘动作和证据摘要。
- `ClassroomService.getAssignmentOverview` 基于现有班级能力薄弱点、高频错因、提交诊断和题目标题生成规则版课堂复盘建议，默认最多 3 条。
- 生成逻辑优先使用班级能力薄弱点，找对应失败提交作为示例证据；若能力点不足，再回退到高频错因生成建议。
- 复盘建议继续复用 `DiagnosisTaxonomy` 和现有 `resolveInterventionSuggestion`，避免另起一套教学话术和标签体系。
- 教师端在“班级能力薄弱点”旁新增“课堂复盘建议”，展示示例题、引导问题、复盘动作和证据摘要。
- 新增/扩展 `ClassroomServiceCorrectionTest`，覆盖复盘建议的示例题、证据标签、证据提交、引导问题和动作。
- 已通过 `./mvnw.cmd -q "-Dtest=ClassroomServiceCorrectionTest" test` 和 `./mvnw.cmd -q test`。
- 2026-05-19 追加一轮：新增 `ClassReviewFeedback`、`ClassReviewFeedbackService`、`ClassReviewFeedbackRepository`、`ClassReviewFeedbackRequest/Response` 和 `POST /api/teacher/assignments/{assignmentId}/class-review-feedback`。
- 课堂复盘建议新增稳定 `suggestionKey` 和 `latestFeedback`，老师记录采纳、忽略或调整后，重新加载作业概览即可看到最新反馈状态。
- 教师工作台“课堂复盘建议”每条建议增加“采纳 / 调整 / 忽略”操作，并把目标能力、示例题、证据标签和教师备注写入反馈记录。
- `ClassroomServiceCorrectionTest` 增加“概览返回复盘建议 key”和“概览带回最新复盘反馈”覆盖。
- `visual-smoke.mjs` 和 `browser-smoke.mjs` 增加 `class-review-feedback` 接口和前端按钮契约检查。
- 已通过 `./mvnw.cmd -q "-Dtest=ClassroomServiceCorrectionTest,AiQualityTrendServiceTest" test`、`npm run typecheck`、`npm run build`、`npm run smoke:visual`、`npm run smoke:browser` 和 `./mvnw.cmd -q test`。
- 剩余风险：当前记录的是老师对建议的动作和备注，还没有把这些反馈自动纳入建议排序或 eval 抽样；下一步可以统计“被忽略最多的建议类型”和“被调整后更常采纳的教学动作”。

### D6: 浏览器视觉 smoke

- [x] 增加项目级 Playwright smoke 脚本或稳定使用 Codex 浏览器插件。
- [x] 覆盖学生页、题目页、教师页桌面/移动端。
- [x] 检查横向溢出、关键按钮可见、AI 面板非空、夜间主题可读。

#### 2026-05-18 执行记录

- 前端新增 `npm run smoke:visual`，运行 `frontend/scripts/visual-smoke.mjs`。
- 当前 smoke 为静态构建产物检查：确认 `src/main/resources/static/app/index.html` 存在、viewport/root 存在、CSS/JS 资源存在。
- 脚本会检查学生能力画像、下一步推荐、AI 质量、课堂复盘建议、教练追问、推荐点击归因、进入题目事件、移动端媒体查询和夜间主题选择器是否进入构建产物。
- 已通过 `npm run smoke:visual` 和 `./mvnw.cmd -q test`。
- 剩余风险：这一轮还不是浏览器截图级 smoke，不能检查真实布局遮挡、横向滚动、按钮可点区域或截图像素。后续如果加入 Playwright/浏览器插件，应升级为桌面/移动端真实页面截图与关键元素可见性检查。

#### 2026-05-19 执行记录

- 前端新增 `npm run smoke:browser`，运行 `frontend/scripts/browser-smoke.mjs`。
- 脚本使用 Playwright 启动真实 Chromium 和 Vite preview，并用 mock API 数据覆盖学生页、题目页、教师工作台、教师管理台。
- 覆盖桌面 `1366x900` 与移动端 `390x844`，每个页面分别检查白天/夜间主题。
- 检查项包括关键 AI 区块可见且非空、关键按钮/控件存在且未横向裁切、页面无横向溢出、页面无运行时异常、暗色主题抽样对比度可读。
- 截图保存到 `output/playwright/`，作为本地验证证据；该目录已加入 `.gitignore`，避免截图产物污染版本库。
- 已通过 `npm run typecheck`、`npm run build`、`npm run smoke:visual`、`npm run smoke:browser` 和 `./mvnw.cmd -q test`。
- 剩余风险：当前浏览器 smoke 使用稳定 mock 数据，适合防止 UI/契约回归；它不能替代真实后端联调、视觉像素基线对比或人工体验评审。
