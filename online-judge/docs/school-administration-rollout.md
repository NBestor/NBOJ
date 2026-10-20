# 学校管理员版本上线手册

本手册适用于将现有 V5 多教师数据库升级到 V8 学校租户结构。V7 会永久清理全部旧 `TEACHER` 数据，必须先在数据库副本演练，并确认备份可恢复。

## 1. 迁移前清单

1. 停止应用写入，记录当前部署版本和数据库地址。
2. 对 Postgres 生成完整、自包含备份，并在隔离实例实际恢复一次。
3. 保存备份文件的 SHA-256、对象存储版本号或不可变备份任务 ID，作为 `LEGACY_TEACHER_BACKUP_PROOF`；不得填写密码、Token 或 API Key。
4. 在只读连接中记录清理数量：

```sql
SELECT count(*) AS teachers FROM teacher_accounts WHERE role = 'TEACHER';
SELECT count(*) AS classes FROM class_groups WHERE owner_teacher_id IN (SELECT id FROM teacher_accounts WHERE role = 'TEACHER');
SELECT count(*) AS assignments FROM assignments WHERE owner_teacher_id IN (SELECT id FROM teacher_accounts WHERE role = 'TEACHER');
SELECT count(*) AS preserved_problems FROM problems
WHERE owner_teacher_id IN (SELECT id FROM teacher_accounts WHERE role = 'TEACHER')
  AND scope IN ('PUBLIC', 'SHARED') AND version_state = 'PUBLISHED';
```

5. 在生产副本先运行 V6–V8，核对 `legacy_teacher_purge_manifests` 和正式内容数量，再安排正式窗口。

## 2. 受控迁移

只有旧教师存在时，V7 才要求下面两个值。缺少任一值，Flyway 会失败关闭且不会清理数据。

```text
LEGACY_TEACHER_PURGE_CONFIRMATION=DELETE_ALL_LEGACY_TEACHERS
LEGACY_TEACHER_BACKUP_PROOF=<已验证备份的不可变 ID 或 SHA-256>
```

启动正式实例时保持 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`，由 Flyway 独占结构变更。迁移成功后核验：

```sql
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
SELECT * FROM legacy_teacher_purge_manifests ORDER BY created_at DESC LIMIT 1;
SELECT role, status, count(*) FROM teacher_accounts GROUP BY role, status;
SELECT count(*) FROM teacher_accounts WHERE role IN ('SCHOOL_ADMIN', 'TEACHER') AND school_id IS NULL;
SELECT count(*) FROM problems WHERE scope IN ('PUBLIC', 'SHARED') AND version_state = 'PUBLISHED';
```

如果迁移或核验失败，停止新版本，不要手工跳过 V7/V8；从已经验证的备份恢复，再定位失败原因。

## 3. 首次启用

1. 使用 bootstrap 平台管理员登录 `/app/platform-admin/login`。现有 `Coder` 会迁移为 `PLATFORM_ADMIN`。
2. 创建试点学校，安全交付一次性校管临时密码和学校注册码；两者不会再次显示。
3. 校管从 `/app/school-admin/login` 首次登录并强制改密。
4. 教师从 `/app/teacher/login` 使用学校注册码申请；校管审核后按需分配额度，默认额度为 0。
5. 校管验证班级、名单、作业和提交只读下钻与 CSV 导出；平台管理员只核验学校汇总，不进入教学明细。

## 4. 上线验收与回退

- `/api/system/readiness` 显示 Flyway V8、有效平台管理员、学校租户/额度结构和内容库正常。
- 跨校猜测教师、班级、学生、作业和提交 ID 均返回 403 或 404。
- 停用试点学校后，校管、教师和学生会话立即失效，登录、提交与外部 AI 被阻断。
- 并发分配不超过学校额度；教师额度不能低于已用加预留；月底按 `Asia/Shanghai` 自然月重置且不结转。
- 如出现数据范围或迁移异常，立即停止应用并从迁移前备份恢复。V6–V8 不提供向下 SQL 回滚。
