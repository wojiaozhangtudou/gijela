---
name: attachment-context
description: 读取当前会话附件上下文（摘要/浓缩内容），适用于文档问答
version: 1.0.0
entry: java:com.gijela.morpheus.chat.adapter.skill.AttachmentContextSkillProvider
timeoutMs: 3000
retry: 0
---

# attachment-context

读取当前会话关联附件的摘要和浓缩内容。

## 触发场景
- 用户提到“附件/文档/上传文件/这个文件里”。

## 输入
- `query`（可选）：按文件名/摘要关键词过滤。
- `limit`（可选）：返回条数，默认 5。
- `includeRaw`（可选）：是否包含原文片段。

## 输出
- `items`：附件列表。
- `contextText`：拼接后的可注入上下文文本。
