---
name: knowledge-search
description: 查询知识库片段，适用于事实依据、部署步骤、配置细节类问题
version: 1.0.0
entry: java:com.gijela.morpheus.chat.adapter.skill.KnowledgeSearchSkillProvider
timeoutMs: 3000
retry: 1
---

# knowledge-search

调用知识检索能力，返回命中片段（hits）。

## 触发场景
- 用户问“怎么部署/怎么配置/参数是什么/官方文档怎么写”。

## 输入
- `query`：检索关键词。

## 输出
- `hits`：检索命中列表。
