# RAG 专属接口文档与前端客户端约定

## 1. 目标

RAG 能力采用独立接口文档与独立前端客户端，不复用当前管理后台通用 `api-docs.yaml` 语义，也不直接复用 [src/api/client.ts](../src/api/client.ts) 作为 RAG 业务主入口。

当前专属文件：
- OpenAPI 文档：[api-docs-rag.yaml](../api-docs-rag.yaml)
- 前端 API 客户端：[src/api/rag.ts](../src/api/rag.ts)
- 前端类型定义：[src/types/rag.ts](../src/types/rag.ts)

## 2. 不复用原则

原因：
- RAG 返回结构包含 `confidence`、`evidences`、`degraded`、`degradeReason` 等专属字段
- RAG 错误码与低置信降级语义独立于通用后台 CRUD
- 后续 RAG 可能走独立网关、独立限流、独立监控口径

约束：
- RAG 页面调用优先走 `src/api/rag.ts`
- RAG 类型优先走 `src/types/rag.ts`
- 若后续自动生成客户端，输入文档使用 `api-docs-rag.yaml`

## 3. 建议生成方式

若后续需要基于 OpenAPI 生成专属客户端，可单独针对 `api-docs-rag.yaml` 执行生成，不覆盖现有后台 API。

建议输出目录：
- `src/api/generated/rag/`

建议原则：
- RAG 生成客户端与后台生成客户端分目录放置
- RAG 页面只依赖 RAG 客户端适配层，避免与后台系统 API 混用

## 4. 当前接口范围

- `POST /rag/query`
- `POST /rag/index/rebuild-t1`
- `GET /rag/index/status`
- `POST /rag/review/alias`

详细字段以 [api-docs-rag.yaml](../api-docs-rag.yaml) 为准。