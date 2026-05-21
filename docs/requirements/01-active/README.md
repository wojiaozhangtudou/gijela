# Active 需求区

当前目录只放“正在开发/联调”的需求。
完整工作流参见 [.github/requirements-workflow.md](../../.github/requirements-workflow.md) 中的"激活"与"架构评审"章节。
## 目录建议
- 01-active/REQ-20260509-001/
  - REQ-20260509-001.md（主文档，唯一事实来源）
  - ARCH-REVIEW-20260509-001.md（评审，可选但推荐）
  - BUG-20260509-001.md（缺陷跟踪，可选）

可直接基于 [REQ 模板](../00-governance/REQ-TEMPLATE.md)、[评审模板](../00-governance/ARCH-REVIEW-TEMPLATE.md)、[缺陷模板](../00-governance/BUG-TEMPLATE.md) 创建。

如需参考完整写法，可查看 [示例文档包](../00-governance/examples/)。

## 编写要求
- 主文档必须写清：背景、目标、范围、不做什么、影响模块、验收标准。
- 涉及后端时，补充接口路径、请求/响应、错误码、权限点。
- 涉及前端时，补充页面入口、状态来源、接口依赖、交互边界。
- 若跨 `gijela-core`、`gijela-bloom`、`gijela-core-security-common`，需显式列出模块职责，避免串层实现。

## 状态建议
- draft / reviewing / developing / testing / done

## 控制原则
- 一个需求目录只保留当前有效文档，过程性零散记录尽量收敛到主文档或评审文档。
- 文档变更应与代码改动同步，避免“文档已改、实现未跟”或“代码已变、需求未更新”。
- 若需求主体已完成、仅剩问题修复，建议把缺陷跟踪迁移到 `04-bugs`，避免 `01-active` 长期堆积历史事项。
