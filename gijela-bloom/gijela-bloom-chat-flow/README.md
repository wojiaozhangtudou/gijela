# gijela-bloom-chat-flow

chat-flow 前端联调模块（Vue3 + Vite + TypeScript + Element Plus）。

## 启动

```bash
pnpm install
pnpm dev
```

默认开发端口：`5175`，并代理 `/api` 到 `http://localhost:9010`。

## 当前联调页面

- `/workflows`：流程列表
- `/workflows/:id/editor`：流程编辑联调页
- `/workflows/:id/runs`：运行历史联调页

## 已对接后端接口

- `GET /api/v1/chat-flow/workflows`
- `GET /api/v1/chat-flow/workflows/{workflowId}`
- `POST /api/v1/chat-flow/workflows`
- `POST /api/v1/chat-flow/workflows/{workflowId}/validate`
- `POST /api/v1/chat-flow/workflows/{workflowId}/debug-runs`
- `POST /api/v1/chat-flow/workflows/{workflowId}/runs`
- `GET /api/v1/chat-flow/workflows/{workflowId}/runs`
- `GET /api/v1/chat-flow/runs/{runId}`
