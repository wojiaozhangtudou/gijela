---
applyTo: "**"
excludeAgent:
	- "code-review"
---

# 本地开发环境与工具使用习惯

- 当前本地系统为 Windows，默认终端为 PowerShell。执行命令时优先按 Windows / PowerShell 习惯组织，不要默认写 Linux / bash 风格命令。
- Node.js 通过 `nvm` 管理，当前常用版本是 `v23.11.0`。
- **前端包管理统一优先使用 `pnpm`，不要默认使用 `npm`**，除非用户明确要求。
- Python 通过 Anaconda 管理。执行 Python 相关命令前，优先使用 `conda activate copilot` 切换到 `copilot` 环境，而不是停留在 `base` 环境。
- 执行 pip 安装时，优先使用 `python -m pip`，并默认使用清华镜像：`https://pypi.tuna.tsinghua.edu.cn/simple`。
- Java 当前常用版本是 **Java 21**。若环境变量失效，可使用本地 JDK 目录：`D:\software\jdk-21.0.9`。
- Maven 当前常用版本是 **Apache Maven 3.9.9**。若环境变量失效，可使用本地 Maven 目录：`D:\software\apache-maven-3.9.9`。
- Maven 的 `settings.xml` 指的是 `D:\software\apache-maven-3.9.9\conf\settings.xml`，其中已配置阿里云镜像；进行 Maven 构建、依赖解析、测试验证时，优先沿用这份本地 Maven 配置，不要额外切换到其他包管理习惯。
- Maven 默认本地仓库目录是 `C:\Users\Administrator\.m2\repository`；如需排查依赖缓存、确认 Jar 是否已下载或说明本地仓库位置，优先按这个路径处理。
- 若需要给出构建、运行、安装、验证步骤，优先贴合以上本地工具链与习惯：Node 用 `pnpm`，Python 用 `conda + python -m pip + 清华源`，Java/Maven 优先使用现有本地版本。
- 当任务涉及安装依赖、运行脚本、执行测试时，优先考虑国内网络环境，避免默认使用容易超时的国外源或不稳定下载方式。

