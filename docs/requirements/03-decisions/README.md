# ADR 决策区

用于存放跨需求、长期有效的决策文档。

完整工作流参见 [.github/requirements-workflow.md](../../.github/requirements-workflow.md)。

模板建议：
1. 背景
2. 决策
3. 备选方案
4. 影响范围
5. 回滚条件

命名：ADR-YYYYMMDD-序号.md

补充要求：
- 仅记录可复用、跨需求、会长期影响实现方式的决策。
- 若只是单一需求的实现细节，优先写回对应 `REQ` 或 `ARCH-REVIEW` 文档，不单独拆 ADR。
- 新建 ADR 时，可直接基于 [ADR 模板](../00-governance/ADR-TEMPLATE.md)。
