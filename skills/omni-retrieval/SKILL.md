---
name: omni-retrieval
description: 智能多源联合检索：根据问题特征自动路由并聚合附件、知识库、图谱三路上下文，返回统一的可注入摘要
version: 1.0.0
entry: java:com.gijela.morpheus.chat.adapter.skill.OmniRetrievalSkillProvider
timeoutMs: 8000
retry: 1
tags:
  - retrieval
  - graph
  - knowledge
  - attachment
capabilities:
  - multi-source-retrieval
  - intent-routing
  - context-fusion
---

# omni-retrieval

根据用户 query 的意图特征，**自动判断**需要调用哪些数据源（附件 / 知识库 / 图谱），
并行拉取后融合输出统一上下文，供 LLM 注入使用。

---

## 触发场景

| 场景描述 | 路由策略 |
|---|---|
| "这个文件里说了什么" / "附件里的数据" | → 仅 attachment-context |
| "怎么配置 / 参数是什么 / 文档里怎么写" | → 仅 knowledge-search |
| "XXX 和 YYY 的关系" / "谁参与了这件事" / "图谱里有没有" | → 仅 graph-search |
| 复合问题：涉及多个维度 | → 并行调用多路，按置信度加权融合 |
| 无明显意图 | → 三路全召回，按相关性排序截断 |

---

## 输入参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `query` | string | 是 | 用户原始问题 |
| `sessionId` | string | 否 | 会话 ID，用于定位附件范围 |
| `graphSpace` | string | 否 | 指定图谱空间，默认 `default` |
| `knowledgeBase` | string | 否 | 指定知识库 ID，不传则全库检索 |
| `sources` | array[string] | 否 | 强制指定数据源：`attachment` / `knowledge` / `graph`，不传则自动路由 |
| `maxTokens` | integer | 否 | 融合上下文最大 token 数，默认 2000 |
| `topK` | integer | 否 | 每路最多取 K 条，默认 5 |

---

## 意图路由逻辑

```
1. IntentClassifier.classify(query) →
       ATTACHMENT  / KNOWLEDGE  / GRAPH  / MIXED  / UNKNOWN

2. 按意图决定启用哪些 Fetcher：
   - ATTACHMENT  → AttachmentFetcher
   - KNOWLEDGE   → KnowledgeFetcher
   - GRAPH       → GraphFetcher
   - MIXED       → AttachmentFetcher + KnowledgeFetcher + GraphFetcher（并行）
   - UNKNOWN     → 三路全开（并行，降低 topK 到 3）

3. 并行执行，超时降级：单路超时不阻塞整体，记录 warnings

4. 融合排序：
   - 按 score 降序合并
   - 去重（同源 chunkId / entityId）
   - 裁剪到 maxTokens

5. 输出 contextText（可直接注入 system prompt）
```

---

## 意图关键词规则（初版，后续可接分类模型）

```yaml
attachment:
  keywords: [附件, 文件, 上传, 文档里, 这份, 这个文件]

knowledge:
  keywords: [怎么配置, 怎么部署, 参数, 文档, 官方, 规范, 步骤, 如何]

graph:
  keywords: [关系, 图谱, 实体, 谁参与, 连接, 关联, 邻居, 一跳, 路径]
```

---

## 输出结构

```json
{
  "intent": "MIXED",
  "sources": ["attachment", "graph"],
  "items": [
    {
      "source": "attachment",
      "score": 0.91,
      "title": "需求文档v2.pdf",
      "snippet": "..."
    },
    {
      "source": "graph",
      "score": 0.87,
      "entityName": "张三",
      "entityType": "人物",
      "snippet": "张三 → 参与 → 项目A"
    },
    {
      "source": "knowledge",
      "score": 0.73,
      "chunkId": "kb-001-chunk-12",
      "snippet": "..."
    }
  ],
  "contextText": "【附件摘要】...\n\n【知识库片段】...\n\n【图谱关联】...",
  "warnings": [],
  "truncated": false
}
```

---

## 失败策略

- 单路 Fetcher 超时/异常：降级跳过，在 `warnings` 中记录，不影响其他路
- 全路失败：返回空 `contextText`，`warnings` 注明原因
- `retry: 1`：整体重试一次（超时路除外）

---

## Java 实现要点

```
OmniRetrievalSkillProvider
  ├── IntentClassifier          # 关键词 + 可选 embedding 分类
  ├── AttachmentFetcher         # 复用 AttachmentContextSkillProvider 逻辑
  ├── KnowledgeFetcher          # 复用 KnowledgeSearchSkillProvider 逻辑
  ├── GraphFetcher              # 调用 Neo4jGraphRepository 实体/关系检索
  ├── ResultFusion              # 合并 + 去重 + 按 score 排序 + token 截断
  └── OmniRetrievalResponse     # 统一输出结构
```

包路径：`com.gijela.morpheus.chat.adapter.skill`

---

## 版本演进规划

| 版本 | 目标 |
|---|---|
| v1.0 | 关键词路由 + 三路并行 + 简单拼接融合 |
| v1.1 | 引入 embedding 意图分类，替换关键词规则 |
| v2.0 | 支持多轮追问（携带上轮 `intent` 上下文），跨源引用追踪 |
