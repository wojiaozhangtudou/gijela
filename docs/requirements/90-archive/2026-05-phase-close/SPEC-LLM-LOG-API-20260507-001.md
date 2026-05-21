# SPEC-LLM-LOG-API-20260507-001

> 目的：给“ES + 自研看板”提供可直接开发的 API 契约（不依赖 Kibana）。

## 1. 通用查询对象

### 1.1 LogQueryFilter
```json
{
  "startAt": "2026-05-07T00:00:00Z",
  "endAt": "2026-05-07T23:59:59Z",
  "appCode": "chat",
  "modelRoute": "default",
  "provider": ["openai-compatible"],
  "model": ["qwen-max"],
  "status": ["SUCCESS", "FAILED"],
  "errorCode": ["LLM_TIMEOUT"],
  "toolName": ["knowledge.search"],
  "sessionId": "sess_xxx",
  "traceId": "trc_xxx",
  "userId": "u_xxx",
  "env": ["prod"],
  "sampled": false,
  "keyword": "timeout"
}
```

### 1.2 统一约束
- `startAt/endAt` 必填。
- 默认最大时间窗 24h，扩展查询需管理员权限。
- 分页最大 `pageSize=200`。
- 聚合最大 bucket 数 500。
- `tenantId` 不接受前端传值，由服务端从上下文注入。

## 2. API 契约

### 2.1 时序
- POST `/api/v1/llm-logs/query/timeseries`

请求：
```json
{
  "filter": { "startAt": "...", "endAt": "..." },
  "metric": "qps",
  "interval": "1m",
  "groupBy": ["modelRoute"]
}
```

响应：
```json
{
  "buckets": [
    { "ts": "2026-05-07T10:00:00Z", "value": 120, "group": "default" }
  ]
}
```

### 2.2 TopN
- POST `/api/v1/llm-logs/query/topn`

请求：
```json
{
  "filter": { "startAt": "...", "endAt": "..." },
  "metric": "count",
  "dimension": "errorCode",
  "limit": 10
}
```

响应：
```json
{
  "items": [
    { "key": "LLM_TIMEOUT", "value": 37, "ratio": 0.21 }
  ]
}
```

### 2.3 分布
- POST `/api/v1/llm-logs/query/distribution`

请求：
```json
{
  "filter": { "startAt": "...", "endAt": "..." },
  "metric": "latencyMs",
  "dimension": "modelRoute"
}
```

响应：
```json
{
  "items": [
    { "key": "default", "count": 2000, "p50": 380, "p95": 1500, "p99": 4200 }
  ]
}
```

### 2.4 原始记录
- POST `/api/v1/llm-logs/query/records`

请求：
```json
{
  "filter": { "startAt": "...", "endAt": "..." },
  "sort": [{ "field": "@timestamp", "order": "desc" }],
  "pageNo": 1,
  "pageSize": 50
}
```

响应：
```json
{
  "total": 1234,
  "pageNo": 1,
  "pageSize": 50,
  "records": [
    {
      "eventTime": "2026-05-07T10:01:02Z",
      "eventType": "chat.finish",
      "traceId": "trc_xxx",
      "sessionId": "sess_xxx",
      "status": "FAILED",
      "errorCode": "LLM_TIMEOUT",
      "latencyMs": 5400
    }
  ]
}
```

### 2.5 Trace 下钻
- GET `/api/v1/llm-logs/query/trace/{traceId}`

响应：
```json
{
  "traceId": "trc_xxx",
  "summary": {
    "sessionId": "sess_xxx",
    "status": "FAILED",
    "totalLatencyMs": 5400,
    "modelRoute": "default"
  },
  "timeline": [
    { "ts": "2026-05-07T10:00:00Z", "eventType": "chat.request", "status": "SUCCESS" },
    { "ts": "2026-05-07T10:00:01Z", "eventType": "model.request", "status": "SUCCESS" },
    { "ts": "2026-05-07T10:00:05Z", "eventType": "chat.finish", "status": "FAILED", "errorCode": "LLM_TIMEOUT" }
  ]
}
```

### 2.6 导出
- POST `/api/v1/llm-logs/query/export`

请求：
```json
{
  "filter": { "startAt": "...", "endAt": "..." },
  "format": "csv",
  "columns": ["eventTime", "traceId", "sessionId", "eventType", "status", "errorCode", "latencyMs"]
}
```

响应：
```json
{
  "jobId": "exp_20260507_xxx",
  "status": "PENDING"
}
```

## 3. 前端看板 IA（可开工）

### 3.1 路由
- `/llm-observability/overview`
- `/llm-observability/troubleshoot`
- `/llm-observability/audit`
- `/llm-observability/alerts`

### 3.2 组件分区
1. 全局筛选栏（时间、模型路由、状态、错误码、会话、trace）
2. KPI 卡片区（QPS、错误率、P95、首 token P95、工具失败率）
3. 图表区（趋势、TopN、分布）
4. 明细区（事件表格 + trace 抽屉）

## 4. 安全与合规
- 默认脱敏字段：`authorization`、`apiKey`、`cookie`、`promptRaw`、`attachmentRaw`。
- 仅返回摘要字段：`promptHash`、`promptLength`、`promptPreview`。
- API 统一做租户隔离与字段白名单校验。

## 5. 验收口径
- 同条件下 API 聚合结果与 ES 基准查询误差 < 1%。
- trace 下钻链路完整率 >= 99%。
- 100 条敏感样本脱敏命中率 100%。
