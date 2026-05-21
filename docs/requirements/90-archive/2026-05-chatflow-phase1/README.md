# 归档批次：chat-flow Phase 1 会话工作台

归档日期：2026-05-20  
主题：chatflow-phase1-workbench  
状态：只读

## 本批次说明
chat-flow 模块 Phase 1 会话对话工作台全量实现完成，前后端均通过编译验证，功能闭环。

## 包含文件

| 文件 | 类型 | 说明 |
|------|------|------|
| [REQ-20260520-001.md](./REQ-20260520-001.md) | 需求 | Phase 1 完整需求说明（按实际实现整理） |
| ARCH-REVIEW-20260519-001.md | 架构评审 | chat-flow 独立域初始规划 |
| ARCH-REVIEW-20260519-002.md | 架构评审 | 开工前架构评审 |
| ARCH-REVIEW-20260519-003.md | 架构评审 | 开工复审（P0 前置物补充） |

> 以上 ARCH-REVIEW 文档位于 `docs/requirements/`，未做物理迁移，可按路径查阅。

## 链路回顾

```
需求激活（2026-05-19）
  → 架构评审（ARCH-REVIEW-001/002/003）
  → 后端实现（session/message/completion API + 工作流运行 triggerBy 过滤）
  → 前端实现（ChatflowWorkbench 三栏布局 + 执行面板 + 运行历史弹框）
  → Bug 修复（字段名一致性 / sys.conversation_id 注入 / 异常处理）
  → 模式语义强化（CHAT 模式锁定 / TRANSFORM 模式 strict 输入映射）
  → 运行类型区分（debug / prod 标签，DB 层 triggerBy 过滤）
  → Phase 1 关闭（2026-05-20）
```

## 主要交付物

### 后端（gijela-core-chat-flow）
- `ChatflowController`：会话 CRUD + 消息查询 + completion 接口
- `ChatflowConversationServiceImpl`：异步运行触发、消息回填、历史组装
- `WorkflowRunServiceImpl`：`listRuns` 新增 `triggerBy` DB 过滤，`startDebugRun` 写入 sessionId
- `ChatflowMessageVO`：新增 `runId` 字段透出
- `WorkflowRunDetailVO`：新增 `runType` 字段透出
- `WorkflowRunSummaryVO`：新增 `triggerBy` 字段

### 前端（gijela-bloom-chat-flow）
- `ChatflowWorkbench.vue`：三栏布局（320px | 自适应 | 320px）
- 执行面板：实时节点日志、节点详情弹框（inputSnapshot / outputSnapshot）
- 运行历史弹框：`triggerBy` DB 过滤、会话归属标签、调试/正式标签
- `types/chatflow.ts`：`ChatflowMessageItem` 新增 `runId`
- `types/workflow.ts`：`WorkflowRunSummary` 新增 `triggerBy`，`WorkflowRunDetail` 新增 `runType`
- `api/workflow.ts`：`listRuns` 新增 `triggerBy` 参数

## 遗留事项
- `trigger_by` 历史数据均为 `"system"`，无法回溯到具体会话（历史数据问题，不修复）。
- Phase 2 规划：流式输出（SSE）、权限与审计、多租户隔离。
