# SKILL Frontmatter 规范草案 v0.9

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
---

# knowledge-search

这里写技能说明、触发场景、示例与边界条件。
```

## 3. 字段定义
| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| name | string | 是 | - | 技能标识（小写+中划线） |
| description | string | 是 | - | 技能用途与触发场景 |
| version | string | 是 | - | 版本号（建议 SemVer） |
| entry | string | 是 | - | 执行入口（`java:`/`script:`） |
| timeoutMs | integer | 否 | 3000 | 技能超时（ms） |
| retry | integer | 否 | 0 | 最大重试次数 |
| tags | array[string] | 否 | [] | 标签 |
| capabilities | array[string] | 否 | [] | 能力声明 |
| permissions | object | 否 | {} | 权限声明（网络/文件/外部服务） |

## 4. entry 规则
- `java:<FQCN>`：Java Provider 类
- `script:<relative-path>`：脚本入口（需沙箱/白名单）

## 5. name 规则
- 仅允许：`[a-z0-9-]`
- 长度：3~64
- 禁止与系统保留名冲突：`time.now`、`echo`（legacy）

## 6. 版本策略建议
- 开发环境：可用 `latest`
- 生产环境：必须固定版本（禁止 latest）

## 7. 兼容策略
- 仅允许新增可选字段
- 删除字段或修改语义需升级主版本
