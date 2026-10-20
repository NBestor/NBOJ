# Code 在线编程平台

Code 面向课堂教学、算法训练和信息竞赛，将在线判题、AI 诊断、学生学习与教师教学连接起来。学生提交代码后获得判题与诊断反馈，教师从同一批学习记录中了解学生问题，标准知识库为诊断和教学提供依据。

网站入口：[Code 在线编程平台](https://tuotuzju.com/code/)。

## 运行与开发

应用位于 `online-judge/`，不是仓库根目录。先进入该目录，再按[运行说明](online-judge/README.md)选择对应操作：

```bash
cd online-judge
```

- [本机开发与测试](online-judge/README.md#本机开发)：环境要求、前后端构建、启动与测试。
- [学校部署](online-judge/README.md#学校开箱部署)：配置、环境检查、镜像构建与启动。
- [生产发布](online-judge/README.md#生产发布)：备份、镜像替换、验证与恢复。普通 Git 推送不等于生产发布。
- [数据库迁移与恢复](online-judge/docs/database-migration-guide.md)：已有数据库接入、结构变更与恢复步骤。

运行命令与配置说明统一维护在上述位置，根目录不再复制一套。真实口令与密钥保存在本地环境或部署平台，不写入仓库。

## 工程位置

| 位置 | 内容 |
| --- | --- |
| `online-judge/frontend/` | React/Vite 前端 |
| `online-judge/src/main/java/` | Spring Boot 后端 |
| `online-judge/src/main/resources/` | 应用配置、资源与数据库迁移 |
| `online-judge/scripts/` | 构建、启动、测试与发布脚本 |
| `online-judge/docs/` | 运行、迁移和排障说明 |
| `docs/` | 项目整体设计、功能设计与历史参考 |

学校部署使用 PostgreSQL，H2 用于本机开发；具体环境与运行方式见子项目说明。

## 项目文档

- [项目认知](docs/项目认知.md)：整体定位、内容、逻辑、交互与技术设计，以及当前维护状态。
- [功能设计](docs/项目认知.md#六功能设计入口)：提交与诊断、学生学习、教师教学、标准库、发布与运行、学校与教师管理。
- [项目协作规则](AGENTS.md)：项目边界、文档维护与协作约定。
- [历史专项资料](docs/specs/)：带日期的设计和评测材料，仅用于追溯。

设计描述已确认的要求，不代表所有能力已经实现或通过生产验证；实际进展以当前代码、运行结果和相应验证记录为准。

## 许可

当前仓库未附带 License 文件。
