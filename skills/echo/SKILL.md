---
name: echo
description: 回显输入内容，用于调试与链路验证
version: 1.0.0
entry: java:com.gijela.morpheus.llm.sdk.skill.EchoSkillProvider
timeoutMs: 1000
retry: 0
---

# echo

回显 `text` 参数。

## 输入
- `text`：任意字符串。

## 输出
- `text`：原样返回。
