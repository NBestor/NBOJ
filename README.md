# NBOJ

NBOJ is an AI-powered online judge platform for problem management, code evaluation, leaderboard tracking, and intelligent feedback.

NBOJ 是一个集题目管理、在线评测、排行榜与 AI 分析于一体的智能 OJ 项目，面向算法练习、课程实验与个人作品展示场景。

## Highlights | 项目亮点

- AI-powered submission analysis, comparison, and growth reports
- Problem creation workflow with Markdown statement editing and testcase management
- Leaderboard and submission history for problem-level tracking
- Local-first development setup based on Spring Boot + H2
- 中文界面友好，适合课程项目、毕业设计与个人作品集展示

## Tech Stack | 技术栈

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Validation
- H2 Database
- HTML / CSS / JavaScript
- Apache PDFBox
- Lombok

## Project Structure | 项目结构

```text
src/main/java/com/onlinejudge
|- execution        Code execution capability
|- leaderboard      Leaderboard APIs and application services
|- problem          Problem management domain
|- report           Growth report generation
|- shared           Shared bootstrap and web utilities
|- submission       Submission, judging, AI analysis, and comparison

src/main/resources
|- application.yml
|- static
   |- index.html
   |- leaderboard.html
   |- problem.html
   |- problem-create.html
   |- assets
```

## Requirements | 运行要求

Please make sure the following tools are installed:

请先确保本机安装了以下环境：

- Java 17+
- Maven, or use the bundled Maven Wrapper
- At least one runtime/compiler for judging:
- Python: `python`
- Java: `javac` / `java`
- C / C++: `gcc` / `g++`
- JavaScript: `node`

## Quick Start | 快速启动

Run the project in development mode:

开发模式启动：

```powershell
./mvnw.cmd spring-boot:run
```

Package and run the jar:

打包后启动：

```powershell
./mvnw.cmd clean package -DskipTests
java -jar target/nboj-1.0.0.jar
```

Default URLs | 默认访问地址：

- Home: `http://localhost:8081/`
- Problem editor: `http://localhost:8081/problem-create.html`
- Leaderboard: `http://localhost:8081/leaderboard.html`
- H2 Console: `http://localhost:8081/h2-console`

## AI Configuration | AI 配置

NBOJ currently uses a ModelScope OpenAI-compatible API endpoint.

当前项目默认接入 ModelScope 的 OpenAI 兼容接口。

Configuration file:

配置文件位置：

`src/main/resources/application.yml`

```yaml
ai:
  enabled: true
  base-url: https://api-inference.modelscope.cn/v1
  api-key: ${MODELSCOPE_API_KEY:}
  model: MiniMax/MiniMax-M2.7
```

Set your token before startup:

启动前设置环境变量：

```powershell
$env:MODELSCOPE_API_KEY="your-token"
```

If the token is missing, the core judge flow can still run, but AI-related features may fall back or become unavailable.

如果没有配置 Token，基础评测通常仍可运行，但 AI 相关能力可能不可用或退化为非模型分析流程。

## Core Features | 核心功能

- Problem catalog browsing and filtering
- Problem creation and editing
- Sample and hidden testcase management
- Multi-language submission and judging
- AI analysis for failed or accepted submissions
- Submission comparison and growth report export

## Persistence | 数据持久化

The default H2 file database is stored at:

默认 H2 文件数据库位于：

```text
data/onlinejudge.mv.db
```

This means problem data, submission records, and related analysis can be persisted locally during development.

这意味着题目、提交记录与分析数据都会在本地开发环境中持久保存。

## Build Check | 构建校验

Recommended verification commands:

建议执行以下校验命令：

```powershell
./mvnw.cmd -q -DskipTests compile
node --check src/main/resources/static/assets/js/core/ui.js
node --check src/main/resources/static/assets/js/pages/index-page.js
node --check src/main/resources/static/assets/js/pages/leaderboard-page.js
node --check src/main/resources/static/assets/js/pages/problem-page.js
node --check src/main/resources/static/assets/js/pages/problem-form-page.js
```

## Positioning | 项目定位

NBOJ is suitable for:

NBOJ 适合这些场景：

- Graduation project / 毕业设计
- Course project / 课程作业
- Personal portfolio / 个人作品集
- Internal demo for AI-enhanced judging workflows / AI 评测流程演示

## License | 许可

No license file is included yet.

当前仓库暂未附带 License 文件，可根据你的开源或私有发布计划后续补充。
