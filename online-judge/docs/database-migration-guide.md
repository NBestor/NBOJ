# 数据库迁移与恢复指南

## 1. 权威边界

学校与生产 PostgreSQL 的 Schema 以 `src/main/resources/db/migration/` 为唯一发布源：

- Flyway 负责按版本执行 DDL，并在 `flyway_schema_history` 中记录版本、校验值和结果。
- Hibernate 在 `school` profile 下使用 `ddl-auto=validate`，只校验实体与数据库是否一致。
- H2 继续提供本地快速开发和测试，但不是生产 Schema 的权威来源。
- 正式题库、知识树和 AI 标准库内容仍以数据库为主库，不得放回 Java seed 或资源 seed。

## 2. 迁移文件规则

当前完整基线是：

```text
src/main/resources/db/migration/V1__baseline_schema.sql
```

后续结构变化只能新增文件：

```text
V2__add_example_column.sql
V3__create_example_index.sql
```

已经进入 `main`、可能被任一数据库执行的迁移不得修改、重命名、重排或删除。修改旧迁移会造成校验值不一致，正式应用应拒绝启动。

破坏性变更采用 expand/contract：

1. 新增兼容字段或表。
2. 发布同时兼容新旧结构的应用。
3. 分批迁移并校验数据。
4. 确认旧应用和旧读取退出。
5. 在后续独立版本删除旧结构。

## 3. 全新空数据库

全新 PostgreSQL 不需要手工建表，也不需要开启基线开关。应用第一次以 `school` profile 启动时，Flyway 会执行 V1，随后 Hibernate `validate`。

验收：

```bash
bash scripts/check-database-schema-readiness.sh
bash scripts/check-database-content-readiness.sh
```

空 Schema 建成后没有正式题库内容是正常的；Schema readiness 与内容 readiness 是两条不同门禁。

## 4. 已有非空数据库第一次接入

### 4.1 前置条件

- 使用已经外部构建并验证的新应用镜像。
- PostgreSQL 容器健康，数据库 Volume 明确且不得删除。
- 服务器磁盘足够保存新备份、审计文件和回滚镜像。
- 已记录服务器现有未提交配置，不覆盖 `.env` 或本地安全配置。
- `.env` 中 `FLYWAY_BASELINE_ON_MIGRATE=false`。

### 4.2 执行

```bash
bash scripts/baseline-postgres-flyway.sh --confirm-baseline
```

脚本依次执行：

1. 检查 31 张关键业务表、关键列和关键索引。
2. 确认数据库尚无 `flyway_schema_history`。
3. 创建 custom-format 备份，验证 `pg_restore --list`、SHA-256 和元数据。
4. 保存关键表迁移前计数。
5. 仅在一次性 app 进程中启用 `FLYWAY_BASELINE_ON_MIGRATE=true`。
6. Flyway 登记 `BASELINE V1`，不执行 V1 建表 SQL。
7. Hibernate `validate` 通过后退出一次性进程。
8. 检查 Flyway 历史、关键结构和迁移后计数。

任何一步失败都不得继续替换正式 app 容器。

### 4.3 基线后的正式配置

基线完成后继续保持：

```env
FLYWAY_BASELINE_ON_MIGRATE=false
```

以后部署新版本时，Flyway只执行 V2、V3 等尚未应用的迁移。不要重复运行基线脚本。

## 5. 备份与校验

创建备份：

```bash
bash scripts/backup-postgres.sh
```

输出包括：

```text
onlinejudge-YYYYMMDD-HHMMSS.dump
onlinejudge-YYYYMMDD-HHMMSS.dump.sha256
onlinejudge-YYYYMMDD-HHMMSS.dump.meta
```

单独复核：

```bash
bash scripts/verify-postgres-backup.sh backups/onlinejudge-YYYYMMDD-HHMMSS.dump
```

只有文件非空、SHA-256 匹配、元数据存在且 `pg_restore --list` 可以读取时，才算备份成功。

## 6. 隔离恢复演练

```bash
bash scripts/rehearse-postgres-restore.sh backups/onlinejudge-YYYYMMDD-HHMMSS.dump
```

演练会启动一个不挂载正式 Volume 的临时 PostgreSQL 容器，恢复归档并查询关键表、数据计数和 Flyway 历史状态，结束后删除临时容器。演练不连接正式数据库。

建议：

- 重要迁移前执行一次。
- 日常备份至少定期抽样执行。
- 记录演练时间、备份文件和结果。

## 7. 正式恢复

正式恢复会覆盖数据库对象，必须先进入维护窗口并停止 app 容器：

```bash
docker compose stop app
bash scripts/restore-postgres.sh --confirm-restore backups/onlinejudge-YYYYMMDD-HHMMSS.dump
```

恢复后依次检查：

```bash
bash scripts/check-database-schema-readiness.sh
bash scripts/check-database-content-readiness.sh
bash scripts/start-school.sh
```

然后验证 readiness、代表性题目、知识树导航、标准库导航和一条判题链路。不要使用 `docker compose down -v`、`docker volume prune` 或广泛的 `docker system prune`。

## 8. 开发与提交门禁

每次增加迁移或改变实体映射，至少运行：

```bash
bash scripts/test-postgres-migrations.sh
./mvnw -q -Dskip.frontend=true test
```

PostgreSQL 迁移测试覆盖：

- 空库执行完整 V1。
- 重复启动不重复迁移。
- 非空旧库在无授权时拒绝启动。
- 显式基线只登记历史、不重放 V1。
- 缺少关键列时 Hibernate `validate` 阻止启动。

H2 测试通过但 PostgreSQL 迁移测试失败时，该变更仍然视为未通过。
