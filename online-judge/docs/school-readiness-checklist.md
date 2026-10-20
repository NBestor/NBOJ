# 高中单校内网投用清单

这份清单用于一所学校内网试点。目标是能安全上课，不覆盖公网 SaaS、多校租户或商业合规场景。

## 1. 部署前

1. 复制配置。

```bash
cp .env.example .env
```

2. 修改 `.env` 中的真实值：

- `APP_PROFILE=school`
- `EXECUTOR_MODE=docker`
- `POSTGRES_PASSWORD`
- `BOOTSTRAP_ADMIN_USERNAME`
- `BOOTSTRAP_ADMIN_PASSWORD`（仅首次激活，激活后从环境移除）
- 如需外部 AI：`AI_ENABLED=true`、`OJ_MODELSCOPE_API_KEY`

3. 运行预检。

```bash
bash scripts/doctor-school.sh
```

预检失败时不要开课。先按提示处理 Docker、镜像源、Postgres 密码或密钥配置。

4. 确认 AI 诊断链路开关。

```env
AI_DIAGNOSIS_REPORT_V3_ENABLED=true
AI_MODEL_FAILURE_DEGRADE_ENABLED=true
AI_STANDARD_LIBRARY_GROWTH_ENABLED=true
AI_STANDARD_LIBRARY_AUTO_MERGE_ENABLED=false
```

默认建议保持“成长 Agent 进入候选池、自动入库关闭”。这样 AI 可以根据真实诊断提出扩库建议，但不会绕过老师直接改正式标准库。

## 2. 启动

推送或合并 `main` 不会自动部署到学校服务器。需要使用已经加载的镜像时，直接运行安全启动脚本；只有明确接受服务器受控构建成本并预留维护窗口时，才人工触发 GitHub Actions 的 `Deploy online judge manually`，不得把它恢复成 push 自动部署。

```bash
bash scripts/start-school.sh
```

访问：

```text
http://服务器局域网IP:8081/code/
```

平台管理员和学校管理员使用独立账号登录，教师注册后经本校管理员审核。教师进入 `/code/teacher-management` 查看“开课状态”；只有 `READY` 或明确接受 `DEGRADED` 时才上课。

## 3. AI 数据外发

启用外部 AI 后，系统会把学生提交代码、题目信息、评测结果、测试点摘要和诊断证据发送到配置的模型服务，用于生成学生反馈。

不发送教师密码、学生会话、数据库密码或系统密钥。

如果外部 AI 返回 401、429、超时或格式错误，系统仍可判题，但不能宣称 AI 能力可用，也不建议作为高中 AI 试点版开课。

如果学校不允许学生代码外发，保持：

```env
AI_ENABLED=false
```

## 4. AI 诊断与标准库成长

当前正式链路是：

```text
学生提交 -> 初步诊断 -> AI 标准库导航 -> 最终诊断 -> 学生三段反馈 -> 库外发现进入扩库候选池
```

教师端 `/code/teacher/manage/ai-library` 会显示两类内容：

- 正式标准库：当前 AI 可用的能力点、易错点和知识锚点。
- 扩库候选：AI 在 `PARTIAL` 或 `MISS` 场景发现的新细颗粒错因。

处理规则：

- `PROPOSED`：可由教师合并或忽略。
- `NEEDS_REVIEW`：候选方向有价值，但入库门禁未完全通过，需要编辑后再处理。
- `BLOCKED`：缺来源、缺路径、重复、冲突或置信度不足，不能直接入库。
- `MERGED`：已写入正式标准库。
- `IGNORED`：教师判断暂不采用。

回滚方式：

- 候选未合并前：忽略候选即可回滚。
- 候选已合并后：到正式标准库中停用或删除对应条目，并保留候选审计记录中的 `beforeSnapshot`、`diffSummary`、`rollbackInfo`。
- 自动入库默认关闭；只有学校明确接受风险，并把 `AI_STANDARD_LIBRARY_AUTO_MERGE_ENABLED=true` 后，系统才会在高置信、无冲突、来源完整时尝试自动写入正式库。

## 5. 数据备份

学校部署使用 PostgreSQL。Schema 由 Flyway 版本化迁移，Hibernate 在 `school` profile 下只执行 `validate`，不会自动改表。

已有非空数据库第一次接入 Flyway 时，必须使用受控基线入口：

```bash
bash scripts/baseline-postgres-flyway.sh --confirm-baseline
```

该脚本会先检查关键结构，创建并验证备份，记录迁移前计数，然后只登记 V1 基线。正式 `.env` 中的 `FLYWAY_BASELINE_ON_MIGRATE` 必须保持 `false`。

启动脚本会在应用启动、Flyway 执行前强制备份；另建议每天课后生成一次可验证备份：

```bash
bash scripts/backup-postgres.sh
```

新备份采用 PostgreSQL custom format，同时生成 `.sha256` 和 `.meta`。定期执行不接触正式 Volume 的恢复演练：

```bash
bash scripts/rehearse-postgres-restore.sh backups/onlinejudge-YYYYMMDD-HHMMSS.dump
```

正式恢复前先停止 app 容器，并显式确认破坏性操作：

```bash
bash scripts/restore-postgres.sh --confirm-restore backups/onlinejudge-YYYYMMDD-HHMMSS.dump
```

历史 `.sql` 仍可兼容恢复，但缺少 custom-format 归档目录校验。备份文件和迁移审计默认在 `backups/`，不要提交到 Git。完整流程见 [数据库迁移与恢复指南](database-migration-guide.md)。

## 6. 常见故障

- `Docker daemon is not available`：启动 Docker Desktop、OrbStack 或 Docker Engine。
- `C++17 runner image is not built yet`：运行 `bash scripts/build-cpp17-runner.sh` 或重新 `bash scripts/start-school.sh`。
- AI smoke 显示 `INSUFFICIENT_QUOTA`：ModelScope 额度不足，充值或关闭 AI。
- AI smoke 显示认证失败：检查 `OJ_MODELSCOPE_API_KEY`。
- readiness 显示 `AI 诊断报告 v3` 失败：检查 `AI_DIAGNOSIS_REPORT_V3_ENABLED=true`。
- readiness 显示成长 Agent 提醒：检查是否符合学校对自动扩库权限的要求。
- 管理员无法进入：首次部署检查 bootstrap 环境变量与 readiness；账号激活后使用数据库账号登录。

## 7. 最低验收

- `bash scripts/doctor-school.sh` 通过。
- 教师端能登录。
- `/api/system/readiness` 返回 `READY`，或只剩学校明确接受的非阻断提醒。
- Python 提交可 AC/WA。
- C++17 提交在 runner 就绪后可 AC。
- AI smoke 通过；如果 smoke 不通过，系统只能作为判题系统使用，不能宣称 AI 诊断能力已可交付。
