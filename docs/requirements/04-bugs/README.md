# Bug 修复区

用于维护“需求主体已完成，但仍处于缺陷修复/回归验证”的问题记录。
完整工作流参见 [.github/requirements-workflow.md](../../.github/requirements-workflow.md) 中的"Bug 追踪"章节。
## 适用场景
- 功能已上线或已基本完成，仅剩问题修复。
- 问题需要连续跟踪，但不适合继续放在 `01-active` 占用活跃需求目录。
- 需要按问题维度组织回归、验证、上线修复记录。

## 建议结构
- 04-bugs/BUG-LIST.md（Bug 聚合页）
- 04-bugs/BUG-20260509-001.md
- 04-bugs/BUG-20260509-002.md

## 使用规则
- 单个问题优先使用 [BUG 模板](../00-governance/BUG-TEMPLATE.md)。
- Bug 文档应关联原始需求、影响模块、修复状态、验证结果。
- 若某问题已演变为新的正式需求，应回退到 `02-backlog` 或 `01-active` 单独立项。
- 问题关闭后，可按阶段一起迁移到 `90-archive`。