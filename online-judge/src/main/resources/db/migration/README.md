# 数据库迁移规则

本目录是学校与生产 PostgreSQL Schema 的唯一发布源。

## 命名

- 基线文件固定为 `V1__baseline_schema.sql`。
- 后续版本使用 `V2__short_description.sql`、`V3__short_description.sql`，版本只增不减。
- 描述使用小写英文和下划线，正文 SQL 与必要注释可以使用中文。

## 不可变约束

- 已经进入 `main` 并可能被任一数据库执行的迁移不得修改、重命名、重排或删除。
- Schema 变化必须新增迁移文件，不得重新生成或覆盖 V1。
- 禁止恢复 Hibernate `ddl-auto=update` 作为学校环境改表方式。
- 破坏性变更采用 expand/contract：先增加兼容结构并迁移数据，确认旧代码退出后再在后续版本删除旧结构。

## 验证

每次增加迁移后必须执行：

```bash
bash scripts/test-postgres-migrations.sh
```

该验证会在隔离 PostgreSQL 中执行完整迁移链、重复启动和 Hibernate Schema 校验。H2 测试只能提供快速反馈，不能替代 PostgreSQL 迁移验证。
