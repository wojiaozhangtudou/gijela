# 文档治理规范（需求/评审/缺陷）

## 目标
- 与仓库级规范统一：遵循 `.github/copilot-instructions.md` 与 `.github/instructions/*.instructions.md` 的约束。
- 降低文档噪音，避免“一个需求 N 份并行文档”。
- 保持可追溯：需求 → 评审 → 实施 → 归档。

## 统一原则
- 文档默认中文，强调“可执行、可验证、可维护”。
- 文档只服务当前开发，不替代代码注释、接口定义和测试结果。
- 需求描述需明确影响模块，避免跨前后端、跨安全模块的职责漂移。
- 改动涉及接口、权限、缓存键、菜单、登录流程时，需求文档必须同步标记受影响模块与联动项。
- `index.md` 只做导航，不维护冗长流水账。

## 模板清单
- `REQ-TEMPLATE.md`：需求主文档模板
- `ARCH-REVIEW-TEMPLATE.md`：架构评审模板
- `BUG-TEMPLATE.md`：缺陷跟踪模板
- `ADR-TEMPLATE.md`：长期决策模板

## 示例清单
- `examples/REQ-EXAMPLE.md`：需求主文档示例
- `examples/ARCH-REVIEW-EXAMPLE.md`：架构评审示例
- `examples/BUG-EXAMPLE.md`：缺陷跟踪示例

## 生命周期
1. Backlog：放在 02-backlog。
2. 激活：迁移到 01-active，并创建主需求文档。
3. 评审：在 01-active 下补充 ARCH-REVIEW 文档。
4. 缺陷修复：已上线或已完成需求进入修复阶段时，迁移到 04-bugs 或在其中挂缺陷跟踪。
5. 关闭：统一迁移到 90-archive/批次目录。

## 命名规范
- 需求主文档：REQ-YYYYMMDD-序号.md
- 架构评审：ARCH-REVIEW-YYYYMMDD-序号.md
- 缺陷记录：BUG-YYYYMMDD-序号.md
- 决策记录：ADR-YYYYMMDD-序号.md

## 约束
- index.md 仅做导航与状态，不维护超长历史列表。
- 每个需求最多 1 个主文档 + 若干评审/决策文档。
- 实现细节文档优先合并到主文档，避免平行 README 泛滥。
- 文档中的模块边界、接口风格、前后端职责应与仓库现状一致：
	- 后端：优先复用 `/api/v1/**`、`ApiResponse`、既有 Service / Mapper / Controller 分层。
	- 前端：优先复用 `src/api`、`src/store`、`src/views` 现有链路，不在页面层散写请求细节。
- 评审结论若为“有条件可落地”或“暂不可落地”，必须写清具体调整点、优先级与 MVP 顺序。
- 新建文档优先从模板复制，减少口径漂移。
- 示例文档统一放在 `00-governance/examples`，不放入 `01-active`，避免污染当前活跃需求视图。
