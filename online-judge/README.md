# Online Judge

一个面向中文场景的轻量在线评测系统，支持题库管理、代码评测、排行榜、提交历史、AI 分析和成长报告导出。

这次工程化整理后的目标是:

- 后端按业务域分包，减少“所有 controller/service/repository 平铺在一起”的维护成本
- 前端静态资源按入口页、公共资源、页面脚本、第三方库分层
- 删除明显未使用接口和历史残留代码
- 在不影响现有功能的前提下，尽量保持依赖精简

## 技术栈

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Validation
- H2
- 原生 HTML / CSS / JavaScript
- Apache PDFBox
- Lombok

## 运行要求

需要本机已安装:

- Java 17+
- Maven
- 至少一种代码执行环境
  - Python: `python`
  - Java: `javac` / `java`
  - C / C++: `gcc` / `g++`
  - JavaScript: `node`

## 启动方式

在项目目录执行:

```powershell
./mvnw.cmd spring-boot:run
```

或先打包再启动:

```powershell
./mvnw.cmd clean package -DskipTests
java -jar target/online-judge-1.0.0.jar
```

默认访问地址:

- 首页: `http://localhost:8081/`
- 创建 / 编辑题目: `http://localhost:8081/problem-create.html`
- 排行榜: `http://localhost:8081/leaderboard.html`
- H2 控制台: `http://localhost:8081/h2-console`

## AI 配置

项目接入了 ModelScope 的 OpenAI 兼容接口，配置位于 `src/main/resources/application.yml`:

```yaml
ai:
  enabled: true
  base-url: https://api-inference.modelscope.cn/v1
  api-key: ${MODELSCOPE_API_KEY:}
  model: MiniMax/MiniMax-M2.7
```

启动前设置环境变量:

```powershell
$env:MODELSCOPE_API_KEY="你的 ModelScope Token"
```

如果未配置该变量，系统会回退到规则分析，不影响基础评测。

## 工程结构

### 后端

后端不再按 `controller / service / repository / model` 技术层平铺，而是按业务域组织:

```text
src/main/java/com/onlinejudge
├─ execution                  # 代码执行能力
├─ leaderboard
│  ├─ api                     # 排行榜接口
│  ├─ application             # 排行榜用例服务
│  ├─ dto                     # 排行榜返回模型
│  └─ persistence             # 排行榜统计投影
├─ problem
│  ├─ api                     # 题目接口
│  ├─ application             # 题目管理服务
│  ├─ domain                  # Problem / TestCase
│  ├─ dto                     # 创建、详情、管理等模型
│  └─ persistence             # 仓储与题库投影
├─ report
│  ├─ application             # 成长报告服务
│  └─ dto
├─ shared
│  ├─ bootstrap               # 初始化数据
│  └─ web                     # 全局异常处理等共享 Web 能力
└─ submission
   ├─ api                     # 提交、分析、对比接口
   ├─ application             # 评测、AI 分析、差异比较等服务
   ├─ domain                  # Submission / Analysis / CaseResult
   ├─ dto                     # 提交与分析响应模型
   └─ persistence             # 提交相关仓储
```

这样调整后，每个业务能力的控制器、服务、DTO 和持久层都能收拢在一起，查问题和扩展功能会更直接。

### 前端

前端静态资源改成“页面入口 + assets 资源目录”的方式组织:

```text
src/main/resources/static
├─ index.html
├─ leaderboard.html
├─ problem.html
├─ problem-create.html
└─ assets
   ├─ css
   │  └─ app.css
   ├─ js
   │  ├─ core
   │  │  └─ ui.js
   │  └─ pages
   │     ├─ index-page.js
   │     ├─ leaderboard-page.js
   │     ├─ problem-form-page.js
   │     └─ problem-page.js
   └─ vendor
      └─ jszip.min.js
```

入口 HTML 只负责页面结构和资源引用，页面逻辑全部外置，便于后续继续组件化或接入构建工具。

## 本次工程化调整

- 后端包结构改为按业务域分包
- 前端静态资源集中到 `assets/` 目录
- 首页和排行榜移除了内联脚本
- 删除了明显未使用的接口和服务方法
- 保留当前仍在使用的依赖，避免为了“看起来少”而误删功能依赖

## 依赖说明

当前依赖已经比较克制:

- `spring-boot-starter-web`: Web API
- `spring-boot-starter-data-jpa`: 数据访问
- `spring-boot-starter-validation`: 请求校验
- `h2`: 本地持久化
- `pdfbox`: PDF 导出成长报告
- `lombok`: 降低 DTO 和服务构造器样板代码

暂未继续裁剪的原因是这些依赖都仍有实际使用点。

## 持久化

默认 H2 文件数据库位于:

```text
data/onlinejudge.mv.db
```

这意味着:

- 新建题目会持久保留
- 提交记录与分析结果会持久保留
- 删除题目时会级联清理其相关测试点、提交和分析数据

## 验证

本次结构调整后，已通过:

```powershell
./mvnw.cmd -q -DskipTests compile
node --check src/main/resources/static/assets/js/core/ui.js
node --check src/main/resources/static/assets/js/pages/index-page.js
node --check src/main/resources/static/assets/js/pages/leaderboard-page.js
node --check src/main/resources/static/assets/js/pages/problem-page.js
node --check src/main/resources/static/assets/js/pages/problem-form-page.js
```

## 后续建议

如果继续往企业工程化方向推进，下一步更值得做的是:

1. 引入 `frontend/` 独立工程和构建流程，把静态页逐步组件化
2. 增加统一的接口返回模型与错误码规范
3. 为核心服务补单元测试和集成测试
4. 把代码执行器进一步抽象成独立基础设施模块
5. 引入用户、权限和审计日志
