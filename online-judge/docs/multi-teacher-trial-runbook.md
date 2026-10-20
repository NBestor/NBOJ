# Code 多教师试用版上线手册

## 上线门禁

只有以下项目全部通过，才可邀请 3–5 位教师试点：

- `migrate-seeded-content-to-database` 已完成或由负责人书面冻结；正式题目以数据库为主源，运行时 seed 关闭。
- 备份文件已生成并验证非空；在独立数据库上完成一次恢复演练。
- Flyway 空库迁移、现有库副本 V1 baseline 接管、Hibernate `validate` 均通过。
- bootstrap 管理员已激活，readiness 不存在孤立归属、异常名单或无公共题阻断项。
- Maven、前端 typecheck/build、双语浏览器主流程和依赖审计通过。

## 首次配置

复制 `.env.example`，至少设置：

```env
APP_PROFILE=school
POSTGRES_PASSWORD=<随机强密码>
BOOTSTRAP_ADMIN_USERNAME=<平台管理员用户名>
BOOTSTRAP_ADMIN_PASSWORD=<至少10位且包含字母和数字>
BOOTSTRAP_ADMIN_DISPLAY_NAME=<管理员姓名>
OJ_MODELSCOPE_API_KEY=<平台统一持有的供应商密钥>
```

bootstrap 密码只用于首次激活，不会被记录或由接口返回。管理员激活后应从部署环境移除明文密码，并通过正常改密接口管理账号。

## 强制备份与迁移

`start-school` 会先只启动 Postgres、等待健康检查、生成非空 SQL 备份，再启动应用。备份失败时应用不会启动，Flyway 因而不会执行。

```powershell
.\scripts\start-school.ps1
```

```bash
bash scripts/start-school.sh
```

手工备份可使用：

```powershell
.\scripts\backup-postgres.ps1
```

恢复必须指向仓库内已核验的备份，并显式确认：

```powershell
.\scripts\restore-postgres.ps1 -InputFile backups\onlinejudge-YYYYMMDD-HHMMSS.sql -ConfirmRestore
```

恢复演练必须使用独立数据库或生产卷副本，禁止直接在生产库试验。回滚方式是停止应用并恢复迁移前备份，不执行逆向 Flyway 脚本。

## 首次试点流程

1. 管理员登录并确认 `/api/system/readiness` 为 `READY`。
2. 修复旧数据中 `NEEDS_REVIEW` 或缺少学号的名单。
3. 教师注册后，由管理员审核；教师自行建班、导入名单并轮换班级码。
4. 分别发布一份全班作业和一份定向作业，由学生使用班级码、姓名、学号登录并提交。
5. 教师核对当前完成率、非当前名单历史记录、题库版本和本月 AI 额度。
6. 管理员在 `/actuator/prometheus` 检查登录失败、越权拒绝、名单匹配、提交、AI 成功/延迟和额度拒绝指标。

## 推广判定

试点 1–2 周内必须保持零跨教师数据泄漏、零迁移丢失，且教师无需平台人员代操作即可完成名单和作业主流程。未达到门禁时不得发教研群，也不得宣传支付、外部 Token 或教师自带 Key。
