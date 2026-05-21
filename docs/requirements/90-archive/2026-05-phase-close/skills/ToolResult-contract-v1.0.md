# ToolResult 契约 v1.0（冻结）

- 状态：Frozen
- 冻结日期：2026-04-30
- 适用范围：`knowledge.search`、`attachment.context` 及后续所有 Skills

## 1. 目标
统一技能执行结果结构，保证模型续跑、前端展示、观测审计一致。

## 2. 顶层结构（固定）
```json
{
  "success": true,
  "data": {},
  "error": null,
  "meta": {
    "skillId": "knowledge.search",
    "version": "1.0.0",
    "latencyMs": 83,
    "traceId": "req-xxx",
    "empty": false
  }
}
```

## 3. 字段定义（强约束）
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| success | boolean | 是 | 仅允许 `true/false` |
| data | any | 是 | `success=true` 时建议非 null；`success=false` 时必须为 null |
| error | object\|null | 是 | `success=true` 时必须为 null |
| meta | object | 是 | 见下表 |
| meta.skillId | string | 是 | 形如 `knowledge.search` |
| meta.version | string | 是 | 建议 SemVer |
| meta.latencyMs | integer | 是 | `>=0` |
| meta.traceId | string | 否 | 请求链路标识 |
| meta.empty | boolean | 否 | 查询类技能空结果标识 |

## 4. error 结构（失败必填）
```json
{
  "code": "SKILL_TIMEOUT",
  "message": "技能执行超时",
  "retryable": true
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| code | string | 是 | 必须属于错误码集合 |
| message | string | 是 | 非空 |
| retryable | boolean | 否 | 默认 false |

## 5. 错误码集合（v1.0）
- `SKILL_INVALID_ARGS`
- `SKILL_TIMEOUT`
- `SKILL_UPSTREAM_ERROR`
- `SKILL_FORBIDDEN`
- `SKILL_NOT_FOUND`
- `SKILL_UNKNOWN`

## 6. 语义规则（冻结）
1. `success=true` => `error=null`
2. `success=false` => `data=null` 且 `error.code/message` 必填
3. `meta.skillId/version/latencyMs` 始终必填
4. 新增字段仅允许“可选字段”，不得破坏现有语义

## 7. 示例
### 7.1 成功
```json
{
  "success": true,
  "data": {"hits": [{"id": "a1", "score": 0.92}]},
  "error": null,
  "meta": {"skillId": "knowledge.search", "version": "1.0.0", "latencyMs": 66}
}
```

### 7.2 成功但空结果
```json
{
  "success": true,
  "data": {"hits": []},
  "error": null,
  "meta": {"skillId": "knowledge.search", "version": "1.0.0", "latencyMs": 43, "empty": true}
}
```

### 7.3 失败
```json
{
  "success": false,
  "data": null,
  "error": {"code": "SKILL_TIMEOUT", "message": "技能执行超时", "retryable": true},
  "meta": {"skillId": "attachment.context", "version": "1.0.0", "latencyMs": 3000}
}
```
