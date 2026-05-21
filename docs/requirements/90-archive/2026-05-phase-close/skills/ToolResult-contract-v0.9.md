# ToolResult 契约草案 v0.9

## 1. 目标
统一技能执行结果结构，保证：
- 模型续跑可预测
- 前端展示可预测
- 观测与审计可聚合

## 2. 顶层结构
```json
{
  "success": true,
  "data": {},
  "error": null,
  "meta": {
    "skillId": "knowledge.search",
    "version": "1.0.0",
    "latencyMs": 83,
    "traceId": "req-xxx"
  }
}
```

## 3. 字段定义
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| success | boolean | 是 | 技能是否执行成功 |
| data | object\|array\|string\|number\|boolean\|null | 是 | 成功时结果数据；失败时可为 null |
| error | object\|null | 是 | 失败信息；成功时必须为 null |
| meta | object | 是 | 元信息 |
| meta.skillId | string | 是 | 技能唯一标识 |
| meta.version | string | 是 | 技能版本（建议 SemVer） |
| meta.latencyMs | integer | 是 | 执行耗时（毫秒） |
| meta.traceId | string | 否 | 请求链路标识 |
| meta.empty | boolean | 否 | 查询类技能是否空结果 |

## 4. error 结构
```json
{
  "code": "SKILL_TIMEOUT",
  "message": "技能执行超时",
  "retryable": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | 错误码 |
| message | string | 是 | 人类可读错误信息 |
| retryable | boolean | 否 | 是否可重试 |

## 5. 错误码建议
- `SKILL_INVALID_ARGS`
- `SKILL_TIMEOUT`
- `SKILL_UPSTREAM_ERROR`
- `SKILL_FORBIDDEN`
- `SKILL_NOT_FOUND`
- `SKILL_UNKNOWN`

## 6. 正例
### 6.1 成功且有数据
```json
{
  "success": true,
  "data": {"hits": [{"id": "a1", "score": 0.92}]},
  "error": null,
  "meta": {"skillId": "knowledge.search", "version": "1.0.0", "latencyMs": 66}
}
```

### 6.2 成功但空结果
```json
{
  "success": true,
  "data": {"hits": []},
  "error": null,
  "meta": {"skillId": "knowledge.search", "version": "1.0.0", "latencyMs": 43, "empty": true}
}
```

### 6.3 失败
```json
{
  "success": false,
  "data": null,
  "error": {"code": "SKILL_TIMEOUT", "message": "技能执行超时", "retryable": true},
  "meta": {"skillId": "attachment.context", "version": "1.0.0", "latencyMs": 3000}
}
```

## 7. 约束
- `success=true` 时：`error` 必须为 null。
- `success=false` 时：`error.code` 与 `error.message` 必填。
- `meta.skillId/version/latencyMs` 始终必填。
