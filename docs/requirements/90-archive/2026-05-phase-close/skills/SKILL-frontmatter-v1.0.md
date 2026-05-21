# SKILL Frontmatter 规范 v1.0（冻结）

- 状态：Frozen
- 冻结日期：2026-04-30
- 适用范围：本地 `skills/` 包加载器

## 1. 目录规范
```text
skills/<skill-name>/
├── SKILL.md (required)
├── scripts/ (optional)
├── references/ (optional)
└── assets/ (optional)
```

## 2. SKILL.md 最小示例
```markdown
---
name: knowledge-search
description: 检索知识库片段并返回引用
version: 1.0.0
entry: java:com.gijela.morpheus.chat.skill.KnowledgeSearchProvider
timeoutMs: 3000
retry: 0
---

# knowledge-search

描述触发场景、输入输出、失败策略与示例。
```

## 3. 字段定义（v1.0）
| 字段 | 类型 | 必填 | 默认值 | 约束 |
|---|---|---|---|---|
| name | string | 是 | - | `[a-z0-9-]{3,64}` |
| description | string | 是 | - | 非空 |
| version | string | 是 | - | 建议 SemVer |
| entry | string | 是 | - | `java:` 或 `script:` 前缀 |
| timeoutMs | integer | 否 | 3000 | `100~30000` |
| retry | integer | 否 | 0 | `0~2` |
| tags | array[string] | 否 | [] | 每项非空 |
| capabilities | array[string] | 否 | [] | 能力声明 |
| permissions | object | 否 | {} | 权限声明 |

## 4. entry 规则
- `java:<FQCN>`：Java Provider 类
- `script:<relative-path>`：脚本入口（必须走白名单与沙箱）

## 5. 保留名与兼容策略
- 保留名：`time.now`、`echo`（legacy）
- 兼容策略：
  - 仅允许新增可选字段
  - 删除或修改语义 => 主版本升级

## 6. 生产策略
- 开发环境：允许 `latest`
- 生产环境：禁止 `latest`，必须固定版本
