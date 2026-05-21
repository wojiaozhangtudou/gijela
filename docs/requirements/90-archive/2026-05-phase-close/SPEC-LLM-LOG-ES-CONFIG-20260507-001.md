# SPEC-LLM-LOG-ES-CONFIG-20260507-001

> 目的：给 ES 部署与初始化提供可直接执行的配置（索引模板 + ILM + ingest pipeline）。

## 1. 索引生命周期管理策略（ILM）

```json
{
  "policy": "llm-logs-policy",
  "phases": {
    "hot": {
      "min_age": "0d",
      "actions": {
        "rollover": {
          "max_age": "1d",
          "max_primary_shard_size": "30gb"
        },
        "set_priority": { "priority": 100 }
      }
    },
    "warm": {
      "min_age": "1d",
      "actions": {
        "set_priority": { "priority": 50 },
        "forcemerge": { "max_num_segments": 1 }
      }
    },
    "cold": {
      "min_age": "8d",
      "actions": {
        "set_priority": { "priority": 0 }
      }
    },
    "delete": {
      "min_age": "30d",
      "actions": { "delete": {} }
    }
  }
}
```

注：audit 日志保留 180 天，仅改 `delete.min_age: "180d"`。

## 2. 索引模板（runtime）

```json
{
  "name": "llm-runtime-template",
  "index_patterns": ["llm-runtime-*"],
  "template": {
    "settings": {
      "number_of_shards": 2,
      "number_of_replicas": 1,
      "index.lifecycle.name": "llm-logs-policy",
      "index.lifecycle.rollover_alias": "llm-runtime-write",
      "codec": "best_compression"
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "@timestamp": { "type": "date" },
        "eventType": { "type": "keyword" },
        "eventTime": { "type": "date", "format": "epoch_millis" },
        "traceId": { "type": "keyword", "index": true },
        "spanId": { "type": "keyword" },
        "sessionId": { "type": "keyword", "index": true },
        "messageId": { "type": "keyword" },
        "turnId": { "type": "integer" },
        "tenantId": { "type": "keyword", "index": true },
        "appCode": { "type": "keyword" },
        "userId": { "type": "keyword" },
        "status": { "type": "keyword" },
        "errorCode": { "type": "keyword", "index": true },
        "errorType": { "type": "keyword" },
        "errorMsg": { "type": "text" },
        "retryable": { "type": "boolean" },
        "latencyMs": { "type": "long" },
        "provider": { "type": "keyword" },
        "model": { "type": "keyword" },
        "modelRoute": { "type": "keyword", "index": true },
        "endpoint": { "type": "keyword" },
        "promptTokens": { "type": "integer" },
        "completionTokens": { "type": "integer" },
        "totalTokens": { "type": "integer" },
        "costMicros": { "type": "long" },
        "firstTokenMs": { "type": "long" },
        "finishReason": { "type": "keyword" },
        "toolName": { "type": "keyword", "index": true },
        "toolCallId": { "type": "keyword" },
        "toolStatus": { "type": "keyword" },
        "toolLatencyMs": { "type": "long" },
        "toolErrorCode": { "type": "keyword" },
        "promptHash": { "type": "keyword" },
        "promptLength": { "type": "integer" },
        "promptPreview": { "type": "text" },
        "contentHash": { "type": "keyword" },
        "contentLength": { "type": "integer" },
        "streamStart": { "type": "boolean" },
        "streamDone": { "type": "boolean" },
        "sampled": { "type": "boolean" },
        "env": { "type": "keyword" },
        "source": { "type": "keyword" }
      }
    }
  }
}
```

关键约束：
- `dynamic: 'strict'`：新增字段需评审模板。
- 所有 keyword 字段用 `index: true`（支持精确聚合）。
- 大文本字段（`errorMsg`）采用 `text` 类型（支持模糊搜索）。
- 摘要字段（`promptHash/promptPreview`）用于脱敏后透传。

## 3. Ingest Pipeline（脱敏 + 归一）

```json
{
  "description": "LLM logs desensitization and normalization",
  "processors": [
    {
      "set": {
        "field": "@timestamp",
        "value": "{{ _ingest.timestamp }}"
      }
    },
    {
      "gsub": {
        "field": "errorMsg",
        "pattern": "(Authorization|apiKey|Bearer)\\s*[:=]\\s*([\\w-]+)",
        "replacement": "$1: [REDACTED]"
      }
    },
    {
      "gsub": {
        "field": "promptPreview",
        "pattern": "\\b(1[3-9]\\d{9})\\b",
        "replacement": "[PHONE_REDACTED]"
      }
    },
    {
      "gsub": {
        "field": "promptPreview",
        "pattern": "\\b([0-9]{6}[0-9]{8}|[0-9]{18})\\b",
        "replacement": "[ID_REDACTED]"
      }
    },
    {
      "remove": {
        "field": ["promptRaw", "contentRaw", "attachmentRaw"],
        "ignore_missing": true
      }
    },
    {
      "fail": {
        "if": "ctx.traceId == null",
        "message": "Missing required field: traceId"
      }
    }
  ]
}
```

关键规则：
- 自动脱敏：密钥、手机号、身份证。
- 自动删除：原文字段（`promptRaw/contentRaw`）。
- 强制校验：`traceId` 必填。

## 4. 初始化脚本（Bash）

```bash
#!/bin/bash

# 设置 ES 地址与认证
ES_HOST="http://localhost:9200"
AUTH="-u elastic:password"

# 1. 创建 ILM 策略
echo "Creating ILM policy..."
curl $AUTH -X PUT "$ES_HOST/_ilm/policy/llm-logs-policy" \
  -H "Content-Type: application/json" \
  -d @ilm-policy.json

# 2. 创建 ingest pipeline
echo "Creating ingest pipeline..."
curl $AUTH -X PUT "$ES_HOST/_ingest/pipeline/llm-logs-pipeline" \
  -H "Content-Type: application/json" \
  -d @ingest-pipeline.json

# 3. 创建索引模板
echo "Creating index templates..."
for template in runtime access audit; do
  curl $AUTH -X PUT "$ES_HOST/_index_template/llm-${template}-template" \
    -H "Content-Type: application/json" \
    -d @template-${template}.json
done

# 4. 创建初始索引
echo "Creating initial indices..."
TODAY=$(date +%Y.%m.%d)
for type in runtime access audit; do
  curl $AUTH -X PUT "$ES_HOST/llm-${type}-${TODAY}" \
    -H "Content-Type: application/json" \
    -d '{"settings": {"index.lifecycle.name": "llm-logs-policy"}}'
done

# 5. 创建写入别名
echo "Creating write aliases..."
curl $AUTH -X POST "$ES_HOST/_aliases" \
  -H "Content-Type: application/json" \
  -d @aliases.json

echo "Done!"
```

aliases.json 内容：
```json
{
  "actions": [
    {
      "add": {
        "index": "llm-runtime-*",
        "alias": "llm-runtime-write",
        "is_write_index": true
      }
    },
    {
      "add": {
        "index": "llm-access-*",
        "alias": "llm-access-write",
        "is_write_index": true
      }
    },
    {
      "add": {
        "index": "llm-audit-*",
        "alias": "llm-audit-write",
        "is_write_index": true
      }
    }
  ]
}
```

## 5. 验收清单
- [ ] ILM 策略已应用到所有索引。
- [ ] ingest pipeline 脱敏规则已生效。
- [ ] 测试日志写入：密钥/手机号已脱敏。
- [ ] 分片大小< 20GB（按预期）。
- [ ] rollover 触发后新索引名符合 `llm-{type}-YYYY.MM.DD` 规范。
