# 自动兜底标准库静态备份

本目录保存 `remove-generated-fallback-archive-runtime` 变更删除运行时兜底库前的快照。

- 生成日期：2026-07-06
- 来源：删除前的 `AiStandardLibrarySeedCatalog.archivedGeneratedFallbackSeeds()`
- 条目总数：1156
- 能力点：578
- 易错点：578

使用边界：

- 本目录只作为冷备份与审计材料，可以随 Git 或云端同步。
- 后端、前端、Seeder、候选包、AI 上下文和质量报告不得读取或导入本目录内容。
- 如果未来要重新利用其中素材，应先人工筛选、重写、测试，再进入正式手写标准库文件。
