# gijela Copilot 全局协作规范

## 1. 仓库定位
- 当前仓库是“管理后台”单仓：
  - 后端：`gijela-core/`（Maven 多模块，Spring Boot + Spring Security + MyBatis-Plus + Redis + JWT）
  - 前端：`gijela-bloom/gijela-bloom-pistil/`（Vue3 + Vite + TypeScript + Pinia + Element Plus）
- 目标优先级：**可维护 > 一致性 > 可扩展 > 花哨实现**。
- 快速入门：见 [QUICK-START.md](../QUICK-START.md)。
## 2. 规则优先级
1. 更具体的 `.github/instructions/*.instructions.md`
2. 本文件（仓库级）

若冲突，优先“离当前文件更近、范围更具体”的规则。

## 3. 全局改动原则
- 先识别模块再改代码，避免跨模块硬套风格。
- 优先最小必要改动，不做无关重构和大面积格式化。
- 先复用已有对象与链路：`ApiResponse`、错误码、DTO/VO、Mapper、Service、Store。
- 非明确要求时，不主动升级核心依赖版本（Spring Boot、MyBatis-Plus、Element Plus 等）。
- 非明确要求时，不修改 `target/`、`logs/` 等运行产物目录。

## 4. 后端统一约束
- 默认 Java 17（以 `gijela-core/pom.xml` 为准）。
- 保持分层清晰：`controller` / `service` / `mapper` / `domain`。
- 包命名遵循现有前缀：`com.gijela.morpheus.pistil`。
- 接口返回优先复用现有统一响应结构，不新造并行协议。
- 正式代码使用 `SLF4J + LoggerFactory`，不要加入 `System.out.println`、`printStackTrace()`。

## 5. 前端统一约束
- 默认技术栈：Vue 3 + TypeScript + Vite + Pinia + Axios + Element Plus。
- 接口请求优先放在 `src/api`，页面层避免散写请求细节。
- Store 状态优先复用既有 `src/store`，避免并行状态源。
- 文案优先中文；涉及 i18n 时同步维护中英文资源文件。

## 6. 文档与安全
- 文档默认中文，保持“可执行、可验证、可维护”。
- 改动若影响接口、配置、启动方式、权限策略，需同步更新相关文档。

### 需求文档工作流
- 需求、评审、缺陷文档统一维护在 `docs/requirements/`，遵循 `docs/requirements/00-governance/README.md` 规范。
- 完整的工作流说明见 [requirements-workflow.md](requirements-workflow.md)，包括：
  - Backlog → Active → ARCH-REVIEW → 实现 → Bug 追踪 → 归档 的全链路
  - 每个阶段的职责、产出物、时限要求
  - 与代码库（接口、权限、菜单、缓存等）的同步点
  - 协作规范与常见问题

- 不在代码目录或项目根目录散放需求文档。

- 严禁提交真实密钥、令牌、账号、数据库密码和私有地址。

## 7. 一句话原则
> 在当前 gijela 仓库中，优先遵守：**模块边界清晰、最小必要改动、与现有实现一致、变更可验证**。
