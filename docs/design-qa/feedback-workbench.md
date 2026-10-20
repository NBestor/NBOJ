# 代码联动修正工作台 Design QA（已归档）

- source visual truth path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-concept.png`
- implementation screenshot path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-implementation-desktop.png`
- mobile screenshot path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-implementation-mobile.png`
- dark screenshot path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-implementation-dark.png`
- English screenshot path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-implementation-english.png`
- full-view comparison path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-comparison.png`
- user scroll reference path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-scroll-reference.png`
- user action reference path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-action-reference.png`
- focused scroll comparison path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-scroll-comparison.png`
- focused action comparison path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-action-comparison.png`
- user growth-detail reference path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-growth-full-reference.png`
- growth inspector implementation path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-growth-inspector.png`
- growth-detail comparison path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-growth-comparison.png`
- user knowledge-path reference path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-knowledge-path-reference.png`
- knowledge-path implementation crop: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-knowledge-path-implementation.png`
- focused knowledge-path comparison path: `D:/WenCode/Code/docs/ui-mockups/feedback-workbench-knowledge-path-comparison.png`
- viewport: desktop `1487 × 1058`，mobile `390 × 844`
- state: 未通过提交；2 个修正问题、2 个提升建议；64 行长代码滚动到问题 2 的第 9 行

## Findings

最终对比没有剩余 P0、P1 或 P2 问题。

- 信息架构：实现保持方案三的左侧状态与问题导航、中间代码证据、右侧聚焦诊断三栏结构；修正工作台与成长仪表盘仍使用现有页签模式。
- 颜色联动：问题 1 使用橙色，问题 2 使用青色；颜色和编号同时出现在问题卡片、代码行、行号锚点、内联标签与右侧诊断中，不依赖颜色作为唯一识别方式。
- 代码定位：模型返回的 `evidenceSnippets` 或 `code:line:n` 会映射为真实代码行；范围会按源代码总行数收敛，缺少可靠证据时不会伪造定位。
- 代码滚动：桌面代码证据区保持在 500–620px、平板保持在 400–500px 的可读窗口内，长代码使用独立纵向滚动；点击问题或提升建议只滚动中间代码区，不会推动整页或左右栏。
- 信息密度：当前问题的原因、修改建议、验证方式、证据定位与知识路径集中在右侧；左侧只承担状态和切换，避免重复展示同一段诊断。
- 提升建议：左侧改为与问题清单一致的编号卡片，只展示标题与代码定位；点击后右侧展示建议正文、证据定位与知识路径，中间同步切换为蓝色或紫色代码证据。
- 知识路径：连续文本已拆成有边框的层级标签，并以 Lucide 方向箭头连接；标签继承当前问题/建议的语义色，长路径按“箭头 + 后一标签”为一组自然换行。
- 操作收敛：移除“已理解代码范围 / 已准备重新运行测试”两个确认项；该动作不改变系统状态，也不再占用右侧诊断空间。
- 后续动作：只读代码不再出现“运行测试”或“运行并验证”；唯一下一步是右侧“返回代码修改”，避免重复执行未修改的提交。
- 响应式：桌面端三栏无横向溢出；移动端按“问题清单 → 代码 → 诊断”纵向排列，代码区域保留独立横向阅读能力。
- 主题和多语言：亮色、暗色、中英文界面均通过浏览器场景验证；动态诊断内容保留后端原文属于预期行为。
- 可访问性：问题卡、提升建议卡、代码锚点和内联标签均为按钮；当前条目使用 `aria-pressed`，右侧诊断使用 `aria-live`，颜色之外同时保留编号与文字标签。

## Full-view comparison evidence

组合图左侧为概念图，右侧为同尺寸浏览器实现。三栏比例、代码区主视觉、问题颜色映射和失败用例保持一致。根据用户最新反馈，实现将工作台总高度压缩到最多 760px，并让代码区独立滚动；概念图中的运行操作被右侧“返回代码修改”取代。这两处差异是对已验证使用问题的有意修正。

focused action comparison 左侧为用户标注的旧“运行测试”入口，右侧为实现：代码头只保留语言信息，下一步集中到右侧“返回代码修改”。focused scroll comparison 左侧为撑满页面的旧代码区，右侧为固定窗口并已滚动到第 9 行的实现。

growth-detail comparison 左侧为上一版：提升建议折叠在左栏、右侧保留无状态价值的确认项；右侧为本轮实现：提升建议成为紧凑可选清单，选中后以独立颜色联动中间代码，并把完整内容放到最右诊断栏。对照后未发现裁切、错位、文字溢出或颜色映射错误。

knowledge-path focused comparison 上方为用户提供的连续文字路径，下方为实现后的标签路径。实现保留原有标题、层级顺序和状态标签，同时通过方框、箭头和语义色提高扫描性；五级路径在右栏宽度内换成两行，未产生横向溢出。该区域不含图像资产，使用产品现有 Lucide 图标库的 `ChevronRight`，未使用字符箭头或自绘图形。

## Comparison history

### Iteration 1

- 同尺寸对比确认概念图的核心三栏关系已落地；未发现需要返工的 P0/P1/P2 视觉差异。
- 以浏览器交互断言复核：点击第二个问题后，左侧、代码区、右侧均切换为青色；点击代码内联标签可反向切换聚焦问题；返回代码后页面滚动状态恢复。

### Iteration 2

- [P2] 长代码把工作台撑到 1680px，左右上下文难以同时保持在视野内；修复为桌面最大 760px 的工作台和独立代码滚动区。
- [P2] 只读代码上的“运行测试 / 运行并验证”会原样重跑旧提交；移除两个入口，只保留“返回代码修改”。
- [P2] 平板宽度下页签底部与内容有 12px 视觉重叠；收紧平板页头底部留白后通过回归。
- post-fix evidence: 浏览器实测代码区 `scrollHeight > clientHeight` 且 `scrollTop` 可独立变化；运行按钮数量为 0，返回修改按钮数量为 1。

### Iteration 3

- [P2] 右侧两个确认项不产生新状态，也不影响后续动作；已删除并保留唯一“返回代码修改”入口。
- [P2] 提升建议在左栏展开正文导致信息重复、列表过长；已改为与问题清单一致的可选卡片，正文、证据和知识路径统一放到右栏。
- [P2] 提升建议此前缺少代码颜色联动；现在与问题共用证据映射，建议 1/2 分别使用蓝色/紫色，并在代码行、锚点、内联标签和右侧诊断保持一致。
- [P2] 平板点击条目时不应改变整页滚动位置；代码定位改为只控制代码容器的 `scrollTop`，并将平板代码窗口收敛到 400–500px。
- post-fix evidence: 2 个问题卡、2 个提升卡均可切换；提升正文不再出现在左栏；右栏显示“提升建议 1 / 2”；确认项数量为 0；手机、平板、桌面场景通过。

### Iteration 4

- [P2] 知识路径为一段连续文字，层级扫描成本较高，换行后不易判断节点关系；改为独立方框标签，并使用方向箭头明确顺序。
- [P2] 五级路径在窄栏中需要稳定换行；将“箭头 + 后一标签”放在同一弹性单元内，避免箭头脱离目标标签，并为长标签启用安全断行。
- post-fix evidence: 桌面五级路径展示 5 个标签和 4 个箭头，三节点提升路径展示 3 个标签和 2 个箭头；手机、平板、桌面均满足 `scrollWidth <= clientWidth`，亮色、暗色、中英文状态通过全量回归。

## Primary interactions tested

- 左侧问题卡切换问题 1 / 问题 2。
- 左侧提升建议卡切换建议 1 / 建议 2，并在右侧打开完整建议。
- 知识路径标签数量、顺序、箭头数量及窄屏无横向溢出。
- 中间代码锚点和内联标签反向聚焦右侧诊断。
- 代码证据独立纵向滚动、返回代码修改。
- 亮色 / 暗色切换。
- 中文 / 英文切换。
- 桌面、平板与手机视口。
- 关闭弹窗前后的页面滚动锁定与恢复。
- 浏览器回归：1108 项检查通过。

## Verification

- `npm.cmd run test:feedback-workbench`：2/2 通过。
- `npm.cmd run typecheck`：通过。
- `npm.cmd run build`：通过，仅保留既有的大 chunk 提示。
- `npm.cmd run smoke:visual`：28 项通过。
- `npm.cmd run smoke:browser`：1108 项通过。
- `npm.cmd audit --audit-level=high`：镜像源不实现 npm audit API，无法取得审计结果；未发现新增依赖。

final result: passed
