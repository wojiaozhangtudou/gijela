---
name: time-now
description: 返回当前时间，用于需要时间上下文的回答
version: 1.0.0
entry: java:com.gijela.morpheus.llm.sdk.skill.TimeNowSkillProvider
timeoutMs: 1000
retry: 0
---

# time-now

返回系统当前时间字符串。

## 触发场景
- 用户询问“现在几点/当前时间”。

## 输出
- `now`：ISO-8601 时间字符串。
