# 归档总览

所有已关闭的需求、缺陷、评审、规格文档按批次存放于此目录，均为只读。

## 命名规范

每个批次命名遵循：**YYYY-MM-DD-主题**
- `YYYY-MM-DD`：批次创建日期
- `主题`：英文单词或短语，清晰描述批次内容，便于查找

示例：
- `2026-05-09-bugfix-logging-observability` → Bug 修复：日志与可观测性改进
- `2026-05-09-doc-governance-system-design` → 文档治理体系设计完成
- `2026-06-architecture-review-batch-1` → 架构评审第一批

## 批次列表

| 批次 | 类型 | 时间 | 主题 | 状态 | 说明 |
| --- | --- | --- | --- | --- | --- |
| [2026-05-phase-close](./2026-05-phase-close/README.md) | 阶段归档 | 2026-05 | requirements-phase-historical | 只读 | 需求阶段历史文档，含 REQ / ARCH-REVIEW / SPEC / DEV-FEEDBACK |
| [2026-05-bugfix-close](./2026-05-bugfix-close/README.md) | 增量归档 | 2026-05-09 | bug-logging-cache-tokens | 只读 | Bug 修复收官：日志粒度、缓存一致性、Token 显示（REQ-20260509-001） |
| [2026-05-doc-governance](./2026-05-doc-governance/README.md) | 治理归档 | 2026-05-09 | docs-governance-system-design | 只读 | 文档治理体系设计完成（ARCH-REVIEW-001~010） |
| [2026-05-chatflow-phase1](./2026-05-chatflow-phase1/README.md) | 阶段归档 | 2026-05-20 | chatflow-phase1-workbench | 只读 | chat-flow Phase 1 会话工作台全量实现完成（REQ-20260520-001） |
| [2026-05-20-arch-review-consolidation](./2026-05-20-arch-review-consolidation/README.md) | 增量归档 | 2026-05-20 | arch-review-consolidation | 只读 | chat-flow 历史架构评审文档收敛归档（ARCH-REVIEW-20260515/18/19-*） |
| [2026-05-20-avatar-upload-422-fix](./2026-05-20-avatar-upload-422-fix/README.md) | 增量归档 | 2026-05-20 | avatar-upload-422-fix | 只读 | 用户头像上传 422 问题评审归档（ARCH-REVIEW-20260520-002） |
| [2026-05-21-chat-model-config-review-close](./2026-05-21-chat-model-config-review-close/README.md) | 增量归档 | 2026-05-21 | chat-model-config-review-close | 只读 | chat 模块模型配置数据库化评审归档（ARCH-REVIEW-20260520-003） |

## 使用规则
- 归档目录只读，不在此处修改任何文档。
- 当前工作请回到 [需求文档索引](../index.md)。
- 若需查阅历史决策背景，按批次进入对应目录检索。
- 新增归档批次时：
  1. 按 `YYYY-MM-DD-主题` 创建目录
  2. 补充对应批次 `README.md`（含时间、主题、文件清单）
  3. 同步在本表追加一行
