# 测试运行折叠面板 Design QA

- source visual truth: `C:/Users/Lenovo/AppData/Local/Temp/codex-clipboard-fc44fce2-1727-45e5-8482-299d3f5a3ee8.png`（1038 × 796）
- implementation collapsed: `D:/WenCode/Code/online-judge/frontend/output/code-run-ui/implementation-collapsed.png`（848 × 49）
- implementation expanded: `D:/WenCode/Code/online-judge/frontend/output/code-run-ui/implementation-expanded.png`（848 × 273）
- implementation mobile collapsed: `D:/WenCode/Code/online-judge/frontend/output/code-run-ui/implementation-mobile-collapsed.png`（368 × 49）
- implementation mobile expanded: `D:/WenCode/Code/online-judge/frontend/output/code-run-ui/implementation-mobile-expanded.png`（368 × 355）
- combined comparison input: `D:/WenCode/Code/online-judge/frontend/output/code-run-ui/comparison.png`（1800 × 1050）
- browser viewports: desktop 2400 × 1200，mobile 390 × 844
- verified URL: `http://127.0.0.1:8081/app/student/assignments/7/problems/101?studentProfileId=41`

## Findings

最终对照没有剩余 P0、P1 或 P2 问题。

- 信息层级与参考图一致：常态仅显示“测试运行”标题和右侧下拉箭头，输入、操作和结果在展开后出现。
- 已删除“快速验证”“自定义输入运行”“临时运行”、功能说明和 `stdin` 提示，避免与标题及字段标签重复。
- 展开区保留现有产品按钮、颜色、圆角和暗色模式变量，没有引入参考站点的绿色品牌色，避免破坏当前设计系统。
- 桌面端操作保持同一行，运行按钮位于右侧；移动端操作纵向排列，无横向溢出。
- 第一轮移动端截图发现收起面板被父级网格拉高；增加 `align-self: start` 后收敛到 49px 单行高度，并完成复测。
- 折叠按钮支持键盘焦点、`aria-expanded`、`aria-controls` 和中英文可访问名称；展开箭头随状态旋转。
- 切换题目会重置为收起状态；运行、样例载入、标准输出和内容过期提示保持原有行为。

## Interactions and runtime checks

- 默认状态断言为收起，点击后切换为展开，再次点击可收起。
- 载入首个样例后输入为 `3 5`，运行请求返回标准输出 `8`，未打开正式提交结果弹窗。
- 桌面与移动视口均通过聚焦浏览器用例；聚焦用例捕获的 console/page errors 为 0。
- 全量浏览器 smoke 覆盖手机、平板、桌面、亮色、暗色和英文场景，共 1141 项通过；其中包含测试运行操作与最近提交结果互不重叠的几何断言。
- 静态视觉 smoke 28 项通过；TypeScript typecheck 与生产构建通过。
- `npm audit --audit-level=high` 因当前 `npmmirror` 不实现 npm audit API 而无法取得报告；本次未新增或升级依赖。

## Visual comparison conclusion

组合图同时包含用户参考图、当前默认收起状态和展开状态。实现复用了参考图的核心交互与布局关系：一行折叠标题、展开后的大输入区、底部操作区；同时按现有产品设计系统收紧了边框、间距和按钮视觉。未发现裁切、错位、异常留白、文字溢出或移动端横向滚动。

final result: passed
