# C++17 判题环境部署说明

Windows、安卓平板或其他学生电脑只是通过浏览器访问网站，不在学生设备上编译 C++。C++17 代码在网站后端服务器的 runner 中编译和运行。

## 推荐部署方式

课堂或竞赛试点推荐使用 Docker 沙箱：

1. 在运行后端服务的服务器上安装并启动 Docker。
2. 运行学校部署预检。

macOS / Linux:

```bash
bash scripts/doctor-school.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/doctor-school.ps1
```

3. 构建项目自带 C++17 runner 镜像。

macOS / Linux:

```bash
bash scripts/build-cpp17-runner.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-cpp17-runner.ps1
```

4. 启动后端时使用 Docker 执行模式：

```bash
EXECUTOR_MODE=docker ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:EXECUTOR_MODE = "docker"
.\mvnw.cmd spring-boot:run
```

默认 runner 镜像名为 `wenzhong-oj-cpp17-runner:13`，基础镜像为 `gcc:13-bookworm`。如需改名，构建和启动时保持同一个环境变量：

```bash
OJ_CPP17_DOCKER_IMAGE=my-oj-cpp17:13 bash scripts/build-cpp17-runner.sh
EXECUTOR_MODE=docker OJ_CPP17_DOCKER_IMAGE=my-oj-cpp17:13 ./mvnw spring-boot:run
```

如果 Docker Hub 临时不可用，可以把 `OJ_CPP17_BASE_IMAGE` 改成学校内网镜像仓库中兼容 `gcc:13-bookworm` 的镜像。当前本机已验证下面这个备用源可以构建并通过 smoke：

```bash
OJ_CPP17_BASE_IMAGE=public.ecr.aws/docker/library/gcc:13-bookworm bash scripts/build-cpp17-runner.sh
```

完整应用镜像也支持替换基础镜像：`OJ_NODE_BASE_IMAGE`、`OJ_MAVEN_BASE_IMAGE`、`OJ_JRE_BASE_IMAGE`、`OJ_DOCKER_CLI_IMAGE` 分别控制前端构建、后端构建、应用运行时和 Docker CLI 来源。

本机已验证上述四类应用构建基础镜像可从 `public.ecr.aws/docker/library` 读取。Python runner 镜像源是否替换，建议按学校网络和内网仓库实际情况配置。

一键 Docker Compose 部署时，源码和输入由应用后端生成 tar 流传入 runner，不依赖宿主机临时目录路径，也不要求应用容器额外安装系统 `tar` 命令。

## 本机执行模式

本机模式只适合开发调试。服务器必须安装可编译 `#include <bits/stdc++.h>` 的 GNU g++，必要时设置：

```bash
OJ_CPP17_COMPILER=g++-13 ./mvnw spring-boot:run
```

macOS 自带的 `/usr/bin/g++` 通常是 Apple clang，不能作为信息竞赛 C++17 环境。

## 教师端状态

教师端系统状态会检查：

- Docker 是否启动。
- `wenzhong-oj-cpp17-runner:13` 镜像是否存在。
- C++17 runner 是否可用于后端判题。

如果状态显示未就绪，学生仍可以打开网站和写代码，但 C++17 提交会被后端拦截并提示联系老师完成部署配置。
